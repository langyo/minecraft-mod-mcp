"""Minecraft MCP Test Daemon — decoupled process lifecycle manager.

Architecture:
  1. Launches MCP WS server as a child process (stdin-controlled)
  2. Launches Minecraft as a detached child process
  3. Monitors MC process with configurable timeout
  4. Connects to MCP WS server as a Python bridge client
  5. When mod is NOT connected, falls back to window-level
     screenshot + simulated mouse/keyboard via pyautogui/win32gui
  6. Exposes a simple CLI for interactive or automated testing

Usage:
  python scripts/test_daemon.py start 1.21.7-forge-57.0.2
  python scripts/test_daemon.py start 1.21.7-forge-57.0.2 --timeout 180
  python scripts/test_daemon.py screenshot --save test.png
  python scripts/test_daemon.py ping
  python scripts/test_daemon.py stop
"""

import argparse
import asyncio
import base64
import io
import json
import os
import platform
import signal
import subprocess
import sys
import threading
import time
from pathlib import Path
from typing import Optional

import pyautogui
import websockets

if platform.system() == "Windows":
    try:
        import ctypes
        windll = ctypes.windll
        windll.shcore.SetProcessDpiAwareness(2)
    except Exception:
        try:
            import ctypes
            windll = ctypes.windll
            windll.user32.SetProcessDPIAware()
        except Exception:
            windll = None

ROOT = Path(__file__).resolve().parent.parent
SCRIPTS = ROOT / "scripts"
SERVER_JAR = ROOT / "build" / "libs" / "mcp-server-0.1.1.jar"
MC_DIR = Path(os.environ.get("APPDATA", os.path.expanduser("~"))) / ".minecraft"

WS_PORT = 9876
STATE_FILE = ROOT / ".test_daemon.json"

sys.path.insert(0, str(SCRIPTS))
from launch_mc import merge_version_json, build_classpath, extract_natives, build_jvm_args, build_game_args, find_java


def save_state(data: dict):
    STATE_FILE.write_text(json.dumps(data, indent=2), encoding="utf-8")


def load_state() -> dict:
    if STATE_FILE.exists():
        return json.loads(STATE_FILE.read_text(encoding="utf-8"))
    return {}


def clear_state():
    STATE_FILE.unlink(missing_ok=True)


class WindowController:
    @staticmethod
    def find_mc_window():
        if platform.system() != "Windows":
            return None
        try:
            import win32gui
            result = []

            def cb(hwnd, _):
                if win32gui.IsWindowVisible(hwnd) or True:
                    title = win32gui.GetWindowText(hwnd)
                    if title and "Minecraft" in title:
                        result.append((hwnd, title))

            win32gui.EnumWindows(cb, None)
            return result[0] if result else None
        except ImportError:
            titles = pyautogui.getWindowsWithTitle("Minecraft")
            if titles:
                return (None, titles[0].title)
            return None

    @staticmethod
    def focus_mc():
        info = WindowController.find_mc_window()
        if not info:
            return False
        hwnd, title = info
        try:
            wins = pyautogui.getWindowsWithTitle(title)
            if wins:
                wins[0].activate()
                time.sleep(0.5)
                return True
        except Exception:
            pass
        if hwnd is None:
            return False
        try:
            import win32con
            windll.user32.ShowWindow(hwnd, win32con.SW_RESTORE)
            windll.user32.BringWindowToTop(hwnd)
            windll.user32.AllowSetForegroundWindow(-1)
            windll.user32.SetForegroundWindow(hwnd)
            return True
        except Exception:
            return False

    @staticmethod
    def screenshot_window(save_path: Optional[str] = None) -> Optional[bytes]:
        info = WindowController.find_mc_window()
        if not info:
            sc = pyautogui.screenshot()
        else:
            hwnd, _ = info
            if hwnd is None:
                sc = pyautogui.screenshot()
            else:
                sc = WindowController._capture_hwnd(hwnd)
        buf = io.BytesIO()
        sc.save(buf, format="PNG")
        data = buf.getvalue()
        if save_path:
            Path(save_path).parent.mkdir(parents=True, exist_ok=True)
            sc.save(save_path)
        return data

    @staticmethod
    def _capture_hwnd(hwnd):
        try:
            import win32gui
            import win32con
            import win32api
            import struct
            import ctypes
            windll.user32.ShowWindow(hwnd, win32con.SW_RESTORE)
            windll.user32.BringWindowToTop(hwnd)
            time.sleep(0.5)
            old_dpi_ctx = None
            if hasattr(windll.user32, "GetThreadDpiAwarenessContext"):
                old_dpi_ctx = windll.user32.GetThreadDpiAwarenessContext()
                win_dpi_ctx = windll.user32.GetWindowDpiAwarenessContext(hwnd)
                windll.user32.SetThreadDpiAwarenessContext(win_dpi_ctx)
            try:
                left, top, right, bottom = win32gui.GetWindowRect(hwnd)
                w = max(right - left, 1)
                h = max(bottom - top, 1)
                hdc_win = windll.user32.GetDC(hwnd)
                hdc_mem = windll.gdi32.CreateCompatibleDC(hdc_win)
                bmp = windll.gdi32.CreateCompatibleBitmap(hdc_win, w, h)
                windll.gdi32.SelectObject(hdc_mem, bmp)
                windll.gdi32.BitBlt(hdc_mem, 0, 0, w, h, hdc_win, 0, 0, 0x00CC0020)
                bmi = ctypes.create_string_buffer(40)
                struct.pack_into("i", bmi, 0, 40)
                struct.pack_into("i", bmi, 4, w)
                struct.pack_into("i", bmi, 8, h)
                struct.pack_into("h", bmi, 12, 1)
                struct.pack_into("h", bmi, 14, 24)
                buf = ctypes.create_string_buffer(w * h * 4)
                windll.gdi32.GetDIBits(hdc_mem, bmp, 0, h, buf, bmi, 0)
                windll.gdi32.DeleteObject(bmp)
                windll.gdi32.DeleteDC(hdc_mem)
                windll.user32.ReleaseDC(hwnd, hdc_win)
                from PIL import Image
                img = Image.frombuffer("RGBX", (w, h), buf, "raw", "BGRX", 0, 1).convert("RGB")
            finally:
                if old_dpi_ctx is not None:
                    windll.user32.SetThreadDpiAwarenessContext(old_dpi_ctx)
            return img
        except Exception as e:
            print(f"[WIN] window-DC BitBlt failed: {e}, try maximize")
            try:
                import win32gui, win32con, win32api
                cur_rect = win32gui.GetWindowRect(hwnd)
                windll.user32.ShowWindow(hwnd, win32con.SW_RESTORE)
                windll.user32.SetWindowPos(hwnd, 0, 0, 0,
                    win32api.GetSystemMetrics(0), win32api.GetSystemMetrics(1),
                    0x0040 | 0x0002 | 0x0001)
                time.sleep(1.0)
                sc = pyautogui.screenshot()
                windll.user32.SetWindowPos(hwnd, 0,
                    cur_rect[0], cur_rect[1],
                    cur_rect[2] - cur_rect[0], cur_rect[3] - cur_rect[1],
                    0x0040)
                return sc
            except Exception:
                return pyautogui.screenshot()

    @staticmethod
    def _force_foreground(hwnd):
        import win32gui
        import win32con
        import win32process
        import win32api
        try:
            fg = win32gui.GetForegroundWindow()
            fg_tid = win32process.GetWindowThreadProcessId(fg)[0]
            cur_tid = win32api.GetCurrentThreadId()
            tgt_tid = win32process.GetWindowThreadProcessId(hwnd)[0]
            if cur_tid != tgt_tid:
                windll.user32.AttachThreadInput(cur_tid, tgt_tid, True)
            windll.user32.ShowWindow(hwnd, win32con.SW_RESTORE)
            time.sleep(0.2)
            windll.user32.SetForegroundWindow(hwnd)
            time.sleep(0.2)
            if cur_tid != tgt_tid:
                windll.user32.AttachThreadInput(cur_tid, tgt_tid, False)
        except Exception as e:
            print(f"[WIN] force_foreground: {e}")
            try:
                windll.user32.ShowWindow(hwnd, win32con.SW_RESTORE)
                windll.user32.BringWindowToTop(hwnd)
            except Exception:
                pass

    @staticmethod
    def click(x: int, y: int, button="left"):
        WindowController.focus_mc()
        time.sleep(0.2)
        pyautogui.click(x=x, y=y, button=button)

    @staticmethod
    def press_key(key: str):
        WindowController.focus_mc()
        time.sleep(0.1)
        pyautogui.press(key)

    @staticmethod
    def type_text(text: str):
        WindowController.focus_mc()
        time.sleep(0.1)
        pyautogui.write(text, interval=0.05)

    @staticmethod
    def get_window_rect():
        info = WindowController.find_mc_window()
        if not info:
            return None
        hwnd, _ = info
        if hwnd is None:
            return None
        try:
            import win32gui
            return win32gui.GetWindowRect(hwnd)
        except Exception:
            return None


class McpWsBridge:
    def __init__(self, port=WS_PORT):
        self.port = port
        self.ws = None
        self.req_id = 0

    async def connect(self):
        uri = f"ws://127.0.0.1:{self.port}"
        try:
            self.ws = await asyncio.wait_for(websockets.connect(uri), timeout=5)
            return True
        except Exception as e:
            print(f"[BRIDGE] Connect failed: {e}")
            return False

    async def call_tool(self, name: str, args: dict = None, timeout=10) -> dict:
        if not self.ws:
            return {"error": "not connected"}
        self.req_id += 1
        msg = {
            "jsonrpc": "2.0",
            "id": self.req_id,
            "method": "tools/call",
            "params": {"name": name, "arguments": args or {}},
        }
        try:
            await self.ws.send(json.dumps(msg))
            resp = await asyncio.wait_for(self.ws.recv(), timeout=timeout)
            return json.loads(resp)
        except Exception as e:
            return {"error": str(e)}

    async def is_mod_connected(self) -> bool:
        resp = await self.call_tool("get_window_info", {})
        try:
            text = resp["result"]["content"][0]["text"]
            data = json.loads(text)
            return data.get("mcConnected", False)
        except Exception:
            return False

    async def close(self):
        if self.ws:
            await self.ws.close()


class MinecraftDaemon:
    def __init__(self, version: str, timeout: int = 300, ws_port: int = WS_PORT):
        self.version = version
        self.timeout = timeout
        self.ws_port = ws_port
        self.server_proc: Optional[subprocess.Popen] = None
        self.mc_proc: Optional[subprocess.Popen] = None
        self.server_thread: Optional[threading.Thread] = None
        self._stop_event = threading.Event()
        self.bridge: Optional[McpWsBridge] = None
        self.window = WindowController()

    def _start_server(self):
        java = find_java(21)
        cmd = [java, "-jar", str(SERVER_JAR)]
        self.server_proc = subprocess.Popen(
            cmd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )

        def reader():
            for line in self.server_proc.stdout:
                if self._stop_event.is_set():
                    break
                line = line.strip()
                if line:
                    print(f"  [SRV] {line}")

        self.server_thread = threading.Thread(target=reader, daemon=True)
        self.server_thread.start()

    def _send_server(self, tool: str, args: dict = None) -> Optional[str]:
        if not self.server_proc or self.server_proc.stdin.closed:
            return None
        rid = int(time.time() * 1000)
        msg = json.dumps({"jsonrpc": "2.0", "id": rid, "method": "tools/call",
                          "params": {"name": tool, "arguments": args or {}}})
        try:
            self.server_proc.stdin.write(msg + "\n")
            self.server_proc.stdin.flush()
            return msg
        except Exception:
            return None

    def _start_mc(self):
        vj = merge_version_json(self.version)
        cp = build_classpath(vj)
        natives_dir = extract_natives(vj)
        java_ver = vj.get("javaVersion", {}).get("majorVersion", 21)
        java_exe = find_java(java_ver)
        jvm_args = build_jvm_args(vj, natives_dir, java_exe=java_exe)
        game_args = build_game_args(vj, self.version)

        sep = ";" if platform.system() == "Windows" else ":"
        cp_str = sep.join(cp)

        cmd = [java_exe, "-Xmx4G", "-Xms1G", f"-Dmcp.server=ws://127.0.0.1:{self.ws_port}"]
        cmd.extend(jvm_args)
        cmd.extend(["-cp", cp_str])
        cmd.append(vj.get("mainClass"))
        cmd.extend(game_args)

        env = os.environ.copy()
        env["MC_MCP_SERVER"] = f"ws://127.0.0.1:{self.ws_port}"

        stdout_log = MC_DIR / "mcp-launch-stdout.log"
        stderr_log = MC_DIR / "mcp-launch-stderr.log"
        fout = open(stdout_log, "w", encoding="utf-8", errors="replace")
        ferr = open(stderr_log, "w", encoding="utf-8", errors="replace")

        self.mc_proc = subprocess.Popen(cmd, env=env, cwd=str(MC_DIR), stdout=fout, stderr=ferr)
        print(f"[DAEMON] MC started: pid={self.mc_proc.pid}")

    def start(self):
        print(f"[DAEMON] Starting for {self.version} (timeout={self.timeout}s)")
        self._start_server()
        time.sleep(3)

        print("[DAEMON] Checking WS server...")
        import socket
        for _ in range(20):
            try:
                s = socket.socket()
                s.settimeout(1)
                s.connect(("127.0.0.1", self.ws_port))
                s.close()
                print("[DAEMON] WS server ready")
                break
            except Exception:
                time.sleep(0.5)

        self._start_mc()

        state = {
            "version": self.version,
            "mc_pid": self.mc_proc.pid,
            "server_pid": self.server_proc.pid,
            "ws_port": self.ws_port,
            "started_at": time.time(),
            "timeout": self.timeout,
        }
        save_state(state)
        print(f"[DAEMON] Running. MC pid={self.mc_proc.pid}, Server pid={self.server_proc.pid}")

    def wait_for_mod(self, timeout=120) -> bool:
        print(f"[DAEMON] Waiting up to {timeout}s for mod to connect...")
        start = time.time()
        while time.time() - start < timeout:
            if self.mc_proc and self.mc_proc.poll() is not None:
                print(f"[DAEMON] MC exited with code {self.mc_proc.returncode}")
                return False
            self._send_server("get_window_info")
            time.sleep(2)
            stdout_log = MC_DIR / "mcp-launch-stdout.log"
            if stdout_log.exists():
                content = stdout_log.read_text(encoding="utf-8", errors="replace")
                if "[MCP-WS] Connected" in content:
                    print("[DAEMON] Mod connected via WS!")
                    return True
        print("[DAEMON] Timeout waiting for mod connection")
        return False

    def wait_mc(self):
        if not self.mc_proc:
            return
        try:
            self.mc_proc.wait(timeout=self.timeout)
            print(f"[DAEMON] MC exited with code {self.mc_proc.returncode}")
        except subprocess.TimeoutExpired:
            print(f"[DAEMON] MC timeout ({self.timeout}s), killing...")
            self.mc_proc.kill()

    def screenshot(self, save_path: Optional[str] = None) -> Optional[str]:
        bridge_connected = False
        try:
            loop = asyncio.new_event_loop()
            bridge = McpWsBridge(self.ws_port)
            bridge_connected = loop.run_until_complete(bridge.connect())
            if bridge_connected:
                connected = loop.run_until_complete(bridge.is_mod_connected())
                if connected:
                    resp = loop.run_until_complete(bridge.call_tool("screenshot", {}, timeout=15))
                    try:
                        text = resp["result"]["content"][0]["text"]
                        if not text.startswith("error") and not text.startswith("mod error"):
                            if save_path:
                                img_data = base64.b64decode(text)
                                Path(save_path).write_bytes(img_data)
                            loop.run_until_complete(bridge.close())
                            loop.close()
                            return "mod"
                    except Exception:
                        pass
            loop.run_until_complete(bridge.close())
            loop.close()
        except Exception as e:
            print(f"[DAEMON] WS screenshot failed: {e}")

        print("[DAEMON] Falling back to window screenshot...")
        data = self.window.screenshot_window(save_path=save_path)
        return "window" if data else None

    def ping(self) -> dict:
        try:
            loop = asyncio.new_event_loop()
            bridge = McpWsBridge(self.ws_port)
            connected = loop.run_until_complete(bridge.connect())
            if connected:
                resp = loop.run_until_complete(bridge.call_tool("ping", {}, timeout=10))
                loop.run_until_complete(bridge.close())
                loop.close()
                return resp
            loop.run_until_complete(bridge.close())
            loop.close()
        except Exception as e:
            pass

        print("[DAEMON] WS ping failed, sending via server stdin...")
        self._send_server("ping")
        time.sleep(3)
        return {"method": "stdin_relay", "status": "sent"}

    def stop(self):
        print("[DAEMON] Stopping...")
        self._stop_event.set()
        if self.mc_proc and self.mc_proc.poll() is None:
            self.mc_proc.kill()
            print(f"[DAEMON] MC killed (pid={self.mc_proc.pid})")
        if self.server_proc and self.server_proc.poll() is None:
            try:
                self.server_proc.stdin.close()
            except Exception:
                pass
            self.server_proc.kill()
            print(f"[DAEMON] Server killed (pid={self.server_proc.pid})")
        clear_state()
        print("[DAEMON] Stopped.")


def cmd_start(args):
    d = MinecraftDaemon(args.version, timeout=args.timeout, ws_port=args.port)
    d.start()
    if args.wait:
        connected = d.wait_for_mod(timeout=args.wait)
        if connected and args.auto_test:
            print("[DAEMON] Running auto-test sequence...")
            d.ping()
            time.sleep(3)
            d.screenshot(save_path=str(ROOT / f"test_{args.version}_screenshot.png"))
    if args.foreground:
        try:
            d.wait_mc()
        except KeyboardInterrupt:
            d.stop()
    else:
        print(f"[DAEMON] Running in background. Use 'python scripts/test_daemon.py stop' to stop.")
        print(f"[DAEMON] State saved to {STATE_FILE}")


def cmd_screenshot(args):
    d = _attach_daemon()
    if not d:
        return
    path = args.save or str(ROOT / f"test_{d.version}_screenshot.png")
    result = d.screenshot(save_path=path)
    if result:
        print(f"[DAEMON] Screenshot saved via {result}: {path}")
    else:
        print("[DAEMON] Screenshot failed")


def cmd_ping(args):
    d = _attach_daemon()
    if not d:
        return
    result = d.ping()
    print(f"[DAEMON] Ping result: {json.dumps(result, indent=2)}")


def cmd_stop(args):
    state = load_state()
    if not state:
        print("[DAEMON] No running daemon found")
        return
    mc_pid = state.get("mc_pid")
    server_pid = state.get("server_pid")
    if mc_pid:
        try:
            os.kill(mc_pid, signal.SIGTERM)
            print(f"[DAEMON] Killed MC pid={mc_pid}")
        except ProcessLookupError:
            print(f"[DAEMON] MC pid={mc_pid} already dead")
    if server_pid:
        try:
            os.kill(server_pid, signal.SIGTERM)
            print(f"[DAEMON] Killed server pid={server_pid}")
        except ProcessLookupError:
            print(f"[DAEMON] Server pid={server_pid} already dead")
    clear_state()
    print("[DAEMON] Stopped")


def cmd_status(args):
    state = load_state()
    if not state:
        print("[DAEMON] No running daemon")
        return
    mc_pid = state.get("mc_pid")
    server_pid = state.get("server_pid")
    mc_alive = _is_alive(mc_pid)
    srv_alive = _is_alive(server_pid)
    print(f"  Version:    {state.get('version')}")
    print(f"  MC PID:     {mc_pid} ({'alive' if mc_alive else 'dead'})")
    print(f"  Server PID: {server_pid} ({'alive' if srv_alive else 'dead'})")
    print(f"  WS Port:    {state.get('ws_port')}")
    elapsed = time.time() - state.get("started_at", time.time())
    print(f"  Uptime:     {elapsed:.0f}s")
    print(f"  Timeout:    {state.get('timeout')}s")

    wc = WindowController()
    info = wc.find_mc_window()
    if info:
        print(f"  Window:     {info[1]}")
        rect = wc.get_window_rect()
        if rect:
            print(f"  Rect:       {rect}")
    else:
        print("  Window:     not found")


def cmd_window(args):
    wc = WindowController()
    info = wc.find_mc_window()
    if info:
        hwnd, title = info
        print(f"  Found: hwnd={hwnd}, title='{title}'")
        rect = wc.get_window_rect()
        if rect:
            print(f"  Rect: {rect}")
    else:
        print("  No MC window found")

    if args.screenshot:
        data = wc.screenshot_window(save_path=args.screenshot)
        if data:
            print(f"  Screenshot saved: {args.screenshot} ({len(data)} bytes)")
        else:
            print("  Screenshot failed")

    if args.click:
        x, y = map(int, args.click.split(","))
        wc.click(x, y)
        print(f"  Clicked ({x}, {y})")

    if args.press:
        wc.press_key(args.press)
        print(f"  Pressed: {args.press}")

    if args.type:
        wc.type_text(args.type)
        print(f"  Typed: {args.type}")

    if args.focus:
        ok = wc.focus_mc()
        print(f"  Focus: {'ok' if ok else 'failed'}")


def _attach_daemon() -> Optional[MinecraftDaemon]:
    state = load_state()
    if not state:
        print("[DAEMON] No running daemon. Start one first.")
        return None
    d = MinecraftDaemon(state["version"], ws_port=state.get("ws_port", WS_PORT))
    mc_pid = state.get("mc_pid")
    srv_pid = state.get("server_pid")
    if _is_alive(mc_pid):
        d.mc_proc = subprocess.Popen(["echo", "attach"])  # dummy
        d.mc_proc.pid = mc_pid
    if _is_alive(srv_pid):
        d.server_proc = subprocess.Popen(["echo", "attach"], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
        d.server_proc.pid = srv_pid
    return d


def _is_alive(pid) -> bool:
    if not pid:
        return False
    try:
        os.kill(pid, 0)
        return True
    except (ProcessLookupError, PermissionError):
        return False


def main():
    parser = argparse.ArgumentParser(description="Minecraft MCP Test Daemon")
    sub = parser.add_subparsers(dest="command")

    p_start = sub.add_parser("start", help="Start MC + MCP server")
    p_start.add_argument("version", help="MC version (e.g. 1.21.7-forge-57.0.2)")
    p_start.add_argument("--timeout", type=int, default=300, help="MC process timeout (seconds)")
    p_start.add_argument("--port", type=int, default=WS_PORT, help="WS port")
    p_start.add_argument("--foreground", action="store_true", help="Block until MC exits")
    p_start.add_argument("--wait", type=int, default=0, help="Wait N seconds for mod connection")
    p_start.add_argument("--auto-test", action="store_true", help="Run test sequence after mod connects")

    p_stop = sub.add_parser("stop", help="Stop running daemon")

    p_status = sub.add_parser("status", help="Show daemon status")

    p_screenshot = sub.add_parser("screenshot", help="Take screenshot")
    p_screenshot.add_argument("--save", help="Save path")

    p_ping = sub.add_parser("ping", help="Ping mod via WS")

    p_window = sub.add_parser("window", help="Window control (fallback)")
    p_window.add_argument("--screenshot", help="Take window screenshot")
    p_window.add_argument("--click", help="Click x,y")
    p_window.add_argument("--press", help="Press key")
    p_window.add_argument("--type", help="Type text")
    p_window.add_argument("--focus", action="store_true", help="Focus MC window")

    args = parser.parse_args()
    if args.command == "start":
        cmd_start(args)
    elif args.command == "stop":
        cmd_stop(args)
    elif args.command == "status":
        cmd_status(args)
    elif args.command == "screenshot":
        cmd_screenshot(args)
    elif args.command == "ping":
        cmd_ping(args)
    elif args.command == "window":
        cmd_window(args)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()

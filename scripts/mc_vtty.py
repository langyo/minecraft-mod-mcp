"""MC Virtual Terminal — standalone WS server for MC mod + TCP control.

Runs a WebSocket server on port 9876 (MC mod connects to it).
Runs a TCP control server on port 9877 for CLI commands.
No Kotlin MCP server needed — pure Python.

Usage:
  python scripts/mc_vtty.py                              # start daemon
  python scripts/mc_vtty.py --send 'status'              # check state
  python scripts/mc_vtty.py --send 'launch 1.21.7-forge-57.0.2'
  python scripts/mc_vtty.py --send 'screenshot menu'     # take screenshot
  python scripts/mc_vtty.py --send 'click 960 480'       # click
  python scripts/mc_vtty.py --send 'type_text hello'     # type text
  python scripts/mc_vtty.py --send 'press_key E'         # press key
  python scripts/mc_vtty.py --send 'hotkey Ctrl,S'       # key combo
  python scripts/mc_vtty.py --send 'scroll -3'           # scroll
  python scripts/mc_vtty.py --send 'command /gamemode creative'
  python scripts/mc_vtty.py --send 'wait 5'              # wait seconds
  python scripts/mc_vtty.py --send 'kill'                # kill MC
"""

import argparse
import asyncio
import base64
import json
import os
import signal
import socket
import subprocess
import sys
import threading
import time
import traceback
from pathlib import Path


def _log(msg):
    try:
        print(msg, flush=True)
    except OSError:
        try:
            print(msg)
        except Exception:
            pass

import websockets

ROOT = Path(__file__).resolve().parent.parent
SCRIPTS = ROOT / "scripts"
MC_DIR = Path(os.environ.get("APPDATA", os.path.expanduser("~"))) / ".minecraft"
VTTY_PORT = 9877
WS_PORT = 9876
SS_DIR = ROOT / "screenshots" / "vtty"

sys.path.insert(0, str(SCRIPTS))
from test_version import clear_mods, install_mod, _start_mc


class McVtty:
    def __init__(self):
        self.mc_proc = None
        self.version = None
        self._mc_ws = None
        self._ws_connected = False
        self._pending = {}
        self._req_id = 0
        self._lock = threading.Lock()
        self._ws_loop = None
        self._ws_server = None

    def start_ws_server(self):
        self._ws_loop = asyncio.new_event_loop()
        t = threading.Thread(target=self._run_ws_loop, daemon=True)
        t.start()
        time.sleep(0.5)
        _log(f"[VTTY] WS server on port {WS_PORT}")

    def _run_ws_loop(self):
        asyncio.set_event_loop(self._ws_loop)
        self._ws_loop.run_until_complete(self._ws_serve())

    async def _ws_serve(self):
        self._ws_server = await websockets.serve(
            self._ws_handler,
            "0.0.0.0",
            WS_PORT,
            max_size=20 * 1024 * 1024,
        )
        await self._ws_server.wait_closed()

    async def _ws_handler(self, ws):
        _log(f"[WS] MC mod connected")
        self._mc_ws = ws
        self._ws_connected = True
        try:
            await ws.send(json.dumps({
                "jsonrpc": "2.0",
                "method": "initialize",
                "params": {},
                "id": "init",
            }))
        except Exception:
            pass

        try:
            async for raw in ws:
                try:
                    msg = json.loads(raw)
                except Exception:
                    continue

                rid = str(msg.get("id", ""))
                if rid == "init":
                    _log(f"[WS] Init OK")
                    continue

                if rid in self._pending:
                    cb = self._pending.pop(rid)
                    cb(msg)
                else:
                    result = str(msg.get("result", ""))
                    if result.startswith("data:image"):
                        _log(f"[WS] unsolicited image ({len(result)} chars)")
                    else:
                        _log(f"[WS] msg: {result[:200]}")
        except websockets.ConnectionClosed:
            pass
        finally:
            _log(f"[WS] MC mod disconnected")
            if self._mc_ws is ws:
                self._mc_ws = None
                self._ws_connected = False

    def _send_ws(self, method, params=None, timeout=20):
        if not self._ws_connected or not self._mc_ws:
            return {"error": "MC mod not connected"}

        with self._lock:
            rid = f"r_{self._req_id}"
            self._req_id += 1

        result_box = [None]
        event = threading.Event()

        def on_response(msg):
            result_box[0] = msg
            event.set()

        self._pending[rid] = on_response

        msg = {
            "jsonrpc": "2.0",
            "method": method,
            "params": dict(params or {}, requestId=rid),
        }
        try:
            asyncio.run_coroutine_threadsafe(
                self._mc_ws.send(json.dumps(msg)),
                self._ws_loop,
            )
        except Exception as e:
            self._pending.pop(rid, None)
            return {"error": f"WS send failed: {e}"}

        if not event.wait(timeout):
            self._pending.pop(rid, None)
            return {"error": f"timeout after {timeout}s"}

        resp = result_box[0]
        if resp and "result" in resp:
            r = resp["result"]
            if isinstance(r, str):
                if r.startswith("data:image/png;base64,"):
                    return {"type": "image", "data": r}
                if r.startswith("error:"):
                    return {"error": r}
                return {"type": "text", "data": r}
            return r
        if resp and "error" in resp:
            return {"error": str(resp["error"])}
        return resp or {"error": "empty response"}

    def launch(self, version, loader="forge"):
        self.version = version
        if self.mc_proc:
            self.kill()

        _log(f"[VTTY] Installing mod {version}/{loader}...")
        clear_mods()
        install_mod(version, loader)
        time.sleep(1)

        _log(f"[VTTY] Launching MC {version}...")
        self.mc_proc = _start_mc(version)
        if not self.mc_proc:
            return {"error": "MC launch failed"}

        _log(f"[VTTY] MC pid={self.mc_proc.pid}, waiting for mod WS...")
        for i in range(80):
            if self.mc_proc.poll() is not None:
                return {"error": f"MC exited code={self.mc_proc.returncode}"}
            if self._ws_connected:
                break
            time.sleep(3)

        if not self._ws_connected:
            return {"error": "Mod WS connection timeout", "pid": self.mc_proc.pid}
        return {"status": "ok", "pid": self.mc_proc.pid, "ws": True}

    def status(self):
        mc_alive = self.mc_proc is not None and self.mc_proc.poll() is None
        return {
            "mc_alive": mc_alive,
            "ws_connected": self._ws_connected,
            "version": self.version,
            "pid": self.mc_proc.pid if self.mc_proc else None,
        }

    def screenshot(self, name="screenshot"):
        SS_DIR.mkdir(parents=True, exist_ok=True)
        result = self._send_ws("screenshot", timeout=20)
        if isinstance(result, dict) and result.get("type") == "image":
            data_uri = result["data"]
            if data_uri.startswith("data:image/png;base64,"):
                b64 = data_uri[len("data:image/png;base64,"):]
                png_bytes = base64.b64decode(b64)
                ts = int(time.time() * 1000)
                target = SS_DIR / f"{name}_{ts}.png"
                target.write_bytes(png_bytes)
                return {"path": str(target), "size": len(png_bytes)}
            return result
        if isinstance(result, dict) and result.get("error"):
            return result
        return {"result": str(result)[:200]}

    def click(self, x, y, button="left"):
        return self._send_ws("click", {"x": str(x), "y": str(y), "button": button})

    def focus(self):
        try:
            import pyautogui
            wins = pyautogui.getWindowsWithTitle("Minecraft")
            if wins:
                w = wins[0]
                w.activate()
                time.sleep(0.5)
                return {"focused": True, "title": w.title}
            return {"focused": False, "error": "window not found"}
        except Exception as e:
            return {"error": str(e)}

    def pyclick(self, x, y, button="left"):
        try:
            import pyautogui
            self.focus()
            time.sleep(0.3)

            rect = self.get_window_rect()
            if "error" in rect:
                return rect
            win_w = rect["width"]
            win_h = rect["height"]
            win_l = rect["left"]
            win_t = rect["top"]

            fb_w, fb_h = self._get_framebuffer_size()

            if fb_w > 0 and fb_h > 0:
                sx = win_l + int(x * win_w / fb_w)
                sy = win_t + int(y * win_h / fb_h)
            else:
                sx = win_l + x
                sy = win_t + y

            btn = "left" if button == "left" else "right" if button == "right" else "middle"
            pyautogui.click(x=sx, y=sy, button=btn)
            return {"clicked": True, "screen_x": sx, "screen_y": sy, "fb_coord": [x, y], "window": [win_l, win_t, win_w, win_h], "fb": [fb_w, fb_h]}
        except Exception as e:
            return {"error": str(e)}

    def win32click(self, x, y, button="left"):
        try:
            import ctypes
            import pyautogui
            user32 = ctypes.windll.user32

            self.focus()
            time.sleep(0.5)

            rect = self.get_window_rect()
            if "error" in rect:
                return rect
            win_w = rect["width"]
            win_h = rect["height"]
            win_l = rect["left"]
            win_t = rect["top"]

            fb_w, fb_h = self._get_framebuffer_size()

            if fb_w > 0 and fb_h > 0:
                sx = win_l + int(x * win_w / fb_w)
                sy = win_t + int(y * win_h / fb_h)
            else:
                sx = win_l + x
                sy = win_t + y

            screen_w = user32.GetSystemMetrics(0)
            screen_h = user32.GetSystemMetrics(1)

            MOUSEEVENTF_LEFTDOWN = 0x0002
            MOUSEEVENTF_LEFTUP = 0x0004
            MOUSEEVENTF_RIGHTDOWN = 0x0008
            MOUSEEVENTF_RIGHTUP = 0x0010
            MOUSEEVENTF_MIDDLEDOWN = 0x0020
            MOUSEEVENTF_MIDDLEUP = 0x0040
            MOUSEEVENTF_MOVE = 0x0001
            MOUSEEVENTF_ABSOLUTE = 0x8000

            norm_x = int(sx * 65535 / (screen_w - 1))
            norm_y = int(sy * 65535 / (screen_h - 1))

            user32.SetCursorPos(sx, sy)
            time.sleep(0.05)
            user32.mouse_event(MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_MOVE, norm_x, norm_y, 0, 0)
            time.sleep(0.02)

            if button == "left":
                user32.mouse_event(MOUSEEVENTF_LEFTDOWN, norm_x, norm_y, 0, 0)
                time.sleep(0.05)
                user32.mouse_event(MOUSEEVENTF_LEFTUP, norm_x, norm_y, 0, 0)
            elif button == "right":
                user32.mouse_event(MOUSEEVENTF_RIGHTDOWN, norm_x, norm_y, 0, 0)
                time.sleep(0.05)
                user32.mouse_event(MOUSEEVENTF_RIGHTUP, norm_x, norm_y, 0, 0)
            else:
                user32.mouse_event(MOUSEEVENTF_MIDDLEDOWN, norm_x, norm_y, 0, 0)
                time.sleep(0.05)
                user32.mouse_event(MOUSEEVENTF_MIDDLEUP, norm_x, norm_y, 0, 0)

            return {"clicked": True, "screen_x": sx, "screen_y": sy, "fb": [fb_w, fb_h]}
        except Exception as e:
            return {"error": str(e)}

    def get_window_rect(self):
        try:
            import pyautogui
            wins = pyautogui.getWindowsWithTitle("Minecraft")
            if wins:
                w = wins[0]
                return {"left": w.left, "top": w.top, "width": w.width, "height": w.height, "title": w.title}
            return {"error": "window not found"}
        except Exception as e:
            return {"error": str(e)}

    def _get_framebuffer_size(self):
        if hasattr(self, '_fb_cache') and self._fb_cache:
            return self._fb_cache
        try:
            import tempfile
            tmp = tempfile.mktemp(suffix=".png")
            SS_DIR.mkdir(parents=True, exist_ok=True)
            result = self._send_ws("screenshot", timeout=20)
            if isinstance(result, dict) and result.get("type") == "image":
                data_uri = result["data"]
                if data_uri.startswith("data:image/png;base64,"):
                    b64 = data_uri[len("data:image/png;base64,"):]
                    png_bytes = base64.b64decode(b64)
                    w = int.from_bytes(png_bytes[16:20], 'big')
                    h = int.from_bytes(png_bytes[20:24], 'big')
                    self._fb_cache = (w, h)
                    return (w, h)
        except Exception:
            pass
        return (0, 0)

    def press_key(self, key, hold=0):
        p = {"key": key}
        if hold > 0:
            p["hold_seconds"] = str(hold)
        return self._send_ws("press_key", p)

    def type_text(self, text, enter=False):
        p = {"text": text}
        if enter:
            p["press_enter"] = "true"
        return self._send_ws("type_text", p)

    def scroll(self, clicks):
        return self._send_ws("scroll", {"clicks": str(clicks)})

    def hotkey(self, keys_str):
        keys = [k.strip() for k in keys_str.split(",")]
        return self._send_ws("hotkey", {"keys": ",".join(keys)})

    def execute_command(self, cmd):
        return self._send_ws("execute_command", {"command": cmd})

    def kill(self):
        self._ws_connected = False
        self._mc_ws = None
        if self._ws_server:
            async def _close():
                self._ws_server.close()
            if self._ws_loop and not self._ws_loop.is_closed():
                asyncio.run_coroutine_threadsafe(_close(), self._ws_loop)
        if self.mc_proc and self.mc_proc.poll() is None:
            self.mc_proc.kill()
        self.mc_proc = None
        try:
            r = subprocess.run(
                ["tasklist", "/FI", "IMAGENAME eq java.exe", "/FO", "CSV", "/NH"],
                capture_output=True, text=True, timeout=5,
            )
            for line in r.stdout.strip().split("\n"):
                if "java" in line.lower():
                    parts = line.strip('"').split('","')
                    if len(parts) >= 2:
                        try:
                            os.kill(int(parts[1]), signal.SIGTERM)
                        except Exception:
                            pass
        except Exception:
            pass
        time.sleep(2)

    def handle_cmd(self, cmd_dict):
        cmd = cmd_dict.get("cmd", "")
        try:
            if cmd == "launch":
                return self.launch(
                    cmd_dict.get("version", "1.21.7-forge-57.0.2"),
                    cmd_dict.get("loader", "forge"),
                )
            elif cmd == "status":
                return self.status()
            elif cmd == "kill":
                self.kill()
                return {"status": "killed"}
            elif cmd == "screenshot":
                return self.screenshot(cmd_dict.get("name", "auto"))
            elif cmd == "click":
                return self.click(
                    int(cmd_dict.get("x", 0)),
                    int(cmd_dict.get("y", 0)),
                    cmd_dict.get("button", "left"),
                )
            elif cmd == "press_key":
                return self.press_key(
                    cmd_dict.get("key", "Enter"),
                    float(cmd_dict.get("hold", 0)),
                )
            elif cmd == "type_text":
                return self.type_text(
                    cmd_dict.get("text", ""),
                    cmd_dict.get("enter", False),
                )
            elif cmd == "scroll":
                return self.scroll(int(cmd_dict.get("clicks", 1)))
            elif cmd == "focus":
                return self.focus()
            elif cmd == "pyclick":
                return self.pyclick(
                    int(cmd_dict.get("x", 0)),
                    int(cmd_dict.get("y", 0)),
                    cmd_dict.get("button", "left"),
                )
            elif cmd == "win32click":
                return self.win32click(
                    int(cmd_dict.get("x", 0)),
                    int(cmd_dict.get("y", 0)),
                    cmd_dict.get("button", "left"),
                )
            elif cmd == "window_rect":
                return self.get_window_rect()
            elif cmd == "hotkey":
                return self.hotkey(cmd_dict.get("keys", ""))
            elif cmd == "command":
                return self.execute_command(cmd_dict.get("command", ""))
            elif cmd == "wait":
                time.sleep(float(cmd_dict.get("seconds", 1)))
                return {"waited": cmd_dict.get("seconds", 1)}
            else:
                return {"error": f"unknown cmd: {cmd}"}
        except Exception as e:
            return {"error": str(e), "traceback": traceback.format_exc()}


def run_tcp_server(vtty):
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(("127.0.0.1", VTTY_PORT))
    sock.listen(5)
    _log(f"[VTTY] TCP control server on 127.0.0.1:{VTTY_PORT}")
    while True:
        conn, addr = sock.accept()
        threading.Thread(target=_handle_tcp, args=(vtty, conn), daemon=True).start()


def _handle_tcp(vtty, conn):
    try:
        conn.settimeout(120)
        buf = b""
        while True:
            data = conn.recv(65536)
            if not data:
                break
            buf += data
            while b"\n" in buf:
                line, buf = buf.split(b"\n", 1)
                try:
                    cmd_dict = json.loads(line)
                    result = vtty.handle_cmd(cmd_dict)
                    resp = json.dumps(result, ensure_ascii=False) + "\n"
                    conn.sendall(resp.encode("utf-8"))
                except Exception as e:
                    try:
                        conn.sendall((json.dumps({"error": str(e)}) + "\n").encode())
                    except Exception:
                        pass
    except (socket.timeout, ConnectionResetError, OSError):
        pass
    finally:
        try:
            conn.close()
        except Exception:
            pass


def send_command(cmd_str, port=VTTY_PORT):
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(120)
    sock.connect(("127.0.0.1", port))
    parts = cmd_str.strip().split(None, 1)
    cmd = parts[0]
    rest = parts[1] if len(parts) > 1 else ""
    cmd_dict = {"cmd": cmd}

    if cmd == "launch":
        cmd_dict["version"] = rest if rest else "1.21.7-forge-57.0.2"
    elif cmd == "click":
        coords = rest.split()
        if len(coords) >= 2:
            cmd_dict["x"] = int(coords[0])
            cmd_dict["y"] = int(coords[1])
        if len(coords) >= 3:
            cmd_dict["button"] = coords[2]
    elif cmd == "press_key":
        cmd_dict["key"] = rest
    elif cmd == "type_text":
        cmd_dict["text"] = rest
    elif cmd == "screenshot":
        cmd_dict["name"] = rest if rest else "auto"
    elif cmd == "scroll":
        cmd_dict["clicks"] = int(rest) if rest else 1
    elif cmd == "focus":
        pass
    elif cmd == "pyclick":
        coords = rest.split()
        if len(coords) >= 2:
            cmd_dict["x"] = int(coords[0])
            cmd_dict["y"] = int(coords[1])
        if len(coords) >= 3:
            cmd_dict["button"] = coords[2]
    elif cmd == "win32click":
        coords = rest.split()
        if len(coords) >= 2:
            cmd_dict["x"] = int(coords[0])
            cmd_dict["y"] = int(coords[1])
        if len(coords) >= 3:
            cmd_dict["button"] = coords[2]
    elif cmd == "window_rect":
        pass
    elif cmd == "hotkey":
        cmd_dict["keys"] = rest
    elif cmd == "command":
        cmd_dict["command"] = rest
    elif cmd == "wait":
        cmd_dict["seconds"] = float(rest) if rest else 1

    sock.sendall((json.dumps(cmd_dict) + "\n").encode())
    buf = b""
    while True:
        data = sock.recv(65536)
        if not data:
            break
        buf += data
        if b"\n" in buf:
            line, _ = buf.split(b"\n", 1)
            result = json.loads(line)
            sock.close()
            return result
    sock.close()
    return {"error": "no response"}


def main():
    global VTTY_PORT, WS_PORT
    parser = argparse.ArgumentParser(description="MC Virtual Terminal")
    parser.add_argument("--send", type=str, help="Send command to running daemon")
    parser.add_argument("--json", type=str, help="Send raw JSON command")
    parser.add_argument("--port", type=int, default=VTTY_PORT, help="TCP control port")
    parser.add_argument("--ws-port", type=int, default=WS_PORT, help="WS server port")
    args = parser.parse_args()

    VTTY_PORT = args.port
    WS_PORT = args.ws_port

    if args.send:
        result = send_command(args.send, VTTY_PORT)
        print(json.dumps(result, indent=2, ensure_ascii=False))
        return
    elif args.json:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(120)
        sock.connect(("127.0.0.1", VTTY_PORT))
        sock.sendall((args.json + "\n").encode())
        buf = b""
        while True:
            data = sock.recv(65536)
            if not data:
                break
            buf += data
            if b"\n" in buf:
                line, _ = buf.split(b"\n", 1)
                print(line.decode())
                sock.close()
                return
        sock.close()
        return

    vtty = McVtty()
    vtty.start_ws_server()

    threading.Thread(target=run_tcp_server, args=(vtty,), daemon=True).start()

    _log(f"[VTTY] Daemon ready. WS={WS_PORT} TCP={VTTY_PORT}")
    _log(f"[VTTY] Commands: launch, status, screenshot, click, pyclick, focus, window_rect, press_key, type_text, scroll, hotkey, command, wait, kill")

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        _log("\n[VTTY] Shutting down...")
        vtty.kill()


if __name__ == "__main__":
    main()

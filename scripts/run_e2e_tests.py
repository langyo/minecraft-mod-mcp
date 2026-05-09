#!/usr/bin/env python3
"""
Minecraft MCP E2E Test - Full Automation
Launches MC, creates/joins world, runs complete test suite via mod WebSocket

Usage:
    python run_e2e_tests.py                          # full auto (create superflat world)
    python run_e2e_tests.py --world-name MyWorld     # join specific world
    python run_e2e_tests.py --no-launch               # MC already running
    python run_e2e_tests.py --port 9879 --timeout 120
"""

import json
import os
import subprocess
import sys
import time
import shutil
import signal
import platform
from pathlib import Path
from typing import Optional

PROJECT_ROOT = Path(__file__).resolve().parent
JAR_PATH = PROJECT_ROOT / "build" / "libs" / "mcp-server-0.1.0.jar"
SCREENSHOT_DIR = PROJECT_ROOT / "screenshots"

# --- Terminal colors ---
G, R, Y, M, C, W, D = "\033[92m", "\033[91m", "\033[93m", "\033[95m", "\033[96m", "\033[0m", "\033[90m"


def log(msg, c=W): print(f"{c}{msg}{W}")


# --- MCP Client ---
class McpClient:
    def __init__(self, port: int):
        self.port = port
        self.proc = None

    def call(self, method: str, params: dict = None, timeout: int = 10) -> dict:
        """Single JSON-RPC call, returns parsed response."""
        req_id = int(time.time() * 1000) % 99999
        req = {"jsonrpc": "2.0", "id": req_id, "method": method}
        if params:
            req["params"] = params
        results = self._send_batch([req], timeout)
        for r in results:
            if r.get("id") == req_id:
                return r
        return {"error": {"message": "timeout"}}

    def call_tool(self, name: str, arguments: dict = None, timeout: int = 10) -> dict:
        """Call a tool."""
        params = {"name": name}
        if arguments:
            params["arguments"] = arguments
        return self.call("tools/call", params, timeout)

    def _send_batch(self, requests: list, timeout: int = 15) -> list:
        payload = "\n".join(json.dumps(r) for r in requests)
        env = os.environ.copy()
        env["MC_MCP_WS_PORT"] = str(self.port)

        self.proc = subprocess.Popen(
            [shutil.which("java") or "java", "-jar", str(JAR_PATH)],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
            env=env, cwd=str(PROJECT_ROOT),
            creationflags=subprocess.CREATE_NO_WINDOW if platform.system() == "Windows" else 0,
        )
        try:
            self.proc.stdin.write(payload.encode())
            self.proc.stdin.close()
            raw = b""
            deadline = time.time() + timeout
            while time.time() < deadline:
                try:
                    chunk = self.proc.stdout.read(65536)
                    if not chunk:
                        break
                    raw += chunk
                except Exception:
                    break
                time.sleep(0.05)
        finally:
            try:
                self.proc.wait(timeout=3)
            except subprocess.TimeoutExpired:
                self.proc.kill()

        results = []
        for line in raw.strip().splitlines():
            line = line.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
                if "result" in obj or "error" in obj:
                    results.append(obj)
            except json.JSONDecodeError:
                continue
        return results

    def cleanup(self):
        if self.proc and self.proc.poll() is None:
            self.proc.kill()


# --- MC Screen Navigator (uses mod input system) ---
class McNavigator:
    """
    Navigates MC GUI screens using mod's click/press_key/type_text tools.
    Coordinates are relative to MC window (default 854x480).
    """

    # Standard MC GUI button positions (default window scale: GUI scale Normal)
    # These are approximate center points of common buttons
    BTNS = {
        "singleplayer":      (427, 228),   # Title: Singleplayer
        "multiplayer":       (427, 282),   # Title: Multiplayer
        "play_selected":     (427, 350),   # Select World: Play Selected World
        "create_new":        (427, 300),   # Select World: Create New World
        "create_world":      (427, 380),   # Create World: Create New World
        "more_options":      (200, 180),   # Create World: More World Options...
        "superflat_toggle":  (280, 140),   # World Options: Superflat checkbox area
        "world_name_field":  (427, 165),   # Create World: World Name field
        "confirm_name":      (427, 205),   # Create World: (after typing name, somewhere safe)
        "back":              (400, 235),   # Generic back button (bottom-left-ish)
        "quit_save":         (427, 350),   # Pause: Save and Quit
        "title_back":        (427, 350),   # Various "Back to Title" buttons
        "close_gui":         (0, 0),      # Press ESC
    }

    def __init__(self, client: McpClient):
        self.client = client
        self.wait_sec = 1.5

    def _click(self, x: int, y: int, btn: str = "left"):
        r = self.client.call_tool("click", {"x": x, "y": y, "button": btn}, timeout=8)
        time.sleep(0.3)
        return r

    def _key(self, key: str, hold: float = 0.05):
        r = self.client.call_tool("press_key", {"key": key, "hold_seconds": hold}, timeout=8)
        time.sleep(0.2)
        return r

    def _type(self, text: str, enter: bool = False):
        r = self.client.call_tool("type_text", {"text": text, "press_enter": enter}, timeout=8)
        time.sleep(0.3)
        return r

    def _hotkey(self, keys: list):
        r = self.client.call_tool("hotkey", {"keys": keys}, timeout=8)
        time.sleep(0.3)
        return r

    def _esc(self):
        return self._key("escape")

    def _screenshot(self, path: str = None) -> dict:
        args = {}
        if path:
            args["save_path"] = str(path)
        return self.client.call_tool("screenshot", args, timeout=15)

    def _wait_and_click(self, label: str, x: int, y: int, wait_before: float = 1.0):
        """Wait, then click."""
        time.sleep(wait_before)
        log(f"  >> Clicking {label} at ({x},{y})", Y)
        return self._click(x, y)

    def navigate_to_singleplayer(self):
        """Title screen → Singleplayer button."""
        log("  >> Navigate: Title → Singleplayer", Y)
        self._wait_and_click("Singleplayer", *self.BTNS["singleplayer"], 2.0)

    def select_or_create_world(self, world_name: str = "MCP_Test_World",
                                create_new: bool = False,
                                superflat: bool = True,
                                seed: str = "mcp-test-seed"):
        """
        From singleplayer menu, either select existing world or create new one.
        """
        if create_new:
            return self._create_world(world_name, superflat, seed)
        else:
            return self._select_existing_world(world_name)

    def _create_world(self, name: str, superflat: bool, seed: str):
        """Create New World flow."""
        log(f"  >> Creating world: {name} (superflat={superflat})", Y)
        self._wait_and_click("Create New World", *self.BTNS["create_new"], 2.0)

        # Type world name
        time.sleep(1)
        self._click(*self.BTNS["world_name_field"])
        time.sleep(0.5)
        self._type(name)

        # Open More World Options for superflat
        time.sleep(0.5)
        self._click(*self.BTNS["more_options"])
        time.sleep(1.5)

        if superflat:
            log("  >> Enabling superflat...", Y)
            # Click the world type dropdown/button area - need to find "Superflat"
            # In MC create world, "World Type" is in more options
            # Try clicking around where the type selector usually is
            self._click(300, 155)  # approximate world type area
            time.sleep(0.8)
            # Scroll/click to find superflat option
            self._scroll(-5)
            time.sleep(0.5)

        # Click Done / back from more options
        self._esc()
        time.sleep(0.5)

        # Click "Create New World"
        log("  >> Confirming world creation...", Y)
        self._wait_and_click("Create New World confirm", *self.BTNS["create_world"], 1.0)
        return True

    def _select_existing_world(self, world_name: str):
        """Select existing world from list and play."""
        log(f"  >> Looking for world: {world_name}", Y)
        # Already at singleplayer world list after clicking singleplayer
        time.sleep(1.5)
        # Just click Play Selected World (selects top/last-used world)
        self._wait_and_click("Play Selected World", *self.BTNS["play_selected"], 1.0)
        return True

    def _scroll(self, clicks: int):
        self.client.call_tool("scroll", {"clicks": clicks}, timeout=8)
        time.sleep(0.3)


# --- Test Runner ---
class TestRunner:
    def __init__(self, port: int, skip_launch: bool, timeout: int,
                 world_name: str, create_world: bool, superflat: bool):
        self.port = port
        self.skip_launch = skip_launch
        self.timeout = timeout
        self.world_name = world_name
        self.create_world = create_world
        self.superflat = superflat
        self.client = McpClient(port)
        self.nav = McNavigator(self.client)
        self.mc_process = None
        self.total = 0
        self.passed = 0
        self.failed = 0

    def _ok(self, cond, good, bad):
        self.total += 1
        if cond:
            self.passed += 1
            log(f"  PASS: {good}", G)
        else:
            self.failed += 1
            log(f"  FAIL: {bad}", R)

    def _text(self, resp) -> str:
        if not resp:
            return "(no response)"
        content = resp.get("result", {}).get("content", [])
        if content:
            return content[0].get("text", "")
        err = resp.get("error", {})
        return err.get("message", str(resp.get("result", {})))

    def build_jar(self):
        log("[Build] Checking JAR...", M)
        if not JAR_PATH.exists():
            log("Building shadowJar...", C)
            r = subprocess.run([self._gw(), "shadowJar", "--quiet"],
                              cwd=str(PROJECT_ROOT), capture_output=True, timeout=120)
            if r.returncode != 0:
                log(f"Build failed: {r.stderr.decode()}", R)
                sys.exit(1)
        log(f"  PASS: JAR ready ({JAR_PATH.stat().st_size // 1024}KB)", G)
        self.total += 1; self.passed += 1

    def launch_mc(self):
        if self.skip_launch:
            log("[Launch] Skipped (--no-launch)", D)
            return

        log(f"[Launch] Starting Minecraft (port={self.port})...", M)
        mc_dir = Path.home() / ".mcbbs-memorial"
        mc_dir.mkdir(parents=True, exist_ok=True)
        env = os.environ.copy()
        env["MC_MCP_SERVER"] = f"ws://127.0.0.1:{self.port}"

        # MC must launch from neoforge-dev/ where NeoForge MDK lives
        mc_cwd = PROJECT_ROOT / "neoforge-dev"

        is_win = platform.system() == "Windows"
        gw = "gradlew.bat" if is_win else "./gradlew"
        if is_win:
            cmd = ["cmd", "/c", "start", "Minecraft MCP Test",
                  gw, "runClient",
                  "-Porg.gradle.jvmargs=-Xmx4G", f"-PmcDir={mc_dir}"]
        else:
            cmd = [gw, "runClient",
                  "-Porg.gradle.jvmargs=-Xmx4G", f"-PmcDir={mc_dir}"]

        flags = subprocess.CREATE_NEW_CONSOLE if is_win else 0
        self.mc_process = subprocess.Popen(cmd, cwd=str(mc_cwd), env=env,
                                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                          creationflags=flags)
        log(f"  MC launched (pid={self.mc_process.pid}), waiting for window...", Y)

    def wait_for_mod(self, timeout: int = 90) -> bool:
        log(f"[Connect] Waiting for mod WebSocket (timeout={timeout}s)...", M)
        deadline = time.time() + timeout
        while time.time() < deadline:
            r = self.client.call_tool("get_window_info", {}, timeout=5)
            t = self._text(r)
            if "true" in t.lower():
                log("  PASS: Mod connected!", G)
                self.total += 1; self.passed += 1
                return True
            time.sleep(2)
        log("  FAIL: Mod did not connect in time", R)
        self.total += 1; self.failed += 1
        return False

    def phase_init(self):
        log("\n[Phase 1] Initialize MCP", M)
        r = self.client.call("initialize", {})
        self._ok("result" in r and r["result"].get("protocolVersion"),
                 f"Protocol: {r['result']['protocolVersion']}",
                 f"Init failed: {str(r)[:100]}")

        r2 = self.client.call("tools/list")
        tools = r2.get("result", {}).get("tools", [])
        names = [t["name"] for t in tools]
        names_str = ", ".join(names[:6]) + ("..." if len(names) > 6 else "")
        self._ok(len(tools) >= 12,
                 f"Tools: {len(tools)} registered ({names_str})",
                 f"Only {len(tools)} tools")

    def phase_screenshot(self):
        log("\n[Phase 2] Screenshot Pipeline", M)
        SCREENSHOT_DIR.mkdir(exist_ok=True)
        ss_path = SCREENSHOT_DIR / "e2e_ingame.png"
        r = self.nav._screenshot(str(ss_path))
        t = self._text(r)
        has_img = any(c.get("type") == "image"
                       for c in r.get("result", {}).get("content", [])) if "result" in r else False
        self._ok(has_img, f"Screenshot captured ({ss_path.name})", f"Screenshot failed: {t[:100]}")

    def phase_commands(self):
        log("\n[Phase 3] In-Game Commands", M)
        cmds = [
            ("mctest",          {"command": "mctest"}),
            ("mcgive diamond",   {"command": "mcgive diamond"}),
            ("mcinfo",           {"command": "mcinfo"}),
            ("gamerule daylock", {"command": "gamerule doDayCycle false"}),
            ("time set day",     {"command": "time set 6000"}),
            ("give test items",  {"command": "mcgive test_wand"}),
            ("teleport up",      {"command": "mcteleport"}),
        ]
        for desc, args in cmds:
            r = self.client.call_tool("execute_command", args, timeout=10)
            t = self._text(r)
            ok = "Error" not in t and "not connected" not in t.lower()
            self._ok(ok, f"Cmd '{desc}': OK", f"Cmd '{desc}': {t[:80]}")
            time.sleep(0.3)

    def phase_input(self):
        log("\n[Phase 4] Input Simulation", M)
        inputs = [
            ("open inv (E)",       lambda: self.nav._key("e")),
            ("close inv (ESC)",    lambda: self.nav._esc()),
            ("type /mcinfo",       lambda: self.nav._type("/mcinfo")),
            ("send chat",          lambda: self.nav._key("enter")),
            ("scroll down",        lambda: self.client.call_tool("scroll", {"clicks": -3}, timeout=8)),
            ("F3 debug",           lambda: self.nav._hotkey(["f3"])),
            ("close F3 (ESC)",     lambda: self.nav._esc()),
        ]
        for desc, fn in inputs:
            try:
                r = fn()
                t = self._text(r)
                ok = "Error" not in t and "not connected" not in t.lower() and "timeout" not in t
                self._ok(ok, f"{desc}: OK", f"{desc}: {t[:80]}")
            except Exception as e:
                self._ok(False, f"{desc}: OK", f"{desc}: exception {e}")
            time.sleep(0.5)

    def phase_state(self):
        log("\n[Phase 5] State Queries", M)
        queries = [
            ("player_info", "get_player_info"),
            ("world_info",  "get_world_info"),
            ("ping",        "ping"),
        ]
        for desc, tool in queries:
            r = self.client.call_tool(tool, {}, timeout=10)
            t = self._text(r)
            valid_keys = {
                "player_info": ["position", "health", "dimension"],
                "world_info":  ["worldName", "dayTime", "difficulty"],
                "ping":        ["pong", "timestamp", "mod online"],
            }
            ok = any(k in t for k in valid_keys.get(desc, []))
            self._ok(ok, f"{desc}: valid data", f"{desc}: {t[:80]}")

    def phase_click_test(self):
        log("\n[Phase 6] Click & Interaction Test", M)
        ss_path = SCREENSHOT_DIR / "e2e_after_actions.png"
        r = self.nav._screenshot(str(ss_path))
        t = self._text(r)
        has_img = any(c.get("type") == "image"
                       for c in r.get("result", {}).get("content", [])) if "result" in r else False
        self._ok(has_img, f"Post-action screenshot saved", f"No screenshot: {t[:100]}")

    def phase_conn_health(self):
        log("\n[Phase 1b] Connection Health", M)
        r = self.client.call_tool("ping", {}, timeout=8)
        t = self._text(r)
        self._ok("pong" in t or "timestamp" in t or "mod online" in t,
                 f"Ping: responsive ({t[:60]})",
                 f"No ping: {t[:60]}")

    def summary(self):
        log("\n" + "=" * 50, C)
        log("  TEST SUMMARY", C)
        log("=" * 50, C)
        pct = (self.passed / self.total * 100) if self.total else 0
        clr = G if pct >= 90 else (Y if pct >= 50 else R)
        log(f"  Total:   {self.total}", W)
        log(f"  Passed:  {self.passed}", G)
        log(f"  Failed:  {self.failed}", R if self.failed else G)
        log(f"  Rate:    {pct:.0f}%", clr)
        log("=" * 50, C)
        return self.failed == 0

    @staticmethod
    def _gw():
        return "gradlew.bat" if platform.system() == "Windows" else "./gradlew"

    def cleanup(self):
        self.client.cleanup()
        if self.mc_process and self.mc_process.poll() is None:
            log("\n[Cleanup] Stopping MC...", Y)
            self.mc_process.terminate()
            try:
                self.mc_process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                self.mc_process.kill()


def main():
    import argparse
    p = argparse.ArgumentParser(description="Minecraft MCP E2E Test")
    p.add_argument("--port", type=int, default=9879)
    p.add_argument("--no-launch", action="store_true")
    p.add_argument("--timeout", type=int, default=180)
    p.add_argument("--world-name", type=str, default="MCP_Test_World")
    p.add_argument("--create-world", action="store_true", default=True,
                   help="Create new world (default)")
    p.add_argument("--join-world", action="store_true",
                   help="Join existing world instead of creating")
    p.add_argument("--superflat", action="store_true", default=True,
                   help="Use superflat for new world (default)")
    p.add_argument("--seed", type=str, default="mcp-e2e-test",
                   help="World seed (default: mcp-e2e-test)")
    args = p.parse_args()

    if args.join_world:
        args.create_world = False

    log("=" * 50, C)
    log("  Minecraft MCP E2E - Full Automation", C)
    log(f"  Port: {args.port} | World: {args.world_name}", C)
    log(f"  Create: {args.create_world} | Superflat: {args.superflat}", C)
    log("=" * 50, C)

    runner = TestRunner(args.port, args.no_launch, args.timeout,
                        args.world_name, args.create_world, args.superflat)

    signal.signal(signal.SIGINT, lambda s, f: (runner.cleanup(), sys.exit(130)))
    if hasattr(signal, "SIGTERM"):
        signal.signal(signal.SIGTERM, lambda s, f: (runner.cleanup(), sys.exit(143)))

    try:
        runner.build_jar()
        runner.launch_mc()

        if not args.no_launch:
            if not runner.wait_for_mod(args.timeout):
                log("\nMod never connected. Check MC console for errors.", R)
                runner.cleanup()
                sys.exit(1)

            # Auto-navigate into a world
            log("\n[Auto-Navigate] Entering game world...", M)
            runner.nav.navigate_to_singleplayer()
            time.sleep(2)
            runner.nav.select_or_create_world(
                args.world_name,
                create_new=args.create_world,
                superflat=args.superflat,
                seed=args.seed,
            )

            # Wait for world to load (this takes a while)
            log("\n[World Load] Waiting for world generation (up to 120s)...", M)
            time.sleep(5)  # initial wait for world gen to start

            # Poll player info until we get real data (means we're in-game)
            loaded = False
            for i in range(60):  # 60 * 2s = 120s max
                r = runner.client.call_tool("get_player_info", {}, timeout=8)
                t = runner._text(r)
                if "positionX" in t or "positionY" in t or "inGame" in t:
                    log(f"  PASS: Player in-world! (after ~{i*2}s)", G)
                    runner.total += 1; runner.passed += 1
                    loaded = True
                    break
                if i % 10 == 9:
                    log(f"  ... still loading ({i*2}s)", D)
                time.sleep(2)

            if not loaded:
                log("  WARN: World may still be loading, running tests anyway...", Y)
                runner.total += 1; runner.failed += 1
            time.sleep(3)  # extra settle time

        # Run all test phases
        runner.phase_init()
        runner.phase_conn_health()
        runner.phase_screenshot()
        runner.phase_commands()
        runner.phase_input()
        runner.phase_state()
        runner.phase_click_test()

        ok = runner.summary()
        runner.cleanup()
        sys.exit(0 if ok else 1)
    except Exception as e:
        log(f"\nFATAL: {e}", R)
        import traceback
        traceback.print_exc()
        runner.cleanup()
        sys.exit(2)


if __name__ == "__main__":
    main()

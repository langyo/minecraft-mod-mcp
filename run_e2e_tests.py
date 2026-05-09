#!/usr/bin/env python3
"""
Minecraft MCP End-to-End Integration Test
Launches MC with mcp-mod + test-example-mod, runs full test suite via WebSocket

Usage:
    python run_e2e_tests.py [--port 9879] [--no-launch] [--timeout 180]
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

# --- Colors for terminal ---
class C:
    G = "\033[92m"  # green
    R = "\033[91m"  # red
    Y = "\033[93m"  # yellow
    M = "\033[95m"  # magenta
    C = "\033[96m"  # cyan
    W = "\033[0m"   # reset


def log(msg: str, color: str = C.W):
    print(f"{color}{msg}{C.W}")


def log_test(msg: str):
    ts = time.strftime("%H:%M:%S")
    log(f"  [{ts}] {msg}", C.Y)


def log_pass(msg: str):
    log(f"  PASS: {msg}", C.G)


def log_fail(msg: str):
    log(f"  FAIL: {msg}", C.R)


def log_skip(msg: str):
    log(f"  SKIP: {msg}", "\033[90m")


# --- MCP Client ---
class McpClient:
    def __init__(self, port: int):
        self.port = port
        self.proc: Optional[subprocess.Popen] = None

    def send_batch(self, requests: list[dict], timeout: int = 15) -> list[dict]:
        """Send batch of JSON-RPC requests, return parsed responses."""
        payload = "\n".join(json.dumps(r) for r in requests)
        env = os.environ.copy()
        env["MC_MCP_WS_PORT"] = str(self.port)

        self.proc = subprocess.Popen(
            [self._java_cmd(), "-jar", str(JAR_PATH)],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            env=env,
            cwd=str(PROJECT_ROOT),
            creationflags=self._creation_flags(),
        )
        try:
            self.proc.stdin.write(payload.encode())
            self.proc.stdin.close()
            raw = ""
            start = time.time()
            while time.time() - start < timeout:
                try:
                    chunk = self.proc.stdout.read(65536)
                    if not chunk:
                        break
                    raw += chunk.decode("utf-8", errors="replace")
                except Exception:
                    break
                time.sleep(0.1)
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

    @staticmethod
    def _java_cmd() -> str:
        java = shutil.which("java")
        return java if java else "java"

    @staticmethod
    def _creation_flags() -> int:
        if platform.system() == "Windows":
            return subprocess.CREATE_NO_WINDOW
        return 0

    def cleanup(self):
        if self.proc and self.proc.poll() is None:
            self.proc.kill()


# --- Test Runner ---
class TestRunner:
    def __init__(self, port: int, skip_launch: bool, timeout: int):
        self.port = port
        self.skip_launch = skip_launch
        self.timeout = timeout
        self.client = McpClient(port)
        self.total = 0
        self.passed = 0
        self.failed = 0
        self.mc_process: Optional[subprocess.Popen] = None

    def _assert(self, condition: bool, msg_pass: str, msg_fail: str):
        self.total += 1
        if condition:
            log_pass(msg_pass)
            self.passed += 1
        else:
            log_fail(msg_fail)
            self.failed += 1

    def _find_response(self, results: list[dict], req_id: int) -> Optional[dict]:
        for r in results:
            if r.get("id") == req_id:
                return r
        return None

    def _get_text(self, resp: Optional[dict]) -> str:
        if not resp:
            return "(no response)"
        result = resp.get("result", {})
        content = result.get("content", [])
        if content and isinstance(content, list):
            return content[0].get("text", "")
        error = resp.get("error", {})
        return error.get("message", str(result))

    def build_jar(self):
        log("[Build] Checking MCP server jar...", C.M)
        if not JAR_PATH.exists():
            log("Building shadowJar...", C.C)
            r = subprocess.run(
                [self._gradlew(), "shadowJar", "--quiet"],
                cwd=str(PROJECT_ROOT),
                capture_output=True,
                timeout=120,
            )
            if r.returncode != 0:
                log_fail(f"Build failed: {r.stderr.decode()}")
                sys.exit(1)
        log_pass(f"JAR ready: {JAR_PATH.name}")

    def launch_mc(self):
        if self.skip_launch:
            log_skip("Skipping MC launch (--no-launch)")
            return

        log("[Launch] Starting Minecraft via gradle...", C.M)
        mc_dir = Path.home() / ".mcbbs-memorial"
        env = os.environ.copy()
        env["MC_MCP_SERVER"] = f"ws://127.0.0.1:{self.port}"

        is_win = platform.system() == "Windows"
        if is_win:
            cmd = [
                "cmd", "/c", "start", "Minecraft",
                "gradlew.bat", "runClient",
                f"-Porg.gradle.jvmargs=-Xmx4G",
                f"-PmcDir={mc_dir}",
            ]
        else:
            cmd = [
                self._gradlew(), "runClient",
                f"-Porg.gradle.jvmargs=-Xmx4G",
                f"-PmcDir={mc_dir}",
            ]

        self.mc_process = subprocess.Popen(
            cmd,
            cwd=str(PROJECT_ROOT),
            env=env,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            creationflags=subprocess.CREATE_NEW_CONSOLE if is_win else 0,
        )
        log(f"MC process started (pid={self.mc_process.pid})")

    def wait_for_connection(self, timeout: int = 60) -> bool:
        log(f"[Wait] Waiting for mod WS connection (timeout={timeout}s)...", C.M)
        deadline = time.time() + timeout
        while time.time() < deadline:
            results = self.client.send_batch([
                {"jsonrpc": "2.0", "id": 0, "method": "tools/call", "params": {"name": "get_window_info", "arguments": {}}}
            ], timeout=5)
            text = self._get_text(self._find_response(results, 0))
            if "mcConnected" in text and "true" in text:
                log_pass("Mod connected via WebSocket")
                return True
            time.sleep(2)
        log_fail("Timeout waiting for connection")
        return False

    def phase_init(self):
        log("\n[Phase 1] MCP Server Initialization", C.M)
        results = self.client.send_batch([
            {"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {}},
            {"jsonrpc": "2.0", "id": 2, "method": "tools/list"},
        ])

        r1 = self._find_response(results, 1)
        self._assert(
            r1 and "result" in r1 and r1["result"].get("protocolVersion"),
            f"MCP protocol: {r1['result']['protocolVersion']}",
            f"Init failed: {json.dumps(r1)[:100]}"
        )

        r2 = self._find_response(results, 2)
        tools = r2.get("result", {}).get("tools", []) if r2 else []
        tool_names = [t.get("name", "?") for t in tools]
        names_str = ", ".join(tool_names[:5]) + ("..." if len(tool_names) > 5 else "")
        self._assert(len(tools) >= 10, f"Tools registered: {len(tools)} ({names_str})", f"Only {len(tools)} tools")

    def phase_connection(self):
        log("\n[Phase 2] Connection Status", C.M)
        results = self.client.send_batch([
            {"jsonrpc": "2.0", "id": 10, "method": "tools/call", "params": {"name": "get_window_info", "arguments": {}}},
            {"jsonrpc": "2.0", "id": 11, "method": "tools/call", "params": {"name": "ping", "arguments": {}}},
        ])
        text10 = self._get_text(self._find_response(results, 10))
        text11 = self._get_text(self._find_response(results, 11))
        self._assert("mcConnected" in text10, f"WS status: {text10[:80]}", f"Not connected: {text10}")
        self._assert("mcConnected" in text11 or "pong" in text11 or "timestamp" in text11, f"Ping: {text11[:60]}", f"No ping response")

    def phase_screenshot(self):
        log("\n[Phase 3] Screenshot Pipeline", C.M)
        SCREENSHOT_DIR.mkdir(parents=True, exist_ok=True)
        ss_path = SCREENSHOT_DIR / "e2e_test.png"
        results = self.client.send_batch([
            {"jsonrpc": "2.0", "id": 20, "method": "tools/call", "params": {
                "name": "screenshot", "arguments": {"save_path": str(ss_path)}
            }},
        ])
        text = self._get_text(self._find_response(results, 20))
        has_image = any(
            c.get("type") == "image" for c in
            self._find_response(results, 20).get("result", {}).get("content", [])
        ) if self._find_response(results, 20) else False
        self._assert(has_image, f"Screenshot captured (saved to {ss_path.name})", f"Failed: {text}")

    def phase_commands(self):
        log("\n[Phase 4] In-Game Commands", C.M)
        cmds = [
            ("30", "mctest", "mctest help"),
            ("31", "mcgive diamond", "give diamond"),
            ("32", "mcinfo", "player info"),
            ("33", "gamerule doDayCycle false", "gamerule set"),
            ("34", "time set 6000", "time set day"),
        ]
        reqs = [
            {"jsonrpc": "2.0", "id": int(cid), "method": "tools/call", "params": {
                "name": "execute_command", "arguments": {"command": cmd}
            }}
            for cid, cmd, _ in cmds
        ]
        results = self.client.send_batch(reqs)
        for cid, _, desc in cmds:
            text = self._get_text(self._find_response(results, int(cid)))
            ok = "Error" not in text and "error" not in text and "fail" not in text
            self._assert(ok, f"Command '{desc}': OK", f"Command '{desc}': {text[:80]}")

    def phase_input(self):
        log("\n[Phase 5] Input Simulation (via mod)", C.M)
        inputs = [
            ("40", "press_key(e)", {"name": "press_key", "arguments": {"key": "e"}}),
            ("41", "press_key(esc)", {"name": "press_key", "arguments": {"key": "escape"}}),
            ("42", "type_text(/mcinfo)", {"name": "type_text", "arguments": {"text": "/mcinfo"}}),
            ("43", "scroll(-3)", {"name": "scroll", "arguments": {"clicks": -3}}),
            ("44", "hotkey(F3)", {"name": "hotkey", "arguments": {"keys": ["f3"]}}),
        ]
        reqs = [
            {"jsonrpc": "2.0", "id": int(cid), "method": "tools/call", "params": params}
            for cid, _, params in inputs
        ]
        results = self.client.send_batch(reqs)
        time.sleep(2)
        for cid, desc, _ in inputs:
            text = self._get_text(self._find_response(results, int(cid)))
            ok = "Error" not in text and "error" not in text and "fail" not in text and "timeout" not in text
            self._assert(ok, f"{desc}: OK", f"{desc}: {text[:80]}")

    def phase_state(self):
        log("\n[Phase 6] State Queries", C.M)
        queries = [
            ("50", "get_player_info", {"name": "get_player_info", "arguments": {}}),
            ("51", "get_world_info", {"name": "get_world_info", "arguments": {}}),
            ("52", "ping", {"name": "ping", "arguments": {}}),
        ]
        reqs = [
            {"jsonrpc": "2.0", "id": int(cid), "method": "tools/call", "params": params}
            for cid, _, params in queries
        ]
        results = self.client.send_batch(reqs)
        for cid, _, _ in queries:
            t = self._get_text(self._find_response(results, cid))
            checks = {
                50: ("position" in t or "health" in t or "Error" not in t, "Player info valid"),
                51: ("worldName" in t or "dayTime" in t or "Error" not in t, "World info valid"),
                52: ("pong" in t or "timestamp" in t or "mod online" in t, "Ping responsive"),
            }
        for cid, (valid, desc) in checks.items():
            tt = self._get_text(self._find_response(results, cid))
            self._assert(valid, desc, f"{desc}: {tt[:60]}")

    def summary(self):
        log("\n" + "=" * 50, C.C)
        log("  TEST SUMMARY", C.C)
        log("=" * 50, C.C)
        rate = (self.passed / self.total * 100) if self.total > 0 else 0
        color = C.G if rate >= 90 else (C.Y if rate >= 50 else C.R)
        log(f"  Total:  {self.total}", C.W)
        log(f"  Passed: {self.passed}", C.G)
        log(f"  Failed: {self.failed}", C.R if self.failed > 0 else C.G)
        log(f"  Rate:   {rate:.0f}%", color)
        log("=" * 50, C.C)
        return self.failed == 0

    @staticmethod
    def _gradlew() -> str:
        return "gradlew.bat" if platform.system() == "Windows" else "./gradlew"

    def cleanup(self):
        self.client.cleanup()
        if self.mc_process and self.mc_process.poll() is None:
            log("\n[Cleanup] Terminating MC process...", C.Y)
            self.mc_process.terminate()
            try:
                self.mc_process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                self.mc_process.kill()


def main():
    import argparse
    parser = argparse.ArgumentParser(description="Minecraft MCP E2E Test Suite")
    parser.add_argument("--port", type=int, default=9879, help="MCP WS port (default: 9879)")
    parser.add_argument("--no-launch", action="store_true", help="Skip launching MC (assume running)")
    parser.add_argument("--timeout", type=int, default=180, help="Max wait seconds for MC connection")
    args = parser.parse_args()

    log("=" * 50, C.C)
    log("  Minecraft MCP E2E Test Suite", C.C)
    log("  Mods: minecraft-mcp.langyo.xyz + test-example", C.C)
    log("=" * 50, C.C)

    runner = TestRunner(args.port, args.no_launch, args.timeout)

    def sigint_handler(sig, frame):
        runner.cleanup()
        sys.exit(130)

    signal.signal(signal.SIGINT, sigint_handler)

    try:
        runner.build_jar()
        runner.launch_mc()

        if not args.no_launch:
            if not runner.wait_for_connection(args.timeout):
                log("\nMC not connected. Run with game loaded, or without --no-launch.", C.R)
                runner.cleanup()
                sys.exit(1)

        runner.phase_init()
        runner.phase_connection()
        runner.phase_screenshot()
        runner.phase_commands()
        runner.phase_input()
        runner.phase_state()

        ok = runner.summary()
        runner.cleanup()
        sys.exit(0 if ok else 1)
    except Exception as e:
        log(f"\nFATAL: {e}", C.R)
        import traceback
        traceback.print_exc()
        runner.cleanup()
        sys.exit(2)


if __name__ == "__main__":
    main()

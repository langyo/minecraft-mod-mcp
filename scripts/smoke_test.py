"""Smoke test: start MCP server, launch MC with mod, verify WS communication.

Usage:
  python scripts/smoke_test.py 1.21.7
  python scripts/smoke_test.py 1.21.7 --loader forge
  python scripts/smoke_test.py 1.21.7 --no-launch   # assume game already running
"""

import argparse
import asyncio
import base64
import json
import os
import shutil
import subprocess
import sys
import time
import threading

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from version_config import ALL_VERSIONS, BASE_DIR, MODS_DIR, get_loaders

MCP_SERVER_JAR = os.path.join(BASE_DIR, "build", "libs", "mcp-server-0.1.0.jar")
SCREENSHOTS_DIR = os.path.join(BASE_DIR, "screenshots")
WS_PORT = 9876


def find_mod_jar(mc, loader):
    mod_dir = os.path.join(MODS_DIR, mc, loader)
    libs_dir = os.path.join(mod_dir, "build", "libs")
    if not os.path.isdir(libs_dir):
        return None
    for f in os.listdir(libs_dir):
        if f.endswith(".jar") and "mcp" in f.lower():
            return os.path.join(libs_dir, f)
    for f in os.listdir(libs_dir):
        if f.endswith(".jar"):
            return os.path.join(libs_dir, f)
    return None


def find_mc_dir():
    mc_dir = os.environ.get("MC_RUN_DIR")
    if mc_dir:
        return mc_dir
    if sys.platform == "win32":
        appdata = os.environ.get("APPDATA", "")
        if appdata:
            return os.path.join(appdata, ".minecraft")
    return os.path.expanduser("~/.minecraft")


def find_version_dir(mc_dir, mc, loader="forge"):
    versions_dir = os.path.join(mc_dir, "versions")
    if not os.path.isdir(versions_dir):
        return None
    candidates = []
    for d in os.listdir(versions_dir):
        dl = d.lower()
        if loader == "forge" and ("forge" in dl) and mc in d:
            candidates.append(d)
        elif loader == "neoforge" and ("neoforge" in dl or "neo-forge" in dl) and mc in d:
            candidates.append(d)
        elif loader == "fabric" and "fabric" in dl and mc in d:
            candidates.append(d)
    if candidates:
        candidates.sort(key=len, reverse=True)
        return os.path.join(versions_dir, candidates[0], candidates[0] + ".json")
    return None


def find_java():
    for p in [
        os.environ.get("JAVA_HOME", ""),
        r"C:\Program Files\Amazon Corretto\jdk21.0.8_9",
        r"C:\Program Files\Java\jdk-21",
    ]:
        if not p:
            continue
        exe = os.path.join(p, "bin", "java.exe" if sys.platform == "win32" else "java")
        if os.path.isfile(exe):
            return exe
    return "java"


class McpServer:
    def __init__(self):
        self.proc = None
        self.reader_thread = None
        self.responses = {}
        self.lock = threading.Lock()
        self.req_id = 0

    def start(self):
        if not os.path.isfile(MCP_SERVER_JAR):
            print("[ERROR] MCP server jar not found. Run: gradlew shadowJar")
            return False
        java = find_java()
        cmd = [java, "-jar", MCP_SERVER_JAR]
        env = os.environ.copy()
        env["MC_MCP_WS_PORT"] = str(WS_PORT)
        self.proc = subprocess.Popen(
            cmd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            env=env,
            cwd=BASE_DIR,
        )
        self.reader_thread = threading.Thread(target=self._read_stdout, daemon=True)
        self.reader_thread.start()
        self._stderr_thread = threading.Thread(target=self._read_stderr, daemon=True)
        self._stderr_thread.start()
        time.sleep(1)
        if self.proc.poll() is not None:
            print(f"[ERROR] MCP server exited with code {self.proc.returncode}")
            return False
        print(f"[OK] MCP server started (pid={self.proc.pid}) on ws://127.0.0.1:{WS_PORT}")
        return True

    def _read_stdout(self):
        for line in self.proc.stdout:
            try:
                line = line.decode("utf-8", errors="replace").strip()
            except Exception:
                line = str(line).strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
                rid = obj.get("id")
                if rid is not None:
                    with self.lock:
                        self.responses[str(rid)] = obj
            except Exception:
                pass
            print(f"  [MCP-OUT] {line[:200]}")

    def _read_stderr(self):
        for line in self.proc.stderr:
            try:
                line = line.decode("utf-8", errors="replace").strip()
            except Exception:
                line = str(line).strip()
            if line:
                print(f"  [MCP-ERR] {line[:200]}")

    def send_command(self, method, params=None, timeout=10):
        with self.lock:
            self.req_id += 1
            rid = self.req_id
        req = {"jsonrpc": "2.0", "method": method, "params": params or {}, "id": rid}
        raw = json.dumps(req) + "\n"
        try:
            self.proc.stdin.write(raw.encode("utf-8"))
            self.proc.stdin.flush()
        except Exception as e:
            return {"error": f"write failed: {e}"}
        deadline = time.time() + timeout
        while time.time() < deadline:
            with self.lock:
                resp = self.responses.pop(str(rid), None)
            if resp:
                return resp
            time.sleep(0.1)
        return {"error": "timeout"}

    def stop(self):
        if self.proc:
            try:
                self.proc.terminate()
                self.proc.wait(timeout=5)
            except Exception:
                try:
                    self.proc.kill()
                except Exception:
                    pass
            print("[OK] MCP server stopped")


async def ws_test_direct():
    """Connect directly to the game mod's WS and send a ping."""
    import websockets

    uri = f"ws://127.0.0.1:{WS_PORT}"
    print(f"\n[TEST] Connecting to {uri} ...")
    try:
        async with websockets.connect(uri, open_timeout=5) as ws:
            print("[OK] Connected to MCP WS server!")
            ping_req = {
                "action": "ping",
                "params": {"requestId": "smoke-ping-1"},
            }
            await ws.send(json.dumps(ping_req))
            print(f"[TEST] Sent: {json.dumps(ping_req)}")
            try:
                resp = await asyncio.wait_for(ws.recv(), timeout=10)
                print(f"[OK] Response: {resp[:300]}")
                return True
            except asyncio.TimeoutError:
                print("[WARN] No response from mod (game may not have connected yet)")
                return False
    except Exception as e:
        print(f"[WARN] WS connect failed: {e}")
        return False


def launch_game(mc_dir, version_json_path, mod_jar_path, mc, loader):
    mods_dir = os.path.join(mc_dir, "mods")
    os.makedirs(mods_dir, exist_ok=True)
    mod_name = os.path.basename(mod_jar_path)
    dst = os.path.join(mods_dir, mod_name)
    if os.path.isfile(dst):
        os.remove(dst)
    shutil.copy2(mod_jar_path, dst)
    print(f"[OK] Copied mod: {mod_name} -> {mods_dir}")

    version_name = os.path.basename(os.path.dirname(version_json_path))

    from launch_mc import (
        merge_version_json, build_classpath, extract_natives,
        build_jvm_args, build_game_args, find_java as find_java_launch,
        download_libraries, ensure_version_jar,
    )

    vj = merge_version_json(version_name, mc_dir=mc_dir)
    download_libraries(vj, mc_dir=mc_dir)
    ensure_version_jar(vj, mc_dir=mc_dir)
    main_class = vj.get("mainClass", "")
    cp = build_classpath(vj, mc_dir=mc_dir)
    natives_dir = extract_natives(vj, mc_dir=mc_dir)
    java_ver = vj.get("javaVersion", {}).get("majorVersion", 21)
    java_exe = find_java_launch(java_ver)
    jvm_args = build_jvm_args(vj, natives_dir, mc_dir=mc_dir, java_exe=java_exe)
    game_args = build_game_args(vj, version_name, mc_dir=mc_dir)

    sep = ";" if sys.platform == "win32" else ":"
    cp_str = sep.join(cp)

    cmd = [java_exe, "-Xmx4G", "-Xms2G", f"-Dmcp.server=ws://127.0.0.1:{WS_PORT}"]
    cmd.extend(jvm_args)
    cmd.extend(["-cp", cp_str])
    cmd.append(main_class)
    cmd.extend(game_args)

    env = os.environ.copy()
    env["MC_MCP_SERVER"] = f"ws://127.0.0.1:{WS_PORT}"

    print(f"[LAUNCH] Starting Minecraft {version_name} (mainClass={main_class}) ...")
    proc = subprocess.Popen(cmd, env=env, cwd=mc_dir)
    print(f"[OK] Game launched (pid={proc.pid})")
    return proc


def main():
    parser = argparse.ArgumentParser(description="Smoke test MCP server + game mod")
    parser.add_argument("mc", help="MC version (e.g. 1.21.7)")
    parser.add_argument("--loader", default=None, help="Loader (forge/neoforge/fabric)")
    parser.add_argument("--no-launch", action="store_true", help="Don't launch game (assume running)")
    parser.add_argument("--no-server", action="store_true", help="Don't start MCP server (assume running)")
    parser.add_argument("--timeout", type=int, default=120, help="Wait timeout for game connection (seconds)")
    args = parser.parse_args()

    mc = args.mc
    info = ALL_VERSIONS.get(mc)
    if not info:
        print(f"[ERROR] Unknown MC version: {mc}")
        print(f"  Available: {', '.join(sorted(ALL_VERSIONS.keys()))}")
        sys.exit(1)

    loaders = get_loaders(mc)
    if not loaders:
        print(f"[ERROR] No loaders available for {mc}")
        sys.exit(1)
    loader = args.loader or loaders[0]
    if loader not in loaders:
        print(f"[ERROR] Loader '{loader}' not available for {mc}. Available: {loaders}")
        sys.exit(1)

    print(f"=== Smoke Test: {mc}/{loader} ===\n")

    mod_jar = find_mod_jar(mc, loader)
    if not mod_jar:
        print(f"[ERROR] No mod jar found for {mc}/{loader}. Build first.")
        sys.exit(1)
    print(f"[OK] Mod jar: {mod_jar}")

    mc_dir = find_mc_dir()
    print(f"[OK] MC dir: {mc_dir}")

    version_json = find_version_dir(mc_dir, mc, loader)
    if version_json:
        print(f"[OK] Version JSON: {version_json}")
    else:
        print(f"[WARN] No {loader} installation found for MC {mc} in {mc_dir}")
        print(f"  Please install {loader} for MC {mc} via the official launcher.")

    server = None
    if not args.no_server:
        print("\n--- Starting MCP Server ---")
        server = McpServer()
        if not server.start():
            sys.exit(1)

    if not args.no_launch and version_json:
        print("\n--- Launching Game ---")
        launch_game(mc_dir, version_json, mod_jar, mc, loader)

    print(f"\n--- Waiting for game to connect (timeout={args.timeout}s) ---")
    connected = False
    deadline = time.time() + args.timeout
    while time.time() < deadline:
        try:
            import asyncio
            connected = asyncio.run(ws_test_direct())
            if connected:
                break
        except Exception:
            pass
        remaining = int(deadline - time.time())
        if remaining > 0:
            print(f"  Waiting... ({remaining}s remaining)")
        time.sleep(5)

    if not connected:
        print("\n[WARN] Game did not connect via WS. Check if the game is running and the mod loaded.")
        print("  You can still test manually with the WS client below.")

    print("\n--- Running MCP Commands via stdin ---")
    if server and not args.no_server:
        tools_resp = server.send_command("tools/list", timeout=5)
        print(f"  tools/list: {json.dumps(tools_resp, indent=2)[:500]}")

        window_resp = server.send_command(
            "tools/call",
            {"name": "get_window_info"},
            timeout=5,
        )
        print(f"  get_window_info: {json.dumps(window_resp, indent=2)[:300]}")

        wait_resp = server.send_command(
            "tools/call",
            {"name": "wait_for_screen", "arguments": {"timeout_seconds": 30}},
            timeout=35,
        )
        print(f"  wait_for_screen: {json.dumps(wait_resp, indent=2)[:300]}")

        if connected or (wait_resp.get("result", {}).get("content", [{}])[0].get("text", "").find("connected=True") >= 0):
            print("\n--- Taking Screenshot ---")
            os.makedirs(SCREENSHOTS_DIR, exist_ok=True)
            ss_path = os.path.join(SCREENSHOTS_DIR, f"smoke_{mc}_{loader}.png")
            ss_resp = server.send_command(
                "tools/call",
                {"name": "screenshot", "arguments": {"save_path": ss_path}},
                timeout=15,
            )
            print(f"  screenshot: {json.dumps(ss_resp, indent=2)[:500]}")

            content = ss_resp.get("result", {}).get("content", [])
            for item in content:
                if item.get("type") == "image" and item.get("data"):
                    b64 = item["data"]
                    if b64.startswith("data:image/png;base64,"):
                        b64 = b64[len("data:image/png;base64,"):]
                    img_bytes = base64.b64decode(b64)
                    with open(ss_path, "wb") as f:
                        f.write(img_bytes)
                    print(f"  [OK] Screenshot saved to: {ss_path} ({len(img_bytes)} bytes)")

            print("\n--- Test Click ---")
            click_resp = server.send_command(
                "tools/call",
                {"name": "click", "arguments": {"x": 400, "y": 300}},
                timeout=5,
            )
            print(f"  click(400,300): {json.dumps(click_resp, indent=2)[:300]}")

            print("\n--- Test Ping ---")
            ping_resp = server.send_command(
                "tools/call",
                {"name": "ping"},
                timeout=5,
            )
            print(f"  ping: {json.dumps(ping_resp, indent=2)[:300]}")

    print("\n=== Smoke Test Complete ===")
    print(f"  MC version: {mc}")
    print(f"  Loader: {loader}")
    print(f"  Mod JAR: {mod_jar}")
    print(f"  WS connected: {connected}")
    print(f"  Screenshots: {SCREENSHOTS_DIR}")

    if server and not args.no_server:
        print("\nPress Ctrl+C to stop the MCP server...")
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            pass
        server.stop()


if __name__ == "__main__":
    main()

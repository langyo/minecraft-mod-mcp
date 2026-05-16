"""Per-version automated test runner for Minecraft MCP mods.

Usage:
  python scripts/test_version.py 1.21.7-forge-57.0.2
  python scripts/test_version.py 1.21.7-forge-57.0.2 --timeout 300
  python scripts/test_version.py --all           # test all installed versions
  python scripts/test_version.py --all -j 3      # parallel (careful: multiple MC instances)
"""

import argparse
import json
import os
import subprocess
import sys
import time
from pathlib import Path
from dataclasses import dataclass, field

ROOT = Path(__file__).resolve().parent.parent
SCRIPTS = ROOT / "scripts"
SERVER_JAR = ROOT / "build" / "libs" / "mcp-server-0.1.0.jar"
MC_DIR = Path(os.environ.get("APPDATA", os.path.expanduser("~"))) / ".minecraft"

sys.path.insert(0, str(SCRIPTS))
from version_config import ALL_VERSIONS, get_loaders, get_fg_era, get_jdk_home, find_jdk17


@dataclass
class TestResult:
    version: str
    loader: str
    build_ok: bool = False
    launch_ok: bool = False
    mod_connected: bool = False
    ping_ok: bool = False
    screenshot_method: str = ""
    window_title: str = ""
    errors: list = field(default_factory=list)
    duration: float = 0.0

    def to_dict(self):
        return {
            "version": self.version, "loader": self.loader,
            "build_ok": self.build_ok, "launch_ok": self.launch_ok,
            "mod_connected": self.mod_connected, "ping_ok": self.ping_ok,
            "screenshot_method": self.screenshot_method,
            "window_title": self.window_title,
            "errors": self.errors, "duration": round(self.duration, 1),
        }

    @property
    def pass_rate(self):
        total = 5
        passed = sum([self.build_ok, self.launch_ok, self.mod_connected,
                       self.ping_ok, bool(self.screenshot_method)])
        return f"{passed}/{total}"

    @property
    def status(self):
        return "PASS" if all([self.build_ok, self.launch_ok, self.mod_connected,
                               self.ping_ok]) else "FAIL"


def find_mod_jar(version: str, loader: str) -> Path:
    mod_dir = _resolve_mod_dir(version, loader)
    if not mod_dir.exists():
        return None
    jars = [j for j in mod_dir.glob("build/libs/*.jar")
            if not (j.name.endswith("-sources.jar") or j.name.endswith("-javadoc.jar"))]
    return jars[0] if jars else None


def install_mod(version: str, loader: str) -> bool:
    jar = find_mod_jar(version, loader)
    if not jar:
        return False
    mods_dir = MC_DIR / "mods"
    mods_dir.mkdir(exist_ok=True)
    dest = mods_dir / jar.name
    import shutil
    shutil.copy2(jar, dest)
    return True


def clear_mods():
    mods_dir = MC_DIR / "mods"
    if mods_dir.exists():
        for f in mods_dir.glob("*mcp*"):
            f.unlink()


def kill_all_java():
    import signal
    try:
        result = subprocess.run(["tasklist", "/FI", "IMAGENAME eq java.exe", "/FO", "CSV", "/NH"],
                                capture_output=True, text=True, timeout=5)
        for line in result.stdout.strip().split("\n"):
            if "java" in line.lower():
                parts = line.strip('"').split('","')
                if len(parts) >= 2:
                    try:
                        pid = int(parts[1])
                        os.kill(pid, signal.SIGTERM)
                    except (ValueError, ProcessLookupError, PermissionError):
                        pass
    except Exception:
        pass
    time.sleep(3)


_server_output_lines: list = []


def _start_mcp_server() -> subprocess.Popen:
    global _server_output_lines
    from launch_mc import find_java
    java = find_java(21)
    proc = subprocess.Popen(
        [java, "-jar", str(SERVER_JAR)],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, bufsize=1,
    )

    _server_output_lines = []

    def reader(p):
        for line in p.stdout:
            stripped = line.strip()
            _server_output_lines.append(stripped)
            print(f"  [SRV] {stripped}")

    __import__("threading").Thread(target=reader, args=(proc,), daemon=True).start()
    return proc


def _send_server_cmd(server: subprocess.Popen, tool: str, args: dict):
    if server.poll() is not None:
        return
    rid = int(time.time() * 1000)
    msg = json.dumps({"jsonrpc": "2.0", "id": rid, "method": "tools/call",
                      "params": {"name": tool, "arguments": args}})
    try:
        server.stdin.write(msg + "\n")
        server.stdin.flush()
        print(f"  [SRV] >>> {tool}")
    except Exception:
        pass
    return rid


def _drain_server_stdout(server: subprocess.Popen, timeout: float = 5.0) -> str:
    lines = []
    deadline = time.time() + timeout
    try:
        while time.time() < deadline:
            import select
            if hasattr(select, 'select'):
                r, _, _ = select.select([server.stdout], [], [], 0.5)
            else:
                r = [True]
            if r:
                line = server.stdout.readline()
                if line:
                    lines.append(line)
                else:
                    break
            else:
                break
    except Exception:
        pass
    return "".join(lines)


def _find_in_gradle_cache(group: str, artifact: str, version: str) -> str:
    cache_dir = os.path.join(os.path.expanduser("~"), ".gradle", "caches",
                              "modules-2", "files-2.1", group, artifact, version)
    if not os.path.isdir(cache_dir):
        return ""
    for root, dirs, files in os.walk(cache_dir):
        for f in files:
            if f.endswith(".jar"):
                return os.path.join(root, f)
    return ""


def _strip_module_info(jar_path: str) -> str:
    import zipfile
    stripped = jar_path.replace(".jar", "-nomodule.jar")
    if os.path.isfile(stripped):
        return stripped
    with zipfile.ZipFile(jar_path) as zin:
        with zipfile.ZipFile(stripped, 'w', zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                if os.path.basename(item.filename) != "module-info.class":
                    zout.writestr(item, zin.read(item.filename))
    return stripped


def _download_from_maven(group: str, artifact: str, ver: str, target: str):
    import urllib.request
    path = "/".join(group.split(".") + [artifact, ver, f"{artifact}-{ver}.jar"])
    url = f"https://repo1.maven.org/maven2/{path}"
    print(f"  Downloading {url}")
    os.makedirs(os.path.dirname(target), exist_ok=True)
    urllib.request.urlretrieve(url, target)


def _ensure_websocket_deps(mc_dir: Path) -> list:
    import shutil as _shutil
    jars = []
    deps = [
        ("org.java-websocket", "Java-WebSocket", "1.5.4"),
        ("org.slf4j", "slf4j-api", "2.0.7"),
        ("org.slf4j", "slf4j-simple", "2.0.7"),
    ]
    lib_base = mc_dir / "libraries"
    for group, artifact, ver in deps:
        rel = os.path.join(*group.split("."), artifact, ver, f"{artifact}-{ver}.jar")
        target = str(lib_base / rel)
        if not os.path.isfile(target):
            src = _find_in_gradle_cache(group, artifact, ver)
            if src:
                os.makedirs(os.path.dirname(target), exist_ok=True)
                _shutil.copy2(src, target)
        if not os.path.isfile(target):
            try:
                _download_from_maven(group, artifact, ver, target)
            except Exception as e:
                print(f"  WARNING: Failed to download {artifact}-{ver}: {e}")
        if os.path.isfile(target):
            stripped = _strip_module_info(target)
            jars.append(stripped)
    return jars


def _is_mc_jar(path: str, mc_dir: Path) -> bool:
    p = path.replace("\\", "/").lower()
    if "/versions/" in p and p.endswith(".jar"):
        return True
    if "/net/minecraftforge/forge/" in p and "-client.jar" in p:
        return True
    if "/net/minecraft/" in p:
        return True
    return False


def _start_mc(version: str) -> subprocess.Popen:
    from launch_mc import merge_version_json, build_classpath, extract_natives, build_jvm_args, build_game_args, find_java, download_libraries, ensure_version_jar, ensure_asset_index
    vj = merge_version_json(version, mc_dir=str(MC_DIR))
    download_libraries(vj, mc_dir=str(MC_DIR))
    ensure_version_jar(vj, mc_dir=str(MC_DIR))
    ensure_asset_index(vj, mc_dir=str(MC_DIR))
    cp = build_classpath(vj, mc_dir=str(MC_DIR))
    natives_dir = extract_natives(vj, mc_dir=str(MC_DIR))
    mc_key = _resolve_mc_key(version)
    era_config = get_fg_era(mc_key) if mc_key else None
    if era_config:
        java_ver = era_config.get("java", 8)
    else:
        java_ver = vj.get("javaVersion", {}).get("majorVersion", 21)
    java_exe = find_java(java_ver)
    jvm_args = build_jvm_args(vj, natives_dir, mc_dir=str(MC_DIR), java_exe=java_exe)
    game_args = build_game_args(vj, version, mc_dir=str(MC_DIR))

    uses_module_path = any(
        a in ("-p", "--module-path")
        for a in jvm_args
    )

    extra_jars = _ensure_websocket_deps(MC_DIR)

    cmd = [java_exe, "-Xmx4G", "-Xms1G", "-Dmcp.server=ws://127.0.0.1:9876"]
    cmd.extend(jvm_args)

    if uses_module_path:
        filtered = [p for p in cp + extra_jars
                    if not _is_mc_jar(p, MC_DIR)]
        if filtered:
            sep = ";" if sys.platform == "win32" else ":"
            cmd.extend(["-cp", sep.join(filtered)])
    else:
        cp.extend(extra_jars)
        sep = ";" if sys.platform == "win32" else ":"
        cp_str = sep.join(cp)
        cmd.extend(["-cp", cp_str])

    cmd.append(vj.get("mainClass"))
    cmd.extend(game_args)

    env = os.environ.copy()
    env["MC_MCP_SERVER"] = "ws://127.0.0.1:9876"

    stdout_log = MC_DIR / "mcp-launch-stdout.log"
    stderr_log = MC_DIR / "mcp-launch-stderr.log"
    fout = open(stdout_log, "w", encoding="utf-8", errors="replace")
    ferr = open(stderr_log, "w", encoding="utf-8", errors="replace")

    proc = subprocess.Popen(cmd, env=env, cwd=str(MC_DIR), stdout=fout, stderr=ferr)
    print(f"  MC started: pid={proc.pid}")
    return proc


def _resolve_mc_key(version: str) -> str:
    for mc_ver in sorted(ALL_VERSIONS.keys(), key=len, reverse=True):
        if version.startswith(mc_ver):
            return mc_ver
    return version


def _resolve_mod_dir(version: str, loader: str) -> Path:
    mc_key = _resolve_mc_key(version)
    return ROOT / "mods" / mc_key / loader


def test_single(version: str, loader: str, timeout: int = 300) -> TestResult:
    result = TestResult(version=version, loader=loader)
    start_time = time.time()

    print(f"\n{'='*60}", flush=True)
    print(f"TEST: {version}/{loader}", flush=True)
    print(f"{'='*60}", flush=True)

    # Phase 1: Build
    print(f"[1/5] Building mod...", flush=True)
    mod_dir = _resolve_mod_dir(version, loader)
    if not mod_dir.exists():
        result.errors.append("No mod project directory")
        result.duration = time.time() - start_time
        return result

    gradlew = str(mod_dir / "gradlew.bat") if sys.platform == "win32" else str(mod_dir / "gradlew")

    mc_key = _resolve_mc_key(version)
    mc_info = ALL_VERSIONS.get(mc_key, {})
    era_config = get_fg_era(mc_key)

    env = os.environ.copy()
    if era_config:
        java_ver = era_config.get("java", 8)
        jdk = get_jdk_home(java_ver)
        if not jdk and java_ver == 17:
            jdk = find_jdk17()
        if jdk:
            env["JAVA_HOME"] = jdk
    env.pop("JAVA_TOOL_OPTIONS", None)
    env["GRADLE_OPTS"] = "-Xmx3G"

    gp = str(mod_dir / "gradle.properties")
    with open(gp, "w") as f:
        f.write("org.gradle.jvmargs=-Xmx3G\n")

    try:
        build = subprocess.run(
            [gradlew, "jar", "--no-daemon"],
            cwd=str(mod_dir), capture_output=True, text=True, timeout=300,
            env=env,
        )
        if build.returncode == 0:
            result.build_ok = True
            print(f"  BUILD OK", flush=True)
        else:
            result.errors.append(f"Build failed: {build.returncode}")
            print(f"  BUILD FAILED", flush=True)
            result.duration = time.time() - start_time
            return result
    except subprocess.TimeoutExpired:
        result.errors.append("Build timeout")
        print(f"  BUILD TIMEOUT", flush=True)
        result.duration = time.time() - start_time
        return result

    # Phase 2: Install mod + launch
    print(f"[2/5] Installing mod and launching MC...")
    clear_mods()
    install_mod(version, loader)
    kill_all_java()

    server_proc = None
    mc_proc = None
    try:
        server_proc = _start_mcp_server()
        time.sleep(5)

        mc_proc = _start_mc(version)

        wait_time = min(timeout, 120)
        deadline = time.time() + wait_time
        launch_connected = False
        while time.time() < deadline:
            if mc_proc and mc_proc.poll() is not None:
                result.errors.append(f"MC exited early with code {mc_proc.returncode}")
                break
            stdout_log = MC_DIR / "mcp-launch-stdout.log"
            if stdout_log.exists():
                try:
                    content = stdout_log.read_text(encoding="utf-8", errors="replace")
                    if "[MCP-WS] Connected" in content:
                        launch_connected = True
                        break
                except Exception:
                    pass
            time.sleep(2)

        if mc_proc and mc_proc.poll() is None:
            result.launch_ok = True
        elif mc_proc is None:
            result.errors.append("Failed to start MC")
        elif launch_connected:
            result.launch_ok = True

        if launch_connected:
            result.mod_connected = True
            print(f"  MOD CONNECTED")

        # Give it a moment after connection
        time.sleep(5)

        # Phase 3: Check window
        print(f"[3/5] Checking window...")
        from test_daemon import WindowController
        wc = WindowController()
        info = wc.find_mc_window()
        if info:
            result.window_title = info[1]
            print(f"  Window: {info[1]}")

        # Phase 4: Ping via server stdin
        print(f"[4/5] Testing ping...")
        if result.mod_connected and server_proc:
            pre_count = len(_server_output_lines)
            _send_server_cmd(server_proc, "ping", {})
            time.sleep(3)
            new_lines = _server_output_lines[pre_count:]
            new_output = "\n".join(new_lines).lower()
            if "pong" in new_output:
                result.ping_ok = True
                print(f"  PING OK (server)")
            else:
                stdout_log = MC_DIR / "mcp-launch-stdout.log"
                if stdout_log.exists():
                    try:
                        content = stdout_log.read_text(encoding="utf-8", errors="replace")
                        if "pong" in content.lower():
                            result.ping_ok = True
                            print(f"  PING OK (game log)")
                    except Exception:
                        pass
            if not result.ping_ok:
                result.errors.append("Ping response not found")

        # Phase 5: Screenshot via MCP server -> game mod pipeline
        print(f"[5/5] Testing screenshot...")
        ss_dir = ROOT / "screenshots"
        ss_dir.mkdir(exist_ok=True)
        ss_path = str(ss_dir / f"test_{version}_{loader}_{int(time.time())}.png")
        if result.mod_connected and server_proc:
            _send_server_cmd(server_proc, "screenshot", {"save_path": ss_path})
            time.sleep(8)
            stdout_log = MC_DIR / "mcp-launch-stdout.log"
            if stdout_log.exists():
                try:
                    content = stdout_log.read_text(encoding="utf-8", errors="replace")
                    if "saved:" in content or "SCREENSHOT" in content:
                        result.screenshot_method = "mod_pipeline"
                        print(f"  Screenshot via mod pipeline")
                except Exception:
                    pass
        if not getattr(result, "screenshot_method", None):
            try:
                wc = WindowController()
                wc.focus_mc()
                time.sleep(1)
                data = wc.screenshot_window(save_path=ss_path)
                if data:
                    result.screenshot_method = "window_fallback"
                    print(f"  Screenshot saved (fallback): {ss_path}")
            except Exception as e:
                result.errors.append(f"Screenshot error: {e}")

        # Let MC run a bit more for observation
        if mc_proc and mc_proc.poll() is None:
            time.sleep(5)

    except Exception as e:
        result.errors.append(f"Launch error: {e}")
    finally:
        if mc_proc and mc_proc.poll() is None:
            mc_proc.kill()
        if server_proc:
            try:
                server_proc.stdin.close()
            except Exception:
                pass
            server_proc.kill()
        kill_all_java()

    result.duration = time.time() - start_time
    print(f"\n  RESULT: {result.status} ({result.pass_rate}) in {result.duration:.0f}s")
    if result.errors:
        for err in result.errors:
            print(f"  ERROR: {err}")

    return result


def _find_forge_version(mc: str, versions_dir: Path) -> str:
    if not versions_dir.exists():
        return ""
    for d in sorted(versions_dir.iterdir()):
        if not d.is_dir():
            continue
        name = d.name
        name_lower = name.lower()
        if name_lower.startswith(f"{mc}-forge") or name_lower.startswith(f"{mc}-neoforge"):
            vj = d / f"{name}.json"
            if vj.exists():
                return name
    return ""


def test_all_installed(max_parallel: int = 1):
    results = []
    installed_versions = []

    versions_dir = MC_DIR / "versions"
    if not versions_dir.exists():
        print("No .minecraft/versions directory found")
        return

    for mc, info in ALL_VERSIONS.items():
        for loader in get_loaders(mc):
            ver_name = _find_forge_version(mc, versions_dir)
            if not ver_name:
                continue
            if loader == "forge" and "neoforge" in ver_name.lower():
                continue
            if loader == "neoforge" and "neoforge" not in ver_name.lower():
                continue
            installed_versions.append((mc, loader, ver_name))

    print(f"Found {len(installed_versions)} installed version+loader combinations")
    for mc, loader, ver_name in installed_versions:
        print(f"  {ver_name} ({loader})")

    for mc, loader, ver_name in installed_versions:
        result = test_single(ver_name, loader)
        results.append(result)
        kill_all_java()
        time.sleep(5)

    print(f"\n\n{'='*60}")
    print(f"BATCH TEST RESULTS ({len(results)} versions)")
    print(f"{'='*60}")

    report = []
    for r in results:
        status_icon = "OK" if r.status == "PASS" else "FAIL"
        print(f"  [{status_icon}] {r.version:40s} {r.pass_rate}  {r.duration:.0f}s  errors={len(r.errors)}")
        report.append(r.to_dict())

    report_path = ROOT / "test_results.json"
    report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"\nReport saved to {report_path}")


def main():
    parser = argparse.ArgumentParser(description="Test MC MCP mods per version")
    parser.add_argument("version", nargs="?", help="Version to test (e.g. 1.21.7-forge-57.0.2)")
    parser.add_argument("--loader", default="forge", help="Loader (forge/neoforge/fabric)")
    parser.add_argument("--timeout", type=int, default=300, help="MC process timeout (seconds)")
    parser.add_argument("--all", action="store_true", help="Test all installed versions")
    parser.add_argument("-j", "--jobs", type=int, default=1, help="Parallel jobs (careful!)")
    args = parser.parse_args()

    if args.all:
        test_all_installed(max_parallel=args.jobs)
    elif args.version:
        result = test_single(args.version, args.loader, timeout=args.timeout)
        sys.exit(0 if result.status == "PASS" else 1)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()



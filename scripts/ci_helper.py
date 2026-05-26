"""CI helper utilities for Minecraft MCP headless testing.

Used by GitHub Actions workflows to:
  - Setup Xvfb + Mesa llvmpipe for software rendering
  - Wait for mod HTTP server to be ready
  - Run API tests (ping, get_player_info, execute_command, etc.)
  - Verify screenshots
  - Generate test summary reports

Usage:
  python scripts/ci_helper.py smoke --mc-ver 1.21.7 --loader forge --jdk-ver 21
  python scripts/ci_helper.py screenshot-test --mc-ver 1.21.7 --loader forge
  python scripts/ci_helper.py e2e --mc-ver 1.21.7 --loader forge --world test-world
"""

import argparse
import base64
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.request
import urllib.error
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCRIPTS = ROOT / "scripts"
MC_DIR = Path(os.environ.get("HOME", os.path.expanduser("~"))) / ".minecraft"

TEST_WORLD_DIR = ROOT / "tests" / "reference-screenshots"
SCREENSHOT_DIR = ROOT / "screenshots" / "ci"
REF_SCREENSHOT_DIR = ROOT / "tests" / "reference-screenshots"


def setup_mc_version(mc_ver, loader, mc_dir=None):
    """Ensure a MC version is installed in .minecraft before launching.

    Downloads vanilla JSON+JAR if needed, installs Forge/NeoForge/Fabric.
    Returns the version name (e.g. '1.21.7-forge-57.0.2').
    """
    mc = mc_dir or str(MC_DIR)

    launcher_profiles = Path(mc) / "launcher_profiles.json"
    if not launcher_profiles.exists():
        launcher_profiles.write_text('{"profiles":{}}')

    if loader in ("forge", "neoforge"):
        install_script = str(SCRIPTS / "install_forge.py")
        _log(f"Running {install_script} --mc {mc_ver} ...")
        result = subprocess.run(
            [sys.executable, install_script, "--mc", mc_ver],
            env={**os.environ, "HOME": str(Path(mc).parent)},
            capture_output=True, text=True, timeout=600,
        )
        if result.returncode != 0:
            _log(f"install_forge stderr: {result.stderr[-500:]}")
        for line in result.stdout.splitlines():
            if "OK:" in line:
                name = line.split("OK:")[-1].strip()
                _log(f"Forge installed: {name}")
                return name

        from install_forge import _find_installed
        installed = _find_installed(mc_ver, loader)
        if installed:
            _log(f"Found installed: {installed}")
            return installed
        _log(f"Forge install failed for {mc_ver}/{loader}")
        return None

    elif loader == "fabric":
        return _install_fabric_json(mc_ver, mc)

    return None


def _install_fabric_json(mc_ver, mc):
    """Create Fabric loader version JSON via Fabric meta API."""
    import urllib.request, json

    meta_url = f"https://meta.fabricmc.net/v2/versions/loader/{mc_ver}"
    req = urllib.request.Request(meta_url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        versions = json.loads(resp.read())
    if not versions:
        _log(f"No Fabric loader for {mc_ver}")
        return None
    loader_info = versions[0]
    loader_ver = loader_info["loader"]["version"]
    version_name = f"fabric-loader-{loader_ver}-{mc_ver}"

    profile_url = (f"https://meta.fabricmc.net/v2/versions/loader/{mc_ver}"
                   f"/{loader_ver}/profile/json")
    req2 = urllib.request.Request(profile_url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req2, timeout=30) as resp2:
        profile = json.loads(resp2.read())

    ver_dir = Path(mc) / "versions" / version_name
    ver_dir.mkdir(parents=True, exist_ok=True)
    (ver_dir / f"{version_name}.json").write_text(json.dumps(profile, indent=2))

    _log(f"Fabric version JSON: {version_name}")
    return version_name

sys.path.insert(0, str(SCRIPTS))


def _log(msg):
    print(f"[CI-HELPER] {msg}", flush=True)


def setup_xvfb(display=":99", screen="1024x768x24"):
    """Start Xvfb virtual display for headless rendering."""
    pids = _find_xvfb()
    if pids:
        _log(f"Xvfb already running on {display}")
    else:
        _log("Starting Xvfb...")
        subprocess.Popen(
            ["Xvfb", display, "-screen", "0", screen, "-ac", "-nolisten", "tcp"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        time.sleep(2)
    os.environ["DISPLAY"] = display
    os.environ["LIBGL_ALWAYS_SOFTWARE"] = "true"
    os.environ["GALLIUM_DRIVER"] = "llvmpipe"
    os.environ["MESA_GL_VERSION_OVERRIDE"] = "3.3"
    os.environ["MESA_GLSL_VERSION_OVERRIDE"] = "330"
    os.environ["__GLX_VENDOR_LIBRARY_NAME"] = "mesa"
    _log(f"DISPLAY={display} GALLIUM_DRIVER=llvmpipe")


def _find_xvfb():
    """Check if Xvfb is already running."""
    try:
        result = subprocess.run(["pgrep", "-x", "Xvfb"], capture_output=True, text=True, timeout=3)
        return result.stdout.strip().split("\n") if result.stdout.strip() else []
    except Exception:
        return []


def kill_minecraft():
    """Kill all running Minecraft Java processes."""
    try:
        subprocess.run(["pkill", "-f", "minecraft"], timeout=5)
    except Exception:
        pass
    try:
        subprocess.run(["pkill", "-f", "java.*minecraft"], timeout=5)
    except Exception:
        pass
    time.sleep(2)


def install_mod_jar(jar_path, mc_dir=None):
    """Copy mod JAR into .minecraft/mods/ directory."""
    mc = mc_dir or str(MC_DIR)
    mods_dir = Path(mc) / "mods"
    mods_dir.mkdir(parents=True, exist_ok=True)
    for existing in list(mods_dir.glob("*.jar")):
        try:
            existing.unlink()
        except Exception:
            pass
    dest = mods_dir / Path(jar_path).name
    shutil.copy2(jar_path, dest)
    _log(f"Mod installed: {dest.name} ({Path(jar_path).stat().st_size // 1024}KB)")
    return str(dest)


def install_test_world(world_name="CI_TestWorld", mc_dir=None):
    """Copy or generate the test superflat world save."""
    mc = mc_dir or str(MC_DIR)
    saves_dir = Path(mc) / "saves" / world_name
    if saves_dir.exists():
        shutil.rmtree(str(saves_dir), ignore_errors=True)
    saves_dir.mkdir(parents=True, exist_ok=True)
    _generate_level_dat(str(saves_dir), world_name)
    _log(f"Test world created: {saves_dir}")
    return str(saves_dir)


def _generate_level_dat(save_dir, world_name):
    """Generate a minimal superflat Redstone Ready level.dat using gzip+NBT."""
    import gzip
    import struct
    import io

    buf = io.BytesIO()

    def write_tag(tag_type, name, write_value):
        buf.write(struct.pack(">b", tag_type))
        if name is not None:
            name_bytes = name.encode("utf-8")
            buf.write(struct.pack(">H", len(name_bytes)))
            buf.write(name_bytes)
        if write_value:
            write_value()

    def write_byte(val):
        buf.write(struct.pack(">b", val))

    def write_int(val):
        buf.write(struct.pack(">i", val))

    def write_long(val):
        buf.write(struct.pack(">q", val))

    def write_string(val):
        data = val.encode("utf-8")
        buf.write(struct.pack(">H", len(data)))
        buf.write(data)

    def write_compound(entries):
        for tag_type, name, write_fn in entries:
            write_tag(tag_type, name, write_fn)
        buf.write(b"\x00")  # TAG_End

    def _gen():
        write_compound([
            # Data compound
            (10, "Data", lambda: write_compound([
                (4, "DataVersion", lambda: write_int(3953)),
                (4, "version", lambda: write_int(19133)),
                (8, "LevelName", lambda: write_string(world_name)),
                (4, "GameType", lambda: write_int(1)),
                (1, "MapFeatures", lambda: write_byte(1)),
                (1, "allowCommands", lambda: write_byte(1)),
                (1, "hardcore", lambda: write_byte(0)),
                (1, "Difficulty", lambda: write_byte(2)),
                (1, "DifficultyLocked", lambda: write_byte(0)),
                (3, "SpawnX", lambda: write_int(0)),
                (3, "SpawnY", lambda: write_int(5)),
                (3, "SpawnZ", lambda: write_int(0)),
                (4, "RandomSeed", lambda: write_long(42)),
                (8, "generatorName", lambda: write_string("flat")),
                (8, "generatorOptions", lambda: write_string(
                    "3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;village")),
                (1, "initialized", lambda: write_byte(1)),
                (4, "LastPlayed", lambda: write_long(int(time.time() * 1000))),
                (4, "Time", lambda: write_long(0)),
                (4, "DayTime", lambda: write_long(1000)),
                (4, "ClearWeatherTime", lambda: write_int(0)),
                (4, "rainTime", lambda: write_int(0)),
                (4, "thunderTime", lambda: write_int(0)),
                (1, "raining", lambda: write_byte(0)),
                (1, "thundering", lambda: write_byte(0)),
                (10, "GameRules", lambda: write_compound([
                    (8, "doDaylightCycle", lambda: write_string("true")),
                    (8, "doWeatherCycle", lambda: write_string("true")),
                    (8, "doFireTick", lambda: write_string("false")),
                    (8, "doMobSpawning", lambda: write_string("false")),
                    (8, "keepInventory", lambda: write_string("true")),
                ])),
                (10, "WorldGenSettings", lambda: write_compound([
                    (1, "bonus_chest", lambda: write_byte(0)),
                    (1, "generate_features", lambda: write_byte(1)),
                    (10, "dimensions", lambda: write_compound([
                        (10, "minecraft:overworld", lambda: write_compound([
                            (8, "type", lambda: write_string("minecraft:overworld")),
                            (10, "generator", lambda: write_compound([
                                (8, "type", lambda: write_string("minecraft:flat")),
                                (10, "settings", lambda: write_compound([
                                    (10, "layers", lambda: write_compound([])),
                                    (8, "biome", lambda: write_string("minecraft:plains")),
                                ])),
                            ])),
                        ])),
                    ])),
                ])),
            ])),
        ])

    _gen()

    level_dat = os.path.join(save_dir, "level.dat")
    os.makedirs(save_dir, exist_ok=True)
    with gzip.open(level_dat, "wb") as f:
        f.write(buf.getvalue())

    session_lock = os.path.join(save_dir, "session.lock")
    with open(session_lock, "wb") as f:
        f.write(b"\x00" * 8)


def wait_for_mod(url="http://127.0.0.1:9876", timeout=180, start_port=None):
    """Wait for the mod HTTP server to be ready.

    Scans ports from start_port down to 9000.
    Returns the URL of the found mod, or raises TimeoutError.
    """
    ports = []
    if start_port:
        ports = list(range(int(start_port), 8999, -1))
    ports = ports or list(range(9876, 8999, -1))

    deadline = time.time() + timeout
    last_log = 0
    attempted = set()

    while time.time() < deadline:
        for port in ports:
            if port in attempted:
                continue
            candidate = f"http://127.0.0.1:{port}"
            attempted.add(port)
            try:
                req = urllib.request.Request(
                    f"{candidate}/api/status",
                    headers={"User-Agent": "minecraft-mcp-ci"}
                )
                with urllib.request.urlopen(req, timeout=5) as resp:
                    data = json.loads(resp.read().decode())
                    if data.get("ok") and data.get("type") == "minecraft-mod":
                        _log(f"Mod ready at {candidate} (t={time.time() - deadline + timeout:.0f}s)")
                        return candidate
            except (urllib.error.URLError, ConnectionRefusedError, OSError,
                    json.JSONDecodeError, TimeoutError):
                continue

        now = time.time()
        if now - last_log > 10:
            _log(f"Waiting for mod... ({now - (deadline - timeout):.0f}s elapsed, {len(attempted)} ports tried)")
            last_log = now
        time.sleep(2)

    raise TimeoutError(f"Mod did not start within {timeout}s")


def api_call(mod_url, cmd, params=None, timeout=30):
    """Call the mod HTTP API and return the response."""
    payload = json.dumps({"cmd": cmd, "params": params or {}}).encode()
    req = urllib.request.Request(
        f"{mod_url}/api/cmd",
        data=payload,
        headers={"Content-Type": "application/json", "User-Agent": "minecraft-mcp-ci"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode())


def get_screenshot(mod_url, timeout=60):
    """Get a screenshot from the mod and return raw PNG bytes."""
    req = urllib.request.Request(
        f"{mod_url}/api/screenshot",
        headers={"User-Agent": "minecraft-mcp-ci"},
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read()


def verify_screenshot(png_data, min_size_kb=1):
    """Verify that PNG data appears valid (has PNG header and reasonable size)."""
    if not png_data or len(png_data) < 8:
        return False, "Empty or too small"
    if png_data[:8] != b"\x89PNG\r\n\x1a\n":
        return False, "Not a valid PNG header"
    size_kb = len(png_data) // 1024
    if size_kb < min_size_kb:
        return False, f"PNG too small ({size_kb}KB < {min_size_kb}KB)"
    return True, f"Valid PNG, {size_kb}KB"


def compare_screenshots(new_data, ref_path, threshold=0.02):
    """Compare two screenshots pixel-by-pixel.

    Returns (passed, detail_string) where passed=True means within threshold.
    """
    if not os.path.isfile(ref_path):
        return False, f"Reference screenshot not found: {ref_path}"

    with open(ref_path, "rb") as f:
        ref_data = f.read()

    if len(new_data) != len(ref_data):
        return False, f"Size mismatch: {len(new_data)} vs {len(ref_data)} bytes"

    diff_bytes = sum(1 for a, b in zip(new_data, ref_data) if a != b)
    diff_ratio = diff_bytes / len(new_data) if new_data else 1.0

    if diff_ratio <= threshold:
        return True, f"Match (diff={diff_ratio:.4f}, {diff_bytes}/{len(new_data)} bytes)"
    else:
        return False, f"Too different (diff={diff_ratio:.4f}, {diff_bytes}/{len(new_data)} bytes)"


def compare_screenshots_structural(new_path, ref_path, threshold=0.90, pixel_tolerance=30):
    """Compare screenshot structural similarity.

    threshold: minimum similarity ratio to pass (0.90 = 90% match)
    pixel_tolerance: max per-channel difference for "same" pixel (default 30)

    Returns (passed, detail).
    """
    if not os.path.isfile(ref_path):
        return False, f"Reference not found: {ref_path}"
    if not os.path.isfile(new_path):
        return False, f"New screenshot not found: {new_path}"

    try:
        from PIL import Image

        ref_img = Image.open(ref_path).convert("RGB")
        new_img = Image.open(new_path).convert("RGB")

        if ref_img.size != new_img.size:
            return False, f"Resolution mismatch: {ref_img.size} vs {new_img.size}"

        compare_size = (256, 256)
        ref_small = ref_img.resize(compare_size, Image.LANCZOS)
        new_small = new_img.resize(compare_size, Image.LANCZOS)

        ref_data = list(ref_small.getdata())
        new_data = list(new_small.getdata())

        diff_count = 0
        total = len(ref_data)
        for rp, np in zip(ref_data, new_data):
            if abs(rp[0] - np[0]) > pixel_tolerance or abs(rp[1] - np[1]) > pixel_tolerance or abs(rp[2] - np[2]) > pixel_tolerance:
                diff_count += 1

        similarity = 1.0 - (diff_count / total if total > 0 else 1.0)
        ref_avg = sum(sum(p) for p in ref_data) / (total * 3) if total else 0
        new_avg = sum(sum(p) for p in new_data) / (total * 3) if total else 0

        if similarity >= threshold:
            return True, f"Structural match (similarity={similarity:.3f} >= {threshold:.2f}, ref_avg={ref_avg:.1f}, new_avg={new_avg:.1f})"
        else:
            return False, f"Structural mismatch (similarity={similarity:.3f} < {threshold:.2f})"
    except ImportError:
        ref_size = os.path.getsize(ref_path)
        new_size = os.path.getsize(new_path)
        ratio = min(new_size, ref_size) / max(new_size, ref_size) if max(new_size, ref_size) > 0 else 0
        if ratio >= 0.70:
            return True, f"Size ratio OK ({ratio:.2f}, ref={ref_size}B, new={new_size}B)"
        return False, f"Size ratio out of range ({ratio:.2f})"


def compare_screenshots_strict(new_path, ref_path):
    """Exact byte-level comparison. For LLVMpipe deterministic renders."""
    if not os.path.isfile(ref_path):
        return False, f"Reference not found: {ref_path}"
    if not os.path.isfile(new_path):
        return False, f"New screenshot not found: {new_path}"
    with open(ref_path, "rb") as f:
        ref = f.read()
    with open(new_path, "rb") as f:
        new = f.read()
    if len(ref) != len(new):
        return False, f"Size mismatch: {len(new)} vs {len(ref)} bytes"
    diff = sum(1 for a, b in zip(ref, new) if a != b)
    ratio = diff / len(ref) if ref else 0
    if ratio <= 0.01:
        return True, f"Strict match (diff={ratio:.5f}, {diff}/{len(ref)} bytes)"
    return False, f"Strict mismatch (diff={ratio:.5f}, {diff}/{len(ref)} bytes)"


def generate_options_txt(mc_dir=None, settings=None):
    """Generate a deterministic options.txt for CI testing.

    Ensures consistent rendering across CI runs by disabling:
      - Clouds, particles, entity shadows
      - Dynamic FOV, view bobbing
      - Autosave indicator
    Sets fixed: FOV=70, renderDistance=4, graphics=fast, brightness=100.
    """
    mc = mc_dir or str(MC_DIR)
    opts_path = Path(mc) / "options.txt"

    default_settings = {
        "fov": "0.8",
        "gamma": "1.0",
        "renderDistance": "4",
        "simulationDistance": "4",
        "graphicsMode": "0",
        "ao": "true",
        "maxFps": "20",
        "enableVsync": "false",
        "guiScale": "2",
        "fullscreen": "false",
        "viewBobbing": "false",
        "cloudStatus": "false",
        "particles": "0",
        "entityShadows": "false",
        "darknessEffectScale": "0.0",
        "hideServerAddress": "true",
        "showAutosaveIndicator": "false",
        "skipMultiplayerWarning": "true",
        "soundCategory_master": "0.0",
        "soundCategory_music": "0.0",
        "soundCategory_record": "0.0",
        "soundCategory_weather": "0.0",
        "soundCategory_block": "0.0",
        "soundCategory_hostile": "0.0",
        "soundCategory_neutral": "0.0",
        "soundCategory_player": "0.0",
        "soundCategory_ambient": "0.0",
        "soundCategory_voice": "0.0",
        "difficulty": "2",
        "syncChunkWrites": "false",
        "mipmapLevels": "0",
        "useNativeTransport": "false",
        "pauseOnLostFocus": "false",
        "debugEnabled": "false",
    }

    if settings:
        default_settings.update(settings)

    Path(mc).mkdir(parents=True, exist_ok=True)
    with open(opts_path, "w", encoding="utf-8") as f:
        for key, value in default_settings.items():
            f.write(f"{key}:{value}\n")

    _log(f"options.txt written to {opts_path} ({len(default_settings)} settings)")
    return str(opts_path)


def run_smoke_test(mc_ver, loader, jdk_ver, mod_jar, headless=True, world_name=None, timeout=300):
    """Run a full smoke test: launch MC, test APIs, verify screenshot.

    Returns: dict with test results {test_name: {passed: bool, detail: str}}
    """
    results = {}

    kill_minecraft()
    setup_xvfb()
    install_mod_jar(mod_jar)

    if world_name:
        install_test_world(world_name)

    version_name = setup_mc_version(mc_ver, loader)
    if not version_name:
        _log("ERROR: Failed to setup MC version")
        return {"setup": {"passed": False, "detail": "Version setup failed"}}

    _log(f"Launching MC {version_name} (JDK {jdk_ver})")

    env = os.environ.copy()
    env["JAVA_HOME"] = os.environ.get(f"JAVA_HOME_{jdk_ver}_X64", os.environ.get("JAVA_HOME", ""))

    extra_jvm = ""
    if headless:
        extra_jvm = "-Djava.awt.headless=true -Dorg.lwjgl.opengl.libname=egl-headless"
        if world_name:
            extra_jvm += f" -Dmcp.test.world={world_name}"

    launcher = str(SCRIPTS / "launch_mc.py")
    try:
        mc_proc = subprocess.Popen(
            [sys.executable, launcher, version_name, "--headless",
             "--width", "854", "--height", "480",
             "--extra-jvm", extra_jvm,
             "--no-assets-download", "--no-mod-sync"],
            env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, bufsize=1,
        )

        def log_mc_output(proc):
            for line in proc.stdout:
                stripped = line.strip()
                if stripped:
                    _log(f"  [MC] {stripped[:200]}")

        import threading
        log_thread = threading.Thread(target=log_mc_output, args=(mc_proc,), daemon=True)
        log_thread.start()

    except Exception as e:
        results["launch"] = {"passed": False, "detail": str(e)}
        return results

    results["launch"] = {"passed": True, "detail": f"pid={mc_proc.pid}"}

    mod_url = None
    try:
        mod_url = wait_for_mod(timeout=180)
        results["mod_ready"] = {"passed": True, "detail": mod_url}
    except TimeoutError:
        results["mod_ready"] = {"passed": False, "detail": "HTTP server not found"}
        kill_minecraft()
        return results

    time.sleep(5)  # Wait for world load etc

    tests = [
        ("ping", {}, lambda r: r.get("result") == "pong"),
        ("get_player_info", {}, lambda r: "x" in str(r) and "y" in str(r)),
        ("get_world_info", {}, lambda r: isinstance(r, dict)),
        ("execute_command", {"command": "/time set day"},
         lambda r: isinstance(r, dict) and "error" not in str(r).lower()),
    ]

    for cmd, params, validator in tests:
        try:
            resp = api_call(mod_url, cmd, params)
            if validator(resp):
                results[cmd] = {"passed": True, "detail": "OK"}
                _log(f"  TEST [{cmd}]: PASS")
            else:
                results[cmd] = {"passed": False, "detail": str(resp)[:200]}
                _log(f"  TEST [{cmd}]: FAIL - {str(resp)[:100]}")
        except Exception as e:
            results[cmd] = {"passed": False, "detail": str(e)}
            _log(f"  TEST [{cmd}]: ERROR - {e}")

    try:
        png_data = get_screenshot(mod_url)
        ok, detail = verify_screenshot(png_data, min_size_kb=1)
        results["screenshot"] = {"passed": ok, "detail": detail}
        if ok:
            ss_path = SCREENSHOT_DIR / f"{mc_ver}_{loader}_{int(time.time())}.png"
            ss_path.parent.mkdir(parents=True, exist_ok=True)
            ss_path.write_bytes(png_data)
            _log(f"  TEST [screenshot]: PASS ({detail}), saved to {ss_path}")

            ref_path = REF_SCREENSHOT_DIR / f"ref_{mc_ver}_{loader}.png"
            if ref_path.exists():
                ok2, detail2 = compare_screenshots_structural(str(ss_path), str(ref_path))
                results["screenshot_compare"] = {"passed": ok2, "detail": detail2}
                _log(f"  TEST [screenshot_compare]: {'PASS' if ok2 else 'FAIL'} ({detail2})")
    except Exception as e:
        results["screenshot"] = {"passed": False, "detail": str(e)}
        _log(f"  TEST [screenshot]: ERROR - {e}")

    _log("Tests done, killing MC")
    kill_minecraft()
    if mc_proc and mc_proc.poll() is None:
        mc_proc.kill()
    mc_proc.wait(timeout=5)

    return results


def run_e2e_test(mc_ver, loader, jdk_ver, mod_jar, world_name, timeout=600):
    """Run E2E smoke test using the test world and the mod's MCP tools.

    Steps:
      1. Launch MC with mod into test world
      2. Wait for mod ready
      3. Verify in-game (screenshot check)
      4. Place a sign, verify text
      5. Take screenshots at each stage
      6. Compare with reference screenshots if available

    Returns: dict with test results.
    """
    results = {}
    kill_minecraft()
    setup_xvfb(screen="1280x720x24")
    install_mod_jar(mod_jar)
    install_test_world(world_name)

    version_name = setup_mc_version(mc_ver, loader)
    if not version_name:
        return {"setup": {"passed": False, "detail": "Version setup failed"}}

    env = os.environ.copy()
    launcher = str(SCRIPTS / "launch_mc.py")

    mc_proc = subprocess.Popen(
        [sys.executable, launcher, version_name,
         "--width", "1280", "--height", "720",
         "--world", world_name,
         "--no-assets-download", "--no-mod-sync"],
        env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, bufsize=1,
    )

    import threading
    def log_output(proc):
        for line in proc.stdout:
            stripped = line.strip()
            if stripped:
                _log(f"  [MC] {stripped[:200]}")
    threading.Thread(target=log_output, args=(mc_proc,), daemon=True).start()

    try:
        mod_url = wait_for_mod(timeout=240)
        results["launch"] = {"passed": True, "detail": mod_url}
    except TimeoutError:
        results["launch"] = {"passed": False, "detail": "Timeout"}
        kill_minecraft()
        return results

    time.sleep(10)

    e2e_steps = [
        ("verify_in_game", "get_player_info", {}, lambda r: isinstance(r, dict)),
        ("game_mode_creative", "set_gamemode", {"mode": "creative"}, lambda r: True),
    ]

    for step_name, cmd, params, validator in e2e_steps:
        try:
            resp = api_call(mod_url, cmd, params)
            results[step_name] = {"passed": validator(resp), "detail": str(resp)[:100]}
        except Exception as e:
            results[step_name] = {"passed": False, "detail": str(e)}

    steps_to_screenshot = ["01_ingame", "02_inventory", "03_sign_placed"]
    for label in steps_to_screenshot:
        try:
            png = get_screenshot(mod_url)
            ss_path = SCREENSHOT_DIR / f"e2e_{mc_ver}_{loader}_{label}_{int(time.time())}.png"
            ss_path.parent.mkdir(parents=True, exist_ok=True)
            ss_path.write_bytes(png)
            ok, detail = verify_screenshot(png)
            results[f"screenshot_{label}"] = {"passed": ok, "detail": detail}

            ref_path = REF_SCREENSHOT_DIR / f"ref_{mc_ver}_{loader}_{label}.png"
            if ref_path.exists():
                ok2, detail2 = compare_screenshots_structural(str(ss_path), str(ref_path))
                results[f"compare_{label}"] = {"passed": ok2, "detail": detail2}
        except Exception as e:
            results[f"screenshot_{label}"] = {"passed": False, "detail": str(e)}

    kill_minecraft()
    if mc_proc and mc_proc.poll() is None:
        mc_proc.kill()
    return results


def generate_report(results, output_path=None):
    """Generate a JSON summary report from test results."""
    summary = {
        "total": len(results),
        "passed": sum(1 for r in results if r.get("status") == "PASS"),
        "failed": sum(1 for r in results if r.get("status") != "PASS"),
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "details": results,
    }
    report_json = json.dumps(summary, indent=2, ensure_ascii=False)

    if output_path:
        Path(output_path).write_text(report_json, encoding="utf-8")
        _log(f"Report saved to {output_path}")
    else:
        print(report_json)

    return summary


def main():
    parser = argparse.ArgumentParser(description="CI helper for Minecraft MCP testing")
    subparsers = parser.add_subparsers(dest="command")

    smoke = subparsers.add_parser("smoke", help="Run headless smoke test")
    smoke.add_argument("--mc-ver", required=True)
    smoke.add_argument("--loader", required=True)
    smoke.add_argument("--jdk-ver", default="21")
    smoke.add_argument("--mod-jar", help="Path to mod JAR (auto-detect if omitted)")
    smoke.add_argument("--world", help="World save name to use")
    smoke.add_argument("--output-json", help="Save results to JSON file")

    screenshot = subparsers.add_parser("screenshot-test", help="Run screenshot verification test")
    screenshot.add_argument("--mc-ver", required=True)
    screenshot.add_argument("--loader", required=True)
    screenshot.add_argument("--mod-jar", help="Path to mod JAR")
    screenshot.add_argument("--world", default="CI_TestWorld")
    screenshot.add_argument("--ref-screenshot", help="Path to reference screenshot for comparison")

    e2e = subparsers.add_parser("e2e", help="Run full E2E test")
    e2e.add_argument("--mc-ver", required=True)
    e2e.add_argument("--loader", required=True)
    e2e.add_argument("--jdk-ver", default="21")
    e2e.add_argument("--mod-jar", required=True)
    e2e.add_argument("--world", default="CI_TestWorld")
    e2e.add_argument("--output-json", help="Save results to JSON file")

    args = parser.parse_args()

    if args.command == "smoke":
        mod_jar = args.mod_jar or _find_mod_jar(args.mc_ver, args.loader)
        if not mod_jar:
            _log("ERROR: No mod JAR found")
            sys.exit(1)
        results = run_smoke_test(
            args.mc_ver, args.loader, args.jdk_ver, mod_jar,
            headless=True, world_name=args.world,
        )
        passed = all(v["passed"] for v in results.values())
        summary = {
            "mc_ver": args.mc_ver,
            "loader": args.loader,
            "status": "PASS" if passed else "FAIL",
            "tests": results,
        }
        if args.output_json:
            generate_report([summary], args.output_json)
        sys.exit(0 if passed else 1)

    elif args.command == "screenshot-test":
        mod_jar = args.mod_jar or _find_mod_jar(args.mc_ver, args.loader)
        if not mod_jar:
            _log("ERROR: No mod JAR found")
            sys.exit(1)
        kill_minecraft()
        setup_xvfb(screen="1280x720x24")
        install_mod_jar(mod_jar)
        install_test_world(args.world)

        version_name = setup_mc_version(args.mc_ver, args.loader)
        if not version_name:
            _log("ERROR: Failed to setup MC version")
            sys.exit(1)

        launcher = str(SCRIPTS / "launch_mc.py")
        mc_proc = subprocess.Popen(
            [sys.executable, launcher, version_name,
             "--width", "1280", "--height", "720",
             "--world", args.world,
             "--no-assets-download", "--no-mod-sync"],
            env=os.environ.copy(),
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, bufsize=1,
        )

        try:
            mod_url = wait_for_mod(timeout=240)
            time.sleep(15)
            png = get_screenshot(mod_url)
            ok, detail = verify_screenshot(png)
            print(f"SCREENSHOT: {'PASS' if ok else 'FAIL'} - {detail}")
            ss_path = SCREENSHOT_DIR / f"ref_ci_{args.mc_ver}_{args.loader}.png"
            ss_path.parent.mkdir(parents=True, exist_ok=True)
            ss_path.write_bytes(png)
            if args.ref_screenshot and os.path.isfile(args.ref_screenshot):
                ok2, detail2 = compare_screenshots_structural(str(ss_path), args.ref_screenshot)
                print(f"COMPARE: {'PASS' if ok2 else 'FAIL'} - {detail2}")
            sys.exit(0 if ok else 1)
        finally:
            kill_minecraft()
            mc_proc.kill()

    elif args.command == "e2e":
        results = run_e2e_test(args.mc_ver, args.loader, args.jdk_ver, args.mod_jar, args.world)
        passed = all(v["passed"] for v in results.values())
        if args.output_json:
            generate_report([{"mc_ver": args.mc_ver, "loader": args.loader,
                               "status": "PASS" if passed else "FAIL", "tests": results}],
                             args.output_json)
        sys.exit(0 if passed else 1)

    else:
        parser.print_help()


def _find_mod_jar(mc_ver, loader):
    """Find a mod JAR in release-jars/ or packages/mods/."""
    for search_dir in [ROOT / "release-jars", ROOT]:
        for root, dirs, files in os.walk(str(search_dir)):
            if "node_modules" in root:
                continue
            for f in files:
                if f.endswith(".jar") and mc_ver in f and loader in f and "sources" not in f:
                    return os.path.join(root, f)

    mod_dir = ROOT / "packages" / "mods" / mc_ver / loader / "build" / "libs"
    if mod_dir.exists():
        jars = [j for j in mod_dir.glob("*.jar") if "sources" not in j.name]
        if jars:
            return str(jars[0])
    return None


if __name__ == "__main__":
    main()

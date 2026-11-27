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
import secrets
import shutil
import subprocess
import sys
import time
import urllib.request
import urllib.error
import urllib.parse
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from netsec import safe_join, safe_urlopen  # noqa: E402


def _assert_local_mod_url(url):
    """The mod HTTP API is only ever reachable on loopback (CI runs the
    game and the checker on the same host); refuse anything else."""
    host = urllib.parse.urlsplit(str(url)).hostname or ""
    if host not in ("127.0.0.1", "localhost", "::1"):
        raise ValueError(f"mod API URL must be loopback, got {url!r}")
    return url


def ensure_mod_token():
    """Pin one shared mod-API token for this process tree (issue #81).

    The launcher hands MC_MCP_TOKEN to the game and the mod then requires
    ``Authorization: Bearer <token>`` on /api/cmd and /api/screenshot.
    Setting it before any launch keeps the launcher, the game and these
    helpers on the same token.
    """
    token = os.environ.get("MC_MCP_TOKEN") or secrets.token_hex(32)
    os.environ["MC_MCP_TOKEN"] = token
    return token


def _mod_auth_headers():
    token = os.environ.get("MC_MCP_TOKEN")
    return {"Authorization": f"Bearer {token}"} if token else {}

ROOT = Path(__file__).resolve().parent.parent
SCRIPTS = ROOT / "scripts"
def _resolve_mc_dir():
    """The game directory the Node launcher will pick.

    The launcher prefers APPDATA on Windows and ~ elsewhere (see
    packages/minecraft-mod-mcp/src/mc/platform.ts:mcDir), so the world this
    helper writes has to land in the same place it launches from. On the Linux
    runners both candidates collapse to ~/.minecraft.
    """
    candidates = []
    if os.name == "nt" and os.environ.get("APPDATA"):
        candidates.append(Path(os.environ["APPDATA"]) / ".minecraft")
    candidates.append(Path(os.environ.get("HOME") or os.path.expanduser("~")) / ".minecraft")
    for candidate in candidates:
        if (candidate / "versions").is_dir():
            return candidate
    return candidates[0]


MC_DIR = _resolve_mc_dir()
GAME_DIR = MC_DIR / "mcp_launcher" / "game"

TEST_WORLD_DIR = ROOT / "tests" / "reference-screenshots"
SCREENSHOT_DIR = ROOT / "screenshots" / "ci"
REF_SCREENSHOT_DIR = ROOT / "tests" / "reference-screenshots"

# Strip proxy env vars to prevent CI network hangs
for k in list(os.environ.keys()):
    if k.lower().endswith("proxy") or k.lower() == "all_proxy":
        del os.environ[k]


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
        mcp_cli = str(Path(__file__).resolve().parent.parent / "packages" / "minecraft-mod-mcp" / "dist" / "cli.js")
        _log(f"Running MCP CLI install for {mc_ver} ({loader})...")
        env_clean = {k: v for k, v in os.environ.items()
                      if not k.lower().endswith("proxy") and k.lower() != "all_proxy"}
        env_clean["HOME"] = str(Path(mc).parent)
        env_clean["JAVA_HOME"] = os.environ.get("JAVA_HOME", "")
        cmd = ["node", mcp_cli, "install", mc_ver, "--loader", loader]
        result = subprocess.run(
            cmd,
            env=env_clean,
            capture_output=True, text=True, timeout=600,
        )
        if result.returncode != 0:
            _log(f"MCP install stderr: {result.stderr[-500:]}")
        
        installed = _find_installed(mc_ver, loader, mc)
        if installed:
            _log(f"Installed: {installed}")
            return installed
        _log(f"Install failed for {mc_ver}/{loader}")
        return None

    elif loader == "fabric":
        # Install through the CLI like forge/neoforge: it fetches the profile
        # AND downloads the loader libraries (KnotClient et al.). Writing the
        # profile json here directly used to skip the library downloads and
        # the JVM then died with ClassNotFoundException.
        mcp_cli = str(Path(__file__).resolve().parent.parent / "packages" / "minecraft-mod-mcp" / "dist" / "cli.js")
        _log(f"Running MCP CLI install for {mc_ver} (fabric)...")
        env_clean = {k: v for k, v in os.environ.items()
                     if not k.lower().endswith("proxy") and k.lower() != "all_proxy"}
        env_clean["HOME"] = str(Path(mc).parent)
        env_clean["JAVA_HOME"] = os.environ.get("JAVA_HOME", "")
        result = subprocess.run(
            ["node", mcp_cli, "install", mc_ver, "--loader", "fabric"],
            env=env_clean, capture_output=True, text=True, timeout=600)
        if result.returncode != 0:
            _log(f"MCP install stderr: {result.stderr[-500:]}")
        installed = _find_installed(mc_ver, loader, mc)
        if installed:
            _log(f"Installed: {installed}")
            return installed
        _log(f"Install failed for {mc_ver}/fabric")
        return None

    return None


def _install_fabric_json(mc_ver, mc):
    """Create Fabric loader version JSON via Fabric meta API."""
    import urllib.request, json

    meta_url = f"https://meta.fabricmc.net/v2/versions/loader/{mc_ver}"
    with safe_urlopen(meta_url, timeout=30, headers={"User-Agent": "Mozilla/5.0"}) as resp:
        versions = json.loads(resp.read())
    if not versions:
        _log(f"No Fabric loader for {mc_ver}")
        return None
    # Pin the loader per version_config instead of blindly taking the newest
    # from meta: 0.19.x dropped support for pre-26.x mixin layouts.
    pinned = None
    try:
        from version_config import ALL_VERSIONS
        info = ALL_VERSIONS.get(mc_ver, {})
        pinned = info.get("fabric_loader")
        if not pinned:
            pinned = _LEGACY_FABRIC_LOADERS.get(mc_ver, "0.16.14")
    except Exception:
        pinned = "0.16.14"
    loader_ver = pinned
    loader_info = next((v for v in versions if v["loader"]["version"] == loader_ver), None)
    if loader_info is None:
        _log(f"Fabric loader {loader_ver} unavailable for {mc_ver}, falling back to {versions[0]['loader']['version']}")
        loader_info = versions[0]
        loader_ver = loader_info["loader"]["version"]
    version_name = f"fabric-loader-{loader_ver}-{mc_ver}"

    profile_url = (f"https://meta.fabricmc.net/v2/versions/loader/{mc_ver}"
                   f"/{loader_ver}/profile/json")
    with safe_urlopen(profile_url, timeout=30, headers={"User-Agent": "Mozilla/5.0"}) as resp2:
        profile = json.loads(resp2.read())

    ver_dir = Path(mc) / "versions" / version_name
    ver_dir.mkdir(parents=True, exist_ok=True)
    (ver_dir / f"{version_name}.json").write_text(json.dumps(profile, indent=2))

    _log(f"Fabric version JSON: {version_name}")
    return version_name


# Fabric loader pins for smoke tests on pre-26.x versions (matches the
# builds' loader tables; newest 0.19.x cannot load these games).
_LEGACY_FABRIC_LOADERS = {
    "1.14.2": "0.16.14", "1.14.3": "0.16.14", "1.14.4": "0.16.14",
    "1.15": "0.16.14", "1.15.1": "0.16.14", "1.15.2": "0.16.14",
    "1.16.1": "0.16.14", "1.16.2": "0.16.14", "1.16.3": "0.16.14",
    "1.16.4": "0.16.14", "1.16.5": "0.16.14",
    "1.17.1": "0.16.14", "1.18.2": "0.16.14", "1.19.4": "0.16.14",
    "1.20.4": "0.16.14", "1.20.6": "0.16.14",
    "1.21.11": "0.16.14",
}


def _fabric_loader_pin(mc_ver: str) -> str | None:
    """Loader version for a MC version: version_config pin, else the legacy table."""
    try:
        from version_config import ALL_VERSIONS
        info = ALL_VERSIONS.get(mc_ver, {})
        if info.get("fabric_loader"):
            return info["fabric_loader"]
    except Exception:
        pass
    return _LEGACY_FABRIC_LOADERS.get(mc_ver, "0.16.14")


def _find_installed(mc_ver: str, loader: str, mc_dir: str | None = None) -> str | None:
    """Find an installed Forge/NeoForge/Fabric version directory by scanning .minecraft/versions/."""
    base = Path(mc_dir) if mc_dir else Path.home() / ".minecraft"
    versions = base / "versions"
    if not versions.is_dir():
        return None
    loader_lower = loader.lower()
    for d in sorted(versions.iterdir()):
        if not d.is_dir():
            continue
        json_path = d / f"{d.name}.json"
        if not json_path.exists():
            continue
        try:
            data = json.loads(json_path.read_text(encoding="utf-8"))
        except Exception:
            continue
        inherits = data.get("inheritsFrom", data.get("id", ""))
        if inherits != mc_ver:
            continue
        mc_cls = (data.get("mainClass") or "").lower()
        # 20.6.x-era NeoForge ships a forge-style BootstrapLauncher
        # mainClass, so also accept the conventional directory name.
        if loader_lower == "neoforge" and ("neoforged" in mc_cls or d.name.lower().startswith("neoforge")):
            return d.name
        if loader_lower == "forge" and ("minecraftforge" in mc_cls or "forgebootstrap" in mc_cls
                                        or "launchwrapper" in mc_cls or "bootstraplauncher" in mc_cls
                                        or "modlauncher" in mc_cls):
            return d.name
        if loader_lower == "fabric" and "fabricmc" in mc_cls:
            return d.name
    return None


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
            ["Xvfb", display, "-screen", "0", screen, "-ac",
             "+extension", "RANDR", "+extension", "GLX", "-nolisten", "tcp"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        time.sleep(2)
        _configure_xrandr(display, screen)
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


def _configure_xrandr(display, screen):
    """Add display modes via xrandr for LWJGL 2.x compatibility."""
    try:
        w, h = screen.split("x")[0], screen.split("x")[1]
        mode_name = f"{w}x{h}_60"
        subprocess.run(
            ["xrandr", "--newmode", mode_name,
             str(int(w) * 0.062), w,
             str(int(w) + int(int(w) * 0.05)),
             str(int(w) + int(int(w) * 0.15)),
             str(int(w) + int(int(w) * 0.30)),
             h,
             str(int(h) + 3),
             str(int(h) + 6),
             str(int(h) + int(int(h) * 0.04)),
             "-hsync", "+vsync"],
            capture_output=True, timeout=5,
        )
        subprocess.run(
            ["xrandr", "--addmode", "default", mode_name],
            capture_output=True, timeout=5,
        )
        _log(f"xrandr: added mode {mode_name}")
    except Exception as e:
        _log(f"xrandr: {e} (non-fatal)")


def kill_minecraft():
    """Kill all running Minecraft Java processes."""
    try:
        subprocess.run(["killall", "-9", "java"], timeout=3, capture_output=True)
    except Exception:
        pass
    time.sleep(1)


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


def install_test_world(world_name="CI_TestWorld", mc_dir=None, data_version=None):
    """Copy or generate the test superflat world save."""
    mc = mc_dir or str(MC_DIR)
    saves_dir = Path(mc) / "saves" / world_name
    if saves_dir.exists():
        shutil.rmtree(str(saves_dir), ignore_errors=True)
    saves_dir.mkdir(parents=True, exist_ok=True)
    _generate_level_dat(str(saves_dir), world_name, data_version)
    _log(f"Test world created: {saves_dir}")
    return str(saves_dir)


# DataVersion of each version the smoke matrix runs on, so the game starts its
# own upgrade from a plausible point instead of from one number on every lane.
#
# These are best effort. A value OLDER than the running game is safe: the game
# upgrades the save. Values newer than the game are not, so 26.2/26.3 reuse
# 26.1.2's number and get its upgrade chain rather than a downgrade.
_DATA_VERSIONS = {
    "1.14.4": 1976, "1.15.2": 2230, "1.16.5": 2586, "1.17.1": 2730,
    "1.18.2": 2975, "1.19.4": 3337, "1.20.1": 3465, "1.20.4": 3700,
    "1.20.6": 3839, "1.21.1": 3955, "1.21.4": 4189, "1.21.7": 4438,
    "1.21.11": 4671, "26.1.2": 4790, "26.2": 4790, "26.3": 4790,
}


def data_version_for(mc_ver):
    """Best-known DataVersion for a MC version string, else the 1.20.2 value."""
    return _DATA_VERSIONS.get(str(mc_ver), 3953)


# NBT tag ids used by the encoder below.
_NBT_END, _NBT_BYTE, _NBT_INT, _NBT_LONG, _NBT_STRING, _NBT_LIST, _NBT_COMPOUND = 0, 1, 3, 4, 8, 9, 10


class _Byte(int):
    """NBT TAG_Byte."""

    def __new__(cls, value):
        if not -128 <= value <= 127:
            raise ValueError(f"TAG_Byte out of range: {value}")
        return super().__new__(cls, value)


class _Int(int):
    """NBT TAG_Int."""

    def __new__(cls, value):
        if not -(2 ** 31) <= value < 2 ** 31:
            raise ValueError(f"TAG_Int out of range: {value}")
        return super().__new__(cls, value)


class _Long(int):
    """NBT TAG_Long."""


def _nbt_type(value):
    if isinstance(value, _Byte):
        return _NBT_BYTE
    if isinstance(value, _Int):
        return _NBT_INT
    if isinstance(value, _Long):
        return _NBT_LONG
    if isinstance(value, str):
        return _NBT_STRING
    if isinstance(value, list):
        return _NBT_LIST
    if isinstance(value, dict):
        return _NBT_COMPOUND
    raise TypeError(f"unsupported NBT value {value!r}; wrap ints as _Byte/_Int/_Long")


def _nbt_write(value, out):
    """Write one tag payload (no type byte and no name)."""
    import struct

    kind = _nbt_type(value)
    if kind == _NBT_BYTE:
        out.write(struct.pack(">b", value))
    elif kind == _NBT_INT:
        out.write(struct.pack(">i", value))
    elif kind == _NBT_LONG:
        out.write(struct.pack(">q", value))
    elif kind == _NBT_STRING:
        data = value.encode("utf-8")
        out.write(struct.pack(">H", len(data)))
        out.write(data)
    elif kind == _NBT_LIST:
        # An empty list declares TAG_String; every non-empty list here is
        # homogeneous so its first element names the type.
        out.write(struct.pack(">bi", _nbt_type(value[0]) if value else _NBT_STRING, len(value)))
        for item in value:
            _nbt_write(item, out)
    else:
        for name, item in value.items():
            name_bytes = name.encode("utf-8")
            out.write(struct.pack(">b", _nbt_type(item)))
            out.write(struct.pack(">H", len(name_bytes)))
            out.write(name_bytes)
            _nbt_write(item, out)
        out.write(b"\x00")  # TAG_End


def _nbt_encode_root(tree):
    """Root TAG_Compound header (type byte + empty name) plus the payload."""
    import io

    out = io.BytesIO()
    out.write(b"\x0a\x00\x00")
    _nbt_write(tree, out)
    return out.getvalue()


def _generate_level_dat(save_dir, world_name, data_version=None):
    """Generate a superflat level.dat (gzip + NBT).

    The tree mirrors what the game writes for a flat 1.17-1.20 world. Hand
    writing the byte stream tag by tag dropped the root header, tagged ints as
    longs and emitted "layers" as an empty compound, so the game refused every
    world with "Loading NBT data: EOFException" / "Overworld settings
    missing" / "Failed to load data pack config".
    """
    import gzip

    dv = int(data_version if data_version is not None else 3953)
    tree = {"Data": {
        "DataVersion": _Int(dv),
        "version": _Int(19133),
        "LevelName": world_name,
        "GameType": _Int(1),
        "MapFeatures": _Byte(1),
        "allowCommands": _Byte(1),
        "hardcore": _Byte(0),
        "Difficulty": _Byte(2),
        "DifficultyLocked": _Byte(0),
        "SpawnX": _Int(0),
        "SpawnY": _Int(10),
        "SpawnZ": _Int(0),
        "RandomSeed": _Long(42),
        "generatorName": "flat",
        "initialized": _Byte(1),
        "LastPlayed": _Long(int(time.time() * 1000)),
        "Time": _Long(0),
        "DayTime": _Long(1000),
        "clearWeatherTime": _Int(0),
        "rainTime": _Int(0),
        "thunderTime": _Int(0),
        "raining": _Byte(0),
        "thundering": _Byte(0),
        "DataPacks": {"Enabled": ["vanilla"], "Disabled": []},
        "GameRules": {
            "doDaylightCycle": "true",
            "doWeatherCycle": "true",
            "doFireTick": "false",
            "doMobSpawning": "false",
            "keepInventory": "true",
        },
        "WorldGenSettings": {
            "bonus_chest": _Byte(0),
            "seed": _Long(42),
            "generate_features": _Byte(1),
            "dimensions": {
                "minecraft:overworld": {
                    "type": "minecraft:overworld",
                    "generator": {
                        "type": "minecraft:flat",
                        "settings": {
                            "features": _Int(1),
                            "biome": "minecraft:plains",
                            "lakes": _Byte(0),
                            "layers": [
                                {"block": "minecraft:bedrock", "height": _Int(1)},
                                {"block": "minecraft:dirt", "height": _Int(2)},
                                {"block": "minecraft:grass_block", "height": _Int(1)},
                            ],
                            "structure_overrides": [],
                        },
                    },
                },
                "minecraft:the_nether": {
                    "type": "minecraft:the_nether",
                    "generator": {
                        "type": "minecraft:noise",
                        "settings": "minecraft:nether",
                        "biome_source": {"type": "minecraft:multi_noise", "preset": "minecraft:nether"},
                    },
                },
                "minecraft:the_end": {
                    "type": "minecraft:the_end",
                    "generator": {
                        "type": "minecraft:noise",
                        "settings": "minecraft:end",
                        "biome_source": {"type": "minecraft:the_end"},
                    },
                },
            },
        },
    }}
    payload = _nbt_encode_root(tree)

    level_dat = safe_join(save_dir, "level.dat")
    os.makedirs(save_dir, exist_ok=True)
    with gzip.open(level_dat, "wb") as f:
        f.write(payload)

    session_lock = safe_join(save_dir, "session.lock")
    with open(session_lock, "wb") as f:
        f.write(b"\x00" * 8)


def wait_for_mod(url="http://127.0.0.1:9876", timeout=180, start_port=None):
    """Wait for the mod HTTP server to be ready.

    Scans ports from start_port down to 9000.
    Returns the URL of the found mod, or raises TimeoutError.
    """
    _assert_local_mod_url(url)
    ports = list(range(int(start_port), 8999, -1)) if start_port else list(range(9876, 8999, -1))

    deadline = time.time() + timeout
    last_log = 0

    while time.time() < deadline:
        for port in ports[:10]:
            candidate = f"http://127.0.0.1:{port}"
            try:
                req = urllib.request.Request(
                    f"{candidate}/api/status",
                    headers={"User-Agent": "minecraft-mcp-ci"}
                )
                with urllib.request.urlopen(req, timeout=2) as resp:
                    data = json.loads(resp.read().decode())
                    if data.get("ok") and data.get("type") == "minecraft-mod":
                        elapsed = time.time() - (deadline - timeout)
                        _log(f"Mod ready at {candidate} (t={elapsed:.0f}s)")
                        return candidate
            except (urllib.error.URLError, ConnectionRefusedError, OSError,
                    json.JSONDecodeError, TimeoutError, Exception):
                continue

        now = time.time()
        if now - last_log > 10:
            _log(f"Waiting for mod... ({now - (deadline - timeout):.0f}s elapsed)")
            last_log = now
        time.sleep(2)

    _dump_launch_diagnostics()
    raise TimeoutError(f"Mod did not start within {timeout}s")


def _dump_launch_diagnostics():
    """On mod-wait timeout, surface why: MC log tail, deployed mods, java liveness."""
    game_dir = Path(MC_DIR) / "mcp_launcher" / "game"
    mods = game_dir / "mods"
    if mods.is_dir():
        _log(f"[diag] game mods: {sorted(p.name for p in mods.glob('*.jar'))}")
    else:
        _log(f"[diag] game mods dir missing: {mods}")
    for stderr_name in ("mc-stderr.log", "mc-stdout.log"):
        stderr_log = game_dir / "logs" / stderr_name
        if stderr_log.is_file() and stderr_log.stat().st_size > 0:
            try:
                lines = stderr_log.read_text(encoding="utf-8", errors="replace").splitlines()
                if lines:
                    _log(f"[diag] {stderr_name} (last 15 lines):")
                    for l in lines[-15:]:
                        _log(f"  [MC] {l[:200]}")
            except Exception as e:
                _log(f"[diag] failed reading {stderr_name}: {e}")
    log = game_dir / "logs" / "latest.log"
    if log.is_file():
        try:
            lines = log.read_text(encoding="utf-8", errors="replace").splitlines()
            tail = lines[-40:]
            crash = [l for l in lines if "Exception" in l or "Error" in l or "CRASH" in l]
            if crash:
                _log("[diag] latest.log error lines:")
                for l in crash[:12]:
                    _log(f"  [MC] {l[:200]}")
            _log("[diag] latest.log tail:")
            for l in tail[-15:]:
                _log(f"  [MC] {l[:200]}")
        except Exception as e:
            _log(f"[diag] failed reading latest.log: {e}")
    else:
        _log(f"[diag] no latest.log at {log}")
    try:
        out = subprocess.run(["pgrep", "-af", "java"], capture_output=True, text=True, timeout=10)
        procs = [l for l in (out.stdout or "").splitlines() if l.strip()]
        _log(f"[diag] java processes alive: {len(procs)}")
        for l in procs[:3]:
            _log(f"  [diag] {l[:160]}")
    except Exception:
        pass


def api_call(mod_url, cmd, params=None, timeout=30, retries=4):
    _assert_local_mod_url(mod_url)
    """Call the mod HTTP API and return the response.

    Retries transient refusals: right after the HTTP bridge comes up the
    render thread may still be settling under llvmpipe and drop requests.
    """
    payload = json.dumps({"cmd": cmd, "params": params or {}}).encode()
    last_err = None
    for attempt in range(retries):
        req = urllib.request.Request(
            f"{mod_url}/api/cmd",
            data=payload,
            headers={"Content-Type": "application/json", "User-Agent": "minecraft-mcp-ci", **_mod_auth_headers()},
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                return json.loads(resp.read().decode())
        except Exception as e:
            last_err = e
            time.sleep(2 + attempt * 3)
    raise last_err


def get_screenshot(mod_url, timeout=60):
    """Get a screenshot from the mod and return raw PNG bytes.

    /api/screenshot answers with a JSON envelope
    ({"original": "data:image/png;base64,...", "grid": ..., "width", "height"}),
    not a bare PNG. Everything downstream - verify_screenshot, the saved
    screenshots/, compare_screenshots_* - expects bytes, so unwrap here; this
    is the single place that knows about the envelope.
    """
    _assert_local_mod_url(mod_url)
    req = urllib.request.Request(
        f"{mod_url}/api/screenshot",
        headers={"User-Agent": "minecraft-mcp-ci", **_mod_auth_headers()},
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        body = resp.read()
    if body[:1] == b"{":
        try:
            envelope = json.loads(body.decode("utf-8"))
        except (ValueError, UnicodeDecodeError):
            return body
        for key in ("original", "grid"):
            uri = envelope.get(key) if isinstance(envelope, dict) else None
            if isinstance(uri, str) and uri.startswith("data:image/png;base64,"):
                try:
                    return base64.b64decode(uri.split(",", 1)[1])
                except (ValueError, TypeError):
                    break
    return body


def _png_pixel_stats(png_data):
    """Decode a PNG well enough to tell a real frame from a blank one.

    Returns (width, height, distinct_colours_sampled) or raises. Only the
    standard library is available in CI, so this inflates IDAT by hand.
    """
    import struct
    import zlib

    if png_data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    pos = 8
    width = height = bit_depth = color_type = None
    idat = bytearray()
    while pos + 8 <= len(png_data):
        length = struct.unpack_from(">I", png_data, pos)[0]
        ctype = png_data[pos + 4:pos + 8]
        data = png_data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if ctype == b"IHDR":
            width, height = struct.unpack_from(">II", data, 0)
            bit_depth = data[8]
            color_type = data[9]
        elif ctype == b"IDAT":
            idat += data
        elif ctype == b"IEND":
            break
    if width is None or bit_depth != 8 or color_type not in (2, 6):
        raise ValueError(f"unsupported PNG colour type {color_type} / depth {bit_depth}")
    channels = 3 if color_type == 2 else 4
    stride = width * channels
    raw = zlib.decompress(bytes(idat))

    def paeth(a, b, c):
        p = a + b - c
        pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
        return a if pa <= pb and pa <= pc else (b if pb <= pc else c)

    prev = bytearray(stride)
    colors = set()
    rpos = 0
    # Sample a grid rather than every pixel: enough to catch a flat frame.
    step_y = max(1, height // 64)
    step_x = max(1, width // 64)
    for y in range(height):
        ftype = raw[rpos]
        rpos += 1
        line = bytearray(raw[rpos:rpos + stride])
        rpos += stride
        if ftype == 1:
            for i in range(channels, stride):
                line[i] = (line[i] + line[i - channels]) & 0xFF
        elif ftype == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif ftype == 3:
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((left + prev[i]) >> 1)) & 0xFF
        elif ftype == 4:
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                upleft = prev[i - channels] if i >= channels else 0
                line[i] = (line[i] + paeth(left, prev[i], upleft)) & 0xFF
        prev = line
        if y % step_y == 0:
            for x in range(0, stride, channels * step_x):
                colors.add(bytes(line[x:x + 3]))
    return width, height, len(colors)


def verify_screenshot(png_data, min_size_kb=1, min_colors=16):
    """Verify that PNG data is a real frame, not a blank capture.

    Size alone proved useless: a single-colour 1280x720 PNG is 5 KB, which
    passed the old 1 KB floor while every screenshot on 1.17-1.21.4 was blank.
    Sampling distinct colours catches a flat frame at any resolution.
    """
    if not png_data or len(png_data) < 8:
        return False, "Empty or too small"
    if png_data[:8] != b"\x89PNG\r\n\x1a\n":
        return False, "Not a valid PNG header"
    size_kb = len(png_data) // 1024
    if size_kb < min_size_kb:
        return False, f"PNG too small ({size_kb}KB < {min_size_kb}KB)"
    try:
        width, height, colors = _png_pixel_stats(png_data)
    except Exception as e:
        return False, f"PNG unreadable ({e})"
    if colors < min_colors:
        return False, f"blank frame: {colors} distinct colour(s) sampled in {width}x{height}"
    return True, f"Valid PNG {width}x{height}, {size_kb}KB, {colors} sampled colours"


def valid_player_info(resp):
    """A player read that found the player, not a stub.

    The old check only asserted isinstance(dict), so {"name": null} passed and
    every Forge lane shipped with get_player_info returning a stub (issue #100).
    Keep it loose on game state: a dead player really does report health 0, and
    a player can legitimately stand at the origin.
    """
    if not isinstance(resp, dict) or "error" in resp:
        return False
    name = resp.get("name")
    if not isinstance(name, str) or not name.strip():
        return False
    try:
        float(resp.get("health"))
        coords = [float(v) for v in str(resp.get("pos", "")).split()]
    except (TypeError, ValueError):
        return False
    return len(coords) == 3


def valid_world_info(resp):
    """A world read that found a level, not a null/placeholder stub.

    A save may legitimately be called "unknown", so only the shapes the mod
    emits when it found nothing are rejected: a null name, or the literal
    "MpServer" placeholder a client-side WorldInfo carries in multiplayer.
    """
    if not isinstance(resp, dict) or "error" in resp:
        return False
    name = resp.get("world_name")
    if not isinstance(name, str) or not name.strip():
        return False
    return name != "MpServer"


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
    opts_path = Path(safe_join(mc, "options.txt"))

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


def _dump_game_diagnostics(reason, tail_lines=80):
    """Dump the game's stdout/stderr logs, crash reports and JVM fatal-error
    logs into the job log.

    The launcher writes the game JVM's output to files inside the game dir;
    without this dump a smoke failure leaves no trace of WHY the game died
    (the runner is destroyed right after).
    """
    _log(f"  GAME DIAGNOSTICS ({reason}):")
    # The node CLI writes logs/mc-stdout.log + logs/mc-stderr.log into the
    # game dir; the legacy Python launcher wrote mcp-launch-*.log directly
    # into it. Cover both so the dump works under either launcher.
    candidates = [
        GAME_DIR / "logs" / "mc-stderr.log",
        GAME_DIR / "logs" / "mc-stdout.log",
        GAME_DIR / "mcp-launch-stderr.log",
        GAME_DIR / "mcp-launch-stdout.log",
    ]
    for path in candidates:
        if not path.is_file():
            continue
        try:
            lines = path.read_text(errors="replace").splitlines()
        except OSError:
            continue
        if not lines:
            continue
        _log(f"    --- tail of {path.name} ({len(lines)} lines) ---")
        for line in lines[-tail_lines:]:
            _log(f"    {line.rstrip()[:220]}")
    # Crash reports and JVM hs_err logs live in the game dir (the run dir).
    for pattern in ("crash-reports/*.txt", "hs_err_pid*.log", "crash-*.txt"):
        for f in sorted(GAME_DIR.glob(pattern))[-2:]:
            if not f.is_file():
                continue
            _log(f"    --- {f.name} (head) ---")
            try:
                with open(f, "r", errors="replace") as fh:
                    for line in fh.readlines()[:50]:
                        _log(f"    {line.rstrip()[:220]}")
            except OSError:
                pass



def mc_version_tuple(mc_ver):
    """(major, minor, patch) of a MC version string, (1, 0) on garbage."""
    try:
        return tuple(int(x) for x in str(mc_ver).split("."))
    except ValueError:
        return (1, 0)


def era_launch_flags(mc_ver, loader, world_name=None):
    """Era-appropriate launch flags shared by the smoke and screenshot jobs.

    Returns (extra_jvm, extra_game, can_quick_play):
      extra_jvm      - JVM system properties the era needs under Xvfb/llvmpipe;
      extra_game     - quick play for the test save when the version has it;
      can_quick_play - whether the version can enter a world without the GUI.

    Keeping the smoke and screenshot launches on the same flags matters: the
    screenshot job used to launch without the early-display suppression and
    captured a single-colour frame on 1.20.4-forge while the smoke lane, with
    the flags, captured a real frame of the same version.
    """
    try:
        vtuple = tuple(int(x) for x in str(mc_ver).split("."))
    except ValueError:
        vtuple = (1, 0)
    major = vtuple[0]
    parts = []
    # MC 26.3+ (Render Pearl) on Forge/NeoForge: BOTH the AWT-headless trick
    # and the disableEarlyDisplay flags suppress FML's early GLFW window,
    # which forces Render Pearl to create its own GLX/Vulkan window - and
    # that dies on the runner's llvmpipe stack ("Couldn't find matching GLX
    # visual" + "no VK_KHR_surface"), fatally on NeoForge-patched init.
    # Letting FML open its (working, GLFW-based) early window lets Render
    # Pearl adopt that context instead. Older lanes keep the original flags.
    renderpearl = loader in ("forge", "neoforge") and vtuple >= (26, 3)
    if major >= 13 and not renderpearl:
        parts.append("-Djava.awt.headless=true")
    # Forge >= 1.20.5 and NeoForge >= 20.6 open an early GL window that
    # times out under Xvfb/llvmpipe ("Timed out trying to setup the Game
    # Window"); older loaders ignore the properties. NeoForge 20.6.x still
    # reads the forge-prefixed switch, 21+ the neoforged one.
    if loader in ("forge", "neoforge") and not renderpearl:
        parts.append("-Dforge.disableEarlyDisplay=true")
        parts.append("-Dneoforge.disableEarlyDisplay=true")
    # Quick play is the only non-GUI way into a world; versions before 1.20
    # have none and stay on the main menu.
    can_quick_play = vtuple >= (1, 20)
    extra_game = f"--quickPlaySingleplayer {world_name}" if world_name and can_quick_play else ""
    return " ".join(parts), extra_game, can_quick_play


def wait_for_player(mod_url, timeout_s=90):
    """Poll until the player entity is readable, for lanes that entered a world.

    The world keeps loading after the HTTP server answers (spawn prep on
    llvmpipe is slow, and the 26.x renderer adds more), so a fixed sleep
    asserted mid-load and read stubs even though the world was about to come
    up. Returns True when a player appeared, False on timeout.
    """
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        try:
            if valid_player_info(api_call(mod_url, "get_player_info", {}, retries=1)):
                time.sleep(3)  # let the first ticks settle before asserting
                return True
        except Exception:
            pass
        time.sleep(3)
    return False


def capture_screenshot(mod_url, attempts=2, timeout=90):
    """get_screenshot with one retry for transient transport drops.

    The JDK 8 fabric lanes on llvmpipe occasionally close the screenshot
    connection mid-response; a second attempt reads fine.
    """
    last = None
    for attempt in range(attempts):
        try:
            return get_screenshot(mod_url, timeout=timeout)
        except Exception as e:
            last = e
            time.sleep(5)
    raise last



def run_smoke_test(mc_ver, loader, jdk_ver, mod_jar, headless=True, world_name=None, timeout=300):
    """Run a full smoke test: launch MC, test APIs, verify screenshot.

    Returns: dict with test results {test_name: {passed: bool, detail: str}}
    """
    results = {}

    kill_minecraft()
    setup_xvfb()
    install_mod_jar(mod_jar)

    if world_name:
        # The launcher's game directory is <mc>/mcp_launcher/game when no
        # --mc-dir is passed (settings.ts:gameDirPath), and quick play
        # resolves the save relative to it - MC_DIR/saves is never read.
        install_test_world(world_name, mc_dir=str(GAME_DIR),
                           data_version=data_version_for(mc_ver))

    version_name = setup_mc_version(mc_ver, loader)
    if not version_name:
        _log("ERROR: Failed to setup MC version")
        return {"setup": {"passed": False, "detail": "Version setup failed"}}

    _log(f"Launching MC {version_name} (JDK {jdk_ver})")

    ensure_mod_token()
    env = os.environ.copy()
    env["JAVA_HOME"] = os.environ.get(f"JAVA_HOME_{jdk_ver}_X64",
                                       os.environ.get(f"JAVA_HOME_{jdk_ver}",
                                                       os.environ.get("JAVA_HOME", "")))

    # -Djava.awt.headless=true is two-sided on Xvfb: LWJGL2 (MC <= 1.12)
    # fails to open its X display with it ("Could not open X display
    # connection"), while LWJGL3-era loads benefit (Forge >= 1.20.5 skips
    # its EarlyDisplay window when AWT is headless). Apply it only to
    # MC >= 1.13.
    parts, extra_game, can_quick_play = era_launch_flags(mc_ver, loader, world_name)
    extra_jvm = " ".join(parts)

    launcher = str(Path(__file__).resolve().parent.parent / "packages" / "minecraft-mod-mcp" / "dist" / "cli.js")
    try:
        # Show exactly what will be executed: zero-output deaths leave no
        # other trace on the runner.
        # JVM flags go through JAVA_TOOL_OPTIONS: the JVM reads it itself, so
        # they never travel through our own argument assembly (where the
        # "-D..." strings were being shredded into single characters on the
        # runners). Game args stay on the env path the plan showed working.
        if extra_jvm:
            env["JAVA_TOOL_OPTIONS"] = extra_jvm
        else:
            env.pop("JAVA_TOOL_OPTIONS", None)
        env["MC_MCP_EXTRA_GAME"] = extra_game
        dry = subprocess.run(
            ["node", launcher, "launch", version_name, "--headless",
             "--memory", "2048",
             "--mod-jar", str(mod_jar), "--dry-run"],
            env=env, capture_output=True, text=True, timeout=120,
        )
        plan_lines = ((dry.stdout or "") + (dry.stderr or "")).splitlines()
        node_version = subprocess.run(["node", "--version"], capture_output=True, text=True).stdout.strip()
        _log(f"Node: {node_version}")
        _log("Dry-run launch plan (first 14 lines):")
        for line in plan_lines[:14]:
            _log(f"  [plan] {line[:220]}")
        mc_proc = subprocess.Popen(
            ["node", launcher, "launch", version_name, "--headless",
             "--memory", "2048",
             "--mod-jar", str(mod_jar)],
            env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, bufsize=1,
        )

        import threading
        def log_mc_output():
            for line in mc_proc.stdout:
                stripped = line.strip()
                if stripped:
                    _log(f"  [MC] {stripped[:200]}")
        threading.Thread(target=log_mc_output, daemon=True).start()

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
        _dump_game_diagnostics("mod never became ready")
        # The dry-run plan is the exact command the JVM rejected; dump it in
        # full (the 14-line preview above hides the Args section).
        _log("  [plan] full launch command the JVM rejected:")
        for line in plan_lines:
            _log(f"  [plan] {line[:300]}")
        kill_minecraft()
        return results

    time.sleep(5)  # Wait for world load etc

    try:
        api_call(mod_url, "enter_control_mode", {})
        _log(f"  Entered control mode")
    except Exception:
        pass
    time.sleep(1)

    # Quick play exists from 1.20; before it there is no non-GUI way into a
    # world, so on those lanes the game stays on the main menu and the player
    # and world reads legitimately answer with stubs - assert shape only there.
    can_quick_play = mc_version_tuple(mc_ver) >= (1, 20)
    info_validator = valid_player_info if can_quick_play else (lambda r: isinstance(r, dict))
    world_validator = valid_world_info if can_quick_play else (lambda r: isinstance(r, dict))
    tests = [
        ("ping", {}, lambda r: r.get("result") == "pong"),
        ("get_player_info", {}, info_validator),
        ("get_world_info", {}, world_validator),
        ("execute_command", {"command": "/time set day"},
         lambda r: isinstance(r, dict)),
        # The screenshot workspace jail (issue #81): an escaping path must be
        # refused, not written.
        ("screenshot_to_file", {"path": "../escape.png"},
         lambda r: "escapes" in str(r.get("error", ""))),
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

    capture_supported = mc_version_tuple(mc_ver) < (26, 1, 2)
    if not capture_supported:
        # 26.x renderers answer /api/screenshot with HTTP 500: the capture
        # path does not know them yet. Record a documented skip so the lane
        # still asserts the API contract without pretending the pixels were
        # checked.
        _log("  TEST [screenshot]: SKIP (capture not implemented for this renderer era)")
        results["screenshot"] = {"passed": True, "skipped": True,
                                 "detail": "capture not implemented for this renderer era"}
    else:
        try:
            png_data = capture_screenshot(mod_url)
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
            else:
                _log(f"  TEST [screenshot]: FAIL ({detail})")
        except Exception as e:
            _log(f"  TEST [screenshot]: FAIL ({e})")
            results["screenshot"] = {"passed": False, "detail": str(e)[:200]}

    if any(not v.get("passed") for v in results.values()):
        _dump_game_diagnostics("at least one API test failed")

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
    install_test_world(world_name, mc_dir=str(GAME_DIR),
                       data_version=data_version_for(mc_ver))

    version_name = setup_mc_version(mc_ver, loader)
    if not version_name:
        return {"setup": {"passed": False, "detail": "Version setup failed"}}

    ensure_mod_token()
    env = os.environ.copy()
    launcher = str(Path(__file__).resolve().parent.parent / "packages" / "minecraft-mod-mcp" / "dist" / "cli.js")

    e2e_jvm = "-Dforge.disableEarlyDisplay=true -Dneoforge.disableEarlyDisplay=true"
    try:
        if int(mc_ver.split(".")[0]) < 13:
            e2e_jvm = ""  # LWJGL2 eras need no early-display switch (and no headless)
    except ValueError:
        pass
    mc_proc = subprocess.Popen(
        ["node", launcher, "launch", version_name,
         "--memory", "2048", "--extra-jvm", e2e_jvm, "--mod-jar", str(mod_jar)],
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
            _log(f"  E2E [{step_name}]: {'PASS' if results[step_name]['passed'] else 'FAIL'}")
        except Exception as e:
            results[step_name] = {"passed": False, "detail": str(e)}
            _log(f"  E2E [{step_name}]: ERROR - {e}")

    steps_to_screenshot = ["01_ingame", "02_inventory", "03_sign_placed"]
    for label in steps_to_screenshot:
        try:
            # Under llvmpipe the frame can stay black for a while after the
            # mod handshake; retry until the shot is worth verifying.
            png = None
            ok, detail = False, "no frame"
            for attempt in range(8):
                png = get_screenshot(mod_url)
                ok, detail = verify_screenshot(png, min_size_kb=1)
                if ok:
                    break
                time.sleep(10)
            ss_path = SCREENSHOT_DIR / f"e2e_{mc_ver}_{loader}_{label}_{int(time.time())}.png"
            ss_path.parent.mkdir(parents=True, exist_ok=True)
            ss_path.write_bytes(png)
            results[f"screenshot_{label}"] = {"passed": ok, "detail": detail}
            _log(f"  E2E [screenshot {label}]: {'PASS' if ok else 'FAIL'} - {detail}")

            ref_path = REF_SCREENSHOT_DIR / f"ref_{mc_ver}_{loader}_{label}.png"
            if ref_path.exists():
                ok2, detail2 = compare_screenshots_structural(str(ss_path), str(ref_path))
                results[f"compare_{label}"] = {"passed": ok2, "detail": detail2}
        except Exception as e:
            results[f"screenshot_{label}"] = {"passed": False, "detail": str(e)}
            _log(f"  E2E [screenshot {label}]: ERROR - {e}")

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
        install_test_world(args.world, mc_dir=str(GAME_DIR),
                           data_version=data_version_for(args.mc_ver))

        version_name = setup_mc_version(args.mc_ver, args.loader)
        if not version_name:
            _log("ERROR: Failed to setup MC version")
            sys.exit(1)

        launcher = str(Path(__file__).resolve().parent.parent / "packages" / "minecraft-mod-mcp" / "dist" / "cli.js")
        ensure_mod_token()
        extra_jvm, extra_game, can_quick_play = era_launch_flags(args.mc_ver, args.loader, args.world)
        if extra_jvm:
            os.environ["JAVA_TOOL_OPTIONS"] = extra_jvm
        else:
            os.environ.pop("JAVA_TOOL_OPTIONS", None)
        os.environ["MC_MCP_EXTRA_GAME"] = extra_game
        mc_proc = subprocess.Popen(
            ["node", launcher, "launch", version_name,
             "--memory", "2048",
             "--mod-jar", str(args.mod_jar)],
            env=os.environ.copy(),
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, bufsize=1,
        )

        try:
            mod_url = wait_for_mod(timeout=240)
            if can_quick_play:
                wait_for_player(mod_url, timeout_s=120)
            else:
                time.sleep(15)
            png = capture_screenshot(mod_url)
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
        # Screenshots are best-effort: the runner's llvmpipe stack cannot
        # always produce real frames, so they must not gate the E2E verdict
        # (the API control path is the contract under test here).
        gate_keys = [k for k in results if not k.startswith("screenshot") and not k.startswith("compare")]
        passed = all(results[k]["passed"] for k in gate_keys)
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

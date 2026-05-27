"""Batch install Forge/NeoForge client for all versions defined in version_config.

Handles three installation modes:
  1. Modern Forge (1.13+) --installClient headless
  2. Old Forge (1.7.10-1.12.2) extract install_profile.json + download vanilla
  3. NeoForge --installClient headless
Also downloads parent vanilla version JSONs from Mojang manifest.

Usage:
  python scripts/install_forge.py                # install all missing
  python scripts/install_forge.py --force         # reinstall all
  python scripts/install_forge.py --mc 1.20.6     # install specific version
  python scripts/install_forge.py --vanilla-only   # only download parent vanilla JSONs
"""

import argparse
import json
import os
import subprocess
import sys
import time
import zipfile
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parent
ROOT = SCRIPTS.parent
MC_DIR = Path(os.environ.get("APPDATA", os.path.expanduser("~"))) / ".minecraft"
CACHE_DIR = ROOT / ".cache" / "installers"
VERSIONS_DIR = MC_DIR / "versions"

sys.path.insert(0, str(SCRIPTS))
from version_config import ALL_VERSIONS, get_loaders

FORGE_MAVEN = "https://maven.minecraftforge.net/net/minecraftforge/forge"
NEOFORGE_MAVEN = "https://maven.neoforged.net/releases/net/neoforged/neoforge"
VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

OLD_FORGE_THRESHOLD = "1.12.2"

_manifest_cache = None


def _fetch_json(url: str) -> dict:
    import urllib.request
    req = urllib.request.Request(url, headers={
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    })
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read())


def _get_manifest() -> dict:
    global _manifest_cache
    if _manifest_cache is None:
        print("  Fetching MC version manifest...")
        _manifest_cache = _fetch_json(VERSION_MANIFEST)
    return _manifest_cache


def _scan_versions() -> set:
    if not VERSIONS_DIR.exists():
        return set()
    return {d.name for d in VERSIONS_DIR.iterdir() if d.is_dir()}


def _find_installed(mc_ver: str, loader: str) -> str:
    prefixes = [f"{mc_ver}-{loader}-", f"{mc_ver}-{loader.capitalize()}",
                f"{mc_ver}-{loader.capitalize()}-"]
    for d in sorted(VERSIONS_DIR.iterdir()) if VERSIONS_DIR.exists() else []:
        if d.is_dir():
            name_lower = d.name.lower()
            if any(d.name.startswith(p) for p in prefixes):
                vj = d / f"{d.name}.json"
                if vj.exists():
                    return d.name
            if loader == "forge" and name_lower.startswith(f"{mc_ver}-forge"):
                vj = d / f"{d.name}.json"
                if vj.exists():
                    return d.name
    return ""


def _get_forge_url(mc_ver: str) -> tuple:
    info = ALL_VERSIONS.get(mc_ver, {})
    forge_ver = info.get("forge", "")
    if not forge_ver:
        return "", ""
    url = f"{FORGE_MAVEN}/{forge_ver}/forge-{forge_ver}-installer.jar"
    cache = f"forge-{forge_ver}-installer.jar"
    return url, cache


def _get_neoforge_url(mc_ver: str) -> tuple:
    info = ALL_VERSIONS.get(mc_ver, {})
    nf_ver = info.get("neoforge", "")
    if not nf_ver:
        return "", ""
    url = f"{NEOFORGE_MAVEN}/{nf_ver}/neoforge-{nf_ver}-installer.jar"
    cache = f"neoforge-{nf_ver}-installer.jar"
    return url, cache


def _is_old_forge(mc_ver: str) -> bool:
    parts = mc_ver.split(".")
    if len(parts) < 2:
        return False
    threshold = OLD_FORGE_THRESHOLD.split(".")
    for i in range(min(len(parts), len(threshold))):
        a, b = int(parts[i]), int(threshold[i])
        if a < b:
            return True
        if a > b:
            return False
    return True


def download_installer(url: str, cache_name: str) -> Path:
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    out = CACHE_DIR / cache_name
    if out.exists() and out.stat().st_size > 1000:
        print(f"  Cached: {out.name}")
        return out
    print(f"  Downloading: {url}")
    import urllib.request
    req = urllib.request.Request(url, headers={
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                       "(KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
    })
    with urllib.request.urlopen(req) as resp:
        data = resp.read()
    out.write_bytes(data)
    if out.stat().st_size < 1000:
        out.unlink()
        raise RuntimeError(f"Download too small: {url}")
    print(f"  Saved: {out.name} ({out.stat().st_size // 1024} KB)")
    return out


def download_vanilla(mc_ver: str) -> bool:
    vj_path = VERSIONS_DIR / mc_ver / f"{mc_ver}.json"
    if vj_path.exists():
        return True
    print(f"  Downloading vanilla {mc_ver} version JSON...")
    try:
        manifest = _get_manifest()
        ver_url = None
        for v in manifest.get("versions", []):
            if v["id"] == mc_ver:
                ver_url = v["url"]
                break
        if not ver_url:
            print(f"  Vanilla {mc_ver} not in manifest")
            return False
        vj = _fetch_json(ver_url)
        vj_dir = VERSIONS_DIR / mc_ver
        vj_dir.mkdir(parents=True, exist_ok=True)
        vj_path.write_text(json.dumps(vj, indent=2), encoding="utf-8")
        print(f"  VANILLA: {vj_path}")
        return True
    except Exception as e:
        print(f"  ERROR downloading vanilla: {e}")
        return False


def install_modern_forge(installer_jar: Path) -> tuple:
    before = _scan_versions()
    cmd = ["java", "-jar", str(installer_jar), "--installClient", str(MC_DIR)]
    print(f"  Running: {installer_jar.name}")
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
        after = _scan_versions()
        new_dirs = after - before
        success = "Successfully installed" in (result.stdout or "")
        if not success and result.returncode != 0:
            stderr_tail = (result.stderr or "")[-300:]
            stdout_tail = (result.stdout or "")[-300:]
            if stderr_tail:
                print(f"  ERR: {stderr_tail.strip()}")
            if stdout_tail:
                print(f"  OUT: {stdout_tail.strip()}")
        return success, new_dirs
    except subprocess.TimeoutExpired:
        print(f"  TIMEOUT")
        return False, set()


def install_old_forge(mc_ver: str, installer_jar: Path) -> tuple:
    print(f"  Extracting old Forge version data...")
    try:
        with zipfile.ZipFile(str(installer_jar)) as zf:
            names = zf.namelist()
            profile_entry = None
            for n in names:
                if n.endswith("install_profile.json") or n == "install_profile.json":
                    profile_entry = n
                    break
            if not profile_entry:
                print(f"  No install_profile.json in installer")
                return False, set()

            profile = json.loads(zf.read(profile_entry))

        target = profile.get("install", {}).get("target", "")
        if not target:
            target = profile.get("versionInfo", {}).get("id", "")

        version_info = profile.get("versionInfo", profile.get("versionInfo", {}))
        mc_version = profile.get("install", {}).get("minecraft", mc_ver)

        if not target or not version_info:
            print(f"  Missing target/versionInfo in profile")
            return False, set()

        forge_vj_dir = VERSIONS_DIR / target
        forge_vj_dir.mkdir(parents=True, exist_ok=True)
        forge_vj = forge_vj_dir / f"{target}.json"
        forge_vj.write_text(json.dumps(version_info, indent=2), encoding="utf-8")

        print(f"  Created: {forge_vj}")

        if not download_vanilla(mc_version):
            print(f"  WARNING: Vanilla {mc_version} download failed")

        before = _scan_versions()
        after = _scan_versions()
        return True, after - before
    except Exception as e:
        print(f"  ERROR: {e}")
        return False, set()


def install_one(mc_ver: str, loader: str, force: bool = False):
    existing = _find_installed(mc_ver, loader)
    if existing and not force:
        print(f"  SKIP {existing} (installed)")
        _ensure_parents(existing)
        return "skip"

    if loader == "forge":
        url, cache = _get_forge_url(mc_ver)
    elif loader == "neoforge":
        url, cache = _get_neoforge_url(mc_ver)
    else:
        return None

    if not url:
        return None

    label = f"{mc_ver}/{loader}"
    print(f"\n>>> Installing {label}...")
    try:
        installer = download_installer(url, cache)

        if loader == "forge" and _is_old_forge(mc_ver):
            ok, new_dirs = install_old_forge(mc_ver, installer)
        else:
            ok, new_dirs = install_modern_forge(installer)

        if ok or new_dirs:
            for d in sorted(new_dirs):
                if loader in d or mc_ver in d:
                    print(f"  OK: {d}")
                    _ensure_parents(d)
                    return "ok"
            if new_dirs:
                print(f"  OK (new): {', '.join(sorted(new_dirs))}")
                return "ok"
            installed = _find_installed(mc_ver, loader)
            if installed:
                print(f"  OK: {installed}")
                _ensure_parents(installed)
                return "ok"
        print(f"  FAIL: {label}")
        return "fail"
    except Exception as e:
        print(f"  ERROR: {e}")
        return "fail"


def _ensure_parents(version_name: str):
    vj_path = VERSIONS_DIR / version_name / f"{version_name}.json"
    if not vj_path.exists():
        return
    try:
        vj = json.loads(vj_path.read_text(encoding="utf-8"))
    except Exception:
        return
    parent = vj.get("inheritsFrom")
    if parent and not (VERSIONS_DIR / parent / f"{parent}.json").exists():
        download_vanilla(parent)


def main():
    parser = argparse.ArgumentParser(description="Install Forge/NeoForge for all versions")
    parser.add_argument("--mc", help="Install specific MC version only")
    parser.add_argument("--force", action="store_true", help="Reinstall even if installed")
    parser.add_argument("--loader", choices=["forge", "neoforge"], help="Loader only")
    parser.add_argument("--vanilla-only", action="store_true",
                        help="Only download missing parent vanilla version JSONs")
    args = parser.parse_args()

    results = {"ok": 0, "skip": 0, "fail": 0, "none": 0}
    start = time.time()

    if args.vanilla_only:
        print("Downloading missing parent vanilla version JSONs...")
        for mc_ver in ALL_VERSIONS:
            for loader in get_loaders(mc_ver):
                installed = _find_installed(mc_ver, loader)
                if installed:
                    _ensure_parents(installed)
        print("Done.")
        return 0

    versions = [args.mc] if args.mc else list(ALL_VERSIONS.keys())

    for mc_ver in versions:
        info = ALL_VERSIONS.get(mc_ver)
        if not info:
            print(f"\n  UNKNOWN: {mc_ver}")
            results["none"] += 1
            continue
        loaders = [args.loader] if args.loader else get_loaders(mc_ver)
        for loader in loaders:
            r = install_one(mc_ver, loader, force=args.force)
            if r:
                results[r] += 1
            else:
                results["none"] += 1

    elapsed = time.time() - start
    print(f"\n{'='*60}")
    print(f"Results: {results['ok']} installed, {results['skip']} skipped, "
          f"{results['fail']} failed, {results['none']} N/A")
    print(f"Time: {elapsed:.0f}s ({elapsed/60:.1f}min)")
    print(f"{'='*60}")
    return 0 if results["fail"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())

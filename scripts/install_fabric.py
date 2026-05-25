"""Install Fabric Loader client profiles for all versions defined in version_config.

Creates version JSONs in .minecraft/versions/{mc_ver}-fabric/ that reference
the vanilla game jars + fabric-loader, and downloads all required libraries.

Usage:
  python scripts/install_fabric.py                # install all missing
  python scripts/install_fabric.py --force         # reinstall all
  python scripts/install_fabric.py --mc 1.20.6     # install specific version
"""

import argparse
import json
import os
import re
import sys
import urllib.request
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parent
ROOT = SCRIPTS.parent
MC_DIR = Path(os.environ.get("APPDATA", os.path.expanduser("~"))) / ".minecraft"
VERSIONS_DIR = MC_DIR / "versions"
LIBRARIES_DIR = MC_DIR / "libraries"

sys.path.insert(0, str(SCRIPTS))
from version_config import ALL_VERSIONS, get_loaders

VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
FABRIC_MAVEN = "https://maven.fabricmc.net"

_manifest_cache = None


def _fetch_json(url: str) -> dict:
    req = urllib.request.Request(url, headers={
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    })
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read())


def _get_manifest() -> dict:
    global _manifest_cache
    if _manifest_cache is None:
        print("  Fetching MC version manifest...")
        _manifest_cache = _fetch_json(VERSION_MANIFEST)
    return _manifest_cache


def _download_file(url: str, dest: Path):
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists():
        return False
    print(f"    Downloading {dest.name}...")
    req = urllib.request.Request(url, headers={
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    })
    with urllib.request.urlopen(req, timeout=120) as resp:
        data = resp.read()
    with open(dest, "wb") as f:
        f.write(data)
    return True


def _get_fabric_loader_version(build_gradle_path: Path) -> str:
    content = build_gradle_path.read_text(encoding="utf-8")
    m = re.search(r'fabric-loader:([^"\']+)', content)
    if m:
        return m.group(1)
    return "0.15.11"


def _lib_name_to_path(name: str) -> Path:
    parts = name.split(":")
    group = parts[0].replace(".", "/")
    artifact = parts[1]
    version = parts[2] if len(parts) > 2 else ""
    classifier = parts[3] if len(parts) > 3 else ""
    filename = f"{artifact}-{version}" + (f"-{classifier}" if classifier else "") + ".jar"
    return LIBRARIES_DIR / group / artifact / version / filename


def _download_libraries(profile: dict):
    dl_count = 0
    for lib in profile.get("libraries", []):
        name = lib.get("name", "")
        if not name:
            continue
        downloads = lib.get("downloads", {})
        artifact = downloads.get("artifact", {})
        url = artifact.get("url", "")
        path_str = artifact.get("path", "")
        if url:
            if path_str:
                dest = LIBRARIES_DIR / path_str
            else:
                dest = _lib_name_to_path(name)
            if _download_file(url, dest):
                dl_count += 1
        elif "url" in lib:
            maven_url = lib["url"]
            lib_path = _lib_name_to_path(name)
            if not lib_path.exists():
                parts = name.split(":")
                group = parts[0].replace(".", "/")
                artifact = parts[1]
                version = parts[2] if len(parts) > 2 else ""
                filename = f"{artifact}-{version}.jar"
                jar_url = f"{maven_url}{group}/{artifact}/{version}/{filename}"
                try:
                    if _download_file(jar_url, lib_path):
                        dl_count += 1
                except Exception:
                    pass
    return dl_count


def _ensure_vanilla_version_json(mc_ver: str) -> dict:
    version_dir = VERSIONS_DIR / mc_ver
    vj_path = version_dir / f"{mc_ver}.json"
    if vj_path.exists():
        with open(vj_path, "r", encoding="utf-8") as f:
            return json.load(f)

    manifest = _get_manifest()
    for v in manifest.get("versions", []):
        if v["id"] == mc_ver:
            print(f"  Downloading vanilla {mc_ver} JSON...")
            vj = _fetch_json(v["url"])
            version_dir.mkdir(parents=True, exist_ok=True)
            with open(vj_path, "w", encoding="utf-8") as f:
                json.dump(vj, f, indent=2)
            _download_libraries(vj)
            return vj
    raise RuntimeError(f"Vanilla version {mc_ver} not found in manifest")


def install_fabric(mc_ver: str, force: bool = False):
    info = ALL_VERSIONS.get(mc_ver, {})
    if "fabric_yarn" not in info:
        print(f"  {mc_ver}: no fabric config, skipping")
        return False

    loader_ver = _get_fabric_loader_version(
        ROOT / "packages" / "mods" / mc_ver / "fabric" / "build.gradle"
    )
    profile_id = f"{mc_ver}-fabric"
    version_dir = VERSIONS_DIR / profile_id
    vj_path = version_dir / f"{profile_id}.json"

    if vj_path.exists() and not force:
        print(f"  {profile_id}: already installed")
        return True

    print(f"  Installing {profile_id} (loader {loader_ver})...")

    _ensure_vanilla_version_json(mc_ver)

    profile_url = f"https://meta.fabricmc.net/v2/versions/loader/{mc_ver}/{loader_ver}/profile/json"
    print(f"    Fetching profile from meta.fabricmc.net...")
    fabric_profile = _fetch_json(profile_url)

    dl_count = _download_libraries(fabric_profile)
    if dl_count:
        print(f"    Downloaded {dl_count} libraries")

    version_dir.mkdir(parents=True, exist_ok=True)
    with open(vj_path, "w", encoding="utf-8") as f:
        json.dump(fabric_profile, f, indent=2)

    print(f"  {profile_id}: installed OK")
    return True


def main():
    parser = argparse.ArgumentParser(description="Install Fabric Loader client profiles")
    parser.add_argument("--mc", help="Specific MC version to install")
    parser.add_argument("--force", action="store_true", help="Reinstall if already exists")
    args = parser.parse_args()

    VERSIONS_DIR.mkdir(parents=True, exist_ok=True)

    target = args.mc
    ok, fail, skip = 0, 0, 0

    for mc_ver in sorted(ALL_VERSIONS.keys()):
        if target and mc_ver != target:
            continue
        loaders = get_loaders(mc_ver)
        if "fabric" not in loaders:
            continue
        try:
            result = install_fabric(mc_ver, force=args.force)
            if result:
                ok += 1
            else:
                skip += 1
        except Exception as e:
            print(f"  FAIL {mc_ver}: {e}")
            fail += 1

    print(f"\nDone: {ok} installed, {fail} failed, {skip} skipped")


if __name__ == "__main__":
    main()

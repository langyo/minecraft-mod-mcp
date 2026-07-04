#!/usr/bin/env python3
"""Force-rebuild every mod JAR (fabric/forge/neoforge) on Windows.

The committed JARs drift out of sync with the mod sources; this script clears
each project's build/ and re-runs `gradlew build`, so the JARs the launcher
picks up contain the current mixin classes. Picks the right JDK per project
from version_config.JDK_PATHS and runs N builds in parallel.

Usage:
  python scripts/win_rebuild_mods.py [--loader fabric] [--filter 1.20*] [--force]
  python scripts/win_rebuild_mods.py --loader forge --filter "1.21.11,26.1.2"
"""
from __future__ import annotations

import fnmatch
import os
import re
import shutil
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from version_config import ALL_VERSIONS, get_loaders, get_fg_era  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODS = os.path.join(ROOT, "packages", "mods")
PARALLEL = int(os.environ.get("BUILD_PARALLEL", "3"))
# Local HTTP proxy for the (slow) Forge/NeoForge/Fabric maven downloads.
PROXY_URL = os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy") or os.environ.get("HTTP_PROXY") or os.environ.get("http_proxy") or ""
# Cache: java major → java.exe path. Discovered lazily via the CLI's detector.
_JAVA_CACHE: dict[int, str] = {}


def _discover_javas() -> dict[int, str]:
    """Map java major → bin/java.exe path by scanning .jdks and gradle jdks."""
    found: dict[int, str] = {}
    for base in (
        os.path.join(ROOT, ".jdks"),
        os.path.join(os.path.expanduser("~"), ".gradle", "jdks"),
    ):
        if not os.path.isdir(base):
            continue
        for d in os.listdir(base):
            full = os.path.join(base, d)
            release = os.path.join(full, "release")
            if not os.path.isfile(release):
                continue
            try:
                with open(release, encoding="utf-8", errors="ignore") as f:
                    for line in f:
                        if line.startswith("JAVA_VERSION="):
                            ver_str = line.split("=", 1)[1].strip().strip('"')
                            parts = ver_str.split(".")
                            major = int(parts[0])
                            if major == 1 and len(parts) > 1:
                                major = int(parts[1])
                            exe = os.path.join(full, "bin", "java.exe")
                            if os.path.isfile(exe) and major not in found:
                                found[major] = exe
                            break
            except Exception:
                pass
    # Also check the Microsoft/Amazon JDKs on PATH-style locations.
    for extra in ("C:\\Program Files\\Amazon Corretto", "C:\\Program Files\\Eclipse Adoptium"):
        if not os.path.isdir(extra):
            continue
        for d in os.listdir(extra):
            exe = os.path.join(extra, d, "bin", "java.exe")
            rel = os.path.join(extra, d, "release")
            if os.path.isfile(exe) and os.path.isfile(rel):
                try:
                    with open(rel, encoding="utf-8", errors="ignore") as f:
                        for line in f:
                            if line.startswith("JAVA_VERSION="):
                                ver = line.split("=", 1)[1].strip().strip('"')
                                parts = ver.split(".")
                                major = int(parts[1]) if parts[0] == "1" else int(parts[0])
                                if major not in found:
                                    found[major] = exe
                except Exception:
                    pass
    return found


def java_exe_for(major: int) -> str:
    if not _JAVA_CACHE:
        _JAVA_CACHE.update(_discover_javas())
    # Prefer exact, then any >= major.
    if _JAVA_CACHE.get(major):
        return _JAVA_CACHE[major]
    for m in sorted(_JAVA_CACHE):
        if m >= major:
            return _JAVA_CACHE[m]
    raise RuntimeError(f"No JDK >= {major} found")


def java_home_for(major: int) -> str:
    exe = java_exe_for(major)
    # bin/java.exe → home is two parents up.
    return os.path.dirname(os.path.dirname(exe))


def projects(loader: str | None, filter_patterns: list[str]) -> list[tuple[str, str]]:
    out = []
    for mc in ALL_VERSIONS:
        if filter_patterns and not any(fnmatch.fnmatch(mc, p) for p in filter_patterns):
            continue
        for ld in get_loaders(mc):
            if loader and ld != loader:
                continue
            proj = os.path.join(MODS, mc, ld)
            if os.path.isfile(os.path.join(proj, "build.gradle")):
                out.append((mc, ld))
    return out


def build_one(mc: str, loader: str, force: bool) -> tuple[str, str, bool, str]:
    proj = os.path.join(MODS, mc, loader)
    gradlew = os.path.join(proj, "gradlew.bat")
    if not os.path.isfile(gradlew):
        gradlew = os.path.join(proj, "gradlew")
    if not os.path.isfile(gradlew):
        return mc, loader, False, "no gradlew"

    info = ALL_VERSIONS[mc]
    java_major = info.get("java", 17)
    if loader == "fabric":
        java_major = max(java_major, 17)
    elif loader in ("forge", "neoforge"):
        era = get_fg_era(mc)
        if era:
            java_major = era.get("java", java_major)

    try:
        java_home = java_home_for(java_major)
    except RuntimeError as e:
        return mc, loader, False, str(e)

    if force:
        shutil.rmtree(os.path.join(proj, "build"), ignore_errors=True)

    # gradle.properties: tame memory + disable daemon so parallel builds don't OOM.
    try:
        with open(os.path.join(proj, "gradle.properties"), "w") as f:
            f.write("org.gradle.jvmargs=-Xmx2g\norg.gradle.daemon=false\norg.gradle.console=plain\n")
    except Exception:
        pass

    env = dict(os.environ)
    env["JAVA_HOME"] = java_home
    env["PATH"] = os.path.join(java_home, "bin") + os.pathsep + env.get("PATH", "")
    # Route Gradle's dependency downloads through the local proxy if set.
    proxy = PROXY_URL
    if proxy:
        for k in ("HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy"):
            env[k] = proxy
    t0 = time.time()
    try:
        r = subprocess.run(
            [gradlew, "build", "--no-daemon", "--console=plain"],
            cwd=proj, env=env, capture_output=True, timeout=1200,
            shell=True,
        )
        # Decode bytes with errors="replace" — Gradle on Windows emits the
        # local console codepage (GBK on zh-CN), not UTF-8, so str-mode
        # capture_output would raise UnicodeDecodeError on Chinese text.
        out = (r.stdout or b"").decode("utf-8", "replace") + (r.stderr or b"").decode("utf-8", "replace")
        dt = time.time() - t0
        libs = os.path.join(proj, "build", "libs")
        has_jar = False
        if os.path.isdir(libs):
            for f in os.listdir(libs):
                if f.endswith(".jar") and not f.endswith(("-sources.jar", "-javadoc.jar")):
                    has_jar = True
                    break
        if r.returncode == 0 and has_jar:
            return mc, loader, True, f"built in {dt:.0f}s"
        tail = out.strip()[-200:]
        return mc, loader, False, f"rc={r.returncode} {tail}"
    except subprocess.TimeoutExpired:
        return mc, loader, False, "timeout 1200s"
    except Exception as e:
        return mc, loader, False, f"{type(e).__name__}: {e}"


def main():
    import argparse
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--loader", choices=["fabric", "forge", "neoforge"],
                    help="Restrict to one loader (default: all)")
    ap.add_argument("--filter", default="*",
                    help="Comma-separated version globs (default: *)")
    ap.add_argument("--force", action="store_true",
                    help="Delete build/ before building (rebuild even if JAR exists)")
    ap.add_argument("--list", action="store_true", help="List projects and exit")
    ap.add_argument("--proxy", metavar="URL", default=PROXY_URL,
                    help=f"HTTP proxy for maven downloads (default: env HTTPS_PROXY or {PROXY_URL or 'none'})")
    args = ap.parse_args()

    # Apply proxy globally so build_one()'s env inherits it.
    if args.proxy:
        global PROXY_URL
        PROXY_URL = args.proxy
        for k in ("HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy"):
            os.environ[k] = args.proxy

    patterns = [p.strip() for p in args.filter.split(",") if p.strip()]
    projs = projects(args.loader, patterns)
    if args.list:
        for mc, ld in projs:
            print(f"  {mc:<10} {ld}")
        print(f"\n{len(projs)} projects")
        return

    if not projs:
        print("No projects match.")
        return 1

    print(f"Building {len(projs)} projects ({PARALLEL}-way parallel)...\n")
    ok, fail = [], []
    t0 = time.time()
    with ThreadPoolExecutor(max_workers=PARALLEL) as pool:
        futures = {pool.submit(build_one, mc, ld, args.force): (mc, ld) for mc, ld in projs}
        for fut in as_completed(futures):
            mc, ld, success, msg = fut.result()
            status = "OK  " if success else "FAIL"
            line = f"  [{status}] {mc:<10} {ld:<9} {msg[:70]}"
            print(line, flush=True)
            (ok if success else fail).append((mc, ld))

    print(f"\nDone in {time.time()-t0:.0f}s: {len(ok)} built, {len(fail)} failed.")
    if fail:
        print("Failed:")
        for mc, ld in fail:
            print(f"  {mc} {ld}")
    return 0 if not fail else 1


if __name__ == "__main__":
    sys.exit(main())

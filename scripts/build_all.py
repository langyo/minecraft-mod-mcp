"""Batch build ALL mod projects and report results.

Sets correct JAVA_HOME per FG era and loader.
FG 1.2 builds get special handling: HTTP server for versions.json,
FG jar patch for URL redirect, MCP cache pre-population.

Usage:
  python scripts/build_all.py
  python scripts/build_all.py --era fg51
  python scripts/build_all.py --loader forge
  python scripts/build_all.py --mc 1.20.1
"""
import subprocess
import sys
import os
import time
import json
import argparse
import threading
import hashlib
import shutil
from concurrent.futures import ThreadPoolExecutor, as_completed

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from version_config import (
    ALL_VERSIONS, FG_ERAS, MODS_DIR,
    get_loaders, get_fg_era, get_jdk_home, find_jdk17, is_legacy, JDK_PATHS,
)

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODS_DIR_ACTUAL = MODS_DIR

BUILD_TIMEOUT = 1200
TEMP_DIR = os.path.join(os.environ.get("TEMP", "C:\\Temp"), "opencode")
GRADLE_USER_HOME = os.path.join(os.path.expanduser("~"), ".gradle")
MODULES_CACHE = os.path.join(GRADLE_USER_HOME, "caches", "modules-2", "files-2.1")


def sha1_file(path):
    h = hashlib.sha1()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


_http_server_proc = None


def ensure_mcp_stable_in_fg_cache(mc, info):
    mappings = info.get("mappings", "")
    if not mappings or "_" not in mappings:
        return
    channel, ver = mappings.split("_", 1)
    forge_ver = info.get("forge", "")
    mcp_full_ver = f"{ver}-{mc}"
    src_dir = os.path.join(MODULES_CACHE, "de.oceanlabs.mcp", f"mcp_{channel}", mcp_full_ver)
    src_zip = None
    if os.path.isdir(src_dir):
        for d in os.listdir(src_dir):
            candidate = os.path.join(src_dir, d, f"mcp_{channel}-{mcp_full_ver}.zip")
            if os.path.isfile(candidate):
                src_zip = candidate
                break
    if not src_zip:
        return
    cache_dir = os.path.join(
        GRADLE_USER_HOME, "caches", "minecraft", "net", "minecraftforge", "forge",
        forge_ver, channel, ver,
    )
    os.makedirs(cache_dir, exist_ok=True)
    for name in (f"mcp_{channel}-{mcp_full_ver}.zip", "mcp_stable.zip"):
        dst = os.path.join(cache_dir, name)
        if not os.path.isfile(dst):
            shutil.copy2(src_zip, dst)


def resolve_java_home(mc, info, loader="forge"):
    fg_era = info.get("fg_era", "")
    if loader == "forge" and fg_era in ("fg21", "fg22", "fg23", "fg3"):
        return get_jdk_home(8) or "C:\\Users\\langy\\.jdks\\jdk8"
    if fg_era == "fg41":
        jdk21 = get_jdk_home(21)
        if jdk21:
            return jdk21
        return find_jdk17() or get_jdk_home(17) or get_jdk_home(8)
    if loader == "fabric":
        java_ver = info.get("java", 17)
        if java_ver in (21, 25):
            jdk21 = get_jdk_home(21)
            if jdk21:
                return jdk21
        jdk17 = find_jdk17()
        if jdk17:
            return jdk17
        jdk21 = get_jdk_home(21)
        if jdk21:
            return jdk21
    java_ver = info.get("java", 8)
    jdk = get_jdk_home(java_ver)
    if jdk:
        return jdk
    if java_ver in (16, 17):
        return find_jdk17()
    if java_ver in (21, 25):
        jdk21 = get_jdk_home(21)
        if jdk21:
            return jdk21
    return None


_print_lock = threading.Lock()


def _build_one(task_info):
    mc, loader, info = task_info
    path = os.path.join(MODS_DIR_ACTUAL, mc, loader)
    key = f"{mc}/{loader}"

    if not os.path.isdir(path):
        return key, "skip", {"key": key, "reason": "no project dir"}

    gradlew = os.path.join(path, "gradlew.bat")
    if not os.path.isfile(gradlew):
        return key, "skip", {"key": key, "reason": "no gradlew"}

    env = os.environ.copy()
    jdk = resolve_java_home(mc, info, loader)
    if jdk:
        env["JAVA_HOME"] = jdk

    gp = os.path.join(path, "gradle.properties")
    with open(gp, "w") as f:
        f.write("org.gradle.jvmargs=-Xmx3G\n")
    env.pop("JAVA_TOOL_OPTIONS", None)
    env["GRADLE_OPTS"] = "-Xmx3G"

    start = time.time()
    cmd = ["cmd", "/c", "gradlew.bat", "build", "--no-daemon", "--rerun-tasks"]
    try:
        proc = subprocess.run(
            cmd,
            cwd=path,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=BUILD_TIMEOUT,
            env=env,
        )
        elapsed = time.time() - start
        out = (proc.stdout or "") + (proc.stderr or "")
        if proc.returncode == 0 and "BUILD SUCCESSFUL" in out:
            return key, "success", {"key": key, "time": round(elapsed, 1)}
        else:
            err_msg = out[-2000:] if len(out) > 2000 else out
            log_path = os.path.join(path, "build-error.log")
            with open(log_path, "w", encoding="utf-8") as lf:
                lf.write(out)
            return key, "fail", {"key": key, "time": round(elapsed, 1), "error": err_msg}
    except subprocess.TimeoutExpired:
        elapsed = time.time() - start
        return key, "fail", {"key": key, "time": round(elapsed, 1), "error": "TIMEOUT"}
    except Exception as e:
        return key, "fail", {"key": key, "error": str(e)}


def main():
    parser = argparse.ArgumentParser(description="Batch build all mod projects")
    parser.add_argument("--era", help="Only build specific FG era (e.g. fg51, fg6)")
    parser.add_argument("--loader", help="Only build specific loader (forge, neoforge, fabric)")
    parser.add_argument("--mc", help="Only build specific MC version")
    parser.add_argument("--no-cache", action="store_true", help="Skip prepare_cache step")
    parser.add_argument("-j", "--jobs", type=int, default=4, help="Parallel workers (default: 4)")
    args = parser.parse_args()

    if not args.no_cache:
        print("Running prepare_cache.py first...")
        ret = subprocess.run(
            [sys.executable, os.path.join(os.path.dirname(__file__), "prepare_cache.py")],
            cwd=BASE,
        )
        if ret.returncode != 0:
            print("WARNING: prepare_cache had failures, continuing anyway...")
        print()

    results = {"success": [], "fail": [], "skip": []}
    tasks = []

    for mc, info in sorted(ALL_VERSIONS.items()):
        if args.mc and mc != args.mc:
            continue
        if args.era and info.get("fg_era") != args.era:
            continue
        loaders = get_loaders(mc)
        if args.loader and args.loader not in loaders:
            continue
        for loader in loaders:
            tasks.append((mc, loader, info))

    total = len(tasks)

    print(f"Building {total} projects with {args.jobs} workers...")
    if args.era:
        print(f"  Filter era: {args.era}")
    if args.loader:
        print(f"  Filter loader: {args.loader}")
    if args.mc:
        print(f"  Filter mc: {args.mc}")
    print()

    done = 0
    start_all = time.time()

    with ThreadPoolExecutor(max_workers=args.jobs) as pool:
        future_map = {pool.submit(_build_one, t): t for t in tasks}
        for future in as_completed(future_map):
            done += 1
            key, status, entry = future.result()
            elapsed = entry.get("time", 0)
            with _print_lock:
                tag = {"success": "OK", "fail": "FAIL", "skip": "SKIP"}[status]
                print(f"[{done}/{total}] {tag} {key} ({elapsed:.1f}s)")
            results[status].append(entry)

    total_time = time.time() - start_all
    print(f"\n{'=' * 60}")
    s, f, sk = len(results["success"]), len(results["fail"]), len(results["skip"])
    print(f"Results: {s} OK, {f} FAIL, {sk} SKIP / {total} total")
    print(f"Total time: {total_time:.0f}s ({total_time / 60:.1f}min) with {args.jobs} workers")
    print(f"{'=' * 60}")

    if results["fail"]:
        print("\nFAILURES:")
        for entry in results["fail"]:
            err_text = entry.get('error', '')[:200]
            safe = err_text.encode('ascii', errors='replace').decode('ascii')
            print(f"  {entry['key']}: {safe}")

    report_path = os.path.join(BASE, "build-report.json")
    with open(report_path, "w") as fp:
        json.dump(results, fp, indent=2)
    print(f"\nReport saved to {report_path}")


if __name__ == "__main__":
    main()

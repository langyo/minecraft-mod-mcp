"""Build all mods and publish JARs to GitHub Release.

Builds common library, then all mod JARs with correct JDK per version,
collects the artifacts, and creates/updates a GitHub Release via `gh`.

Can be used locally or from CI.

Usage:
  python scripts/release.py v0.1.0
  python scripts/release.py v0.1.0 --no-upload
  python scripts/release.py v0.1.0 --loader forge --loader fabric
  python scripts/release.py v0.1.0 --mc 1.21.11
"""
import subprocess
import sys
import os
import time
import json
import argparse
import shutil
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from version_config import (
    ALL_VERSIONS, MODS_DIR,
    get_loaders, get_jdk_home, find_jdk17,
)
from mirrors import probe_all as probe_mirrors, patch_all_wrappers, generate_init_gradle

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BUILD_TIMEOUT = 1200


def strip_v(version):
    return version[1:] if version.startswith("v") else version


def resolve_java_home(mc, info, loader="forge"):
    fg_era = info.get("fg_era", "")
    if loader == "forge" and fg_era in ("fg21", "fg22", "fg23", "fg3"):
        return get_jdk_home(8)
    if fg_era == "fg41":
        return get_jdk_home(21) or find_jdk17() or get_jdk_home(17) or get_jdk_home(8)
    if loader == "fabric":
        java_ver = info.get("java", 17)
        if java_ver in (21, 25):
            jdk21 = get_jdk_home(21)
            if jdk21:
                return jdk21
        return find_jdk17() or get_jdk_home(21)
    java_ver = info.get("java", 8)
    jdk = get_jdk_home(java_ver)
    if jdk:
        return jdk
    if java_ver in (16, 17):
        return find_jdk17()
    if java_ver in (21, 25):
        return get_jdk_home(21)
    return None


_print_lock = threading.Lock()


def build_common(tag):
    print("=== Building common library ===")
    mc_common = os.path.join(BASE, "packages", "common")
    gradlew = os.path.join(mc_common, "gradlew.bat") if sys.platform == "win32" else os.path.join(mc_common, "gradlew")
    if not os.path.isfile(gradlew):
        gradlew = os.path.join(BASE, "gradlew.bat") if sys.platform == "win32" else os.path.join(BASE, "gradlew")

    env = os.environ.copy()
    env["GRADLE_OPTS"] = "-Xmx2G"

    r = subprocess.run(
        [gradlew, "clean", "publish", "--no-daemon", "--console=plain"],
        cwd=mc_common,
        env=env,
    )
    if r.returncode != 0:
        print("ERROR: common library build failed")
        sys.exit(1)
    print("  Common library OK\n")


def _build_one(task_info):
    mc, loader, info = task_info
    path = os.path.join(MODS_DIR, mc, loader)
    key = f"{mc}/{loader}"

    if not os.path.isdir(path):
        return key, "skip", {"key": key, "reason": "no project dir"}

    gradlew = os.path.join(path, "gradlew.bat") if sys.platform == "win32" else os.path.join(path, "gradlew")
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
    gradlew_name = "gradlew.bat" if sys.platform == "win32" else "./gradlew"
    cmd = [gradlew_name, "clean", "jar", "--no-daemon", "--rerun-tasks", "--console=plain"]
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


def collect_jars(dist_dir, tag):
    tag_clean = strip_v(tag)
    os.makedirs(dist_dir, exist_ok=True)

    count = 0
    for mc, info in sorted(ALL_VERSIONS.items()):
        for loader in get_loaders(mc):
            jar_dir = os.path.join(MODS_DIR, mc, loader, "build", "libs")
            if not os.path.isdir(jar_dir):
                continue
            for jar in os.listdir(jar_dir):
                if not jar.endswith(".jar"):
                    continue
                if any(s in jar for s in ("-sources", "-dev", "-slim")):
                    continue
                src = os.path.join(jar_dir, jar)
                new_name = f"minecraft-mcp-{mc}-{loader}-{tag_clean}.jar"
                dst = os.path.join(dist_dir, new_name)
                shutil.copy2(src, dst)
                size_kb = os.path.getsize(src) / 1024
                print(f"  {new_name} ({size_kb:.1f} KB)")
                count += 1

    print(f"\n  Collected {count} JARs to {dist_dir}/")
    return count


def create_release(tag, dist_dir, dry_run=False):
    jars = sorted([f for f in os.listdir(dist_dir) if f.endswith(".jar")])
    if not jars:
        print("ERROR: no JARs found in dist/")
        sys.exit(1)

    jar_args = []
    for jar in jars:
        jar_args.extend(["--file", os.path.join(dist_dir, jar)])

    cmd = [
        "gh", "release", "create", tag,
        "--title", tag,
        "--notes", f"## {tag}\n\nMod JARs for {len(jars)} version+loader combinations.\n\nSee [README](https://github.com/langyo/minecraft-mod-mcp#readme) for usage.",
    ] + jar_args

    if dry_run:
        print("Dry run — would run:")
        print("  " + " ".join(cmd))
        return

    print(f"\n=== Creating GitHub Release {tag} with {len(jars)} JARs ===")
    r = subprocess.run(cmd, cwd=BASE)
    if r.returncode != 0:
        print("ERROR: gh release create failed")
        sys.exit(1)
    print(f"Release {tag} published!")


def main():
    parser = argparse.ArgumentParser(description="Build mods and publish GitHub Release")
    parser.add_argument("tag", help="Release tag (e.g. v0.1.0)")
    parser.add_argument("--no-upload", action="store_true", help="Build and collect JARs only, skip gh release")
    parser.add_argument("--loader", action="append", help="Only build specific loader(s)")
    parser.add_argument("--mc", help="Only build specific MC version")
    parser.add_argument("-j", "--jobs", type=int, default=4, help="Parallel workers (default: 4)")
    args = parser.parse_args()

    tag = args.tag
    tag_clean = strip_v(tag)
    dist_dir = os.path.join(BASE, "dist")

    print(f"=== Release: {tag} ===\n")

    build_common(tag)

    probe_mirrors()
    patched = patch_all_wrappers(BASE)
    if patched > 0:
        print(f"  Patched {patched} gradle-wrapper.properties\n")
    generate_init_gradle()

    results = {"success": [], "fail": [], "skip": []}
    tasks = []

    for mc, info in sorted(ALL_VERSIONS.items()):
        if args.mc and mc != args.mc:
            continue
        loaders = get_loaders(mc)
        for loader in loaders:
            if args.loader and loader not in args.loader:
                continue
            tasks.append((mc, loader, info))

    total = len(tasks)
    print(f"Building {total} mod projects with {args.jobs} workers...\n")

    done = 0
    start_all = time.time()

    with ThreadPoolExecutor(max_workers=args.jobs) as pool:
        future_map = {pool.submit(_build_one, t): t for t in tasks}
        for future in as_completed(future_map):
            done += 1
            key, status, entry = future.result()
            elapsed = entry.get("time", 0)
            with _print_lock:
                lbl = {"success": "OK", "fail": "FAIL", "skip": "SKIP"}[status]
                print(f"  [{done}/{total}] {lbl} {key} ({elapsed:.1f}s)")
            results[status].append(entry)

    total_time = time.time() - start_all
    s, f, sk = len(results["success"]), len(results["fail"]), len(results["skip"])
    print(f"\n{'=' * 60}")
    print(f"Build: {s} OK, {f} FAIL, {sk} SKIP / {total} total ({total_time:.0f}s)")

    if results["fail"]:
        print("\nFAILURES:")
        for entry in results["fail"]:
            err_text = entry.get("error", "")[:200]
            safe = err_text.encode("ascii", errors="replace").decode("ascii")
            print(f"  {entry['key']}: {safe}")
        print(f"\n{'=' * 60}")
        sys.exit(1)

    print(f"{'=' * 60}")

    if os.path.isdir(dist_dir):
        shutil.rmtree(dist_dir)

    print(f"\n=== Collecting JARs ===")
    count = collect_jars(dist_dir, tag)

    if args.no_upload:
        print(f"\nDone. {count} JARs in dist/. Upload skipped (--no-upload).")
        return

    create_release(tag, dist_dir)


if __name__ == "__main__":
    main()

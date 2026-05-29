"""Local build + GitHub Release script.

Usage:
  python scripts/local_release.py                  # build all + release
  python scripts/local_release.py --skip-build     # release from existing JARs
  python scripts/local_release.py --mc 1.21.7      # only build specific MC version
  python scripts/local_release.py --dry-run        # show what would be released
"""
import subprocess
import sys
import os
import json
import shutil
import argparse
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
import threading

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from version_config import ALL_VERSIONS, MODS_DIR, get_loaders
from mirrors import probe_all as probe_mirrors, patch_all_wrappers, generate_init_gradle

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIST_DIR = os.path.join(BASE, "dist")


def run_build_common():
    print("=== Building common package ===")
    cmd = [os.path.join(BASE, "gradlew.bat"), ":packages:common:clean",
           ":packages:common:publish", "--no-daemon", "--console=plain"]
    proc = subprocess.run(cmd, cwd=BASE, capture_output=True, text=True,
                          encoding="utf-8", errors="replace", timeout=600)
    if proc.returncode != 0:
        print(f"  FAILED (exit {proc.returncode})")
        print(proc.stdout[-1000:] if len(proc.stdout) > 1000 else proc.stdout)
        print(proc.stderr[-500:] if proc.stderr else "")
        return False
    print("  OK")
    return True


def build_one_mod(task_info):
    mc, loader = task_info
    key = f"{mc}/{loader}"
    path = os.path.join(MODS_DIR, mc, loader)

    if not os.path.isdir(path):
        return key, "skip", "no project dir"

    gradlew = os.path.join(path, "gradlew.bat")
    if not os.path.isfile(gradlew):
        return key, "skip", "no gradlew"

    env = os.environ.copy()
    env["GRADLE_OPTS"] = "-Xmx3G"

    gp = os.path.join(path, "gradle.properties")
    with open(gp, "w") as f:
        f.write("org.gradle.jvmargs=-Xmx3G\n")
    env.pop("JAVA_TOOL_OPTIONS", None)

    start = time.time()
    cmd = ["cmd", "/c", "gradlew.bat", "build", "--no-daemon"]
    try:
        proc = subprocess.run(cmd, cwd=path, capture_output=True, text=True,
                              encoding="utf-8", errors="replace", timeout=1200,
                              env=env)
        elapsed = time.time() - start
        out = (proc.stdout or "") + (proc.stderr or "")
        if proc.returncode == 0 and "BUILD SUCCESSFUL" in out:
            return key, "success", f"{elapsed:.0f}s"
        else:
            return key, "fail", f"exit={proc.returncode} ({elapsed:.0f}s)"
    except subprocess.TimeoutExpired:
        return key, "fail", "TIMEOUT (1200s)"
    except Exception as e:
        return key, "fail", str(e)


def collect_jars():
    if os.path.isdir(DIST_DIR):
        shutil.rmtree(DIST_DIR)
    os.makedirs(DIST_DIR, exist_ok=True)

    count = 0
    for mc_dir in sorted(os.listdir(MODS_DIR)):
        mc_path = os.path.join(MODS_DIR, mc_dir)
        if not os.path.isdir(mc_path):
            continue
        for loader_dir in sorted(os.listdir(mc_path)):
            loader_path = os.path.join(mc_path, loader_dir)
            libs_dir = os.path.join(loader_path, "build", "libs")
            if not os.path.isdir(libs_dir):
                continue
            for jar in os.listdir(libs_dir):
                if not jar.endswith(".jar"):
                    continue
                if "-sources" in jar or "-javadoc" in jar:
                    continue
                src = os.path.join(libs_dir, jar)
                parts = jar.replace(".jar", "").split("-")
                ext_name = f"{mc_dir}-{loader_dir}.jar"
                dst = os.path.join(DIST_DIR, ext_name)
                shutil.copy2(src, dst)
                size_kb = os.path.getsize(dst) / 1024
                print(f"  {ext_name}  ({size_kb:.1f} KB)")
                count += 1

    print(f"\nCollected {count} JARs into {DIST_DIR}/")
    return count


def create_release(tag, dry_run=False):
    jar_files = [os.path.join(DIST_DIR, f) for f in os.listdir(DIST_DIR) if f.endswith(".jar")]
    if not jar_files:
        print("No JARs to release!")
        return False

    short_sha = subprocess.run(["git", "rev-parse", "--short", "HEAD"],
                               capture_output=True, text=True, cwd=BASE).stdout.strip()
    branch = subprocess.run(["git", "branch", "--show-current"],
                            capture_output=True, text=True, cwd=BASE).stdout.strip()

    if not tag:
        tag = f"dev/{short_sha}"

    title = f"Dev Build {short_sha}"
    body = f"Development build from commit `{short_sha}`\nBranch: `{branch}`\n\n"

    jar_list = sorted(os.path.basename(f) for f in jar_files)
    body += f"## JARs ({len(jar_list)})\n"
    for j in jar_list:
        mc, loader = j.replace(".jar", "").rsplit("-", 1)
        body += f"- `{mc}/{loader}`\n"

    if dry_run:
        print(f"\n=== DRY RUN ===")
        print(f"Tag: {tag}")
        print(f"Title: {title}")
        print(f"Body:\n{body}")
        print(f"Files ({len(jar_files)}):")
        for f in sorted(jar_files):
            print(f"  {os.path.basename(f)}")
        return True

    cmd = (["gh", "release", "create", tag]
           + jar_files
           + ["--repo", "langyo/minecraft-mod-mcp",
              "--title", title,
              "--prerelease",
              "--notes", body])

    print(f"\n=== Creating release {tag} ===")
    proc = subprocess.run(cmd, cwd=BASE, capture_output=True, text=True,
                          encoding="utf-8", errors="replace")
    if proc.returncode != 0:
        print(f"FAILED:\n{proc.stderr}\n{proc.stdout}")
        return False

    print(f"OK: {proc.stdout.strip()}")
    return True


def main():
    parser = argparse.ArgumentParser(description="Local build + GitHub Release")
    parser.add_argument("--skip-build", action="store_true", help="Skip build, release existing JARs")
    parser.add_argument("--skip-common", action="store_true", help="Skip common build")
    parser.add_argument("--mc", help="Only build specific MC version")
    parser.add_argument("--loader", help="Only build specific loader")
    parser.add_argument("--tag", help="Release tag (default: dev/<short-sha>)")
    parser.add_argument("--dry-run", action="store_true", help="Show what would be released")
    parser.add_argument("-j", "--jobs", type=int, default=4, help="Parallel build workers")
    args = parser.parse_args()

    if not args.skip_build:
        probe_mirrors()
        patch_all_wrappers(BASE)
        generate_init_gradle()

        if not args.skip_common:
            if not run_build_common():
                print("\nCommon build failed, aborting.")
                sys.exit(1)

        tasks = []
        for mc, info in sorted(ALL_VERSIONS.items()):
            if args.mc and mc != args.mc:
                continue
            for loader in get_loaders(mc):
                if args.loader and loader != args.loader:
                    continue
                tasks.append((mc, loader))

        if tasks:
            print(f"\n=== Building {len(tasks)} mod projects ({args.jobs} workers) ===")
            results = {"success": [], "fail": [], "skip": []}
            done = 0
            _lock = threading.Lock()
            start_all = time.time()

            with ThreadPoolExecutor(max_workers=args.jobs) as pool:
                future_map = {pool.submit(build_one_mod, t): t for t in tasks}
                for future in as_completed(future_map):
                    done += 1
                    key, status, detail = future.result()
                    with _lock:
                        tag = {"success": "OK", "fail": "FAIL", "skip": "SKIP"}[status]
                        print(f"  [{done}/{len(tasks)}] {tag} {key} ({detail})")
                        results[status].append(key)

            elapsed = time.time() - start_all
            s, f, sk = len(results["success"]), len(results["fail"]), len(results["skip"])
            print(f"\nBuild results: {s} OK, {f} FAIL, {sk} SKIP / {len(tasks)} total ({elapsed:.0f}s)")

            if results["fail"]:
                print("FAILURES:")
                for k in results["fail"]:
                    print(f"  {k}")
                print("\nContinuing with successful builds...")
    else:
        print("Skipping build (--skip-build)")

    print("\n=== Collecting JARs ===")
    count = collect_jars()
    if count == 0:
        print("No JARs found! Run build first.")
        sys.exit(1)

    if not create_release(args.tag, args.dry_run):
        sys.exit(1)


if __name__ == "__main__":
    main()

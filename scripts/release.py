"""Build all mods and publish JARs to GitHub Release.

Builds common library, then all mod JARs with correct JDK per version,
collects the artifacts, and creates/updates a GitHub Release via `gh`.

Can be used locally or from CI.

Usage:
  python scripts/release.py v0.1.1
  python scripts/release.py v0.1.1 --no-upload
  python scripts/release.py v0.1.1 --loader forge --loader fabric
  python scripts/release.py v0.1.1 --mc 1.21.11
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
    get_loaders,
)
from mirrors import probe_all as probe_mirrors, patch_all_wrappers, generate_init_gradle

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BUILD_TIMEOUT = 1200
_EXE_SUFFIX = ".exe" if sys.platform == "win32" else ""

_JDK_CACHE = {}


def _find_jdk_home(target_ver):
    if target_ver in _JDK_CACHE:
        return _JDK_CACHE[target_ver]
    # Check .jdks/ first (auto-provisioned)
    project_jdks = os.path.join(BASE, ".jdks")
    if os.path.isdir(project_jdks):
        for d in sorted(os.listdir(project_jdks), reverse=True):
            full = os.path.join(project_jdks, d)
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
                            if major == target_ver:
                                _JDK_CACHE[target_ver] = full
                                return full
                            break
            except Exception:
                pass
    if sys.platform == "win32":
        for base in [r"C:\Program Files\Amazon Corretto", r"C:\Program Files\Eclipse Adoptium"]:
            if os.path.isdir(base):
                for d in sorted(os.listdir(base), reverse=True):
                    if d.startswith(f"jdk{target_ver}.") or d.startswith(f"jdk1.{target_ver}."):
                        path = os.path.join(base, d)
                        if os.path.isfile(os.path.join(path, "bin", f"java{_EXE_SUFFIX}")):
                            _JDK_CACHE[target_ver] = path
                            return path
    jdks_dir = os.path.join(os.path.expanduser("~"), ".gradle", "jdks")
    if os.path.isdir(jdks_dir):
        for d in sorted(os.listdir(jdks_dir), reverse=True):
            if f"-{target_ver}." in d and "lock" not in d:
                path = os.path.join(jdks_dir, d)
                exe = os.path.join(path, "bin", f"java{_EXE_SUFFIX}")
                if os.path.isfile(exe):
                    _JDK_CACHE[target_ver] = path
                    return path
    return None


def strip_v(version):
    return version[1:] if version.startswith("v") else version


def resolve_java_home(mc, info, loader="forge"):
    fg_era = info.get("fg_era", "")
    java_ver = info.get("java", 8)

    if loader == "forge" and fg_era in ("fg12_gtnh", "fg21", "fg22", "fg23", "fg3"):
        return _find_jdk_home(8)
    if loader == "forge" and fg_era == "fg41":
        return _find_jdk_home(8)
    if loader == "fabric":
        if java_ver >= 21:
            jdk21 = _find_jdk_home(21)
            if jdk21:
                return jdk21
        return _find_jdk_home(17) or _find_jdk_home(21)
    if loader == "neoforge":
        jdk = _find_jdk_home(java_ver)
        if jdk:
            return jdk
        return _find_jdk_home(21) or _find_jdk_home(17)
    jdk = _find_jdk_home(java_ver)
    if jdk:
        return jdk
    if java_ver in (16, 17):
        return _find_jdk_home(17)
    if java_ver >= 21:
        return _find_jdk_home(21)
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

    if sys.platform == "win32":
        cmd = [gradlew, "clean", "publish", "--no-daemon", "--console=plain"]
    else:
        cmd = [gradlew, "clean", "publish", "--no-daemon", "--console=plain"]

    r = subprocess.run(cmd, cwd=mc_common, env=env)
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
    else:
        env.pop("JAVA_HOME", None)

    gp = os.path.join(path, "gradle.properties")
    with open(gp, "w") as f:
        f.write("org.gradle.jvmargs=-Xmx3G\n")
    env.pop("JAVA_TOOL_OPTIONS", None)
    env["GRADLE_OPTS"] = "-Xmx3G"
    if loader == "neoforge":
        env["GRADLE_OPTS"] += " -Dsun.net.client.defaultConnectTimeout=30000 -Dsun.net.client.defaultReadTimeout=30000"

    start = time.time()
    if loader == "fabric":
        cmd = [gradlew, "clean", "build", "--no-daemon", "--rerun-tasks", "--console=plain"]
    else:
        cmd = [gradlew, "clean", "jar", "--no-daemon", "--rerun-tasks", "--console=plain"]
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
            new_name = f"minecraft-mcp-{mc}-{loader}.jar"
            found = False
            libs_dir = os.path.join(MODS_DIR, mc, loader, "build", "libs")
            if os.path.isdir(libs_dir):
                for jar in os.listdir(libs_dir):
                    if not jar.endswith(".jar"):
                        continue
                    if any(s in jar for s in ("-sources", "-dev", "-slim")):
                        continue
                    src = os.path.join(libs_dir, jar)
                    dst = os.path.join(dist_dir, new_name)
                    shutil.copy2(src, dst)
                    size_kb = os.path.getsize(src) / 1024
                    print(f"  {new_name} ({size_kb:.1f} KB)")
                    count += 1
                    found = True
                    break
            if found:
                continue
            devlibs_dir = os.path.join(MODS_DIR, mc, loader, "build", "devlibs")
            if os.path.isdir(devlibs_dir):
                for jar in os.listdir(devlibs_dir):
                    if not jar.endswith(".jar") or "-sources" in jar or "-slim" in jar:
                        continue
                    src = os.path.join(devlibs_dir, jar)
                    dst = os.path.join(dist_dir, new_name)
                    shutil.copy2(src, dst)
                    size_kb = os.path.getsize(src) / 1024
                    print(f"  {new_name} ({size_kb:.1f} KB) [dev]")
                    count += 1
                    break

    print(f"\n  Collected {count} JARs to {dist_dir}/")
    return count


def _get_previous_tag():
    r = subprocess.run(
        ["git", "tag", "--sort=-version:refname"],
        capture_output=True, text=True, cwd=BASE, encoding="utf-8", errors="replace",
    )
    tags = [t.strip() for t in r.stdout.strip().split("\n") if t.strip()]
    return tags[0] if tags else None


def _generate_release_notes(tag):
    prev_tag = _get_previous_tag()
    if prev_tag == tag:
        all_tags = [t.strip() for t in subprocess.run(
            ["git", "tag", "--sort=-version:refname"],
            capture_output=True, text=True, cwd=BASE, encoding="utf-8", errors="replace",
        ).stdout.strip().split("\n") if t.strip()]
        idx = all_tags.index(tag)
        prev_tag = all_tags[idx + 1] if idx + 1 < len(all_tags) else None

    if prev_tag:
        r = subprocess.run(
            ["git", "log", "--format=- %s", f"{prev_tag}..HEAD", "master"],
            capture_output=True, text=True, cwd=BASE, encoding="utf-8", errors="replace",
        )
        commits = r.stdout.strip()
    else:
        r = subprocess.run(
            ["git", "log", "--format=- %s", "master"],
            capture_output=True, text=True, cwd=BASE, encoding="utf-8", errors="replace",
        )
        commits = r.stdout.strip()

    lines = [
        f"## {tag}",
        "",
    ]
    if commits:
        lines.append("### Changes")
        lines.append(commits)
        lines.append("")
    return "\n".join(lines)


def create_release(tag, dist_dir, dry_run=False):
    jars = sorted([f for f in os.listdir(dist_dir) if f.endswith(".jar")])
    if not jars:
        print("ERROR: no JARs found in dist/")
        sys.exit(1)

    jar_paths = [os.path.join(dist_dir, jar) for jar in jars]
    notes = _generate_release_notes(tag)

    notes_file = os.path.join(dist_dir, "_release_notes.md")
    with open(notes_file, "w", encoding="utf-8") as f:
        f.write(notes)

    cmd = [
        "gh", "release", "create", tag,
    ] + jar_paths + [
        "--title", tag,
        "--notes-file", notes_file,
    ]

    if dry_run:
        print("Dry run — would run:")
        print("  " + " ".join(cmd))
        print(f"\nRelease notes:\n{notes}")
        return

    print(f"\n=== Creating GitHub Release {tag} with {len(jars)} JARs ===")
    r = subprocess.run(cmd, cwd=BASE)
    if r.returncode != 0:
        print("ERROR: gh release create failed")
        sys.exit(1)
    print(f"Release {tag} published!")


def main():
    parser = argparse.ArgumentParser(description="Build mods and publish GitHub Release")
    parser.add_argument("tag", help="Release tag (e.g. v0.1.1)")
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
    print(f"Building {total} mod projects...\n")

    serial_keys = set()
    for mc, info in ALL_VERSIONS.items():
        fg_era = info.get("fg_era", "")
        if fg_era in ("fg21", "fg22", "fg23", "fg3", "fg41"):
            for loader in get_loaders(mc):
                if loader == "forge":
                    serial_keys.add(f"{mc}/{loader}")

    serial_tasks = [t for t in tasks if f"{t[0]}/{t[1]}" in serial_keys]
    parallel_tasks = [t for t in tasks if f"{t[0]}/{t[1]}" not in serial_keys]

    done = 0
    start_all = time.time()
    results = {"success": [], "fail": [], "skip": []}

    if serial_tasks:
        print(f"--- Serial builds ({len(serial_tasks)} old Forge) ---")
        for t in serial_tasks:
            done += 1
            key, status, entry = _build_one(t)
            elapsed = entry.get("time", 0)
            lbl = {"success": "OK", "fail": "FAIL", "skip": "SKIP"}[status]
            print(f"  [{done}/{total}] {lbl} {key} ({elapsed:.1f}s)")
            results[status].append(entry)

    if parallel_tasks:
        print(f"\n--- Parallel builds ({len(parallel_tasks)} mods, {args.jobs} workers) ---")
        with ThreadPoolExecutor(max_workers=args.jobs) as pool:
            future_map = {pool.submit(_build_one, t): t for t in parallel_tasks}
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

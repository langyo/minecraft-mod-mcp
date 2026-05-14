"""Batch build ALL mod projects and report results.

Sets correct JAVA_HOME per FG era and loader.
Legacy builds (FG 1.2–4.1) get proxy settings for Cloudflare bypass.

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

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from version_config import (
    ALL_VERSIONS, FG_ERAS, MODS_DIR,
    get_loaders, get_fg_era, get_jdk_home, find_jdk17, is_legacy, JDK_PATHS,
)

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODS_DIR_ACTUAL = MODS_DIR

PROXY_HOST = "127.0.0.1"
PROXY_PORT = 7890
BUILD_TIMEOUT = 1200


def resolve_java_home(mc, info, loader="forge"):
    fg_era = info.get("fg_era", "")
    if loader == "forge" and fg_era in ("fg12", "fg21", "fg22", "fg23", "fg3", "fg41"):
        return get_jdk_home(8) or "C:\\Users\\langy\\.jdks\\jdk8"
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


def main():
    parser = argparse.ArgumentParser(description="Batch build all mod projects")
    parser.add_argument("--era", help="Only build specific FG era (e.g. fg51, fg6)")
    parser.add_argument("--loader", help="Only build specific loader (forge, neoforge, fabric)")
    parser.add_argument("--mc", help="Only build specific MC version")
    parser.add_argument("--no-cache", action="store_true", help="Skip prepare_cache step")
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
    done = 0

    print(f"Building {total} projects...")
    if args.era:
        print(f"  Filter era: {args.era}")
    if args.loader:
        print(f"  Filter loader: {args.loader}")
    if args.mc:
        print(f"  Filter mc: {args.mc}")
    print()

    for mc, loader, info in tasks:
        done += 1
        path = os.path.join(MODS_DIR_ACTUAL, mc, loader)
        key = f"{mc}/{loader}"

        if not os.path.isdir(path):
            results["skip"].append({"key": key, "reason": "no project dir"})
            print(f"[{done}/{total}] SKIP {key}: no project dir")
            continue

        gradlew = os.path.join(path, "gradlew.bat")
        if not os.path.isfile(gradlew):
            results["skip"].append({"key": key, "reason": "no gradlew"})
            print(f"[{done}/{total}] SKIP {key}: no gradlew")
            continue

        print(f"[{done}/{total}] BUILD {key}...", end="", flush=True)

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
        cmd = ["cmd", "/c", "gradlew.bat", "build", "--no-daemon"]
        fg_era = info.get("fg_era", "")
        if fg_era == "fg12":
            init_script = os.path.join(
                os.environ.get("TEMP", "C:\\Temp"),
                "fg12_init.gradle",
            )
            if not os.path.isfile(init_script):
                with open(init_script, "w") as f:
                    f.write(
                        "allprojects {\n"
                        "  tasks.whenTaskAdded { task ->\n"
                        "    if (task.name == 'downloadClient' || task.name == 'downloadServer') {\n"
                        "      task.enabled = false\n"
                        "    }\n"
                        "  }\n"
                        "}\n"
                    )
            cmd += ["-I", init_script]
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
                results["success"].append({"key": key, "time": round(elapsed, 1)})
                print(f" OK ({elapsed:.1f}s)")
            else:
                err_msg = out[-2000:] if len(out) > 2000 else out
                results["fail"].append({"key": key, "time": round(elapsed, 1), "error": err_msg})
                log_path = os.path.join(path, "build-error.log")
                with open(log_path, "w", encoding="utf-8") as lf:
                    lf.write(out)
                print(f" FAIL ({elapsed:.1f}s)")
        except subprocess.TimeoutExpired:
            elapsed = time.time() - start
            results["fail"].append({"key": key, "time": round(elapsed, 1), "error": "TIMEOUT"})
            print(f" TIMEOUT ({elapsed:.1f}s)")
        except Exception as e:
            results["fail"].append({"key": key, "error": str(e)})
            print(f" ERROR: {e}")

    print(f"\n{'=' * 60}")
    s, f, sk = len(results["success"]), len(results["fail"]), len(results["skip"])
    print(f"Results: {s} OK, {f} FAIL, {sk} SKIP / {total} total")
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

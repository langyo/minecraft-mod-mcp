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
BUILD_TIMEOUT = 600


def resolve_java_home(mc, info):
    java_ver = info.get("java", 8)
    jdk = get_jdk_home(java_ver)
    if jdk:
        return jdk
    if java_ver == 17:
        return find_jdk17()
    return None


def main():
    parser = argparse.ArgumentParser(description="Batch build all mod projects")
    parser.add_argument("--era", help="Only build specific FG era (e.g. fg51, fg6)")
    parser.add_argument("--loader", help="Only build specific loader (forge, neoforge, fabric)")
    parser.add_argument("--mc", help="Only build specific MC version")
    args = parser.parse_args()

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

        jdk = resolve_java_home(mc, info)
        if jdk:
            env["JAVA_HOME"] = jdk

        if is_legacy(mc):
            proxy_args = (
                f"-Dhttps.proxyHost={PROXY_HOST}"
                f" -Dhttps.proxyPort={PROXY_PORT}"
                f" -Dhttp.proxyHost={PROXY_HOST}"
                f" -Dhttp.proxyPort={PROXY_PORT}"
            )
            env["JAVA_TOOL_OPTIONS"] = proxy_args
            env["GRADLE_OPTS"] = "-Dorg.gradle.jvmargs=-Xmx3G"
        else:
            env.pop("JAVA_TOOL_OPTIONS", None)
            env["GRADLE_OPTS"] = "-Dorg.gradle.jvmargs=-Xmx3G"

        start = time.time()
        try:
            proc = subprocess.run(
                ["cmd", "/c", "gradlew.bat", "build", "--no-daemon"],
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
                errs = [
                    l for l in out.split("\n")
                    if "Caused by" in l or ("> " in l and "at " not in l)
                ]
                err_msg = errs[0].strip()[:200] if errs else out[-300:]
                results["fail"].append({"key": key, "time": round(elapsed, 1), "error": err_msg})
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
            print(f"  {entry['key']}: {entry.get('error', '')[:200]}")

    report_path = os.path.join(BASE, "build-report.json")
    with open(report_path, "w") as fp:
        json.dump(results, fp, indent=2)
    print(f"\nReport saved to {report_path}")


if __name__ == "__main__":
    main()

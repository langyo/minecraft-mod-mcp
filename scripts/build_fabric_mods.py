#!/usr/bin/env python3
"""Batch-build all Fabric mod JARs using the local Gradle + proxy.

Builds every fabric mod project under packages/mods/<ver>/fabric/ that doesn't
already have a built JAR. Runs N builds in parallel to exploit multi-core.
Requires http_proxy / https_proxy env vars and JAVA_HOME pointing to a JDK.
"""
from __future__ import annotations
import os, sys, subprocess, time, glob
from concurrent.futures import ThreadPoolExecutor, as_completed

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODS = os.path.join(ROOT, "packages", "mods")
JAVA_HOME = os.environ.get("JAVA_HOME", "/home/lab/.gradle/jdks/eclipse_adoptium-21")
PARALLEL = int(os.environ.get("BUILD_PARALLEL", "4"))


def fabric_versions() -> list[str]:
    out = []
    for entry in sorted(os.listdir(MODS)):
        fab = os.path.join(MODS, entry, "fabric")
        if os.path.isdir(fab) and os.path.isfile(os.path.join(fab, "build.gradle")):
            out.append(entry)
    return out


def has_jar(ver: str) -> bool:
    libs = os.path.join(MODS, ver, "fabric", "build", "libs")
    return bool(glob.glob(os.path.join(libs, "*.jar")) and
                not all(s.endswith(("-sources.jar", "-javadoc.jar"))
                        for s in glob.glob(os.path.join(libs, "*.jar"))))


def build_one(ver: str) -> tuple[str, bool, str]:
    proj = os.path.join(MODS, ver, "fabric")
    gradlew = os.path.join(proj, "gradlew")
    if not os.path.isfile(gradlew):
        return ver, False, "no gradlew"
    os.chmod(gradlew, 0o755)
    with open(os.path.join(proj, "gradle.properties"), "w") as f:
        f.write("org.gradle.jvmargs=-Xmx3g\norg.gradle.daemon=false\n")
    env = dict(os.environ)
    env["JAVA_HOME"] = JAVA_HOME
    env["PATH"] = f"{JAVA_HOME}/bin:{env.get('PATH','')}"
    try:
        r = subprocess.run(
            [gradlew, "build", "--no-daemon", "--console=plain"],
            cwd=proj, env=env, capture_output=True, text=True, timeout=600,
        )
        if r.returncode == 0 and has_jar(ver):
            return ver, True, f"{r.stdout.strip()[-80:]}"
        tail = (r.stderr or r.stdout or "").strip()[-120:]
        return ver, False, tail
    except subprocess.TimeoutExpired:
        return ver, False, "timeout 600s"
    except Exception as e:
        return ver, False, str(e)


def main():
    versions = fabric_versions()
    todo = [v for v in versions if not has_jar(v)]
    skip = [v for v in versions if has_jar(v)]
    print(f"Fabric mod projects: {len(versions)} total, {len(skip)} already built, {len(todo)} to build")
    print(f"Already built: {', '.join(skip[:10])}{'...' if len(skip)>10 else ''}")
    print(f"Building {len(todo)} with {PARALLEL}-way parallelism...\n")

    ok, fail = [], []
    t0 = time.time()
    with ThreadPoolExecutor(max_workers=PARALLEL) as pool:
        futures = {pool.submit(build_one, v): v for v in todo}
        for fut in as_completed(futures):
            ver, success, msg = fut.result()
            status = "OK  " if success else "FAIL"
            print(f"  [{status}] {ver:<10} ({msg[:60]})", flush=True)
            (ok if success else fail).append(ver)

    print(f"\nDone in {time.time()-t0:.0f}s: {len(ok)} built, {len(fail)} failed.")
    if fail:
        print(f"Failed: {', '.join(sorted(fail))}")
    return 0 if not fail else 1


if __name__ == "__main__":
    sys.exit(main())

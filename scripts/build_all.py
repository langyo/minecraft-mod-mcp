"""Batch build all mod projects (FG 5.1+ only) and report results.

Skips FG 1.2-4 (1.7-1.15.2) legacy versions due to Cloudflare/TLS issues.
Sets correct JAVA_HOME per FG era:
  FG 5.1 (1.16-1.19.2): JDK 17
  FG 6/7, NeoForge, Fabric: system default (JDK 21+)
"""
import subprocess
import sys
import os
import time
import json
import glob

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODS_DIR = os.path.join(BASE, "mods")

SKIP_VERSIONS = {
    "1.7.2", "1.7.10",
    "1.8", "1.8.9", "1.9", "1.9.4", "1.10", "1.10.2",
    "1.11", "1.11.2", "1.12", "1.12.2",
    "1.13.2", "1.14.4", "1.15", "1.15.2",
}

FG5_VERSIONS = {
    "1.16.1", "1.16.3", "1.16.4", "1.16.5",
    "1.17.1", "1.18", "1.18.2", "1.19", "1.19.2",
}

ALL_VERSIONS = {
    "1.16.1": ["forge","fabric"], "1.16.3": ["forge","fabric"],
    "1.16.4": ["forge","fabric"], "1.16.5": ["forge","fabric"],
    "1.17.1": ["forge","fabric"],
    "1.18": ["forge","fabric"], "1.18.2": ["forge","fabric"],
    "1.19": ["forge","fabric"], "1.19.2": ["forge","fabric"],
    "1.19.3": ["forge","fabric"], "1.19.4": ["forge","fabric"],
    "1.20": ["forge","fabric"],
    "1.20.1": ["forge","neoforge","fabric"],
    "1.20.2": ["forge","neoforge","fabric"],
    "1.20.3": ["forge","neoforge","fabric"],
    "1.20.4": ["forge","neoforge","fabric"],
    "1.20.5": ["neoforge","fabric"],
    "1.20.6": ["forge","neoforge","fabric"],
    "1.21": ["forge","fabric"],
    "1.21.1": ["forge","neoforge","fabric"],
    "1.21.2": ["neoforge","fabric"],
    "1.21.3": ["forge","neoforge","fabric"],
    "1.21.4": ["forge","neoforge","fabric"],
    "1.21.5": ["forge","neoforge","fabric"],
    "26.1": ["forge"],
    "26.1.1": ["forge","neoforge"],
    "26.1.2": ["forge","neoforge"],
}

def find_jdk17():
    home = os.path.expanduser("~")
    patterns = [
        os.path.join(home, ".gradle", "jdks", "eclipse_adoptium-17*"),
        os.path.join(home, ".jdks", "jdk-17*"),
    ]
    for p in patterns:
        matches = [m for m in glob.glob(p) if os.path.isdir(m) and "lock" not in m]
        if matches:
            return sorted(matches)[-1]
    return None

JDK17 = find_jdk17()

results = {"success": [], "fail": [], "skip": []}
total = sum(len(v) for v in ALL_VERSIONS.values())
done = 0

for mc, loaders in ALL_VERSIONS.items():
    for loader in loaders:
        done += 1
        path = os.path.join(MODS_DIR, mc, loader)
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

        proxy = "-Dorg.gradle.jvmargs=-Xmx2G -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890"
        print(f"[{done}/{total}] BUILD {key}...", end="", flush=True)

        start = time.time()
        try:
            env = os.environ.copy()
            env["JAVA_TOOL_OPTIONS"] = proxy
            if mc in FG5_VERSIONS and JDK17:
                env["JAVA_HOME"] = JDK17
            proc = subprocess.run(
                ["cmd", "/c", "gradlew.bat", "build", "--no-daemon"],
                cwd=path,
                capture_output=True,
                text=True,
                timeout=600,
                env=env,
            )
            elapsed = time.time() - start
            out = (proc.stdout or "") + (proc.stderr or "")
            if proc.returncode == 0 and "BUILD SUCCESSFUL" in out:
                results["success"].append({"key": key, "time": round(elapsed, 1)})
                print(f" OK ({elapsed:.1f}s)")
            else:
                errs = [l for l in out.split("\n") if "Caused by" in l or ("> " in l and "at " not in l)]
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

print(f"\n{'='*60}")
print(f"Results: {len(results['success'])} OK, {len(results['fail'])} FAIL, {len(results['skip'])} SKIP / {total} total")
print(f"{'='*60}")

if results["fail"]:
    print("\nFAILURES:")
    for f in results["fail"]:
        print(f"  {f['key']}: {f.get('error', '')[:200]}")

report_path = os.path.join(BASE, "build-report.json")
with open(report_path, "w") as f:
    json.dump(results, f, indent=2)
print(f"\nReport saved to {report_path}")

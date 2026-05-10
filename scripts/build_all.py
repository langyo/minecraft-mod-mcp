"""Batch build all 81 mod projects and report results."""
import subprocess
import sys
import os
import time
import json

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODS_DIR = os.path.join(BASE, "mods")

ALL_VERSIONS = {
    "1.7.2": ["forge"], "1.7.10": ["forge"],
    "1.8": ["forge"], "1.8.9": ["forge"],
    "1.9": ["forge"], "1.9.4": ["forge"],
    "1.10": ["forge"], "1.10.2": ["forge"],
    "1.11": ["forge"], "1.11.2": ["forge"],
    "1.12": ["forge"], "1.12.2": ["forge"],
    "1.13.2": ["forge"],
    "1.14.4": ["forge","fabric"], "1.15": ["forge","fabric"], "1.15.2": ["forge","fabric"],
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

        proxy = "-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890"
        print(f"[{done}/{total}] BUILD {key}...", end="", flush=True)
        
        start = time.time()
        try:
            env = os.environ.copy()
            env["JAVA_TOOL_OPTIONS"] = proxy
            proc = subprocess.run(
                ["cmd", "/c", "gradlew.bat", "build", "--no-daemon"],
                cwd=path,
                capture_output=True,
                text=True,
                timeout=600,
                env=env,
            )
            elapsed = time.time() - start
            if proc.returncode == 0:
                results["success"].append({"key": key, "time": round(elapsed, 1)})
                print(f" OK ({elapsed:.1f}s)")
            else:
                stderr = proc.stderr[-300:] if proc.stderr else ""
                results["fail"].append({"key": key, "time": round(elapsed, 1), "error": stderr})
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

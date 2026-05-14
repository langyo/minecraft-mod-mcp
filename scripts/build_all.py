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


def start_fg12_http_server():
    global _http_server_proc
    if _http_server_proc is not None:
        try:
            _http_server_proc.wait(timeout=0)
        except Exception:
            return
    port = 58080
    serve_dir = os.path.join(TEMP_DIR, "fg_http")
    os.makedirs(serve_dir, exist_ok=True)
    versions_json_path = os.path.join(serve_dir, "versions.json")
    if not os.path.isfile(versions_json_path):
        import urllib.request
        try:
            resp = urllib.request.urlopen(
                "https://maven.minecraftforge.net/de/oceanlabs/mcp/versions.json", timeout=30
            )
            data = json.loads(resp.read())
        except Exception:
            data = {}
        if "1.7.2" not in data:
            if "1.7.10" in data:
                data["1.7.2"] = data["1.7.10"]
        with open(versions_json_path, "w", encoding="utf-8") as f:
            json.dump(data, f)
    padded_name = "a" * 32 + "v.json"
    dst = os.path.join(serve_dir, padded_name)
    if not os.path.isfile(dst):
        shutil.copy2(versions_json_path, dst)
    server_py = os.path.join(TEMP_DIR, "fg12_http_server.py")
    if not os.path.isfile(server_py):
        with open(server_py, "w", encoding="utf-8") as f:
            f.write(
                "import http.server, socketserver, os\n"
                f"os.chdir(r'{serve_dir}')\n"
                f"with socketserver.TCPServer(('', {port}), http.server.SimpleHTTPRequestHandler) as httpd:\n"
                "    httpd.serve_forever()\n"
            )
    _http_server_proc = subprocess.Popen(
        [sys.executable, server_py],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    time.sleep(1)
    for _ in range(10):
        try:
            import urllib.request
            urllib.request.urlopen(f"http://localhost:{port}/{padded_name}", timeout=2)
            print(f"  FG 1.2 HTTP server started on port {port}")
            return True
        except Exception:
            time.sleep(0.5)
    print("  WARNING: FG 1.2 HTTP server failed to start")
    return False


def stop_fg12_http_server():
    global _http_server_proc
    if _http_server_proc:
        _http_server_proc.terminate()
        _http_server_proc = None


def patch_fg12_jar():
    fg12_dirs = os.path.join(MODULES_CACHE, "net.minecraftforge.gradle", "ForgeGradle", "1.2-SNAPSHOT")
    if not os.path.isdir(fg12_dirs):
        return None
    jar_path = None
    for d in os.listdir(fg12_dirs):
        candidate = os.path.join(fg12_dirs, d, "ForgeGradle-1.2-SNAPSHOT.jar")
        if os.path.isfile(candidate):
            jar_path = candidate
            break
    if not jar_path:
        return None
    bak_path = jar_path + ".bak"
    if os.path.isfile(bak_path):
        pass
    else:
        shutil.copy2(jar_path, bak_path)
    import zipfile
    old_url = b"https://maven.minecraftforge.net/de/oceanlabs/mcp/versions.json"
    new_url = b"http://localhost:58080/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaav.json"
    if len(new_url) != len(old_url):
        return None
    patched = False
    tmp_dir = os.path.join(TEMP_DIR, "fg_repack")
    if os.path.isdir(tmp_dir):
        shutil.rmtree(tmp_dir, ignore_errors=True)
    os.makedirs(tmp_dir, exist_ok=True)
    with zipfile.ZipFile(bak_path, "r") as z:
        z.extractall(tmp_dir)
    targets = [
        os.path.join(tmp_dir, "net", "minecraftforge", "gradle", "common", "BasePlugin.class"),
        os.path.join(tmp_dir, "net", "minecraftforge", "gradle", "common", "Constants.class"),
    ]
    for cls_file in targets:
        if not os.path.isfile(cls_file):
            continue
        data = bytearray(open(cls_file, "rb").read())
        idx = data.find(old_url)
        if idx >= 0:
            data[idx:idx + len(new_url)] = new_url
            with open(cls_file, "wb") as f:
                f.write(data)
            patched = True
    if patched:
        if os.path.isfile(jar_path):
            os.remove(jar_path)
        with zipfile.ZipFile(jar_path, "w", zipfile.ZIP_DEFLATED) as z:
            for root, dirs, files in os.walk(tmp_dir):
                for fn in files:
                    fp = os.path.join(root, fn)
                    arcname = os.path.relpath(fp, tmp_dir).replace(os.sep, "/")
                    z.write(fp, arcname)
        gradle_cache_214 = os.path.join(GRADLE_USER_HOME, "caches", "2.14")
        if os.path.isdir(gradle_cache_214):
            shutil.rmtree(gradle_cache_214, ignore_errors=True)
        return jar_path
    return None


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
        src_dir_710 = os.path.join(MODULES_CACHE, "de.oceanlabs.mcp", f"mcp_{channel}", f"{ver}-1.7.10")
        if os.path.isdir(src_dir_710):
            for d in os.listdir(src_dir_710):
                candidate = os.path.join(src_dir_710, d, f"mcp_{channel}-{ver}-1.7.10.zip")
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

    has_fg12 = any(info.get("fg_era") == "fg12" for _, _, info in tasks)
    if has_fg12:
        print("Setting up FG 1.2 environment...")
        start_fg12_http_server()
        patched = patch_fg12_jar()
        if patched:
            print(f"  Patched FG 1.2 jar: {patched}")
        for mc, loader, info in tasks:
            if info.get("fg_era") == "fg12":
                ensure_mcp_stable_in_fg_cache(mc, info)
        print()

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

    stop_fg12_http_server()
    fg12_jar = os.path.join(
        MODULES_CACHE, "net.minecraftforge.gradle", "ForgeGradle", "1.2-SNAPSHOT",
    )
    if os.path.isdir(fg12_jar):
        for d in os.listdir(fg12_jar):
            bak = os.path.join(fg12_jar, d, "ForgeGradle-1.2-SNAPSHOT.jar.bak")
            jar = os.path.join(fg12_jar, d, "ForgeGradle-1.2-SNAPSHOT.jar")
            if os.path.isfile(bak) and os.path.isfile(jar):
                shutil.copy2(bak, jar)
                print(f"Restored FG 1.2 jar from backup")


if __name__ == "__main__":
    main()

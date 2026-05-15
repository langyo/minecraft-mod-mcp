"""Launch Minecraft directly from version JSON files.
Handles classpath construction, native extraction, and proper argument building.

Usage:
  python scripts/launch_mc.py 1.21.7-forge-57.0.2
  python scripts/launch_mc.py 1.21.7-forge-57.0.2 --jvm-args "-Xmx4G -Xms2G"
"""

import argparse
import json
import os
import platform
import shutil
import subprocess
import sys
import tempfile
import zipfile

MC_DIR = os.path.join(
    os.environ.get("APPDATA", os.path.expanduser("~")),
    ".minecraft" if platform.system() == "Windows" else ".minecraft",
)


def merge_version_json(version_name, mc_dir=None):
    mc_dir = mc_dir or MC_DIR
    version_dir = os.path.join(mc_dir, "versions", version_name)
    json_path = os.path.join(version_dir, f"{version_name}.json")
    with open(json_path, "r", encoding="utf-8") as f:
        vj = json.load(f)
    parent = vj.get("inheritsFrom")
    if parent:
        parent_json_path = os.path.join(mc_dir, "versions", parent, f"{parent}.json")
        if os.path.isfile(parent_json_path):
            with open(parent_json_path, "r", encoding="utf-8") as f:
                pj = json.load(f)
            merged = merge_dicts(pj, vj)
            merged["_parent"] = parent
            return merged
    return vj


def merge_dicts(base, override):
    result = dict(base)
    for key, val in override.items():
        if key == "libraries" and key in result:
            seen = set()
            merged = []
            for lib in result[key]:
                name = lib.get("name", "")
                if name not in seen:
                    seen.add(name)
                    merged.append(lib)
            for lib in val:
                name = lib.get("name", "")
                if name not in seen:
                    seen.add(name)
                    merged.append(lib)
            result[key] = merged
        elif key == "arguments" and key in result:
            ba = result.get("arguments", {})
            merged_args = dict(ba)
            for ak, av in val.items():
                if ak in merged_args:
                    merged_args[ak] = list(merged_args[ak]) + list(av)
                else:
                    merged_args[ak] = list(av)
            result[key] = merged_args
        else:
            result[key] = val
    return result


def build_classpath(vj, mc_dir=None):
    mc_dir = mc_dir or MC_DIR
    cp = []
    seen_keys = set()
    for lib in vj.get("libraries", []):
        if not should_include_lib(lib):
            continue
        name = lib.get("name", "")
        if ":natives-" in name:
            continue
        parts = name.split(":")
        if len(parts) >= 3:
            base = parts[0] + ":" + parts[1]
            classifier = parts[3] if len(parts) > 3 else ""
            if classifier:
                key = base + ":" + classifier
            else:
                key = base
        else:
            key = name
            base = name
        if key in seen_keys:
            continue
        seen_keys.add(key)
        dl = lib.get("downloads", {})
        artifact = dl.get("artifact", {})
        path = artifact.get("path")
        if path:
            full = os.path.join(mc_dir, "libraries", path)
            if os.path.isfile(full):
                cp.append(full)
    parent = vj.get("_parent")
    if parent:
        jar = os.path.join(mc_dir, "versions", parent, f"{parent}.jar")
        if os.path.isfile(jar):
            cp.append(jar)
    version_name = vj.get("id", vj.get("inheritsFrom", ""))
    jar2 = os.path.join(mc_dir, "versions", version_name, f"{version_name}.jar")
    if os.path.isfile(jar2) and jar2 not in cp:
        cp.append(jar2)
    return cp


def should_include_lib(lib):
    rules = lib.get("rules", [])
    if not rules:
        return True
    current_os = get_os_name()
    current_arch = get_arch()
    allowed = False
    disallowed = False
    for rule in rules:
        action = rule.get("action", "allow")
        os_rule = rule.get("os", {})
        if os_rule:
            os_name = os_rule.get("name", "")
            os_arch = os_rule.get("arch", "")
            match = True
            if os_name and os_name != current_os:
                match = False
            if os_arch and os_arch not in current_arch:
                match = False
            if match:
                if action == "allow":
                    allowed = True
                elif action == "disallow":
                    disallowed = True
        else:
            if action == "allow":
                allowed = True
            elif action == "disallow":
                disallowed = True
    if disallowed:
        return False
    return allowed or not rules


def get_os_name():
    s = platform.system().lower()
    if s == "darwin":
        return "osx"
    if s == "linux":
        return "linux"
    return "windows"


def get_arch():
    m = platform.machine().lower()
    bits = platform.architecture()[0]
    if m in ("amd64", "x86_64", "x64") or "64" in bits:
        return "x86_64"
    if m in ("aarch64", "arm64"):
        return "aarch64"
    return "x86"


def extract_natives(vj, mc_dir=None):
    mc_dir = mc_dir or MC_DIR
    version_id = vj.get("id") or vj.get("inheritsFrom", "unknown")
    natives_dir = os.path.join(mc_dir, "versions-natives", version_id)
    if os.path.isdir(natives_dir) and os.listdir(natives_dir):
        return natives_dir
    os.makedirs(natives_dir, exist_ok=True)
    current_os = get_os_name()
    for lib in vj.get("libraries", []):
        name = lib.get("name", "")
        dl = lib.get("downloads", {})
        classifiers = dl.get("classifiers", {})
        natives_map = lib.get("natives", {})
        native_jar = None
        if natives_map:
            key = natives_map.get(current_os, "")
            if "${arch}" in key:
                key = key.replace("${arch}", "64")
            art = classifiers.get(key, {})
            path = art.get("path", "")
            if path:
                native_jar = os.path.join(mc_dir, "libraries", path)
        elif "natives-windows" in name and "natives-windows-arm64" not in name:
            art = dl.get("artifact", {})
            path = art.get("path", "")
            if path:
                native_jar = os.path.join(mc_dir, "libraries", path)
        if native_jar and os.path.isfile(native_jar):
            try:
                with zipfile.ZipFile(native_jar, "r") as zf:
                    for info in zf.infolist():
                        fn = info.filename
                        if fn.endswith((".dll", ".so", ".dylib", ".jnilib")):
                            if "META-INF" in fn:
                                continue
                            zf.extract(info, natives_dir)
            except Exception:
                pass
    return natives_dir


def build_jvm_args(vj, natives_dir, mc_dir=None):
    mc_dir = mc_dir or MC_DIR
    args = []
    jvm_raw = vj.get("arguments", {}).get("jvm", [])
    skip_next = False
    for i, arg in enumerate(jvm_raw):
        if skip_next:
            skip_next = False
            continue
        if isinstance(arg, str):
            if arg in ("-cp", "-classpath"):
                if i + 1 < len(jvm_raw) and isinstance(jvm_raw[i + 1], str) and "${classpath}" in jvm_raw[i + 1]:
                    skip_next = True
                continue
            if "${classpath}" in arg:
                continue
            args.append(replace_vars(arg, natives_dir, mc_dir))
        elif isinstance(arg, dict):
            if should_include_arg(arg):
                val = arg.get("value")
                if isinstance(val, list):
                    for v in val:
                        args.append(replace_vars(v, natives_dir, mc_dir))
                elif isinstance(val, str):
                    args.append(replace_vars(val, natives_dir, mc_dir))
    return args


def should_include_arg(arg_obj):
    rules = arg_obj.get("rules", [])
    if not rules:
        return True
    current_os = get_os_name()
    current_arch = get_arch()
    matched_allow = False
    for rule in rules:
        action = rule.get("action", "allow")
        os_rule = rule.get("os", {})
        if os_rule:
            os_name = os_rule.get("name", "")
            os_arch = os_rule.get("arch", "")
            match = True
            if os_name and os_name != current_os:
                match = False
            if os_arch and os_arch not in current_arch:
                match = False
            if match:
                if action == "disallow":
                    return False
                matched_allow = True
        else:
            if action == "allow":
                matched_allow = True
            elif action == "disallow":
                return False
    return matched_allow


def build_game_args(vj, version_name, mc_dir=None, username="Player", uuid="00000000-0000-0000-0000-000000000000", access_token="0"):
    mc_dir = mc_dir or MC_DIR
    args = []
    game_raw = vj.get("arguments", {}).get("game", [])
    replacements = {
        "${auth_player_name}": username,
        "${version_name}": version_name,
        "${game_directory}": mc_dir,
        "${assets_root}": os.path.join(mc_dir, "assets"),
        "${assets_index_name}": vj.get("assetIndex", {}).get("id", ""),
        "${auth_uuid}": uuid,
        "${auth_access_token}": access_token,
        "${clientid}": "00000000-0000-0000-0000-000000000000",
        "${auth_xuid}": "",
        "${user_type}": "msa",
        "${version_type}": "release",
    }
    for arg in game_raw:
        if isinstance(arg, str):
            a = arg
            for k, v in replacements.items():
                a = a.replace(k, v)
            if a.startswith("${") and a.endswith("}"):
                continue
            args.append(a)
        elif isinstance(arg, dict):
            if should_include_arg(arg):
                val = arg.get("value")
                if isinstance(val, list):
                    for v in val:
                        a = v
                        for k2, r in replacements.items():
                            a = a.replace(k2, r)
                        if not (a.startswith("${") and a.endswith("}")):
                            args.append(a)
                elif isinstance(val, str):
                    a = val
                    for k2, r in replacements.items():
                        a = a.replace(k2, r)
                    if not (a.startswith("${") and a.endswith("}")):
                        args.append(a)
    args = [a for a in args if a]
    clean = []
    skip_next = False
    for i, a in enumerate(args):
        if skip_next:
            skip_next = False
            continue
        if a == "--demo":
            continue
        if a in ("--width", "--height"):
            skip_next = True
            continue
        if a.startswith("--quickPlay"):
            skip_next = True
            continue
        if a.startswith("${") and a.endswith("}"):
            continue
        clean.append(a)
    return clean


def replace_vars(s, natives_dir, mc_dir):
    return (
        s.replace("${natives_directory}", natives_dir)
        .replace("${launcher_name}", "minecraft-mcp")
        .replace("${launcher_version}", "1.0")
        .replace("${game_directory}", mc_dir)
    )


def find_java(version_java=None):
    if version_java and "21" in str(version_java):
        jdk21 = r"C:\Program Files\Amazon Corretto\jdk21.0.8_9"
        if os.path.isfile(os.path.join(jdk21, "bin", "java.exe")):
            return os.path.join(jdk21, "bin", "java.exe")
    for env_key in ("JAVA_HOME", "JDK_21"):
        home = os.environ.get(env_key)
        if home:
            exe = os.path.join(home, "bin", "java.exe" if platform.system() == "Windows" else "java")
            if os.path.isfile(exe):
                return exe
    return "java"


def main():
    parser = argparse.ArgumentParser(description="Launch Minecraft directly")
    parser.add_argument("version", help="Version name (e.g. 1.21.7-forge-57.0.2)")
    parser.add_argument("--mc-dir", default=None, help=".minecraft directory")
    parser.add_argument("--java", default=None, help="Java executable path")
    parser.add_argument("--jvm-args", default="-Xmx4G -Xms1G", help="Extra JVM args")
    parser.add_argument("--username", default="Player", help="Username")
    parser.add_argument("--dry-run", action="store_true", help="Print command but don't launch")
    parser.add_argument("--extra-jvm", default="", help="Extra JVM system properties")
    args = parser.parse_args()

    mc_dir = args.mc_dir or MC_DIR
    version_name = args.version

    print(f"[LAUNCH] MC version: {version_name}")
    print(f"[LAUNCH] MC dir: {mc_dir}")

    vj = merge_version_json(version_name, mc_dir)
    main_class = vj.get("mainClass")
    if not main_class:
        print("[ERROR] No mainClass found in version JSON")
        sys.exit(1)
    print(f"[LAUNCH] mainClass: {main_class}")

    cp = build_classpath(vj, mc_dir)
    print(f"[LAUNCH] Classpath: {len(cp)} entries")

    natives_dir = extract_natives(vj, mc_dir)
    native_files = []
    for root, dirs, files in os.walk(natives_dir):
        for f in files:
            if f.endswith((".dll", ".so", ".dylib", ".jnilib")):
                native_files.append(f)
    print(f"[LAUNCH] Natives: {len(native_files)} files in {natives_dir}")

    jvm_args = build_jvm_args(vj, natives_dir, mc_dir)
    game_args = build_game_args(vj, version_name, mc_dir, args.username)

    java_ver = vj.get("javaVersion", {}).get("majorVersion", 21)
    java_exe = args.java or find_java(java_ver)
    print(f"[LAUNCH] Java: {java_exe} (MC wants {java_ver})")

    ws_jar = os.path.join(mc_dir, "libraries", "org", "java-websocket",
                           "Java-WebSocket", "1.5.4", "Java-WebSocket-1.5.4.jar")
    if not os.path.isfile(ws_jar):
        gradle_cache = os.path.join(os.path.expanduser("~"), ".gradle", "caches",
                                     "modules-2", "files-2.1", "org.java-websocket",
                                     "Java-WebSocket", "1.5.4")
        if os.path.isdir(gradle_cache):
            for root, dirs, files in os.walk(gradle_cache):
                for f in files:
                    if f.endswith(".jar"):
                        src = os.path.join(root, f)
                        os.makedirs(os.path.dirname(ws_jar), exist_ok=True)
                        shutil.copy2(src, ws_jar)
                        print(f"[LAUNCH] Copied Java-WebSocket to {ws_jar}")
                        break
    if os.path.isfile(ws_jar):
        cp.append(ws_jar)
        print(f"[LAUNCH] Added Java-WebSocket to classpath")

    sep = ";" if platform.system() == "Windows" else ":"
    cp_str = sep.join(cp)

    cmd = [java_exe]
    cmd.extend(args.jvm_args.split())
    cmd.extend(["-Dmcp.server=ws://127.0.0.1:9876"])
    if args.extra_jvm:
        cmd.extend(args.extra_jvm.split())
    cmd.extend(jvm_args)
    cmd.extend(["-cp", cp_str])
    cmd.append(main_class)
    cmd.extend(game_args)

    if args.dry_run:
        print(f"\n[DRY-RUN] Command ({len(cmd)} args):")
        for i, a in enumerate(cmd):
            if len(a) > 120:
                print(f"  [{i}] {a[:80]}...({len(a)} chars)")
            else:
                print(f"  [{i}] {a}")
        return

    env = os.environ.copy()
    env["MC_MCP_SERVER"] = "ws://127.0.0.1:9876"

    print(f"\n[LAUNCH] Starting Minecraft...")
    print(f"  Version: {version_name}")
    print(f"  Main: {main_class}")
    print(f"  CP: {len(cp)} libs")
    print(f"  Natives: {natives_dir}")
    print(f"  Extra JVM: -Dmcp.server=ws://127.0.0.1:9876")

    log_out = os.path.join(mc_dir, "mcp-launch-stdout.log")
    log_err = os.path.join(mc_dir, "mcp-launch-stderr.log")
    fout = open(log_out, "w", encoding="utf-8", errors="replace")
    ferr = open(log_err, "w", encoding="utf-8", errors="replace")

    proc = subprocess.Popen(cmd, env=env, cwd=mc_dir, stdout=fout, stderr=ferr)
    print(f"[LAUNCH] Process started: pid={proc.pid}")
    print(f"[LAUNCH] stdout -> {log_out}")
    print(f"[LAUNCH] stderr -> {log_err}")
    print(f"[LAUNCH] Waiting for process to exit...")
    rc = proc.wait()
    fout.close()
    ferr.close()
    print(f"[LAUNCH] Process exited with code {rc}")

    for label, path in [("STDOUT", log_out), ("STDERR", log_err)]:
        if os.path.isfile(path) and os.path.getsize(path) > 0:
            print(f"\n=== {label} (last 80 lines) ===")
            with open(path, "r", encoding="utf-8", errors="replace") as f:
                lines = f.readlines()
            for line in lines[-80:]:
                print(line.rstrip())


if __name__ == "__main__":
    main()

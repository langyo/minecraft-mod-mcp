"""Launch Minecraft directly from version JSON files.
Handles classpath construction, native extraction, and proper argument building.

Usage:
  python scripts/launch_mc.py 1.21.7-forge-57.0.2
  python scripts/launch_mc.py 1.21.7-forge-57.0.2 --jvm-args "-Xmx4G -Xms2G"
"""

import argparse
import glob
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


def _lib_key(lib):
    name = lib.get("name", "")
    has_natives = "natives" in lib
    classifiers = list(lib.get("downloads", {}).get("classifiers", {}).keys())
    return (name, has_natives, tuple(sorted(classifiers)))


def _lib_base_key(lib):
    name = lib.get("name", "")
    parts = name.split(":")
    if len(parts) >= 2:
        base = parts[0] + ":" + parts[1]
        classifier = parts[3] if len(parts) > 3 else ""
        return base + (":" + classifier if classifier else "")
    return name


def merge_dicts(base, override):
    result = dict(base)
    for key, val in override.items():
        if key == "libraries" and key in result:
            seen = set()
            merged = []
            for lib in result[key]:
                k = _lib_key(lib)
                if k not in seen:
                    seen.add(k)
                    merged.append(lib)
            override_bases = {}
            for lib in val:
                bk = _lib_base_key(lib)
                override_bases[bk] = lib
            filtered = []
            for lib in merged:
                bk = _lib_base_key(lib)
                if bk in override_bases:
                    filtered.append(override_bases.pop(bk))
                else:
                    filtered.append(lib)
            for lib in override_bases.values():
                filtered.append(lib)
            result[key] = filtered
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


def download_libraries(vj, mc_dir=None):
    import urllib.request
    mc_dir = mc_dir or MC_DIR
    lib_dir = os.path.join(mc_dir, "libraries")
    missing = 0
    downloaded = 0
    for lib in vj.get("libraries", []):
        if not should_include_lib(lib):
            continue
        dl = lib.get("downloads", {})
        artifact = dl.get("artifact", {})
        path = artifact.get("path")
        url = artifact.get("url", "")
        if path and url:
            full = os.path.join(lib_dir, path)
            if not os.path.isfile(full):
                missing += 1
                try:
                    os.makedirs(os.path.dirname(full), exist_ok=True)
                    req = urllib.request.Request(url, headers={
                        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                    })
                    with urllib.request.urlopen(req, timeout=60) as resp:
                        data = resp.read()
                    with open(full, "wb") as f:
                        f.write(data)
                    downloaded += 1
                except Exception:
                    pass
        for classifier_key in ("natives-windows", "natives-windows-arm64"):
            native = dl.get("classifiers", {}).get(classifier_key, {})
            npath = native.get("path")
            nurl = native.get("url", "")
            if npath and nurl:
                nfull = os.path.join(lib_dir, npath)
                if not os.path.isfile(nfull):
                    try:
                        os.makedirs(os.path.dirname(nfull), exist_ok=True)
                        req = urllib.request.Request(nurl, headers={
                            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                        })
                        with urllib.request.urlopen(req, timeout=60) as resp:
                            data = resp.read()
                        with open(nfull, "wb") as f:
                            f.write(data)
                    except Exception:
                        pass
        name = lib.get("name", "")
        parts = name.split(":")
        if len(parts) >= 3 and not path:
            group, artifact_id, ver = parts[0], parts[1], parts[2]
            group_path = group.replace(".", "/")
            jar_name = f"{artifact_id}-{ver}.jar"
            jar_dir = os.path.join(lib_dir, group_path, artifact_id, ver)
            jar_path = os.path.join(jar_dir, jar_name)
            if os.path.isfile(jar_path):
                continue
            is_forge = "minecraftforge" in group or "net.minecraftforge" in group
            suffixes = [""]
            if is_forge:
                suffixes = ["", "-universal", "-client"]
            repo_url = lib.get("url", "")
            fallback_repos = []
            if repo_url:
                fallback_repos.append(repo_url)
            fallback_repos.append("https://libraries.minecraft.net/")
            fallback_repos.append("https://repo.maven.apache.org/maven2/")
            found = False
            for suffix in suffixes:
                if found:
                    break
                candidate_name = f"{artifact_id}-{ver}{suffix}.jar"
                candidate_path = os.path.join(jar_dir, candidate_name)
                if os.path.isfile(candidate_path):
                    found = True
                    break
                for base_url in fallback_repos:
                    jar_url = f"{base_url}{group_path}/{artifact_id}/{ver}/{candidate_name}"
                    try:
                        os.makedirs(jar_dir, exist_ok=True)
                        req = urllib.request.Request(jar_url, headers={
                            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                           "AppleWebKit/537.36",
                        })
                        with urllib.request.urlopen(req, timeout=60) as resp:
                            data = resp.read()
                        with open(candidate_path, "wb") as f:
                            f.write(data)
                        downloaded += 1
                        found = True
                        break
                    except Exception:
                        continue
    if missing or downloaded:
        print(f"  Libraries: {downloaded} downloaded, {missing - downloaded} failed")
    return downloaded


def ensure_version_jar(vj, mc_dir=None):
    import urllib.request
    mc_dir = mc_dir or MC_DIR
    parent = vj.get("_parent") or vj.get("inheritsFrom")
    targets = []
    if parent:
        targets.append(parent)
    version_name = vj.get("id", "")
    if version_name and version_name not in targets:
        targets.append(version_name)
    for ver_name in targets:
        jar_path = os.path.join(mc_dir, "versions", ver_name, f"{ver_name}.jar")
        if os.path.isfile(jar_path):
            continue
        json_path = os.path.join(mc_dir, "versions", ver_name, f"{ver_name}.json")
        if not os.path.isfile(json_path):
            continue
        with open(json_path, "r", encoding="utf-8") as f:
            vdata = json.load(f)
        client_dl = vdata.get("downloads", {}).get("client", {})
        url = client_dl.get("url", "")
        if not url:
            continue
        print(f"  Downloading {ver_name}.jar ...", flush=True)
        os.makedirs(os.path.dirname(jar_path), exist_ok=True)
        req = urllib.request.Request(url, headers={
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
        })
        with urllib.request.urlopen(req, timeout=120) as resp:
            data = resp.read()
        with open(jar_path, "wb") as f:
            f.write(data)
        print(f"  Downloaded {ver_name}.jar ({len(data)} bytes)")


def ensure_asset_index(vj, mc_dir=None):
    import urllib.request
    mc_dir = mc_dir or MC_DIR
    ai = vj.get("assetIndex")
    if not ai:
        return
    aid = vj.get("assets", ai.get("id", ""))
    if not aid:
        return
    indexes_dir = os.path.join(mc_dir, "assets", "indexes")
    index_path = os.path.join(indexes_dir, f"{aid}.json")
    if os.path.isfile(index_path):
        return
    url = ai.get("url", "")
    if not url:
        return
    print(f"  Downloading asset index {aid}.json ...", flush=True)
    os.makedirs(indexes_dir, exist_ok=True)
    req = urllib.request.Request(url, headers={
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
    })
    with urllib.request.urlopen(req, timeout=60) as resp:
        data = resp.read()
    with open(index_path, "wb") as f:
        f.write(data)
    print(f"  Downloaded asset index {aid}.json ({len(data)} bytes)")


def download_missing_assets(vj, mc_dir=None):
    import urllib.request
    import hashlib
    import concurrent.futures
    mc_dir = mc_dir or MC_DIR
    ai = vj.get("assetIndex")
    if not ai:
        return
    aid = vj.get("assets", ai.get("id", ""))
    if not aid:
        return
    index_path = os.path.join(mc_dir, "assets", "indexes", f"{aid}.json")
    if not os.path.isfile(index_path):
        return
    with open(index_path, "r", encoding="utf-8") as f:
        idx = json.load(f)
    objects = idx.get("objects", {})
    if not objects:
        return
    objects_dir = os.path.join(mc_dir, "assets", "objects")
    missing = []
    for name, info in objects.items():
        h = info.get("hash", "")
        sz = info.get("size", 0)
        if not h:
            continue
        obj_path = os.path.join(objects_dir, h[:2], h)
        if os.path.isfile(obj_path) and os.path.getsize(obj_path) == sz:
            continue
        missing.append((name, h, sz))
    if not missing:
        return
    total = len(missing)
    done = [0]
    lock = __import__("threading").Lock()

    def dl_one(item):
        name, h, sz = item
        obj_path = os.path.join(objects_dir, h[:2], h)
        os.makedirs(os.path.dirname(obj_path), exist_ok=True)
        url = f"https://resources.download.minecraft.net/{h[:2]}/{h}"
        for attempt in range(3):
            try:
                req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
                with urllib.request.urlopen(req, timeout=30) as resp:
                    data = resp.read()
                if len(data) == sz:
                    with open(obj_path, "wb") as f:
                        f.write(data)
                    with lock:
                        done[0] += 1
                        if done[0] % 50 == 0 or done[0] == total:
                            print(f"  Assets: {done[0]}/{total}", flush=True)
                    return True
            except Exception:
                pass
        return False

    print(f"[LAUNCH] Downloading {total} missing assets...", flush=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(dl_one, missing))
    failed = sum(1 for r in results if not r)
    if failed:
        print(f"[LAUNCH] Warning: {failed} assets failed to download")
    else:
        print(f"[LAUNCH] All {total} assets downloaded")


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
        elif len(parts) >= 3:
            group_path = parts[0].replace(".", "/")
            artifact_id = parts[1]
            ver = parts[2]
            jar_dir = os.path.join(mc_dir, "libraries", group_path, artifact_id, ver)
            is_forge = "minecraftforge" in parts[0] or "net.minecraftforge" in parts[0]
            suffixes = [""] if not is_forge else ["", "-universal", "-client"]
            found = False
            for suffix in suffixes:
                jar_name = f"{artifact_id}-{ver}{suffix}.jar"
                full = os.path.join(jar_dir, jar_name)
                if os.path.isfile(full):
                    cp.append(full)
                    found = True
                    break
            if found:
                continue
            jar_name = f"{artifact_id}-{ver}.jar"
            full = os.path.join(jar_dir, jar_name)
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


_FLAGS_REQUIRING_JDK22 = {
    "--sun-misc-unsafe-memory-access",
}

_FLAGS_REQUIRING_JDK24 = {
    "-XX:+UseCompactObjectHeaders",
}

_EXPERIMENTAL_FLAGS = {
    "-XX:+UseObjectPreBlockEncryption",
}


def _java_major_version(java_exe):
    try:
        out = subprocess.check_output(
            [java_exe, "-version"], stderr=subprocess.STDOUT, text=True, timeout=10
        )
        for line in out.splitlines():
            if "version" in line:
                part = line.split('"')[1] if '"' in line else ""
                if part.startswith("1."):
                    return int(part.split(".")[1])
                main = part.split(".")[0]
                return int(main) if main.isdigit() else 0
    except Exception:
        pass
    return 0


def build_jvm_args(vj, natives_dir, mc_dir=None, java_exe="java"):
    mc_dir = mc_dir or MC_DIR
    java_ver = _java_major_version(java_exe)
    strip_jdk22_flags = java_ver < 22
    strip_jdk24_flags = java_ver < 24
    args = []
    jvm_raw = vj.get("arguments", {}).get("jvm", [])
    needs_unlock = False
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
            flag = arg.split("=")[0] if "=" in arg else arg.split(" ")[0]
            if strip_jdk22_flags and flag in _FLAGS_REQUIRING_JDK22:
                continue
            if strip_jdk24_flags and flag in _FLAGS_REQUIRING_JDK24:
                continue
            if arg in _EXPERIMENTAL_FLAGS:
                needs_unlock = True
            args.append(replace_vars(arg, natives_dir, mc_dir))
        elif isinstance(arg, dict):
            if should_include_arg(arg):
                val = arg.get("value")
                items = val if isinstance(val, list) else [val]
                for v in items:
                    if not isinstance(v, str):
                        continue
                    v_flag = v.split("=")[0] if "=" in v else v.split(" ")[0]
                    if strip_jdk22_flags and v_flag in _FLAGS_REQUIRING_JDK22:
                        continue
                    if strip_jdk24_flags and v_flag in _FLAGS_REQUIRING_JDK24:
                        continue
                    if v in _EXPERIMENTAL_FLAGS:
                        needs_unlock = True
                    args.append(replace_vars(v, natives_dir, mc_dir))
    if needs_unlock:
        args.insert(0, "-XX:+UnlockExperimentalVMOptions")
    if not args and not jvm_raw:
        args.append(replace_vars("-Djava.library.path=${natives_directory}", natives_dir, mc_dir))
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
        "${user_properties}": "{}",
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
    if not args:
        mc_args_str = vj.get("minecraftArguments", "")
        if mc_args_str:
            import shlex
            for tok in shlex.split(mc_args_str):
                a = tok
                for k, v in replacements.items():
                    a = a.replace(k, v)
                if a.startswith("${") and a.endswith("}"):
                    continue
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
    cp_sep = ";" if platform.system() == "Windows" else ":"
    lib_dir = os.path.join(mc_dir, "libraries")
    return (
        s.replace("${natives_directory}", natives_dir)
        .replace("${launcher_name}", "minecraft-mcp")
        .replace("${launcher_version}", "1.0")
        .replace("${game_directory}", mc_dir)
        .replace("${library_directory}", lib_dir)
        .replace("${classpath_separator}", cp_sep)
        .replace("${version_name}", "")
        .replace("${auth_player_name}", "Player")
        .replace("${auth_uuid}", "00000000-00000000-00000000-00000000")
        .replace("${auth_access_token}", "0")
        .replace("${user_type}", "msa")
        .replace("${version_type}", "release")
    )


_JDK_LOOKUP = {
    8:  r"C:\Users\langy\.jdks\jdk8",
    21: r"C:\Program Files\Amazon Corretto\jdk21.0.8_9",
    24: r"C:\Users\langy\.jdks\openjdk-24.0.2+12-54",
    25: r"C:\Program Files\Amazon Corretto\jdk25.0.3_9",
}


def find_java(version_java=None):
    ver = int(version_java) if version_java else None
    if ver and ver in _JDK_LOOKUP:
        home = _JDK_LOOKUP[ver]
        exe = os.path.join(home, "bin", "java.exe" if platform.system() == "Windows" else "java")
        if os.path.isfile(exe):
            return exe
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
    parser.add_argument("--loader", default=None, help="Loader type filter (forge/neoforge/fabric)")
    args = parser.parse_args()

    mc_dir = args.mc_dir or MC_DIR
    version_name = args.version

    mc_version = version_name.split("-")[0] if "-" in version_name else version_name

    print(f"[LAUNCH] MC version: {version_name}")
    print(f"[LAUNCH] MC dir: {mc_dir}")

    vj = merge_version_json(version_name, mc_dir)
    ensure_asset_index(vj, mc_dir)
    download_missing_assets(vj, mc_dir)
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

    java_ver = vj.get("javaVersion", {}).get("majorVersion", 21)
    java_exe = args.java or find_java(java_ver)
    print(f"[LAUNCH] Java: {java_exe} (MC wants {java_ver})")

    jvm_args = build_jvm_args(vj, natives_dir, mc_dir, java_exe)
    game_args = build_game_args(vj, version_name, mc_dir, args.username)

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

    _GRADLE_CACHE = os.path.join(os.path.expanduser("~"), ".gradle", "caches",
                                  "modules-2", "files-2.1")

    def _find_in_gradle_cache(group, artifact, version):
        cache_dir = os.path.join(_GRADLE_CACHE, group, artifact, version)
        if not os.path.isdir(cache_dir):
            return None
        for root, dirs, files in os.walk(cache_dir):
            for f in files:
                if f.endswith(".jar") and "sources" not in f and "javadoc" not in f:
                    return os.path.join(root, f)
        return None

    for group, artifact, version in [
        ("net.java.dev.jna", "jna", "5.15.0"),
        ("net.java.dev.jna", "jna-platform", "5.15.0"),
    ]:
        already_in_cp = any(
            os.path.basename(p).lower().startswith(artifact.lower() + "-")
            for p in cp
        )
        if already_in_cp:
            print(f"[LAUNCH] {artifact} already in classpath, skipping")
            continue
        lib_dir_jar = os.path.join(mc_dir, "libraries",
                                    group.replace(".", os.sep), artifact, version,
                                    f"{artifact}-{version}.jar")
        if os.path.isfile(lib_dir_jar):
            cp.append(lib_dir_jar)
            print(f"[LAUNCH] Added {artifact} to classpath (lib dir)")
        else:
            gradle_jar = _find_in_gradle_cache(group, artifact, version)
            if gradle_jar:
                os.makedirs(os.path.dirname(lib_dir_jar), exist_ok=True)
                shutil.copy2(gradle_jar, lib_dir_jar)
                cp.append(lib_dir_jar)
                print(f"[LAUNCH] Added {artifact} to classpath (gradle cache -> lib dir)")
            else:
                print(f"[LAUNCH] WARNING: {artifact}-{version} not found")

    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    mods_dir = os.path.join(mc_dir, "mods")
    os.makedirs(mods_dir, exist_ok=True)

    loader_filter = args.loader
    if not loader_filter:
        if "neoforge" in version_name.lower() or "neoforge" in (vj.get("id", "") + vj.get("inheritsFrom", "")).lower():
            loader_filter = "neoforge"
        elif "fabric" in version_name.lower() or "fabric" in (vj.get("id", "") + vj.get("inheritsFrom", "")).lower():
            loader_filter = "fabric"
        else:
            loader_filter = "forge"

    mod_base_dir = os.path.join(project_root, "packages", "mods", mc_version)
    mod_jar_dirs = []
    if os.path.isdir(mod_base_dir):
        loader_dir = os.path.join(mod_base_dir, loader_filter, "build", "libs")
        if os.path.isdir(loader_dir):
            mod_jar_dirs.append(loader_dir)
        else:
            for ld in os.listdir(mod_base_dir):
                ld_path = os.path.join(mod_base_dir, ld, "build", "libs")
                if os.path.isdir(ld_path):
                    mod_jar_dirs.append(ld_path)

    for existing in glob.glob(os.path.join(mods_dir, "*.jar")):
        try:
            os.remove(existing)
            print(f"[LAUNCH] Removed old jar: {os.path.basename(existing)}")
        except Exception:
            pass

    for lib_dir in mod_jar_dirs:
        for jar in glob.glob(os.path.join(lib_dir, "*.jar")):
            if "sources" in os.path.basename(jar):
                continue
            dest = os.path.join(mods_dir, os.path.basename(jar))
            shutil.copy2(jar, dest)
            print(f"[LAUNCH] Synced mod jar: {os.path.basename(jar)} ({os.path.getsize(dest)} bytes)")

    common_jars = sorted(glob.glob(os.path.join(project_root, "packages", "common", "build", "libs", "*.jar")))
    for jar in common_jars:
        if "sources" in os.path.basename(jar):
            continue

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
    sys.stdout.reconfigure(errors="replace")
    main()

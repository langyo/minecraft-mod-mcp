"""Launch Minecraft directly from version JSON files.
Handles classpath construction, native extraction, and proper argument building.

Usage:
  python scripts/launch_mc.py 1.21.7-forge-57.0.2
  python scripts/launch_mc.py 1.21.7-forge-57.0.2 --jvm-args "-Xmx4G -Xms2G"

TODO: add Fabric loader profile installation support (was install_fabric.py)
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


def patch_lwjgl2_headless(cp):
    """Patch LWJGL 2.x LinuxDisplay to return a fallback display mode on headless."""
    try:
        _patch_lwjgl2_headless_impl(cp)
    except Exception as e:
        import traceback
        print(f"[LAUNCH] LWJGL patch ERROR: {e}", flush=True)
        traceback.print_exc(file=sys.stdout)
        sys.stdout.flush()


def _patch_lwjgl2_headless_impl(cp):
    lwjgl_jar = None
    lwjgl_candidates = []
    for p in cp:
        bn = os.path.basename(p).lower()
        if "lwjgl" in bn and bn.endswith(".jar") and "platform" not in bn and "natives" not in bn and "source" not in bn and "util" not in bn:
            lwjgl_candidates.append(p)
    if lwjgl_candidates:
        for c in lwjgl_candidates:
            if os.path.basename(c).lower().startswith("lwjgl-"):
                lwjgl_jar = c
                break
        if not lwjgl_jar:
            lwjgl_jar = lwjgl_candidates[0]
    if not lwjgl_jar:
        print(f"[LAUNCH] LWJGL patch: no lwjgl jar found in classpath ({len(cp)} entries)", flush=True)
        return
    patched_marker = lwjgl_jar + ".headless-patched"
    if os.path.isfile(patched_marker):
        print(f"[LAUNCH] LWJGL patch: already patched ({os.path.basename(lwjgl_jar)})", flush=True)
        return
    import struct as _struct

    CLASS_NAME = "org/lwjgl/opengl/LinuxDisplay"
    METHOD_NAME = "getAvailableDisplayModes"
    METHOD_DESC = "()[Lorg/lwjgl/opengl/DisplayMode;"
    DM_CLASS = "org/lwjgl/opengl/DisplayMode"
    DM_ARR_CLASS = "[Lorg/lwjgl/opengl/DisplayMode;"
    DM_INIT = "<init>"
    try:
        with zipfile.ZipFile(lwjgl_jar, "r") as zf:
            if CLASS_NAME + ".class" not in zf.namelist():
                print(f"[LAUNCH] LWJGL patch: {CLASS_NAME}.class not found in {os.path.basename(lwjgl_jar)}", flush=True)
                return
            data = zf.read(CLASS_NAME + ".class")
    except Exception as e:
        print(f"[LAUNCH] LWJGL patch: failed to read jar: {e}")
        return

    def _u2(d, o):
        return _struct.unpack_from(">H", d, o)[0]

    def _u4(d, o):
        return _struct.unpack_from(">I", d, o)[0]

    def _skip_cp_entry(d, o):
        tag = d[o]
        o += 1
        FIXED = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4,
                 11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
        if tag == 1:
            length = _u2(d, o)
            o += 2 + length
        elif tag in FIXED:
            o += FIXED[tag]
            if tag in (5, 6):
                o += 0  # takes 2 slots handled by caller
        else:
            return -1
        return o

    def _parse_cp(d):
        cp_count = _u2(d, 8)
        off = 10
        utf8s = {}
        classes = {}
        nats = {}
        methodrefs = {}
        idx = 1
        while idx < cp_count:
            tag = d[off]
            off += 1
            if tag == 1:
                length = _u2(d, off)
                off += 2
                utf8s[idx] = bytes(d[off:off + length]).decode("utf-8", errors="replace")
                off += length
            elif tag == 7:
                classes[idx] = _u2(d, off)
                off += 2
            elif tag == 8:
                off += 2
            elif tag == 9:
                off += 4
            elif tag == 12:
                nats[idx] = (_u2(d, off), _u2(d, off + 2))
                off += 4
            elif tag == 10:
                methodrefs[idx] = (_u2(d, off), _u2(d, off + 2))
                off += 4
            elif tag == 11:
                off += 4
            elif tag == 3 or tag == 4:
                off += 4
            elif tag == 5:
                off += 8
                idx += 1
            elif tag == 6:
                off += 8
            elif tag == 15:
                off += 3
            elif tag == 16:
                off += 2
            elif tag == 17:
                off += 4
            elif tag == 18:
                off += 4
            elif tag == 19 or tag == 20:
                off += 2
            else:
                return None, None, None, None
            idx += 1
        return utf8s, classes, nats, methodrefs

    utf8s, classes, nats, methodrefs = _parse_cp(data)
    if utf8s is None:
        print(f"[LAUNCH] LWJGL patch: constant pool parse failed", flush=True)
        return

    dm_class_idx = None
    dm_arr_idx = None
    for ci, ni in classes.items():
        name = utf8s.get(ni, "")
        if name == DM_CLASS:
            dm_class_idx = ci
        elif name == DM_ARR_CLASS:
            dm_arr_idx = ci
    if not dm_class_idx:
        print(f"[LAUNCH] LWJGL patch: DisplayMode class not found in CP", flush=True)
        return
    if not dm_arr_idx:
        utf8_name_idx = max(utf8s.keys()) + 1
        dm_arr_idx = max(classes.keys()) + 1
        utf8_name_bytes = b"\x01" + _struct.pack(">H", len(DM_ARR_CLASS)) + DM_ARR_CLASS.encode("utf-8")
        class_bytes = b"\x07" + _struct.pack(">H", utf8_name_idx)
        data = bytearray(data)
        cp_count_pos = 8
        old_cp_count = _u2(data, cp_count_pos)
        new_cp_count = old_cp_count + 2
        _struct.pack_into(">H", data, cp_count_pos, new_cp_count)
        cp_end = 10
        tmp_idx = 1
        while tmp_idx < old_cp_count:
            tag = data[cp_end]
            cp_end += 1
            if tag == 1:
                l = _u2(data, cp_end); cp_end += 2 + l
            elif tag in (3, 4): cp_end += 4
            elif tag == 5: cp_end += 8; tmp_idx += 1
            elif tag == 6: cp_end += 8
            elif tag in (7, 8, 16, 19, 20): cp_end += 2
            elif tag in (9, 10, 11, 12, 17, 18): cp_end += 4
            elif tag == 15: cp_end += 3
            tmp_idx += 1
        data = bytes(data[:cp_end]) + utf8_name_bytes + class_bytes + bytes(data[cp_end:])
        utf8s[utf8_name_idx] = DM_ARR_CLASS
        classes[dm_arr_idx] = utf8_name_idx
        print(f"[LAUNCH] LWJGL patch: injected DM[] CP entries utf8={utf8_name_idx} class={dm_arr_idx}", flush=True)

    dm_init_ref = None
    dm_init_desc_str = None
    for mi, (ci, ni) in methodrefs.items():
        cls_name = utf8s.get(classes.get(ci, 0), "")
        n, d = nats.get(ni, (0, 0))
        mname = utf8s.get(n, "")
        mdesc = utf8s.get(d, "")
        if cls_name == DM_CLASS and mname == DM_INIT:
            dm_init_ref = mi
            dm_init_desc_str = mdesc
            break
    if not dm_init_ref:
        print(f"[LAUNCH] LWJGL patch: DisplayMode.<init> not found in methodrefs (checked {len(methodrefs)})", flush=True)
        return
    print(f"[LAUNCH] LWJGL patch: found dm_class={dm_class_idx} dm_arr={dm_arr_idx} init_ref={dm_init_ref} desc={dm_init_desc_str}", flush=True)

    new_bytecode = bytearray()
    new_bytecode.append(0x04)  # iconst_1
    new_bytecode.extend(b"\xbd" + dm_arr_idx.to_bytes(2, "big"))  # anewarray DisplayMode[]
    new_bytecode.append(0x59)  # dup
    new_bytecode.append(0x03)  # iconst_0
    new_bytecode.extend(b"\xbb" + dm_class_idx.to_bytes(2, "big"))  # new DisplayMode
    new_bytecode.append(0x59)  # dup
    new_bytecode.extend(b"\x11\x04\x00")  # sipush 1024
    new_bytecode.extend(b"\x11\x03\x00")  # sipush 768
    if dm_init_desc_str and dm_init_desc_str.startswith("(IIII"):
        new_bytecode.extend(b"\x10\x18")  # bipush 24
        new_bytecode.extend(b"\x10\x3c")  # bipush 60
        if dm_init_desc_str == "(IIIZ)V":
            new_bytecode.append(0x04)  # iconst_1 (fullscreen=true)
    new_bytecode.extend(b"\xb7" + dm_init_ref.to_bytes(2, "big"))  # invokespecial <init>
    new_bytecode.append(0x53)  # aastore
    new_bytecode.append(0xb0)  # areturn
    code_len = len(new_bytecode)

    max_stack = 5  # array, array, 0, DM, DM
    max_stack += 2  # 1024, 768
    if dm_init_desc_str and dm_init_desc_str.startswith("(IIII"):
        max_stack += 2  # bpp, freq
        if dm_init_desc_str == "(IIIZ)V":
            max_stack += 1  # fullscreen
    code_attr_body = bytearray()
    code_attr_body.extend(_struct.pack(">H", max_stack))   # max_stack
    code_attr_body.extend(_struct.pack(">H", 1))   # max_locals (JVM requires >=1)
    code_attr_body.extend(_struct.pack(">I", code_len))
    code_attr_body.extend(new_bytecode)
    code_attr_body.extend(_struct.pack(">H", 0))   # exception_table_length
    code_attr_body.extend(_struct.pack(">H", 0))   # attributes_count

    def _find_and_replace_code(d):
        off = 10
        cp_count = _u2(d, 8)
        idx = 1
        while idx < cp_count:
            tag = d[off]
            off += 1
            sz = {1: 2, 3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4,
                  11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4}
            if tag not in sz:
                return None
            off += sz[tag]
            if tag == 1:
                length = _u2(d, off - 2)
                off += length
            elif tag in (5, 6):
                idx += 1
            idx += 1
        off += 6
        iface_count = _u2(d, off)
        off += 2 + iface_count * 2
        field_count = _u2(d, off)
        off += 2
        for _ in range(field_count):
            off += 6
            ac = _u2(d, off)
            off += 2
            for _ in range(ac):
                off += 2
                alen = _u4(d, off)
                off += 4 + alen
        method_count = _u2(d, off)
        off += 2
        for _ in range(method_count):
            m_access = _u2(d, off)
            m_name = _u2(d, off + 2)
            m_desc = _u2(d, off + 4)
            mn = utf8s.get(m_name, "")
            md = utf8s.get(m_desc, "")
            off += 6
            ac = _u2(d, off)
            off += 2
            for _ in range(ac):
                attr_start = off
                attr_name_idx = _u2(d, off)
                off += 2
                alen = _u4(d, off)
                off += 4
                if mn == METHOD_NAME and md == METHOD_DESC and utf8s.get(attr_name_idx) == "Code":
                    old_total = 2 + 4 + alen
                    new_body = _struct.pack(">H", attr_name_idx) + _struct.pack(">I", len(code_attr_body)) + bytes(code_attr_body)
                    pre = d[:attr_start]
                    post = d[attr_start + old_total:]
                    return pre + new_body + post
                off += alen
        return None

    result = _find_and_replace_code(data)
    if not result:
        print(f"[LAUNCH] LWJGL patch: failed to find/replace Code attribute", flush=True)
        return
    print(f"[LAUNCH] LWJGL patch: code replaced, new class size={len(result)}, rebuilding jar...", flush=True)

    try:
        tmp_jar = lwjgl_jar + ".tmp"
        with zipfile.ZipFile(lwjgl_jar, "r") as zin:
            with zipfile.ZipFile(tmp_jar, "w", zipfile.ZIP_DEFLATED) as zout:
                for item in zin.infolist():
                    if item.filename == CLASS_NAME + ".class":
                        zout.writestr(item, bytes(result))
                    else:
                        zout.writestr(item, zin.read(item.filename))
        shutil.move(tmp_jar, lwjgl_jar)
        with open(patched_marker, "w") as f:
            f.write("patched")
        print(f"[LAUNCH] Patched LWJGL 2.x LinuxDisplay for headless mode", flush=True)
    except Exception as e:
        print(f"[LAUNCH] WARNING: Failed to patch LWJGL: {e}", flush=True)


def merge_version_json(version_name, mc_dir=None):
    mc_dir = mc_dir or MC_DIR
    version_dir = os.path.join(mc_dir, "versions", version_name)
    json_path = os.path.join(version_dir, f"{version_name}.json")
    with open(json_path, "r", encoding="utf-8") as f:
        vj = json.load(f)
    parent = vj.get("inheritsFrom")
    if parent:
        parent_json_path = os.path.join(mc_dir, "versions", parent, f"{parent}.json")
        if not os.path.isfile(parent_json_path):
            try:
                from install_forge import download_vanilla
                download_vanilla(parent)
            except Exception:
                pass
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
    failed = 0
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
                    failed += 1
        for classifier_key in ("natives-windows", "natives-windows-arm64",
                                "natives-linux", "natives-osx"):
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
        print(f"  Libraries: {downloaded} downloaded, {failed} failed")
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
                            base = os.path.basename(fn)
                            target = os.path.join(natives_dir, base)
                            if not os.path.isfile(target):
                                zf.extract(info, natives_dir)
            except Exception:
                pass
        if "natives-" in name and not native_jar:
            art = dl.get("artifact", {})
            path = art.get("path", "")
            if path:
                native_jar = os.path.join(mc_dir, "libraries", path)
                if os.path.isfile(native_jar):
                    try:
                        with zipfile.ZipFile(native_jar, "r") as zf:
                            for info in zf.infolist():
                                fn = info.filename
                                if fn.endswith((".dll", ".so", ".dylib", ".jnilib")):
                                    if "META-INF" in fn:
                                        continue
                                    base = os.path.basename(fn)
                                    target = os.path.join(natives_dir, base)
                                    if not os.path.isfile(target):
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


def build_jvm_args(vj, natives_dir, mc_dir=None, java_exe="java", version_name=""):
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
            args.append(replace_vars(arg, natives_dir, mc_dir, version_name))
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
                    args.append(replace_vars(v, natives_dir, mc_dir, version_name))
    if needs_unlock:
        args.insert(0, "-XX:+UnlockExperimentalVMOptions")
    if not args and not jvm_raw:
        args.append(replace_vars("-Djava.library.path=${natives_directory}", natives_dir, mc_dir, version_name))
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


def replace_vars(s, natives_dir, mc_dir, version_name=""):
    cp_sep = ";" if platform.system() == "Windows" else ":"
    lib_dir = os.path.join(mc_dir, "libraries")
    return (
        s.replace("${natives_directory}", natives_dir)
        .replace("${launcher_name}", "minecraft-mcp")
        .replace("${launcher_version}", "1.0")
        .replace("${game_directory}", mc_dir)
        .replace("${library_directory}", lib_dir)
        .replace("${classpath_separator}", cp_sep)
        .replace("${version_name}", version_name)
        .replace("${auth_player_name}", "Player")
        .replace("${auth_uuid}", "00000000-0000-0000-0000-00000000000")
        .replace("${auth_access_token}", "0")
        .replace("${user_type}", "msa")
        .replace("${version_type}", "release")
    )


_IS_WINDOWS = platform.system() == "Windows"
_EXE_SUFFIX = ".exe" if _IS_WINDOWS else ""


_JAVA_HOME_ENV_KEYS = ["JAVA_HOME", "JAVA_HOME_8_X64", "JAVA_HOME_17_X64", "JAVA_HOME_21_X64",
                       "JAVA_HOME_25_X64", "JDK_21"]


def find_java(version_java=None):
    ver = int(version_java) if version_java else None
    if ver:
        ver_keys = [f"JAVA_HOME_{ver}_X64", f"JAVA_HOME_{ver}"]
        for ek in ver_keys:
            home = os.environ.get(ek)
            if home:
                exe = os.path.join(home, "bin", f"java{_EXE_SUFFIX}")
                if os.path.isfile(exe):
                    return exe
        jdk_home = _find_jdk_home(ver)
        if jdk_home:
            exe = os.path.join(jdk_home, "bin", f"java{_EXE_SUFFIX}")
            if os.path.isfile(exe):
                return exe
    for env_key in _JAVA_HOME_ENV_KEYS:
        home = os.environ.get(env_key)
        if home:
            exe = os.path.join(home, "bin", f"java{_EXE_SUFFIX}")
            if os.path.isfile(exe):
                return exe
    return "java"


def _find_jdk_home(target_ver):
    if platform.system() == "Windows":
        corretto_base = r"C:\Program Files\Amazon Corretto"
        if os.path.isdir(corretto_base):
            for d in sorted(os.listdir(corretto_base), reverse=True):
                if d.startswith(f"jdk{target_ver}.") or d.startswith(f"jdk1.{target_ver}."):
                    path = os.path.join(corretto_base, d)
                    if os.path.isfile(os.path.join(path, "bin", f"java{_EXE_SUFFIX}")):
                        return path
        adoptium_base = r"C:\Program Files\Eclipse Adoptium"
        if os.path.isdir(adoptium_base):
            for d in sorted(os.listdir(adoptium_base), reverse=True):
                if f"-{target_ver}." in d:
                    path = os.path.join(adoptium_base, d)
                    if os.path.isfile(os.path.join(path, "bin", f"java{_EXE_SUFFIX}")):
                        return path
    jdks_dir = os.path.join(os.path.expanduser("~"), ".gradle", "jdks")
    if os.path.isdir(jdks_dir):
        for d in sorted(os.listdir(jdks_dir), reverse=True):
            if f"-{target_ver}." in d and "lock" not in d:
                path = os.path.join(jdks_dir, d)
                exe = os.path.join(path, "bin", f"java{_EXE_SUFFIX}")
                if platform.system() != "Windows":
                    exe = os.path.join(path, "bin", "java")
                if os.path.isfile(exe):
                    return path
    return None


def _launch_neoforge_gradlew(mc_version, args):
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    mod_dir = os.path.join(project_root, "packages", "mods", mc_version, "neoforge")

    if not os.path.isdir(mod_dir):
        print(f"[LAUNCH] ERROR: NeoForge mod directory not found: {mod_dir}")
        sys.exit(1)

    gradlew = os.path.join(mod_dir, "gradlew.bat" if platform.system() == "Windows" else "gradlew")
    if not os.path.isfile(gradlew):
        gradlew = os.path.join(mod_dir, "gradlew")
    if not os.path.isfile(gradlew):
        print(f"[LAUNCH] ERROR: gradlew not found in {mod_dir}")
        sys.exit(1)

    version_config = {}
    vc_path = os.path.join(script_dir, "version_config.py")
    if os.path.isfile(vc_path):
        import importlib.util
        spec = importlib.util.spec_from_file_location("version_config", vc_path)
        vc_mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(vc_mod)
        version_config = vc_mod.ALL_VERSIONS.get(mc_version, {})

    java_ver = version_config.get("java", 21)
    java_exe = find_java(java_ver)
    found_ver = _java_major_version(java_exe) if java_exe != "java" and os.path.isfile(java_exe) else 0

    if found_ver != java_ver:
        java_home = _find_jdk_home(java_ver)
        if java_home:
            java_exe = os.path.join(java_home, "bin", f"java{_EXE_SUFFIX}")
            print(f"[LAUNCH] Found JDK {java_ver} at {java_home}")

    java_home_dir = os.path.dirname(os.path.dirname(java_exe)) if java_exe != "java" and os.path.isfile(java_exe) else None

    gradle_args = ["runClient"]

    mc_dir = args.mc_dir or MC_DIR
    env = os.environ.copy()
    if java_home_dir:
        env["JAVA_HOME"] = java_home_dir

    jvm_opts = []
    if args.jvm_args:
        for a in args.jvm_args.split():
            if a.startswith("-X"):
                jvm_opts.append(a)
    if args.headless:
        jvm_opts.append("-Djava.awt.headless=true")
    if jvm_opts:
        env["GRADLE_OPTS"] = (env.get("GRADLE_OPTS", "") + " " + " ".join(jvm_opts)).strip()

    cmd = [gradlew] + gradle_args
    cmd_str = " ".join(cmd)

    if args.dry_run:
        print(f"[DRY-RUN] NeoForge runClient:")
        print(f"  cwd: {mod_dir}")
        print(f"  cmd: {cmd_str}")
        print(f"  JAVA_HOME: {env.get('JAVA_HOME', '(not set)')}")
        return

    print(f"[LAUNCH] NeoForge runClient: {mc_version}/neoforge")
    print(f"  cwd: {mod_dir}")
    print(f"  cmd: {cmd_str}")
    print(f"  JAVA_HOME: {env.get('JAVA_HOME', '(default)')}")

    proc = subprocess.Popen(cmd, cwd=mod_dir, env=env)
    print(f"[LAUNCH] Process started: pid={proc.pid}")
    rc = proc.wait()
    print(f"[LAUNCH] Process exited with code {rc}")


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
    parser.add_argument("--headless", action="store_true", help="Launch in headless mode (Xvfb+llvmpipe)")
    parser.add_argument("--width", default="854", help="Window width (default 854)")
    parser.add_argument("--height", default="480", help="Window height (default 480)")
    parser.add_argument("--world", default=None, help="World save name to load directly")
    parser.add_argument("--no-assets-download", action="store_true",
                        help="Skip asset downloading (CI pre-cache)")
    parser.add_argument("--no-mod-sync", action="store_true",
                        help="Skip mod JAR syncing from packages/mods (CI pre-installed)")
    parser.add_argument("--fixed-framerate", type=int, default=0,
                        help="Fixed framerate cap (0=uncapped)")
    args = parser.parse_args()

    mc_dir = args.mc_dir or MC_DIR
    version_name = args.version

    loader_filter = args.loader
    if not loader_filter:
        if "neoforge" in version_name.lower():
            loader_filter = "neoforge"
        elif "fabric" in version_name.lower():
            loader_filter = "fabric"
        else:
            loader_filter = "forge"

    if loader_filter == "fabric" and version_name.startswith("fabric-loader"):
        import re
        matches = re.findall(r"(\d+\.\d+(?:\.\d+)?)", version_name)
        mc_version = matches[-1] if matches else version_name
    else:
        mc_version = version_name.split("-")[0] if "-" in version_name else version_name

    if "-" not in version_name:
        _vc_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "version_config.py")
        if os.path.isfile(_vc_path):
            import importlib.util
            spec = importlib.util.spec_from_file_location("version_config", _vc_path)
            vc_mod = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(vc_mod)
            vc_entry = vc_mod.ALL_VERSIONS.get(mc_version, {})
            resolved = vc_entry.get("version_id")
            if resolved:
                version_name = resolved
                print(f"[LAUNCH] Resolved version: {version_name} (loader={loader_filter})")

    if loader_filter == "neoforge":
        _launch_neoforge_gradlew(mc_version, args)
        return

    print(f"[LAUNCH] MC version: {version_name}")
    print(f"[LAUNCH] MC dir: {mc_dir}")

    vj = merge_version_json(version_name, mc_dir)
    ensure_asset_index(vj, mc_dir)
    if not args.no_assets_download:
        download_missing_assets(vj, mc_dir)
    else:
        print("[LAUNCH] Skipping asset download (--no-assets-download)")
    main_class = vj.get("mainClass")
    if not main_class:
        print("[ERROR] No mainClass found in version JSON")
        sys.exit(1)
    if "fabricmc" in main_class:
        jv = vj.get("javaVersion", {})
        if not jv or jv.get("majorVersion", 8) < 17:
            vj["javaVersion"] = {"majorVersion": 17}
    print(f"[LAUNCH] mainClass: {main_class}")

    if not args.no_assets_download:
        download_missing_assets(vj, mc_dir)

    download_libraries(vj, mc_dir)
    ensure_version_jar(vj, mc_dir)

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

    jvm_args = build_jvm_args(vj, natives_dir, mc_dir, java_exe, version_name)
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

    if not args.no_mod_sync:
        for existing in glob.glob(os.path.join(mods_dir, "*.jar")):
            try:
                os.remove(existing)
                print(f"[LAUNCH] Removed old jar: {os.path.basename(existing)}")
            except Exception:
                pass

    if not args.no_mod_sync:
        for lib_dir in mod_jar_dirs:
            for jar in glob.glob(os.path.join(lib_dir, "*.jar")):
                if "sources" in os.path.basename(jar):
                    continue
                dest = os.path.join(mods_dir, os.path.basename(jar))
                shutil.copy2(jar, dest)
                print(f"[LAUNCH] Synced mod jar: {os.path.basename(jar)} ({os.path.getsize(dest)} bytes)")
    else:
        print("[LAUNCH] Skipping mod sync (--no-mod-sync)")
        existing_mods = glob.glob(os.path.join(mods_dir, "*.jar"))
        if existing_mods:
            print(f"[LAUNCH] Pre-installed mods: {len(existing_mods)}")

    common_jars = sorted(glob.glob(os.path.join(project_root, "packages", "common", "build", "libs", "*.jar")))
    for jar in common_jars:
        if "sources" in os.path.basename(jar):
            continue

    sep = ";" if platform.system() == "Windows" else ":"
    cp_str = sep.join(cp)

    cmd = [java_exe]
    cmd.extend(args.jvm_args.split())
    mc_ver = mc_version
    loader = loader_filter

    if args.headless:
        cmd.extend([
            "-Djava.awt.headless=true",
        ])
        try:
            major = int(mc_ver.split(".")[1]) if "." in mc_ver else 0
        except ValueError:
            major = 99
        if major <= 13:
            cmd.extend([
                "-Dorg.lwjgl.opengl.Display.allowSoftwareOpenGL=true",
                "-Dorg.lwjgl.opengl.Display.noinput=true",
            ])
            print(f"[LAUNCH] LWJGL 2.x detected (mc_ver={mc_ver}, major={major}), patching...", flush=True)
            patch_lwjgl2_headless(cp)
        print(f"[LAUNCH] Headless mode: width={args.width}, height={args.height}")

    cmd.extend([
        f"-Dmcp.mod.version={mc_ver}",
        f"-Dmcp.mod.loader={loader}",
    ])
    if args.extra_jvm:
        cmd.extend(args.extra_jvm.split())
    cmd.extend(jvm_args)

    parent = vj.get("_parent")
    if parent and main_class and "bootstraplauncher" in main_class.lower():
        parent_jar = parent + ".jar"
        for i, a in enumerate(cmd):
            if a.startswith("-DignoreList="):
                if parent_jar not in a:
                    cmd[i] = a.rstrip() + "," + parent_jar
                break
    cmd.extend(["-cp", cp_str])
    cmd.append(main_class)

    cmd.extend(["--width", args.width, "--height", args.height])
    if args.world:
        mc_num = mc_ver.split(".")
        major = int(mc_num[0]) if len(mc_num) > 0 and mc_num[0].isdigit() else 0
        minor = int(mc_num[1]) if len(mc_num) > 1 and mc_num[1].isdigit() else 0
        if major > 1 or (major == 1 and minor >= 20):
            cmd.extend(["--quickPlaySingleplayer", args.world])
            print(f"[LAUNCH] Quick-play world: {args.world}")
        else:
            print(f"[LAUNCH] World specified but --quickPlaySingleplayer not supported on {mc_ver}")
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

    print(f"\n[LAUNCH] Starting Minecraft...")
    print(f"  Version: {version_name}")
    print(f"  Main: {main_class}")
    print(f"  CP: {len(cp)} libs")
    print(f"  Natives: {natives_dir}")
    print(f"  Extra JVM: -Dmcp.mod.version={mc_ver} -Dmcp.mod.loader={loader}")
    if args.headless:
        print(f"  Headless: true  Window: {args.width}x{args.height}")
    if args.world:
        print(f"  World: {args.world}")
    if args.no_mod_sync:
        print(f"  Mod sync: skipped")

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

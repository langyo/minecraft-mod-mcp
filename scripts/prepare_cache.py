"""Pre-populate ALL caches for ALL mod projects.

Every build (not just legacy) benefits from pre-cached artifacts:
  - Forge/NeoForge userdev jars, POMs
  - Fabric yarn mappings
  - FG plugin jars
  - MCP snapshot mappings (FG 3/4.1)
  - MC version jars (FG 1.2)
  - NeoForge moddev bundles

Downloads with JDK 21 (handles modern TLS), places into Gradle caches.

Gradle cache layout:
  ~/.gradle/caches/modules-2/files-2.1/{group}/{artifact}/{version}/{sha1}/{file}

FG internal cache layout:
  ~/.gradle/caches/forge_gradle/mcp_repo/{maven_path}/{version}/

Usage:
  python scripts/prepare_cache.py
"""
import subprocess
import os
import sys
import hashlib
import shutil
import json

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from version_config import (
    ALL_VERSIONS, FG_ERAS, LEGACY_ERAS,
    is_legacy, get_loaders, get_fabric_loom,
)
from mirrors import probe_all as probe_mirrors, get_url, download as mirror_download, get_all_urls

TEMP_DIR = os.path.join(os.environ.get("TEMP", "/tmp"), "opencode")
DL_CLASS = os.path.join(TEMP_DIR, "Dl.class")
DL_JAVA = os.path.join(TEMP_DIR, "Dl.java")

JDK21 = r"C:\Program Files\Amazon Corretto\jdk21.0.8_9"
GRADLE_USER_HOME = os.path.join(os.path.expanduser("~"), ".gradle")
MODULES_CACHE = os.path.join(GRADLE_USER_HOME, "caches", "modules-2", "files-2.1")
FG_CACHE = os.path.join(GRADLE_USER_HOME, "caches", "forge_gradle")

PROXY_HOST = "127.0.0.1"
PROXY_PORT = 7890


def ensure_dl_class():
    if os.path.isfile(DL_CLASS):
        return
    os.makedirs(TEMP_DIR, exist_ok=True)
    java_src = r"""
import java.net.*;
import java.io.*;
import java.nio.file.*;

public class Dl {
    public static void main(String[] args) throws Exception {
        boolean useProxy = Boolean.parseBoolean(System.getProperty("useProxy", "false"));
        String proxyHost = System.getProperty("proxyHost", "127.0.0.1");
        String proxyPort = System.getProperty("proxyPort", "7890");
        for (int i = 0; i < args.length; i += 2) {
            String urlStr = args[i], filePath = args[i + 1];
            Path path = Paths.get(filePath);
            Files.createDirectories(path.getParent());
            if (Files.exists(path) && Files.size(path) > 0) {
                System.out.println("SKIP " + path.getFileName());
                continue;
            }
            URL url = new URL(urlStr);
            HttpURLConnection conn;
            if (useProxy) {
                Proxy proxy = new Proxy(Proxy.Type.HTTP,
                    new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort)));
                conn = (HttpURLConnection) url.openConnection(proxy);
            } else {
                conn = (HttpURLConnection) url.openConnection();
            }
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);
            conn.setInstanceFollowRedirects(true);
            if (conn.getResponseCode() == 200) {
                try (InputStream is = conn.getInputStream(); OutputStream os = Files.newOutputStream(path)) {
                    is.transferTo(os);
                }
                System.out.println("OK " + path.getFileName() + " (" + Files.size(path) + " bytes)");
            } else {
                System.out.println("FAIL " + conn.getResponseCode() + " " + path.getFileName());
            }
            conn.disconnect();
        }
    }
}
"""
    with open(DL_JAVA, "w") as f:
        f.write(java_src)
    javac = os.path.join(JDK21, "bin", "javac.exe")
    subprocess.run([javac, DL_JAVA], cwd=TEMP_DIR, check=True, capture_output=True)
    print(f"Compiled {DL_CLASS}")


def download_files(url_path_pairs, use_proxy=False):
    args = []
    for url, path in url_path_pairs:
        args.extend([url, path])
    cmd = [
        os.path.join(JDK21, "bin", "java.exe"),
        f"-DuseProxy={str(use_proxy).lower()}",
        f"-DproxyHost={PROXY_HOST}",
        f"-DproxyPort={PROXY_PORT}",
        "-cp", TEMP_DIR,
        "Dl",
    ] + args
    proc = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
    for line in (proc.stdout or "").strip().split("\n"):
        if line:
            print(f"  {line}")
    return proc.returncode == 0


def sha1_file(path):
    h = hashlib.sha1()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def place_in_gradle_cache(group, artifact, version, filename, src_file):
    dest_dir = os.path.join(
        MODULES_CACHE,
        group.replace(".", os.sep),
        artifact,
        version,
        sha1_file(src_file),
    )
    os.makedirs(dest_dir, exist_ok=True)
    dest = os.path.join(dest_dir, filename)
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        return True
    shutil.copy2(src_file, dest)
    return True


def download_and_cache(rel_path, group, artifact, version, filename, mirror_group, use_proxy=False):
    staging = os.path.join(TEMP_DIR, "forge_staging", artifact, version)
    os.makedirs(staging, exist_ok=True)
    tmp_file = os.path.join(staging, filename)

    if os.path.isfile(tmp_file) and os.path.getsize(tmp_file) > 0:
        place_in_gradle_cache(group, artifact, version, filename, tmp_file)
        return True

    for base_url in get_all_urls(mirror_group):
        url = f"{base_url.rstrip('/')}/{rel_path.lstrip('/')}"
        success = download_files([(url, tmp_file)], use_proxy=use_proxy)
        if success and os.path.isfile(tmp_file) and os.path.getsize(tmp_file) > 0:
            place_in_gradle_cache(group, artifact, version, filename, tmp_file)
            return True

    return False


def download_mc_jar(mc_ver):
    staging = os.path.join(TEMP_DIR, "mc_jars", mc_ver)
    os.makedirs(staging, exist_ok=True)
    tmp_file = os.path.join(staging, f"{mc_ver}.jar")
    if os.path.isfile(tmp_file) and os.path.getsize(tmp_file) > 0:
        return tmp_file
    try:
        import urllib.request
        manifest_url = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        resp = urllib.request.urlopen(manifest_url, timeout=30)
        manifest = json.loads(resp.read())
        ver_entry = next((v for v in manifest["versions"] if v["id"] == mc_ver), None)
        if not ver_entry:
            print(f"  FAIL {mc_ver} not in version manifest")
            return None
        ver_resp = urllib.request.urlopen(ver_entry["url"], timeout=30)
        ver_data = json.loads(ver_resp.read())
        dl = ver_data.get("downloads", {}).get("client", {})
        dl_url = dl.get("url", "")
        if not dl_url:
            print(f"  FAIL no client download for {mc_ver}")
            return None
        print(f"  Downloading {mc_ver}...")
        urllib.request.urlretrieve(dl_url, tmp_file)
        print(f"  OK {mc_ver}.jar ({os.path.getsize(tmp_file)} bytes)")
        return tmp_file
    except Exception as e:
        print(f"  FAIL {mc_ver}: {e}")
        return None


def place_in_fg_mcp_repo(channel, full_ver, src_file):
    dest_dir = os.path.join(
        FG_CACHE, "mcp_repo", "de", "oceanlabs", "mcp",
        f"mcp_{channel}", full_ver,
    )
    os.makedirs(dest_dir, exist_ok=True)
    filename = f"mcp_{channel}-{full_ver}.zip"
    dest = os.path.join(dest_dir, filename)
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        return True
    shutil.copy2(src_file, dest)
    url_src = f"{MAVEN_FORGE}/de/oceanlabs/mcp/mcp_{channel}/{full_ver}/{filename}"
    with open(dest + ".input", "w") as f:
        f.write(url_src)
    with open(dest + ".sha1", "w") as f:
        f.write(sha1_file(src_file))
    return True


def main():
    probe_mirrors()

    ensure_dl_class()
    os.makedirs(TEMP_DIR, exist_ok=True)

    ok = 0
    fail = 0

    print("=" * 60)
    print("Pre-populating ALL caches for ALL mod projects")
    print("=" * 60)

    # ================================================================
    # Phase 1: MCP snapshot mappings → FG mcp_repo (CRITICAL for FG 3/4.1)
    # ================================================================
    print("\n[Phase 1] MCP snapshot mappings → FG mcp_repo")
    for mc, info in sorted(ALL_VERSIONS.items()):
        era_key = info.get("fg_era", "")
        if era_key not in ("fg3", "fg41"):
            continue
        mappings = info.get("mappings", "")
        if not mappings or "_" not in mappings:
            continue
        channel, datever = mappings.split("_", 1)
        full_ver = f"{datever}-{mc}"
        artifact_name = f"mcp_{channel}"
        filename = f"{artifact_name}-{full_ver}.zip"
        rel_path = f"de/oceanlabs/mcp/{artifact_name}/{full_ver}/{filename}"
        print(f"  [{mc}] {artifact_name}/{full_ver}")
        staging = os.path.join(TEMP_DIR, "mcp_staging", artifact_name, full_ver)
        os.makedirs(staging, exist_ok=True)
        tmp_file = os.path.join(staging, filename)
        downloaded = False
        for base_url in get_all_urls("maven_forge"):
            url = f"{base_url.rstrip('/')}/{rel_path}"
            success = download_files([(url, tmp_file)])
            if success and os.path.isfile(tmp_file) and os.path.getsize(tmp_file) > 0:
                downloaded = True
                break
            if os.path.isfile(tmp_file):
                os.remove(tmp_file)
        if downloaded:
            place_in_gradle_cache("de.oceanlabs.mcp", artifact_name, full_ver, filename, tmp_file)
            place_in_fg_mcp_repo(channel, full_ver, tmp_file)
            ok += 1
        else:
            fail += 1

    # ================================================================
    # Phase 2: ForgeGradle plugin jars (ALL eras)
    # ================================================================
    print("\n[Phase 3] ForgeGradle plugin artifacts")
    fg_plugins = [
        ("net.minecraftforge.gradle", "ForgeGradle", "1.2-SNAPSHOT"),
        ("net.minecraftforge.gradle", "ForgeGradle", "2.1-SNAPSHOT"),
        ("net.minecraftforge.gradle", "ForgeGradle", "2.2-SNAPSHOT"),
        ("net.minecraftforge.gradle", "ForgeGradle", "2.3-SNAPSHOT"),
        ("net.minecraftforge.gradle", "ForgeGradle", "4.1.16"),
        ("net.minecraftforge.gradle", "ForgeGradle", "5.1.77"),
        ("net.minecraftforge.gradle", "ForgeGradle", "6.0.53"),
        ("net.minecraftforge.gradle", "ForgeGradle", "7.0.25"),
    ]
    for group, artifact, version in fg_plugins:
        for ext in ("jar", "pom"):
            filename = f"{artifact}-{version}.{ext}"
            rel_path = f"{group.replace('.', '/')}/{artifact}/{version}/{filename}"
            print(f"  FG {version}: {ext}")
            if download_and_cache(rel_path, group, artifact, version, filename, "maven_forge"):
                ok += 1
            else:
                fail += 1

    # ================================================================
    # Phase 4: MCP snapshot mappings (FG 3/4.1) → FG's own mcp_repo
    # ================================================================
    print("\n[Phase 4] MCP snapshot mappings → FG mcp_repo")
    for mc, info in sorted(ALL_VERSIONS.items()):
        era_key = info.get("fg_era", "")
        if era_key not in ("fg3", "fg41"):
            continue
        mappings = info.get("mappings", "")
        if not mappings or "_" not in mappings:
            continue
        channel, datever = mappings.split("_", 1)
        full_ver = f"{datever}-{mc}"
        filename = f"{channel}-{full_ver}.zip"
        rel_path = f"de/oceanlabs/mcp/mcp_{channel}/{full_ver}/{channel}-{full_ver}.zip"
        print(f"  [{mc}] mcp_{channel}/{full_ver}")
        staging = os.path.join(TEMP_DIR, "mcp_staging", f"mcp_{channel}", full_ver)
        os.makedirs(staging, exist_ok=True)
        tmp_file = os.path.join(staging, filename)
        downloaded = False
        for base_url in get_all_urls("maven_forge"):
            url = f"{base_url.rstrip('/')}/{rel_path}"
            success = download_files([(url, tmp_file)])
            if success and os.path.isfile(tmp_file) and os.path.getsize(tmp_file) > 0:
                downloaded = True
                break
            if os.path.isfile(tmp_file):
                os.remove(tmp_file)
        if downloaded:
            place_in_gradle_cache("de.oceanlabs.mcp", f"mcp_{channel}", full_ver, filename, tmp_file)
            place_in_fg_mcp_repo(channel, full_ver, tmp_file)
            ok += 1
        else:
            fail += 1

    # ================================================================
    # Phase 6: Fabric yarn mappings
    # ================================================================
    print("\n[Phase 6] Fabric yarn mappings")
    for mc, info in sorted(ALL_VERSIONS.items()):
        yarn = info.get("fabric_yarn", "")
        if not yarn:
            continue
        for classifier in ("v2", ""):
            filename = f"yarn-{yarn}{'' if not classifier else f'-{classifier}'}.jar"
            rel_path = f"net/fabricmc/yarn/{yarn}/{filename}"
            print(f"  [{mc}] yarn {yarn} ({classifier or 'default'})")
            if download_and_cache(rel_path, "net.fabricmc", "yarn", yarn, filename, "maven_fabric"):
                ok += 1
            else:
                if not classifier:
                    fail += 1

    # ================================================================
    # Phase 7: Fabric Loader jars
    # ================================================================
    print("\n[Phase 7] Fabric Loader jars")
    loader_versions = set()
    for mc, info in sorted(ALL_VERSIONS.items()):
        if "fabric_yarn" not in info:
            continue
        loom_ver = get_fabric_loom(mc)
        loader_ver = _get_fabric_loader(mc)
        loader_versions.add(loader_ver)
    for lv in sorted(loader_versions):
        for ext in ("jar", "pom"):
            filename = f"fabric-loader-{lv}.{ext}"
            rel_path = f"net/fabricmc/fabric-loader/{lv}/{filename}"
            print(f"  loader {lv}: {ext}")
            if download_and_cache(rel_path, "net.fabricmc", "fabric-loader", lv, filename, "maven_fabric"):
                ok += 1
            else:
                fail += 1

    # ================================================================
    # Phase 8: Fabric Loom plugin jars
    # ================================================================
    print("\n[Phase 8] Fabric Loom plugin jars")
    loom_versions = set()
    for mc, info in sorted(ALL_VERSIONS.items()):
        if "fabric_yarn" not in info:
            continue
        loom_versions.add(get_fabric_loom(mc))
    for lv in sorted(loom_versions):
        for ext in ("jar", "pom"):
            filename = f"fabric-loom-{lv}.{ext}"
            rel_path = f"net/fabricmc/fabric-loom/{lv}/{filename}"
            print(f"  loom {lv}: {ext}")
            if download_and_cache(rel_path, "net.fabricmc", "fabric-loom", lv, filename, "maven_fabric"):
                ok += 1
            else:
                fail += 1

    # ================================================================
    # Phase 9: NeoForge artifacts
    # ================================================================
    print("\n[Phase 9] NeoForge artifacts")
    for mc, info in sorted(ALL_VERSIONS.items()):
        nf_ver = info.get("neoforge", "")
        if not nf_ver:
            continue
        mdg = info.get("mdg", "")
        style = info.get("neoforge_style", "mdg")
        if style == "fg6":
            for ext in ("jar", "pom"):
                filename = f"forge-{nf_ver}.{ext}"
                rel_path = f"net/neoforged/forge/{nf_ver}/{filename}"
                print(f"  [{mc}] NF FG6 {filename[:50]}")
                if download_and_cache(rel_path, "net.neoforged", "forge", nf_ver, filename, "maven_forge"):
                    ok += 1
                else:
                    fail += 1
        else:
            bundle_ver = info.get("mdg", "")
            if bundle_ver:
                filename = f"neoforge-moddev-bundle-{bundle_ver}.jar"
                rel_path = f"net/neoforged/moddev/{bundle_ver}/{filename}"
                print(f"  [{mc}] MDG bundle {bundle_ver}")
                if download_and_cache(rel_path, "net.neoforged", "moddev", bundle_ver, filename, "maven_neoforge"):
                    ok += 1
                else:
                    fail += 1
            for ext in ("jar", "pom"):
                filename = f"neoforge-{nf_ver}.{ext}"
                rel_path = f"net/neoforged/neoforge/{nf_ver}/{filename}"
                print(f"  [{mc}] NF {filename[:50]}")
                if download_and_cache(rel_path, "net.neoforged", "neoforge", nf_ver, filename, "maven_neoforge"):
                    ok += 1
                else:
                    fail += 1

    # ================================================================
    # Phase 11: Generate srg_to_snapshot tsrg files (FG 4.1)
    # ================================================================
    print("\n[Phase 11] Generate srg_to_snapshot tsrg files (FG 4.1)")
    import csv
    import zipfile
    import io

    fg41_versions = []
    for mc, info in sorted(ALL_VERSIONS.items()):
        era = info.get("fg_era", "")
        if era != "fg41":
            continue
        if "forge" not in get_loaders(mc):
            continue
        mappings = info.get("mappings", "")
        if not mappings.startswith("snapshot_"):
            continue
        snapshot_id = mappings[len("snapshot_"):]
        snapshot_dir_name = None
        snap_base = os.path.join(
            FG_CACHE, "maven_downloader", "de", "oceanlabs", "mcp", "mcp_snapshot",
        )
        for candidate in (snapshot_id, f"{snapshot_id}-{mc}"):
            if os.path.isfile(os.path.join(snap_base, candidate, f"mcp_snapshot-{candidate}.zip")):
                snapshot_dir_name = candidate
                break
        if not snapshot_dir_name:
            continue
        fg_ver = info["forge"]
        mcp_config_dir = os.path.join(
            FG_CACHE, "maven_downloader", "de", "oceanlabs", "mcp", "mcp_config",
        )
        mcp_config_ver = ""
        if os.path.isdir(mcp_config_dir):
            candidates = []
            for d in os.listdir(mcp_config_dir):
                if d.startswith(mc + "-") or d == mc:
                    zip_path = os.path.join(mcp_config_dir, d, f"mcp_config-{d}.zip")
                    if os.path.isfile(zip_path):
                        candidates.append(d)
            for c in candidates:
                if "-" in c:
                    mcp_config_ver = c
                    break
            if not mcp_config_ver and candidates:
                mcp_config_ver = candidates[0]
        if not mcp_config_ver:
            continue
        fg41_versions.append((mc, fg_ver, snapshot_dir_name, mcp_config_ver))

    tsrg_ok = 0
    for mc, fg_ver, snapshot_dir_name, mcp_config_ver in fg41_versions:
        mcp_config_zip = os.path.join(
            FG_CACHE, "maven_downloader", "de", "oceanlabs", "mcp",
            "mcp_config", mcp_config_ver, f"mcp_config-{mcp_config_ver}.zip",
        )
        snapshot_zip = os.path.join(
            FG_CACHE, "maven_downloader", "de", "oceanlabs", "mcp",
            "mcp_snapshot", snapshot_dir_name, f"mcp_snapshot-{snapshot_dir_name}.zip",
        )
        out_dir = os.path.join(
            FG_CACHE, "minecraft_user_repo", "de", "oceanlabs", "mcp",
            "mcp_config", mcp_config_ver,
        )
        tsrg_name = f"srg_to_{snapshot_dir_name}.tsrg"
        tsrg_path = os.path.join(out_dir, tsrg_name)
        input_path = tsrg_path + ".input"

        if os.path.isfile(tsrg_path) and os.path.isfile(input_path):
            tsrg_ok += 1
            continue

        if not os.path.isfile(mcp_config_zip) or not os.path.isfile(snapshot_zip):
            print(f"  [{mc}] SKIP (missing mcp_config or snapshot zip)")
            continue

        try:
            field_map = {}
            method_map = {}
            with zipfile.ZipFile(snapshot_zip) as sz:
                with sz.open("fields.csv") as f:
                    for row in csv.reader(io.TextIOWrapper(f, "utf-8")):
                        if row and row[0] not in ("searge", ""):
                            field_map[row[0]] = row[1]
                with sz.open("methods.csv") as f:
                    for row in csv.reader(io.TextIOWrapper(f, "utf-8")):
                        if row and row[0] not in ("searge", ""):
                            method_map[row[0]] = row[1]

            lines_out = []
            with zipfile.ZipFile(mcp_config_zip) as cz:
                with cz.open("config/joined.tsrg") as f:
                    for raw in io.TextIOWrapper(f, "utf-8"):
                        line = raw.rstrip("\n\r")
                        if not line:
                            continue
                        if line.startswith("\t"):
                            parts = line.lstrip("\t").split(" ")
                            if len(parts) >= 3:
                                srg_name = parts[2]
                                desc = parts[1]
                                obf = parts[0]
                                mcp_name = method_map.get(srg_name, srg_name)
                                lines_out.append(f"\t{srg_name} {desc} {mcp_name}")
                            elif len(parts) == 2:
                                srg_name = parts[1]
                                mcp_name = field_map.get(srg_name, srg_name)
                                lines_out.append(f"\t{srg_name} {mcp_name}")
                            else:
                                lines_out.append(line)
                        else:
                            parts = line.split(" ")
                            if len(parts) >= 2:
                                srg_class = parts[1]
                                lines_out.append(f"{srg_class} {srg_class}")
                            else:
                                lines_out.append(line)

            os.makedirs(out_dir, exist_ok=True)
            with open(tsrg_path, "w", encoding="utf-8", newline="\n") as f:
                f.write("\n".join(lines_out) + "\n")

            config_sha1 = hashlib.sha1(open(mcp_config_zip, "rb").read()).hexdigest()
            snapshot_sha1 = hashlib.sha1(open(snapshot_zip, "rb").read()).hexdigest()
            with open(input_path, "w", encoding="utf-8", newline="\n") as f:
                f.write(f"mapping={config_sha1}\nmcp={snapshot_sha1}\n")

            tsrg_ok += 1
            print(f"  [{mc}] Generated {tsrg_name}")
        except Exception as e:
            print(f"  [{mc}] FAIL: {e}")

    print(f"  Generated {tsrg_ok}/{len(fg41_versions)} tsrg files")

    # ================================================================
    # Phase 12: Shared dependencies (mcp-common, Java-WebSocket)
    # ================================================================
    print("\n[Phase 11] Shared dependencies")
    shared = [
        ("xyz.langyo.minecraft.mcp", "mcp-common", "0.1.0-SNAPSHOT",
         "mcp-common-0.1.0-SNAPSHOT.jar", "maven-local"),
    ]
    import urllib.request
    for mc, info in sorted(ALL_VERSIONS.items()):
        break

    # ================================================================
    # Summary
    # ================================================================
    print(f"\n{'=' * 60}")
    print(f"Cache population complete: {ok} OK, {fail} FAIL")
    print("=" * 60)

    return 0 if fail == 0 else 1


def _get_fabric_loader(mc):
    _MAP = {
        "1.14.4": "0.11.3", "1.15": "0.11.3", "1.15.2": "0.11.3",
        "1.16.1": "0.12.12", "1.16.3": "0.12.12",
        "1.16.4": "0.12.12", "1.16.5": "0.12.12",
        "1.17.1": "0.14.9", "1.18": "0.14.9", "1.18.2": "0.14.9",
        "1.19": "0.14.21", "1.19.2": "0.14.21",
        "1.19.3": "0.15.6", "1.19.4": "0.15.6",
        "1.20": "0.15.6", "1.20.1": "0.15.6",
        "1.20.2": "0.15.11", "1.20.3": "0.16.0", "1.20.4": "0.16.0",
        "1.20.5": "0.16.0", "1.20.6": "0.16.0",
        "1.21": "0.16.0", "1.21.1": "0.16.0", "1.21.2": "0.16.0",
        "1.21.3": "0.16.0", "1.21.4": "0.16.0", "1.21.5": "0.16.0",
    }
    return _MAP.get(mc, "0.16.0")


if __name__ == "__main__":
    sys.exit(main())

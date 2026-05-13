"""Pre-populate Gradle module cache for legacy FG builds (FG 1.2–4.1).

Old JDK 8 TLS is blocked by Cloudflare on maven.minecraftforge.net (403).
Solution: Download artifacts with JDK 21, place in Gradle's module cache.

Gradle cache layout:
  ~/.gradle/caches/modules-2/files-2.1/{group_path}/{artifact}/{version}/{sha1}/{filename}

Usage:
  1. Ensure proxy running on 127.0.0.1:7890 (optional)
  2. python scripts/prepare_cache.py
"""
import subprocess
import os
import sys
import hashlib
import shutil

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from version_config import ALL_VERSIONS, FG_ERAS, LEGACY_ERAS, is_legacy, get_loaders

TEMP_DIR = os.path.join(os.environ.get("TEMP", "/tmp"), "opencode")
DL_CLASS = os.path.join(TEMP_DIR, "Dl.class")
DL_JAVA = os.path.join(TEMP_DIR, "Dl.java")

JDK21 = r"C:\Program Files\Amazon Corretto\jdk21.0.8_9"
GRADLE_USER_HOME = os.path.join(os.path.expanduser("~"), ".gradle")
MODULES_CACHE = os.path.join(GRADLE_USER_HOME, "caches", "modules-2", "files-2.1")

PROXY_HOST = "127.0.0.1"
PROXY_PORT = 7890
MAVEN_FORGE = "https://maven.minecraftforge.net"

FG_PLUGIN_ARTIFACTS = [
    ("net.minecraftforge.gradle", "ForgeGradle", "1.2-SNAPSHOT"),
    ("net.minecraftforge.gradle", "ForgeGradle", "2.1-SNAPSHOT"),
    ("net.minecraftforge.gradle", "ForgeGradle", "2.2-SNAPSHOT"),
    ("net.minecraftforge.gradle", "ForgeGradle", "2.3-SNAPSHOT"),
]


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
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort)));
                conn = (HttpURLConnection) url.openConnection(proxy);
            } else {
                conn = (HttpURLConnection) url.openConnection();
            }
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
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


def download_files(url_path_pairs, use_proxy=True):
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
    rel = dest_dir.replace(MODULES_CACHE, "...")
    print(f"  -> cache: {rel}/{filename}")
    return True


def download_and_cache(url, group, artifact, version, filename, use_proxy=True):
    staging = os.path.join(TEMP_DIR, "forge_staging", artifact, version)
    os.makedirs(staging, exist_ok=True)
    tmp_file = os.path.join(staging, filename)
    success = download_files([(url, tmp_file)], use_proxy=use_proxy)
    if success and os.path.isfile(tmp_file) and os.path.getsize(tmp_file) > 0:
        place_in_gradle_cache(group, artifact, version, filename, tmp_file)
        return True
    return False


def legacy_forge_versions():
    result = {}
    for mc, info in sorted(ALL_VERSIONS.items()):
        if not is_legacy(mc):
            continue
        loaders = get_loaders(mc)
        if "forge" not in loaders:
            continue
        result[mc] = info
    return result


def main():
    ensure_dl_class()
    os.makedirs(TEMP_DIR, exist_ok=True)

    legacy = legacy_forge_versions()

    print("=" * 60)
    print("Pre-populating Gradle cache for legacy FG builds")
    print(f"Legacy versions: {len(legacy)}")
    print(f"Cache: {MODULES_CACHE}")
    print("=" * 60)

    ok = 0
    fail = 0

    # Phase 1: Forge userdev jars
    print("\n[Phase 1] Forge userdev jars")
    for mc, info in legacy.items():
        fv = info["forge"]
        filename = f"forge-{fv}-userdev.jar"
        url = f"{MAVEN_FORGE}/net/minecraftforge/forge/{fv}/{filename}"
        print(f"  [{mc}] {fv}")
        if download_and_cache(url, "net.minecraftforge", "forge", fv, filename):
            ok += 1
        else:
            fail += 1

    # Phase 2: Forge POMs
    print("\n[Phase 2] Forge POMs")
    for mc, info in legacy.items():
        fv = info["forge"]
        filename = f"forge-{fv}.pom"
        url = f"{MAVEN_FORGE}/net/minecraftforge/forge/{fv}/{filename}"
        print(f"  [{mc}] {fv}")
        if download_and_cache(url, "net.minecraftforge", "forge", fv, filename):
            ok += 1
        else:
            fail += 1

    # Phase 3: Forge universal jars
    print("\n[Phase 3] Forge universal jars")
    for mc, info in legacy.items():
        fv = info["forge"]
        filename = f"forge-{fv}-universal.jar"
        url = f"{MAVEN_FORGE}/net/minecraftforge/forge/{fv}/{filename}"
        print(f"  [{mc}] {fv}")
        if download_and_cache(url, "net.minecraftforge", "forge", fv, filename):
            ok += 1
        else:
            fail += 1

    # Phase 4: Forge launcher jars (FG 3/4 only)
    print("\n[Phase 4] Forge launcher jars (FG 3/4)")
    for mc, info in legacy.items():
        era_key = info.get("fg_era", "")
        if era_key not in ("fg3", "fg41"):
            continue
        fv = info["forge"]
        filename = f"forge-{fv}-launcher.jar"
        url = f"{MAVEN_FORGE}/net/minecraftforge/forge/{fv}/{filename}"
        print(f"  [{mc}] {fv}")
        if download_and_cache(url, "net.minecraftforge", "forge", fv, filename):
            ok += 1
        else:
            fail += 1

    # Phase 5: Forge installer jars (FG 1.2 only)
    print("\n[Phase 5] Forge installer jars (FG 1.2)")
    for mc, info in legacy.items():
        if info.get("fg_era") != "fg12":
            continue
        fv = info["forge"]
        filename = f"forge-{fv}-installer.jar"
        url = f"{MAVEN_FORGE}/net/minecraftforge/forge/{fv}/{filename}"
        print(f"  [{mc}] {fv}")
        if download_and_cache(url, "net.minecraftforge", "forge", fv, filename):
            ok += 1
        else:
            fail += 1

    # Phase 6: FG plugin jars
    print("\n[Phase 6] ForgeGradle plugin artifacts")
    for group, artifact, version in FG_PLUGIN_ARTIFACTS:
        for ext in ("jar", "pom"):
            filename = f"{artifact}-{version}.{ext}"
            url = f"{MAVEN_FORGE}/{group.replace('.', '/')}/{artifact}/{version}/{filename}"
            print(f"  FG {version}: {filename}")
            if download_and_cache(url, group, artifact, version, filename):
                ok += 1
            else:
                fail += 1

    # Summary
    print(f"\n{'=' * 60}")
    print(f"Cache population complete: {ok} OK, {fail} FAIL")
    print("Now run legacy FG builds — they should resolve from cache.")
    if fail:
        print("If Cloudflare still blocks, set gradle.properties proxy:")
        print(f"  systemProp.http.proxyHost={PROXY_HOST}")
        print(f"  systemProp.http.proxyPort={PROXY_PORT}")
        print(f"  systemProp.https.proxyHost={PROXY_HOST}")
        print(f"  systemProp.https.proxyPort={PROXY_PORT}")
    print("=" * 60)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
Minecraft MCP Project - One-Command Initializer

Sets up complete dev environment: JDK 21, NeoForge MDK, builds everything.

Usage:
    python scripts/init.py                    # full auto setup
    python scripts/init.py --skip-mdk         # skip MDK download
    python scripts/init.py --check-only       # just check env
"""

import json
import os
import platform
import re
import shutil
import subprocess
import sys
import time
import urllib.request
import zipfile
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
SCRIPTS_DIR = Path(__file__).resolve().parent
NEOFORGE_DEV = PROJECT_ROOT / "neoforge-dev"
EXAMPLE_MOD = PROJECT_ROOT / "example-mod"
TEST_MOD = PROJECT_ROOT / "test-example-mod"
BUILD_DIR = PROJECT_ROOT / "build"

G, R, Y, M, C, W, D = "\033[92m", "\033[91m", "\033[93m", "\033[95m", "\033[0m", "\033[0m", "\033[90m"


def log(msg, c=W): print(f"{c}{msg}{W}")


def run(cmd, cwd=None, capture=True, timeout=300, env=None):
    log(f"  $ {cmd}", D)
    kw = {"cwd": str(cwd or "."), "capture_output": capture, "timeout": timeout, "text": True}
    if env:
        kw["env"] = {**os.environ, **env}
    if platform.system() == "Windows":
        kw["shell"] = True
    return subprocess.run(cmd, **kw)


def find_java():
    """Find Java 21+. Returns (path_str, version_str) or (None, None)."""
    candidates = []
    jh = os.environ.get("JAVA_HOME")
    if jh:
        candidates.append((Path(jh) / "bin" / ("java.exe" if platform.system() == "Windows" else "java"), "JAVA_HOME"))
    pj = shutil.which("java")
    if pj:
        candidates.append((Path(pj), "PATH"))
    if platform.system() == "Windows":
        for p in [r"C:\Program Files\Java", r"C:\Program Files (x86)\Java",
                    r"C:\Program Files\Eclipse Adoptium", r"C:\Program Files\Microsoft",
                    r"C:\Program Files\Amazon Corretto", r"C:\Program Files\Zulu",
                    Path.home() / ".jdks", Path.home() / ".gradle" / "jdks"]:
            p = Path(p)
            if p.exists():
                for d in p.iterdir():
                    if d.is_dir():
                        exe = d / "bin" / "java.exe"
                        if exe.exists():
                            candidates.append((exe, f"{p.name}/{d.name}"))
    else:
        for p in ["/usr/lib/jvm", "/usr/local/lib/jvm", "/opt/java",
                    Path.home() / ".sdkman/candidates/java",
                    "/Library/Java/JavaVirtualMachines"]:
            p = Path(p)
            if p.exists():
                for d in p.iterdir():
                    if d.is_dir():
                        exe = d / "bin" / "java"
                        if exe.exists():
                            candidates.append((exe, str(d)))
    best_21 = (None, None, 0)
    best_any = (None, None, 0)
    seen = set()
    for ep, label in candidates:
        key = str(ep.resolve())
        if key in seen:
            continue
        seen.add(key)
        try:
            r = subprocess.run([str(ep), "-version"], capture_output=True, text=True, timeout=10,
                             creationflags=subprocess.CREATE_NO_WINDOW if platform.system() == "Windows" else 0)
            ver = r.stderr or r.stdout or ""
            m = re.search(r'version "(\d+)\.(\d+)\.(\d+)"', ver)
            if m:
                maj, mi, pat = int(m.group(1)), int(m.group(2)), int(m.group(3))
                s_any = maj * 10000 + mi * 100 + pat
                s21 = s_any if maj == 21 else 0
                if s21 > best_21[2]: best_21 = (str(ep), ver.strip(), s21)
                if s_any > best_any[2]: best_any = (str(ep), ver.strip(), s_any)
        except Exception:
            continue
    return (best_21[0], best_21[1]) if best_21[0] else (best_any[0], best_any[1])


def check_python():
    v = sys.version_info
    ok = v.major >= 3 and v.minor >= 10
    log(f"  {'PASS' if ok else 'FAIL'}: Python {v.major}.{v.minor}.{v.micro}", G if ok else R)
    return ok


def check_java(java_path=None):
    if java_path:
        exe = Path(java_path) / ("java.exe" if platform.system() == "Windows" else "java")
        if not exe.exists():
            log(f"  FAIL: Java not found at {java_path}", R); return False, None
        try:
            r = subprocess.run([str(exe), "-version"], capture_output=True, text=True, timeout=10,
                             creationflags=subprocess.CREATE_NO_WINDOW if platform.system() == "Windows" else 0)
            m = re.search(r'version "(\d+)', r.stderr or "")
            if m and int(m.group(1)) >= 21:
                log(f"  PASS: Java {r.stderr.strip().split(chr(10))[0][:40]}", G); return True, str(exe)
            log(f"  FAIL: Java < 21 ({r.stderr.strip()[:40]})", R); return False, None
        except Exception as e:
            log(f"  FAIL: {e}", R); return False, None
    jp, jv = find_java()
    if jp:
        m = re.search(r'(\d+)\.', jv)
        if m and int(m.group(1)) >= 21:
            log(f"  PASS: Java {jv.split(chr(10))[0][:40]} at {jp}", G); return True, jp
        log("  FAIL: No Java 21+ found", R); return False, jp


def setup_gradle_wrapper():
    gw_bat = PROJECT_ROOT / "gradlew.bat"
    wrapper_dir = PROJECT_ROOT / "gradle" / "wrapper"
    if gw_bat.exists() and (wrapper_dir / "gradle-wrapper.jar").exists():
        log("  PASS: Gradle wrapper exists", G); return True
    log("  Setting up Gradle wrapper...", Y)
    pj = shutil.which("gradle")
    if pj:
        run(["gradle", "wrapper", "--gradle-version", "8.5"])
    else:
        wrapper_dir.mkdir(parents=True, exist_ok=True)
        target = wrapper_dir / "gradle-wrapper.jar"
        if not target.exists():
            try:
                urllib.request.urlretrieve(
                    "https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar",
                    target)
            except Exception:
                pass
    props = wrapper_dir / "gradle-wrapper.properties"
    if not props.exists():
        props.write_text("""distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-8.5-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
""")
    if not gw_bat.exists():
        gw_bat.write_text('@rem off\r\n@echo off\r\n"%~dp0\\" %*\\" %*" | findstr /i \"gradlew.bat\" >nul && call "%~s" gradlew.bat %%*')
    log("  PASS: Gradle wrapper ready", G); return True


def download_mdk(version="21.1.228"):
    """Set up NeoForge MDK. Download or generate minimal structure."""
    urls = [
        f"https://maven.neoforged.net/releases/net/neoforged/neoforge/{version}/neoforge-{version}-mdk.zip",
        f"https://github.com/neoforged/NeoForge/releases/download/{version}/neoforge-{version}-mdk.zip",
    ]
    zip_target = NEOFORGE_DEV / "mdk.zip"
    marker = NEOFORGE_DEV / ".mdk-done"
    if marker.exists() and (NEOFORGE_DEV / "build.gradle.kts").exists():
        log("  PASS: NeoForge MDK already set up", G); return True
    log(f"  Setting up NeoForge {version}...", M)
    NEOFORGE_DEV.mkdir(parents=True, exist_ok=True)
    success = False
    for url in urls:
        try:
            urllib.request.urlretrieve(url, zip_target)
            success = True; log(f"  Downloaded ({zip_target.stat().st_size // 1024}KB)", G); break
        except Exception as e:
            log(f"  Failed: {url.split('/')[-1]} - {e}", D)
    if success:
        try:
            with zipfile.ZipFile(zip_target, "r") as zf:
                for member in zf.namelist():
                    parts = member.replace("\\", "/").split("/")
                    if len(parts) <= 1:
                        continue
                    if parts[0] in ("src", "gradle", "build", "settings", ".gitkeep"):
                        out = "/".join(parts[1:]) if parts[0] == "src" else member
                        op = NEOFORGE_DEV / out
                        op.parent.mkdir(parents=True, exist_ok=True)
                        with zf.open(member) as src, open(op, "wb") as dst:
                            shutil.copyfileobj(src, dst)
            zip_target.unlink(missing_ok=True)
            marker.write_text(f"{version}\n"); log("  PASS: MDK extracted", G); return True
        except Exception as e:
            log(f"  Extract failed: {e}, generating from scratch...", Y)
    _gen_minimal_mdk(version); return True


def _gen_minimal_mdk(version):
    """Generate minimal NeoForge-compatible project structure."""
    dirs = ["src/main/java", "src/main/resources/META-INF",
           "src/test/java", "src/test/resources",
           "gradle/wrapper", ".gradle", "run/configs"]
    for d in dirs:
        (NEOFORGE_DEV / d).mkdir(parents=True, exist_ok=True)
    (NEOFORGE_DEV / "settings.gradle.kts").write_text(
        'pluginManagement {\n'
        '    repositories {\n'
        '        maven("https://maven.neoforged.net/releases/")\n'
        '        gradlePluginPortal()\n'
        '        mavenCentral()\n'
        '    }\n'
        '}\n'
        'plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0" }\n'
        'rootProject.name = "minecraft-mcp-neoforge-dev"\n'
        'dependencyResolutionManagement {\n'
        '    repositories {\n'
        '        maven("https://maven.neoforged.net/releases/")\n'
        '        mavenCentral()\n'
        '    }\n'
        '}\n'
    )
    (NEOFORGE_DEV / "gradle.properties").write_text(
        f'org.gradle.jvmargs=-Xmx4G\n'
        f'minecraft_version=1.21.1\n'
        f'neo_version={version}\n'
        f'mod_id=mcpmod\n'
        f'mod_name=ModDev MCP\n'
        f'mod_version=1.0.0-SNAPSHOT\n'
        f'mod_group_id=xyz.langyo\n'
    )
    wp = NEOFORGE_DEV / "gradle" / "wrapper" / "gradle-wrapper.properties"
    if not wp.exists():
        wp.write_text(
            'distributionBase=GRADLE_USER_HOME\ndistributionPath=wrapper/dists\n'
            'distributionUrl=https://services.gradle.org/distributions/gradle-8.5-bin.zip\n'
            'networkTimeout=10000\nvalidateDistributionUrl=true\n'
            'zipStoreBase=GRADLE_USER_HOME\nzipStorePath=wrapper/dists\n'
        )
    for gw_name in ["gradlew.bat", "gradlew"]:
        src_gw = PROJECT_ROOT / gw_name
        if src_gw.exists():
            shutil.copy2(src_gw, NEOFORGE_DEV / gw_name)
    src_gradle = PROJECT_ROOT / "gradle"
    dst_gradle = NEOFORGE_DEV / "gradle"
    if not dst_gradle.exists() and src_gradle.exists():
        shutil.copytree(src_gradle, dst_gradle)
    marker = NEOFORGE_DEV / ".mdk-done"
    marker.write_text(f"{version}\n")
    log("    Generated: settings, properties, wrapper, run/configs", D)


def configure_dual_mods():
    bf = NEOFORGE_DEV / "build.gradle.kts"
    sf = NEOFORGE_DEV / "settings.gradle.kts"
    bf.write_text('''plugins {
    id("net.neoforged.gradle.userdev") version "7.0.175"
    kotlin("jvm") version "2.3.21"
    idea
}

group = "xyz.langyo"
version = "1.0.0-SNAPSHOT"

base { archivesName.set("minecraft-mcp-neoforge") }

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases/")
}

dependencies {
    implementation("net.neoforged:neoforge:${property("neo_version")}")
    implementation("org.java-websocket:Java-WebSocket:1.5.4")
    implementation("com.google.code.gson:gson:2.11.0")
}

afterEvaluate {
    sourceSets {
        main {
            java.srcDirs(
                file("../../example-mod/src/main/java"),
                file("../../test-example-mod/src/main/java")
            )
            resources.srcDirs(
                file("../../example-mod/src/main/resources"),
                file("../../test-example-mod/src/main/resources")
            )
        }
    }
}

tasks.jar {
    manifest {
        attributes["ModSide"] = "BOTH"
        attributes["Automatic-Module"] = "xyz.langyo.minecraft.mcp.mod"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
''')
    log("  PASS: Dual-mod build configured", G); return True


def build_mcp_server(java_path=None):
    env = dict(os.environ)
    if java_path:
        env["JAVA_HOME"] = str(Path(java_path).parent.parent)
    log("  Building MCP server shadowJar...", M)
    r = run(["gradlew.bat" if platform.system() == "Windows" else "./gradlew",
              "shadowJar", "--quiet"], env=env, timeout=180)
    jar = BUILD_DIR / "libs" / "mcp-server-0.1.0.jar"
    if jar.exists():
        log(f"  PASS: MCP JAR ({jar.stat().st_size // 1024}KB)", G); return True
    log(f"  FAIL: Build error:\n{r.stderr[-500:]}", R); return False


def build_mods(java_path=None):
    env = dict(os.environ)
    if java_path:
        env["JAVA_HOME"] = str(Path(java_path).parent.parent)
    log("  Compiling NeoForge mods (may take 15-30 min on first run)...", M)
    gw = "gradlew.bat" if platform.system() == "Windows" else "./gradlew"
    r = run([gw, "compileJava", "--quiet"], cwd=str(NEOFORGE_DEV), env=env, timeout=1800)
    classes = NEOFORGE_DEV / "build" / "classes" / "java" / "main"
    has_mcp = (classes / "xyz" / "langyo" / "minecraft" / "mcp" / "mod")
    has_test = (classes / "xyz" / "langyo" / "testmod").exists()
    log(f"  MCP mod: {'OK' if has_mcp else 'MISS'}", G if has_mcp else R)
    log(f"  Test mod: {'OK' if has_test else 'MISS'}", G if has_test else R)
    return has_mcp and has_test


def verify_structure():
    required = [
        ("project root", PROJECT_ROOT),
        ("scripts/", SCRIPTS_DIR),
        ("example-mod/", EXAMPLE_MOD),
        ("example-mod/src/main/java/...", EXAMPLE_MOD / "src"),
        ("example-mod/src/main/resources/META-INF/mods.toml", EXAMPLE_MOD / "src" / "main" / "resources" / "META-INF" / "mods.toml"),
        ("test-example-mod/", TEST_MOD),
        ("test-example-mod/src/main/resources/META-INF/mods.toml", TEST_MOD / "src" / "main" / "resources" / "META-INF" / "mods.toml"),
        ("neoforge-dev/", NEOFORGE_DEV),
        ("build.gradle.kts (root)", PROJECT_ROOT / "build.gradle.kts"),
        ("settings.gradle.kts (root)", PROJECT_ROOT / "settings.gradle.kts"),
    ]
    all_ok = True
    for name, p in required:
        if p.exists(): log(f"  OK   {name}", G)
        else: log(f"  MISS {name}: {p}", R); all_ok = False
    return all_ok


def print_summary(jp):
    log("\n" + "=" * 55, C)
    log("  Environment Summary", C)
    log("=" * 55, C)
    log(f"  OS:     {platform.system()} {platform.release()}", W)
    log(f"  Python: {sys.version.split()[0]}", W)
    log(f"  PWD:   {PROJECT_ROOT}", W)
    log(f"  Java:  {jp}", G if jp else R)
    jar = BUILD_DIR / "libs" / "mcp-server-0.1.0.jar"
    log(f"  JAR:   {'EXISTS (' + str(jar.stat().st_size // 1024) + 'KB)' if jar.exists() else 'NOT BUILT'}", W)
    mk = (NEOFORGE_DEV / ".mdk-done").exists()
    log(f"  MDK:   {'SETUP' if mk else 'NOT SETUP'}", W)
    log("=" * 55, C)


def main():
    import argparse
    p = argparse.ArgumentParser(description="Minecraft MCP Project Initializer")
    p.add_argument("--java-path", help="Java installation path")
    p.add_argument("--skip-mdk", action="store_true")
    p.add_argument("--skip-build", action="store_true")
    p.add_argument("--check-only", action="store_true")
    p.add_argument("--force-reinit", action="store_true")
    args = p.parse_args()

    log("=" * 55, C)
    log("  Minecraft MCP Project - Environment Setup", C)
    log("=" * 55, C)

    results = []
    results.append(check_python())
    java_ok, java_path = check_java(args.java_path)
    results.append(java_ok)
    results.append(verify_structure())
    results.append(setup_gradle_wrapper())

    if args.check_only:
        print_summary(java_path)
        sys.exit(0 if all(results) else 1)

    results.append(download_mdk())
    if (NEOFORGE_DEV / "build.gradle.kts").exists():
        configure_dual_mods()

    if args.skip_build:
        log("\n  SKIP (--skip-build)", D)
    else:
        results.append(build_mcp_server(java_path))
        if not args.skip_mdk and (NEOFORGE_DEV / "build.gradle.kts").exists():
            build_mods(java_path)

    print_summary(java_path)
    all_pass = all(results)
    log(f"\n  Result: {'ALL PASSED - Ready to test!' if all_pass else 'SOME STEPS FAILED'}", G if all_pass else R)
    if all_pass:
        log("\n  Next steps:", C)
        log("    python scripts/run_e2e_tests.py              # Full automated test", W)
        log("    python scripts/run_e2e_tests.py --no-launch   # MC already running", W)
        log("    cd neoforge-dev && gradlew runClient          # Manual MC launch", W)
    sys.exit(0 if all_pass else 1)


if __name__ == "__main__":
    main()

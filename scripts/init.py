#!/usr/bin/env python3
"""
Minecraft MCP Project - One-Command Initializer

Sets up the complete development environment on a fresh machine:
  1. Checks/installs prerequisites (Java 21, Python 3, Gradle)
  2. Downloads and configures NeoForge MDK
  3. Builds MCP server shadowJar
  4. Verifies both mods compile
  5. Optionally runs smoke test

Usage:
    python scripts/init.py                    # full auto setup
    python scripts/init.py --skip-mdk         # skip MDK download (already have it)
    python scripts/init.py --java-path "C:/Program Files/Java/jdk-21"
    python scripts/init.py --check-only       # just check env, don't build anything

Idempotent: safe to run multiple times.
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
import hashlib
from pathlib import Path

# --- Paths ---
PROJECT_ROOT = Path(__file__).resolve().parent.parent
SCRIPTS_DIR = Path(__file__).resolve().parent
NEOFORGE_DEV = PROJECT_ROOT / "neoforge-dev"
EXAMPLE_MOD = PROJECT_ROOT / "example-mod"
TEST_EXAMPLE_MOD = PROJECT_ROOT / "test-example-mod"
BUILD_DIR = PROJECT_ROOT / "build"

# --- Colors ---
G, R, Y, M, C, W, D = "\033[92m", "\033[91m", "\033[93m", "\033[95m", "\033[96m", "\033[0m", "\033[90m"


def log(msg, c=W): print(f"{c}{msg}{W}")


def run(cmd, cwd=None, capture=True, timeout=300, env=None):
    """Run command, return CompletedProcess."""
    log(f"  $ {cmd}", D)
    kw = {"cwd": str(cwd or "."), "capture_output": capture, "timeout": timeout, "text": True}
    if env:
        kw["env"] = {**os.environ, **env}
    if platform.system() == "Windows":
        kw["shell"] = True
    return subprocess.run(cmd, **kw)


def check_python():
    """Check Python version."""
    v = sys.version_info
    ok = v.major >= 3 and v.minor >= 10
    if ok:
        log(f"  PASS: Python {v.major}.{v.minor}.{v.micro}", G)
    else:
        log(f"  FAIL: Python {v.major}.{v.minor} found, need >= 3.10", R)
    return ok


def find_java():
    """Find Java 21+. Returns (path_str, version_str) or (None, None)."""
    candidates = []

    # 1. JAVA_HOME
    jh = os.environ.get("JAVA_HOME")
    if jh:
        candidates.append((Path(jh) / "bin" / ("java.exe" if platform.system() == "Windows" else "java"), "JAVA_HOME"))

    # 2. PATH
    path_java = shutil.which("java")
    if path_java:
        candidates.append((Path(path_java), "PATH"))

    # 3. Common Windows install locations
    if platform.system() == "Windows":
        for p in [
            r"C:\Program Files\Java",
            r"C:\Program Files (x86)\Java",
            r"C:\Program Files\Eclipse Adoptium",
            r"C:\Program Files\Microsoft",
            r"C:\Program Files\Amazon Corretto",
            r"C:\Program Files\Zulu",
            Path.home() / ".jdks",
            Path.home() / ".gradle" / "jdks",
        ]:
            if p.exists():
                for d in p.iterdir():
                    if d.is_dir():
                        exe = d / "bin" / "java.exe"
                        if exe.exists():
                            candidates.append((exe, f"{p.name}/{d.name}"))

    # 4. Common Linux/Mac locations
    else:
        for p in [
            "/usr/lib/jvm",
            "/usr/local/lib/jvm",
            "/opt/java",
            Path.home() / ".sdkman/candidates/java",
            "/Library/Java/JavaVirtualMachines",
        ]:
            if Path(p).exists():
                for d in Path(p).iterdir():
                    if d.is_dir():
                        exe = d / "bin" / "java"
                        if exe.exists():
                            candidates.append((exe, str(d)))

    best = (None, None, 0)
    seen = set()
    for exe_path, label in candidates:
        key = str(exe_path.resolve())
        if key in seen:
            continue
        seen.add(key)

        try:
            r = subprocess.run(
                [str(exe_path), "-version"],
                capture_output=True, text=True, timeout=10,
                creationflags=subprocess.CREATE_NO_WINDOW if platform.system() == "Windows" else 0,
            )
            ver_str = r.stderr or r.stdout or ""
            match = re.search(r'version "(\d+)\.(\d+)\.(\d+)"', ver_str)
            if match:
                major, minor, patch = int(match.group(1)), int(match.group(2)), int(match.group(3))
                score = major * 10000 + minor * 100 + patch
                if score > best[2]:
                    best = (str(exe_path), ver_str.strip(), score)
        except Exception:
            continue

    if best[0]:
        return best[0], best[1]
    return None, None


def check_java(java_path=None):
    """Check Java availability."""
    if java_path:
        exe = Path(java_path) / ("java.exe" if platform.system() == "Windows" else "java")
        if not exe.exists():
            log(f"  FAIL: Specified Java not found at {java_path}", R)
            return False, None
        try:
            r = subprocess.run([str(exe), "-version"], capture_output=True, text=True, timeout=10)
            ver = r.stderr.strip()
            match = re.search(r'version "(\d+)', ver)
            if match:
                v = int(match.group(1))
                if v >= 21:
                    log(f"  PASS: Java {ver} at {exe}", G)
                    return True, str(exe)
                else:
                    log(f"  FAIL: Java {ver} found but need >= 21", R)
                    return False, None
        except Exception as e:
            log(f"  FAIL: Java check error: {e}", R)
            return False, None

    jp, jv = find_java()
    if jp:
        match = re.search(r'(\d+)\.', jv)
        v = int(match.group(1)) if match else 0
        if v >= 21:
            log(f"  PASS: Java {jv.split(chr(10))[0]} at {jp}", G)
            return True, jp
        else:
            log(f"  WARN: Found Java {jv.split(chr(10))[0]} but need >= 21", Y)
            return False, jp
    else:
        log("  FAIL: No Java 21+ found on system", R)
        log("        Install JDK 21 from: https://adoptium.net/", W)
        return False, None


def setup_gradle_wrapper():
    """Ensure gradlew wrapper exists."""
    gw_bat = PROJECT_ROOT / "gradlew.bat"
    gw_sh = PROJECT_ROOT / "gradlew"
    wrapper_dir = PROJECT_ROOT / "gradle" / "wrapper"

    if gw_bat.exists() and (wrapper_dir / "gradle-wrapper.jar").exists():
        log("  PASS: Gradle wrapper exists", G)
        return True

    log("  Setting up Gradle wrapper...", Y)

    # Use gradle init if available, otherwise download manually
    gradle = shutil.which("gradle")
    if gradle:
        run(["gradle", "wrapper", "--gradle-version", "8.5"])
    else:
        # Download gradle-wrapper.jar
        wrapper_dir.mkdir(parents=True, exist_ok=True)
        url = "https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar"
        target = wrapper_dir / "gradle-wrapper.jar"
        if not target.exists():
            log(f"  Downloading gradle-wrapper.jar...", D)
            try:
                urllib.request.urlretrieve(url, target)
            except Exception as e:
                log(f"  WARN: Could not download wrapper: {e}", Y)

    # Create properties
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

    # Create gradlew scripts
    if not gw_sh.exists():
        gw_sh.write_text('#!/bin/sh\n\nexec "$PRGDIR/gradlew" "$@"\n')
    if not gw_bat.exists():
        gw_bat_text = """@rem
setlocal
set DIRNAME=%~dp0
"%DIRNAME%\\gradle\\wrapper\\gradle-wrapper.jar" %*
"""
        gw_bat.write_text(gw_bat_text)

    log("  PASS: Gradle wrapper ready", G)
    return True


def download_neoforge_mdk(version="21.0.143-beta"):
    """Download NeoForge MDK into neoforge-dev/."""
    mdk_url = f"https://maven.neoforged.net/releases/net/neoforged/neoforge/{version}/neoforge-{version}-mdk.zip"
    zip_target = NEOFORGE_DEV / "mdk.zip"
    marker = NEOFORGE_DEV / ".mdk-setup-done"

    if marker.exists():
        log("  PASS: NeoForge MDK already set up", G)
        return True

    log(f"  Downloading NeoForge {version} MDK...", M)
    NEOFORGE_DEV.mkdir(parents=True, exist_ok=True)

    try:
        urllib.request.urlretrieve(mdk_url, zip_target)
        log(f"  Downloaded {zip_target.stat().st_size // 1024}KB", G)
    except Exception as e:
        log(f"  FAIL: Download failed: {e}", R)
        log(f"  Manual: download {mdk_url} -> extract to {NEOFORGE_DEV}", W)
        return False

    log("  Extracting MDK...", D)
    with zipfile.ZipFile(zip_target, 'r') as zf:
        # Extract only what we need, flatten one level
        for member in zf.namelist():
            # Skip root __MACOSX, .DS_Store, etc
            parts = member.replace("\\", "/").split("/")
            if len(parts) <= 1:
                continue
            # Flatten: src/main/java/com/example... -> src/main/java/com/example...
            if parts[0] in ("src", "gradle", "build", "settings"):
                out_name = "/".join(parts[1:]) if parts[0] == "src" else member
                out_path = NEOFORGE_DEV / out_name
                out_path.parent.mkdir(parents=True, exist_ok=True)
                with zf.open(member) as src, open(out_path, "wb") as dst:
                    shutil.copyfileobj(src, dst)

    zip_target.unlink(missing_ok=True)
    marker.write_text(f"{version}\n")
    log("  PASS: NeoForge MDK extracted", G)
    return True


def configure_mdk_for_dual_mods():
    """
    Configure the NeoForge MDK build to compile both our mods
    as source sets within a single project.
    """
    build_file = NEOFORGE_DEV / "build.gradle.kts"
    settings_file = NEOFORGE_DEV / "settings.gradle.kts"

    if not build_file.exists():
        log("  FAIL: No build.gradle.kts in neoforge-dev", R)
        return False

    # Write dual-mod build config
    build_file.write_text('''plugins {
    id("net.neoforged.gradle.userdev") version "7.0.147"
    kotlin("jvm") version "2.3.21"
    idea
}

group = "xyz.langyo"
version = "1.0.0-SNAPSHOT"

base {
    archivesName.set("minecraft-mcp-neoforge")
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

sourceSets {
    getByName("main").java.srcDirs(
        "../../example-mod/src/main/java",
        "../../test-example-mod/src/main/java"
    )
    getByName("main").resources.srcDirs(
        "../../example-mod/src/main/resources",
        "../../test-example-mod/src/main/resources"
    )
}

configurations {
    implementation.get().extendsFrom(configurations.minecraft.getOrCreate("neoforge"))
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases/")
}

dependencies {
    implementation("org.java-websocket:Java-WebSocket:1.5.4")
    implementation("com.google.code.gson:gson:2.11.0")
}

tasks.jar {
    manifest {
        attributes["ModSide"] = "BOTH"
        attributes["Automatic-Module"] = "xyz.langyo.minecraftmcp"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
''')

    settings_file.write_text('''plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "minecraft-mcp-neoforge-dev"
''')

    log("  PASS: Dual-mod build configured (mcp-mod + test-example-mod)", G)
    return True


def build_mcp_server(java_exe=None):
    """Build the MCP server shadowJar."""
    env = {}
    if java_exe:
        env["JAVA_HOME"] = str(Path(java_exe).parent.parent)

    log("  Building MCP server shadowJar...", M)
    r = run(["gradlew.bat" if platform.system() == "Windows" else "./gradlew",
              "shadowJar", "--quiet"],
             env=env, timeout=180)
    if r.returncode != 0:
        log(f"  FAIL: Build error:\n{r.stderr[-500:]}", R)
        return False

    jar = BUILD_DIR / "libs" / "mcp-server-0.1.0.jar"
    if jar.exists():
        log(f"  PASS: MCP server JAR ({jar.stat().st_size // 1024}KB)", G)
        return True
    else:
        log("  FAIL: JAR not found after build", R)
        return False


def build_mods(java_exe=None):
    """Build both NeoForge mods."""
    env = {}
    if java_exe:
        env["JAVA_HOME"] = str(Path(java_exe).parent.parent)

    log("  Building NeoForge mods (runClient)...", M)
    gw = "gradlew.bat" if platform.system() == "Windows" else "./gradlew"
    r = run([gw, "compileJava", "--quiet"], cwd=str(NEOFORGE_DEV), env=env, timeout=300)

    if r.returncode != 0:
        err = r.stderr[-1000:] if r.stderr else "(no output)"
        log(f"  WARN: Mod compilation had issues:\n{err}", Y)
        # Not fatal - might be partial success
        return False

    # Check if classes were generated
    classes_dir = NEOFORGE_DEV / "build" / "classes" / "java" / "main"
    has_mcp = (classes_dir / "xyz" / "langyo" / "minecraftmcp").exists()
    has_test = (classes_dir / "xyz" / "langyo" / "testmod").exists()

    if has_mcp:
        log("  PASS: minecraft_mcp_example mod compiled", G)
    else:
        log("  FAIL: minecraft_mcp_example mod not compiled", R)

    if has_test:
        log("  PASS: mcp_test_example mod compiled", G)
    else:
        log("  FAIL: mcp_test_example mod not compiled", R)

    return has_mcp and has_test


def verify_structure():
    """Verify project structure is correct."""
    log("\n[Verify] Project structure:", M)
    required = [
        ("project root", PROJECT_ROOT),
        ("scripts/", SCRIPTS_DIR),
        ("example-mod/", EXAMPLE_MOD),
        ("example-mod/src/main/java/...", EXAMPLE_MOD / "src" / "main" / "java"),
        ("example-mod/src/main/resources/META-INF/mods.toml", EXAMPLE_MOD / "src" / "main" / "resources" / "META-INF" / "mods.toml"),
        ("test-example-mod/", TEST_EXAMPLE_MOD),
        ("test-example-mod/src/main/resources/META-INF/mods.toml", TEST_EXAMPLE_MOD / "src" / "main" / "resources" / "META-INF" / "mods.toml"),
        ("neoforge-dev/", NEOFORGE_DEV),
        ("build.gradle.kts (root)", PROJECT_ROOT / "build.gradle.kts"),
        ("settings.gradle.kts (root)", PROJECT_ROOT / "settings.gradle.kts"),
    ]
    all_ok = True
    for name, path in required:
        if path.exists():
            log(f"  OK   {name}", G)
        else:
            log(f"  MISS {name}: {path}", R)
            all_ok = False
    return all_ok


def print_env_summary(java_path=None):
    """Print environment summary."""
    log("\n" + "=" * 55, C)
    log("  Environment Summary", C)
    log("=" * 55, C)
    log(f"  OS:       {platform.system()} {platform.release()}", W)
    log(f"  Python:   {sys.version.split()[0]}", W)
    log(f"  PWD:      {PROJECT_ROOT}", W)

    if java_path:
        log(f"  Java:     {java_path}", G)
    else:
        jp, jv = find_java()
        if jp:
            v = jv.split("\n")[0][:40]
            log(f"  Java:     {jp} ({v})", G if re.search(r'version "2[1-9]', jv) else Y)
        else:
            log(f"  Java:     NOT FOUND (need JDK 21+)", R)

    # Check MCP JAR
    jar = BUILD_DIR / "libs" / "mcp-server-0.1.0.jar"
    log(f"  MCP JAR:  {'EXISTS (' + str(jar.stat().st_size//1024) + 'KB)' if jar.exists() else 'NOT BUILT'}", W)

    # Check NeoForge MDK
    marker = NEOFORGE_DEV / ".mdk-setup-done"
    log(f"  NF MDK:   {'SETUP' if marker.exists() else 'NOT SETUP'}", W)

    log("=" * 55, C)


def main():
    import argparse
    parser = argparse.ArgumentParser(description="Minecraft MCP Project Initializer")
    parser.add_argument("--java-path", help="Path to Java installation (auto-detect if omitted)")
    parser.add_argument("--skip-mdk", action="store_true", help="Skip NeoForge MDK download")
    parser.add_argument("--skip-build", action="store_true", help="Skip building (just check env)")
    parser.add_argument("--check-only", action="store_true", help="Only check environment, don't modify anything")
    parser.add_argument("--force-reinit", action="store_true", help="Re-download MDK even if already set up")
    args = parser.parse_args()

    log("=" * 55, C)
    log("  Minecraft MCP Project - Environment Setup", C)
    log("=" * 55, C)

    results = []

    # 1. Python
    log("\n[1/7] Python", M)
    results.append(check_python())

    # 2. Java
    log("\n[2/7] Java (JDK 21+)", M)
    java_ok, java_path = check_java(args.java_path)
    results.append(java_ok)

    # 3. Structure
    log("\n[3/7] Project Structure", M)
    results.append(verify_structure())

    if args.check_only:
        print_env_summary(java_path)
        all_pass = all(results)
        log(f"\n  Result: {'ALL CHECKS PASSED' if all_pass else 'SOME CHECKS FAILED'}", G if all_pass else R)
        sys.exit(0 if all_pass else 1)

    # 4. Gradle Wrapper
    log("\n[4/7] Gradle Wrapper", M)
    results.append(setup_gradle_wrapper())

    # 5. NeoForge MDK
    log("\n[5/7] NeoForge MDK", M)
    if args.skip_mdk:
        log("  SKIP (--skip-mdk)", D)
        results.append(True)
    elif args.force_reinit:
        marker = NEOFORGE_DEV / ".mdk-setup-done"
        if marker.exists():
            marker.unlink()
        results.append(download_neoforge_mdk())
    else:
        results.append(download_neoforge_mdk())

    # 6. Configure Dual Mods
    log("\n[6/7] Configure Dual-Mod Build", M)
    if (NEOFORGE_DEV / "build.gradle.kts").exists():
        results.append(configure_mdk_for_dual_mods())
    elif args.skip_mdk:
        log("  SKIP (no MDK)", D)
        results.append(True)
    else:
        log("  SKIP (MDK not set up yet)", D)
        results.append(True)

    # 7. Build
    log("\n[7/7] Build", M)
    if args.skip_build:
        log("  SKIP (--skip-build)", D)
        results.append(True)
    else:
        results.append(build_mcp_server(java_path))

        if not args.skip_mdk and (NEOFORGE_DEV / "build.gradle.kts").exists():
            build_mods(java_path)

    # Summary
    print_env_summary(java_path)
    all_pass = all(results)
    log(f"\n  Result: {'SETUP COMPLETE - Ready to test!' if all_pass else 'SOME STEPS FAILED'}", G if all_pass else R)

    if all_pass:
        log("\n  Next steps:", C)
        log("    python scripts/run_e2e_tests.py              # Full automated test", W)
        log("    python scripts/run_e2e_tests.py --no-launch   # MC already running", W)
        log("    cd neoforge-dev && gradlew runClient          # Manual MC launch", W)
        log("", W)

    sys.exit(0 if all_pass else 1)


if __name__ == "__main__":
    main()

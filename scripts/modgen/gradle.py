"""Gradle wrapper provisioning and per-loader Gradle version pins."""

import os
import shutil

from version_config import BASE_DIR, FG_ERAS, get_fabric_loom, get_neoforge_gradle

WRAPPER_JAR = os.path.join(BASE_DIR, "gradle", "wrapper", "gradle-wrapper.jar")


def maven_local_depth():
    return "../../../"


def _gradle_ver_for_forge(era_key):
    return FG_ERAS[era_key]["gradle"]


def _gradle_ver_for_fabric(mc):
    loom = get_fabric_loom(mc)
    if loom.startswith("0."):
        return "7.6.4"
    if loom.startswith("1.14"):
        return "9.3.0"
    # loom >= 1.15 needs modern Gradle; 1.17 (MC 26.x unobfuscated era)
    # follows the official example (9.5.1) — reuse the fg7 pin 9.6.1.
    if loom.startswith(("1.15", "1.16", "1.17")):
        return "9.6.1"
    return "8.10"


def _gradle_ver_for_neoforge(mc):
    _, gradle = get_neoforge_gradle(mc)
    return gradle if gradle else "8.10"


def copy_wrapper(path, gradle_ver):
    gw = os.path.join(path, "gradle", "wrapper")
    os.makedirs(gw, exist_ok=True)
    dst = os.path.join(gw, "gradle-wrapper.jar")
    try:
        shutil.copy2(WRAPPER_JAR, dst)
    except (OSError, PermissionError):
        try:
            os.remove(dst)
        except OSError:
            pass
        shutil.copy2(WRAPPER_JAR, dst)
    with open(os.path.join(gw, "gradle-wrapper.properties"), "w") as f:
        f.write("distributionBase=GRADLE_USER_HOME\n")
        f.write("distributionPath=wrapper/dists\n")
        f.write(
            f"distributionUrl=https\\://services.gradle.org/distributions/gradle-{gradle_ver}-bin.zip\n"
        )
        f.write("networkTimeout=10000\n")
        f.write("validateDistributionUrl=true\n")
        f.write("zipStoreBase=GRADLE_USER_HOME\n")
        f.write("zipStorePath=wrapper/dists\n")
    for name in ("gradlew", "gradlew.bat"):
        src = os.path.join(BASE_DIR, "packages", "mods", "1.8.9", "forge", name)
        if os.path.isfile(src):
            dst_gw = os.path.join(path, name)
            if not os.path.isfile(dst_gw):
                shutil.copy2(src, dst_gw)

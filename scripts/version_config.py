"""Single source of truth for ALL version/dependency mappings.

Every script imports from this module. No version strings hardcoded elsewhere.

FG Era Definitions:
  FG 2.1  → MC 1.8-1.8.9 → Gradle 2.14  → JDK 8  → buildscript + apply "net.minecraftforge.gradle.forge"
  FG 2.2  → MC 1.9-1.11  → Gradle 2.14  → JDK 8  → buildscript + apply "net.minecraftforge.gradle.forge"
  FG 2.3  → MC 1.12.x    → Gradle 4.10  → JDK 8  → buildscript + apply "net.minecraftforge.gradle.forge"
  FG 3.x  → MC 1.13-1.14 → Gradle 4.10  → JDK 8  → buildscript + apply "net.minecraftforge.gradle"
  FG 4.1  → MC 1.15-1.16 → Gradle 6.9   → JDK 8  → buildscript + apply "net.minecraftforge.gradle"
  FG 5.1  → MC 1.17-1.19 → Gradle 7.6   → JDK 17 → buildscript + apply "net.minecraftforge.gradle"
  FG 6.x  → MC 1.19.3-1.21.3 → Gradle 8.5 → JDK 17/21 → buildscript + apply "net.minecraftforge.gradle"
  FG 7.x  → MC 1.21.4+   → Gradle 9.3   → JDK 21/25 → buildscript + apply "net.minecraftforge.gradle"
    Note: MC 26.x requires JDK 25; override per-version in ALL_VERSIONS
"""

import os

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODS_DIR = os.path.join(BASE_DIR, "mods")

# ============================================================
# FG ERA DEFINITIONS (structural, not per-version)
# ============================================================

FG_ERAS = {
    "fg21": {
        "fg_version": "2.1-SNAPSHOT",
        "gradle": "2.14",
        "plugin_id": "net.minecraftforge.gradle.forge",
        "apply_method": "buildscript",
        "java": 8,
        "min_mc": "1.8",
        "max_mc": "1.8.9",
    },
    "fg22": {
        "fg_version": "2.2-SNAPSHOT",
        "gradle": "2.14",
        "plugin_id": "net.minecraftforge.gradle.forge",
        "apply_method": "buildscript",
        "java": 8,
        "min_mc": "1.9",
        "max_mc": "1.11.2",
    },
    "fg23": {
        "fg_version": "2.3-SNAPSHOT",
        "gradle": "4.10.3",
        "plugin_id": "net.minecraftforge.gradle.forge",
        "apply_method": "buildscript",
        "java": 8,
        "min_mc": "1.12",
        "max_mc": "1.12.2",
    },
    "fg3": {
        "fg_version": "3.+",
        "gradle": "4.10.3",
        "plugin_id": "net.minecraftforge.gradle",
        "apply_method": "buildscript",
        "java": 8,
        "min_mc": "1.13.2",
        "max_mc": "1.14.4",
    },
    "fg41": {
        "fg_version": "[4.1,4.2)",
        "gradle": "6.9.4",
        "plugin_id": "net.minecraftforge.gradle",
        "apply_method": "buildscript",
        "java": 8,
        "min_mc": "1.15",
        "max_mc": "1.16.5",
    },
    "fg51": {
        "fg_version": "5.1.+",
        "gradle": "7.6.4",
        "plugin_id": "net.minecraftforge.gradle",
        "apply_method": "buildscript",
        "java": 17,
        "min_mc": "1.17.1",
        "max_mc": "1.19.2",
    },
    "fg6": {
        "fg_version": "[6.0,6.2)",
        "gradle": "8.5",
        "plugin_id": "net.minecraftforge.gradle",
        "apply_method": "buildscript",
        "java": 17,
        "min_mc": "1.19.3",
        "max_mc": "1.21.3",
    },
    "fg7": {
        "fg_version": "[7.0.23,8)",
        "gradle": "9.3.1",
        "plugin_id": "net.minecraftforge.gradle",
        "apply_method": "buildscript",
        "java": 21,
        "min_mc": "1.21.4",
        "max_mc": "99.99.99",
    },
}

# ============================================================
# PER-VERSION DATA
# ============================================================
# Each entry: mc_version → {forge, fg_era, java, mappings,
#                              neoforge?, mdg?, fabric_yarn?}

ALL_VERSIONS = {
    # --- FG 2.1 (MC 1.8.x) ---
    "1.8.9": {"forge": "1.8.9-11.15.1.2318-1.8.9",  "fg_era": "fg21", "java": 8, "mappings": "snapshot_20160113",
              "version_id": "1.8.9-forge1.8.9-11.15.1.2318-1.8.9"},

    # --- FG 2.2 (MC 1.9.4-1.11.2) ---
    "1.9.4":  {"forge": "1.9.4-12.17.0.2317-1.9.4",  "fg_era": "fg22", "java": 8, "mappings": "snapshot_20160518",
               "version_id": "1.9.4-forge1.9.4-12.17.0.2317-1.9.4"},
    "1.10.2": {"forge": "1.10.2-12.18.3.2511",       "fg_era": "fg22", "java": 8, "mappings": "snapshot_20160518",
               "version_id": "1.10.2-forge1.10.2-12.18.3.2511"},
    "1.11.2": {"forge": "1.11.2-13.20.1.2588",       "fg_era": "fg22", "java": 8, "mappings": "snapshot_20170111",
               "version_id": "1.11.2-forge1.11.2-13.20.1.2588"},

    # --- FG 2.3 (MC 1.12.x) ---
    "1.12.2": {"forge": "1.12.2-14.23.5.2847",       "fg_era": "fg23", "java": 8, "mappings": "snapshot_20171003",
               "version_id": "1.12.2-forge1.12.2-14.23.5.2847"},

    # --- FG 3.x (MC 1.13-1.14) ---
    "1.13.2": {"forge": "1.13.2-25.0.223",           "fg_era": "fg3",  "java": 8, "mappings": "snapshot_20190314",
               "version_id": "1.13.2-forge-25.0.223"},
    "1.14.4": {"forge": "1.14.4-28.2.28",            "fg_era": "fg3",  "java": 8, "mappings": "snapshot_20200119",
               "version_id": "1.14.4-forge-28.2.28",
               "fabric_yarn": "1.14.4+build.18"},

    # --- FG 4.1 (MC 1.15-1.16.5) ---
    "1.15.2": {"forge": "1.15.2-31.2.60",            "fg_era": "fg41", "java": 8, "mappings": "snapshot_20200224-1.15.1",
               "version_id": "1.15.2-forge-31.2.60",
               "fabric_yarn": "1.15.2+build.17"},
    "1.16.5": {"forge": "1.16.5-36.2.42",            "fg_era": "fg41", "java": 16, "mappings": "snapshot_20210309",
               "version_id": "1.16.5-forge-36.2.42",
               "fabric_yarn": "1.16.5+build.10"},

    # --- FG 5.1 (MC 1.17-1.18.2) ---
    "1.17.1": {"forge": "1.17.1-37.1.1",             "fg_era": "fg51", "java": 17, "mappings": "official_1.17.1",
               "version_id": "1.17.1-forge-37.1.1",
               "fabric_yarn": "1.17.1+build.65"},
    "1.18.2": {"forge": "1.18.2-40.3.12",            "fg_era": "fg51", "java": 17, "mappings": "official_1.18.2",
               "version_id": "1.18.2-forge-40.3.12",
               "fabric_yarn": "1.18.2+build.4"},

    # --- FG 6.x (MC 1.19.4-1.20.6) ---
    "1.19.4": {"forge": "1.19.4-45.4.3",             "fg_era": "fg6",  "java": 17, "mappings": "official_1.19.4",
               "version_id": "1.19.4-forge-45.4.3",
               "fabric_yarn": "1.19.4+build.1"},
    "1.20.6": {"forge": "1.20.6-50.2.8",             "fg_era": "fg6",  "java": 21, "mappings": "official_1.20.6",
               "version_id": "1.20.6-forge-50.2.8",
               "neoforge": "20.6.139", "mdg": "2.0.141",
               "fabric_yarn": "1.20.6+build.1"},

    # --- FG 7.x (MC 1.21.7+) ---
    "1.21.7": {"forge": "1.21.7-57.0.2",             "fg_era": "fg7",  "java": 21, "mappings": "official_1.21.7",
               "version_id": "1.21.7-forge-57.0.2"},

    # --- FG 7.x (MC 26.x) ---
    "26.1.2": {"forge": "26.1.2-64.0.8",             "fg_era": "fg7",  "java": 25, "mappings": "official_26.1.2",
               "version_id": "26.1.2-forge-64.0.8",
               "neoforge": "26.1.2.36-beta", "mdg": "2.0.141"},
}

# ============================================================
# JAVA SOURCE API GROUPS (determines which Java template to use)
# ============================================================
# Maps MC version → API group name for source generation.
# This is separate from FG era because source compatibility
# doesn't always match FG era boundaries.

def get_api_group(mc):
    """Return the API group for source code generation."""
    _MAP = {
        "1.8.9": "legacy",
        "1.9.4": "legacy", "1.10.2": "legacy", "1.11.2": "legacy",
        "1.12.2": "legacy",
        "1.13.2": "fg3",
        "1.14.4": "fg4", "1.15.2": "fg4", "1.16.5": "fg4",
        "1.17.1": "fg5", "1.18.2": "fg5",
        "1.19.4": "fg6",
        "1.20.6": "fg6",
        "1.21.7": "mc26",
        "26.1.2": "mc26",
    }
    return _MAP.get(mc, "fg6")


# ============================================================
# FABRIC LOOM VERSIONS
# ============================================================

def get_fabric_loom(mc):
    """Return Fabric Loom version for a given MC version."""
    _MAP = [
        (["1.14.4"], "0.8-SNAPSHOT"),
        (["1.15.2"], "0.8-SNAPSHOT"),
        (["1.16.5"], "0.12-SNAPSHOT"),
        (["1.17.1", "1.18.2"], "1.0-SNAPSHOT"),
        (["1.19.4"], "1.3-SNAPSHOT"),
        (["1.20.6"], "1.5-SNAPSHOT"),
    ]
    for versions, loom in _MAP:
        if mc in versions:
            return loom
    return "1.7-SNAPSHOT"


# ============================================================
# NEOFORGE MDG VERSIONS
# ============================================================

def get_neoforge_gradle(mc):
    """Return (mdg_version, gradle_version) for NeoForge."""
    info = ALL_VERSIONS.get(mc, {})
    mdg = info.get("mdg", "")
    if "2.0" in mdg:
        return (mdg, "9.3.1")
    if mdg:
        return (mdg, "8.10")
    return (None, None)


# ============================================================
# HELPER: Get loaders available for a MC version
# ============================================================

def get_loaders(mc):
    """Return list of available loaders for a MC version."""
    info = ALL_VERSIONS.get(mc, {})
    loaders = []
    if "forge" in info:
        loaders.append("forge")
    if "neoforge" in info:
        loaders.append("neoforge")
    if "fabric_yarn" in info:
        loaders.append("fabric")
    return loaders


# ============================================================
# HELPER: Get FG era config
# ============================================================

def get_fg_era(mc):
    """Return FG era config dict for a MC version."""
    info = ALL_VERSIONS.get(mc, {})
    era_key = info.get("fg_era")
    if era_key and era_key in FG_ERAS:
        return FG_ERAS[era_key]
    return None


# ============================================================
# HELPER: JDK paths
# ============================================================

JDK_PATHS = {
    8:  r"C:\Users\langy\.jdks\jdk8",
    16: None,  # Auto-downloaded by Gradle toolchain
    17: None,  # Auto-downloaded to ~/.gradle/jdks/
    21: r"C:\Program Files\Amazon Corretto\jdk21.0.8_9",
    24: r"C:\Users\langy\.jdks\openjdk-24.0.2+12-54",
    25: r"C:\Program Files\Amazon Corretto\jdk25.0.3_9",
}

def get_jdk_home(java_version):
    """Return JDK home path, or None if auto-download."""
    return JDK_PATHS.get(java_version)


def find_jdk17():
    """Find auto-downloaded JDK 17 in Gradle cache."""
    jdks_dir = os.path.join(os.path.expanduser("~"), ".gradle", "jdks")
    if not os.path.isdir(jdks_dir):
        return None
    for d in sorted(os.listdir(jdks_dir), reverse=True):
        if d.startswith("eclipse_adoptium-17") and "lock" not in d:
            path = os.path.join(jdks_dir, d)
            if os.path.isdir(path):
                return path
    return None


# ============================================================
# LEGACY BUILD ERAS (need pre-cache due to Cloudflare TLS)
# ============================================================

LEGACY_ERAS = {"fg21", "fg22", "fg23", "fg3", "fg41"}


def is_legacy(mc):
    """Return True if this MC version needs pre-cached artifacts."""
    info = ALL_VERSIONS.get(mc, {})
    return info.get("fg_era", "") in LEGACY_ERAS

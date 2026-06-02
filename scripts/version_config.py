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
MODS_DIR = os.path.join(BASE_DIR, "packages", "mods")

# ============================================================
# FG ERA DEFINITIONS (structural, not per-version)
# ============================================================

FG_ERAS = {
    "fg12_gtnh": {
        "fg_version": "1.2.11",
        "gradle": "4.4.1",
        "plugin_id": "forge",
        "apply_method": "buildscript_gtnh",
        "java": 8,
        "min_mc": "1.7.2",
        "max_mc": "1.7.10",
        "extra_repo": "https://jitpack.io",
    },
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
        "gradle": "9.3.0",
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
    # --- FG 1.2 GTNH (MC 1.7.x) ---
    "1.7.2":  {"forge": "1.7.2-10.12.1.1109",           "fg_era": "fg12_gtnh", "java": 8,
               "version_id": "1.7.2-Forge10.12.1.1109"},
    "1.7.10": {"forge": "1.7.10-10.13.4.1614-1.7.10",   "fg_era": "fg12_gtnh", "java": 8,
               "version_id": "1.7.10-Forge10.13.4.1614-1.7.10"},

    # --- FG 2.1 (MC 1.8.x) ---
    "1.8":   {"forge": "1.8-11.14.4.1563",                "fg_era": "fg21", "java": 8, "mappings": "snapshot_20141111",
              "version_id": "1.8-forge-11.14.4.1563"},
    "1.8.8": {"forge": "1.8.8-11.15.0.1655",              "fg_era": "fg21", "java": 8, "mappings": "snapshot_20141111",
              "version_id": "1.8.8-forge-11.15.0.1655"},
    "1.8.9": {"forge": "1.8.9-11.15.1.2318-1.8.9",       "fg_era": "fg21", "java": 8, "mappings": "snapshot_20160113",
              "version_id": "1.8.9-forge1.8.9-11.15.1.2318-1.8.9"},

    # --- FG 2.2 (MC 1.9-1.11) ---
    "1.9.4":  {"forge": "1.9.4-12.17.0.2317-1.9.4",       "fg_era": "fg22", "java": 8, "mappings": "snapshot_20160518",
               "version_id": "1.9.4-forge1.9.4-12.17.0.2317-1.9.4"},
    "1.10.2": {"forge": "1.10.2-12.18.3.2511",             "fg_era": "fg22", "java": 8, "mappings": "snapshot_20160518",
                "version_id": "1.10.2-forge1.10.2-12.18.3.2511"},
    "1.11":   {"forge": "1.11-13.19.1.2189",               "fg_era": "fg22", "java": 8, "mappings": "snapshot_20170111",
               "version_id": "1.11-Forge13.19.1.2189"},
    "1.11.2": {"forge": "1.11.2-13.20.1.2588",             "fg_era": "fg22", "java": 8, "mappings": "snapshot_20170111",
               "version_id": "1.11.2-forge1.11.2-13.20.1.2588"},

    # --- FG 2.3 (MC 1.12.x) ---
    "1.12":   {"forge": "1.12-14.21.1.2387",               "fg_era": "fg23", "java": 8, "mappings": "snapshot_20171003",
               "version_id": "1.12-Forge14.21.1.2387"},
    "1.12.1": {"forge": "1.12.1-14.22.1.2478",             "fg_era": "fg23", "java": 8, "mappings": "snapshot_20171003",
               "version_id": "1.12.1-Forge14.22.1.2478"},
    "1.12.2": {"forge": "1.12.2-14.23.5.2847",             "fg_era": "fg23", "java": 8, "mappings": "snapshot_20171003",
               "version_id": "1.12.2-forge1.12.2-14.23.5.2847"},

    # --- FG 3.x (MC 1.13-1.14) ---
    "1.13.2": {"forge": "1.13.2-25.0.223",                 "fg_era": "fg3",  "java": 8, "mappings": "snapshot_20190314",
               "version_id": "1.13.2-forge-25.0.223"},
    "1.14.2": {"forge": "1.14.2-26.0.63",                  "fg_era": "fg3",  "java": 8, "mappings": "snapshot_20190616",
               "version_id": "1.14.2-Forge26.0.63", "fabric_yarn": "1.14.2+build.7"},
    "1.14.3": {"forge": "1.14.3-27.0.60",                  "fg_era": "fg3",  "java": 8, "mappings": "snapshot_20190716",
               "version_id": "1.14.3-Forge27.0.60", "fabric_yarn": "1.14.3+build.9"},
    "1.14.4": {"forge": "1.14.4-28.2.28",                  "fg_era": "fg3",  "java": 8, "mappings": "snapshot_20200119",
               "version_id": "1.14.4-forge-28.2.28",
               "fabric_yarn": "1.14.4+build.18"},

    # --- FG 4.1 (MC 1.15-1.16.5) ---
    "1.15":   {"forge": "1.15-29.0.4",                     "fg_era": "fg41", "java": 8, "mappings": "snapshot_20200119",
               "version_id": "1.15-Forge29.0.4", "fabric_yarn": "1.15+build.2"},
    "1.15.1": {"forge": "1.15.1-30.0.51",                  "fg_era": "fg41", "java": 8, "mappings": "snapshot_20200224",
               "version_id": "1.15.1-Forge30.0.51", "fabric_yarn": "1.15.1+build.10"},
    "1.15.2": {"forge": "1.15.2-31.2.60",                  "fg_era": "fg41", "java": 8, "mappings": "snapshot_20200224-1.15.1",
               "version_id": "1.15.2-forge-31.2.60",
               "fabric_yarn": "1.15.2+build.17"},
    "1.16.1": {"forge": "1.16.1-32.0.108",                 "fg_era": "fg41", "java": 8, "mappings": "snapshot_20200723",
               "version_id": "1.16.1-Forge32.0.108", "fabric_yarn": "1.16.1+build.10"},
    "1.16.2": {"forge": "1.16.2-33.0.61",                  "fg_era": "fg41", "java": 8, "mappings": "snapshot_20200916",
               "version_id": "1.16.2-Forge33.0.61", "fabric_yarn": "1.16.2+build.1"},
    "1.16.3": {"forge": "1.16.3-34.1.0",                   "fg_era": "fg41", "java": 8, "mappings": "snapshot_20200514",
               "version_id": "1.16.3-Forge34.1.0", "fabric_yarn": "1.16.3+build.7"},
    "1.16.4": {"forge": "1.16.4-35.1.4",                   "fg_era": "fg41", "java": 8, "mappings": "snapshot_20210309",
               "version_id": "1.16.4-Forge35.1.4", "fabric_yarn": "1.16.4+build.1"},
    "1.16.5": {"forge": "1.16.5-36.2.42",                  "fg_era": "fg41", "java": 16, "mappings": "snapshot_20210309",
               "version_id": "1.16.5-forge-36.2.42",
               "fabric_yarn": "1.16.5+build.10"},

    # --- FG 5.1 (MC 1.17-1.19.2) ---
    "1.17.1": {"forge": "1.17.1-37.1.1",                   "fg_era": "fg51", "java": 17, "mappings": "official_1.17.1",
               "version_id": "1.17.1-forge-37.1.1",
               "fabric_yarn": "1.17.1+build.65"},
    "1.18":   {"forge": "1.18-38.0.17",                    "fg_era": "fg51", "java": 17, "mappings": "official_1.18",
               "version_id": "1.18-Forge38.0.17", "fabric_yarn": "1.18+build.1"},
    "1.18.1": {"forge": "1.18.1-39.1.0",                   "fg_era": "fg51", "java": 17, "mappings": "official_1.18.1",
               "version_id": "1.18.1-Forge39.1.0", "fabric_yarn": "1.18.1+build.8"},
    "1.18.2": {"forge": "1.18.2-40.3.12",                  "fg_era": "fg51", "java": 17, "mappings": "official_1.18.2",
               "version_id": "1.18.2-forge-40.3.12",
               "fabric_yarn": "1.18.2+build.4"},
    "1.19":   {"forge": "1.19-41.1.0",                     "fg_era": "fg51", "java": 17, "mappings": "official_1.19",
               "version_id": "1.19-Forge41.1.0", "fabric_yarn": "1.19+build.4"},
    "1.19.1": {"forge": "1.19.1-42.0.9",                   "fg_era": "fg51", "java": 17, "mappings": "official_1.19.1",
               "version_id": "1.19.1-Forge42.0.9", "fabric_yarn": "1.19.1+build.4"},
    "1.19.2": {"forge": "1.19.2-43.5.0",                   "fg_era": "fg51", "java": 17, "mappings": "official_1.19.2",
               "version_id": "1.19.2-Forge43.5.0", "fabric_yarn": "1.19.2+build.28"},

    # --- FG 6.x (MC 1.19.3-1.21.3) ---
    "1.19.3": {"forge": "1.19.3-44.1.0",                   "fg_era": "fg6",  "java": 17, "mappings": "official_1.19.3",
               "version_id": "1.19.3-Forge44.1.0", "fabric_yarn": "1.19.3+build.2"},
    "1.19.4": {"forge": "1.19.4-45.4.3",                   "fg_era": "fg6",  "java": 17, "mappings": "official_1.19.4",
               "version_id": "1.19.4-forge-45.4.3",
               "fabric_yarn": "1.19.4+build.1"},
    "1.20":   {"forge": "1.20-46.0.14",                    "fg_era": "fg6",  "java": 17, "mappings": "official_1.20",
               "version_id": "1.20-Forge46.0.14", "fabric_yarn": "1.20+build.1"},
    "1.20.1": {"forge": "1.20.1-47.4.10",                  "fg_era": "fg6",  "java": 17, "mappings": "official_1.20.1",
               "version_id": "1.20.1-forge-47.4.10",
               "fabric_yarn": "1.20.1+build.10"},
    "1.20.2": {"forge": "1.20.2-48.1.0",                   "fg_era": "fg6",  "java": 17, "mappings": "official_1.20.2",
               "version_id": "1.20.2-Forge48.1.0", "fabric_yarn": "1.20.2+build.1"},
    "1.20.3": {"forge": "1.20.3-49.0.2",                   "fg_era": "fg6",  "java": 17, "mappings": "official_1.20.3",
               "version_id": "1.20.3-Forge49.0.2", "fabric_yarn": "1.20.3+build.1"},
    "1.20.4": {"forge": "1.20.4-49.2.0",                   "fg_era": "fg6",  "java": 17, "mappings": "official_1.20.4",
               "version_id": "1.20.4-Forge49.2.0", "fabric_yarn": "1.20.4+build.3",
               "neoforge": "49.0.51"},
    "1.20.6": {"forge": "1.20.6-50.2.8",                   "fg_era": "fg6",  "java": 21, "mappings": "official_1.20.6",
               "version_id": "1.20.6-forge-50.2.8",
               "neoforge": "20.6.139", "mdg": "2.0.141",
               "fabric_yarn": "1.20.6+build.1"},
    "1.21":   {"forge": "1.21-51.0.33",                    "fg_era": "fg6",  "java": 21, "mappings": "official_1.21",
               "version_id": "1.21-Forge51.0.33", "fabric_yarn": "1.21+build.1"},
    "1.21.1": {"forge": "1.21.1-52.1.0",                   "fg_era": "fg6",  "java": 21, "mappings": "official_1.21.1",
               "version_id": "1.21.1-Forge52.1.0", "fabric_yarn": "1.21.1+build.3",
               "neoforge": "21.1.172", "mdg": "2.0.141"},
    "1.21.3": {"forge": "1.21.3-53.1.0",                   "fg_era": "fg6",  "java": 21, "mappings": "official_1.21.3",
               "version_id": "1.21.3-Forge53.1.0", "fabric_yarn": "1.21.3+build.1",
               "neoforge": "21.3.63", "mdg": "2.0.141"},

    # --- FG 7.x (MC 1.21.4+) ---
    "1.21.4": {"forge": "1.21.4-54.1.14",                  "fg_era": "fg7",  "java": 21, "mappings": "official_1.21.4",
               "version_id": "1.21.4-Forge54.1.14",
               "neoforge": "21.4.0-beta", "mdg": "2.0.141"},
    "1.21.5": {"forge": "1.21.5-55.1.0",                   "fg_era": "fg7",  "java": 21, "mappings": "official_1.21.5",
               "version_id": "1.21.5-Forge55.1.0"},
    "1.21.6": {"forge": "1.21.6-56.0.9",                   "fg_era": "fg7",  "java": 21, "mappings": "official_1.21.6",
               "version_id": "1.21.6-Forge56.0.9"},
    "1.21.7": {"forge": "1.21.7-57.0.3",                   "fg_era": "fg7",  "java": 21, "mappings": "official_1.21.7",
               "version_id": "1.21.7-Forge57.0.3"},
    "1.21.8": {"forge": "1.21.8-58.1.0",                   "fg_era": "fg7",  "java": 21, "mappings": "official_1.21.8",
               "version_id": "1.21.8-Forge58.1.0"},
    "1.21.9": {"forge": "1.21.9-59.0.5",                   "fg_era": "fg7",  "java": 21, "mappings": "official_1.21.9",
               "version_id": "1.21.9-Forge59.0.5"},
    "1.21.10": {"forge": "1.21.10-60.1.0",                 "fg_era": "fg7",  "java": 21, "mappings": "official_1.21.10",
               "version_id": "1.21.10-Forge60.1.0"},
    "1.21.11": {"forge": "1.21.11-61.1.5",                 "fg_era": "fg7",  "java": 21, "mappings": "official_1.21.11",
               "version_id": "1.21.11-forge-61.1.5",
               "neoforge": "21.11.42", "mdg": "2.0.141",
               "fabric_yarn": "1.21.11+build.6"},
    "26.1.2": {"forge": "26.1.2-64.0.8",                   "fg_era": "fg7",  "java": 25, "mappings": "official_26.1.2",
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
        "1.7.2": "legacy17", "1.7.10": "legacy17",
        "1.8": "legacy", "1.8.8": "legacy", "1.8.9": "legacy",
        "1.9.4": "legacy",
        "1.10.2": "legacy",
        "1.11": "legacy", "1.11.2": "legacy",
        "1.12": "legacy", "1.12.1": "legacy", "1.12.2": "legacy",
        "1.13.2": "fg3",
        "1.14.2": "fg3", "1.14.3": "fg3", "1.14.4": "fg3",
        "1.15": "fg4", "1.15.1": "fg4", "1.15.2": "fg4",
        "1.16.1": "fg4", "1.16.2": "fg4", "1.16.3": "fg4", "1.16.4": "fg4", "1.16.5": "fg4",
        "1.17.1": "fg5",
        "1.18": "fg5", "1.18.1": "fg5", "1.18.2": "fg5",
        "1.19": "fg5", "1.19.1": "fg5", "1.19.2": "fg5",
        "1.19.3": "fg6", "1.19.4": "fg6",
        "1.20": "fg6", "1.20.1": "fg6", "1.20.2": "fg6", "1.20.3": "fg6",
        "1.20.4": "fg6", "1.20.6": "fg6",
        "1.21": "fg6", "1.21.1": "fg6", "1.21.3": "fg6",
        "1.21.4": "mc26", "1.21.5": "mc26", "1.21.6": "mc26",
        "1.21.7": "mc26", "1.21.8": "mc26", "1.21.9": "mc26",
        "1.21.10": "mc26", "1.21.11": "mc26",
        "26.1.2": "mc26",
    }
    return _MAP.get(mc, "fg6")


# ============================================================
# FABRIC LOOM VERSIONS
# ============================================================

def get_fabric_loom(mc):
    """Return Fabric Loom version for a given MC version."""
    _MAP = [
        (["1.14.2", "1.14.3", "1.14.4"], "0.8-SNAPSHOT"),
        (["1.15", "1.15.1", "1.15.2"], "0.8-SNAPSHOT"),
        (["1.16.1", "1.16.2", "1.16.3", "1.16.4", "1.16.5"], "0.12-SNAPSHOT"),
        (["1.17.1", "1.18", "1.18.1", "1.18.2"], "1.0-SNAPSHOT"),
        (["1.19", "1.19.1", "1.19.2"], "1.0-SNAPSHOT"),
        (["1.19.3", "1.19.4"], "1.3-SNAPSHOT"),
        (["1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4"], "1.3-SNAPSHOT"),
        (["1.20.6"], "1.5-SNAPSHOT"),
        (["1.21", "1.21.1", "1.21.3"], "1.5-SNAPSHOT"),
        (["1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8"], "1.7-SNAPSHOT"),
        (["1.21.11"], "1.14-SNAPSHOT"),
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

_PROJECT_JDKS = os.path.join(BASE_DIR, ".jdks")
_GRADLE_JDKS = os.path.join(os.path.expanduser("~"), ".gradle", "jdks")


def _scan_jdk_dir(jdks_dir):
    """Scan a directory for JDK installations, return {major_version: path}."""
    found = {}
    if not os.path.isdir(jdks_dir):
        return found
    for d in os.listdir(jdks_dir):
        full = os.path.join(jdks_dir, d)
        if not os.path.isdir(full):
            continue
        release = os.path.join(full, "release")
        if not os.path.isfile(release):
            continue
        try:
            with open(release, encoding="utf-8", errors="ignore") as f:
                for line in f:
                    if line.startswith("JAVA_VERSION="):
                        ver_str = line.split("=", 1)[1].strip().strip('"')
                        parts = ver_str.split(".")
                        major = int(parts[0])
                        if major == 1 and len(parts) > 1:
                            major = int(parts[1])
                        if major not in found:
                            found[major] = full
                        break
        except Exception:
            pass
    return found


def _discover_jdks():
    """Auto-discover JDKs from .jdks/ and Gradle cache."""
    paths = {}
    paths.update(_scan_jdk_dir(_PROJECT_JDKS))
    for major, path in _scan_jdk_dir(_GRADLE_JDKS).items():
        if major not in paths:
            paths[major] = path
    return paths


_DISCOVERED_JDKS = _discover_jdks()

JDK_PATHS = {
    8:  _DISCOVERED_JDKS.get(8),
    16: _DISCOVERED_JDKS.get(16),
    17: _DISCOVERED_JDKS.get(17),
    21: _DISCOVERED_JDKS.get(21),
    24: _DISCOVERED_JDKS.get(24),
    25: _DISCOVERED_JDKS.get(25),
}


def get_jdk_home(java_version):
    """Return JDK home path for a given major version, auto-discovered from .jdks/."""
    return JDK_PATHS.get(java_version)


def find_jdk17():
    """Find JDK 17 — checks .jdks/ first, then Gradle cache."""
    if JDK_PATHS.get(17):
        return JDK_PATHS[17]
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

LEGACY_ERAS = {"fg12_gtnh", "fg21", "fg22", "fg23", "fg3", "fg41"}


def is_legacy(mc):
    """Return True if this MC version needs pre-cached artifacts."""
    info = ALL_VERSIONS.get(mc, {})
    return info.get("fg_era", "") in LEGACY_ERAS

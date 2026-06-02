"""Generate complete version_config.py entries for ALL Forge-supported MC versions.

Uses Forge promotions_slim.json to discover all available versions,
then maps them to the correct FG era, Java version, and mappings.

Output: Python dict entries for version_config.py ALL_VERSIONS
"""
import json
import re
import sys

# Forge era boundaries (MC version -> era)
# These determine which ForgeGradle version and build template to use
FG_ERA_MAP = [
    # (max_mc_inclusive, era_key, java_version)
    ("1.7.10", "fg12_gtnh", 8),
    ("1.8.9", "fg21", 8),
    ("1.11.2", "fg22", 8),
    ("1.12.2", "fg23", 8),
    ("1.14.4", "fg3", 8),
    ("1.16.5", "fg41", 8),
    ("1.19.2", "fg51", 17),
    ("1.21.3", "fg6", 17),
    ("99.99.99", "fg7", 21),
]

# Java overrides per MC version (some versions need different JDK than era default)
JAVA_OVERRIDES = {
    "1.16.5": 16,
    "1.20.6": 21,
    "26.1.2": 25,
}

# MCP mappings per MC version (for FG 2.x/3.x/4.1 eras)
# Format: snapshot_YYYYMMDD or stable_XX or official_X.X.X
MAPPINGS = {
    # FG 2.x/3.x/4.1 - need MCP mappings
    "1.8": "snapshot_20141111",
    "1.8.8": "snapshot_20141111",
    "1.8.9": "snapshot_20160113",
    "1.9": "snapshot_20160316",
    "1.9.4": "snapshot_20160518",
    "1.10": "snapshot_20160518",
    "1.10.2": "snapshot_20160518",
    "1.11": "snapshot_20170111",
    "1.11.2": "snapshot_20170111",
    "1.12": "snapshot_20171003",
    "1.12.1": "snapshot_20171003",
    "1.12.2": "snapshot_20171003",
    "1.13.2": "snapshot_20190314",
    "1.14.2": "snapshot_20190314",
    "1.14.3": "snapshot_20190314",
    "1.14.4": "snapshot_20200119",
    "1.15": "snapshot_20191220",
    "1.15.1": "snapshot_20200224-1.15.1",
    "1.15.2": "snapshot_20200224-1.15.1",
    "1.16.1": "snapshot_20200514-1.16",
    "1.16.2": "snapshot_20200802-1.16.2",
    "1.16.3": "snapshot_20200802-1.16.2",
    "1.16.4": "snapshot_20201009-1.16.3",
    "1.16.5": "snapshot_20210309",
    # FG 5.1+ - official mappings
    "1.17.1": "official_1.17.1",
    "1.18": "official_1.18",
    "1.18.1": "official_1.18.1",
    "1.18.2": "official_1.18.2",
    "1.19": "official_1.19",
    "1.19.1": "official_1.19.1",
    "1.19.2": "official_1.19.2",
    "1.19.3": "official_1.19.3",
    "1.19.4": "official_1.19.4",
    "1.20": "official_1.20",
    "1.20.1": "official_1.20.1",
    "1.20.2": "official_1.20.2",
    "1.20.3": "official_1.20.3",
    "1.20.4": "official_1.20.4",
    "1.20.6": "official_1.20.6",
    "1.21": "official_1.21",
    "1.21.1": "official_1.21.1",
    "1.21.3": "official_1.21.3",
    "1.21.4": "official_1.21.4",
    "1.21.5": "official_1.21.5",
    "1.21.6": "official_1.21.6",
    "1.21.7": "official_1.21.7",
    "1.21.8": "official_1.21.8",
    "1.21.9": "official_1.21.9",
    "1.21.10": "official_1.21.10",
    "1.21.11": "official_1.21.11",
    "26.1.2": "official_26.1.2",
}

# Fabric yarn versions per MC version
FABRIC_YARN = {
    "1.14.2": "1.14.2+build.7",
    "1.14.3": "1.14.3+build.9",
    "1.14.4": "1.14.4+build.18",
    "1.15": "1.15+build.2",
    "1.15.1": "1.15.1+build.10",
    "1.15.2": "1.15.2+build.17",
    "1.16.1": "1.16.1+build.10",
    "1.16.2": "1.16.2+build.1",
    "1.16.3": "1.16.3+build.7",
    "1.16.4": "1.16.4+build.1",
    "1.16.5": "1.16.5+build.10",
    "1.17.1": "1.17.1+build.65",
    "1.18": "1.18+build.1",
    "1.18.1": "1.18.1+build.8",
    "1.18.2": "1.18.2+build.4",
    "1.19": "1.19+build.4",
    "1.19.1": "1.19.1+build.4",
    "1.19.2": "1.19.2+build.28",
    "1.19.3": "1.19.3+build.2",
    "1.19.4": "1.19.4+build.1",
    "1.20": "1.20+build.1",
    "1.20.1": "1.20.1+build.10",
    "1.20.2": "1.20.2+build.1",
    "1.20.3": "1.20.3+build.1",
    "1.20.4": "1.20.4+build.3",
    "1.20.6": "1.20.6+build.1",
    "1.21": "1.21+build.1",
    "1.21.1": "1.21.1+build.3",
    "1.21.3": "1.21.3+build.1",
    "1.21.4": "1.21.4+build.1",
    "1.21.5": "1.21.5+build.1",
    "1.21.8": "1.21.8+build.1",
    "1.21.11": "1.21.11+build.6",
}

# NeoForge versions (only for 1.20.1+)
NEOFORGE = {
    "1.20.1": ("47.1.106", None),
    "1.20.4": ("49.0.51", None),
    "1.20.6": ("20.6.139", "2.0.141"),
    "1.21.1": ("21.1.172", "2.0.141"),
    "1.21.3": ("21.3.63", "2.0.141"),
    "1.21.4": ("21.4.0-beta", "2.0.141"),
    "1.21.11": ("21.11.42", "2.0.141"),
    "26.1.2": ("26.1.2.36-beta", "2.0.141"),
}

# Skip versions < 1.7.2
SKIP = {"1.1", "1.2.3", "1.2.4", "1.2.5", "1.3.2", "1.4.0", "1.4.1", "1.4.2",
        "1.4.3", "1.4.4", "1.4.5", "1.4.6", "1.4.7", "1.5", "1.5.1", "1.5.2",
        "1.6.1", "1.6.2", "1.6.3", "1.6.4"}

# Already have (keep existing)
EXISTING = {"1.7.2", "1.7.10", "1.8.9", "1.9.4", "1.10.2", "1.11.2", "1.12.2",
            "1.13.2", "1.14.4", "1.15.2", "1.16.5", "1.17.1", "1.18.2", "1.19.4",
            "1.20.6", "1.21.11", "26.1.2"}


def version_sort_key(v):
    return [int(p) for p in v.split('.')]


def get_era(mc):
    parts = [int(p) for p in mc.split('.')]
    for max_mc, era_key, java in FG_ERA_MAP:
        max_parts = [int(p) for p in max_mc.split('.')]
        if parts <= max_parts:
            return era_key, java
    return "fg7", 21


def forge_version_string(mc, forge_ver):
    if mc in ("1.7.2",):
        return f"{mc}-{forge_ver}"
    return f"{mc}-{forge_ver}-{mc}" if mc >= "1.8" else f"{mc}-{forge_ver}"


def version_id(mc, forge_ver):
    if mc <= "1.7.10":
        return f"{mc}-Forge{forge_ver}"
    return f"{mc}-forge-{forge_ver}"


def main():
    # Read promotions from stdin or use hardcoded
    promos_raw = sys.stdin.read() if not sys.stdin.isatty() else None
    if not promos_raw:
        print("Usage: python scripts/generate_version_data.py < promotions_slim.json")
        print("       or pipe JSON via stdin")
        sys.exit(1)

    data = json.loads(promos_raw)
    promos = data.get("promos", data)

    # Collect recommended versions
    mc_versions = {}
    for k, v in promos.items():
        if k.endswith("-recommended"):
            mc = k[:-len("-recommended")]
            if mc not in SKIP:
                mc_versions[mc] = v

    # Also add latest-only versions
    for k, v in promos.items():
        if k.endswith("-latest"):
            mc = k[:-len("-latest")]
            if mc not in SKIP and mc not in mc_versions:
                mc_versions[mc] = v

    print("# Auto-generated version entries for version_config.py")
    print("# Generated from Forge promotions_slim.json")
    print()

    for mc in sorted(mc_versions.keys(), key=version_sort_key):
        forge_num = mc_versions[mc]
        era, default_java = get_era(mc)
        java = JAVA_OVERRIDES.get(mc, default_java)
        mappings = MAPPINGS.get(mc, "")
        forge_full = forge_version_string(mc, forge_num)
        vid = version_id(mc, forge_num)

        extras = []
        if mappings:
            extras.append(f'"mappings": "{mappings}"')

        # Fabric
        yarn = FABRIC_YARN.get(mc)
        if yarn:
            extras.append(f'"fabric_yarn": "{yarn}"')

        # NeoForge
        nf = NEOFORGE.get(mc)
        if nf:
            nf_ver, mdg = nf
            extras.append(f'"neoforge": "{nf_ver}"')
            if mdg:
                extras.append(f'"mdg": "{mdg}"')

        tag = "  # NEW" if mc not in EXISTING else ""
        extras_str = ", " + ", ".join(extras) if extras else ""

        print(f'    "{mc}": {{"forge": "{forge_full}", "fg_era": "{era}", "java": {java}{extras_str},')
        print(f'               "version_id": "{vid}"}},{tag}')


if __name__ == "__main__":
    main()

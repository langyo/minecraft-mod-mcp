#!/usr/bin/env python3
"""Regenerate packages/minecraft-mod-mcp/src/mc/versions-embedded.json from
version_config.ALL_VERSIONS.

The CLI ships this JSON as its single source of truth (it can't import the
Python module at runtime). It drifts out of sync when version_config.py gains
new fabric_yarn / neoforge / mdg entries — the loader-availability checks then
silently fail ("fabric not available for X"). Run this after editing
version_config.py.

Usage: python scripts/gen_embedded_versions.py [--check]   # --check: exit 1 if stale
"""
from __future__ import annotations

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from version_config import (  # noqa: E402
    ALL_VERSIONS, FG_ERAS, get_api_group, get_fabric_loom,
    get_neoforge_gradle, LEGACY_ERAS,
)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "packages", "minecraft-mod-mcp", "src", "mc",
                   "versions-embedded.json")


def _key(mc: str) -> str:
    """MC version string → JSON key ('.' → '_'). Matches existing convention."""
    return mc.replace(".", "_")


def build() -> dict:
    fg_eras = {}
    for k, e in FG_ERAS.items():
        entry = {
            "key": k,
            "fg_version": e["fg_version"],
            "gradle": e["gradle"],
            "plugin_id": e["plugin_id"],
            "java": e["java"],
            "min_mc": e.get("min_mc", ""),
            "max_mc": e.get("max_mc", ""),
        }
        if "extra_repo" in e:
            entry["extra_repo"] = e["extra_repo"]
        fg_eras[k] = entry

    versions = {}
    for mc, info in ALL_VERSIONS.items():
        entry = {
            "forge": info.get("forge", ""),
            "fg_era": info.get("fg_era", ""),
            "java": info.get("java", 17),
            "version_id": info.get("version_id", mc),
        }
        if "mappings" in info:
            entry["mappings"] = info["mappings"]
        if "fabric_yarn" in info:
            entry["fabric_yarn"] = info["fabric_yarn"]
        if "neoforge" in info:
            entry["neoforge"] = info["neoforge"]
        if "mdg" in info:
            entry["mdg"] = info["mdg"]
        versions[_key(mc)] = entry

    api_groups = {_key(mc): get_api_group(mc) for mc in ALL_VERSIONS}

    fabric_loom = {}
    for mc in ALL_VERSIONS:
        loom = get_fabric_loom(mc)
        if loom:
            fabric_loom[mc] = loom

    # MDG / NeoForge gradle versions — pick representative entries.
    neoforge_gradle = {
        "mdg_2_0_prefix": "2.0",
        "mdg_2_0_gradle": "9.3.1",
        "mdg_other_gradle": "8.10",
    }

    return {
        "fg_eras": fg_eras,
        "versions": versions,
        "api_groups": api_groups,
        "fabric_loom": fabric_loom,
        "neoforge_gradle": neoforge_gradle,
        "legacy": {"eras": sorted(LEGACY_ERAS)},
    }


def main():
    import argparse
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true",
                    help="Don't write; exit 1 if the file would change")
    args = ap.parse_args()

    data = build()
    rendered = json.dumps(data, indent=2, ensure_ascii=False) + "\n"

    if args.check:
        existing = ""
        if os.path.isfile(OUT):
            with open(OUT, "r", encoding="utf-8") as f:
                existing = f.read()
        if existing != rendered:
            missing = []
            for mc, info in ALL_VERSIONS.items():
                if "fabric_yarn" in info:
                    old = json.loads(existing).get("versions", {}).get(_key(mc), {})
                    if "fabric_yarn" not in old:
                        missing.append(mc)
            print(f"STALE: versions-embedded.json is out of sync with version_config.py")
            if missing:
                print(f"  Missing fabric_yarn for: {', '.join(missing)}")
            return 1
        print("OK: versions-embedded.json is up to date")
        return 0

    with open(OUT, "w", encoding="utf-8") as f:
        f.write(rendered)
    print(f"Wrote {OUT}")
    print(f"  {len(ALL_VERSIONS)} versions, {sum(1 for v in ALL_VERSIONS.values() if 'fabric_yarn' in v)} with fabric_yarn")
    return 0


if __name__ == "__main__":
    sys.exit(main())

"""Generate mod projects for all supported MC versions and loaders.

All version/era data comes from version_config.py — nothing is hardcoded here.

FG Era build.gradle templates:
  FG 1.2 GTNH (1.7.x)       → buildscript + apply "forge", GTNH FG 1.2.11, Gradle 4.4.1
  FG 2.1/2.2/2.3 (1.8-1.12) → buildscript + apply "net.minecraftforge.gradle.forge"
  FG 3.x    (1.13-1.14)     → buildscript + apply "net.minecraftforge.gradle", Gradle 4.10
  FG 4.1    (1.15-1.16)     → buildscript + apply "net.minecraftforge.gradle", Gradle 6.9
  FG 5.1    (1.17-1.19)     → buildscript + apply, java.toolchain JDK 17, Gradle 7.6
  FG 6.x    (1.19.3-1.21.3) → buildscript + apply, workingDirectory, Gradle 8.5
  FG 7.x    (1.21.4+)       → buildscript + apply, Gradle 9.3

NeoForge ModDev:
  MDG 1.0.x → Gradle 8.10
  MDG 2.0.x → Gradle 9.3

Fabric Loom:
  0.12.x (1.14.4-1.16) → Gradle 8.10
  1.x    (1.17+)        → varies
"""

import os

from version_config import ALL_VERSIONS, FG_ERAS, MODS_DIR, get_loaders

from modgen.fabric import write_fabric_build, write_fabric_mod_json, write_fabric_settings
from modgen.forge import (
    write_forge_build_fg12,
    write_forge_build_fg2,
    write_forge_build_fg3,
    write_forge_build_fg41,
    write_forge_build_fg51,
    write_forge_build_fg6,
    write_forge_build_fg7,
    write_forge_settings_legacy,
    write_forge_settings_modern,
)
from modgen.gradle import (
    _gradle_ver_for_fabric,
    _gradle_ver_for_forge,
    _gradle_ver_for_neoforge,
    copy_wrapper,
)
from modgen.neoforge import (
    write_neoforge_build,
    write_neoforge_build_fg6,
    write_neoforge_settings,
)


# ============================================================
# BUILD / SETTINGS DISPATCH
# ============================================================

_FORGE_BUILD_FNS = {
    "fg12_gtnh": write_forge_build_fg12,
    "fg21": write_forge_build_fg2,
    "fg22": write_forge_build_fg2,
    "fg23": write_forge_build_fg2,
    "fg3":  write_forge_build_fg3,
    "fg41": write_forge_build_fg41,
    "fg51": write_forge_build_fg51,
    "fg6":  write_forge_build_fg6,
    "fg7":  write_forge_build_fg7,
}

_FORGE_SETTINGS_LEGACY_ERAS = {"fg12_gtnh", "fg21", "fg22", "fg23", "fg3", "fg41"}


def _get_gradle_ver(mc, loader, info):
    if loader == "fabric":
        return _gradle_ver_for_fabric(mc)
    if loader == "neoforge":
        return _gradle_ver_for_neoforge(mc)
    era_key = info.get("fg_era", "")
    if era_key in FG_ERAS:
        return _gradle_ver_for_forge(era_key)
    return "8.10"


def create_mod(mc, loader, info):
    base = os.path.join(MODS_DIR, mc, loader)
    os.makedirs(base, exist_ok=True)
    gradle_ver = _get_gradle_ver(mc, loader, info)
    copy_wrapper(base, gradle_ver)

    if loader == "forge":
        era_key = info.get("fg_era", "")
        if era_key in _FORGE_SETTINGS_LEGACY_ERAS:
            write_forge_settings_legacy(mc, info, base)
        else:
            write_forge_settings_modern(mc, info, base)
        build_fn = _FORGE_BUILD_FNS.get(era_key)
        if build_fn:
            build_fn(mc, info, base)
    elif loader == "neoforge":
        style = info.get("neoforge_style", "mdg")
        if style == "fg6":
            era_key = info.get("fg_era", "fg6")
            if era_key in _FORGE_SETTINGS_LEGACY_ERAS:
                write_forge_settings_legacy(mc, info, base)
            else:
                write_forge_settings_modern(mc, info, base)
            write_neoforge_build_fg6(mc, info, base)
        else:
            write_neoforge_settings(mc, info, base)
            write_neoforge_build(mc, info, base)
    elif loader == "fabric":
        write_fabric_settings(mc, info, base)
        write_fabric_build(mc, info, base)
        write_fabric_mod_json(mc, info, base)

    return base


# ============================================================
# MAIN
# ============================================================

if __name__ == "__main__":
    total = 0
    for mc, info in ALL_VERSIONS.items():
        loaders = get_loaders(mc)
        for loader in loaders:
            path = create_mod(mc, loader, info)
            total += 1
            print(
                f"  Created: {mc}/{loader} (Gradle {_get_gradle_ver(mc, loader, info)})"
            )
    print(f"\nTotal: {total} mod projects created")

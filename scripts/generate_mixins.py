#!/usr/bin/env python3
"""Backfill missing Fabric mixin classes from a same-era reference version.

Many fabric mod projects commit only the version-sensitive mixin (e.g.
MouseMixin) but their mcpmod.mixins.json references 4-5 mixins, so Fabric
fatally aborts at startup with ClassNotFoundException on the missing ones.

The broad-hook mixins (InGameHudMixin, ScreenMixin, MinecraftClientMixin) are
stable across a render-API era (1.14-1.19 MatrixStack, 1.20 DrawContext,
1.21+ RenderTickCounter), so a version-correct set already exists in each era's
"reference" (fully-built) version. This script copies only the MISSING mixin
classes from the era reference into each incomplete version — it never
overwrites an existing (version-correct) mixin.

Idempotent: re-running is a no-op once mixins are present.
"""
import json
import os
import shutil
import sys

MODS = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "packages", "mods")
PKG = os.path.join("src", "main", "java", "xyz", "langyo", "minecraft", "mcp", "mod", "mixin")
RES = os.path.join("src", "main", "resources", "mcpmod.mixins.json")

# incomplete version -> era reference (a fully-built version with identical
# render API). Chosen so the reference's broad-hook mixin targets resolve in
# the target. The version-sensitive MouseMixin/MouseClickMixin is always
# preserved from the target itself.
FABRIC_ERA_REF = {
    "1.14.2": "1.14.4", "1.14.3": "1.14.4",
    "1.15": "1.15.2", "1.15.1": "1.15.2",
    "1.16.1": "1.16.5", "1.16.2": "1.16.5", "1.16.3": "1.16.5", "1.16.4": "1.16.5",
    "1.18": "1.18.2", "1.18.1": "1.18.2",
    "1.19": "1.19.4", "1.19.1": "1.19.4", "1.19.2": "1.19.4", "1.19.3": "1.19.4",
    "1.20": "1.20.6", "1.20.1": "1.20.6", "1.20.2": "1.20.6", "1.20.3": "1.20.6", "1.20.4": "1.20.6",
    "1.21": "1.21.11", "1.21.1": "1.21.11", "1.21.3": "1.21.11", "1.21.4": "1.21.11",
    "1.21.5": "1.21.11", "1.21.6": "1.21.11", "1.21.7": "1.21.11", "1.21.8": "1.21.11",
    "1.21.9": "1.21.11", "1.21.10": "1.21.11",
    "26.1.2": "1.21.11",
}


def mixin_classes(version):
    """Return the mixin class names referenced by this version's mixins.json."""
    path = os.path.join(MODS, version, "fabric", RES)
    if not os.path.isfile(path):
        return []
    try:
        with open(path, encoding="utf-8") as f:
            return list(json.load(f).get("client", []))
    except Exception:
        return []


def main():
    total = 0
    for target, ref in sorted(FABRIC_ERA_REF.items()):
        tgt_dir = os.path.join(MODS, target, "fabric", PKG)
        src_dir = os.path.join(MODS, ref, "fabric", PKG)
        if not os.path.isdir(src_dir):
            print(f"  SKIP {target}: reference {ref} has no mixin dir")
            continue
        os.makedirs(tgt_dir, exist_ok=True)

        copied = []
        for cls in mixin_classes(target):
            tgt_file = os.path.join(tgt_dir, cls + ".java")
            if os.path.isfile(tgt_file):
                continue  # never overwrite a version-correct mixin
            src_file = os.path.join(src_dir, cls + ".java")
            if not os.path.isfile(src_file):
                print(f"  WARN {target}: {cls} missing in reference {ref} too")
                continue
            shutil.copyfile(src_file, tgt_file)
            copied.append(cls)

        if copied:
            print(f"  {target}/fabric <- {ref}: copied {', '.join(copied)}")
            total += len(copied)

    print(f"\nDone. Copied {total} mixin class(es).")


if __name__ == "__main__":
    sys.exit(main())

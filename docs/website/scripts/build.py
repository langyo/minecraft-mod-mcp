#!/usr/bin/env python3
"""Build the Minecraft Mod MCP website — install deps, typecheck, bundle, generate favicons, copy assets.

In CI the workflow runs install / typecheck / vite build as separate steps for
caching and passes ``--skip-deps --skip-typecheck --skip-vite`` so this script
only handles favicons, the logo directory, CNAME and .nojekyll.
"""

import argparse
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / "dist"
RES = ROOT / "res"
LOGOS = RES / "logos"
SOURCE_LOGO = LOGOS / "minecraft-mod-mcp.webp"


def run(cmd: list[str], *, cwd: Path | None = None) -> None:
    print(f"> {' '.join(cmd)}")
    # On Windows, pnpm/vue-tsc/vite are .cmd shims that subprocess can't find
    # without a shell. On POSIX, run directly.
    r = subprocess.run(cmd, cwd=cwd or ROOT, shell=os.name == "nt")
    if r.returncode != 0:
        sys.exit(r.returncode)


def generate_favicons() -> None:
    if Image is None:
        print("WARNING: Pillow not installed — skipping favicon generation")
        return

    if not SOURCE_LOGO.exists():
        print(f"ERROR: source logo not found: {SOURCE_LOGO}")
        sys.exit(1)

    img = Image.open(SOURCE_LOGO).convert("RGBA")
    print(f"\n=== Generating favicons from {SOURCE_LOGO.name} ({img.size[0]}x{img.size[1]}) ===")

    sizes = {
        "favicon-16x16.png": 16,
        "favicon-32x32.png": 32,
        "favicon-48x48.png": 48,
        "android-chrome-192x192.png": 192,
        "android-chrome-512x512.png": 512,
        "apple-touch-icon.png": 180,
    }

    for filename, size in sizes.items():
        resized = img.resize((size, size), Image.LANCZOS)
        dst = DIST / filename
        resized.save(dst, "PNG")
        print(f"  {filename}")

    ico_sizes = [(16, 16), (32, 32), (48, 48)]
    ico_images = [img.resize(s, Image.LANCZOS) for s in ico_sizes]
    ico_images[0].save(
        DIST / "favicon.ico",
        format="ICO",
        sizes=ico_sizes,
        append_images=ico_images[1:],
    )
    print("  favicon.ico")

    manifest = {
        "name": "Minecraft Mod MCP",
        "short_name": "Mod MCP",
        "icons": [
            {"src": "android-chrome-192x192.png", "sizes": "192x192", "type": "image/png"},
            {"src": "android-chrome-512x512.png", "sizes": "512x512", "type": "image/png"},
        ],
        "theme_color": "#8b5cf6",
        "background_color": "#000000",
        "display": "standalone",
    }
    (DIST / "site.webmanifest").write_text(json.dumps(manifest, indent=2) + "\n")
    print("  site.webmanifest")


def copy_logos() -> None:
    print("\n=== Copying logos ===")
    logos_dst = DIST / "logos"
    if logos_dst.exists():
        shutil.rmtree(logos_dst)
    shutil.copytree(LOGOS, logos_dst)
    for f in sorted(logos_dst.iterdir()):
        print(f"  logos/{f.name}")


try:
    from PIL import Image
except ImportError:
    Image = None  # only needed for favicon generation


def main() -> None:
    parser = argparse.ArgumentParser(description="Build the Minecraft Mod MCP website.")
    parser.add_argument("--skip-deps", action="store_true", help="skip pnpm install")
    parser.add_argument("--skip-typecheck", action="store_true", help="skip vue-tsc")
    parser.add_argument("--skip-vite", action="store_true", help="skip vite build")
    args = parser.parse_args()

    if not args.skip_deps:
        print("=== Installing dependencies ===")
        run(["pnpm", "install", "--ignore-scripts"])

    if not args.skip_typecheck:
        print("\n=== Type checking ===")
        run(["npx", "vue-tsc", "-b", "--noEmit"])

    if not args.skip_vite:
        print("\n=== Building ===")
        run(["npx", "vite", "build"])

    generate_favicons()
    copy_logos()

    print("\n=== Copying CNAME ===")
    cname = ROOT / "CNAME"
    if cname.exists():
        shutil.copy2(cname, DIST / "CNAME")
        print("  CNAME")
    else:
        print("  (no CNAME — skipping; add one when binding a custom domain)")

    print("\n=== Creating .nojekyll ===")
    (DIST / ".nojekyll").write_text("\n")

    print(f"\n✓ Build complete → {DIST}")


if __name__ == "__main__":
    main()

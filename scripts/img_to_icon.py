#!/usr/bin/env python3
"""
img_to_icon.py - Convert an image into a Minecraft mod icon rendered via fill() calls.

Usage:
    python scripts/img_to_icon.py <input_image> [options]

Examples:
    python scripts/img_to_icon.py resume.png --size 16 --bg white
    python scripts/img_to_icon.py transfer.png --size 16 --threshold 30 --padding 3
    python scripts/img_to_icon.py icon.svg --format java --method resume
    python scripts/img_to_icon.py icon.png --format grid

Output formats:
    java   - Java source code for McpOverlayLogic (r.fill calls)
    grid   - Colored text grid for visual preview
    png    - Pixelated PNG texture file
"""

import argparse
import math
import os
import sys
from pathlib import Path

try:
    from PIL import Image
    import numpy as np
except ImportError:
    print("Requires Pillow and numpy: pip install Pillow numpy", file=sys.stderr)
    sys.exit(1)


BG_STRATEGIES = {
    "white": lambda r, g, b, a: (r > 220 and g > 220 and b > 220),
    "black": lambda r, g, b, a: (r < 35 and g < 35 and b < 35),
    "transparent": lambda r, g, b, a: (a < 30),
    "auto": None,
}


def detect_background(img):
    arr = np.array(img.convert("RGBA"))
    h, w = arr.shape[:2]
    edges = []
    for row in arr[0:2, :]:
        edges.extend(row.tolist())
    for row in arr[-2:, :]:
        edges.extend(row.tolist())
    for col in arr[:, 0:2]:
        edges.extend(col.tolist())
    for col in arr[:, -2:]:
        edges.extend(col.tolist())

    avg_r = sum(p[0] for p in edges) / len(edges)
    avg_g = sum(p[1] for p in edges) / len(edges)
    avg_b = sum(p[2] for p in edges) / len(edges)
    avg_a = sum(p[3] for p in edges) / len(edges)

    if avg_a < 80:
        return "transparent"
    if avg_r > 200 and avg_g > 200 and avg_b > 200:
        return "white"
    if avg_r < 60 and avg_g < 60 and avg_b < 60:
        return "black"
    return "auto_fallback"


def remove_background(img, strategy="auto", threshold=50):
    rgba = img.convert("RGBA")
    arr = np.array(rgba, dtype=np.int32)
    r, g, b, a = arr[:, :, 0], arr[:, :, 1], arr[:, :, 2], arr[:, :, 3]

    if strategy == "auto":
        strategy = detect_background(img)

    checker = BG_STRATEGIES.get(strategy, BG_STRATEGIES["transparent"])

    if strategy in ("white", "black"):
        if strategy == "white":
            dist = np.sqrt((r - 255) ** 2 + (g - 255) ** 2 + (b - 255) ** 2)
        else:
            dist = np.sqrt(r ** 2 + g ** 2 + b ** 2)
        mask_bg = dist < threshold
    elif strategy == "transparent":
        mask_bg = a < 30
    else:
        mask_bg = np.zeros_like(a, dtype=bool)

    arr[mask_bg, 3] = 0

    non_transparent = np.where(~mask_bg)
    if len(non_transparent[0]) == 0:
        return Image.fromarray(arr.astype(np.uint8))

    rows_crop = non_transparent[0]
    cols_crop = non_transparent[1]
    r0, r1 = rows_crop.min(), rows_crop.max() + 1
    c0, c1 = cols_crop.min(), cols_crop.max() + 1
    cropped = arr[r0:r1, c0:c1]

    return Image.fromarray(cropped.astype(np.uint8))


def pixelate(img, size):
    w, h = img.size
    if w == 0 or h == 0:
        return Image.new("RGBA", (size, size), (0, 0, 0, 0))
    small = img.resize((size, size), Image.LANCZOS)
    return small


def to_argb8888(r, g, b, a):
    return (a << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF)


def to_abgr8888(r, g, b, a):
    return (a << 24) | ((b & 0xFF) << 16) | ((g & 0xFF) << 8) | (r & 0xFF)


def extract_pixels(img, min_alpha=30):
    arr = np.array(img.convert("RGBA"))
    pixels = []
    for y in range(arr.shape[0]):
        for x in range(arr.shape[1]):
            r, g, b, a = int(arr[y, x, 0]), int(arr[y, x, 1]), int(arr[y, x, 2]), int(arr[y, x, 3])
            if a >= min_alpha:
                pixels.append((x, y, r, g, b, a))
    return pixels, arr.shape[1], arr.shape[0]


def optimize_runs(pixels, w, h):
    grid = {}
    for x, y, r, g, b, a in pixels:
        grid[(x, y)] = (r, g, b, a)

    visited = set()
    runs = []

    for y in range(h):
        x = 0
        while x < w:
            if (x, y) in visited or (x, y) not in grid:
                x += 1
                continue
            color = grid[(x, y)]
            rx = x
            while rx < w and (rx, y) not in visited and grid.get((rx, y)) == color:
                rx += 1
            run_width = rx - x
            run_h = 1
            done = False
            while not done:
                ry = y + run_h
                if ry >= h:
                    break
                ok = True
                for cx in range(x, rx):
                    if (cx, ry) in visited or grid.get((cx, ry)) != color:
                        ok = False
                        break
                if ok:
                    run_h += 1
                else:
                    done = True

            r, g, b, a = color
            runs.append((x, y, run_width, run_h, r, g, b, a))
            for dy in range(run_h):
                for dx in range(run_width):
                    visited.add((x + dx, y + dy))
            x = rx
        x = 0

    return runs


def format_java(runs, method_name, size, padding=3, btn_size=22):
    lines = []
    lines.append(f"    private static void draw{method_name}Icon(McpRenderer r, int bx, int by, int bw, int bh, int colorOverride) {{")

    if not runs:
        lines.append("        // empty icon")
        lines.append("    }")
        return "\n".join(lines)

    scale_x = f"(bw - {padding * 2}) / {size}"
    scale_y = f"(bh - {padding * 2}) / {size}"

    lines.append(f"        int s = Math.min(({scale_x}), ({scale_y}));")
    lines.append(f"        if (s < 1) s = 1;")
    lines.append(f"        int ox = bx + (bw - {size} * s) / 2;")
    lines.append(f"        int oy = by + (bh - {size} * s) / 2;")
    lines.append(f"        int c = colorOverride;")

    prev_color = None
    for rx, ry, rw, rh, r, g, b, a in runs:
        argb = to_argb8888(r, g, b, a)
        color_hex = f"0x{argb:08X}"
        if color_hex != prev_color:
            lines.append(f"        c = 0x{a:02X}{r:02X}{g:02X}{b:02X};")
            prev_color = color_hex
        lines.append(f"        r.fill(ox + {rx}*s, oy + {ry}*s, ox + {rx + rw}*s, oy + {ry + rh}*s, c);")

    lines.append("    }")
    return "\n".join(lines)


def format_grid(pixels, w, h):
    grid = {}
    for x, y, r, g, b, a in pixels:
        grid[(x, y)] = (r, g, b, a)

    lines = []
    for y in range(h):
        row = ""
        for x in range(w):
            c = grid.get((x, y))
            if c is None:
                row += "  .  "
            else:
                r, g, b, a = c
                row += f" {r:02x}{g:02x}{b:02x}"
        lines.append(row)
    return "\n".join(lines)


def format_runs_table(runs, size):
    lines = [f"    // Icon: {size}x{size}, {len(runs)} fill calls"]
    lines.append(f"    // Format: x, y, w, h, ARGB hex")
    for rx, ry, rw, rh, r, g, b, a in runs:
        argb = to_argb8888(r, g, b, a)
        lines.append(f"    //   fill({rx},{ry},{rw},{rh}, 0x{argb:08X})")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(
        description="Convert an image to a Minecraft mod icon (fill-based pixel art)"
    )
    parser.add_argument("input", help="Input image file (png, jpg, svg, etc.)")
    parser.add_argument("--size", type=int, default=16, help="Output pixel size (default: 16)")
    parser.add_argument("--bg", choices=["white", "black", "transparent", "auto"], default="auto",
                        help="Background removal strategy (default: auto)")
    parser.add_argument("--threshold", type=int, default=50,
                        help="Background color distance threshold (default: 50)")
    parser.add_argument("--min-alpha", type=int, default=30,
                        help="Minimum alpha to include pixel (default: 30)")
    parser.add_argument("--format", choices=["java", "grid", "png", "all"], default="all",
                        help="Output format (default: all)")
    parser.add_argument("--method", default="Custom",
                        help="Java method name suffix for icon (default: Custom)")
    parser.add_argument("--padding", type=int, default=3,
                        help="Padding inside button bounds (default: 3)")
    parser.add_argument("--btn-size", type=int, default=22,
                        help="Button size in pixels (default: 22)")
    parser.add_argument("--output", "-o", default=None,
                        help="Output file path (default: auto)")
    parser.add_argument("--no-optimize", action="store_true",
                        help="Disable run-length optimization (one fill per pixel)")
    args = parser.parse_args()

    if not os.path.exists(args.input):
        print(f"Error: file not found: {args.input}", file=sys.stderr)
        sys.exit(1)

    img = Image.open(args.input)
    print(f"[1/4] Loaded: {args.input} ({img.size[0]}x{img.size[1]})")

    cleaned = remove_background(img, strategy=args.bg, threshold=args.threshold)
    print(f"[2/4] Background removed ({args.bg}), content: {cleaned.size[0]}x{cleaned.size[1]}")

    pix = pixelate(cleaned, args.size)
    print(f"[3/4] Pixelated to {args.size}x{args.size}")

    pixels, w, h = extract_pixels(pix, min_alpha=args.min_alpha)
    print(f"[4/4] Extracted {len(pixels)} non-transparent pixels")

    if args.no_optimize:
        runs = [(x, y, 1, 1, r, g, b, a) for x, y, r, g, b, a in pixels]
    else:
        runs = optimize_runs(pixels, w, h)
    print(f"      Optimized to {len(runs)} fill() calls")

    stem = Path(args.input).stem

    if args.format in ("java", "all"):
        java_code = format_java(runs, args.method, args.size, args.padding, args.btn_size)
        out_file = args.output or f"{stem}_icon.java"
        if args.format == "all" and not args.output:
            out_file = f"{stem}_icon.java"
        with open(out_file, "w", encoding="utf-8") as f:
            f.write("// Auto-generated by img_to_icon.py\n")
            f.write(f"// Source: {args.input}, size={args.size}x{args.size}, {len(runs)} fills\n")
            f.write(format_runs_table(runs, args.size) + "\n")
            f.write(java_code + "\n")
        print(f"  Java -> {out_file}")

    if args.format in ("grid", "all"):
        grid_text = format_grid(pixels, w, h)
        out_file = f"{stem}_grid.txt"
        with open(out_file, "w", encoding="utf-8") as f:
            f.write(f"{w}x{h} pixel grid:\n")
            f.write(grid_text + "\n")
        print(f"  Grid -> {out_file}")

    if args.format in ("png", "all"):
        out_file = f"{stem}_pixelated.png"
        pix.save(out_file)
        print(f"  PNG  -> {out_file}")

    print("Done!")


if __name__ == "__main__":
    main()

"""Project cleanup — build caches, test artifacts, MC temp files.

Safety: Worlds require explicit --include-worlds flag.
         build caches and temp files are safe to delete.

Usage:
  python scripts/clean.py --dry-run           # report only, no deletion
  python scripts/clean.py                     # clean project + .minecraft temp (safe)
  python scripts/clean.py --include-worlds    # also list test worlds (requires confirm)
  python scripts/clean.py --all --force       # delete everything including worlds
"""

import argparse
import glob
import os
import shutil
import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parent.parent
MC_DIR = Path(os.environ.get("APPDATA", os.path.expanduser("~"))) / ".minecraft"

# ─── Categories ───────────────────────────────────────────

BUILD_CACHE = [
    ("project .gradle/",            PROJECT / ".gradle"),
    ("project .kotlin/",            PROJECT / ".kotlin"),
    ("project .cache/",             PROJECT / ".cache"),
    ("project bin/",                PROJECT / "bin"),
    ("project logs/",               PROJECT / "logs"),
    ("project out/",                PROJECT / "out"),
    ("project build/",              PROJECT / "build"),
    ("mcp-common .gradle/",         PROJECT / "mcp-common" / ".gradle"),
    ("mcp-common build/",           PROJECT / "mcp-common" / "build"),
    ("mcp-common .kotlin/",         PROJECT / "mcp-common" / ".kotlin"),
    ("mcp-common bin/",             PROJECT / "mcp-common" / "bin"),
]

TEMP_FILES = [
    ("project screenshots/",        PROJECT / "screenshots"),
    ("project logs/",               PROJECT / "logs"),
]

MC_TEMP = [
    ("MC logs/",                    MC_DIR / "logs"),
    ("MC crash-reports/",           MC_DIR / "crash-reports"),
    ("MC versions-natives/",        MC_DIR / "versions-natives"),
]

# Definite test patterns (named by our scripts)
TEST_WORLD_PATTERNS = [
    "TestWorld", "TestWorld*",
    "MCP_Test_World", "MCP_Test_World*",
    "mcp_test_*", "test_world_*",
    "smoke_test_*", "smoke_*",
]

# Possible test (default MC auto-names — could be real)
POSSIBLE_TEST_WORLD_PATTERNS = [
    "New World", "New World (*)",
    "新世界", "新世界 (*)",
]


def _size(path):
    """Total bytes of file or directory."""
    if not os.path.exists(str(path)):
        return 0
    if os.path.isfile(str(path)):
        return os.path.getsize(str(path))
    total = 0
    for root, dirs, files in os.walk(str(path)):
        for f in files:
            try:
                total += os.path.getsize(os.path.join(root, f))
            except OSError:
                pass
    return total


def size_fmt(path):
    """Human readable size."""
    b = _size(path)
    if b < 1024:
        return f"{b}B"
    elif b < 1024 * 1024:
        return f"{b / 1024:.1f}KB"
    elif b < 1024 * 1024 * 1024:
        return f"{b / (1024 * 1024):.1f}MB"
    else:
        return f"{b / (1024 * 1024 * 1024):.2f}GB"


def rm_rf(path):
    if not os.path.exists(str(path)):
        return
    for attempt in range(5):
        try:
            if os.path.isfile(str(path)):
                os.unlink(str(path))
            else:
                shutil.rmtree(str(path), ignore_errors=True)
            return
        except PermissionError:
            import time
            time.sleep(1)


def confirm(prompt, default=False):
    if not sys.stdin.isatty():
        print(f"  {prompt} [auto-skipped: no TTY]")
        return default
    try:
        ans = input(prompt).strip().lower()
        if ans in ("y", "yes"):
            return True
        if ans in ("n", "no"):
            return False
        return default
    except (EOFError, KeyboardInterrupt):
        print()
        return default


def find_mod_build_dirs():
    items = []
    mods_dir = PROJECT / "mods"
    if not mods_dir.is_dir():
        return items
    for mc_ver in sorted(mods_dir.iterdir()):
        if not mc_ver.is_dir():
            continue
        for loader in sorted(mc_ver.iterdir()):
            if not loader.is_dir():
                continue
            for sub in [".gradle", "build", ".kotlin", "bin", "out", "run"]:
                p = loader / sub
                if p.exists():
                    items.append((f"mods/{mc_ver.name}/{loader.name}/{sub}/", p))
    return items


def find_mc_launch_logs():
    items = []
    for pattern in ["mcp-launch-*.log", "mcp-debug*.log"]:
        for p in MC_DIR.glob(pattern):
            items.append((f"MC {p.name}", p))
    return items


def find_test_worlds():
    saves_dir = MC_DIR / "saves"
    if not saves_dir.is_dir():
        return []
    results = []
    for world_dir in sorted(saves_dir.iterdir()):
        if not world_dir.is_dir():
            continue
        name = world_dir.name
        for pattern in TEST_WORLD_PATTERNS:
            if world_dir.match(pattern):
                results.append((f"MC save: {name}", world_dir, True))
                break
        else:
            for pattern in POSSIBLE_TEST_WORLD_PATTERNS:
                if world_dir.match(pattern):
                    results.append((f"MC save: {name} [?]", world_dir, False))
                    break
    return results


def collect_all(include_worlds):
    safe = []
    worlds_definite = []
    worlds_possible = []

    safe.extend(find_mod_build_dirs())

    for label, path in BUILD_CACHE:
        if os.path.exists(str(path)):
            safe.append((label, path))
    for label, path in TEMP_FILES:
        if os.path.exists(str(path)):
            safe.append((label, path))
    for label, path in MC_TEMP:
        if os.path.exists(str(path)):
            safe.append((label, path))
    safe.extend(find_mc_launch_logs())

    mc_mods_dir = MC_DIR / "mods"
    if mc_mods_dir.is_dir():
        for jar in mc_mods_dir.glob("*mcp*"):
            safe.append((f"MC mods/{jar.name}", jar))
        for jar in mc_mods_dir.glob("minecraft-moddev-mcp-*"):
            safe.append((f"MC mods/{jar.name}", jar))

    if include_worlds:
        for label, path, is_definite in find_test_worlds():
            if is_definite:
                worlds_definite.append((label, path))
            else:
                worlds_possible.append((label, path))

    return safe, worlds_definite, worlds_possible


def delete_items(items, dry_run):
    total = 0
    for label, path in items:
        sz = size_fmt(path)
        if dry_run:
            print(f"  [WOULD DELETE] {label} ({sz})")
        else:
            total += _size(path)
            print(f"  Deleting {label} ({sz})...", end=" ", flush=True)
            rm_rf(path)
            print("done")
    return total


def main():
    parser = argparse.ArgumentParser(description="Clean project build artifacts and test data")
    parser.add_argument("--dry-run", action="store_true", help="Report only, no deletion")
    parser.add_argument("--include-worlds", action="store_true", help="Detect test MC worlds")
    parser.add_argument("--all", action="store_true", help="Clean everything including all worlds")
    parser.add_argument("--force", "-f", action="store_true", help="Skip confirmation prompts")
    args = parser.parse_args()

    safe, worlds_definite, worlds_possible = collect_all(
        args.include_worlds or args.all
    )

    if not safe and not worlds_definite and not worlds_possible:
        print("Nothing to clean.")
        return

    mode = "DRY-RUN" if args.dry_run else "CLEAN"
    total_freed = 0

    # ── Safe items ──
    print(f"\n{'='*55}")
    print(f"  [{mode}] Build caches & temp files ({len(safe)} items)")
    print(f"{'='*55}")
    for label, path in safe:
        print(f"  {label:50s} {size_fmt(path):>10s}")
    print(f"  {'─'*55}")
    total_freed += delete_items(safe, args.dry_run)
    print()

    # ── Definite test worlds ──
    if worlds_definite:
        print(f"{'='*55}")
        print(f"  [{mode}] Definite test worlds ({len(worlds_definite)} items)")
        print(f"{'='*55}")
        for label, path in worlds_definite:
            print(f"  {label}  ({size_fmt(path)})")
        print()

        if not args.dry_run:
            if args.force or args.all or confirm("  Delete these test worlds? [y/N] "):
                total_freed += delete_items(worlds_definite, False)
            else:
                print("  Skipped.")

    # ── Possible test worlds ──
    if worlds_possible:
        print(f"\n{'='*55}")
        print(f"  [{mode}] Possible test worlds (default MC names — may be real)")
        print(f"  ({len(worlds_possible)} items, review manually)")
        print(f"{'='*55}")
        for label, path in worlds_possible:
            print(f"  {label:50s} {size_fmt(path):>10s}")
        print()

        if not args.dry_run:
            if args.all and args.force:
                total_freed += delete_items(worlds_possible, False)
            elif args.all and confirm("  Delete these worlds too? [y/N] "):
                total_freed += delete_items(worlds_possible, False)
            else:
                print("  Skipped. Use --all --force to delete, or delete manually.")

    if args.dry_run:
        print("Dry-run complete. No files were deleted.")
    else:
        print(f"\nCleanup complete. Freed ~{total_freed / (1024 * 1024):.1f}MB total.")


if __name__ == "__main__":
    main()

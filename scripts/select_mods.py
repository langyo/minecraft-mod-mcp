"""Pick which mod modules a CI run has to build.

Pushes and manual runs always build everything (the smoke/release jobs consume
every jar). Pull requests only build the modules whose files changed; changes
to shared inputs (common sources, generator, root Gradle files, this CI
pipeline) rebuild everything, and PRs touching neither are skipped. Any doubt
(no base commit, git failure) falls back to building everything.

Usage:
  python scripts/select_mods.py --event pull_request --base <sha> \
      --mods-file all-mods.txt --out selected-mods.txt
"""
import argparse
import os
import subprocess
import sys

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SHARED_PREFIXES = (
    "packages/common/",
    "gradle/",
    "scripts/modgen/",
    ".github/workflows/ci.yml",
    "scripts/generate_mods.py",
    "scripts/version_config.py",
    "scripts/prepare_cache.py",
    "scripts/wrapper_jars.py",
    "scripts/mirrors.py",
    "settings.gradle",
    "build.gradle",
)


def select_mods(changed_files, mods):
    """Return the subset of `mods` ("<mc>/<loader>") affected by the changes."""
    files = [f.replace("\\", "/") for f in changed_files]
    if any(f.startswith(SHARED_PREFIXES) for f in files):
        return list(mods)
    return [m for m in mods if any(f.startswith(f"packages/mods/{m}/") for f in files)]


def changed_files_since(base):
    subprocess.run(
        ["git", "fetch", "--no-tags", "--depth=1", "origin", base],
        cwd=BASE, check=True, capture_output=True,
    )
    out = subprocess.run(
        ["git", "diff", "--name-only", base, "HEAD"],
        cwd=BASE, check=True, capture_output=True, text=True,
    ).stdout
    return [line for line in out.splitlines() if line.strip()]


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--event", required=True)
    parser.add_argument("--base", default="")
    parser.add_argument("--mods-file", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    with open(args.mods_file, encoding="utf-8") as f:
        mods = [line.strip() for line in f if line.strip()]

    selected = mods
    if args.event == "pull_request" and args.base:
        try:
            selected = select_mods(changed_files_since(args.base), mods)
        except (subprocess.CalledProcessError, OSError) as e:
            print(f"select_mods: could not diff against base ({e}); building all")

    with open(args.out, "w", encoding="utf-8", newline="\n") as f:
        f.write("".join(m + "\n" for m in selected))
    print(f"Selected {len(selected)}/{len(mods)} mod(s)")
    for m in selected:
        print(f"  {m}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

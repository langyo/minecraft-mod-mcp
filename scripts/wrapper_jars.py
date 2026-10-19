"""Provision gradle-wrapper.jar for every mod module from the single root copy.

All modules used to commit a byte-identical gradle-wrapper.jar (100+ copies).
Only the root gradle/wrapper/gradle-wrapper.jar is tracked now; module copies
are git-ignored and (re)created by this helper. Build entry points call
ensure_wrapper_jars() so `gradlew` keeps working inside every module.

Usage:
  python scripts/wrapper_jars.py
"""
import os
import shutil
import sys

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT_JAR = os.path.join(BASE, "gradle", "wrapper", "gradle-wrapper.jar")
MODS_DIR = os.path.join(BASE, "packages", "mods")


def _same(a, b):
    return os.path.getsize(a) == os.path.getsize(b) and open(a, "rb").read() == open(b, "rb").read()


def ensure_wrapper_jars(mods_dir=MODS_DIR, root_jar=ROOT_JAR):
    """Copy the root wrapper jar into every module that has a wrapper.properties.

    Returns the number of jars written.
    """
    if not os.path.isfile(root_jar):
        print(f"WARNING: {root_jar} missing, cannot provision module wrapper jars")
        return 0
    written = 0
    for dirpath, dirnames, filenames in os.walk(mods_dir):
        dirnames[:] = [d for d in dirnames if d not in ("build", ".gradle", "src", "node_modules")]
        if "gradle-wrapper.properties" not in filenames:
            continue
        dst = os.path.join(dirpath, "gradle-wrapper.jar")
        if os.path.isfile(dst) and _same(root_jar, dst):
            continue
        shutil.copyfile(root_jar, dst)
        written += 1
    return written


if __name__ == "__main__":
    print(f"Provisioned {ensure_wrapper_jars()} wrapper jar(s)")
    sys.exit(0)

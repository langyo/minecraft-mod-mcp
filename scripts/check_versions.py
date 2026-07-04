"""Pre-release version consistency check.

Verifies that all mod version strings across the project match the
release tag version. Run automatically in CI before building.

Usage:
    python scripts/check_versions.py <tag_version>
    python scripts/check_versions.py v0.1.1
    python scripts/check_versions.py 0.1.1
"""
import os
import re
import sys
import json
from pathlib import Path


BASE = Path(__file__).resolve().parent.parent
MODS_DIR = BASE / "packages" / "mods"


def strip_v_prefix(version: str) -> str:
    return version[1:] if version.startswith("v") else version


def base_version(tag: str) -> str:
    """Strip the leading 'v' and any prerelease suffix (-rc1, -beta.2, etc.)
    so a prerelease tag like 'v0.2.1-rc1' checks against mod version '0.2.1'.
    The npm/GitHub workflows publish the full tag as the version, but the mod
    sources only carry the base version."""
    v = strip_v_prefix(tag)
    # Split off prerelease: everything after the first '-' that isn't part of
    # the MC version (MC versions use dots, not hyphens). The suffix may be
    # immediately followed by a digit (rc1, beta2), so don't require a boundary.
    return re.split(r"-(?:rc|beta|alpha|pre|snapshot)\d*", v, maxsplit=1, flags=re.IGNORECASE)[0]


def fail(msg: str):
    print(f"::error::{msg}")
    raise SystemExit(1)


def check_file(path: Path, pattern: str, expected: str, label: str) -> list[str]:
    """Check a file for version strings matching the given pattern."""
    errors = []
    try:
        content = path.read_text(encoding="utf-8")
    except Exception as e:
        errors.append(f"{path}: cannot read ({e})")
        return errors

    matches = re.findall(pattern, content)
    for match in matches:
        actual = match if isinstance(match, str) else match[0]
        actual_clean = actual.strip("\"' ")
        if actual_clean != expected:
            errors.append(
                f"{path}: expected version '{expected}', found '{actual_clean}' ({label})"
            )
    return errors


def _is_build_artifact(path: Path) -> bool:
    """True for generated build outputs (build/, .gradle/) — skip these so the
    check passes locally with stale artifacts present (CI checks out clean)."""
    return any(part in ("build", ".gradle", "node_modules") for part in path.parts)


def check_build_gradle(expected: str) -> list[str]:
    """Check all build.gradle / build.gradle.kts for version strings."""
    errors = []
    # Old forge (groovy): version = "0.1.1-SNAPSHOT"
    pattern_groovy = r'version\s*=\s*["\']([^"\']+)["\']'

    # New forge/neoforge/fabric: version = '0.1.1-SNAPSHOT'
    # Combined pattern catches both
    for path in MODS_DIR.rglob("build.gradle*"):
        if _is_build_artifact(path):
            continue
        # Skip non-mod build files
        parts = path.parts
        try:
            mods_idx = parts.index("mods")
            if len(parts) - mods_idx < 3:
                continue
        except ValueError:
            continue

        content = path.read_text(encoding="utf-8")
        for m in re.finditer(pattern_groovy, content):
            actual = m.group(1)
            if "-" in actual and not actual.startswith("1.0"):
                continue
            line_start = content.rfind("\n", 0, m.start()) + 1
            line = content[line_start:m.end()]
            block_start = content.rfind("\n", 0, m.start()) + 1
            preceding = content[max(0, block_start - 200):block_start]
            if "neoforge {" in preceding.lower() or "neoforged" in preceding.lower():
                continue
            if "minecraft" in line.lower() and "version" in line.lower():
                if "net.minecraftforge" not in line.lower():
                    continue
            if actual != expected:
                errors.append(
                    f"{path.relative_to(BASE)}: expected '{expected}', found '{actual}'"
                )

    # Common library
    common_path = BASE / "packages" / "common" / "build.gradle.kts"
    if common_path.exists():
        content = common_path.read_text(encoding="utf-8")
        m = re.search(r'version\s*=\s*"([^"]+)"', content)
        if m and m.group(1) != expected:
            errors.append(
                f"packages/common/build.gradle.kts: expected '{expected}', found '{m.group(1)}'"
            )

    return errors


def check_metadata_json(paths_glob: str, expected: str, label: str) -> list[str]:
    """Check JSON metadata files for version field."""
    errors = []
    for path in MODS_DIR.rglob(paths_glob):
        if _is_build_artifact(path):
            continue
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue

        if isinstance(data, list):
            for entry in data:
                if isinstance(entry, dict) and "version" in entry:
                    actual = entry["version"]
                    if actual != expected:
                        errors.append(
                            f"{path.relative_to(BASE)}: {label} version expected "
                            f"'{expected}', found '{actual}'"
                        )
        elif isinstance(data, dict) and "version" in data:
            actual = data["version"]
            # fabric.mod.json "version" can be string or object (when multi-lang name is used)
            if isinstance(actual, str) and actual != expected:
                errors.append(
                    f"{path.relative_to(BASE)}: {label} version expected "
                    f"'{expected}', found '{actual}'"
                )
    return errors


def check_mods_toml(expected: str) -> list[str]:
    """Check mods.toml / neoforge.mods.toml files for version=."""
    errors = []
    pattern = r'version\s*=\s*"([^"]+)"'
    for path in MODS_DIR.rglob("mods.toml"):
        if _is_build_artifact(path):
            continue
        errors.extend(check_file(path, pattern, expected, "mods.toml"))
    for path in MODS_DIR.rglob("neoforge.mods.toml"):
        if _is_build_artifact(path):
            continue
        errors.extend(check_file(path, pattern, expected, "neoforge.mods.toml"))
    return errors


def check_java_annotations(expected: str) -> list[str]:
    """Check ModDevMcpMod.java @Mod annotations for version=."""
    errors = []
    pattern = r'@Mod\([^)]*version\s*=\s*"([^"]+)"'
    for path in MODS_DIR.rglob("ModDevMcpMod.java"):
        if _is_build_artifact(path):
            continue
        errors.extend(check_file(path, pattern, expected, "@Mod annotation"))
    return errors


def check_cargo_toml() -> list[str]:
    """Check root Cargo.toml workspace version (informational only)."""
    cargo = BASE / "Cargo.toml"
    if not cargo.exists():
        return []
    content = cargo.read_text(encoding="utf-8")
    m = re.search(r'\[workspace\.package\]\s*\n(?:[^\[]*\n)*?version\s*=\s*"([^"]+)"', content)
    if m:
        server_ver = m.group(1)
        print(f"  Server version (Cargo.toml): {server_ver} (independent from mod versions)")
    return []


def main():
    if len(sys.argv) < 2:
        print("Usage: python scripts/check_versions.py <tag_version>")
        sys.exit(1)

    tag = base_version(sys.argv[1])
    print(f"=== Version Gate: checking all mods for version '{tag}' ===\n")

    all_errors = []

    print("[1/5] Checking build.gradle files...")
    errs = check_build_gradle(tag)
    all_errors.extend(errs)
    for e in errs:
        print(f"  FAIL {e}")
    print(f"  {len(errs)} build.gradle errors")

    print("\n[2/5] Checking mod metadata (mcmod.info, fabric.mod.json)...")
    errs = check_metadata_json("mcmod.info", tag, "mcmod.info")
    errs += check_metadata_json("fabric.mod.json", tag, "fabric.mod.json")
    all_errors.extend(errs)
    for e in errs:
        print(f"  FAIL {e}")
    print(f"  {len(errs)} metadata errors")

    print("\n[3/5] Checking mods.toml / neoforge.mods.toml...")
    errs = check_mods_toml(tag)
    all_errors.extend(errs)
    for e in errs:
        print(f"  FAIL {e}")
    print(f"  {len(errs)} mods.toml errors")

    print("\n[4/5] Checking @Mod annotations...")
    errs = check_java_annotations(tag)
    all_errors.extend(errs)
    for e in errs:
        print(f"  FAIL {e}")
    print(f"  {len(errs)} annotation errors")

    print("\n[5/5] Checking Cargo.toml (informational)...")
    check_cargo_toml()

    print(f"\n{'=' * 60}")
    if all_errors:
        print(f"VERSION GATE FAILED: {len(all_errors)} version mismatches found")
        print("All mod versions must match the release tag before building.")
        print("Run: python scripts/check_versions.py {tag} to re-check after fixing.")
        sys.exit(1)
    else:
        print("VERSION GATE PASSED: all mod versions match the tag")
        sys.exit(0)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Enforce import statement layout rules for Rust (.rs) and TypeScript (.ts/.tsx).

Rust rules (unchanged from entelecheia):
  1. Group `use` into: std/util (1), external (2), workspace/internal (3).
  2. Blank line between groups.
  3. In mod.rs / lib.rs / main.rs, emit `mod` before `use`.
  4. Merge same-prefix paths into braced groups.

TypeScript rules (mirrors Rust grouping):
  1. Group `import` into: runtime/platform (1), external packages (2),
     workspace-internal (3: `@/`, `@celestia-island/`, `./`, `../`, `.module.scss`).
  2. Blank line between groups.
  3. Sort alphabetically within each group (case-insensitive).
  4. Merge same-source default + named imports (best-effort).

  Type-only imports (`import type { ... }`) are kept adjacent to their
  corresponding value import from the same module when possible.
"""

from __future__ import annotations

import os
import re
import sys
import shutil
import subprocess
from collections import OrderedDict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

import cli_format as cf

try:
    import tomllib
except ModuleNotFoundError:
    import tomli as tomllib

# ── Rust shared-utility crates (group 1) ──────────────────────────────

GROUP1_CRATES = {
    "std", "core", "alloc", "anyhow", "serde", "serde_json", "serde_yaml",
    "serde_repr", "serde_with", "serde_bytes", "serde_path_to_error",
    "toml", "ron", "regex", "lazy_static", "once_cell", "tokio", "futures",
    "async_std", "chrono", "log", "uuid", "rand", "base64", "bytes",
    "cfg_if", "parking_lot", "url", "rayon", "snap", "strum",
    "sea_orm", "sea_orm_migration", "axum", "reqwest", "tower", "tower_http",
    "clap", "thiserror", "tracing", "tracing_subscriber", "dotenvy",
    "include_dir", "dashmap", "aes_gcm", "argon2", "jsonwebtoken", "kirino",
    "hmac", "hex", "sha2", "tungstenite", "tokio_tungstenite", "tokio_stream",
    "futures", "async_trait",
}

# ── TypeScript group-1 packages (runtime / platform) ──────────────────

TS_GROUP1 = {
    "vue", "pinia", "vue-router", "@vueuse/core",
    "vite", "@vitejs/plugin-vue-jsx",
    "typescript",
}

# ── Regexes ───────────────────────────────────────────────────────────

USE_RE = re.compile(r"^\s*(pub\s+)?use\b")
MOD_RE = re.compile(r"^\s*(pub\s+)?mod\b")
ATTR_RE = re.compile(r"^\s*#\[")
COMMENT_RE = re.compile(r"^\s*//")
BLOCK_COMMENT_START_RE = re.compile(r"^\s*/\*")

TS_IMPORT_RE = re.compile(
    r"^\s*import\s+(type\s+)?(.+?)\s+from\s+['\"](.+?)['\"]\s*;?\s*$"
)
TS_SIDE_EFFECT_RE = re.compile(r"^\s*import\s+['\"](.+?)['\"]\s*;?\s*$")

# ── Workspace crate discovery ─────────────────────────────────────────

WORKSPACE_CRATES: set[str] = set()


def load_workspace_crates(root: Path) -> set[str]:
    crates: set[str] = set()
    for dirpath, dirnames, filenames in os.walk(root):
        if "target" in dirnames:
            dirnames.remove("target")
        if "node_modules" in dirnames:
            dirnames.remove("node_modules")
        if "Cargo.toml" not in filenames:
            continue
        path = Path(dirpath) / "Cargo.toml"
        try:
            with path.open("rb") as fh:
                data = tomllib.load(fh)
        except Exception:
            continue
        package = data.get("package")
        if package and "name" in package:
            crates.add(package["name"])
    return crates


# ═══════════════════════════════════════════════════════════════════════
# Rust processing (unchanged logic)
# ═══════════════════════════════════════════════════════════════════════

@dataclass
class UseStatement:
    lines: List[str]
    path: Optional[str] = None
    is_pub: bool = False
    group: int = 2
    simple_prefix: Optional[str] = None
    simple_leaf: Optional[str] = None
    has_attrs: bool = False
    merge_base: Optional[str] = None
    merge_remainder: Optional[str] = None

    def text(self) -> str:
        return "".join(self.lines)


@dataclass
class Statement:
    kind: str
    lines: List[str]
    use_stmt: Optional[UseStatement] = None


def strip_line_comments(line: str) -> str:
    idx = line.find("//")
    if idx == -1:
        return line
    before = line[:idx]
    after = line[idx:]
    suffix = ""
    open_b = before.count("{")
    close_b = before.count("}")
    if "}" in after and open_b > close_b:
        suffix += "}" * (open_b - close_b)
    if ";" in after:
        suffix += ";"
    result = before.rstrip()
    if suffix:
        result += suffix
    return result


def extract_use_path(lines: Sequence[str]) -> Optional[str]:
    cleaned = []
    for line in lines:
        if ATTR_RE.match(line):
            continue
        c = strip_line_comments(line).strip()
        if c:
            cleaned.append(c)
    joined = " ".join(cleaned)
    m = re.search(r"\buse\s+([^;]+);", joined)
    return m.group(1).strip() if m else None


def extract_base_crate(path: str) -> str:
    token = path.strip()
    if token.startswith("pub "):
        token = token[4:].strip()
    if token.startswith("use "):
        token = token[4:].strip()
    token = token.lstrip(":").strip()
    m = re.match(r'^([a-zA-Z_][a-zA-Z0-9_]*)', token)
    return m.group(1) if m else ""


def classify_use(path: Optional[str], workspace_crates: set[str]) -> int:
    if not path:
        return 2
    base = extract_base_crate(path)
    if not base:
        return 2
    if base in ("crate", "self", "super"):
        return 3
    if base.startswith("_"):
        return 3
    if base in workspace_crates:
        return 3
    if base in GROUP1_CRATES:
        return 1
    return 2


def compute_simple_components(path: Optional[str], has_attrs: bool) -> Tuple[Optional[str], Optional[str]]:
    if has_attrs or not path:
        return None, None
    token = path.strip()
    if any(ch in token for ch in "{}*"):
        return None, None
    if " as " in token:
        return None, None
    if token.endswith(":"):
        return None, None
    parts = token.split("::")
    if len(parts) < 2:
        return None, None
    prefix = "::".join(parts[:-1])
    leaf = parts[-1].strip()
    if not prefix or not leaf:
        return None, None
    return prefix, leaf


def compute_merge_components(path: Optional[str], has_attrs: bool) -> Tuple[Optional[str], Optional[str]]:
    if has_attrs or not path:
        return None, None
    base = extract_base_crate(path)
    if not base:
        return None, None
    token = path.strip()
    if token.startswith("pub "):
        token = token[4:].strip()
    if token.startswith("use "):
        token = token[4:].strip()
    token = token.lstrip(":").strip()
    if token.startswith(base):
        if len(token) > len(base) and token[len(base):len(base)+2] == "::":
            return base, token[len(base)+2:]
        elif len(token) == len(base):
            return base, ""
    return None, None


def append_blank_line(buf: List[str]) -> None:
    if buf and buf[-1].strip():
        buf.append("\n")


def collect_statement(lines: List[str], idx: int) -> Tuple[Optional[Statement], int]:
    attrs: List[str] = []
    cur = idx
    while cur < len(lines) and ATTR_RE.match(lines[cur]):
        attrs.append(lines[cur])
        cur += 1
    if cur >= len(lines):
        return None, idx
    line = lines[cur]
    if USE_RE.match(line):
        stmt_lines = attrs + [line]
        cur += 1
        brace_balance = line.count("{") - line.count("}")
        semi = ";" in line and brace_balance <= 0
        while not semi and cur < len(lines):
            stmt_lines.append(lines[cur])
            brace_balance += lines[cur].count("{") - lines[cur].count("}")
            if ";" in lines[cur] and brace_balance <= 0:
                semi = True
            cur += 1
        return Statement("use", stmt_lines, build_use_statement(stmt_lines)), cur
    if MOD_RE.match(line):
        if "{" in line and "}" not in line:
            return None, idx
        stmt_lines = attrs + [line]
        return Statement("mod", stmt_lines), idx + 1
    return None, idx


def build_use_statement(lines: List[str]) -> UseStatement:
    code_lines = [l for l in lines if not ATTR_RE.match(l)]
    path = extract_use_path(lines)
    is_pub = bool(code_lines and code_lines[0].lstrip().startswith("pub "))
    has_attrs = any(ATTR_RE.match(l) for l in lines)
    prefix, leaf = compute_simple_components(path, has_attrs)
    merge_base, merge_rem = compute_merge_components(path, has_attrs)
    group = classify_use(path, WORKSPACE_CRATES)
    return UseStatement(lines, path, is_pub, group, prefix, leaf, has_attrs, merge_base, merge_rem)


def normalize_remainder_items(remainder: str) -> List[str]:
    remainder = remainder.strip()
    if remainder.startswith("{") and remainder.endswith("}"):
        inner = remainder[1:-1]
        parts: List[str] = []
        cur: List[str] = []
        depth = 0
        for ch in inner:
            if ch == '{':
                depth += 1
                cur.append(ch)
            elif ch == '}':
                depth -= 1
                cur.append(ch)
            elif ch == ',' and depth == 0:
                part = ''.join(cur).strip()
                if part:
                    parts.append(part)
                cur = []
            else:
                cur.append(ch)
        last = ''.join(cur).strip()
        if last:
            parts.append(last)
        return parts
    if remainder == "":
        return []
    return [remainder]


def find_matching_brace(s: str, start: int) -> int:
    if start >= len(s) or s[start] != '{':
        return -1
    depth = 0
    for i in range(start, len(s)):
        if s[i] == '{':
            depth += 1
        elif s[i] == '}':
            depth -= 1
            if depth == 0:
                return i
    return -1


def expand_braced_item(item: str) -> List[str]:
    if item == "":
        return [""]
    brace_start = item.find('{')
    if brace_start == -1:
        return [item]
    if brace_start < 2 or item[brace_start-2:brace_start] != "::":
        return [item]
    brace_end = find_matching_brace(item, brace_start)
    if brace_end == -1:
        return [item]
    prefix = item[:brace_start-2]
    braced_content = item[brace_start:brace_end+1]
    suffix = item[brace_end+1:]
    inner_items = normalize_remainder_items(braced_content)
    expanded: List[str] = []
    for inner in inner_items:
        new_item = prefix + suffix if inner == "" else f"{prefix}::{inner}{suffix}"
        expanded.extend(expand_braced_item(new_item))
    return expanded


def expand_braced_items(items: List[str]) -> List[str]:
    expanded: List[str] = []
    for item in items:
        expanded.extend(expand_braced_item(item))
    return expanded


def build_import_tree(items: List[str]) -> dict:
    expanded = expand_braced_items(items)
    tree: dict = {}
    for item in expanded:
        if item == "":
            tree[""] = {}
            continue
        parts = item.split("::")
        current = tree
        for part in parts:
            if part not in current:
                current[part] = {}
            current = current[part]
    for item in expanded:
        if item == "":
            continue
        parts = item.split("::")
        current = tree
        for part in parts:
            current = current[part]
        if current:
            current[""] = {}
    return tree


def format_import_tree(tree: dict) -> str:
    if not tree:
        return ""
    has_self = "" in tree
    keys = sorted([k for k in tree.keys() if k != ""])
    if not keys:
        return ""
    if len(keys) == 1 and not tree[keys[0]] and not has_self:
        return keys[0]
    parts: List[str] = []
    if has_self:
        parts.append("self")
    for key in keys:
        subtree = tree[key]
        if not subtree:
            parts.append(key)
        else:
            child = format_import_tree(subtree)
            parts.append(f"{key}::{child}" if child else key)
    if len(parts) == 1 and not has_self:
        return parts[0]
    return "{" + ", ".join(parts) + "}"


def find_common_prefix(paths: List[str]) -> str:
    if not paths:
        return ""
    if len(paths) == 1:
        return "::".join(paths[0].split("::")[:-1]) if "::" in paths[0] else ""
    all_parts = [p.split("::") if p else [] for p in paths]
    if not all_parts or not all(parts for parts in all_parts):
        return ""
    prefix_parts: List[str] = []
    for i in range(min(len(parts) for parts in all_parts)):
        part = all_parts[0][i]
        if all(parts[i] == part for parts in all_parts):
            prefix_parts.append(part)
        else:
            break
    return "::".join(prefix_parts)


def flush_merged_groups(pending: OrderedDict, output: List[str]) -> None:
    for (is_pub, base), items in pending.items():
        if not items:
            continue
        common = find_common_prefix(items)
        if common:
            new_base = f"{base}::{common}"
            new_items: List[str] = []
            for item in items:
                if item == "" or item == common:
                    new_items.append("")
                elif item.startswith(common + "::"):
                    new_items.append(item[len(common)+2:])
                else:
                    new_items.append(item)
            tree = build_import_tree(new_items)
            fmt = format_import_tree(tree)
            line = f"{'pub ' if is_pub else ''}use {new_base}::{fmt};\n" if fmt else f"{'pub ' if is_pub else ''}use {new_base};\n"
        else:
            tree = build_import_tree(items)
            fmt = format_import_tree(tree)
            line = f"{'pub ' if is_pub else ''}use {base}::{fmt};\n" if fmt else f"{'pub ' if is_pub else ''}use {base};\n"
        output.append(line)
    pending.clear()


def render_group(statements: List[UseStatement]) -> List[str]:
    if not statements:
        return []
    output: List[str] = []
    pending: OrderedDict[Tuple[bool, str], List[str]] = OrderedDict()
    for stmt in statements:
        if stmt.merge_base and stmt.merge_remainder is not None and not stmt.has_attrs:
            key = (stmt.is_pub, stmt.merge_base)
            if key not in pending:
                pending[key] = []
            items = normalize_remainder_items(stmt.merge_remainder)
            pending[key].extend(items if items else [""])
            continue
        flush_merged_groups(pending, output)
        output.extend(stmt.lines)
    flush_merged_groups(pending, output)
    return output


def render_use_section(use_stmts: List[UseStatement]) -> List[str]:
    grouped: Dict[int, List[UseStatement]] = {1: [], 2: [], 3: []}
    for stmt in use_stmts:
        grouped[stmt.group].append(stmt)
    rendered: List[str] = []
    for g in (1, 2, 3):
        block = render_group(grouped[g])
        if not block:
            continue
        if rendered and rendered[-1].strip():
            rendered.append("\n")
        rendered.extend(block)
    if rendered and rendered[-1].strip():
        rendered.append("\n")
    return rendered


def process_rust_file(path: Path) -> Optional[str]:
    lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
    idx = 0
    prefix: List[str] = []
    while idx < len(lines):
        stripped = lines[idx].strip()
        if stripped == "" or COMMENT_RE.match(lines[idx]) or BLOCK_COMMENT_START_RE.match(lines[idx]):
            prefix.append(lines[idx])
            idx += 1
            continue
        if lines[idx].startswith("#!"):
            prefix.append(lines[idx])
            idx += 1
            continue
        if ATTR_RE.match(lines[idx]):
            break
        if USE_RE.match(lines[idx]) or MOD_RE.match(lines[idx]):
            break
        return None
    statements: List[Statement] = []
    cur = idx
    while cur < len(lines):
        stmt, next_idx = collect_statement(lines, cur)
        if stmt is None:
            break
        statements.append(stmt)
        cur = next_idx
        while cur < len(lines) and lines[cur].strip() == "":
            cur += 1
    suffix = lines[cur:]
    use_stmts = [s.use_stmt for s in statements if s.kind == "use" and s.use_stmt]
    if not use_stmts:
        return None
    use_section = render_use_section(use_stmts)
    if not use_section:
        return None
    new_lines: List[str] = list(prefix)
    if path.name in {"mod.rs", "lib.rs", "main.rs"}:
        mods = [s.lines for s in statements if s.kind == "mod"]
        for m in mods:
            new_lines.extend(m)
        if mods and use_section:
            append_blank_line(new_lines)
        new_lines.extend(use_section)
    else:
        others = False
        for s in statements:
            if s.kind != "use":
                new_lines.extend(s.lines)
                others = True
        if others and use_section:
            append_blank_line(new_lines)
        new_lines.extend(use_section)
    new_lines.extend(suffix)
    if new_lines and not new_lines[-1].endswith("\n"):
        new_lines[-1] += "\n"
    new_text = "".join(new_lines)
    original = "".join(lines)
    return new_text if new_text != original else None


# ═══════════════════════════════════════════════════════════════════════
# TypeScript processing
# ═══════════════════════════════════════════════════════════════════════

@dataclass
class TsImport:
    original: str
    is_type_only: bool
    importee: str          # what is imported
    source: str            # the module path string
    group: int

    def sort_key(self) -> str:
        return self.source.lower()


def classify_ts_source(source: str) -> int:
    if source.startswith("./") or source.startswith("../"):
        return 3
    if source.startswith("@/"):
        return 3
    if source.startswith("@celestia-island/"):
        return 3
    if source.endswith(".module.scss") or source.endswith(".scss") or source.endswith(".css"):
        return 3
    base = source.split("/")[0]
    if base in TS_GROUP1:
        return 1
    return 2


def parse_ts_imports(lines: List[str]) -> List[TsImport]:
    imports: List[TsImport] = []
    for line in lines:
        stripped = line.strip()
        if not stripped:
            continue
        m = TS_IMPORT_RE.match(stripped)
        if m:
            is_type = bool(m.group(1))
            importee = m.group(2)
            source = m.group(3)
            group = classify_ts_source(source)
            imports.append(TsImport(stripped, is_type, importee, source, group))
            continue
        m2 = TS_SIDE_EFFECT_RE.match(stripped)
        if m2:
            source = m2.group(1)
            group = classify_ts_source(source)
            imports.append(TsImport(stripped, False, "", source, group))
    return imports


def format_ts_import(imp: TsImport) -> str:
    return imp.original


def render_ts_imports(imports: List[TsImport]) -> List[str]:
    groups: Dict[int, List[TsImport]] = {1: [], 2: [], 3: []}
    for imp in imports:
        groups[imp.group].append(imp)
    for g in groups:
        groups[g].sort(key=lambda i: (not i.is_type_only, i.sort_key()))
    lines: List[str] = []
    for g in (1, 2, 3):
        group = groups[g]
        if not group:
            continue
        if lines and lines[-1].strip():
            lines.append("")
        for imp in group:
            lines.append(format_ts_import(imp))
    return lines


def process_ts_file(path: Path) -> Optional[str]:
    content = path.read_text(encoding="utf-8")
    lines = content.split("\n")
    import_start = None
    import_end = None

    # Find the import block at the top of the file
    i = 0
    while i < len(lines):
        stripped = lines[i].strip()
        if not stripped or stripped.startswith("//") or stripped.startswith("/*"):
            i += 1
            continue
        if TS_IMPORT_RE.match(stripped) or TS_SIDE_EFFECT_RE.match(stripped):
            if import_start is None:
                import_start = i
            import_end = i + 1
            i += 1
            continue
        # Hit non-import, non-comment, non-blank line
        if import_start is not None:
            break
        i += 1

    if import_start is None:
        return None

    import_lines = lines[import_start:import_end]
    imports = parse_ts_imports(import_lines)
    if not imports:
        return None

    rendered = render_ts_imports(imports)
    new_lines = lines[:import_start] + rendered + lines[import_end:]
    new_text = "\n".join(new_lines)
    if not new_text.endswith("\n"):
        new_text += "\n"
    original = content
    return new_text if new_text != original else None


# ═══════════════════════════════════════════════════════════════════════
# Main
# ═══════════════════════════════════════════════════════════════════════

def main() -> int:
    cf.header("ENFORCE IMPORT GROUPS")
    root = Path.cwd()
    print(f"Working directory: {root}")

    changed: List[str] = []
    rust_total = 0
    ts_total = 0

    print("Loading workspace crates...")
    global WORKSPACE_CRATES
    WORKSPACE_CRATES = load_workspace_crates(root)
    print(f"Found {len(WORKSPACE_CRATES)} workspace crates")

    for path in root.rglob("*.rs"):
        if any(p in path.parts for p in ("target", "node_modules")):
            continue
        rust_total += 1
        try:
            new_text = process_rust_file(path)
            if new_text is not None:
                path.write_text(new_text, encoding="utf-8")
                changed.append(str(path))
        except Exception as e:
            print(f"Error processing {path}: {e}")
            return 1

    for ext in ("*.ts", "*.tsx"):
        for path in root.rglob(ext):
            if any(p in path.parts for p in ("target", "node_modules", "dist")):
                continue
            if path.name.endswith(".d.ts"):
                continue
            ts_total += 1
            try:
                new_text = process_ts_file(path)
                if new_text is not None:
                    path.write_text(new_text, encoding="utf-8")
                    changed.append(str(path))
            except Exception as e:
                print(f"Error processing {path}: {e}")
                return 1

    print(f"Processed {rust_total} Rust files, {ts_total} TypeScript files")
    cf.ok(f"Updated {len(changed)} files")
    for item in changed:
        print(f"  {item}")

    if shutil.which("cargo"):
        cf.info("Running cargo fmt...")
        subprocess.run(["cargo", "fmt"], check=False, capture_output=True, text=True)

    cf.ok("Done")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as e:
        cf.fail(f"FATAL: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

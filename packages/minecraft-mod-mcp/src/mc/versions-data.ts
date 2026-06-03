import { readFileSync, existsSync } from "node:fs";
import { resolve, join } from "node:path";
import type { FgEra, VersionRaw } from "./versions.js";
import embeddedJson from "./versions-embedded.json" with { type: "json" };

export interface VersionsData {
  fg_eras: Record<string, FgEra>;
  versions: Record<string, VersionRaw>;
  api_groups: Record<string, string>;
  fabric_loom: Record<string, string>;
  neoforge_gradle: { mdg_2_0_prefix: string; mdg_2_0_gradle: string; mdg_other_gradle: string };
  legacy: { eras: string[] };
}

let _cached: VersionsData | null = null;

function parseTomlBasic(content: string): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  let current: Record<string, unknown> = result;

  for (const rawLine of content.split("\n")) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;

    const headerMatch = line.match(/^\[(.+)\]$/);
    if (headerMatch) {
      const keys = headerMatch[1].split(".");
      let obj = result;
      for (let i = 0; i < keys.length - 1; i++) {
        if (!obj[keys[i]] || typeof obj[keys[i]] !== "object") {
          obj[keys[i]] = {};
        }
        obj = obj[keys[i]] as Record<string, unknown>;
      }
      const target: Record<string, unknown> = {};
      obj[keys[keys.length - 1]] = target;
      current = target;
      continue;
    }

    const kvMatch = line.match(/^(\S+)\s*=\s*(.+)$/);
    if (kvMatch) {
      const [, key, rawValue] = kvMatch;
      let value: unknown;
      const trimmed = rawValue.trim();
      if (trimmed.startsWith('"') && trimmed.endsWith('"')) {
        value = trimmed.slice(1, -1);
      } else if (/^-?\d+$/.test(trimmed)) {
        value = parseInt(trimmed, 10);
      } else if (/^-?\d+\.\d+$/.test(trimmed)) {
        value = parseFloat(trimmed);
      } else if (trimmed === "true") {
        value = true;
      } else if (trimmed === "false") {
        value = false;
      } else {
        value = trimmed;
      }
      current[key] = value;
    }
  }

  return result;
}

function findVersionsToml(): string | null {
  const candidates = [
    resolve("packages", "launcher", "shared", "config", "versions.toml"),
    resolve("..", "packages", "launcher", "shared", "config", "versions.toml"),
    resolve("..", "..", "packages", "launcher", "shared", "config", "versions.toml"),
  ];
  const home = process.env.MINECRAFT_MCP_HOME;
  if (home) candidates.push(join(home, "packages", "launcher", "shared", "config", "versions.toml"));

  for (const p of candidates) {
    if (existsSync(p)) return p;
  }
  return null;
}

export function loadVersionsData(): VersionsData {
  if (_cached) return _cached;

  const tomlPath = findVersionsToml();
  if (!tomlPath) {
    _cached = embeddedJson as VersionsData;
    return _cached;
  }

  try {
    const content = readFileSync(tomlPath, "utf-8");
    const raw = parseTomlBasic(content);

    const fgEras = (raw.fg_eras ?? {}) as Record<string, FgEra>;
    const versions = (raw.versions ?? {}) as Record<string, VersionRaw>;
    const apiGroups = (raw.api_groups ?? {}) as Record<string, string>;
    const fabricLoom = (raw.fabric_loom ?? {}) as Record<string, string>;
    const neoforgeGradle = (raw.neoforge_gradle ?? {}) as VersionsData["neoforge_gradle"];
    const legacy = (raw.legacy ?? { eras: [] }) as { eras: string[] };

    _cached = {
      fg_eras: fgEras,
      versions,
      api_groups: apiGroups,
      fabric_loom: fabricLoom,
      neoforge_gradle: neoforgeGradle,
      legacy,
    };
    return _cached;
  } catch {
    _cached = embeddedJson as VersionsData;
    return _cached;
  }
}

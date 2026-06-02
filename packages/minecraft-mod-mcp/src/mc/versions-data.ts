import { readFileSync, existsSync } from "node:fs";
import { resolve, join } from "node:path";
import type { FgEra, VersionRaw } from "./versions.js";

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
  const stack: Array<{ obj: Record<string, unknown>; prefix: string }> = [];
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
    _cached = EMBEDDED_VERSIONS;
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
    _cached = EMBEDDED_VERSIONS;
    return _cached;
  }
}

const EMBEDDED_VERSIONS: VersionsData = {
  fg_eras: {
    fg12_gtnh: { key: "fg12_gtnh", fg_version: "1.2.11", gradle: "4.4.1", plugin_id: "forge", java: 8, min_mc: "1.7.2", max_mc: "1.7.10", extra_repo: "https://jitpack.io" },
    fg21: { key: "fg21", fg_version: "2.1-SNAPSHOT", gradle: "2.14", plugin_id: "net.minecraftforge.gradle.forge", java: 8, min_mc: "1.8", max_mc: "1.8.9" },
    fg22: { key: "fg22", fg_version: "2.2-SNAPSHOT", gradle: "2.14", plugin_id: "net.minecraftforge.gradle.forge", java: 8, min_mc: "1.9", max_mc: "1.11.2" },
    fg23: { key: "fg23", fg_version: "2.3-SNAPSHOT", gradle: "4.10.3", plugin_id: "net.minecraftforge.gradle.forge", java: 8, min_mc: "1.12", max_mc: "1.12.2" },
    fg3: { key: "fg3", fg_version: "3.+", gradle: "4.10.3", plugin_id: "net.minecraftforge.gradle", java: 8, min_mc: "1.13.2", max_mc: "1.14.4" },
    fg41: { key: "fg41", fg_version: "[4.1,4.2)", gradle: "6.9.4", plugin_id: "net.minecraftforge.gradle", java: 8, min_mc: "1.15", max_mc: "1.16.5" },
    fg51: { key: "fg51", fg_version: "5.1.+", gradle: "7.6.4", plugin_id: "net.minecraftforge.gradle", java: 17, min_mc: "1.17.1", max_mc: "1.19.2" },
    fg6: { key: "fg6", fg_version: "[6.0,6.2)", gradle: "8.5", plugin_id: "net.minecraftforge.gradle", java: 17, min_mc: "1.19.3", max_mc: "1.21.3" },
    fg7: { key: "fg7", fg_version: "[7.0.23,8)", gradle: "9.3.0", plugin_id: "net.minecraftforge.gradle", java: 21, min_mc: "1.21.4", max_mc: "99.99.99" },
  },
  versions: {
    "1_7_2": { forge: "1.7.2-10.12.1.1109", fg_era: "fg12_gtnh", java: 8, version_id: "1.7.2-Forge10.12.1.1109" },
    "1_7_10": { forge: "1.7.10-10.13.4.1614-1.7.10", fg_era: "fg12_gtnh", java: 8, version_id: "1.7.10-Forge10.13.4.1614-1.7.10" },
    "1_8": { forge: "1.8-11.14.4.1563-1.8", fg_era: "fg21", java: 8, mappings: "snapshot_20141111", version_id: "1.8-forge-11.14.4.1563" },
    "1_8_8": { forge: "1.8.8-11.15.0.1655-1.8.8", fg_era: "fg21", java: 8, mappings: "snapshot_20141111", version_id: "1.8.8-forge-11.15.0.1655" },
    "1_8_9": { forge: "1.8.9-11.15.1.2318-1.8.9", fg_era: "fg21", java: 8, mappings: "snapshot_20160113", version_id: "1.8.9-forge1.8.9-11.15.1.2318-1.8.9" },
    "1_9": { forge: "1.9-12.16.1.1887-1.9", fg_era: "fg22", java: 8, mappings: "snapshot_20160316", version_id: "1.9-forge-12.16.1.1887" },
    "1_9_4": { forge: "1.9.4-12.17.0.2317-1.9.4", fg_era: "fg22", java: 8, mappings: "snapshot_20160518", version_id: "1.9.4-forge1.9.4-12.17.0.2317-1.9.4" },
    "1_10": { forge: "1.10-12.18.0.2000", fg_era: "fg22", java: 8, mappings: "snapshot_20160518", version_id: "1.10-Forge12.18.0.2000" },
    "1_10_2": { forge: "1.10.2-12.18.3.2511", fg_era: "fg22", java: 8, mappings: "snapshot_20160518", version_id: "1.10.2-forge1.10.2-12.18.3.2511" },
    "1_11": { forge: "1.11-13.19.1.2189", fg_era: "fg22", java: 8, mappings: "snapshot_20170111", version_id: "1.11-Forge13.19.1.2189" },
    "1_11_2": { forge: "1.11.2-13.20.1.2588", fg_era: "fg22", java: 8, mappings: "snapshot_20170111", version_id: "1.11.2-forge1.11.2-13.20.1.2588" },
    "1_12": { forge: "1.12-14.21.1.2387", fg_era: "fg23", java: 8, mappings: "snapshot_20171003", version_id: "1.12-Forge14.21.1.2387" },
    "1_12_1": { forge: "1.12.1-14.22.1.2478", fg_era: "fg23", java: 8, mappings: "snapshot_20171003", version_id: "1.12.1-Forge14.22.1.2478" },
    "1_12_2": { forge: "1.12.2-14.23.5.2847", fg_era: "fg23", java: 8, mappings: "snapshot_20171003", version_id: "1.12.2-forge1.12.2-14.23.5.2847" },
    "1_13_2": { forge: "1.13.2-25.0.223", fg_era: "fg3", java: 8, mappings: "snapshot_20190314", version_id: "1.13.2-forge-25.0.223" },
    "1_14_2": { forge: "1.14.2-26.0.63", fg_era: "fg3", java: 8, mappings: "snapshot_20190314", version_id: "1.14.2-Forge26.0.63", fabric_yarn: "1.14.2+build.7" },
    "1_14_3": { forge: "1.14.3-27.0.60", fg_era: "fg3", java: 8, mappings: "snapshot_20190314", version_id: "1.14.3-Forge27.0.60", fabric_yarn: "1.14.3+build.9" },
    "1_14_4": { forge: "1.14.4-28.2.28", fg_era: "fg3", java: 8, mappings: "snapshot_20200119", version_id: "1.14.4-forge-28.2.28", fabric_yarn: "1.14.4+build.18" },
    "1_15": { forge: "1.15-29.0.4", fg_era: "fg41", java: 8, mappings: "snapshot_20191220", version_id: "1.15-Forge29.0.4", fabric_yarn: "1.15+build.2" },
    "1_15_1": { forge: "1.15.1-30.0.51", fg_era: "fg41", java: 8, mappings: "snapshot_20200224-1.15.1", version_id: "1.15.1-Forge30.0.51", fabric_yarn: "1.15.1+build.10" },
    "1_15_2": { forge: "1.15.2-31.2.60", fg_era: "fg41", java: 8, mappings: "snapshot_20200224-1.15.1", version_id: "1.15.2-forge-31.2.60", fabric_yarn: "1.15.2+build.17" },
    "1_16_1": { forge: "1.16.1-32.0.108", fg_era: "fg41", java: 8, mappings: "snapshot_20200514-1.16", version_id: "1.16.1-Forge32.0.108", fabric_yarn: "1.16.1+build.10" },
    "1_16_2": { forge: "1.16.2-33.0.61", fg_era: "fg41", java: 8, mappings: "snapshot_20200802-1.16.2", version_id: "1.16.2-Forge33.0.61", fabric_yarn: "1.16.2+build.1" },
    "1_16_3": { forge: "1.16.3-34.1.0", fg_era: "fg41", java: 8, mappings: "snapshot_20200802-1.16.2", version_id: "1.16.3-Forge34.1.0", fabric_yarn: "1.16.3+build.7" },
    "1_16_4": { forge: "1.16.4-35.1.4", fg_era: "fg41", java: 8, mappings: "snapshot_20201009-1.16.3", version_id: "1.16.4-Forge35.1.4", fabric_yarn: "1.16.4+build.1" },
    "1_16_5": { forge: "1.16.5-36.2.42", fg_era: "fg41", java: 16, mappings: "snapshot_20210309", version_id: "1.16.5-forge-36.2.42", fabric_yarn: "1.16.5+build.10" },
    "1_17_1": { forge: "1.17.1-37.1.1", fg_era: "fg51", java: 17, mappings: "official_1.17.1", version_id: "1.17.1-forge-37.1.1", fabric_yarn: "1.17.1+build.65" },
    "1_18": { forge: "1.18-38.0.17", fg_era: "fg51", java: 17, mappings: "official_1.18", version_id: "1.18-Forge38.0.17", fabric_yarn: "1.18+build.1" },
    "1_18_1": { forge: "1.18.1-39.1.0", fg_era: "fg51", java: 17, mappings: "official_1.18.1", version_id: "1.18.1-Forge39.1.0", fabric_yarn: "1.18.1+build.8" },
    "1_18_2": { forge: "1.18.2-40.3.12", fg_era: "fg51", java: 17, mappings: "official_1.18.2", version_id: "1.18.2-forge-40.3.12", fabric_yarn: "1.18.2+build.4" },
    "1_19": { forge: "1.19-41.1.0", fg_era: "fg51", java: 17, mappings: "official_1.19", version_id: "1.19-Forge41.1.0", fabric_yarn: "1.19+build.4" },
    "1_19_1": { forge: "1.19.1-42.0.9", fg_era: "fg51", java: 17, mappings: "official_1.19.1", version_id: "1.19.1-Forge42.0.9", fabric_yarn: "1.19.1+build.4" },
    "1_19_2": { forge: "1.19.2-43.5.0", fg_era: "fg51", java: 17, mappings: "official_1.19.2", version_id: "1.19.2-Forge43.5.0", fabric_yarn: "1.19.2+build.28" },
    "1_19_3": { forge: "1.19.3-44.1.0", fg_era: "fg6", java: 17, mappings: "official_1.19.3", version_id: "1.19.3-Forge44.1.0", fabric_yarn: "1.19.3+build.2" },
    "1_19_4": { forge: "1.19.4-45.4.3", fg_era: "fg6", java: 17, mappings: "official_1.19.4", version_id: "1.19.4-forge-45.4.3", fabric_yarn: "1.19.4+build.1" },
    "1_20": { forge: "1.20-46.0.14", fg_era: "fg6", java: 17, mappings: "official_1.20", version_id: "1.20-Forge46.0.14", fabric_yarn: "1.20+build.1" },
    "1_20_1": { forge: "1.20.1-47.4.10", fg_era: "fg6", java: 17, mappings: "official_1.20.1", version_id: "1.20.1-forge-47.4.10", fabric_yarn: "1.20.1+build.10", neoforge: "47.1.106" },
    "1_20_2": { forge: "1.20.2-48.1.0", fg_era: "fg6", java: 17, mappings: "official_1.20.2", version_id: "1.20.2-Forge48.1.0", fabric_yarn: "1.20.2+build.1" },
    "1_20_3": { forge: "1.20.3-49.0.2", fg_era: "fg6", java: 17, mappings: "official_1.20.3", version_id: "1.20.3-Forge49.0.2", fabric_yarn: "1.20.3+build.1" },
    "1_20_4": { forge: "1.20.4-49.2.0", fg_era: "fg6", java: 17, mappings: "official_1.20.4", version_id: "1.20.4-Forge49.2.0", fabric_yarn: "1.20.4+build.3", neoforge: "49.0.51" },
    "1_20_6": { forge: "1.20.6-50.2.8", fg_era: "fg6", java: 21, mappings: "official_1.20.6", version_id: "1.20.6-forge-50.2.8", neoforge: "20.6.139", mdg: "2.0.141", fabric_yarn: "1.20.6+build.1" },
    "1_21": { forge: "1.21-51.0.33", fg_era: "fg6", java: 17, mappings: "official_1.21", version_id: "1.21-Forge51.0.33", fabric_yarn: "1.21+build.1" },
    "1_21_1": { forge: "1.21.1-52.1.0", fg_era: "fg6", java: 17, mappings: "official_1.21.1", version_id: "1.21.1-Forge52.1.0", fabric_yarn: "1.21.1+build.3", neoforge: "21.1.172", mdg: "2.0.141" },
    "1_21_3": { forge: "1.21.3-53.1.0", fg_era: "fg6", java: 17, mappings: "official_1.21.3", version_id: "1.21.3-Forge53.1.0", fabric_yarn: "1.21.3+build.1", neoforge: "21.3.63", mdg: "2.0.141" },
    "1_21_4": { forge: "1.21.4-54.1.14", fg_era: "fg7", java: 21, mappings: "official_1.21.4", version_id: "1.21.4-Forge54.1.14", neoforge: "21.4.0-beta", mdg: "2.0.141" },
    "1_21_5": { forge: "1.21.5-55.1.0", fg_era: "fg7", java: 21, mappings: "official_1.21.5", version_id: "1.21.5-Forge55.1.0" },
    "1_21_6": { forge: "1.21.6-56.0.9", fg_era: "fg7", java: 21, mappings: "official_1.21.6", version_id: "1.21.6-Forge56.0.9" },
    "1_21_7": { forge: "1.21.7-57.0.3", fg_era: "fg7", java: 21, mappings: "official_1.21.7", version_id: "1.21.7-Forge57.0.3" },
    "1_21_8": { forge: "1.21.8-58.1.0", fg_era: "fg7", java: 21, mappings: "official_1.21.8", version_id: "1.21.8-Forge58.1.0" },
    "1_21_9": { forge: "1.21.9-59.0.5", fg_era: "fg7", java: 21, mappings: "official_1.21.9", version_id: "1.21.9-Forge59.0.5" },
    "1_21_10": { forge: "1.21.10-60.1.0", fg_era: "fg7", java: 21, mappings: "official_1.21.10", version_id: "1.21.10-Forge60.1.0" },
    "1_21_11": { forge: "1.21.11-61.1.5", fg_era: "fg7", java: 21, mappings: "official_1.21.11", version_id: "1.21.11-forge-61.1.5", neoforge: "21.11.42", mdg: "2.0.141", fabric_yarn: "1.21.11+build.6" },
    "26_1_2": { forge: "26.1.2-64.0.8", fg_era: "fg7", java: 25, mappings: "official_26.1.2", version_id: "26.1.2-forge-64.0.8", neoforge: "26.1.2.36-beta", mdg: "2.0.141" },
  },
  api_groups: {
    "1.7.2": "legacy17", "1.7.10": "legacy17",
    "1.8": "legacy", "1.8.8": "legacy", "1.8.9": "legacy",
    "1.9": "legacy", "1.9.4": "legacy",
    "1.10": "legacy", "1.10.2": "legacy",
    "1.11": "legacy", "1.11.2": "legacy",
    "1.12": "legacy", "1.12.1": "legacy", "1.12.2": "legacy",
    "1.13.2": "fg3",
    "1.14.2": "fg3", "1.14.3": "fg3", "1.14.4": "fg3",
    "1.15": "fg4", "1.15.1": "fg4", "1.15.2": "fg4",
    "1.16.1": "fg4", "1.16.2": "fg4", "1.16.3": "fg4", "1.16.4": "fg4", "1.16.5": "fg4",
    "1.17.1": "fg5",
    "1.18": "fg5", "1.18.1": "fg5", "1.18.2": "fg5",
    "1.19": "fg5", "1.19.1": "fg5", "1.19.2": "fg5",
    "1.19.3": "fg6", "1.19.4": "fg6",
    "1.20": "fg6", "1.20.1": "fg6", "1.20.2": "fg6", "1.20.3": "fg6",
    "1.20.4": "fg6", "1.20.6": "fg6",
    "1.21": "fg6", "1.21.1": "fg6", "1.21.3": "fg6",
    "1.21.4": "mc26", "1.21.5": "mc26", "1.21.6": "mc26",
    "1.21.7": "mc26", "1.21.8": "mc26", "1.21.9": "mc26",
    "1.21.10": "mc26", "1.21.11": "mc26",
    "26.1.2": "mc26",
  },
  fabric_loom: {
    "1.14.2": "0.8-SNAPSHOT", "1.14.3": "0.8-SNAPSHOT", "1.14.4": "0.8-SNAPSHOT",
    "1.15": "0.8-SNAPSHOT", "1.15.1": "0.8-SNAPSHOT", "1.15.2": "0.8-SNAPSHOT",
    "1.16.1": "0.12-SNAPSHOT", "1.16.2": "0.12-SNAPSHOT", "1.16.3": "0.12-SNAPSHOT", "1.16.4": "0.12-SNAPSHOT", "1.16.5": "0.12-SNAPSHOT",
    "1.17.1": "1.0-SNAPSHOT", "1.18": "1.0-SNAPSHOT", "1.18.1": "1.0-SNAPSHOT", "1.18.2": "1.0-SNAPSHOT",
    "1.19": "1.0-SNAPSHOT", "1.19.1": "1.0-SNAPSHOT", "1.19.2": "1.0-SNAPSHOT",
    "1.19.3": "1.3-SNAPSHOT", "1.19.4": "1.3-SNAPSHOT",
    "1.20": "1.3-SNAPSHOT", "1.20.1": "1.3-SNAPSHOT", "1.20.2": "1.3-SNAPSHOT", "1.20.3": "1.3-SNAPSHOT", "1.20.4": "1.3-SNAPSHOT",
    "1.20.6": "1.5-SNAPSHOT",
    "1.21": "1.5-SNAPSHOT", "1.21.1": "1.5-SNAPSHOT", "1.21.3": "1.5-SNAPSHOT",
    "1.21.4": "1.7-SNAPSHOT", "1.21.5": "1.7-SNAPSHOT", "1.21.6": "1.7-SNAPSHOT", "1.21.7": "1.7-SNAPSHOT", "1.21.8": "1.7-SNAPSHOT",
    "1.21.11": "1.14-SNAPSHOT",
    _default: "1.7-SNAPSHOT",
  },
  neoforge_gradle: { mdg_2_0_prefix: "2.0", mdg_2_0_gradle: "9.3.1", mdg_other_gradle: "8.10" },
  legacy: { eras: ["fg12_gtnh", "fg21", "fg22", "fg23", "fg3", "fg41"] },
};

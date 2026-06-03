import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { versionsDir } from "./platform.js";
import type { VersionJson } from "./versionJson.js";
import { FABRIC } from "./defaults.js";

export type Loader = "forge" | "neoforge" | "fabric";

export const DEFAULT_FABRIC_LOADER_VERSION = FABRIC.defaultLoaderVersion;

export interface FgEra {
  key: string;
  fg_version: string;
  gradle: string;
  plugin_id: string;
  java: number;
  min_mc: string;
  max_mc: string;
  extra_repo?: string;
}

export interface VersionInfo {
  mc_version: string;
  forge: string;
  fg_era: string;
  java: number;
  mappings?: string;
  version_id: string;
  neoforge: string | null;
  mdg: string | null;
  fabric_yarn: string | null;
}

export interface VersionRaw {
  forge: string;
  fg_era: string;
  java: number;
  mappings?: string;
  version_id: string;
  neoforge?: string;
  mdg?: string;
  fabric_yarn?: string;
}

interface VersionsData {
  fg_eras: Record<string, FgEra>;
  versions: Record<string, VersionRaw>;
  api_groups: Record<string, string>;
  fabric_loom: Record<string, string>;
  neoforge_gradle: { mdg_2_0_prefix: string; mdg_2_0_gradle: string; mdg_other_gradle: string };
  legacy: { eras: string[] };
}

function tomlKey(mc: string): string {
  return mc.replace(/\./g, "_");
}

export function loaders(info: VersionInfo): Loader[] {
  const result: Loader[] = [];
  if (info.forge) result.push("forge");
  if (info.neoforge) result.push("neoforge");
  if (info.fabric_yarn) result.push("fabric");
  return result;
}

export function isLegacy(info: VersionInfo, data: VersionsData): boolean {
  return data.legacy.eras.includes(info.fg_era);
}

export function getVersions(data: VersionsData): VersionInfo[] {
  return Object.entries(data.versions).map(([key, raw]) => ({
    mc_version: key.replace(/_/g, "."),
    forge: raw.forge,
    fg_era: raw.fg_era,
    java: raw.java,
    mappings: raw.mappings,
    version_id: raw.version_id,
    neoforge: raw.neoforge ?? null,
    mdg: raw.mdg ?? null,
    fabric_yarn: raw.fabric_yarn ?? null,
  }));
}

export function getVersion(data: VersionsData, mc: string): VersionInfo | null {
  const key = tomlKey(mc);
  const raw = data.versions[key];
  if (!raw) return null;
  return {
    mc_version: mc,
    forge: raw.forge,
    fg_era: raw.fg_era,
    java: raw.java,
    mappings: raw.mappings,
    version_id: raw.version_id,
    neoforge: raw.neoforge ?? null,
    mdg: raw.mdg ?? null,
    fabric_yarn: raw.fabric_yarn ?? null,
  };
}

export function getVersionById(data: VersionsData, versionId: string): VersionInfo | null {
  for (const [key, raw] of Object.entries(data.versions)) {
    if (raw.version_id === versionId) {
      return {
        mc_version: key.replace(/_/g, "."),
        forge: raw.forge,
        fg_era: raw.fg_era,
        java: raw.java,
        mappings: raw.mappings,
        version_id: raw.version_id,
        neoforge: raw.neoforge ?? null,
        mdg: raw.mdg ?? null,
        fabric_yarn: raw.fabric_yarn ?? null,
      };
    }
  }
  return null;
}

export function getVersionForLoader(data: VersionsData, mc: string, loader: Loader): string | null {
  const info = getVersion(data, mc);
  if (!info) return null;
  switch (loader) {
    case "forge": return discoverLoaderVersionId(mc, loader) ?? info.version_id;
    case "neoforge": return info.neoforge ? discoverLoaderVersionId(mc, loader, info.neoforge) : null;
    case "fabric": return info.fabric_yarn ? discoverLoaderVersionId(mc, loader) : null;
  }
}

function discoverLoaderVersionId(mcVersion: string, loader: Loader, loaderVer?: string | null): string | null {
  const vDir = versionsDir();
  if (!existsSync(vDir)) return null;

  try {
    for (const entry of readdirSync(vDir, { withFileTypes: true })) {
      if (!entry.isDirectory()) continue;
      const dirName = entry.name;
      const jsonPath = join(vDir, dirName, `${dirName}.json`);
      if (!existsSync(jsonPath)) continue;

      try {
        const raw = readFileSync(jsonPath, "utf-8");
        const vj = JSON.parse(raw) as VersionJson;
        const inherits = vj.inheritsFrom ?? vj.id;
        if (inherits !== mcVersion) continue;

        const mc = vj.mainClass?.toLowerCase() ?? "";
        if (loader === "neoforge" && mc.includes("neoforged")) return dirName;
        if (loader === "fabric" && mc.includes("fabricmc")) return dirName;
        if (loader === "forge" && (mc.includes("minecraftforge") || mc.includes("forgebootstrap"))) return dirName;
      } catch {
        continue;
      }
    }
  } catch {}

  if (loader === "neoforge" && loaderVer) return `${mcVersion}-neoforge-${loaderVer}`;
  if (loader === "fabric") return `fabric-loader-${DEFAULT_FABRIC_LOADER_VERSION}-${mcVersion}`;
  return null;
}

export function getFgEra(data: VersionsData, key: string): FgEra | null {
  return data.fg_eras[key] ?? null;
}

export function getApiGroup(data: VersionsData, mc: string): string | null {
  return data.api_groups[mc] ?? null;
}

export function getFabricLoom(data: VersionsData, mc: string): string | null {
  return data.fabric_loom[mc] ?? data.fabric_loom._default ?? null;
}

export function getNeoforgeGradle(data: VersionsData, mc: string): [string, string] | null {
  const info = getVersion(data, mc);
  if (!info?.mdg) return null;
  const gradle = info.mdg.startsWith(data.neoforge_gradle.mdg_2_0_prefix)
    ? data.neoforge_gradle.mdg_2_0_gradle
    : data.neoforge_gradle.mdg_other_gradle;
  return [info.mdg, gradle];
}

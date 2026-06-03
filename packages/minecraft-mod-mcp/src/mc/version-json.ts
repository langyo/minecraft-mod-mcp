import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { isWindows, isMacos } from "../runtime/detector.js";
import { versionsDir, librariesDir } from "./platform.js";

export interface VersionJson {
  id: string;
  type: string;
  mainClass: string;
  minecraftArguments?: string;
  arguments?: {
    game?: ArgumentValue[];
    jvm?: ArgumentValue[];
  };
  libraries: Library[];
  assetIndex?: AssetIndex;
  assets?: string;
  downloads?: VersionDownloads;
  inheritsFrom?: string;
  jar?: string;
  releaseTime?: string;
}

export type ArgumentValue = string | ConditionalArgument;

export interface ConditionalArgument {
  rules: Rule[];
  value: string | string[];
}

export interface Rule {
  action: string;
  os?: OsRule;
  features?: Record<string, unknown>;
}

export interface OsRule {
  name?: string;
  version?: string;
  arch?: string;
}

export interface AssetIndex {
  id: string;
  sha1?: string;
  size?: number;
  totalSize?: number;
  url?: string;
}

export interface VersionDownloads {
  client?: ClientDownload;
  server?: ServerDownload;
}

export interface ClientDownload {
  sha1?: string;
  size?: number;
  url?: string;
}

export interface ServerDownload {
  sha1?: string;
  size?: number;
  url?: string;
}

export interface Library {
  name: string;
  downloads?: LibraryDownloads;
  natives?: Record<string, string>;
  extract?: { exclude?: string[] };
  rules?: Rule[];
}

export interface LibraryDownloads {
  artifact?: Artifact;
  classifiers?: Record<string, Record<string, unknown>>;
}

export interface Artifact {
  path: string;
  sha1?: string;
  size?: number;
  url?: string;
}

export function loadVersionJson(path: string): VersionJson {
  const data = readFileSync(path, "utf-8");
  return JSON.parse(data) as VersionJson;
}

export function loadVersion(versionId: string): VersionJson {
  const dir = versionsDir();
  const path = join(dir, versionId, `${versionId}.json`);
  if (!existsSync(path)) {
    throw new Error(
      `Version "${versionId}" is not installed.\n` +
      `  Missing: ${path}\n` +
      `  Run: minecraft-mod-mcp install ${versionId}`
    );
  }
  return loadVersionJson(path);
}

export function loadVersionMerged(versionId: string): VersionJson {
  const primary = loadVersion(versionId);
  return mergeWithParents(primary);
}

function mergeWithParents(vj: VersionJson): VersionJson {
  if (!vj.inheritsFrom) return vj;

  let parent: VersionJson;
  try {
    parent = loadVersion(vj.inheritsFrom);
  } catch {
    return vj;
  }

  parent = mergeWithParents(parent);

  return {
    id: vj.id,
    type: vj.type || parent.type,
    mainClass: vj.mainClass || parent.mainClass,
    inheritsFrom: vj.inheritsFrom,
    libraries: mergeLibraries(parent.libraries ?? [], vj.libraries ?? []),
    arguments: mergeArguments(parent.arguments, vj.arguments),
    minecraftArguments: vj.minecraftArguments || parent.minecraftArguments,
    assetIndex: vj.assetIndex || parent.assetIndex,
    assets: vj.assets || parent.assets,
    downloads: vj.downloads || parent.downloads,
    jar: vj.jar || parent.jar,
    releaseTime: vj.releaseTime || parent.releaseTime,
  };
}

function libBaseKey(name: string): string {
  const parts = name.split(":");
  return parts.length >= 2 ? `${parts[0]}:${parts[1]}` : name;
}

function mergeLibraries(parent: Library[], child: Library[]): Library[] {
  const map = new Map<string, Library>();
  for (const lib of parent) {
    if (lib.name) map.set(libBaseKey(lib.name), lib);
  }
  for (const lib of child) {
    if (lib.name) map.set(libBaseKey(lib.name), lib);
  }
  return [...map.values()];
}

function mergeArguments(
  parent: VersionJson["arguments"],
  child: VersionJson["arguments"],
): VersionJson["arguments"] {
  if (!parent && !child) return undefined;
  if (!parent) return child;
  if (!child) return parent;
  return {
    game: [...(parent.game ?? []), ...(child.game ?? [])],
    jvm: [...(parent.jvm ?? []), ...(child.jvm ?? [])],
  };
}

export function collectAllArgs(vj: VersionJson): { jvmArgs: string[]; gameArgs: string[] } {
  const gameArgs: string[] = [];
  const jvmArgs: string[] = [];

  if (vj.arguments) {
    if (vj.arguments.game) {
      for (const av of vj.arguments.game) {
        if (typeof av === "string") {
          gameArgs.push(av);
        } else if (shouldApply(av.rules)) {
          if (typeof av.value === "string") gameArgs.push(av.value);
          else gameArgs.push(...av.value);
        }
      }
    }
    if (vj.arguments.jvm) {
      for (const av of vj.arguments.jvm) {
        if (typeof av === "string") {
          jvmArgs.push(av);
        } else if (shouldApply(av.rules)) {
          if (typeof av.value === "string") jvmArgs.push(av.value);
          else jvmArgs.push(...av.value);
        }
      }
    }
  }

  if (vj.minecraftArguments) {
    gameArgs.push(...vj.minecraftArguments.split(/\s+/));
  }

  return { jvmArgs, gameArgs };
}

export function shouldApply(rules: Rule[], features?: Record<string, boolean>): boolean {
  if (rules.length === 0) return true;
  const feats = features ?? {};

  let allow = false;
  let deny = false;

  for (const rule of rules) {
    if (rule.features) {
      const featMatch = Object.entries(rule.features).every(([k, v]) => {
        if (typeof v === "boolean") return feats[k] === v;
        return feats[k] != null;
      });
      if (!featMatch) continue;
    }

    const osMatch = rule.os ? matchOs(rule.os) : true;
    if (osMatch) {
      if (rule.action === "allow") allow = true;
      else if (rule.action === "disallow") deny = true;
    }
  }

  return allow && !deny;
}

function matchOs(os: OsRule): boolean {
  if (os.name) {
    const nameOk = (() => {
      switch (os.name) {
        case "windows": return isWindows();
        case "linux": return !isWindows() && !isMacos();
        case "osx": return isMacos();
        default: return true;
      }
    })();
    if (!nameOk) return false;
  }
  return true;
}

export function libraryPath(name: string): string {
  const parts = name.split(":");
  if (parts.length < 3) return join(librariesDir(), name.replace(/:/g, "/"));

  const group = parts[0].replace(/\./g, "/");
  const artifact = parts[1];
  const version = parts[2];
  const classifier = parts[3];

  const filename = classifier
    ? `${artifact}-${version}-${classifier}.jar`
    : `${artifact}-${version}.jar`;

  return join(librariesDir(), group, artifact, version, filename);
}

export function libraryMavenPath(name: string): string {
  const parts = name.split(":");
  if (parts.length < 3) return name.replace(/:/g, "/");
  const group = parts[0].replace(/\./g, "/");
  const artifact = parts[1];
  const version = parts[2];
  const classifier = parts[3];
  return classifier
    ? `${group}/${artifact}/${version}/${artifact}-${version}-${classifier}.jar`
    : `${group}/${artifact}/${version}/${artifact}-${version}.jar`;
}

export function resolveClasspath(libraries: Library[]): string[] {
  const classpath: string[] = [];

  for (const lib of libraries) {
    if (lib.rules && !shouldApply(lib.rules)) continue;

    if (lib.downloads?.artifact) {
      const path = join(librariesDir(), lib.downloads.artifact.path);
      if (existsSync(path)) classpath.push(path);
      continue;
    }

    const path = libraryPath(lib.name);
    if (existsSync(path)) {
      classpath.push(path);
    } else {
      const universalPath = path.replace(/\.jar$/, "-universal.jar");
      if (existsSync(universalPath)) classpath.push(universalPath);
    }
  }

  const patched = patchClasspath(classpath);

  return patched;
}

function patchClasspath(classpath: string[]): string[] {
  return classpath.map(p => {
    if (p.includes("launchwrapper-1.9")) {
      const better = p
        .replace(/launchwrapper[\\/]1\.9[\\/]/, "launchwrapper/1.12/")
        .replace("launchwrapper-1.9.jar", "launchwrapper-1.12.jar");
      if (existsSync(better)) return better;
    }
    return p;
  });
}

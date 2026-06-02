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
  const path = join(versionsDir(), versionId, `${versionId}.json`);
  return loadVersionJson(path);
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

export function shouldApply(rules: Rule[]): boolean {
  if (rules.length === 0) return true;

  let allow = false;
  let deny = false;

  for (const rule of rules) {
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
    if (existsSync(path)) classpath.push(path);
  }

  return classpath;
}

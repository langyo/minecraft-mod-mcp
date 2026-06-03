import { existsSync } from "node:fs";
import { join } from "node:path";
import { crossHomedir } from "../runtime/detector.js";
import type { VersionJson } from "./version-json.js";
import { collectAllArgs, resolveClasspath } from "./version-json.js";
import { nativesDir, assetsDir, versionsDir, librariesDir, classpathSeparator, findJavaForVersion } from "./platform.js";
import { loadVersionsData, type VersionsData } from "./versions-data.js";
import { getVersionById, type Loader } from "./versions.js";

export interface LaunchConfig {
  versionId: string;
  mcDir?: string;
  loader: Loader;
  modJar?: string;
  mcpPort?: number;
  dryRun?: boolean;
  maxMemoryMb?: number;
  minMemoryMb?: number;
  extraJvmArgs?: string;
  extraGameArgs?: string;
  javaPath?: string;
  playerName?: string;
  uuid?: string;
  accessToken?: string;
  userType?: string;
  width?: number;
  height?: number;
}

export interface LaunchCommand {
  java: string;
  args: string[];
  classpath: string;
  mainClass: string;
  javaVersion: number;
}

const LEGACY_JVM_ARGS = [
  "--add-opens", "java.base/java.lang=ALL-UNNAMED",
  "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
  "--add-opens", "java.base/java.util.jar=ALL-UNNAMED",
  "--add-opens", "java.base/java.util.zip=ALL-UNNAMED",
  "--add-opens", "java.base/jdk.internal.loader=ALL-UNNAMED",
  "--add-opens", "java.base/java.io=ALL-UNNAMED",
  "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
];

export function buildLaunchCommand(config: LaunchConfig, vj: VersionJson, data?: VersionsData): LaunchCommand {
  const vd = data ?? loadVersionsData();
  const mcDir = config.mcDir ?? join(crossHomedir(), ".minecraft");

  const versionInfo = getVersionById(vd, config.versionId);
  const targetJavaVersion = versionInfo?.java ?? 17;
  const java = config.javaPath || findJavaForVersion(targetJavaVersion);

  const classpathPaths = resolveClasspath(vj.libraries);

  const inheritsFrom = vj.inheritsFrom ?? config.versionId;
  const baseJar = join(versionsDir(), inheritsFrom, `${inheritsFrom}.jar`);
  if (existsSync(baseJar)) classpathPaths.push(baseJar);

  const versionJar = join(versionsDir(), config.versionId, `${config.versionId}.jar`);
  if (existsSync(versionJar) && versionJar !== baseJar) classpathPaths.push(versionJar);

  if (config.modJar && existsSync(config.modJar)) {
    classpathPaths.push(config.modJar);
  }

  const sep = classpathSeparator();
  const classpath = classpathPaths.join(sep);

  const { jvmArgs, gameArgs } = collectAllArgs(vj);

  const ndir = nativesDir(config.versionId);
  const aDir = assetsDir();
  const assetsIndex = vj.assets ?? inheritsFrom;
  const libDir = librariesDir();

  const allArgs: string[] = [];

  if (config.maxMemoryMb) allArgs.push(`-Xmx${config.maxMemoryMb}m`);
  if (config.minMemoryMb) allArgs.push(`-Xms${config.minMemoryMb}m`);

  if (config.mcpPort) {
    allArgs.push(`-Dmcp.port=${config.mcpPort}`);
  }

  if (targetJavaVersion < 17) {
    allArgs.push(...LEGACY_JVM_ARGS);
  }

  if (config.extraJvmArgs) {
    for (const arg of config.extraJvmArgs.split(/\s+/)) {
      if (arg) allArgs.push(arg);
    }
  }

  const hasCpInJvm = jvmArgs.some((a) => a.includes("${classpath}"));

  const resolvedJvm = jvmArgs.map((arg) =>
    arg
      .replace(/\$\{natives_directory\}/g, ndir)
      .replace(/\$\{launcher_name\}/g, "MMML")
      .replace(/\$\{launcher_version\}/g, "0.1.0")
      .replace(/\$\{classpath\}/g, classpath)
      .replace(/\$\{classpath_separator\}/g, sep)
      .replace(/\$\{library_directory\}/g, libDir)
      .replace(/\$\{version_name\}/g, config.versionId)
      .replace(/\$\{game_directory\}/g, mcDir)
      .replace(/\$\{assets_root\}/g, aDir)
      .replace(/\$\{assets_index_name\}/g, assetsIndex)
  );
  allArgs.push(...resolvedJvm);

  if (!hasCpInJvm && classpath) {
    allArgs.push("-cp", classpath);
  }

  allArgs.push(vj.mainClass);

  const playerName = config.playerName ?? "Player";
  const uuid = config.uuid ?? "0";
  const accessToken = config.accessToken ?? "0";
  const userType = config.userType ?? "legacy";

  const resolvedGame = gameArgs.map((arg) =>
    arg
      .replace(/\$\{auth_player_name\}/g, playerName)
      .replace(/\$\{version_name\}/g, config.versionId)
      .replace(/\$\{game_directory\}/g, mcDir)
      .replace(/\$\{assets_root\}/g, aDir)
      .replace(/\$\{assets_index_name\}/g, assetsIndex)
      .replace(/\$\{auth_uuid\}/g, uuid)
      .replace(/\$\{auth_access_token\}/g, accessToken)
      .replace(/\$\{user_type\}/g, userType)
      .replace(/\$\{version_type\}/g, "release")
      .replace(/\$\{natives_directory\}/g, ndir)
      .replace(/\$\{launcher_name\}/g, "MCP-Launcher")
      .replace(/\$\{launcher_version\}/g, "0.1.0")
      .replace(/\$\{classpath\}/g, classpath)
      .replace(/\$\{classpath_separator\}/g, sep)
      .replace(/\$\{library_directory\}/g, libDir)
      .replace(/\$\{resolution_width\}/g, String(config.width ?? 854))
      .replace(/\$\{resolution_height\}/g, String(config.height ?? 480))
      .replace(/\$\{clientid\}/g, "")
      .replace(/\$\{auth_xuid\}/g, "")
      .replace(/\$\{user_properties\}/g, "{}")
  );
  allArgs.push(...resolvedGame);

  if (config.extraGameArgs) {
    for (const arg of config.extraGameArgs.split(/\s+/)) {
      if (arg) allArgs.push(arg);
    }
  }

  return { java, args: allArgs, classpath, mainClass: vj.mainClass, javaVersion: targetJavaVersion };
}

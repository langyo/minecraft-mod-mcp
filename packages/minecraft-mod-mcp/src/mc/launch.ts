import { existsSync, mkdirSync, readFileSync, writeFileSync, readdirSync } from "node:fs";
import { join, extname, basename } from "node:path";
import { inflateRawSync } from "node:zlib";
import { crossHomedir, isWindows, isMacos } from "../runtime/detector.js";
import type { Library, VersionJson } from "./version-json.js";
import { collectAllArgs, resolveClasspath, shouldApply, libraryPath } from "./version-json.js";
import { nativesDir, assetsDir, versionsDir, librariesDir, classpathSeparator, findJavaForVersion, jdkHome, launcherDir } from "./platform.js";
import { loadVersionsData, type VersionsData } from "./versions-data.js";
import { getVersionById, type Loader } from "./versions.js";
import { installedJavaHome, ensureJavaInstalled } from "./java-download.js";
import { detectJavas } from "./java-detect.js";
import { LAUNCHER, PLAYER, GAME } from "./defaults.js";

const NATIVE_EXTS = new Set([".dll", ".so", ".dylib", ".jnilib"]);

function getNativeOsKey(): string {
  if (isWindows()) return "windows";
  if (isMacos()) return "osx";
  return "linux";
}

function extractNatives(libraries: Library[], nDir: string): void {
  if (existsSync(nDir)) {
    const existing = readdirSync(nDir).filter(f => NATIVE_EXTS.has(extname(f).toLowerCase()));
    if (existing.length > 0) return;
  }
  mkdirSync(nDir, { recursive: true });

  const osKey = getNativeOsKey();

  for (const lib of libraries) {
    if (lib.rules && !shouldApply(lib.rules)) continue;

    let nativePath: string | null = null;

    if (lib.natives && lib.natives[osKey]) {
      const nativeClass = lib.natives[osKey] as string;
      if (lib.downloads?.classifiers?.[nativeClass]) {
        const relPath = (lib.downloads.classifiers[nativeClass] as Record<string, unknown>).path as string;
        if (relPath) nativePath = join(librariesDir(), relPath);
      }
    }

    if (!nativePath && lib.name.includes("natives")) {
      nativePath = libraryPath(lib.name);
    }

    if (!nativePath || !existsSync(nativePath)) continue;

    extractNativeJar(nativePath, nDir);
  }
}

function extractNativeJar(jarPath: string, outDir: string): void {
  const buf = readFileSync(jarPath);

  let endPos = -1;
  for (let i = buf.length - 22; i >= 0; i--) {
    if (buf[i] === 0x50 && buf[i + 1] === 0x4b && buf[i + 2] === 0x05 && buf[i + 3] === 0x06) {
      endPos = i;
      break;
    }
  }
  if (endPos < 0) return;

  const cdOffset = buf.readUInt32LE(endPos + 16);
  const cdSize = buf.readUInt32LE(endPos + 12);
  const cdEnd = cdOffset + cdSize;

  let pos = cdOffset;
  while (pos + 46 <= cdEnd) {
    if (buf.readUInt32LE(pos) !== 0x02014b50) break;
    buf.readUInt16LE(pos + 10);
    buf.readUInt32LE(pos + 20);
    buf.readUInt32LE(pos + 24);
    const nameLen = buf.readUInt16LE(pos + 28);
    const extraLen = buf.readUInt16LE(pos + 30);
    const commentLen = buf.readUInt16LE(pos + 32);
    const localOffset = buf.readUInt32LE(pos + 42);
    const name = buf.subarray(pos + 46, pos + 46 + nameLen).toString("utf-8");
    pos += 46 + nameLen + extraLen + commentLen;

    if (name.endsWith("/")) continue;
    const ext = extname(name).toLowerCase();
    if (!NATIVE_EXTS.has(ext)) continue;

    const fileName = basename(name);
    const outPath = join(outDir, fileName);
    if (existsSync(outPath)) continue;

    const lNameLen = buf.readUInt16LE(localOffset + 26);
    const lExtraLen = buf.readUInt16LE(localOffset + 28);
    const lMethod = buf.readUInt16LE(localOffset + 8);
    const lCSize = buf.readUInt32LE(localOffset + 18);
    const dataStart = localOffset + 30 + lNameLen + lExtraLen;

    if (lMethod === 0) {
      writeFileSync(outPath, buf.subarray(dataStart, dataStart + lCSize));
    } else if (lMethod === 8) {
      const compressed = buf.subarray(dataStart, dataStart + lCSize);
      const decompressed = inflateRawSync(compressed);
      writeFileSync(outPath, decompressed);
    }
  }
}

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

function inferJavaFromVersion(vj: VersionJson): number {
  const id = (vj.inheritsFrom ?? vj.id ?? "").replace(/-/g, ".");
  const m = id.match(/1\.(\d+)/);
  if (!m) return GAME.javaVersionFallback;
  const minor = parseInt(m[1]);
  if (minor >= 20) return 21;
  if (minor >= 17) return 17;
  return 8;
}

function findExactJava(targetVersion: number): string | null {
  const home = jdkHome(targetVersion);
  if (home) {
    const exe = isWindows() ? join(home, "bin", "java.exe") : join(home, "bin", "java");
    if (existsSync(exe)) return exe;
  }

  const all = detectJavas();
  const match = all.find(j => j.version === targetVersion);
  if (match) {
    const exe = isWindows() ? join(match.path, "bin", "java.exe") : join(match.path, "bin", "java");
    if (existsSync(exe)) return exe;
  }

  return null;
}

export async function ensureJavaForLaunch(
  config: LaunchConfig,
  vj: VersionJson,
  data?: VersionsData,
  onProgress?: (msg: string) => void,
): Promise<string> {
  if (config.javaPath && existsSync(config.javaPath)) return config.javaPath;

  const vd = data ?? loadVersionsData();
  let versionInfo = getVersionById(vd, config.versionId);
  if (!versionInfo) {
    const mcVer = vj.inheritsFrom ?? vj.id ?? config.versionId;
    versionInfo = getVersionById(vd, mcVer);
  }
  const targetJava = versionInfo?.java ?? inferJavaFromVersion(vj);

  const cached = installedJavaHome(targetJava);
  if (cached) {
    return isWindows() ? join(cached, "bin", "java.exe") : join(cached, "bin", "java");
  }

  const exact = findExactJava(targetJava);
  if (exact) return exact;

  const home = await ensureJavaInstalled(targetJava, onProgress);
  return isWindows() ? join(home, "bin", "java.exe") : join(home, "bin", "java");
}

export function buildLaunchCommand(config: LaunchConfig, vj: VersionJson, data?: VersionsData): LaunchCommand {
  const vd = data ?? loadVersionsData();
  const mcDir = config.mcDir ?? join(crossHomedir(), ".minecraft");

  let versionInfo = getVersionById(vd, config.versionId);
  if (!versionInfo) {
    const mcVer = vj.inheritsFrom ?? vj.id ?? config.versionId;
    versionInfo = getVersionById(vd, mcVer);
  }
  const targetJavaVersion = versionInfo?.java ?? inferJavaFromVersion(vj);
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

  const needsSortFix = needsLegacySortFix(config.versionId, targetJavaVersion);
  if (needsSortFix) {
    const sortFixJar = join(launcherDir(), "legacyfix", "sortfix-tweaker.jar");
    if (existsSync(sortFixJar)) {
      classpathPaths.unshift(sortFixJar);
    }
  }

  const sep = classpathSeparator();
  const classpath = classpathPaths.join(sep);

  const { jvmArgs, gameArgs } = collectAllArgs(vj);

  const ndir = nativesDir(config.versionId);
  extractNatives(vj.libraries, ndir);
  const aDir = assetsDir();
  const assetsIndex = vj.assets ?? inheritsFrom;
  const libDir = librariesDir();

  const allArgs: string[] = [];

  if (config.maxMemoryMb) allArgs.push(`-Xmx${config.maxMemoryMb}m`);
  if (config.minMemoryMb) allArgs.push(`-Xms${config.minMemoryMb}m`);

  if (existsSync(ndir)) {
    allArgs.push(`-Djava.library.path=${ndir}`);
  }

  if (config.mcpPort) {
    allArgs.push(`-Dmcp.port=${config.mcpPort}`);
  }

  if (targetJavaVersion >= 9 && targetJavaVersion < 17) {
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
      .replace(/\$\{launcher_name\}/g, LAUNCHER.name)
      .replace(/\$\{launcher_version\}/g, LAUNCHER.version)
      .replace(/\$\{classpath\}/g, classpath)
      .replace(/\$\{classpath_separator\}/g, sep)
      .replace(/\$\{library_directory\}/g, libDir)
      .replace(/\$\{version_name\}/g, config.versionId)
      .replace(/\$\{game_directory\}/g, mcDir)
      .replace(/\$\{assets_root\}/g, aDir)
      .replace(/\$\{game_assets\}/g, aDir)
      .replace(/\$\{assets_index_name\}/g, assetsIndex)
  );
  allArgs.push(...resolvedJvm);

  if (!hasCpInJvm && classpath) {
    allArgs.push("-cp", classpath);
  }

  allArgs.push(vj.mainClass);

  if (needsSortFix) {
    allArgs.push("--tweakClass", "mcp.fix.SortFixTweaker");
  }

  const playerName = config.playerName ?? PLAYER.defaultName;
  const uuid = config.uuid ?? PLAYER.defaultUuid;
  const accessToken = config.accessToken ?? PLAYER.defaultAccessToken;
  const userType = config.userType ?? PLAYER.defaultUserType;

  const resolvedGame = gameArgs.map((arg) =>
    arg
      .replace(/\$\{auth_player_name\}/g, playerName)
      .replace(/\$\{version_name\}/g, config.versionId)
      .replace(/\$\{game_directory\}/g, mcDir)
      .replace(/\$\{assets_root\}/g, aDir)
      .replace(/\$\{game_assets\}/g, aDir)
      .replace(/\$\{assets_index_name\}/g, assetsIndex)
      .replace(/\$\{auth_uuid\}/g, uuid)
      .replace(/\$\{auth_access_token\}/g, accessToken)
      .replace(/\$\{user_type\}/g, userType)
      .replace(/\$\{version_type\}/g, LAUNCHER.versionType)
      .replace(/\$\{natives_directory\}/g, ndir)
      .replace(/\$\{launcher_name\}/g, LAUNCHER.nameGame)
      .replace(/\$\{launcher_version\}/g, LAUNCHER.version)
      .replace(/\$\{classpath\}/g, classpath)
      .replace(/\$\{classpath_separator\}/g, sep)
      .replace(/\$\{library_directory\}/g, libDir)
      .replace(/\$\{resolution_width\}/g, String(config.width ?? GAME.defaultWidth))
      .replace(/\$\{resolution_height\}/g, String(config.height ?? GAME.defaultHeight))
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

const SORT_FIX_PREFIXES = ["1.7.2", "1.7.10", "1.6", "1.5"];

function needsLegacySortFix(versionId: string, javaVersion: number): boolean {
  if (javaVersion !== 8) return false;
  return SORT_FIX_PREFIXES.some(p => versionId.startsWith(p));
}

import { existsSync, mkdirSync, readFileSync, writeFileSync, openSync, copyFileSync, readdirSync, symlinkSync, statSync, rmSync } from "node:fs";
import { join, dirname } from "node:path";
import { tmpdir } from "node:os";
import { serverDir } from "./settings.js";
import { downloadFile, downloadForgeInstaller, downloadFabricLoader, downloadNeoforgeInstaller } from "./download.js";
import { loadVersion } from "./versionJson.js";
import { spawn, type ChildProcess } from "node:child_process";
import { findJavaForVersion, librariesDir, versionsDir } from "./platform.js";
import { loadVersionsData } from "./versionsData.js";
import { getVersion, getVersionForLoader, DEFAULT_FABRIC_LOADER_VERSION, type Loader } from "./versions.js";
import { DOWNLOAD, GAME, SERVER, type ServerType, SERVER_TYPES } from "./defaults.js";
import { fetchWithFallback, javaProxyArgs, gradleProxyEnv } from "./proxy.js";

export interface ServerProperties {
  serverPort?: number;
  motd?: string;
  maxPlayers?: number;
  gamemode?: string;
  difficulty?: string;
  onlineMode?: boolean;
  pvp?: boolean;
  viewDistance?: number;
  simulationDistance?: number;
  levelSeed?: string;
  levelName?: string;
  levelType?: string;
  generateStructures?: boolean;
  whiteList?: boolean;
  enforceWhitelist?: boolean;
  opPermissionLevel?: number;
  bindAddress?: string;
  [key: string]: string | number | boolean | undefined;
}

export interface ServerSetup {
  serverDir: string;
  jarPath: string;
  versionId: string;
  mcVersion: string;
  serverType: ServerType;
  javaVersion: number;
}

export interface LaunchServerOpts {
  javaPath?: string;
  javaVersion?: number;
  maxMemoryMb?: number;
  minMemoryMb?: number;
  extraJvmArgs?: string;
  extraGameArgs?: string;
  port?: number;
}

export interface LaunchedServer {
  process: ChildProcess;
  port: number;
  dir: string;
}

const MANIFEST_URL = DOWNLOAD.versionManifestV2Url;

function generateServerProperties(overrides: ServerProperties): string {
  const props: Record<string, string> = {
    "enable-jmx-monitoring": "false",
    "rcon.port": String(SERVER.rconPort),
    "level-seed": overrides.levelSeed ?? "",
    "gamemode": overrides.gamemode ?? "survival",
    "enable-command-block": "true",
    "enable-query": "false",
    "generator-settings": `{"layers":[{"block":"bedrock","height":1},{"block":"dirt","height":2},{"block":"grass_block","height":1}],"biome":"plains"}`,
    "level-name": overrides.levelName ?? "world",
    "motd": overrides.motd ?? "MCP Server",
    "query.port": String(overrides.serverPort ?? GAME.defaultServerPort),
    "pvp": String(overrides.pvp ?? true),
    "generate-structures": String(overrides.generateStructures ?? false),
    "max-chained-neighbor-updates": "1000000",
    "difficulty": overrides.difficulty ?? "easy",
    "network-compression-threshold": String(SERVER.networkCompressionThreshold),
    "max-tick-time": String(SERVER.maxTickTime),
    "require-resource-pack": "false",
    "use-native-transport": "true",
    "max-players": String(overrides.maxPlayers ?? SERVER.maxPlayers),
    "online-mode": String(overrides.onlineMode ?? false),
    "enable-status": "true",
    "allow-flight": "true",
    "initial-disabled-packs": "",
    "broadcast-rcon-to-ops": "true",
    "view-distance": String(overrides.viewDistance ?? SERVER.viewDistance),
    "resource-pack": "",
    "server-ip": overrides.bindAddress ?? SERVER.bindAddress,
    "resource-pack-prompt": "",
    "allow-nether": "true",
    "server-port": String(overrides.serverPort ?? GAME.defaultServerPort),
    "enable-rcon": "false",
    "sync-chunk-writes": "true",
    "op-permission-level": String(overrides.opPermissionLevel ?? 4),
    "prevent-proxy-connections": "false",
    "hide-online-players": "false",
    "resource-pack-sha1": "",
    "entity-broadcast-range-percentage": "100",
    "simulation-distance": String(overrides.simulationDistance ?? SERVER.simulationDistance),
    "rcon.password": "",
    "player-idle-timeout": "0",
    "force-gamemode": "false",
    "rate-limit": "0",
    "hardcore": "false",
    "white-list": String(overrides.whiteList ?? false),
    "broadcast-console-to-ops": "true",
    "spawn-npcs": "true",
    "spawn-animals": "true",
    "function-permission-level": "2",
    "initial-enabled-packs": "vanilla",
    "level-type": overrides.levelType ?? "flat",
    "text-filtering-config": "",
    "spawn-monsters": "true",
    "enforce-whitelist": String(overrides.enforceWhitelist ?? false),
    "max-world-size": String(SERVER.maxWorldSize),
  };

  return Object.entries(props).map(([k, v]) => `${k}=${v}`).join("\n") + "\n";
}

interface ServerDownloadInfo {
  url: string;
  sha1?: string;
}

async function fetchJson(url: string): Promise<any> {
  const mod = await import("node:https");
  return new Promise((resolve, reject) => {
    mod.get(url, { headers: { "User-Agent": SERVER.userAgent } }, (res) => {
      if (res.statusCode && res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        fetchJson(res.headers.location).then(resolve, reject);
        return;
      }
      let data = "";
      res.on("data", (c: Buffer) => (data += c));
      res.on("end", () => {
        try { resolve(JSON.parse(data)); }
        catch { reject(new Error(`Invalid JSON from ${url}`)); }
      });
      res.on("error", reject);
    }).on("error", reject);
  });
}

async function fetchVanillaServerUrl(mcVersion: string): Promise<ServerDownloadInfo> {
  const manifest = await fetchJson(MANIFEST_URL);
  const entry = manifest.versions?.find((v: { id: string }) => v.id === mcVersion);
  if (!entry?.url) throw new Error(`MC ${mcVersion} not found in version manifest`);
  const vj = await fetchJson(entry.url);
  const server = vj?.downloads?.server;
  if (!server?.url) throw new Error(`No server download for ${mcVersion}`);
  return { url: server.url, sha1: server.sha1 };
}

async function ensureVanillaServerJar(mcVersion: string, sDir: string, onProgress?: (msg: string) => void): Promise<string> {
  let serverUrl: string | undefined;
  let serverSha: string | undefined;

  try {
    const baseVj = loadVersion(mcVersion);
    serverUrl = baseVj.downloads?.server?.url;
    serverSha = baseVj.downloads?.server?.sha1;
  } catch {}

  if (!serverUrl) {
    onProgress?.(`Fetching server info for ${mcVersion} from Mojang...`);
    const info = await fetchVanillaServerUrl(mcVersion);
    serverUrl = info.url;
    serverSha = info.sha1;
  }

  const jarPath = join(sDir, `server-${mcVersion}.jar`);
  if (!existsSync(jarPath)) {
    if (!serverUrl) throw new Error(`No server download available for ${mcVersion}`);
    onProgress?.(`Downloading server JAR for ${mcVersion}...`);
    await downloadFile(serverUrl, jarPath, serverSha);
  } else {
    onProgress?.(`Server JAR already exists.`);
  }
  return jarPath;
}

async function downloadPaperServer(mcVersion: string, sDir: string, onProgress?: (msg: string) => void): Promise<string> {
  onProgress?.(`Querying Paper API for ${mcVersion}...`);
  const projectData = await fetchJson(SERVER.paperApiUrl);
  const versionEntry = projectData.versions?.find((v: string) => v === mcVersion);
  if (!versionEntry) throw new Error(`Paper does not support MC ${mcVersion}`);

  const buildsData = await fetchJson(`${SERVER.paperApiUrl}/versions/${mcVersion}/builds`);
  const builds = buildsData.builds as Array<{ build: number; downloads: Record<string, { name: string; sha256: string }> }>;
  if (!builds || builds.length === 0) throw new Error(`No Paper builds found for ${mcVersion}`);

  const latestBuild = builds[builds.length - 1];
  const appName = Object.keys(latestBuild.downloads)[0];
  if (!appName) throw new Error(`No download found in Paper build ${latestBuild.build}`);

  const download = latestBuild.downloads[appName];
  const url = `${SERVER.paperApiUrl}/versions/${mcVersion}/builds/${latestBuild.build}/downloads/${download.name}`;

  const jarPath = join(sDir, `paper-${mcVersion}-${latestBuild.build}.jar`);
  if (!existsSync(jarPath)) {
    onProgress?.(`Downloading Paper ${mcVersion} build ${latestBuild.build}...`);
    await downloadFile(url, jarPath);
  } else {
    onProgress?.(`Paper server JAR already exists.`);
  }
  return jarPath;
}

function buildCacheDir(): string {
  const d = join(tmpdir(), "minecraft-mcp-build");
  if (!existsSync(d)) mkdirSync(d, { recursive: true });
  return d;
}

function isValidPortableGit(dir: string): boolean {
  return existsSync(join(dir, "bin", "git.exe")) ||
    existsSync(join(dir, "bin", "git")) ||
    existsSync(join(dir, "PortableGit", "bin", "git.exe")) ||
    existsSync(join(dir, "PortableGit", "bin", "git"));
}

function ensurePortableGit(workDir: string): void {
  const cache = buildCacheDir();
  for (const entry of readdirSync(cache)) {
    if (!entry.startsWith("PortableGit")) continue;
    const src = join(cache, entry);
    if (!isValidPortableGit(src)) continue;
    const dst = join(workDir, entry);
    if (existsSync(dst) && isValidPortableGit(dst)) return;
    if (existsSync(dst)) {
      try { rmSync(dst, { recursive: true, force: true }); } catch { return; }
    }
    try {
      symlinkSync(src, dst, "junction");
      return;
    } catch { /* junction failed */ }
  }
}

async function runBuildTools(mcVersion: string, sDir: string, javaPath: string, targets: string[], onProgress?: (msg: string) => void): Promise<string> {
  const jarName = targets[0] === "craftbukkit" ? `craftbukkit-${mcVersion}.jar` : `spigot-${mcVersion}.jar`;
  const jarPath = join(sDir, jarName);
  if (existsSync(jarPath)) {
    onProgress?.(`${jarName} already exists.`);
    return jarPath;
  }

  const workDir = join(buildCacheDir(), `${mcVersion}-${targets[0]}`);
  if (!existsSync(workDir)) mkdirSync(workDir, { recursive: true });

  const buildToolsJar = join(workDir, "BuildTools.jar");
  if (!existsSync(buildToolsJar)) {
    onProgress?.(`Downloading BuildTools...`);
    await downloadFile(SERVER.buildToolsUrl, buildToolsJar);
  }

  ensurePortableGit(workDir);

  onProgress?.(`Compiling ${targets.join(" + ")} for ${mcVersion} via BuildTools (may take a few minutes)...`);
  const proxyArgs = javaProxyArgs();
  const sslArgs = proxyArgs.length > 0 ? ["-Djavax.net.ssl.trustStoreType=Windows-ROOT"] : [];
  const args = [...proxyArgs, ...sslArgs, "-jar", buildToolsJar, "--rev", mcVersion, ...targets.map(t => `--compile ${t}`).flatMap(s => s.split(" "))];

  return new Promise((resolve, reject) => {
    const child = spawn(javaPath, args, { cwd: workDir, stdio: ["ignore", "pipe", "pipe"], env: { ...process.env, ...gradleProxyEnv() } });
    let stderr = "";
    child.stdout?.on("data", (c: Buffer) => { const msg = c.toString().trim(); if (msg) onProgress?.(msg); });
    child.stderr?.on("data", (c: Buffer) => { stderr += c.toString(); });

    child.on("close", (code) => {
      let found: string | null = null;
      for (const sub of [workDir, join(workDir, "Spigot"), join(workDir, "CraftBukkit"), "."]) {
        const candidate = join(sub, jarName);
        if (existsSync(candidate)) { found = candidate; break; }
      }
      if (found) {
        mkdirSync(sDir, { recursive: true });
        copyFileSync(found, jarPath);
        onProgress?.(`${jarName} compiled successfully.`);
        resolve(jarPath);
        return;
      }
      reject(new Error(`BuildTools failed (exit ${code}): ${stderr.slice(-500)}`));
    });
    child.on("error", reject);
  });
}

function findForgeServerJar(forgeVersion: string): string | null {
  const patterns = [
    `forge-${forgeVersion}-universal.jar`,
    `forge-${forgeVersion}.jar`,
  ];
  const libBase = join(librariesDir(), "net", "minecraftforge", "forge", forgeVersion);
  for (const p of patterns) {
    const fullPath = join(libBase, p);
    if (existsSync(fullPath)) return fullPath;
  }
  return null;
}

function findFabricServerJar(mcVersion: string): string | null {
  const loaderVer = DEFAULT_FABRIC_LOADER_VERSION;
  const vId = `fabric-loader-${loaderVer}-${mcVersion}`;
  const vDir = join(versionsDir(), vId);
  if (!existsSync(vDir)) return null;

  const jarName = `fabric-server-launch.jar`;
  const libBase = join(librariesDir(), "net", "fabricmc", "fabric-loader", loaderVer);
  const universalPath = join(libBase, `fabric-loader-${loaderVer}.jar`);
  if (existsSync(universalPath)) return universalPath;

  for (const entry of readdirSync(vDir)) {
    if (entry.endsWith(".jar")) return join(vDir, entry);
  }
  return null;
}

async function installForgeServer(mcVersion: string, forgeVersion: string, sDir: string, javaPath: string, onProgress?: (msg: string) => void): Promise<string> {
  const vanillaJar = await ensureVanillaServerJar(mcVersion, sDir, onProgress);

  const serverJarName = `forge-${mcVersion}-${forgeVersion}-server.jar`;
  const serverJarPath = join(sDir, serverJarName);
  if (existsSync(serverJarPath)) {
    onProgress?.(`Forge server JAR already exists.`);
    return serverJarPath;
  }

  const forgeMavenBase = `${DOWNLOAD.forgeMavenUrl}net/minecraftforge/forge/${forgeVersion}`;
  const installerUrl = `${forgeMavenBase}/forge-${forgeVersion}-installer.jar`;
  const installerPath = join(sDir, `forge-${forgeVersion}-installer.jar`);

  if (!existsSync(installerPath)) {
    onProgress?.(`Downloading Forge installer ${forgeVersion}...`);
    await downloadFile(installerUrl, installerPath);
  }

  onProgress?.(`Running Forge server installer...`);
  await new Promise<void>((resolve, reject) => {
    const proxyArgs = javaProxyArgs();
    const child = spawn(javaPath, [...proxyArgs, "-jar", installerPath, "--installServer", sDir], {
      cwd: sDir,
      stdio: ["ignore", "pipe", "pipe"],
    });
    let stderr = "";
    child.stdout?.on("data", (c: Buffer) => { const msg = c.toString().trim(); if (msg) onProgress?.(msg); });
    child.stderr?.on("data", (c: Buffer) => { stderr += c.toString(); });
    child.on("close", (code) => {
      if (code !== 0 && !existsSync(serverJarPath)) {
        const altNames = [
          `forge-${forgeVersion}.jar`,
          `${mcVersion}-${forgeVersion}-server.jar`,
        ];
        for (const alt of altNames) {
          const altPath = join(sDir, alt);
          if (existsSync(altPath)) { resolve(); return; }
        }
      }
      if (existsSync(serverJarPath)) resolve();
      else {
        for (const entry of readdirSync(sDir)) {
          if (entry.includes("forge") && entry.endsWith(".jar") && !entry.includes("installer")) {
            resolve();
            return;
          }
        }
        reject(new Error(`Forge server install failed (exit ${code}): ${stderr.slice(-500)}`));
      }
    });
    child.on("error", reject);
  });

  for (const entry of readdirSync(sDir)) {
    if (entry.includes("forge") && entry.endsWith(".jar") && !entry.includes("installer")) {
      return join(sDir, entry);
    }
  }
  return serverJarPath;
}

async function installFabricServer(mcVersion: string, sDir: string, javaPath: string, onProgress?: (msg: string) => void): Promise<string> {
  const vanillaJar = await ensureVanillaServerJar(mcVersion, sDir, onProgress);

  for (const name of ["fabric-server-launch.jar", `fabric-server-launch-${mcVersion}.jar`]) {
    if (existsSync(join(sDir, name))) {
      onProgress?.(`Fabric server JAR already exists.`);
      return join(sDir, name);
    }
  }

  const metaResp = await fetchWithFallback(`${DOWNLOAD.fabricMetaUrl}/${mcVersion}`);
  if (!metaResp.ok) throw new Error(`Failed to fetch Fabric loaders for ${mcVersion}: HTTP ${metaResp.status}`);
  const loaders = await metaResp.json() as Array<{ loader: { version: string; stable: boolean } }>;
  const stableLoader = loaders.find(l => l.loader.stable);
  const loaderVer = stableLoader?.loader.version ?? loaders[0]?.loader.version;
  if (!loaderVer) throw new Error(`No Fabric loader found for ${mcVersion}`);

  const installerUrl = `https://maven.fabricmc.net/net/fabricmc/fabric-installer/0.11.2/fabric-installer-0.11.2.jar`;
  const installerPath = join(sDir, "fabric-installer.jar");
  if (!existsSync(installerPath)) {
    onProgress?.(`Downloading Fabric installer...`);
    await downloadFile(installerUrl, installerPath);
  }

  onProgress?.(`Running Fabric server installer (loader ${loaderVer})...`);
  await new Promise<void>((resolve, reject) => {
    const child = spawn(javaPath, [
      "-jar", installerPath, "server",
      "-mcversion", mcVersion,
      "-loader", loaderVer,
      "-dir", sDir,
      "-downloadMinecraft",
    ], { cwd: sDir, stdio: ["ignore", "pipe", "pipe"] });
    let stderr = "";
    child.stdout?.on("data", (c: Buffer) => { const msg = c.toString().trim(); if (msg) onProgress?.(msg); });
    child.stderr?.on("data", (c: Buffer) => { stderr += c.toString(); });
    child.on("close", (code) => {
      if (existsSync(join(sDir, "fabric-server-launch.jar"))) resolve();
      else reject(new Error(`Fabric installer failed (exit ${code}): ${stderr.slice(-500)}`));
    });
    child.on("error", reject);
  });

  return join(sDir, "fabric-server-launch.jar");
}

async function installNeoForgeServer(neoforgeVersion: string, mcVersion: string, sDir: string, javaPath: string, onProgress?: (msg: string) => void): Promise<string> {
  const vanillaJar = await ensureVanillaServerJar(mcVersion, sDir, onProgress);

  const serverJarName = `neoforge-${neoforgeVersion}-server.jar`;
  const serverJarPath = join(sDir, serverJarName);
  if (existsSync(serverJarPath)) {
    onProgress?.(`NeoForge server JAR already exists.`);
    return serverJarPath;
  }

  const installerUrl = `${DOWNLOAD.neoforgeMavenUrl}net/neoforged/neoforge/${neoforgeVersion}/neoforge-${neoforgeVersion}-installer.jar`;
  const installerPath = join(sDir, `neoforge-${neoforgeVersion}-installer.jar`);

  if (!existsSync(installerPath)) {
    onProgress?.(`Downloading NeoForge installer ${neoforgeVersion}...`);
    await downloadFile(installerUrl, installerPath);
  }

  onProgress?.(`Running NeoForge server installer...`);
  const proxyArgs = javaProxyArgs();
  await new Promise<void>((resolve, reject) => {
    const child = spawn(javaPath, [...proxyArgs, "-jar", installerPath, "--installServer", sDir], {
      cwd: sDir,
      stdio: ["ignore", "pipe", "pipe"],
    });
    let stderr = "";
    child.stdout?.on("data", (c: Buffer) => { const msg = c.toString().trim(); if (msg) onProgress?.(msg); });
    child.stderr?.on("data", (c: Buffer) => { stderr += c.toString(); });
    child.on("close", (code) => {
      for (const entry of readdirSync(sDir)) {
        if ((entry.includes("neoforge") || entry.includes("server")) && entry.endsWith(".jar") && !entry.includes("installer")) {
          resolve();
          return;
        }
      }
      reject(new Error(`NeoForge server install failed (exit ${code}): ${stderr.slice(-500)}`));
    });
    child.on("error", reject);
  });

  const winArgs = join(sDir, "libraries", "net", "neoforged", "neoforge", neoforgeVersion, "win_args.txt");
  if (existsSync(winArgs)) return winArgs;

  for (const entry of readdirSync(sDir)) {
    if (entry.includes("neoforge") && entry.endsWith(".jar") && !entry.includes("installer")) {
      return join(sDir, entry);
    }
  }
  return serverJarPath;
}

export async function installServer(
  version: string,
  loader: Loader,
  onProgress?: (msg: string) => void,
  serverType?: ServerType,
  properties?: ServerProperties,
): Promise<ServerSetup> {
  const data = loadVersionsData();
  const vi = getVersion(data, version);

  let versionId: string;
  let mcVersion: string;
  let javaVersion = SERVER.defaultJavaVersion;
  let forgeVersion: string | undefined;
  let neoforgeVersion: string | undefined;

  if (vi) {
    const resolved = getVersionForLoader(data, version, loader);
    if (!resolved) throw new Error(`${loader} not available for ${version}`);
    versionId = resolved;
    mcVersion = vi.mc_version;
    javaVersion = vi.java ?? SERVER.defaultJavaVersion;
    forgeVersion = vi.forge || undefined;
    neoforgeVersion = vi.neoforge || undefined;
  } else {
    versionId = version;
    mcVersion = version;
  }

  const effectiveType = serverType ?? SERVER.defaultType;
  const sDir = serverDir(`${versionId}-${effectiveType}`);
  if (!existsSync(sDir)) mkdirSync(sDir, { recursive: true });

  const javaPath = findJavaForVersion(javaVersion);
  let jarPath: string;

  switch (effectiveType) {
    case "paper": {
      jarPath = await downloadPaperServer(mcVersion, sDir, onProgress);
      break;
    }
    case "spigot": {
      jarPath = await runBuildTools(mcVersion, sDir, javaPath, ["spigot"], onProgress);
      break;
    }
    case "craftbukkit": {
      jarPath = await runBuildTools(mcVersion, sDir, javaPath, ["craftbukkit"], onProgress);
      break;
    }
    case "forge": {
      if (!forgeVersion) throw new Error(`Forge not available for ${version}`);
      await downloadForgeInstaller(forgeVersion, onProgress);
      jarPath = await installForgeServer(mcVersion, forgeVersion, sDir, javaPath, onProgress);
      break;
    }
    case "fabric": {
      const loaderVer = DEFAULT_FABRIC_LOADER_VERSION;
      await downloadFabricLoader(mcVersion, loaderVer, onProgress);
      jarPath = await installFabricServer(mcVersion, sDir, javaPath, onProgress);
      break;
    }
    case "neoforge": {
      if (!neoforgeVersion) throw new Error(`NeoForge not available for ${version}`);
      await downloadNeoforgeInstaller(neoforgeVersion, onProgress);
      jarPath = await installNeoForgeServer(neoforgeVersion, mcVersion, sDir, javaPath, onProgress);
      break;
    }
    case "vanilla":
    default: {
      jarPath = await ensureVanillaServerJar(mcVersion, sDir, onProgress);
      break;
    }
  }

  const eulaPath = join(sDir, SERVER.eulaFileName);
  if (!existsSync(eulaPath)) writeFileSync(eulaPath, "eula=true\n", "utf-8");

  const propsPath = join(sDir, SERVER.propertiesFileName);
  const mergedProps: ServerProperties = { ...properties };
  if (mergedProps.serverPort == null) mergedProps.serverPort = GAME.defaultServerPort;
  writeFileSync(propsPath, generateServerProperties(mergedProps), "utf-8");

  const modsDir = join(sDir, SERVER.modsDirName);
  if (!existsSync(modsDir)) mkdirSync(modsDir, { recursive: true });

  onProgress?.(`Server installed at ${sDir}`);
  return { serverDir: sDir, jarPath, versionId, mcVersion, serverType: effectiveType, javaVersion };
}

export function launchServer(
  setup: ServerSetup,
  opts?: LaunchServerOpts,
): LaunchedServer {
  const port = opts?.port ?? GAME.defaultServerPort;
  const javaVersion = opts?.javaVersion ?? setup.javaVersion;
  const java = opts?.javaPath || findJavaForVersion(javaVersion);
  const maxMem = opts?.maxMemoryMb ?? GAME.defaultServerMemoryMb;

  const isNeoForgeScript = setup.jarPath.endsWith(".txt");
  const isRunScript = setup.jarPath.endsWith("run.bat") || setup.jarPath.endsWith("run.sh");

  const args: string[] = [];
  if (isNeoForgeScript) {
    args.push(`-Xmx${maxMem}m`);
    if (opts?.minMemoryMb) args.push(`-Xms${opts.minMemoryMb}m`);
    args.push(`@${setup.jarPath}`, "--nogui");
  } else if (isRunScript) {
    args.push(setup.jarPath);
  } else {
    args.push(`-Xmx${maxMem}m`);
    if (opts?.minMemoryMb) args.push(`-Xms${opts.minMemoryMb}m`);
    args.push(`-Dmcp.port=0`);
    if (opts?.extraJvmArgs) {
      for (const arg of opts.extraJvmArgs.split(/\s+/)) {
        if (arg) args.push(arg);
      }
    }
    args.push(`-jar`, setup.jarPath, "--nogui");
  }

  if (opts?.extraGameArgs && !isNeoForgeScript) {
    for (const arg of opts.extraGameArgs.split(/\s+/)) {
      if (arg) args.push(arg);
    }
  }

  const logPath = join(setup.serverDir, "server-launch.log");
  const logFd = openSync(logPath, "a");
  const launchCmd = isRunScript ? setup.jarPath : java;
  const prefix = `\n[${new Date().toISOString()}] Launching: ${launchCmd} ${args.join(" ")}\n`;
  writeFileSync(logFd, prefix);

  const child = spawn(launchCmd, isRunScript ? [] : args, {
    cwd: setup.serverDir,
    stdio: ["ignore", logFd, logFd],
    detached: process.platform !== "win32",
    shell: isRunScript,
  });

  child.on("error", (err) => {
    console.error(`[${SERVER.userAgent}] Server launch failed: ${err.message}`);
  });

  return { process: child, port, dir: setup.serverDir };
}

export async function waitForServer(sDir: string, timeoutMs: number = 120_000): Promise<boolean> {
  const logPath = join(sDir, "logs", "latest.log");
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (existsSync(logPath)) {
      try {
        const content = readFileSync(logPath, "utf-8");
        if (content.includes("Done (") && content.includes("For help, type \"help\"")) {
          return true;
        }
      } catch {}
    }
    await new Promise(r => setTimeout(r, 2000));
  }
  return false;
}

export interface CompatPair {
  clientLoader: Loader;
  serverType: ServerType;
  valid: boolean;
  reason: string;
}

export function getCompatPairs(version: string): CompatPair[] {
  const data = loadVersionsData();
  const vi = getVersion(data, version);
  if (!vi) return [];

  const clientLoaders: Loader[] = [];
  if (vi.forge) clientLoaders.push("forge");
  if (vi.fabric_yarn) clientLoaders.push("fabric");
  if (vi.neoforge) clientLoaders.push("neoforge");

  const parts = version.split(".").map(Number);
  const major = parts[0] ?? 0;
  const minor = parts[1] ?? 0;
  const patch = parts[2] ?? 0;
  const mcAtLeast = (maj: number, mn: number, pat: number) =>
    major > maj || (major === maj && minor > mn) || (major === maj && minor === mn && patch >= pat);

  const serverTypes: ServerType[] = ["vanilla"];
  if (mcAtLeast(1, 8, 0)) serverTypes.push("spigot", "craftbukkit");
  if (mcAtLeast(1, 8, 0)) serverTypes.push("paper");
  if (vi.forge) serverTypes.push("forge");
  if (vi.fabric_yarn) serverTypes.push("fabric");
  if (vi.neoforge) serverTypes.push("neoforge");

  const pairs: CompatPair[] = [];

  for (const cl of clientLoaders) {
    for (const st of serverTypes) {
      const isModLoader = cl === "forge" || cl === "fabric" || cl === "neoforge";
      const isModServer = st === "forge" || st === "fabric" || st === "neoforge";
      const isPluginServer = st === "spigot" || st === "craftbukkit" || st === "paper";

      let valid = false;
      let reason = "";

      if (cl === st) {
        valid = true;
        reason = "Same framework, full compatibility";
      } else if (isModLoader && st === "vanilla") {
        valid = true;
        reason = "Vanilla server accepts any client (mods inactive)";
      } else if (isModLoader && isPluginServer) {
        valid = true;
        reason = "Plugin server accepts vanilla protocol clients";
      } else if (isModLoader && isModServer && cl !== st) {
        valid = false;
        reason = `${cl} client incompatible with ${st} server`;
      } else {
        valid = true;
        reason = "Compatible";
      }

      pairs.push({ clientLoader: cl, serverType: st, valid, reason });
    }
  }

  return pairs;
}

import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { serverDir } from "./settings.js";
import { downloadFile } from "./download.js";
import { loadVersion } from "./version-json.js";
import { spawn } from "node:child_process";
import { findJavaForVersion } from "./platform.js";
import { loadVersionsData } from "./versions-data.js";
import { getVersion, getVersionForLoader, type Loader } from "./versions.js";
import { DOWNLOAD, SERVER, GAME } from "./defaults.js";
import type { ChildProcess } from "node:child_process";

const SERVER_PROPERTIES = `# MCP auto-generated server.properties
enable-jmx-monitoring=false
rcon.port=${SERVER.rconPort}
level-seed=
gamemode=survival
enable-command-block=true
enable-query=false
generator-settings={"layers":[{"block":"bedrock","height":1},{"block":"dirt","height":2},{"block":"grass_block","height":1}],"biome":"plains"}
level-name=world
motd=MCP Server
query.port=${GAME.defaultServerPort}
pvp=true
generate-structures=false
max-chained-neighbor-updates=1000000
difficulty=easy
network-compression-threshold=${SERVER.networkCompressionThreshold}
max-tick-time=${SERVER.maxTickTime}
require-resource-pack=false
use-native-transport=true
max-players=${SERVER.maxPlayers}
online-mode=false
enable-status=true
allow-flight=true
initial-disabled-packs=
broadcast-rcon-to-ops=true
view-distance=${SERVER.viewDistance}
resource-pack=
server-ip=${SERVER.bindAddress}
resource-pack-prompt=
allow-nether=true
server-port=${GAME.defaultServerPort}
enable-rcon=false
sync-chunk-writes=true
op-permission-level=4
prevent-proxy-connections=false
hide-online-players=false
resource-pack-sha1=
entity-broadcast-range-percentage=100
simulation-distance=${SERVER.simulationDistance}
rcon.password=
player-idle-timeout=0
force-gamemode=false
rate-limit=0
hardcore=false
white-list=false
broadcast-console-to-ops=true
spawn-npcs=true
spawn-animals=true
function-permission-level=2
initial-enabled-packs=vanilla
level-type=flat
text-filtering-config=
spawn-monsters=true
enforce-whitelist=false
max-world-size=${SERVER.maxWorldSize}
`;

export interface ServerSetup {
  serverDir: string;
  jarPath: string;
  versionId: string;
  mcVersion: string;
}

const MANIFEST_URL = DOWNLOAD.versionManifestV2Url;

interface ServerDownloadInfo {
  url: string;
  sha1?: string;
}

async function fetchServerUrl(mcVersion: string): Promise<ServerDownloadInfo> {
  const manifest = await fetchJson(MANIFEST_URL);
  const entry = manifest.versions?.find((v: { id: string }) => v.id === mcVersion);
  if (!entry?.url) throw new Error(`MC ${mcVersion} not found in version manifest`);
  const vj = await fetchJson(entry.url);
  const server = vj?.downloads?.server;
  if (!server?.url) throw new Error(`No server download for ${mcVersion}`);
  return { url: server.url, sha1: server.sha1 };
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

export async function installServer(
  version: string,
  loader: Loader,
  onProgress?: (msg: string) => void,
): Promise<ServerSetup> {
  const data = loadVersionsData();
  const vi = getVersion(data, version);

  let versionId: string;
  let mcVersion: string;

  if (vi) {
    const resolved = getVersionForLoader(data, version, loader);
    if (!resolved) throw new Error(`${loader} not available for ${version}`);
    versionId = resolved;
    mcVersion = vi.mc_version;
  } else {
    versionId = version;
    mcVersion = version;
  }

  let serverUrl: string | undefined;
  let serverSha: string | undefined;

  try {
    const baseVj = loadVersion(mcVersion);
    serverUrl = baseVj.downloads?.server?.url;
    serverSha = baseVj.downloads?.server?.sha1;
  } catch {
    // vanilla version JSON not installed locally, fetch from Mojang API
  }

  if (!serverUrl) {
    onProgress?.(`Fetching server info for ${mcVersion} from Mojang...`);
    const info = await fetchServerUrl(mcVersion);
    serverUrl = info.url;
    serverSha = info.sha1;
  }

  const sDir = serverDir(versionId);
  if (!existsSync(sDir)) mkdirSync(sDir, { recursive: true });

  const jarPath = join(sDir, `server-${mcVersion}.jar`);
  if (!existsSync(jarPath)) {
    if (!serverUrl) throw new Error(`No server download available for ${mcVersion}`);
    onProgress?.(`Downloading server JAR for ${mcVersion}...`);
    await downloadFile(serverUrl, jarPath, serverSha);
  } else {
    onProgress?.(`Server JAR already exists.`);
  }

  const eulaPath = join(sDir, SERVER.eulaFileName);
  if (!existsSync(eulaPath)) {
    writeFileSync(eulaPath, "eula=true\n", "utf-8");
  }

  const propsPath = join(sDir, SERVER.propertiesFileName);
  if (!existsSync(propsPath)) {
    writeFileSync(propsPath, SERVER_PROPERTIES, "utf-8");
  }

  const modsDir = join(sDir, SERVER.modsDirName);
  if (!existsSync(modsDir)) mkdirSync(modsDir, { recursive: true });

  onProgress?.(`Server installed at ${sDir}`);
  return { serverDir: sDir, jarPath, versionId, mcVersion };
}

export interface LaunchedServer {
  process: ChildProcess;
  port: number;
  dir: string;
}

export function launchServer(
  setup: ServerSetup,
  opts?: { javaPath?: string; maxMemoryMb?: number },
): LaunchedServer {
  const port = GAME.defaultServerPort;
  const java = opts?.javaPath || findJavaForVersion(SERVER.defaultJavaVersion);
  const maxMem = opts?.maxMemoryMb ?? GAME.defaultServerMemoryMb;

  const args = [
    `-Xmx${maxMem}m`,
    `-Dmcp.port=0`,
    `-jar`, setup.jarPath,
    "--nogui",
  ];

  const child = spawn(java, args, {
    cwd: setup.serverDir,
    stdio: "ignore",
    detached: process.platform !== "win32",
  });

  child.on("error", (err) => {
    console.error(`[${SERVER.userAgent}] Server launch failed: ${err.message}`);
  });

  return { process: child, port, dir: setup.serverDir };
}

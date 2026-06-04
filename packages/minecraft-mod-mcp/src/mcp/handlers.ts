import { TOOLS } from "./tools.js";
import type { ModClient } from "../api/modClient.js";
import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { GAME, PLAYER, SERVER } from "../mc/defaults.js";
import { spawn } from "node:child_process";
import { join } from "node:path";
import { loadVersionsData } from "../mc/versionsData.js";
import { getVersion, getVersionForLoader, getVersions, loaders, type Loader, DEFAULT_FABRIC_LOADER_VERSION } from "../mc/versions.js";
import { loadVersionMerged } from "../mc/versionJson.js";
import { buildLaunchCommand } from "../mc/launch.js";
import { loadConfig, saveConfig, addAccount, selectedAccount, gameDirPath, javaExecPath, accountUuid, accountUsername, accountAccessToken, accountUserType, type Account } from "../mc/settings.js";
import { findFreePort } from "../discovery/scanner.js";
import { fetchVersionManifest, fetchVersionJson, downloadVersion, listInstalledVersions, downloadLoaderVersion, ensureVersionInstalled } from "../mc/download.js";
import { detectJavas } from "../mc/javaDetect.js";
import { createOfflineUuid } from "../mc/auth.js";
import { versionsDir } from "../mc/platform.js";
import { existsSync, mkdirSync } from "node:fs";

const MANAGEMENT_TOOLS = new Set([
  "launch_minecraft", "kill_minecraft", "get_minecraft_status",
  "install_version", "list_supported_versions", "list_installed_versions",
  "detect_java", "create_offline_account", "list_accounts", "wait",
  "install_server", "launch_server", "serve",
]);

export function registerHandlers(server: McpServer, mod: ModClient) {
  for (const tool of TOOLS) {
    server.registerTool(tool.name, {
      description: tool.description,
      inputSchema: tool.inputSchema as any,
    }, async (params: any) => {
      try {
        if (!mod.connected && !MANAGEMENT_TOOLS.has(tool.name)) {
          await mod.discover();
        }
        const result = await handleTool(tool.name, params, mod);
        return { content: [{ type: "text" as const, text: formatResult(result) }] };
      } catch (err: any) {
        return {
          content: [{ type: "text" as const, text: `Error: ${err.message}` }],
          isError: true,
        };
      }
    });
  }
}

async function handleTool(name: string, params: Record<string, unknown>, mod: ModClient): Promise<unknown> {
  switch (name) {
    case "ping":
      return mod.sendCommand("ping");
    case "screenshot":
      return mod.screenshot();
    case "screenshot_to_file":
      return mod.sendCommand("screenshot_to_file", params);
    case "get_player_info":
      return mod.sendCommand("get_player_info");
    case "get_world_info":
      return mod.sendCommand("get_world_info");
    case "debug_fields":
      return mod.sendCommand("debug_fields");
    case "get_screen_buttons":
      return mod.sendCommand("get_screen_buttons");
    case "enumerate_widgets":
      return mod.sendCommand("enumerate_widgets");
    case "enter_control_mode":
      return mod.sendCommand("enter_control_mode");
    case "exit_control_mode":
      return mod.sendCommand("exit_control_mode");
    case "release_mouse":
      return mod.sendCommand("release_mouse");
    case "pause_game":
      return mod.sendCommand("pause_game");
    case "close_screen":
      return mod.sendCommand("close_screen");
    case "open_chat":
      return mod.sendCommand("open_chat");
    case "set_gamemode":
      return mod.sendCommand("set_gamemode", params);
    case "click":
      return mod.sendCommand("click", params);
    case "right_click":
      return mod.sendCommand("right_click");
    case "mouse_drag":
      return mod.sendCommand("mouse_drag", mapDragParams(params));
    case "scroll":
      return mod.sendCommand("scroll", params);
    case "scroll_at":
      return mod.sendCommand("scroll_at", params);
    case "press_key":
      return mod.sendCommand("press_key", params);
    case "type_text":
      return mod.sendCommand("type_text", params);
    case "paste_text":
      return mod.sendCommand("paste_text", params);
    case "hotkey":
      return mod.sendCommand("hotkey", params);
    case "click_button_id":
      return mod.sendCommand("click_button_id", params);
    case "click_button_index":
      return mod.sendCommand("click_button_index", params);
    case "switch_tab":
      return mod.sendCommand("switch_tab", params);
    case "execute_command":
      return mod.sendCommand("execute_command", params);
    case "set_view_angle":
      return mod.sendCommand("set_view_angle", params);
    case "look_delta":
      return mod.sendCommand("look_delta", params);
    case "use_item":
      return mod.sendCommand("use_item");
    case "place_block":
      return mod.sendCommand("place_block");
    case "overlay_click":
      return mod.sendCommand("overlay_click", params);
    case "wait": {
      const seconds = Number(params.seconds) || 1;
      await new Promise((r) => setTimeout(r, seconds * 1000));
      return { waited: seconds };
    }
    case "launch_minecraft": {
      return await launchMinecraft(params, mod);
    }
    case "kill_minecraft": {
      mod.killMc();
      return { killed: true };
    }
    case "get_minecraft_status": {
      await mod.checkAlive();
      return mod.getStatus();
    }
    case "install_version": {
      return await installVersion(params);
    }
    case "list_supported_versions": {
      return listSupportedVersions();
    }
    case "list_installed_versions": {
      const installed = listInstalledVersions();
      return { versions: installed, count: installed.length };
    }
    case "detect_java": {
      const javas = detectJavas();
      return javas.map((j) => ({
        version: j.version,
        vendor: j.vendor,
        isJdk: j.isJdk,
        path: j.path,
      }));
    }
    case "create_offline_account": {
      const username = String(params.username || "");
      if (!username) throw new Error("Parameter 'username' is required.");
      const uuid = createOfflineUuid(username);
      const account: Account = { type: "offline", uuid, username };
      const config = loadConfig();
      addAccount(config, account);
      if (!config.selected_account) config.selected_account = uuid;
      saveConfig(config);
      return { created: true, username, uuid };
    }
    case "list_accounts": {
      const config = loadConfig();
      return config.accounts.map((a) => ({
        username: accountUsername(a),
        uuid: accountUuid(a),
        type: a.type,
        selected: accountUuid(a) === config.selected_account,
      }));
    }
    case "install_server": {
      return await installServerTool(params);
    }
    case "launch_server": {
      return await launchServerTool(params, mod);
    }
    case "serve": {
      return await serveTool(params, mod);
    }
    default:
      throw new Error(`Unknown tool: ${name}`);
  }
}

async function launchMinecraft(params: Record<string, unknown>, mod: ModClient): Promise<unknown> {
  const version = String(params.version || "");
  const loader = String(params.loader || "forge") as Loader;
  if (!version) throw new Error("Parameter 'version' is required. Use list_supported_versions to see available versions.");

  const logs: string[] = [];
  const versionId = await ensureVersionInstalled(version, loader, (msg) => logs.push(msg));
  const vj = loadVersionMerged(versionId);
  const config = loadConfig();
  const account = selectedAccount(config);
  const mcpPort = config.mcp_port ?? await findFreePort();

  const cmd = buildLaunchCommand({
    versionId,
    loader,
    mcpPort,
    maxMemoryMb: config.max_memory_mb,
    minMemoryMb: config.min_memory_mb,
    extraJvmArgs: config.java_args,
    extraGameArgs: config.game_args,
    javaPath: javaExecPath(config) ?? undefined,
    playerName: account ? accountUsername(account) : PLAYER.defaultName,
    uuid: account ? accountUuid(account) : PLAYER.defaultUuid,
    accessToken: account ? accountAccessToken(account) : PLAYER.defaultAccessToken,
    userType: account ? accountUserType(account) : PLAYER.defaultUserType,
  }, vj, loadVersionsData());

  const mcDir_ = gameDirPath(config);
  if (!existsSync(mcDir_)) mkdirSync(mcDir_, { recursive: true });

  const child = spawn(cmd.java, cmd.args, {
    cwd: mcDir_,
    stdio: "ignore",
    detached: process.platform !== "win32",
  });

  child.on("error", (err) => {
    console.error(`[minecraft-mod-mcp] Launch failed: ${err.message}`);
  });

  mod.setMcProcess(child);
  return { launched: true, version: versionId, loader, pid: child.pid, mcpPort, javaVersion: cmd.javaVersion, logs };
}

async function installVersion(params: Record<string, unknown>): Promise<unknown> {
  const version = String(params.version || "");
  const loader = String(params.loader || "forge") as Loader;
  if (!version) throw new Error("Parameter 'version' is required. Use list_supported_versions to see available versions.");

  const data = loadVersionsData();
  const vi = getVersion(data, version);
  if (!vi) {
    const available = getVersions(data).map((v) => v.mc_version).join(", ");
    throw new Error(`Unknown version: "${version}". Available: ${available}`);
  }

  const mcVersion = vi.mc_version;
  const baseVersionDir = join(versionsDir(), mcVersion);
  const baseJsonPath = join(baseVersionDir, `${mcVersion}.json`);

  const logs: string[] = [];

  if (!existsSync(baseJsonPath)) {
    logs.push(`Downloading base MC ${mcVersion}...`);
    const manifest = await fetchVersionManifest();
    const mv = manifest.versions.find((v) => v.id === mcVersion);
    if (!mv) throw new Error(`Base MC version ${mcVersion} not found in manifest.`);
    const baseVj = await fetchVersionJson(mv.url);
    await downloadVersion(baseVj, (msg) => logs.push(msg));
  } else {
    logs.push(`Base MC ${mcVersion} already installed.`);
  }

  let loaderVersion: string | undefined;
  if (loader === "forge" && vi.forge) loaderVersion = vi.forge;
  else if (loader === "neoforge" && vi.neoforge) loaderVersion = vi.neoforge;
  else if (loader === "fabric") loaderVersion = DEFAULT_FABRIC_LOADER_VERSION;

  if (loaderVersion) {
    logs.push(`Installing ${loader} ${loaderVersion}...`);
    await downloadLoaderVersion(mcVersion, loader, loaderVersion, (msg) => logs.push(msg));
  }

  const versionId = getVersionForLoader(data, version, loader) ?? vi.version_id;

  return { installed: true, versionId, loader, loaderVersion, logs };
}

function listSupportedVersions(): unknown {
  const data = loadVersionsData();
  const versions = getVersions(data);
  return versions.map((v) => ({
    mc_version: v.mc_version,
    java: v.java,
    loaders: loaders(v),
    version_id: v.version_id,
  }));
}

function mapDragParams(params: Record<string, unknown>): Record<string, unknown> {
  return {
    x1: params.x_start,
    y1: params.y_start,
    x2: params.x_end,
    y2: params.y_end,
    button: params.button,
  };
}

function formatResult(result: unknown): string {
  if (result === null || result === undefined) return "null";
  if (typeof result === "string") return result;
  return JSON.stringify(result, null, 2);
}

async function installServerTool(params: Record<string, unknown>): Promise<unknown> {
  const { installServer } = await import("../mc/server.js");
  const version = String(params.version || "");
  const loader = String(params.loader || "forge") as Loader;
  if (!version) throw new Error("Parameter 'version' is required.");

  const logs: string[] = [];
  const setup = await installServer(version, loader, (msg) => logs.push(msg));
  return { installed: true, serverDir: setup.serverDir, jarPath: setup.jarPath, versionId: setup.versionId, logs };
}

async function launchServerTool(params: Record<string, unknown>, _mod: ModClient): Promise<unknown> {
  const { installServer, launchServer } = await import("../mc/server.js");
  const version = String(params.version || "");
  const loader = String(params.loader || "forge") as Loader;
  const memory = Number(params.memory) || GAME.defaultServerMemoryMb;
  if (!version) throw new Error("Parameter 'version' is required.");

  const setup = await installServer(version, loader);
  const srv = launchServer(setup, { maxMemoryMb: memory });
  return { launched: true, pid: srv.process.pid, port: srv.port, dir: srv.dir };
}

async function serveTool(params: Record<string, unknown>, mod: ModClient): Promise<unknown> {
  const { installServer, launchServer } = await import("../mc/server.js");
  const version = String(params.version || "");
  const loader = String(params.loader || "forge") as Loader;
  const clientMem = Number(params.memory) || GAME.defaultMaxMemoryMb;
  const serverMem = Number(params.server_memory) || GAME.defaultServerMemoryMb;
  if (!version) throw new Error("Parameter 'version' is required.");

  const setup = await installServer(version, loader);
  const srv = launchServer(setup, { maxMemoryMb: serverMem });

  await new Promise((r) => setTimeout(r, GAME.serverStartupWaitMs));

  const versionId = await ensureVersionInstalled(version, loader);
  const vj = loadVersionMerged(versionId);
  const config = loadConfig();
  const account = selectedAccount(config);
  const mcpPort = config.mcp_port ?? await findFreePort();

  const cmd = buildLaunchCommand({
    versionId,
    loader,
    mcpPort,
    maxMemoryMb: clientMem,
    minMemoryMb: config.min_memory_mb,
    extraJvmArgs: config.java_args,
    extraGameArgs: `--server ${SERVER.connectHost} --port ${srv.port}`,
    javaPath: javaExecPath(config) ?? undefined,
    playerName: account ? accountUsername(account) : PLAYER.defaultName,
    uuid: account ? accountUuid(account) : PLAYER.defaultUuid,
    accessToken: account ? accountAccessToken(account) : PLAYER.defaultAccessToken,
    userType: account ? accountUserType(account) : PLAYER.defaultUserType,
  }, vj, loadVersionsData());

  const mcDir_ = gameDirPath(config);
  if (!existsSync(mcDir_)) mkdirSync(mcDir_, { recursive: true });

  const child = spawn(cmd.java, cmd.args, {
    cwd: mcDir_,
    stdio: "ignore",
    detached: process.platform !== "win32",
  });

  mod.setMcProcess(child);
  return {
    server: { pid: srv.process.pid, port: srv.port, dir: srv.dir },
    client: { pid: child.pid, version: versionId, mcpPort, connectingTo: `${SERVER.connectHost}:${srv.port}` },
  };
}

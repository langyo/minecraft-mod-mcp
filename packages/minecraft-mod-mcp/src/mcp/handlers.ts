import { TOOLS } from "./tools.js";
import type { ModClient } from "../api/mod-client.js";
import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { spawn } from "node:child_process";
import { join } from "node:path";
import { loadVersionsData } from "../mc/versions-data.js";
import { getVersion, getVersionForLoader, getVersionById, type Loader } from "../mc/versions.js";
import { loadVersion } from "../mc/version-json.js";
import { buildLaunchCommand } from "../mc/launch.js";
import { loadConfig, selectedAccount, gameDirPath, javaExecPath, accountUuid, accountUsername, accountAccessToken, accountUserType } from "../mc/settings.js";
import { findFreePort } from "../discovery/scanner.js";

export function registerHandlers(server: McpServer, mod: ModClient) {
  for (const tool of TOOLS) {
    server.registerTool(tool.name, {
      description: tool.description,
      inputSchema: tool.inputSchema as any,
    }, async (params: any) => {
      try {
        if (!mod.connected && tool.name !== "launch_minecraft") {
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
      const version = String(params.version || "");
      const loader = String(params.loader || "forge") as Loader;
      const data = loadVersionsData();

      let versionId = version;
      const vi = getVersion(data, version);
      if (vi) {
        versionId = getVersionForLoader(data, version, loader) ?? vi.version_id;
      }

      if (!versionId) throw new Error(`Unknown version: ${version}`);

      const vj = loadVersion(versionId);
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
        playerName: account ? accountUsername(account) : "Player",
        uuid: account ? accountUuid(account) : "0",
        accessToken: account ? accountAccessToken(account) : "0",
        userType: account ? accountUserType(account) : "legacy",
      }, vj, data);

      const mcDir_ = gameDirPath(config);
      const child = spawn(cmd.java, cmd.args, {
        cwd: mcDir_,
        stdio: "ignore",
        detached: process.platform !== "win32",
      });

      mod.setMcProcess(child);
      return { launched: true, version: versionId, loader, pid: child.pid, mcpPort };
    }
    case "kill_minecraft": {
      mod.killMc();
      return { killed: true };
    }
    case "get_minecraft_status": {
      await mod.checkAlive();
      return mod.getStatus();
    }
    default:
      throw new Error(`Unknown tool: ${name}`);
  }
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

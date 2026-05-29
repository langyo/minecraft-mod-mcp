import { TOOLS } from "./tools.js";
import type { ModClient } from "../api/mod-client.js";
import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { spawn } from "node:child_process";
import { resolve, join } from "node:path";
import { existsSync } from "node:fs";
import { PORT_START, PORT_END } from "../consts.js";

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
      const loader = String(params.loader || "forge");
      const scriptPath = findLaunchScript();
      if (!scriptPath) throw new Error("launch_mc.py not found. Run from minecraft-mcp project root or set MINECRAFT_MCP_HOME.");
      const proc = spawn("python", [scriptPath, `${version}-${loader}`], {
        stdio: "pipe",
        env: { ...process.env },
      });
      mod.setMcProcess(proc);
      return { launched: true, version, loader, pid: proc.pid };
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

export function findLaunchScript(): string | null {
  const candidates = [
    resolve("scripts/launch_mc.py"),
    resolve("..", "scripts/launch_mc.py"),
    resolve("..", "..", "scripts/launch_mc.py"),
  ];
  const home = process.env.MINECRAFT_MCP_HOME;
  if (home) candidates.push(join(home, "scripts/launch_mc.py"));
  for (const p of candidates) {
    if (existsSync(p)) return p;
  }
  return null;
}

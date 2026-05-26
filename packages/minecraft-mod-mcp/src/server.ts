import { createApiApp } from "./api/http.js";
import { ModClient } from "./api/mod-client.js";
import { createMcpApp } from "./mcp/transport.js";
import { findFreePort, findMod } from "./discovery/scanner.js";
import { PORT_START, PORT_END } from "./consts.js";
import { Hono } from "hono";
import { serve } from "@hono/node-server";

export interface ServerOptions {
  mcpPort?: number;
  autoDiscover?: boolean;
  discoverTimeout?: number;
}

export async function startServer(opts: ServerOptions = {}): Promise<void> {
  const mod = new ModClient();
  const mcpPort = opts.mcpPort || (await findFreePort(PORT_START, PORT_END));

  const mcpApp = createMcpApp(mod);
  const apiApp = createApiApp(mod);

  const root = new Hono();
  root.route("/", mcpApp.app);
  root.route("/", apiApp);

  serve({ fetch: root.fetch, port: mcpPort, hostname: "127.0.0.1" }, (info) => {
    log(`minecraft-mod-mcp v0.1.0`);
    log(`MCP endpoint:  http://127.0.0.1:${info.port}/mcp`);
    log(`HTTP API:      http://127.0.0.1:${info.port}/api/status`);
    log(`Runtime:       ${getRuntimeName()}`);
    log(``);
  });

  if (opts.autoDiscover !== false) {
    log(`Scanning for Minecraft mod (${PORT_START}-${PORT_END})...`);
    const status = await findMod(PORT_START, PORT_END);
    if (status) {
      await mod.discover();
      log(`Found mod: ${status.version}-${status.loader} (pid ${status.pid}) on port ${status.port}`);
    } else {
      log("No mod found. Waiting for Minecraft to start...");
      if (opts.discoverTimeout !== 0) {
        const timeout = opts.discoverTimeout || 300_000;
        const result = await mod.waitForConnection(timeout);
        if (result) {
          log(`Mod connected: ${result.version}-${result.loader} on port ${result.port}`);
        } else {
          log("Discover timeout. Server is still running — mod can connect later.");
        }
      }
    }
  }
}

function log(msg: string) {
  console.error(`[minecraft-mod-mcp] ${msg}`);
}

function getRuntimeName(): string {
  // @ts-ignore
  if (typeof Deno !== "undefined") return "deno";
  // @ts-ignore
  if (typeof Bun !== "undefined") return "bun";
  return "node";
}

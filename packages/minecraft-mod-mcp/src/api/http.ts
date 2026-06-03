import { createServer, type Server, type IncomingMessage, type ServerResponse } from "node:http";
import type { ModClient } from "./modClient.js";
import { PLAYER, GAME, MOD, MCP } from "../mc/defaults.js";

export function createApiApp(mod: ModClient): Server {
  const server = createServer(async (req: IncomingMessage, res: ServerResponse) => {
    const url = new URL(req.url ?? "/", `http://${MCP.bindAddress}`);
    const path = url.pathname;

    res.setHeader("Content-Type", "application/json");

    try {
      if (req.method === "GET" && path === "/api/status") {
        const rt = typeof (globalThis as any).Deno !== "undefined" ? "deno" : typeof (globalThis as any).Bun !== "undefined" ? "bun" : "node";
        res.end(JSON.stringify({
          ok: true,
          type: MOD.httpServerName,
          runtime: rt,
          mod: mod.getStatus(),
        }));
        return;
      }

      if (req.method === "POST" && path === "/api/discover") {
        const status = await mod.discover();
        res.end(JSON.stringify(status || { error: "mod not found" }));
        return;
      }

      if (req.method === "POST" && path === "/api/kill") {
        mod.killMc();
        res.end(JSON.stringify({ killed: true }));
        return;
      }

      if (req.method === "POST" && (path === "/api/launch" || path === "/api/cmd")) {
        const body = await readBody(req);

        if (path === "/api/launch") {
          const version = (body && typeof body === "object" && "version" in body) ? String((body as any).version) : GAME.defaultVersion;
          const loader = (body && typeof body === "object" && "loader" in body) ? String((body as any).loader) : GAME.defaultLoader;
          const { buildLaunchCommand } = await import("../mc/launch.js");
          const { loadVersionMerged } = await import("../mc/versionJson.js");
          const { loadConfig, selectedAccount, gameDirPath, javaExecPath, accountUuid, accountUsername, accountAccessToken, accountUserType } = await import("../mc/settings.js");
          const { findFreePort } = await import("../discovery/scanner.js");
          const { loadVersionsData } = await import("../mc/versionsData.js");
          const { ensureVersionInstalled } = await import("../mc/download.js");
          const { spawn } = await import("node:child_process");
          const { existsSync, mkdirSync } = await import("node:fs");

          const versionId = await ensureVersionInstalled(version, loader as any);
          const vj = loadVersionMerged(versionId);
          const config = loadConfig();
          const account = selectedAccount(config);
          const mcpPort = config.mcp_port ?? await findFreePort();

          const cmd = buildLaunchCommand({
            versionId,
            loader: loader as any,
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
          const child = spawn(cmd.java, cmd.args, { cwd: mcDir_, stdio: "ignore" });
          mod.setMcProcess(child);
          res.end(JSON.stringify({ launched: true, version: versionId, loader, pid: child.pid }));
          return;
        }

        if (!body || typeof body !== "object") {
          res.statusCode = 400;
          res.end(JSON.stringify({ error: "invalid body" }));
          return;
        }

        const method = String((body as any).cmd || (body as any).method || "");
        if (!method) {
          res.statusCode = 400;
          res.end(JSON.stringify({ error: "missing cmd/method" }));
          return;
        }

        const params = ((body as any).params && typeof (body as any).params === "object")
          ? (body as any).params
          : body;
        const result = await mod.sendCommand(method, params);
        res.end(JSON.stringify(result));
        return;
      }

      res.statusCode = 404;
      res.end(JSON.stringify({ error: "not found" }));
    } catch (err: any) {
      res.statusCode = 502;
      res.end(JSON.stringify({ error: err.message }));
    }
  });

  return server;
}

function readBody(req: IncomingMessage): Promise<any> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    req.on("data", (chunk) => chunks.push(chunk));
    req.on("end", () => {
      if (chunks.length === 0) return resolve(null);
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString("utf-8")));
      } catch {
        resolve(null);
      }
    });
    req.on("error", reject);
  });
}

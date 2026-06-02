import { createServer, type Server, type IncomingMessage, type ServerResponse } from "node:http";
import type { ModClient } from "./mod-client.js";

export function createApiApp(mod: ModClient): Server {
  const server = createServer(async (req: IncomingMessage, res: ServerResponse) => {
    const url = new URL(req.url ?? "/", `http://127.0.0.1`);
    const path = url.pathname;

    res.setHeader("Content-Type", "application/json");

    try {
      if (req.method === "GET" && path === "/api/status") {
        const rt = typeof (globalThis as any).Deno !== "undefined" ? "deno" : typeof (globalThis as any).Bun !== "undefined" ? "bun" : "node";
        res.end(JSON.stringify({
          ok: true,
          type: "minecraft-mod-mcp-server",
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
          const version = (body && typeof body === "object" && "version" in body) ? String((body as any).version) : "1.21.7";
          const loader = (body && typeof body === "object" && "loader" in body) ? String((body as any).loader) : "forge";
          const { buildLaunchCommand } = await import("../mc/launch.js");
          const { loadVersion } = await import("../mc/version-json.js");
          const { loadConfig, selectedAccount, gameDirPath, javaExecPath, accountUuid, accountUsername, accountAccessToken, accountUserType } = await import("../mc/settings.js");
          const { findFreePort } = await import("../discovery/scanner.js");
          const { loadVersionsData } = await import("../mc/versions-data.js");
          const { getVersion, getVersionForLoader } = await import("../mc/versions.js");
          const { spawn } = await import("node:child_process");

          const data = loadVersionsData();
          let versionId = version;
          const vi = getVersion(data, version);
          if (vi) versionId = getVersionForLoader(data, version, loader as any) ?? vi.version_id;

          const vj = loadVersion(versionId);
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
            playerName: account ? accountUsername(account) : "Player",
            uuid: account ? accountUuid(account) : "0",
            accessToken: account ? accountAccessToken(account) : "0",
            userType: account ? accountUserType(account) : "legacy",
          }, vj, data);

          const child = spawn(cmd.java, cmd.args, { cwd: gameDirPath(config), stdio: "ignore" });
          mod.setMcProcess(child);
          res.end(JSON.stringify({ launched: true, version, loader, pid: child.pid }));
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

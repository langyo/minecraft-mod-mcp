import { Hono } from "hono";
import type { ModClient } from "../api/mod-client.js";
import { findLaunchScript } from "../mcp/handlers.js";

export function createApiApp(mod: ModClient): Hono {
  const app = new Hono();

  app.get("/api/status", (c) => {
    // @ts-ignore
    const rt = typeof Deno !== "undefined" ? "deno" : typeof Bun !== "undefined" ? "bun" : "node";
    return c.json({
      ok: true,
      type: "minecraft-mod-mcp-server",
      runtime: rt,
      mod: mod.getStatus(),
    });
  });

  app.post("/api/discover", async (c) => {
    const status = await mod.discover();
    return c.json(status || { error: "mod not found" });
  });

  app.post("/api/launch", async (c) => {
    const raw = await c.req.json().catch(() => null);
    const version = (raw && typeof raw === "object" && "version" in raw) ? String((raw as any).version) : "1.21.7";
    const loader = (raw && typeof raw === "object" && "loader" in raw) ? String((raw as any).loader) : "forge";
    const scriptPath = findLaunchScript();
    if (!scriptPath) return c.json({ error: "launch_mc.py not found" }, 500);
    const { spawn } = await import("node:child_process");
    const proc = spawn("python", [scriptPath, `${version}-${loader}`, "--loader", loader], { stdio: "pipe" });
    mod.setMcProcess(proc);
    return c.json({ launched: true, version, loader, pid: proc.pid });
  });

  app.post("/api/kill", (c) => {
    mod.killMc();
    return c.json({ killed: true });
  });

  app.post("/api/cmd", async (c) => {
    const raw = await c.req.json().catch(() => null);
    if (!raw || typeof raw !== "object") return c.json({ error: "invalid body" }, 400);
    const body = raw as Record<string, unknown>;
    const method = String(body.cmd || body.method || "");
    if (!method) return c.json({ error: "missing cmd/method" }, 400);
    try {
      const params = ("params" in body && typeof body.params === "object" && body.params)
        ? body.params as Record<string, unknown>
        : body;
      const result = await mod.sendCommand(method, params);
      return c.json(result);
    } catch (err: any) {
      return c.json({ error: err.message }, 502);
    }
  });

  return app;
}

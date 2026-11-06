import { execFileSync } from "node:child_process";
import { PORT_START, PORT_END, type ModStatus } from "../consts.js";
import { findAllMods, probePort } from "../discovery/scanner.js";
import { MCP, MOD } from "../mc/defaults.js";
import { readMcpToken } from "../mc/settings.js";

export class ModClient {
  private modPort: number | null = null;
  private baseUrl = "";
  private instanceId: string | null = null;
  private mcProcess: ReturnType<typeof import("node:child_process").spawn> | null = null;
  private spawnedAtMs = 0;
  /** How long a fresh launch owns discovery before falling back to any game. */
  private static readonly SPAWN_GRACE_MS = 20_000;

  // Resolved per request: a CLI launch can create the token after this
  // long-running client started (issue #81).
  private authHeaders(): Record<string, string> {
    const token = readMcpToken();
    return token ? { Authorization: `Bearer ${token}` } : {};
  }

  get connected(): boolean {
    return this.modPort !== null;
  }

  getStatus(): { connected: boolean; port: number | null; processAlive: boolean } {
    return {
      connected: this.connected,
      port: this.modPort,
      processAlive: this.mcProcess !== null && this.mcProcess.exitCode === null,
    };
  }

  async discover(startPort = PORT_START, endPort = PORT_END): Promise<ModStatus | null> {
    // Scan the whole range: with several games running, prefer the one this
    // client spawned (pid match), then the one it talked to before
    // (instanceId), else the first that answered (issue #81).
    const all = await findAllMods(startPort, endPort);
    const expectedPid = this.mcProcess?.pid;
    const spawned = this.spawnedAtMs > 0;
    const withinGrace = spawned && Date.now() - this.spawnedAtMs < ModClient.SPAWN_GRACE_MS;
    if (expectedPid) {
      const mine = all.find((s) => s.pid === expectedPid);
      if (mine) {
        this.connect(mine);
        return mine;
      }
      // Right after our own launch the new game is still loading; do not
      // lock onto a different instance during the grace window (issue #81).
      // After it, fall through: launcher stubs (javapath) report a different
      // pid than the JVM, so a pid match is not always possible.
      if (withinGrace) return null;
    }
    const status =
      (this.instanceId ? all.find((s) => s.instanceId === this.instanceId) : undefined) ??
      all[0] ??
      null;
    if (status) this.connect(status);
    return status;
  }

  private connect(status: ModStatus): void {
    this.modPort = status.port;
    this.instanceId = status.instanceId ?? null;
    this.baseUrl = `http://${MCP.bindAddress}:${status.port}`;
  }

  async waitForConnection(timeoutMs: number = MCP.waitTimeoutMs): Promise<ModStatus | null> {
    const deadline = Date.now() + timeoutMs;
    let status: ModStatus | null = null;
    while (Date.now() < deadline) {
      status = await this.discover();
      if (status) return status;
      await new Promise((r) => setTimeout(r, Math.min(MCP.pollIntervalMs, Math.max(0, deadline - Date.now()))));
    }
    return null;
  }

  async checkAlive(): Promise<boolean> {
    if (!this.modPort) return false;
    const s = await probePort(this.modPort);
    return s !== null;
  }

  private disconnect(): void {
    this.modPort = null;
    this.baseUrl = "";
  }

  async sendCommand(method: string, params?: Record<string, unknown>): Promise<unknown> {
    if (!this.baseUrl) {
      await this.discover();
      if (!this.baseUrl) throw new Error("Mod not connected");
    }
    const body: Record<string, unknown> = { cmd: method, ...(params || {}) };
    try {
      const resp = await fetch(`${this.baseUrl}${MOD.cmdEndpoint}`, {
        method: "POST",
        headers: { "Content-Type": "application/json", ...this.authHeaders() },
        body: JSON.stringify(body),
      });
      if (!resp.ok) {
        const text = await resp.text();
        if (resp.status === 502 || resp.status === 503) this.disconnect();
        throw new Error(`Mod returned ${resp.status}: ${text}`);
      }
      return resp.json();
    } catch (err: any) {
      if (err.cause?.code === "ECONNREFUSED") this.disconnect();
      throw err;
    }
  }

  async screenshot(): Promise<unknown> {
    if (!this.baseUrl) {
      await this.discover();
      if (!this.baseUrl) throw new Error("Mod not connected");
    }
    try {
      const resp = await fetch(`${this.baseUrl}${MOD.screenshotEndpoint}`, {
        headers: this.authHeaders(),
      });
      if (!resp.ok) throw new Error(`Screenshot failed: ${resp.status}`);
      return resp.json();
    } catch (err: any) {
      if (err.cause?.code === "ECONNREFUSED") this.disconnect();
      throw err;
    }
  }

  setMcProcess(proc: ReturnType<typeof import("node:child_process").spawn>) {
    this.mcProcess = proc;
    this.spawnedAtMs = Date.now();
    // A newly launched game owns this client: drop any connection to an
    // earlier instance so discovery binds to the new one (issue #81).
    this.disconnect();
    this.instanceId = null;
  }

  getMcProcess() {
    return this.mcProcess;
  }

  killMc() {
    if (this.mcProcess && this.mcProcess.exitCode === null) {
      if (process.platform === "win32") {
        try { execFileSync("taskkill", ["/PID", String(this.mcProcess.pid), "/T", "/F"], { stdio: "ignore" }); } catch {}
      } else {
        this.mcProcess.kill("SIGTERM");
      }
      this.mcProcess = null;
    }
  }
}

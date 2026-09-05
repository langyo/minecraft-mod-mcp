import { execSync } from "node:child_process";
import { PORT_START, PORT_END, type ModStatus } from "../consts.js";
import { findMod, waitForMod, probePort } from "../discovery/scanner.js";
import { MCP, MOD } from "../mc/defaults.js";

export class ModClient {
  private modPort: number | null = null;
  private baseUrl = "";
  private modStatus: ModStatus | null = null;
  private mcProcess: ReturnType<typeof import("node:child_process").spawn> | null = null;

  get connected(): boolean {
    return this.modPort !== null;
  }

  getStatus() {
    const processAlive = this.mcProcess !== null && this.mcProcess.exitCode === null;
    return {
      connected: this.connected,
      port: this.modPort,
      processAlive,
      processManaged: this.mcProcess !== null,
      pid: this.modStatus?.pid ?? null,
      uptime: this.modStatus?.uptime ?? null,
      version: this.modStatus?.version ?? null,
      loader: this.modStatus?.loader ?? null,
      forgeVersion: this.modStatus?.forgeVersion ?? null,
    };
  }

  private connect(status: ModStatus): void {
    this.modPort = status.port;
    this.baseUrl = `http://${MCP.bindAddress}:${status.port}`;
    this.modStatus = status;
  }

  async discover(startPort: number = PORT_START, endPort: number = PORT_END): Promise<ModStatus | null> {
    const previousStatus = this.modStatus;
    const status = await findMod(startPort, endPort);
    if (status) this.connect(status);
    else if (previousStatus === this.modStatus) this.disconnect();
    return status;
  }

  async waitForConnection(timeoutMs: number = MCP.waitTimeoutMs): Promise<ModStatus | null> {
    const previousStatus = this.modStatus;
    const status = await waitForMod(PORT_START, PORT_END, timeoutMs);
    if (status) this.connect(status);
    else if (previousStatus === this.modStatus) this.disconnect();
    return status;
  }

  async checkAlive(startPort: number = PORT_START, endPort: number = PORT_END): Promise<boolean> {
    if (!this.modPort) {
      await this.discover(startPort, endPort);
      return this.connected;
    }
    const port = this.modPort;
    const previousStatus = this.modStatus;
    const status = await probePort(port);
    if (previousStatus === this.modStatus && port === this.modPort) {
      if (status) {
        this.connect(status);
        return true;
      }
      this.disconnect();
      await this.discover(startPort, endPort);
      return this.connected;
    }
    return this.connected;
  }

  private disconnect(): void {
    this.modPort = null;
    this.baseUrl = "";
    this.modStatus = null;
  }

  async sendCommand(method: string, params?: Record<string, unknown>): Promise<unknown> {
    if (!this.baseUrl) {
      await this.discover();
      if (!this.baseUrl) throw new Error("Mod not connected");
    }
    const baseUrl = this.baseUrl;
    const pid = this.modStatus?.pid;
    const body: Record<string, unknown> = { cmd: method, ...(params || {}) };
    let httpError = false;
    try {
      const resp = await fetch(`${baseUrl}${MOD.cmdEndpoint}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      if (!resp.ok) {
        if (resp.status >= 500 && baseUrl === this.baseUrl && pid === this.modStatus?.pid) this.disconnect();
        httpError = true;
        const text = await resp.text();
        throw new Error(`Mod returned ${resp.status}: ${text}`);
      }
      return await resp.json();
    } catch (err: any) {
      if (!httpError && baseUrl === this.baseUrl && pid === this.modStatus?.pid) this.disconnect();
      throw err;
    }
  }

  async screenshot(): Promise<unknown> {
    if (!this.baseUrl) {
      await this.discover();
      if (!this.baseUrl) throw new Error("Mod not connected");
    }
    const baseUrl = this.baseUrl;
    const pid = this.modStatus?.pid;
    let httpError = false;
    try {
      const resp = await fetch(`${baseUrl}${MOD.screenshotEndpoint}`);
      if (!resp.ok) {
        if (resp.status >= 500 && baseUrl === this.baseUrl && pid === this.modStatus?.pid) this.disconnect();
        httpError = true;
        throw new Error(`Screenshot failed: ${resp.status}`);
      }
      return await resp.json();
    } catch (err: any) {
      if (!httpError && baseUrl === this.baseUrl && pid === this.modStatus?.pid) this.disconnect();
      throw err;
    }
  }

  setMcProcess(proc: ReturnType<typeof import("node:child_process").spawn>) {
    this.mcProcess = proc;
  }

  getMcProcess() {
    return this.mcProcess;
  }

  killMc(): boolean {
    if (this.mcProcess && this.mcProcess.exitCode === null) {
      if (process.platform === "win32") {
        try { execSync(`taskkill /PID ${this.mcProcess.pid} /T /F`, { stdio: "ignore" }); }
        catch { return false; }
      } else {
        if (!this.mcProcess.kill("SIGTERM")) return false;
      }
      this.mcProcess = null;
      return true;
    }
    return false;
  }
}

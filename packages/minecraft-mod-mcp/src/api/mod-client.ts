import { PORT_START, PORT_END, type ModStatus } from "../consts.js";
import { findMod, waitForMod, probePort } from "../discovery/scanner.js";

export class ModClient {
  private modPort: number | null = null;
  private baseUrl = "";
  private mcProcess: ReturnType<typeof import("node:child_process").spawn> | null = null;

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
    const status = await findMod(startPort, endPort);
    if (status) {
      this.modPort = status.port;
      this.baseUrl = `http://127.0.0.1:${status.port}`;
    }
    return status;
  }

  async waitForConnection(timeoutMs = 120_000): Promise<ModStatus | null> {
    const status = await waitForMod(PORT_START, PORT_END, timeoutMs);
    if (status) {
      this.modPort = status.port;
      this.baseUrl = `http://127.0.0.1:${status.port}`;
    }
    return status;
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
      const resp = await fetch(`${this.baseUrl}/api/cmd`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
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
      const resp = await fetch(`${this.baseUrl}/api/screenshot`);
      if (!resp.ok) throw new Error(`Screenshot failed: ${resp.status}`);
      return resp.json();
    } catch (err: any) {
      if (err.cause?.code === "ECONNREFUSED") this.disconnect();
      throw err;
    }
  }

  setMcProcess(proc: ReturnType<typeof import("node:child_process").spawn>) {
    this.mcProcess = proc;
  }

  getMcProcess() {
    return this.mcProcess;
  }

  killMc() {
    if (this.mcProcess && this.mcProcess.exitCode === null) {
      this.mcProcess.kill("SIGTERM");
      this.mcProcess = null;
    }
  }
}

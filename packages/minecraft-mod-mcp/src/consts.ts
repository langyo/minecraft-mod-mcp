import { MCP, MOD } from "./mc/defaults.js";

export const PORT_START = MCP.portStart;
export const PORT_END = MCP.portEnd;
export const HEARTBEAT_TIMEOUT_MS = MCP.heartbeatTimeoutMs;

export interface ModStatus {
  ok: boolean;
  type: string;
  version: string;
  loader: string;
  forgeVersion?: string;
  pid: number;
  port: number;
  uptime: number;
}

export function isModStatus(obj: unknown): obj is ModStatus {
  if (typeof obj !== "object" || obj === null) return false;
  const o = obj as Record<string, unknown>;
  return o.ok === true
    && o.type === MOD.statusType
    && typeof o.version === "string"
    && typeof o.loader === "string"
    && typeof o.pid === "number"
    && typeof o.port === "number"
    && typeof o.uptime === "number";
}

export interface ServerPorts {
  mcpPort: number;
  modPort: number | null;
}

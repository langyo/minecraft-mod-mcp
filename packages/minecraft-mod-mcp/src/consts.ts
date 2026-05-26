export const PORT_START = 9876;
export const PORT_END = 9000;
export const HEARTBEAT_TIMEOUT_MS = 2000;

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
  return o.ok === true && o.type === "minecraft-mod" && typeof o.port === "number";
}

export interface ServerPorts {
  mcpPort: number;
  modPort: number | null;
}

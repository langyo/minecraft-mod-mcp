import { PORT_START, PORT_END, HEARTBEAT_TIMEOUT_MS, isModStatus, type ModStatus } from "../consts.js";

export async function probePort(port: number): Promise<ModStatus | null> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), HEARTBEAT_TIMEOUT_MS);
  try {
    const resp = await fetch(`http://127.0.0.1:${port}/api/status`, {
      signal: controller.signal,
    });
    clearTimeout(timer);
    if (!resp.ok) return null;
    const body = await resp.json();
    return isModStatus(body) ? body : null;
  } catch {
    clearTimeout(timer);
    return null;
  }
}

export async function findMod(startPort = PORT_START, endPort = PORT_END): Promise<ModStatus | null> {
  for (let port = startPort; port >= endPort; port--) {
    const status = await probePort(port);
    if (status) return status;
  }
  return null;
}

export async function waitForMod(
  startPort = PORT_START,
  endPort = PORT_END,
  timeoutMs = 120_000,
  pollIntervalMs = 3_000,
): Promise<ModStatus | null> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const status = await findMod(startPort, endPort);
    if (status) return status;
    await sleep(Math.min(pollIntervalMs, deadline - Date.now()));
  }
  return null;
}

export async function findFreePort(startPort = PORT_START, endPort = PORT_END): Promise<number> {
  const { createServer } = await import("node:net");
  for (let port = startPort; port >= endPort; port--) {
    const ok = await new Promise<boolean>((resolve) => {
      const srv = createServer();
      srv.once("error", () => { resolve(false); srv.close(); });
      srv.listen(port, "127.0.0.1", () => { srv.close(); resolve(true); });
    });
    if (ok) return port;
  }
  throw new Error(`No free port in range ${startPort}-${endPort}`);
}

function sleep(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, Math.max(0, ms)));
}

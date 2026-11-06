import { PORT_START, PORT_END, HEARTBEAT_TIMEOUT_MS, isModStatus, type ModStatus } from "../consts.js";
import { MCP, MOD } from "../mc/defaults.js";

export async function probePort(port: number): Promise<ModStatus | null> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), HEARTBEAT_TIMEOUT_MS);
  try {
    const resp = await fetch(`http://${MCP.bindAddress}:${port}${MOD.statusEndpoint}`, {
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

/**
 * Probe every port in the range and return all responding instances, so a
 * bridge that shares the machine with other games can pick the right one
 * instead of locking onto whichever answered first (issue #81). Probes run in
 * small parallel batches: 800+ sequential round-trips are needlessly slow.
 */
export async function findAllMods(startPort = PORT_START, endPort = PORT_END): Promise<ModStatus[]> {
  const found: ModStatus[] = [];
  const BATCH = 32;
  for (let port = startPort; port >= endPort; port -= BATCH) {
    const batch: Promise<ModStatus | null>[] = [];
    for (let p = port; p >= endPort && p > port - BATCH; p--) {
      batch.push(probePort(p));
    }
    const statuses = await Promise.all(batch);
    for (const s of statuses) if (s) found.push(s);
  }
  return found;
}

export async function findMod(startPort = PORT_START, endPort = PORT_END): Promise<ModStatus | null> {
  const all = await findAllMods(startPort, endPort);
  return all.length > 0 ? all[0] : null;
}

export async function waitForMod(
  startPort = PORT_START,
  endPort = PORT_END,
  timeoutMs: number = MCP.waitTimeoutMs,
  pollIntervalMs: number = MCP.pollIntervalMs,
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
      srv.listen(port, MCP.bindAddress, () => { srv.close(); resolve(true); });
    });
    if (ok) return port;
  }
  throw new Error(`No free port in range ${startPort}-${endPort}`);
}

function sleep(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, Math.max(0, ms)));
}

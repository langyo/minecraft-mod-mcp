import { ModClient } from "./api/mod-client.js";
import { createMcpServer, connectStdio } from "./mcp/transport.js";
import { findMod, waitForMod } from "./discovery/scanner.js";
import { PORT_START, PORT_END } from "./consts.js";

export interface ServerOptions {
  autoDiscover?: boolean;
  discoverTimeout?: number;
}

export async function startServer(opts: ServerOptions = {}): Promise<void> {
  const mod = new ModClient();
  const mcpServer = createMcpServer(mod);

  await connectStdio(mcpServer);

  if (opts.autoDiscover !== false) {
    discoverInBackground(mod, opts.discoverTimeout);
  }
}

function discoverInBackground(mod: ModClient, timeout?: number): void {
  (async () => {
    log(`Scanning for Minecraft mod (${PORT_START}-${PORT_END})...`);
    const status = await findMod(PORT_START, PORT_END);
    if (status) {
      await mod.discover();
      log(`Found mod: ${status.version}-${status.loader} (pid ${status.pid}) on port ${status.port}`);
    } else {
      log("No mod found. Background scanning every 5s...");
      const t = timeout || 300_000;
      const result = await mod.waitForConnection(t);
      if (result) {
        log(`Mod connected: ${result.version}-${result.loader} on port ${result.port}`);
      } else {
        log("Discover timeout. Will keep retrying on tool calls.");
      }
    }
  })().catch((err) => {
    log(`Discovery error: ${err.message}`);
  });
}

function log(msg: string) {
  console.error(`[minecraft-mod-mcp] ${msg}`);
}

import { createServer, type Server } from "node:http";
import { afterEach, describe, expect, it } from "vitest";
import { ModClient } from "./modClient.js";

let server: Server | undefined;

afterEach(async () => {
  if (server?.listening) await new Promise<void>((resolve) => server!.close(() => resolve()));
  server = undefined;
});

describe("ModClient", () => {
  it("reports external client metadata and clears a stale connection", async () => {
    server = createServer((_req, res) => {
      res.setHeader("Content-Type", "application/json");
      res.end(JSON.stringify({
        ok: true,
        type: "minecraft-mod",
        version: "26.2",
        loader: "forge",
        pid: 4242,
        port: (server!.address() as { port: number }).port,
        uptime: 12.5,
      }));
    });
    await new Promise<void>((resolve) => server!.listen(0, "127.0.0.1", resolve));
    const port = (server.address() as { port: number }).port;
    const client = new ModClient();

    await expect(client.checkAlive(port, port)).resolves.toBe(true);
    expect(client.getStatus()).toMatchObject({
      connected: true,
      processAlive: false,
      processManaged: false,
      pid: 4242,
      uptime: 12.5,
      version: "26.2",
      loader: "forge",
    });

    await new Promise<void>((resolve) => server!.close(() => resolve()));
    await expect(client.checkAlive(port, port)).resolves.toBe(false);
    expect(client.getStatus()).toMatchObject({ connected: false, port: null, pid: null });
  });
});

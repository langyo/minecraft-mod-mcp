import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { WebStandardStreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/webStandardStreamableHttp.js";
import { Hono } from "hono";
import type { ModClient } from "../api/mod-client.js";
import { registerHandlers } from "./handlers.js";
import { randomUUID } from "node:crypto";

export function createMcpApp(mod: ModClient): { app: Hono; mcpServer: McpServer } {
  const mcpServer = new McpServer({
    name: "minecraft-mod-mcp",
    version: "0.1.0",
  });

  registerHandlers(mcpServer, mod);

  const transport = new WebStandardStreamableHTTPServerTransport({
    sessionIdGenerator: () => randomUUID(),
  });

  mcpServer.connect(transport);

  const app = new Hono();

  app.all("/mcp", async (c) => {
    return transport.handleRequest(c.req.raw);
  });

  app.all("/mcp/{path}+", async (c) => {
    return transport.handleRequest(c.req.raw);
  });

  return { app, mcpServer };
}

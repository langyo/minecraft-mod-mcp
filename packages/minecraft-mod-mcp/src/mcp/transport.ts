import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import type { ModClient } from "../api/mod-client.js";
import { registerHandlers } from "./handlers.js";

export function createMcpServer(mod: ModClient): McpServer {
  const mcpServer = new McpServer({
    name: "minecraft-mod-mcp",
    version: "0.1.0",
  });

  registerHandlers(mcpServer, mod);

  return mcpServer;
}

export async function connectStdio(mcpServer: McpServer): Promise<void> {
  const transport = new StdioServerTransport();
  await mcpServer.connect(transport);
}

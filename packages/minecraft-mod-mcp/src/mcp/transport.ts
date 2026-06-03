import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import type { ModClient } from "../api/modClient.js";
import { registerHandlers } from "./handlers.js";
import { LAUNCHER } from "../mc/defaults.js";

export function createMcpServer(mod: ModClient): McpServer {
  const mcpServer = new McpServer({
    name: LAUNCHER.mcpServerName,
    version: LAUNCHER.version,
  });

  registerHandlers(mcpServer, mod);

  return mcpServer;
}

export async function connectStdio(mcpServer: McpServer): Promise<void> {
  const transport = new StdioServerTransport();
  await mcpServer.connect(transport);
}

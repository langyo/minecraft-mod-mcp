import { startServer } from "./server.js";
import { parseArgs } from "node:util";

async function main() {
  const { values } = parseArgs({
    options: {
      port: { type: "string", short: "p" },
      "no-discover": { type: "boolean", default: false },
      "discover-timeout": { type: "string" },
      help: { type: "boolean", short: "h" },
    },
    strict: true,
  });

  if (values.help) {
    console.log(`minecraft-mod-mcp — MCP server bridge for Minecraft

Usage:
  minecraft-mod-mcp [options]

Options:
  -p, --port <port>          MCP server port (default: auto-scan 9876-9000)
  --no-discover              Don't scan for running Minecraft mod
  --discover-timeout <ms>    Timeout for mod discovery (default: 300000)
  -h, --help                 Show this help

Examples:
  npx minecraft-mod-mcp
  npx minecraft-mod-mcp --port 9878
  npx minecraft-mod-mcp --no-discover
`);
    process.exit(0);
  }

  await startServer({
    mcpPort: values.port ? parseInt(values.port, 10) : undefined,
    autoDiscover: !values["no-discover"],
    discoverTimeout: values["discover-timeout"] ? parseInt(values["discover-timeout"], 10) : undefined,
  });
}

main().catch((err) => {
  console.error("Fatal:", err);
  process.exit(1);
});

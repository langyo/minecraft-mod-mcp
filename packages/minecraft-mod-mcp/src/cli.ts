import { startServer } from "./server.js";
import { parseArgs } from "node:util";

async function main() {
  const { values } = parseArgs({
    options: {
      "no-discover": { type: "boolean", default: false },
      "discover-timeout": { type: "string" },
      help: { type: "boolean", short: "h" },
    },
    strict: true,
  });

  if (values.help) {
    console.error(`minecraft-mod-mcp — MCP server bridge for Minecraft

Usage:
  npx minecraft-mod-mcp [options]

Options:
  --no-discover              Don't scan for running Minecraft mod
  --discover-timeout <ms>    Timeout for mod discovery (default: 300000)
  -h, --help                 Show this help

MCP tools are exposed via stdio. OpenCode connects with:
  { "type": "local", "command": ["npx", "minecraft-mod-mcp"] }
`);
    process.exit(0);
  }

  await startServer({
    autoDiscover: !values["no-discover"],
    discoverTimeout: values["discover-timeout"] ? parseInt(values["discover-timeout"], 10) : undefined,
  });
}

main().catch((err) => {
  console.error("Fatal:", err);
  process.exit(1);
});

# minecraft-mod-mcp

[![npm](https://img.shields.io/npm/v/minecraft-mod-mcp)](https://www.npmjs.com/package/minecraft-mod-mcp)
[![node](https://img.shields.io/node/v/minecraft-mod-mcp)](https://nodejs.org)
![Deno](https://img.shields.io/badge/deno-%E2%9C%93-222?logo=deno)
![Bun](https://img.shields.io/badge/bun-%E2%9C%93-333?logo=bun)
[![License](https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue.svg)](#license)

MCP (Model Context Protocol) server bridge for Minecraft — connect AI tools to Minecraft via standard MCP.

Works with **OpenCode**, **Claude Code**, **Cursor**, and any MCP-compatible AI tool.

## Features

- **35+ MCP tools**: screenshot, click, type, scroll, drag, hotkey, execute commands, query player/world state
- **Multi-version**: Minecraft 1.8.9 through 26.1.2, Forge / Fabric / NeoForge
- **Zero-config port discovery**: mod and server auto-negotiate ports (9876–9000)
- **Cross-runtime**: Node.js 20+, Deno, Bun
- **MCP stdio transport**: the bridge speaks the Model Context Protocol over stdio and proxies every call to the in-game mod's HTTP API

## Quick Start

```bash
# Install globally
npm install -g minecraft-mod-mcp

# Or use directly — starts the MCP stdio server
npx minecraft-mod-mcp

# Start the MCP server, skip background port discovery
npx minecraft-mod-mcp mcp --no-discover
```

For the full CLI reference (launching servers, managing versions, accounts, building SDKs), see **[CLI Usage Guide](../../docs/guides/en/CLI.md)** — available in 8 languages.

## MCP Configuration

This package **is** the MCP server. Add it to your AI tool's config as a **stdio** server launched via `npx` — do not point a URL at it, and do not point an MCP client at the mod's HTTP port (the mod does not speak MCP):

```json
{
  "mcpServers": {
    "minecraft-mod-mcp": {
      "type": "local",
      "command": ["npx", "-y", "minecraft-mod-mcp"]
    }
  }
}
```

(Claude Desktop / older clients use `"command": "npx", "args": ["-y", "minecraft-mod-mcp"]` instead.)

The bridge auto-scans ports 9876–9000 to find the running mod, so you never hard-code a port. See **[AI Tool Integration Guide](../../docs/guides/en/AI-TOOLS.md)** for per-tool config and headless/Linux notes.

## Tools

### Sensing (always available)

| Tool | Description |
|------|-------------|
| `ping` | Ping the mod |
| `screenshot` | Capture screenshot with coordinate grid |
| `get_player_info` | Player position, health, mode, etc. |
| `get_world_info` | World seed, time, weather, etc. |
| `debug_fields` | F3 debug overlay data |
| `get_screen_buttons` | List GUI buttons |
| `enumerate_widgets` | Full widget tree |

### Input (requires control mode)

| Tool | Description |
|------|-------------|
| `click` | Click at (x, y) |
| `right_click` | Use / place |
| `mouse_drag` | Drag from A to B |
| `scroll` | Scroll wheel |
| `press_key` | Press keyboard key |
| `type_text` | Type text |
| `paste_text` | Paste via clipboard |
| `hotkey` | Key combination |
| `set_view_angle` | Set yaw/pitch |
| `look_delta` | Rotate view |
| `execute_command` | Minecraft slash command |

### Control

| Tool | Description |
|------|-------------|
| `enter_control_mode` | Release mouse, enable input |
| `exit_control_mode` | Return to normal |
| `release_mouse` | Release cursor |
| `set_gamemode` | Change game mode |
| `launch_minecraft` | Start a MC client (downloads version + injects mod) |
| `kill_minecraft` | Stop MC |
| `get_minecraft_status` | Connection status |

## How It Works

```
AI Tool ──MCP/stdio──► minecraft-mod-mcp bridge ──HTTP──► Minecraft Mod (Java, in-game)
                        scans ports 9876-9000              serves /api/* on first free port
```

1. The Minecraft mod starts an HTTP server on the first available port (9876→9000)
2. The bridge discovers the mod by scanning the same range and reading `/api/status`
3. Each MCP tool call is translated to an HTTP request — no WebSocket, no hardcoded ports

## Requirements

- Minecraft with the [minecraft-mcp mod](https://github.com/langyo/minecraft-mod-mcp) installed — or just let the `launch_minecraft` / `serve` tools start one for you
- Node.js 20+ (or Deno, or Bun)
- Java (auto-downloaded per version when launching via the bridge)

## License

Licensed under either of:

- Apache License, Version 2.0
- MIT License

at your option.

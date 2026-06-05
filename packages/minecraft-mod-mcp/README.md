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
- **MCP Streamable HTTP transport**: standards-compliant

## Quick Start

```bash
# Install globally
npm install -g minecraft-mod-mcp

# Or use directly
npx minecraft-mod-mcp

# With options
npx minecraft-mod-mcp --port 9878 --no-discover
```

For the full CLI reference (launching servers, managing versions, accounts, building SDKs), see **[CLI Usage Guide](../../docs/guides/en/CLI.md)** — available in 8 languages.

## MCP Configuration

Add to your `.mcp.json` or `opencode.json`:

```json
{
  "mcpServers": {
    "minecraft": {
      "type": "streamable-http",
      "url": "http://localhost:9876/mcp"
    }
  }
}
```

The server auto-scans ports 9876–9000. If 9876 is occupied, it picks the next available.

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
| `launch_minecraft` | Start MC (calls launch_mc.py) |
| `kill_minecraft` | Stop MC |
| `get_minecraft_status` | Connection status |

## How It Works

```
AI Tool ──MCP──► minecraft-mod-mcp (TS) ──HTTP──► Minecraft Mod (Java)
                  port scan 9876-9000              port scan 9876-9000
```

1. The Minecraft mod starts an HTTP server on the first available port (9876→9000)
2. The MCP server discovers the mod by scanning the same range
3. All communication is pure HTTP — no WebSocket, no hardcoded ports

## Requirements

- Minecraft with the [minecraft-mcp mod](https://github.com/langyo/minecraft-mod-mcp) installed
- Node.js 20+ (or Deno, or Bun)
- Python 3.11+ (for `launch_mc.py` game launcher)

## License

Licensed under either of:

- Apache License, Version 2.0
- MIT License

at your option.

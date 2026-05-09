# Minecraft MCP Example Mod

**Package:** `minecraft-mcp.langyo.xyz`

Built-in example NeoForge mod for comprehensive testing of the Minecraft MCP server pipeline.

## Features

| Category | Capabilities |
|----------|-------------|
| **MCP Connection** | WebSocket client connecting to MCP server on startup |
| **Screenshot** | In-game screenshot via AWT Robot (window-aware) |
| **Command Execution** | Send commands to server console (`/give`, `/time`, etc.) |
| **Keyboard Input** | Single key press with configurable hold duration |
| **Text Typing** | Natural text input with human-like timing + Enter support |
| **Mouse Click** | Left/right click at window-relative coordinates |
| **Mouse Scroll** | Scroll wheel simulation |
| **Hotkey Combos** | Multi-key combinations (Ctrl+T, F3+G, etc.) |
| **Player Info** | Position, health, dimension, gamemode query |
| **World Info** | Day time, difficulty, seed, world name |
| **GUI Control** | Open/close title screen, options, inventory |
| **Ping/Echo** | Connectivity and latency testing |

## Architecture

```
[MCP Server (stdio)] <--JSON-RPC--> [AI/Client]
       ^
       | WebSocket
       v
[Example Mod in MC]  <--internal--> [Minecraft Game]
```

## Supported MCP Actions

```
screenshot          - Capture current game view as base64 PNG
click               - Simulate mouse click at (x,y)
press_key           - Press a single keyboard key
type_text           - Type text string (with optional Enter)
scroll              - Mouse wheel scroll
hotkey              - Key combination (e.g. ["ctrl","t"])
execute_command     - Run server command (op required)
get_player_info     - Query player state
get_world_info      - Query world state
set_gui_screen      - Open/close GUI screens
test_echo           - Echo back params for validation
ping                - Latency check
```

## Quick Test

```powershell
# Basic connectivity test (no game launch)
.\example-mod\run_tests.ps1 -WsPort 9879

# Full integration test (launches MC)
.\example-mod\run_tests.ps1 -WsPort 9879 -FullTest
```

## Build

The mod is included as a subproject. The shadowJar bundles all dependencies:

```bash
cd example-mod && gradlew.bat shadowJar
```

Output: `example-mod/build/libs/minecraft-mcp-example-1.0.0.jar`

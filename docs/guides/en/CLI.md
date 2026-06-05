# NPM MCP CLI Usage Guide

**[English](./CLI.md)** &bull; **[简体中文](../zhs/CLI.md)** &bull; **[繁體中文](../zht/CLI.md)** &bull; **[日本語](../ja/CLI.md)** &bull; **[한국어](../ko/CLI.md)** &bull; **[Français](../fr/CLI.md)** &bull; **[Español](../es/CLI.md)** &bull; **[Русский](../ru/CLI.md)**

> The `minecraft-mod-mcp` package provides a full-featured CLI for launching Minecraft clients, servers, managing versions, accounts, and building mod SDKs — all from the command line.

---

## Installation

```bash
npm install -g minecraft-mod-mcp
```

Or run directly without installing:

```bash
npx minecraft-mod-mcp
```

---

## Commands

### MCP Server

Start the MCP stdio server for AI tool integration:

```bash
minecraft-mod-mcp
minecraft-mod-mcp mcp [options]
```

| Option | Description |
|--------|-------------|
| `--no-discover` | Don't scan for running Minecraft mod |
| `--discover-timeout <ms>` | Timeout for mod discovery (default: 300000) |

---

### Launch Client — `launch`

Launch a Minecraft client with the specified version and mod loader.

```bash
minecraft-mod-mcp launch <version> [options]
```

| Option | Default | Description |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | Mod loader |
| `--mc-dir <path>` | Auto | Game directory |
| `--java <path>` | Auto-detect | Java executable path |
| `--memory <mb>` | `2048` | Max JVM memory pool (MB) |
| `--min-memory <mb>` | `512` | Min JVM memory pool (MB) |
| `--jvm-args <args>` | — | Extra JVM arguments (space-separated) |
| `--game-args <args>` | — | Extra game arguments (space-separated) |
| `--fullscreen` | `false` | Launch in fullscreen mode |
| `--width <px>` | `854` | Window width |
| `--height <px>` | `480` | Window height |
| `--server <host>` | — | Auto-connect to server on launch |
| `--server-port <port>` | `25565` | Server port |
| `--port <port>` | Auto | MCP mod port |
| `--mod-jar <path>` | — | Mod JAR to inject |
| `--dry-run` | `false` | Print command without executing |

**Examples:**

```bash
# Launch with 4GB RAM, fullscreen
minecraft-mod-mcp launch 1.21.11 --memory 4096 --fullscreen --loader fabric

# Launch with custom JVM flags, auto-connect to server
minecraft-mod-mcp launch 26.1.2 --jvm-args "-XX:+UseG1GC -Dfml.readTimeout=120" --server myserver.com

# Launch in a windowed 1280x720
minecraft-mod-mcp launch 1.20.6 --width 1280 --height 720 --loader neoforge

# Preview the launch command
minecraft-mod-mcp launch 1.21.11 --dry-run
```

---

### Standalone Server — `server`

Launch a dedicated Minecraft server.

```bash
minecraft-mod-mcp server <version> [options]
```

| Option | Default | Description |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | Mod loader |
| `--java <path>` | Auto-detect | Java executable path |
| `--memory <mb>` | `1024` | Max JVM memory pool (MB) |
| `--min-memory <mb>` | — | Min JVM memory pool (MB) |
| `--jvm-args <args>` | — | Extra JVM arguments (space-separated) |
| `--game-args <args>` | — | Extra server arguments (space-separated) |
| `--mod-jar <path>` | — | Mod JAR to copy into server mods/ |
| `--dry-run` | `false` | Print command without executing |

**Examples:**

```bash
# Launch a server with 4GB RAM
minecraft-mod-mcp server 1.21.11 --memory 4096

# Launch with custom JVM GC tuning
minecraft-mod-mcp server 26.1.2 --jvm-args "-XX:+UseZGC -XX:+ZGenerational" --memory 8192

# Fabric server with mod
minecraft-mod-mcp server 1.21.11 --loader fabric --mod-jar ./path/to/mod.jar
```

---

### Serve (Server + Client) — `serve`

One-command setup: install server + launch server + launch client auto-connected.

```bash
minecraft-mod-mcp serve <version> [options]
```

| Option | Default | Description |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | Mod loader |
| `--java <path>` | Auto-detect | Java executable path |
| `--memory <mb>` | `2048` | Client max memory (MB) |
| `--min-memory <mb>` | — | Client min memory (MB) |
| `--server-memory <mb>` | `1024` | Server max memory (MB) |
| `--server-min-memory <mb>` | — | Server min memory (MB) |
| `--jvm-args <args>` | — | Extra JVM args for both |
| `--game-args <args>` | — | Extra game args for client |
| `--server-game-args <args>` | — | Extra args for server |
| `--fullscreen` | `false` | Launch client in fullscreen |
| `--width <px>` | `854` | Client window width |
| `--height <px>` | `480` | Client window height |
| `--port <port>` | Auto | MCP port |
| `--mod-jar <path>` | — | Mod JAR for both sides |
| `--dry-run` | `false` | Print plan without executing |

**Example:**

```bash
# Full setup: 4GB client, 2GB server, fullscreen client
minecraft-mod-mcp serve 1.21.11 --memory 4096 --server-memory 2048 --fullscreen
```

---

### Version Management

| Command | Description |
|---------|-------------|
| `minecraft-mod-mcp list` | List all supported Minecraft versions |
| `minecraft-mod-mcp installed` | List locally installed versions |
| `minecraft-mod-mcp install <version> [--loader <l>]` | Download and install a version |

---

### Account Management

| Command | Description |
|---------|-------------|
| `minecraft-mod-mcp auth login` | Sign in with Microsoft account |
| `minecraft-mod-mcp auth offline <name>` | Create offline account |
| `minecraft-mod-mcp auth list` | List configured accounts |
| `minecraft-mod-mcp auth select <uuid>` | Set active account |
| `minecraft-mod-mcp auth remove <uuid>` | Remove an account |

---

### Utilities

| Command | Description |
|---------|-------------|
| `minecraft-mod-mcp java` | Detect installed Java versions |
| `minecraft-mod-mcp status` | Show MCP mod connection status |
| `minecraft-mod-mcp sdk <version> [--loader <l>] [--no-build]` | Build mod SDK for a version |

---

## JVM / Game Arguments

The `--jvm-args` and `--game-args` options accept space-separated arguments. In shells that split on spaces, quote the entire value:

```bash
minecraft-mod-mcp launch 1.21.11 --jvm-args "-XX:+UseG1GC -XX:MaxGCPauseMillis=50"
minecraft-mod-mcp server 1.21.11 --game-args "--port 25566 --max-players 10"
```

---

## JSON Config File

Advanced defaults can be set in `~/.minecraft/mcp_launcher/config.json`:

```json
{
  "max_memory_mb": 4096,
  "min_memory_mb": 1024,
  "width": 1920,
  "height": 1080,
  "fullscreen": false,
  "java_args": "-XX:+UseG1GC",
  "game_args": "",
  "game_dir": "~/.minecraft/mcp_launcher/game",
  "mcp_port": 9876,
  "download_source": "bmclapi",
  "language": "en-US"
}
```

CLI flags always override config values.

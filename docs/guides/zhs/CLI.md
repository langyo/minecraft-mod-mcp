# NPM MCP CLI 使用指南

**[English](../en/CLI.md)** &bull; **简体中文** &bull; **[繁體中文](../zht/CLI.md)** &bull; **[日本語](../ja/CLI.md)** &bull; **[한국어](../ko/CLI.md)** &bull; **[Français](../fr/CLI.md)** &bull; **[Español](../es/CLI.md)** &bull; **[Русский](../ru/CLI.md)**

> `minecraft-mod-mcp` 包提供功能完整的 CLI，用于启动 Minecraft 客户端、服务端、管理版本和账户，以及构建模组 SDK —— 全部通过命令行完成。

---

## 安装

```bash
npm install -g minecraft-mod-mcp
```

或无需安装直接运行：

```bash
npx minecraft-mod-mcp
```

---

## 命令

### MCP 服务器

启动 MCP stdio 服务器供 AI 工具集成：

```bash
minecraft-mod-mcp
minecraft-mod-mcp mcp [options]
```

| 选项 | 说明 |
|--------|-------------|
| `--no-discover` | 不扫描运行中的 Minecraft 模组 |
| `--discover-timeout <ms>` | 模组发现超时（默认：300000） |

---

### 启动客户端 — `launch`

启动指定版本和模组加载器的 Minecraft 客户端。

```bash
minecraft-mod-mcp launch <version> [options]
```

| 选项 | 默认值 | 说明 |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | 模组加载器 |
| `--mc-dir <path>` | 自动 | 游戏目录 |
| `--java <path>` | 自动检测 | Java 可执行文件路径 |
| `--memory <mb>` | `2048` | 最大 JVM 内存池（MB） |
| `--min-memory <mb>` | `512` | 最小 JVM 内存池（MB） |
| `--jvm-args <args>` | — | 额外 JVM 参数（空格分隔） |
| `--game-args <args>` | — | 额外游戏参数（空格分隔） |
| `--fullscreen` | `false` | 全屏模式启动 |
| `--width <px>` | `854` | 窗口宽度 |
| `--height <px>` | `480` | 窗口高度 |
| `--server <host>` | — | 启动后自动连接到服务器 |
| `--server-port <port>` | `25565` | 服务器端口 |
| `--port <port>` | 自动 | MCP 模组端口 |
| `--mod-jar <path>` | — | 注入的模组 JAR |
| `--dry-run` | `false` | 仅打印命令，不执行 |

**示例：**

```bash
# 以 4GB 内存、全屏模式启动
minecraft-mod-mcp launch 1.21.11 --memory 4096 --fullscreen --loader fabric

# 使用自定义 JVM 参数并自动连接服务器
minecraft-mod-mcp launch 26.1.2 --jvm-args "-XX:+UseG1GC -Dfml.readTimeout=120" --server myserver.com

# 以 1280x720 窗口化启动
minecraft-mod-mcp launch 1.20.6 --width 1280 --height 720 --loader neoforge

# 预览启动命令
minecraft-mod-mcp launch 1.21.11 --dry-run
```

---

### 独立服务器 — `server`

启动专用的 Minecraft 服务器。

```bash
minecraft-mod-mcp server <version> [options]
```

| 选项 | 默认值 | 说明 |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | 模组加载器 |
| `--java <path>` | 自动检测 | Java 可执行文件路径 |
| `--memory <mb>` | `1024` | 最大 JVM 内存池（MB） |
| `--min-memory <mb>` | — | 最小 JVM 内存池（MB） |
| `--jvm-args <args>` | — | 额外 JVM 参数（空格分隔） |
| `--game-args <args>` | — | 额外服务器参数（空格分隔） |
| `--mod-jar <path>` | — | 复制到服务器 mods/ 的模组 JAR |
| `--dry-run` | `false` | 仅打印命令，不执行 |

**示例：**

```bash
# 以 4GB 内存启动服务器
minecraft-mod-mcp server 1.21.11 --memory 4096

# 使用自定义 GC 参数启动
minecraft-mod-mcp server 26.1.2 --jvm-args "-XX:+UseZGC -XX:+ZGenerational" --memory 8192

# 带模组的 Fabric 服务器
minecraft-mod-mcp server 1.21.11 --loader fabric --mod-jar ./path/to/mod.jar
```

---

### 一体化（服务器 + 客户端） — `serve`

一条命令完成：安装服务器 + 启动服务器 + 启动客户端自动连接。

```bash
minecraft-mod-mcp serve <version> [options]
```

| 选项 | 默认值 | 说明 |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | 模组加载器 |
| `--java <path>` | 自动检测 | Java 可执行文件路径 |
| `--memory <mb>` | `2048` | 客户端最大内存（MB） |
| `--min-memory <mb>` | — | 客户端最小内存（MB） |
| `--server-memory <mb>` | `1024` | 服务器最大内存（MB） |
| `--server-min-memory <mb>` | — | 服务器最小内存（MB） |
| `--jvm-args <args>` | — | 双方额外 JVM 参数 |
| `--game-args <args>` | — | 客户端额外游戏参数 |
| `--server-game-args <args>` | — | 服务器额外参数 |
| `--fullscreen` | `false` | 客户端全屏启动 |
| `--width <px>` | `854` | 客户端窗口宽度 |
| `--height <px>` | `480` | 客户端窗口高度 |
| `--port <port>` | 自动 | MCP 端口 |
| `--mod-jar <path>` | — | 双方注入的模组 JAR |
| `--dry-run` | `false` | 仅打印计划，不执行 |

**示例：**

```bash
# 完整环境：4GB 客户端、2GB 服务器、全屏
minecraft-mod-mcp serve 1.21.11 --memory 4096 --server-memory 2048 --fullscreen
```

---

### 版本管理

| 命令 | 说明 |
|---------|-------------|
| `minecraft-mod-mcp list` | 列出所有支持的 Minecraft 版本 |
| `minecraft-mod-mcp installed` | 列出本地已安装的版本 |
| `minecraft-mod-mcp install <version> [--loader <l>]` | 下载并安装版本 |

---

### 账户管理

| 命令 | 说明 |
|---------|-------------|
| `minecraft-mod-mcp auth login` | 使用 Microsoft 账户登录 |
| `minecraft-mod-mcp auth offline <name>` | 创建离线账户 |
| `minecraft-mod-mcp auth list` | 列出已配置的账户 |
| `minecraft-mod-mcp auth select <uuid>` | 设置活动账户 |
| `minecraft-mod-mcp auth remove <uuid>` | 移除账户 |

---

### 工具

| 命令 | 说明 |
|---------|-------------|
| `minecraft-mod-mcp java` | 检测已安装的 Java 版本 |
| `minecraft-mod-mcp status` | 显示 MCP 模组连接状态 |
| `minecraft-mod-mcp sdk <version> [--loader <l>] [--no-build]` | 为指定版本构建模组 SDK |

---

## JVM / 游戏参数

`--jvm-args` 和 `--game-args` 选项接受空格分隔的参数。在按空格分割的 shell 中，需要将整个值括起来：

```bash
minecraft-mod-mcp launch 1.21.11 --jvm-args "-XX:+UseG1GC -XX:MaxGCPauseMillis=50"
minecraft-mod-mcp server 1.21.11 --game-args "--port 25566 --max-players 10"
```

---

## JSON 配置文件

可在 `~/.minecraft/mcp_launcher/config.json` 中设置高级默认值：

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
  "language": "zh-CN"
}
```

CLI 标志始终优先于配置文件中的值。

# NPM MCP CLI 使用指南

**[English](../en/CLI.md)** &bull; **[简体中文](../zhs/CLI.md)** &bull; **繁體中文** &bull; **[日本語](../ja/CLI.md)** &bull; **[한국어](../ko/CLI.md)** &bull; **[Français](../fr/CLI.md)** &bull; **[Español](../es/CLI.md)** &bull; **[Русский](../ru/CLI.md)**

> `minecraft-mod-mcp` 套件提供功能完整的 CLI，用於啟動 Minecraft 客戶端、伺服器端、管理版本和帳戶，以及構建模組 SDK —— 全部透過命令列完成。

---

## 安裝

```bash
npm install -g minecraft-mod-mcp
```

或無需安裝直接執行：

```bash
npx minecraft-mod-mcp
```

---

## 命令

### MCP 伺服器

啟動 MCP stdio 伺服器供 AI 工具整合：

```bash
minecraft-mod-mcp
minecraft-mod-mcp mcp [options]
```

| 選項 | 說明 |
|--------|-------------|
| `--no-discover` | 不掃描執行中的 Minecraft 模組 |
| `--discover-timeout <ms>` | 模組發現逾時（預設：300000） |

---

### 啟動客戶端 — `launch`

啟動指定版本和模組載入器的 Minecraft 用戶端。

```bash
minecraft-mod-mcp launch <version> [options]
```

| 選項 | 預設值 | 說明 |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | 模組載入器 |
| `--mc-dir <path>` | 自動 | 遊戲目錄 |
| `--java <path>` | 自動偵測 | Java 執行檔路徑 |
| `--memory <mb>` | `2048` | 最大 JVM 記憶體池（MB） |
| `--min-memory <mb>` | `512` | 最小 JVM 記憶體池（MB） |
| `--jvm-args <args>` | — | 額外 JVM 參數（空格分隔） |
| `--game-args <args>` | — | 額外遊戲參數（空格分隔） |
| `--fullscreen` | `false` | 全螢幕模式啟動 |
| `--width <px>` | `854` | 視窗寬度 |
| `--height <px>` | `480` | 視窗高度 |
| `--server <host>` | — | 啟動後自動連接到伺服器 |
| `--server-port <port>` | `25565` | 伺服器埠 |
| `--port <port>` | 自動 | MCP 模組埠 |
| `--mod-jar <path>` | — | 注入的模組 JAR |
| `--dry-run` | `false` | 僅列印命令，不執行 |

**範例：**

```bash
# 以 4GB 記憶體、全螢幕模式啟動
minecraft-mod-mcp launch 1.21.11 --memory 4096 --fullscreen --loader fabric

# 使用自訂 JVM 參數並自動連接伺服器
minecraft-mod-mcp launch 26.1.2 --jvm-args "-XX:+UseG1GC -Dfml.readTimeout=120" --server myserver.com

# 以 1280x720 視窗化啟動
minecraft-mod-mcp launch 1.20.6 --width 1280 --height 720 --loader neoforge

# 預覽啟動命令
minecraft-mod-mcp launch 1.21.11 --dry-run
```

---

### 獨立伺服器 — `server`

啟動專用的 Minecraft 伺服器。

```bash
minecraft-mod-mcp server <version> [options]
```

| 選項 | 預設值 | 說明 |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | 模組載入器 |
| `--java <path>` | 自動偵測 | Java 執行檔路徑 |
| `--memory <mb>` | `1024` | 最大 JVM 記憶體池（MB） |
| `--min-memory <mb>` | — | 最小 JVM 記憶體池（MB） |
| `--jvm-args <args>` | — | 額外 JVM 參數（空格分隔） |
| `--game-args <args>` | — | 額外伺服器參數（空格分隔） |
| `--mod-jar <path>` | — | 複製到伺服器 mods/ 的模組 JAR |
| `--dry-run` | `false` | 僅列印命令，不執行 |

**範例：**

```bash
# 以 4GB 記憶體啟動伺服器
minecraft-mod-mcp server 1.21.11 --memory 4096

# 使用自訂 GC 參數啟動
minecraft-mod-mcp server 26.1.2 --jvm-args "-XX:+UseZGC -XX:+ZGenerational" --memory 8192

# 帶模組的 Fabric 伺服器
minecraft-mod-mcp server 1.21.11 --loader fabric --mod-jar ./path/to/mod.jar
```

---

### 一體化（伺服器 + 客戶端） — `serve`

一條命令完成：安裝伺服器 + 啟動伺服器 + 啟動客戶端自動連接。

```bash
minecraft-mod-mcp serve <version> [options]
```

| 選項 | 預設值 | 說明 |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | 模組載入器 |
| `--java <path>` | 自動偵測 | Java 執行檔路徑 |
| `--memory <mb>` | `2048` | 客戶端最大記憶體（MB） |
| `--min-memory <mb>` | — | 客戶端最小記憶體（MB） |
| `--server-memory <mb>` | `1024` | 伺服器最大記憶體（MB） |
| `--server-min-memory <mb>` | — | 伺服器最小記憶體（MB） |
| `--jvm-args <args>` | — | 雙方額外 JVM 參數 |
| `--game-args <args>` | — | 客戶端額外遊戲參數 |
| `--server-game-args <args>` | — | 伺服器額外參數 |
| `--fullscreen` | `false` | 客戶端全螢幕啟動 |
| `--width <px>` | `854` | 客戶端視窗寬度 |
| `--height <px>` | `480` | 客戶端視窗高度 |
| `--port <port>` | 自動 | MCP 埠 |
| `--mod-jar <path>` | — | 雙方注入的模組 JAR |
| `--dry-run` | `false` | 僅列印計畫，不執行 |

**範例：**

```bash
# 完整環境：4GB 客戶端、2GB 伺服器、全螢幕
minecraft-mod-mcp serve 1.21.11 --memory 4096 --server-memory 2048 --fullscreen
```

---

### 版本管理

| 命令 | 說明 |
|---------|-------------|
| `minecraft-mod-mcp list` | 列出所有支援的 Minecraft 版本 |
| `minecraft-mod-mcp installed` | 列出本機已安裝的版本 |
| `minecraft-mod-mcp install <version> [--loader <l>]` | 下載並安裝版本 |

---

### 帳戶管理

| 命令 | 說明 |
|---------|-------------|
| `minecraft-mod-mcp auth login` | 使用 Microsoft 帳戶登入 |
| `minecraft-mod-mcp auth offline <name>` | 建立離線帳戶 |
| `minecraft-mod-mcp auth list` | 列出已設定的帳戶 |
| `minecraft-mod-mcp auth select <uuid>` | 設定活動帳戶 |
| `minecraft-mod-mcp auth remove <uuid>` | 移除帳戶 |

---

### 工具

| 命令 | 說明 |
|---------|-------------|
| `minecraft-mod-mcp java` | 偵測已安裝的 Java 版本 |
| `minecraft-mod-mcp status` | 顯示 MCP 模組連線狀態 |
| `minecraft-mod-mcp sdk <version> [--loader <l>] [--no-build]` | 為指定版本構建模組 SDK |

---

## JVM / 遊戲參數

`--jvm-args` 和 `--game-args` 選項接受空格分隔的參數。在按空格分割的 shell 中，需要將整個值括起來：

```bash
minecraft-mod-mcp launch 1.21.11 --jvm-args "-XX:+UseG1GC -XX:MaxGCPauseMillis=50"
minecraft-mod-mcp server 1.21.11 --game-args "--port 25566 --max-players 10"
```

---

## JSON 設定檔

可在 `~/.minecraft/mcp_launcher/config.json` 中設定進階預設值：

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
  "language": "zh-TW"
}
```

CLI 標誌始終優先於設定檔中的值。

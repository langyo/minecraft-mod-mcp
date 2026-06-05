# NPM MCP CLI 使用ガイド

**[English](../en/CLI.md)** &bull; **[简体中文](../zhs/CLI.md)** &bull; **[繁體中文](../zht/CLI.md)** &bull; **日本語** &bull; **[한국어](../ko/CLI.md)** &bull; **[Français](../fr/CLI.md)** &bull; **[Español](../es/CLI.md)** &bull; **[Русский](../ru/CLI.md)**

> `minecraft-mod-mcp` パッケージは、Minecraft クライアントやサーバーの起動、バージョン管理、アカウント管理、Mod SDK のビルドをすべてコマンドラインから行えるフル機能の CLI を提供します。

---

## インストール

```bash
npm install -g minecraft-mod-mcp
```

またはインストールせずに直接実行：

```bash
npx minecraft-mod-mcp
```

---

## コマンド

### MCP サーバー

AI ツール統合用の MCP stdio サーバーを起動：

```bash
minecraft-mod-mcp
minecraft-mod-mcp mcp [options]
```

| オプション | 説明 |
|--------|-------------|
| `--no-discover` | 実行中の Minecraft Mod をスキャンしない |
| `--discover-timeout <ms>` | Mod 検出タイムアウト（デフォルト：300000） |

---

### クライアント起動 — `launch`

指定したバージョンと Mod ローダーで Minecraft クライアントを起動。

```bash
minecraft-mod-mcp launch <version> [options]
```

| オプション | デフォルト | 説明 |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | Mod ローダー |
| `--mc-dir <path>` | 自動 | ゲームディレクトリ |
| `--java <path>` | 自動検出 | Java 実行ファイルのパス |
| `--memory <mb>` | `2048` | 最大 JVM メモリプール（MB） |
| `--min-memory <mb>` | `512` | 最小 JVM メモリプール（MB） |
| `--jvm-args <args>` | — | 追加 JVM 引数（スペース区切り） |
| `--game-args <args>` | — | 追加ゲーム引数（スペース区切り） |
| `--fullscreen` | `false` | フルスクリーンモードで起動 |
| `--width <px>` | `854` | ウィンドウ幅 |
| `--height <px>` | `480` | ウィンドウ高さ |
| `--server <host>` | — | 起動時にサーバーへ自動接続 |
| `--server-port <port>` | `25565` | サーバーポート |
| `--port <port>` | 自動 | MCP Mod ポート |
| `--mod-jar <path>` | — | 注入する Mod JAR |
| `--dry-run` | `false` | コマンド内容のみ表示（実行しない） |

**例：**

```bash
# 4GB メモリ、フルスクリーンで起動
minecraft-mod-mcp launch 1.21.11 --memory 4096 --fullscreen --loader fabric

# カスタム JVM 引数で起動しサーバーに自動接続
minecraft-mod-mcp launch 26.1.2 --jvm-args "-XX:+UseG1GC -Dfml.readTimeout=120" --server myserver.com

# 1280x720 のウィンドウモードで起動
minecraft-mod-mcp launch 1.20.6 --width 1280 --height 720 --loader neoforge

# 起動コマンドをプレビュー
minecraft-mod-mcp launch 1.21.11 --dry-run
```

---

### スタンドアロンサーバー — `server`

専用 Minecraft サーバーを起動。

```bash
minecraft-mod-mcp server <version> [options]
```

| オプション | デフォルト | 説明 |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | Mod ローダー |
| `--java <path>` | 自動検出 | Java 実行ファイルのパス |
| `--memory <mb>` | `1024` | 最大 JVM メモリプール（MB） |
| `--min-memory <mb>` | — | 最小 JVM メモリプール（MB） |
| `--jvm-args <args>` | — | 追加 JVM 引数（スペース区切り） |
| `--game-args <args>` | — | 追加サーバー引数（スペース区切り） |
| `--mod-jar <path>` | — | サーバー mods/ にコピーする Mod JAR |
| `--dry-run` | `false` | コマンド内容のみ表示（実行しない） |

**例：**

```bash
# 4GB メモリでサーバー起動
minecraft-mod-mcp server 1.21.11 --memory 4096

# カスタム GC チューニングで起動
minecraft-mod-mcp server 26.1.2 --jvm-args "-XX:+UseZGC -XX:+ZGenerational" --memory 8192

# Mod 付き Fabric サーバー
minecraft-mod-mcp server 1.21.11 --loader fabric --mod-jar ./path/to/mod.jar
```

---

### サーバー＋クライアント — `serve`

ワンコマンドで：サーバーのインストール + サーバー起動 + クライアント起動（自動接続）。

```bash
minecraft-mod-mcp serve <version> [options]
```

| オプション | デフォルト | 説明 |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | Mod ローダー |
| `--java <path>` | 自動検出 | Java 実行ファイルのパス |
| `--memory <mb>` | `2048` | クライアント最大メモリ（MB） |
| `--min-memory <mb>` | — | クライアント最小メモリ（MB） |
| `--server-memory <mb>` | `1024` | サーバー最大メモリ（MB） |
| `--server-min-memory <mb>` | — | サーバー最小メモリ（MB） |
| `--jvm-args <args>` | — | 両方に適用する追加 JVM 引数 |
| `--game-args <args>` | — | クライアント追加ゲーム引数 |
| `--server-game-args <args>` | — | サーバー追加引数 |
| `--fullscreen` | `false` | クライアントをフルスクリーンで起動 |
| `--width <px>` | `854` | クライアントウィンドウ幅 |
| `--height <px>` | `480` | クライアントウィンドウ高さ |
| `--port <port>` | 自動 | MCP ポート |
| `--mod-jar <path>` | — | 両方に注入する Mod JAR |
| `--dry-run` | `false` | 計画のみ表示（実行しない） |

**例：**

```bash
# 完全環境：4GB クライアント、2GB サーバー、フルスクリーン
minecraft-mod-mcp serve 1.21.11 --memory 4096 --server-memory 2048 --fullscreen
```

---

### バージョン管理

| コマンド | 説明 |
|---------|-------------|
| `minecraft-mod-mcp list` | サポートされている Minecraft バージョンの一覧 |
| `minecraft-mod-mcp installed` | ローカルにインストール済みのバージョン一覧 |
| `minecraft-mod-mcp install <version> [--loader <l>]` | バージョンをダウンロードしてインストール |

---

### アカウント管理

| コマンド | 説明 |
|---------|-------------|
| `minecraft-mod-mcp auth login` | Microsoft アカウントでサインイン |
| `minecraft-mod-mcp auth offline <name>` | オフラインアカウントを作成 |
| `minecraft-mod-mcp auth list` | 設定済みアカウントの一覧 |
| `minecraft-mod-mcp auth select <uuid>` | アクティブアカウントを設定 |
| `minecraft-mod-mcp auth remove <uuid>` | アカウントを削除 |

---

### ユーティリティ

| コマンド | 説明 |
|---------|-------------|
| `minecraft-mod-mcp java` | インストール済み Java バージョンを検出 |
| `minecraft-mod-mcp status` | MCP Mod 接続状態を表示 |
| `minecraft-mod-mcp sdk <version> [--loader <l>] [--no-build]` | 指定バージョンの Mod SDK をビルド |

---

## JVM / ゲーム引数

`--jvm-args` と `--game-args` オプションはスペース区切りの引数を受け付けます。スペースで分割するシェルでは、値全体を引用符で囲んでください：

```bash
minecraft-mod-mcp launch 1.21.11 --jvm-args "-XX:+UseG1GC -XX:MaxGCPauseMillis=50"
minecraft-mod-mcp server 1.21.11 --game-args "--port 25566 --max-players 10"
```

---

## JSON 設定ファイル

`~/.minecraft/mcp_launcher/config.json` で高度なデフォルト値を設定できます：

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
  "language": "ja-JP"
}
```

CLI フラグは常に設定ファイルの値より優先されます。

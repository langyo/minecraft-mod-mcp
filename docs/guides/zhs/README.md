<!-- markdownlint-disable MD033 MD041 MD036 -->
<div align="center">

<img src="../../logo.webp" alt="Minecraft Mod MCP logo" width="200"/>

# Minecraft Mod MCP

**AI 驱动的模组开发工具链**

[![License](https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue.svg)](../../LICENSE-MIT)
[![Java](https://img.shields.io/badge/java-8--25-red.svg)](https://www.java.com/)
[![Release](https://img.shields.io/github/v/release/langyo/minecraft-mod-mcp)](https://github.com/langyo/minecraft-mod-mcp/releases)
[![npm](https://img.shields.io/npm/v/minecraft-mod-mcp)](https://www.npmjs.com/package/minecraft-mod-mcp)

**[English](../../README.md)** &bull; **简体中文** &bull; **[繁體中文](../zht/README.md)** &bull; **[日本語](../ja/README.md)** &bull; **[한국어](../ko/README.md)** &bull; **[Français](../fr/README.md)** &bull; **[Español](../es/README.md)** &bull; **[Русский](../ru/README.md)**

</div>
<!-- markdownlint-enable MD033 MD041 MD036 -->

## 🤖 让你的 AI 接入 Minecraft

**复制这个链接粘贴给你的 AI Agent——它会自动完成配置：**

```
https://github.com/langyo/minecraft-mod-mcp/blob/master/docs/guides/zhs/AI-TOOLS.md
```

你的 AI 会自行阅读指南、配置 MCP 连接，然后开始操控游戏。无需手动配置。

> 已经安装了模组？只需这一个链接就够了。

---

## 什么是 Minecraft Mod MCP

Minecraft Mod MCP 是一个**面向模组开发**的 AI 辅助工具。放入你的 `mods` 文件夹，启动游戏，你的 AI 就能看到游戏画面、点击 GUI 按钮、输入指令、与世界交互——全部通过标准的 MCP 协议。专为测试模组、验证行为、自动化重复流程而设计。

- **看** —— 截取带有坐标网格的屏幕截图
- **动** —— 点击、输入、滚动、拖拽、按下任意按键
- **知** —— 查询玩家位置、世界信息、屏幕按钮和调试字段
- **录** —— 通过 SSE 实时推送事件流，捕获视频帧

> 想让你的 AI 测试你的模组 GUI？跑一遍冒烟测试？验证新方块的交互逻辑？Minecraft Mod MCP 让这一切成为可能。

---

## 支持的版本

| MC 版本 | Forge | Fabric | NeoForge |
|---------|:-----:|:------:|:--------:|
| 26.1.2 | [⬇](https://github.com/langyo/minecraft-mod-mcp/releases/latest/download/minecraft-mcp-26.1.2-forge.jar) | — | [⬇](https://github.com/langyo/minecraft-mod-mcp/releases/latest/download/minecraft-mcp-26.1.2-neoforge.jar) |
| 1.21.11 | [⬇](https://github.com/langyo/minecraft-mod-mcp/releases/latest/download/minecraft-mcp-1.21.11-forge.jar) | [⬇](https://github.com/langyo/minecraft-mod-mcp/releases/latest/download/minecraft-mcp-1.21.11-fabric.jar) | [⬇](https://github.com/langyo/minecraft-mod-mcp/releases/latest/download/minecraft-mcp-1.21.11-neoforge.jar) |

> 早期版本（1.8.9 – 1.20.6）请前往 [Releases 页面](https://github.com/langyo/minecraft-mod-mcp/releases) 下载。

---

## 快速开始

### 1. 安装模组

从 [GitHub Releases](https://github.com/langyo/minecraft-mod-mcp/releases) 下载 JAR 文件，放入你的 Minecraft `mods` 文件夹。

- 需要 **Forge** / **Fabric** / **NeoForge**（见上方支持版本）
- 兼容 Minecraft **1.8.9** 至 **26.1.2**

### 2. 安装 MCP 桥接

```bash
npm install -g minecraft-mod-mcp
```

或无需安装直接运行：

```bash
npx minecraft-mod-mcp
```

### 3. 启动 Minecraft

通过你的模组加载器启动游戏。模组会自动在 9876 端口启动 HTTP 服务器。

### 4. 连接你的 AI

**[→ AI 工具集成指南](./AI-TOOLS.md)** —— 包含 Claude Code、Cursor、Cline、Copilot 等 20+ 工具的详细配置步骤。

或者直接把这段链接粘贴给你的 AI Agent，让它自动配置：

```
https://github.com/langyo/minecraft-mod-mcp/blob/master/docs/guides/zhs/AI-TOOLS.md
```

### 5. 使用 CLI

**[→ CLI 使用指南](./CLI.md)** —— 启动客户端、服务端，管理版本和账户，构建模组 SDK，一切尽在命令行。

---

## 使用技巧

### 与模组协同工作

通常情况下，从 Minecraft 切屏会弹出暂停菜单，从而打断 MCP 指令。使用以下任一方法可以解决这一问题：

- **暂停菜单**：按 `Esc` 打开暂停菜单，点击 MCP 悬浮按钮中的**释放鼠标**按钮。这样你就可以自由切换窗口而不会重新触发暂停菜单。
- **游戏内悬浮按钮**：在 3D 视角中，点击**右上角**的 MCP 悬浮按钮即可临时脱离鼠标。释放后，你可以 `Alt+Tab` 切出游戏，游戏不会自动暂停——非常适合在 IDE 或 AI 工具中继续工作，同时 MCP 连接保持活跃。

### 端口与 HTTP 服务

模组启动时会在游戏内开启一个 HTTP 服务器。默认尝试 **9876** 端口，若被占用则依次回退到 **9875 → 9874 → ... → 9000** 直到找到空闲端口。可通过 JVM 参数 `-Dmcp.port=XXXX` 或环境变量 `MC_MCP_PORT` 指定固定端口。

确认模组选择的端口：
- 控制台会输出 `[MCP-MOD] Debug page: http://127.0.0.1:{port}/debug`
- 游戏内聊天栏会出现可点击的调试页面链接
- `GET /api/status` 返回 `version`、`loader`、`port`、`pid`、`uptime` 等字段——Node.js 桥接器据此自动发现模组
- 在浏览器打开 `http://localhost:{port}/debug` 可看到实时仪表盘，包含 MCP 日志、SSE 事件和连接状态

MC 版本和模组加载器信息在握手阶段通过 `/api/status` 确认，桥接器和调试页面均可获知当前连接的 MC 环境。

---

## 工作原理

<details>
<summary>📸 截图 — 点击展开</summary>

<img src="../screenshot.webp" alt="Minecraft Mod MCP 游戏内截图" width="100%"/>

</details>

```mermaid
flowchart LR
    A["🧠 AI Tool<br/>(Claude Code, Cursor, etc.)<br/>.mcp.json → port 9876"]
    B["🔌 Minecraft Mod MCP<br/>(in-game mod)<br/>HTTP + SSE server"]
    C["🎮 Minecraft Client<br/>(1.8.9 – 26.1.2)"]

    A <-- "HTTP / SSE" --> B
    B -- "reflection" --> C
```

该模组在 Minecraft 内部运行一个 HTTP 服务器（端口 9876）。你的 AI 工具通过标准 MCP 协议（SSE 传输）连接，每条命令 —— 点击、输入、截图等 —— 都通过 Java 反射机制实现，无需针对特定版本编写代码，跨所有 Minecraft 版本通用。

---

## 从源码构建

> 本节面向贡献者。如果你只想使用模组，请查看上方的[快速开始](#快速开始)。

详见 [CONTRIBUTING.md](../../CONTRIBUTING.md)，了解开发环境搭建、项目结构和贡献指南。

---

## 许可证

根据你的选择，采用以下任一许可证：

- Apache License, Version 2.0（[LICENSE-APACHE](../../LICENSE-APACHE) 或 http://www.apache.org/licenses/LICENSE-2.0）
- MIT License（[LICENSE-MIT](../../LICENSE-MIT) 或 http://opensource.org/licenses/MIT）

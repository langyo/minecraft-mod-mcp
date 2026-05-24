<!-- markdownlint-disable MD033 MD041 MD036 -->
<div align="center">

<img src="../../logo.webp" alt="Minecraft MCP logo" width="200"/>

# Minecraft MCP

**多版本、多 Mod 加载器的 Minecraft MCP（模型上下文协议）桥接 Mod**

[![License](https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue.svg)](../../LICENSE-MIT)
[![Rust](https://img.shields.io/badge/rust-1.85%2B-orange.svg)](https://www.rust-lang.org/)
[![Java](https://img.shields.io/badge/java-8--25-red.svg)](https://adoptium.net/)
[![Python](https://img.shields.io/badge/python-3.10%2B-yellow.svg)](https://www.python.org/)
[![Version](https://img.shields.io/badge/version-0.1.0-lightgrey.svg)]()

**[English](../en/README.md)** &bull; **[简体中文](README.md)**

</div>
<!-- markdownlint-enable MD033 MD041 MD036 -->

> **版本 0.1.0** — 活跃开发中。Rust 控制服务器、24 个 Mod 插件以及 YAML 工作流自动化引擎均已可用。CI 已覆盖 Rust 检查和 1.21.7 Forge Mod 构建。

## 什么是 Minecraft MCP

Minecraft MCP（Master Control Program，主控程序）是一个多版本、多 Mod 加载器的 Minecraft UI 自动化框架，由三层组成：

- **Rust 控制服务器**（`packages/server/`）— 基于 WebSocket + TCP 协议，提供截图捕获、鼠标/键盘注入和视频流功能
- **Java Mod 插件**（`packages/mods/`）— 24 个 Mod 项目，覆盖 Forge、Fabric 和 NeoForge 加载器，支持 MC 1.8.9 至 26.1.2，共享公共代码库（`packages/common/`）
- **Python 自动化**（`scripts/`）— YAML 工作流引擎，支持"预选点击"（点击前先在截图上标红点验证坐标）、测试运行器、构建自动化和守护进程管理

## 支持的版本

| MC 版本 | Forge | Fabric | NeoForge |
|---------|:-----:|:------:|:--------:|
| 1.8.9 | ✓ | | |
| 1.9.4 | ✓ | | |
| 1.10.2 | ✓ | | |
| 1.11.2 | ✓ | | |
| 1.12.2 | ✓ | | |
| 1.13.2 | ✓ | | |
| 1.14.4 | ✓ | ✓ | |
| 1.15.2 | ✓ | ✓ | |
| 1.16.5 | ✓ | ✓ | |
| 1.17.1 | ✓ | ✓ | |
| 1.18.2 | ✓ | ✓ | |
| 1.19.4 | ✓ | ✓ | |
| 1.20.6 | ✓ | ✓ | ✓ |
| 1.21.7 | ✓ | | |
| 26.1.2 | ✓ | | ✓ |

## 快速开始

### 环境要求

- Python 3.10+
- Rust 1.85+
- JDK 21（推荐 Corretto）

### 安装与构建

```bash
# 安装 Python 依赖
pip install -r scripts/requirements.txt

# 环境检查
just check-env

# 构建全部（生成代码 + 缓存准备 + 构建所有 Mod）
just full

# 或只构建 Rust 服务器
just build-server
```

### 运行

```bash
# 启动控制服务器守护进程
just daemon

# 启动一个 Minecraft 版本
just launch 1.21.7 forge

# 运行冒烟测试（构建 + 启动 + 截图）
just smoke 1.21.7
```

## 架构

```
┌─────────────────────────────────────┐
│          Rust 控制服务器              │
│  (axum WS/TCP, 截图, 输入注入)       │
└──────────────┬──────────────────────┘
               │ MCP 协议 (WS/TCP)
┌──────────────▼──────────────────────┐
│         Java Mod 插件                │
│  (Forge / Fabric / NeoForge)        │
│  ReflectionHelper, InputHandler     │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│         Minecraft 客户端              │
│  (1.8.9 – 26.1.2, 24 个 Mod 变体)  │
└─────────────────────────────────────┘
```

## 文档

- **[工作流自动化](workflow.md)** — 基于 YAML 的 UI 自动化，支持预选点击
- **[PLAN.md](../../PLAN.md)** — 已完成的测试案例：Redstone Ready 世界创建
- **[Workflows](../../workflows/)** — 声明式 YAML 测试定义

## 贡献

欢迎提交 Issue 和 Pull Request。

## 许可证

本项目采用以下双重许可之一：

- Apache License, Version 2.0（[LICENSE-APACHE](../../LICENSE-APACHE) 或 http://www.apache.org/licenses/LICENSE-2.0）
- MIT License（[LICENSE-MIT](../../LICENSE-MIT) 或 http://opensource.org/licenses/MIT）

任选其一。

<!-- markdownlint-disable MD033 MD041 MD036 -->
<div align="center">

<img src="../../logo.webp" alt="Minecraft MCP logo" width="200"/>

# Minecraft MCP

**多版本、多 Mod 載入器 Minecraft MCP（模型上下文協議）橋接 Mod**

[![License](https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue.svg)](../../LICENSE-MIT)
[![Rust](https://img.shields.io/badge/rust-1.85%2B-orange.svg)](https://www.rust-lang.org/)
[![Java](https://img.shields.io/badge/java-8--25-red.svg)](https://www.java.com/)
[![Python](https://img.shields.io/badge/python-3.10%2B-yellow.svg)](https://www.python.org/)
[![Version](https://img.shields.io/badge/version-0.1.0-lightgrey.svg)]()

**[English](../en/README.md)** &bull; **[简体中文](../zhs/README.md)** &bull; **[繁體中文](README.md)** &bull; **[日本語](../ja/README.md)** &bull; **[한국어](../ko/README.md)** &bull; **[Français](../fr/README.md)** &bull; **[Español](../es/README.md)** &bull; **[Русский](../ru/README.md)**

</div>
<!-- markdownlint-enable MD033 MD041 MD036 -->

> **版本 0.1.0** — 活躍開發中。Rust 控制伺服器、24 個 Mod 插件以及 YAML 工作流自動化引擎均已可用。CI 已涵蓋 Rust 檢查和 1.21.7 Forge Mod 構建。

## 什麼是 Minecraft MCP

Minecraft MCP（Master Control Program，主控程式）是一個多版本、多 Mod 載入器的 Minecraft UI 自動化框架，由三層組成：

- **Rust 控制伺服器**（`packages/server/`）— 基於 WebSocket + TCP 協議，提供截圖擷取、滑鼠/鍵盤注入與影片串流功能
- **Java Mod 插件**（`packages/mods/`）— 24 個 Mod 專案，涵蓋 Forge、Fabric 與 NeoForge 載入器，支援 MC 1.8.9 至 26.1.2，共享公共程式碼庫（`packages/common/`）
- **Python 自動化**（`scripts/`）— YAML 工作流引擎，支援「預覽點擊」（點擊前先在截圖上標紅點驗證座標）、測試執行器、構建自動化與守護程序管理

## 支援的版本

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

## 快速開始

### 環境要求

- Python 3.10+
- Rust 1.85+
- JDK 21（推薦 Corretto）

### 安裝與構建

```bash
# 安裝 Python 依賴
pip install -r scripts/requirements.txt

# 環境檢查
just check-env

# 構建全部（生成程式碼 + 快取準備 + 構建所有 Mod）
just full

# 或只構建 Rust 伺服器
just build-server
```

### 執行

```bash
# 啟動控制伺服器守護程序
just daemon

# 啟動一個 Minecraft 版本
just launch 1.21.7 forge

# 執行冒煙測試（構建 + 啟動 + 截圖）
just smoke 1.21.7
```

## 架構

```
┌─────────────────────────────────────┐
│          Rust 控制伺服器              │
│  (axum WS/TCP, 截圖, 輸入注入)       │
└──────────────┬──────────────────────┘
               │ MCP 協議 (WS/TCP)
┌──────────────▼──────────────────────┐
│         Java Mod 插件                │
│  (Forge / Fabric / NeoForge)        │
│  ReflectionHelper, InputHandler     │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│         Minecraft 用戶端              │
│  (1.8.9 – 26.1.2, 24 個 Mod 變體)  │
└─────────────────────────────────────┘
```

## 文件

- **[工作流自動化](workflow.md)** — 基於 YAML 的 UI 自動化，支援預覽點擊
- **[PLAN.md](../../PLAN.md)** — 已完成的測試案例：Redstone Ready 世界創建
- **[Workflows](../../workflows/)** — 宣告式 YAML 測試定義

## 貢獻

歡迎提交 Issue 與 Pull Request。

## 授權條款

本專案採用以下雙重授權之一：

- Apache License, Version 2.0（[LICENSE-APACHE](../../LICENSE-APACHE) 或 http://www.apache.org/licenses/LICENSE-2.0）
- MIT License（[LICENSE-MIT](../../LICENSE-MIT) 或 http://opensource.org/licenses/MIT）

任選其一。

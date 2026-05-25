<!-- markdownlint-disable MD033 MD041 MD036 -->
<div align="center">

<img src="../../logo.webp" alt="Minecraft MCP logo" width="200"/>

# Minecraft MCP

**多版本、多 Mod 載入器 Minecraft MCP（模型上下文協議）橋接 Mod**

[![License](https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue.svg)](../../LICENSE-MIT)
[![Java](https://img.shields.io/badge/java-8--25-red.svg)](https://www.java.com/)
[![Python](https://img.shields.io/badge/python-3.10%2B-yellow.svg)](https://www.python.org/)
[![Version](https://img.shields.io/badge/version-0.1.0-lightgrey.svg)]()

**[English](../en/README.md)** &bull; **[简体中文](../zhs/README.md)** &bull; **繁體中文** &bull; **[日本語](../ja/README.md)** &bull; **[한국어](../ko/README.md)** &bull; **[Français](../fr/README.md)** &bull; **[Español](../es/README.md)** &bull; **[Русский](../ru/README.md)**

</div>
<!-- markdownlint-enable MD033 MD041 MD036 -->

> **版本 0.1.0** — 活躍開發中。Java Mod 插件以及 HTTP 控制伺服器均已可用。CI 已涵蓋 1.21.7 Forge Mod 構建。Fabric 和 NeoForge 支援為 WIP。

## 什麼是 Minecraft MCP

Minecraft MCP（Master Control Program，主控程式）是一個多版本、多 Mod 載入器的 Minecraft UI 自動化框架，由兩層組成：

- **Java Mod 插件**（`packages/mods/`）— 24 個 Mod 專案，涵蓋 Forge、Fabric 與 NeoForge 載入器，支援 MC 1.8.9 至 26.1.2，共享公共程式碼庫（`packages/common/`）
- **Python 自動化**（`scripts/`）— 構建自動化、守護程序管理、測試執行器與冒煙測試

## 支援的版本

| MC 版本 | Forge | Fabric | NeoForge |
|---------|:-----:|:------:|:--------:|
| 1.8.9 | ✓ | | |
| 1.9.4 | ✓ | | |
| 1.10.2 | ✓ | | |
| 1.11.2 | ✓ | | |
| 1.12.2 | ✓ | | |
| 1.13.2 | ✓ | | |
| 1.14.4 | ✓ | 🚧 | |
| 1.15.2 | ✓ | 🚧 | |
| 1.16.5 | ✓ | 🚧 | |
| 1.17.1 | ✓ | 🚧 | |
| 1.18.2 | ✓ | 🚧 | |
| 1.19.4 | ✓ | 🚧 | |
| 1.20.6 | ✓ | 🚧 | 🚧 |
| 1.21.7 | ✓ | | |
| 26.1.2 | ✓ | | 🚧 |

> 🚧 = WIP（開發中）

## 快速開始

### 環境要求

- Python 3.10+
- JDK 21（推薦 Corretto）

### 安裝與構建

```bash
# 安裝 Python 依賴
pip install -r scripts/requirements.txt

# 環境檢查
just check-env

# 構建全部（生成程式碼 + 快取準備 + 構建所有 Mod）
just full
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

## 貢獻

歡迎提交 Issue 與 Pull Request。

## 授權條款

本專案採用以下雙重授權之一：

- Apache License, Version 2.0（[LICENSE-APACHE](../../LICENSE-APACHE) 或 http://www.apache.org/licenses/LICENSE-2.0）
- MIT License（[LICENSE-MIT](../../LICENSE-MIT) 或 http://opensource.org/licenses/MIT）

任選其一。

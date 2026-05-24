<!-- markdownlint-disable MD033 MD041 MD036 -->
<div align="center">

<img src="../../logo.webp" alt="Minecraft MCP logo" width="200"/>

# Minecraft MCP

**マルチバージョン・マルチ Mod ローダー対応 Minecraft MCP（モデルコンテキストプロトコル）ブリッジ Mod**

[![License](https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue.svg)](../../LICENSE-MIT)
[![Rust](https://img.shields.io/badge/rust-1.85%2B-orange.svg)](https://www.rust-lang.org/)
[![Java](https://img.shields.io/badge/java-8--25-red.svg)](https://www.java.com/)
[![Python](https://img.shields.io/badge/python-3.10%2B-yellow.svg)](https://www.python.org/)
[![Version](https://img.shields.io/badge/version-0.1.0-lightgrey.svg)]()

**[English](../en/README.md)** &bull; **[简体中文](../zhs/README.md)** &bull; **[繁體中文](../zht/README.md)** &bull; **[日本語](README.md)** &bull; **[한국어](../ko/README.md)** &bull; **[Français](../fr/README.md)** &bull; **[Español](../es/README.md)** &bull; **[Русский](../ru/README.md)**

</div>
<!-- markdownlint-enable MD033 MD041 MD036 -->

> **バージョン 0.1.0** — 活発に開発中です。Rust 制御サーバー、24 個の Mod プラグイン、および YAML ワークフロー自動化エンジンが動作可能です。CI ビルドは Rust チェックと 1.21.7 Forge Mod でグリーンです。

## Minecraft MCP とは

Minecraft MCP（Master Control Program）は、マルチバージョン・マルチ Mod ローダー対応の Minecraft UI 自動化フレームワークです。以下の 3 層で構成されています：

- **Rust 制御サーバー**（`packages/server/`）— WebSocket + TCP サーバーで、スクリーンショット取得、マウス/キーボード注入、ビデオストリーミングを提供
- **Java Mod プラグイン**（`packages/mods/`）— Forge、Fabric、NeoForge に対応する 24 の Mod プロジェクトで、MC 1.8.9 から 26.1.2 までをカバーし、共通コードベース（`packages/common/`）を共有
- **Python 自動化**（`scripts/`）— 「プレビュークリック」（クリック前にスクリーンショット上で座標を視覚的に確認）機能を備えた YAML ワークフローエンジン、テストランナー、ビルド自動化、デーモン管理

## サポートバージョン

| MC バージョン | Forge | Fabric | NeoForge |
|--------------|:-----:|:------:|:--------:|
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

## クイックスタート

### 前提条件

- Python 3.10+
- Rust 1.85+
- JDK 21（Corretto 推奨）

### セットアップとビルド

```bash
# Python 依存関係のインストール
pip install -r scripts/requirements.txt

# 環境チェック
just check-env

# すべてをビルド（コード生成 + キャッシュ + 全 Mod ビルド）
just full

# Rust サーバーのみビルド
just build-server
```

### 実行

```bash
# 制御サーバーデーモンを起動
just daemon

# Minecraft バージョンを起動
just launch 1.21.7 forge

# スモークテストを実行（ビルド + 起動 + スクリーンショット）
just smoke 1.21.7
```

## アーキテクチャ

```
┌─────────────────────────────────────┐
│          Rust 制御サーバー             │
│  (axum WS/TCP, スクリーンショット,    │
│           入力注入)                   │
└──────────────┬──────────────────────┘
               │ MCP プロトコル (WS/TCP)
┌──────────────▼──────────────────────┐
│         Java Mod プラグイン           │
│  (Forge / Fabric / NeoForge)        │
│  ReflectionHelper, InputHandler     │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│         Minecraft クライアント         │
│  (1.8.9 – 26.1.2, 24 の Mod 派生)  │
└─────────────────────────────────────┘
```

## ドキュメント

- **[ワークフロー自動化](workflow.md)** — プレビュークリック機能付き YAML ベース UI 自動化
- **[PLAN.md](../../PLAN.md)** — 完了したテストケース：Redstone Ready ワールド作成
- **[Workflows](../../workflows/)** — 宣言型 YAML テスト定義

## コントリビューション

Issue とプルリクエストを歓迎します。

## ライセンス

以下のいずれかのライセンスの下で提供されます：

- Apache License, Version 2.0（[LICENSE-APACHE](../../LICENSE-APACHE) または http://www.apache.org/licenses/LICENSE-2.0）
- MIT License（[LICENSE-MIT](../../LICENSE-MIT) または http://opensource.org/licenses/MIT）

お客様の選択によります。

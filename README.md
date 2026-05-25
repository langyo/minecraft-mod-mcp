<!-- markdownlint-disable MD033 MD041 MD036 -->
<div align="center">

<img src="docs/logo.webp" alt="Minecraft MCP logo" width="200"/>

# Minecraft MCP

**Multi-Version, Multi-Modloader Minecraft MCP (Model Context Protocol) Bridge Mod**

[![License](https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue.svg)](#license)
[![Java](https://img.shields.io/badge/java-8--25-red.svg)](https://www.java.com/)
[![Python](https://img.shields.io/badge/python-3.10%2B-yellow.svg)](https://www.python.org/)
[![Version](https://img.shields.io/badge/version-0.1.0-lightgrey.svg)]()

**English** &bull; **[简体中文](docs/guides/zhs/README.md)** &bull; **[繁體中文](docs/guides/zht/README.md)** &bull; **[日本語](docs/guides/ja/README.md)** &bull; **[한국어](docs/guides/ko/README.md)** &bull; **[Français](docs/guides/fr/README.md)** &bull; **[Español](docs/guides/es/README.md)** &bull; **[Русский](docs/guides/ru/README.md)**

</div>
<!-- markdownlint-enable MD033 MD041 MD036 -->

> **Version 0.1.0** — Active development. Java mod plugins and YAML workflow automation engine are functional. CI builds are green for the 1.21.7 Forge mod. Fabric and NeoForge support is WIP.

## What is Minecraft MCP

Minecraft MCP (Master Control Program) is a multi-version, multi-modloader Minecraft UI automation framework. It consists of two layers:

- **Java Mod Plugins** (`packages/mods/`) — 24 mod projects across Forge, Fabric, and NeoForge, spanning MC 1.8.9 through 26.1.2, all sharing a common codebase (`packages/common/`)
- **Python Automation** (`scripts/`) — YAML workflow engine with "Preview Click" (visual coordinate verification before clicking), test runners, build automation, and daemon management

## Supported Versions

| MC Version | Forge | Fabric | NeoForge |
|------------|:-----:|:------:|:--------:|
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

> 🚧 = WIP (Work In Progress)

## Quick Start

### Prerequisites

- Python 3.10+
- JDK 21 (Corretto recommended)

### Setup & Build

```bash
# Install Python dependencies
pip install -r scripts/requirements.txt

# Check environment
just check-env

# Build everything (generate + cache + build all mods)
just full
```

### Run

```bash
# Start the control server daemon
just daemon

# Launch a Minecraft version
just launch 1.21.7 forge

# Run a smoke test (build + launch + screenshot)
just smoke 1.21.7
```

## Architecture

```
┌─────────────────────────────────────┐
│         Java Mod Plugin              │
│  (Forge / Fabric / NeoForge)        │
│  ReflectionHelper, InputHandler     │
└──────────────┬──────────────────────┘
                │
┌──────────────▼──────────────────────┐
│         Minecraft Client             │
│  (1.8.9 – 26.1.2, 24 mod variants) │
└─────────────────────────────────────┘
```

## Docs

- **[Workflow Automation](docs/guides/en/workflow.md)** — YAML-based UI automation with Preview Click
- **[PLAN.md](PLAN.md)** — Completed test case: Redstone Ready world creation
- **[Workflows](workflows/)** — Declarative YAML test definitions

## Contributing

Issues and pull requests are welcome.

## License

Licensed under either of:

- Apache License, Version 2.0 ([LICENSE-APACHE](LICENSE-APACHE) or http://www.apache.org/licenses/LICENSE-2.0)
- MIT License ([LICENSE-MIT](LICENSE-MIT) or http://opensource.org/licenses/MIT)

at your option.

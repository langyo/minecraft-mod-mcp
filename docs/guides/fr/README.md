<!-- markdownlint-disable MD033 MD041 MD036 -->
<div align="center">

<img src="../../logo.webp" alt="Minecraft MCP logo" width="200"/>

# Minecraft MCP

**Mod Passerelle Minecraft MCP (Model Context Protocol) Multi-Version et Multi-Modloader**

[![License](https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue.svg)](../../LICENSE-MIT)
[![Rust](https://img.shields.io/badge/rust-1.85%2B-orange.svg)](https://www.rust-lang.org/)
[![Java](https://img.shields.io/badge/java-8--25-red.svg)](https://www.java.com/)
[![Python](https://img.shields.io/badge/python-3.10%2B-yellow.svg)](https://www.python.org/)
[![Version](https://img.shields.io/badge/version-0.1.0-lightgrey.svg)]()

**[English](../en/README.md)** &bull; **[简体中文](../zhs/README.md)** &bull; **[繁體中文](../zht/README.md)** &bull; **[日本語](../ja/README.md)** &bull; **[한국어](../ko/README.md)** &bull; **[Français](README.md)** &bull; **[Español](../es/README.md)** &bull; **[Русский](../ru/README.md)**

</div>
<!-- markdownlint-enable MD033 MD041 MD036 -->

> **Version 0.1.0** — Développement actif. Le serveur de contrôle Rust, les 24 plugins mod et le moteur d'automatisation de workflows YAML sont fonctionnels. Les builds CI sont au vert pour les vérifications Rust et le mod Forge 1.21.7.

## Qu'est-ce que Minecraft MCP

Minecraft MCP (Master Control Program) est un framework d'automatisation d'interface Minecraft multi-version et multi-modloader. Il se compose de trois couches :

- **Serveur de contrôle Rust** (`packages/server/`) — Serveur WebSocket + TCP fournissant la capture d'écran, l'injection souris/clavier et le streaming vidéo
- **Plugins Java Mod** (`packages/mods/`) — 24 projets de mod couvrant Forge, Fabric et NeoForge, de MC 1.8.9 à 26.1.2, partageant une base de code commune (`packages/common/`)
- **Automatisation Python** (`scripts/`) — Moteur de workflows YAML avec « Clic d'aperçu » (vérification visuelle des coordonnées avant le clic), exécuteurs de tests, automatisation de build et gestion de démons

## Versions supportées

| Version MC | Forge | Fabric | NeoForge |
|------------|:-----:|:------:|:--------:|
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

## Démarrage rapide

### Prérequis

- Python 3.10+
- Rust 1.85+
- JDK 21 (Corretto recommandé)

### Installation et build

```bash
# Installer les dépendances Python
pip install -r scripts/requirements.txt

# Vérifier l'environnement
just check-env

# Tout compiler (génération + cache + build de tous les mods)
just full

# Ou compiler seulement le serveur Rust
just build-server
```

### Exécution

```bash
# Démarrer le démon du serveur de contrôle
just daemon

# Lancer une version de Minecraft
just launch 1.21.7 forge

# Exécuter un test de fumée (build + lancement + capture d'écran)
just smoke 1.21.7
```

## Architecture

```
┌─────────────────────────────────────┐
│       Serveur de contrôle Rust       │
│  (axum WS/TCP, capture d'écran,     │
│        injection d'entrée)           │
└──────────────┬──────────────────────┘
               │ Protocole MCP (WS/TCP)
┌──────────────▼──────────────────────┐
│         Plugin Java Mod              │
│  (Forge / Fabric / NeoForge)        │
│  ReflectionHelper, InputHandler     │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│         Client Minecraft             │
│  (1.8.9 – 26.1.2, 24 variantes)    │
└─────────────────────────────────────┘
```

## Documentation

- **[Automatisation de workflows](workflow.md)** — Automatisation UI basée sur YAML avec Clic d'aperçu
- **[PLAN.md](../../PLAN.md)** — Cas de test terminé : création du monde Redstone Ready
- **[Workflows](../../workflows/)** — Définitions de tests YAML déclaratifs

## Contribuer

Les issues et les pull requests sont les bienvenues.

## Licence

Sous licence, au choix, selon l'une des deux licences suivantes :

- Apache License, Version 2.0 ([LICENSE-APACHE](../../LICENSE-APACHE) ou http://www.apache.org/licenses/LICENSE-2.0)
- MIT License ([LICENSE-MIT](../../LICENSE-MIT) ou http://opensource.org/licenses/MIT)

à votre convenance.

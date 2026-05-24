<!-- markdownlint-disable MD033 MD041 MD036 -->
<div align="center">

<img src="../../logo.webp" alt="Minecraft MCP logo" width="200"/>

# Minecraft MCP

**Мультиверсионный, мульти-загрузочный мост-мод Minecraft MCP (Model Context Protocol)**

[![License](https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue.svg)](../../LICENSE-MIT)
[![Rust](https://img.shields.io/badge/rust-1.85%2B-orange.svg)](https://www.rust-lang.org/)
[![Java](https://img.shields.io/badge/java-8--25-red.svg)](https://www.java.com/)
[![Python](https://img.shields.io/badge/python-3.10%2B-yellow.svg)](https://www.python.org/)
[![Version](https://img.shields.io/badge/version-0.1.0-lightgrey.svg)]()

**[English](../en/README.md)** &bull; **[简体中文](../zhs/README.md)** &bull; **[繁體中文](../zht/README.md)** &bull; **[日本語](../ja/README.md)** &bull; **[한국어](../ko/README.md)** &bull; **[Français](../fr/README.md)** &bull; **[Español](../es/README.md)** &bull; **[Русский](README.md)**

</div>
<!-- markdownlint-enable MD033 MD041 MD036 -->

> **Версия 0.1.0** — Активная разработка. Управляющий сервер на Rust, 24 мод-плагина и движок автоматизации YAML-сценариев работают. CI-сборки проходят успешно для проверок Rust и мода Forge 1.21.7.

## Что такое Minecraft MCP

Minecraft MCP (Master Control Program) — это мультиверсионный, мульти-загрузочный фреймворк автоматизации UI Minecraft. Он состоит из трёх уровней:

- **Управляющий сервер на Rust** (`packages/server/`) — WebSocket + TCP сервер, обеспечивающий захват скриншотов, ввод мыши/клавиатуры и потоковое видео
- **Java мод-плагины** (`packages/mods/`) — 24 мод-проекта для Forge, Fabric и NeoForge, охватывающих MC с 1.8.9 по 26.1.2, с общей кодовой базой (`packages/common/`)
- **Автоматизация на Python** (`scripts/`) — Движок YAML-сценариев с функцией «Предпросмотр клика» (визуальная проверка координат перед кликом), запуск тестов, автоматизация сборки и управление демонами

## Поддерживаемые версии

| Версия MC | Forge | Fabric | NeoForge |
|-----------|:-----:|:------:|:--------:|
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

## Быстрый старт

### Необходимые условия

- Python 3.10+
- Rust 1.85+
- JDK 21 (рекомендуется Corretto)

### Установка и сборка

```bash
# Установить зависимости Python
pip install -r scripts/requirements.txt

# Проверить окружение
just check-env

# Собрать всё (генерация + кэш + сборка всех модов)
just full

# Или собрать только сервер на Rust
just build-server
```

### Запуск

```bash
# Запустить демон управляющего сервера
just daemon

# Запустить версию Minecraft
just launch 1.21.7 forge

# Запустить дымовой тест (сборка + запуск + скриншот)
just smoke 1.21.7
```

## Архитектура

```
┌─────────────────────────────────────┐
│     Управляющий сервер на Rust       │
│  (axum WS/TCP, снимок экрана,       │
│           ввод)                      │
└──────────────┬──────────────────────┘
               │ Протокол MCP (WS/TCP)
┌──────────────▼──────────────────────┐
│         Java мод-плагин              │
│  (Forge / Fabric / NeoForge)        │
│  ReflectionHelper, InputHandler     │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│         Клиент Minecraft             │
│  (1.8.9 – 26.1.2, 24 варианта)     │
└─────────────────────────────────────┘
```

## Документация

- **[Автоматизация сценариев](workflow.md)** — Автоматизация UI на основе YAML с предпросмотром клика
- **[PLAN.md](../../PLAN.md)** — Выполненный тестовый сценарий: создание мира Redstone Ready
- **[Workflows](../../workflows/)** — Декларативные определения тестов YAML

## Участие в разработке

Приветствуются issues и pull requests.

## Лицензия

Распространяется под одной из следующих лицензий на ваш выбор:

- Apache License, Version 2.0 ([LICENSE-APACHE](../../LICENSE-APACHE) или http://www.apache.org/licenses/LICENSE-2.0)
- MIT License ([LICENSE-MIT](../../LICENSE-MIT) или http://opensource.org/licenses/MIT)

на ваше усмотрение.

<!-- markdownlint-disable MD033 MD041 MD036 -->
<div align="center">

<img src="../../logo.webp" alt="Minecraft Mod MCP logo" width="200"/>

# Minecraft Mod MCP

**Пусть ИИ играет в Minecraft**

[![License](https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue.svg)](../../LICENSE-MIT)
[![Java](https://img.shields.io/badge/java-8--25-red.svg)](https://www.java.com/)
[![Release](https://img.shields.io/github/v/release/langyo/minecraft-mod-mcp)](https://github.com/langyo/minecraft-mod-mcp/releases)
[![npm](https://img.shields.io/npm/v/minecraft-mod-mcp)](https://www.npmjs.com/package/minecraft-mod-mcp)

**[English](../../README.md)** &bull; **[简体中文](../zhs/README.md)** &bull; **[繁體中文](../zht/README.md)** &bull; **[日本語](../ja/README.md)** &bull; **[한국어](../ko/README.md)** &bull; **[Français](../fr/README.md)** &bull; **[Español](../es/README.md)** &bull; **Русский**

</div>
<!-- markdownlint-enable MD033 MD041 MD036 -->

## 🤖 Подключите ИИ к Minecraft

**Скопируйте эту ссылку и вставьте её вашему ИИ-агенту — он настроится автоматически:**

```
https://github.com/langyo/minecraft-mod-mcp/blob/main/docs/guides/ru/AI-TOOLS.md
```

Ваш ИИ прочитает руководство, настроит MCP-подключение и начнёт управлять игрой. Ручная настройка не требуется.

> Уже установили мод? Достаточно одной этой ссылки.

---

## Что такое Minecraft Mod MCP

Minecraft Mod MCP — это мост между ИИ-ассистентами и Minecraft. Он работает как мод внутри игры, предоставляя HTTP-сервер, к которому ИИ-инструменты могут подключаться через стандартный протокол MCP. Через этот мост ИИ может видеть игру, нажимать кнопки, вводить команды и взаимодействовать с миром.

- **Видеть** — делать скриншоты с координатной сеткой
- **Действовать** — кликать, вводить текст, прокручивать, перетаскивать и нажимать любые клавиши
- **Знать** — запрашивать позицию игрока, информацию о мире, кнопки на экране и отладочные поля
- **Записывать** — транслировать события в реальном времени через SSE, захватывать видеокадры

> Хотите, чтобы ИИ построил замок? Провёл smoke-тест? Разобрался в меню сборки модов? Minecraft Mod MCP делает это возможным.

---

## Поддерживаемые версии

| Версия MC | Forge | Fabric | NeoForge |
|-----------|:-----:|:------:|:--------:|
| 26.1.2 | [⬇](https://github.com/langyo/minecraft-mod-mcp/releases/latest/download/minecraft-mcp-26.1.2-forge.jar) | — | [⬇](https://github.com/langyo/minecraft-mod-mcp/releases/latest/download/minecraft-mcp-26.1.2-neoforge.jar) |
| 1.21.11 | [⬇](https://github.com/langyo/minecraft-mod-mcp/releases/latest/download/minecraft-mcp-1.21.11-forge.jar) | [⬇](https://github.com/langyo/minecraft-mod-mcp/releases/latest/download/minecraft-mcp-1.21.11-fabric.jar) | [⬇](https://github.com/langyo/minecraft-mod-mcp/releases/latest/download/minecraft-mcp-1.21.11-neoforge.jar) |

> Предыдущие версии (1.8.9 – 1.20.6) доступны на [странице releases](https://github.com/langyo/minecraft-mod-mcp/releases).

---

## Начало работы

### 1. Установите мод

Скачайте JAR-файл из [GitHub Releases](https://github.com/langyo/minecraft-mod-mcp/releases) и поместите его в папку `mods` вашего Minecraft.

- Требуется **Forge**, **Fabric** или **NeoForge** (см. поддерживаемые версии выше)
- Работает с Minecraft от **1.8.9** до **26.1.2**

### 2. Установите MCP Bridge

```bash
npm install -g minecraft-mod-mcp
```

Или запустите без установки:

```bash
npx minecraft-mod-mcp
```

### 3. Запустите Minecraft

Запустите игру с вашим загрузчиком модов. Мод автоматически запускает HTTP-сервер на порту 9876.

### 4. Подключите ваш ИИ

**[→ Руководство по интеграции ИИ-инструментов](./AI-TOOLS.md)** — пошаговая инструкция для Claude Code, Cursor, Cline, Copilot и более 20 других инструментов.

Или вставьте эту ссылку вашему ИИ-агенту, и он всё настроит сам:

```
https://github.com/langyo/minecraft-mod-mcp/blob/main/docs/guides/ru/AI-TOOLS.md
```

---

## Советы по использованию

### Работа параллельно с модом

Обычно при переключении из Minecraft открывается экран паузы, что может прерывать команды MCP. Используйте один из способов, чтобы этого избежать:

- **Экран паузы**: Нажмите `Esc` для открытия экрана паузы, затем нажмите кнопку **освободить мышь** на MCP-оверлее. Это позволит свободно переключаться между окнами без повторного срабатывания паузы.
- **Внутриигровой оверлей**: В 3D-режиме нажмите кнопку MCP-оверлея в **правом верхнем углу**, чтобы временно отсоединить курсор. После этого можно переключаться по `Alt+Tab`, и игра не будет автоматически ставиться на паузу — идеально для работы в IDE или AI-инструменте при активном MCP-соединении.

### Встроенная отладочная веб-страница

При запуске игры мод запускает HTTP-сервер на `http://localhost:9876` с панелью отладки в реальном времени — именно она показана на скриншоте выше. Откройте её в браузере, чтобы видеть MCP-логи, статус соединения и другую диагностику прямо во время написания кода.

---

## Как это работает

<details>
<summary>📸 Скриншот — нажмите, чтобы развернуть</summary>

<img src="../screenshot.webp" alt="Скриншот Minecraft Mod MCP" width="100%"/>

</details>

```mermaid
flowchart LR
    A["🧠 AI Tool<br/>(Claude Code, Cursor, etc.)<br/>.mcp.json → port 9876"]
    B["🔌 Minecraft Mod MCP<br/>(in-game mod)<br/>HTTP + SSE server"]
    C["🎮 Minecraft Client<br/>(1.8.9 – 26.1.2)"]

    A <-- "HTTP / SSE" --> B
    B -- "reflection" --> C
```

Мод запускает HTTP-сервер на порту 9876 внутри Minecraft. Ваш ИИ-инструмент подключается через стандартный протокол MCP (транспорт SSE), и каждая команда — клик, ввод текста, скриншот и т.д. — использует Java reflection для работы во всех версиях Minecraft без версионно-зависимого кода.

---

## Сборка из исходников

> Этот раздел предназначен для контрибьюторов. Если вы просто хотите использовать мод, см. [Начало работы](#начало-работы) выше.

Подробности о настройке среды разработки, структуре проекта и правилах участия см. в [CONTRIBUTING.md](../../CONTRIBUTING.md).

---

## Лицензия

Распространяется под одной из следующих лицензий:

- Apache License, Version 2.0 ([LICENSE-APACHE](../../LICENSE-APACHE) или http://www.apache.org/licenses/LICENSE-2.0)
- MIT License ([LICENSE-MIT](../../LICENSE-MIT) или http://opensource.org/licenses/MIT)

на ваш выбор.

<!-- markdownlint-disable MD033 MD041 MD036 -->
<div align="center">

<img src="../../logo.webp" alt="Minecraft MCP logo" width="200"/>

# Minecraft MCP

**Deja que la IA juegue Minecraft — Controla cualquier versión, cualquier modloader**

[![License](https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue.svg)](../../LICENSE-MIT)
[![Java](https://img.shields.io/badge/java-8--25-red.svg)](https://www.java.com/)

**[English](../en/README.md)** &bull; **[简体中文](../zhs/README.md)** &bull; **[繁體中文](../zht/README.md)** &bull; **[日本語](../ja/README.md)** &bull; **[한국어](../ko/README.md)** &bull; **[Français](../fr/README.md)** &bull; **Español** &bull; **[Русский](../ru/README.md)**

</div>
<!-- markdownlint-enable MD033 MD041 MD036 -->

## ¿Qué es Minecraft MCP

Minecraft MCP es un puente entre los asistentes de IA y Minecraft. Se ejecuta como un mod dentro del juego, exponiendo un servidor HTTP al que las herramientas de IA — **Claude Code, Cursor, Cline, GitHub Copilot, y más de 20 otras** — pueden conectarse mediante el protocolo estándar MCP. A través de este puente, la IA puede ver el juego, hacer clic en botones, escribir comandos e interactuar con el mundo.

> ¿Quieres que tu IA construya un castillo? ¿Ejecute una prueba de humo? ¿Navegue por el menú de un modpack? Minecraft MCP lo hace posible.

- **Ver** — captura capturas de pantalla con cuadrículas de coordenadas
- **Actuar** — hacer clic, escribir, desplazar, arrastrar y presionar cualquier tecla
- **Saber** — consultar la posición del jugador, información del mundo, botones de la pantalla y campos de depuración
- **Grabar** — transmitir eventos en tiempo real mediante SSE, capturar fotogramas de video

[Guía de integración de herramientas de IA →](./AI-TOOLS.md)

## Versiones compatibles

| Versión MC | Forge | Fabric | NeoForge |
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

> 🚧 = Trabajo en progreso

## Inicio rápido

### Requisitos previos

- JDK 21 (se recomienda Corretto)

### Configuración y compilación

```bash
# Instalar dependencias
pip install -r scripts/requirements.txt

# Compilar todo
just full
```

### Ejecutar

```bash
# Iniciar el daemon y lanzar Minecraft
just daemon
just launch 1.21.7 forge

# O ejecutar una prueba de humo de extremo a extremo
just smoke 1.21.7
```

## Cómo funciona

```
┌────────────────────┐     HTTP/SSE      ┌─────────────────────┐
│  Herramienta IA    │ ◄──────────────► │   Minecraft MCP      │
│  config .mcp.json  │   puerto 9876    │   (mod en el juego)  │
└────────────────────┘                   └──────────┬──────────┘
                                                    │ reflection
                                         ┌──────────▼──────────┐
                                         │   Cliente Minecraft  │
                                         │   (1.8.9 – 26.1.2)  │
                                         └─────────────────────┘
```

El mod ejecuta un servidor HTTP en el puerto 9876 dentro de Minecraft. Tu herramienta de IA se conecta mediante el protocolo estándar MCP (transporte SSE), y cada comando — clic, escribir, captura de pantalla, etc. — utiliza Java reflection para funcionar en todas las versiones de Minecraft sin código específico para cada versión.

## Contribuciones

Se aceptan issues y pull requests.

## Licencia

Licenciado bajo cualquiera de las siguientes:

- Apache License, Version 2.0 ([LICENSE-APACHE](../../LICENSE-APACHE) o http://www.apache.org/licenses/LICENSE-2.0)
- MIT License ([LICENSE-MIT](../../LICENSE-MIT) o http://opensource.org/licenses/MIT)

a su elección.

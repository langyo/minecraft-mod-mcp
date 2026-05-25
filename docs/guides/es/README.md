<!-- markdownlint-disable MD033 MD041 MD036 -->
<div align="center">

<img src="../../logo.webp" alt="Minecraft MCP logo" width="200"/>

# Minecraft MCP

**Mod Puente Minecraft MCP (Model Context Protocol) Multi-Versión y Multi-Modloader**

[![License](https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue.svg)](../../LICENSE-MIT)
[![Java](https://img.shields.io/badge/java-8--25-red.svg)](https://www.java.com/)
[![Python](https://img.shields.io/badge/python-3.10%2B-yellow.svg)](https://www.python.org/)
[![Version](https://img.shields.io/badge/version-0.1.0-lightgrey.svg)]()

**[English](../en/README.md)** &bull; **[简体中文](../zhs/README.md)** &bull; **[繁體中文](../zht/README.md)** &bull; **[日本語](../ja/README.md)** &bull; **[한국어](../ko/README.md)** &bull; **[Français](../fr/README.md)** &bull; **Español** &bull; **[Русский](../ru/README.md)**

</div>
<!-- markdownlint-enable MD033 MD041 MD036 -->

> **Versión 0.1.0** — Desarrollo activo. Los plugins Java Mod y el motor de automatización de flujos de trabajo YAML están operativos. Las builds de CI están en verde para el mod Forge 1.21.7. El soporte para Fabric y NeoForge es WIP.

## Qué es Minecraft MCP

Minecraft MCP (Master Control Program) es un framework de automatización de UI de Minecraft multi-versión y multi-modloader. Consta de dos capas:

- **Plugins Java Mod** (`packages/mods/`) — 24 proyectos de mod que abarcan Forge, Fabric y NeoForge, desde MC 1.8.9 hasta 26.1.2, compartiendo una base de código común (`packages/common/`)
- **Automatización Python** (`scripts/`) — Motor de flujos de trabajo YAML con «Clic de vista previa» (verificación visual de coordenadas antes de hacer clic), ejecutores de pruebas, automatización de builds y gestión de demonios

## Versiones soportadas

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

> 🚧 = WIP (En desarrollo)

## Inicio rápido

### Requisitos previos

- Python 3.10+
- JDK 21 (Corretto recomendado)

### Instalación y compilación

```bash
# Instalar dependencias de Python
pip install -r scripts/requirements.txt

# Verificar el entorno
just check-env

# Compilar todo (generación + caché + compilar todos los mods)
just full
```

### Ejecución

```bash
# Iniciar el demonio del servidor de control
just daemon

# Lanzar una versión de Minecraft
just launch 1.21.7 forge

# Ejecutar una prueba de humo (compilar + lanzar + captura de pantalla)
just smoke 1.21.7
```

## Arquitectura

```
┌─────────────────────────────────────┐
│         Plugin Java Mod              │
│  (Forge / Fabric / NeoForge)        │
│  ReflectionHelper, InputHandler     │
└──────────────┬──────────────────────┘
                │
┌──────────────▼──────────────────────┐
│         Cliente Minecraft            │
│  (1.8.9 – 26.1.2, 24 variantes)    │
└─────────────────────────────────────┘
```

## Documentación

- **[Automatización de flujos de trabajo](workflow.md)** — Automatización de UI basada en YAML con Clic de vista previa
- **[PLAN.md](../../PLAN.md)** — Caso de prueba completado: creación del mundo Redstone Ready
- **[Workflows](../../workflows/)** — Definiciones de pruebas YAML declarativas

## Contribuir

Se aceptan issues y pull requests.

## Licencia

Bajo una de las siguientes licencias, a su elección:

- Apache License, Version 2.0 ([LICENSE-APACHE](../../LICENSE-APACHE) o http://www.apache.org/licenses/LICENSE-2.0)
- MIT License ([LICENSE-MIT](../../LICENSE-MIT) o http://opensource.org/licenses/MIT)

a su elección.

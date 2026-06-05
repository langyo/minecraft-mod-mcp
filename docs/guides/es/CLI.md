# Guía de uso de la CLI NPM MCP

**[English](../en/CLI.md)** &bull; **[简体中文](../zhs/CLI.md)** &bull; **[繁體中文](../zht/CLI.md)** &bull; **[日本語](../ja/CLI.md)** &bull; **[한국어](../ko/CLI.md)** &bull; **[Français](../fr/CLI.md)** &bull; **Español** &bull; **[Русский](../ru/CLI.md)**

> El paquete `minecraft-mod-mcp` proporciona una CLI completa para lanzar clientes y servidores de Minecraft, gestionar versiones y cuentas, y compilar SDKs de mods — todo desde la línea de comandos.

---

## Instalación

```bash
npm install -g minecraft-mod-mcp
```

O ejecutar directamente sin instalar:

```bash
npx minecraft-mod-mcp
```

---

## Comandos

### Servidor MCP

Iniciar el servidor MCP stdio para integración con herramientas IA:

```bash
minecraft-mod-mcp
minecraft-mod-mcp mcp [options]
```

| Opción | Descripción |
|--------|-------------|
| `--no-discover` | No escanear el mod de Minecraft en ejecución |
| `--discover-timeout <ms>` | Tiempo de espera de descubrimiento (defecto: 300000) |

---

### Lanzar cliente — `launch`

Lanza un cliente Minecraft con la versión y el cargador de mods especificados.

```bash
minecraft-mod-mcp launch <version> [options]
```

| Opción | Defecto | Descripción |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | Cargador de mods |
| `--mc-dir <path>` | Auto | Directorio del juego |
| `--java <path>` | Detección auto | Ruta del ejecutable Java |
| `--memory <mb>` | `2048` | Memoria JVM máxima (MB) |
| `--min-memory <mb>` | `512` | Memoria JVM mínima (MB) |
| `--jvm-args <args>` | — | Argumentos JVM adicionales (separados por espacios) |
| `--game-args <args>` | — | Argumentos de juego adicionales (separados por espacios) |
| `--fullscreen` | `false` | Iniciar en pantalla completa |
| `--width <px>` | `854` | Ancho de ventana |
| `--height <px>` | `480` | Alto de ventana |
| `--server <host>` | — | Conectar automáticamente al servidor al iniciar |
| `--server-port <port>` | `25565` | Puerto del servidor |
| `--port <port>` | Auto | Puerto del mod MCP |
| `--mod-jar <path>` | — | JAR del mod a inyectar |
| `--dry-run` | `false` | Mostrar comando sin ejecutar |

**Ejemplos:**

```bash
# Iniciar con 4 GB de RAM, pantalla completa
minecraft-mod-mcp launch 1.21.11 --memory 4096 --fullscreen --loader fabric

# Iniciar con flags JVM personalizados, conexión automática al servidor
minecraft-mod-mcp launch 26.1.2 --jvm-args "-XX:+UseG1GC -Dfml.readTimeout=120" --server myserver.com

# Iniciar en ventana 1280x720
minecraft-mod-mcp launch 1.20.6 --width 1280 --height 720 --loader neoforge

# Vista previa del comando de lanzamiento
minecraft-mod-mcp launch 1.21.11 --dry-run
```

---

### Servidor dedicado — `server`

Inicia un servidor Minecraft dedicado.

```bash
minecraft-mod-mcp server <version> [options]
```

| Opción | Defecto | Descripción |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | Cargador de mods |
| `--java <path>` | Detección auto | Ruta del ejecutable Java |
| `--memory <mb>` | `1024` | Memoria JVM máxima (MB) |
| `--min-memory <mb>` | — | Memoria JVM mínima (MB) |
| `--jvm-args <args>` | — | Argumentos JVM adicionales (separados por espacios) |
| `--game-args <args>` | — | Argumentos de servidor adicionales (separados por espacios) |
| `--mod-jar <path>` | — | JAR del mod a copiar en mods/ del servidor |
| `--dry-run` | `false` | Mostrar comando sin ejecutar |

**Ejemplos:**

```bash
# Iniciar servidor con 4 GB de RAM
minecraft-mod-mcp server 1.21.11 --memory 4096

# Iniciar con ajuste de GC personalizado
minecraft-mod-mcp server 26.1.2 --jvm-args "-XX:+UseZGC -XX:+ZGenerational" --memory 8192

# Servidor Fabric con mod
minecraft-mod-mcp server 1.21.11 --loader fabric --mod-jar ./path/to/mod.jar
```

---

### Todo en uno (servidor + cliente) — `serve`

Un solo comando: instalar servidor + iniciar servidor + iniciar cliente (conexión automática).

```bash
minecraft-mod-mcp serve <version> [options]
```

| Opción | Defecto | Descripción |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | Cargador de mods |
| `--java <path>` | Detección auto | Ruta del ejecutable Java |
| `--memory <mb>` | `2048` | Memoria máx del cliente (MB) |
| `--min-memory <mb>` | — | Memoria mín del cliente (MB) |
| `--server-memory <mb>` | `1024` | Memoria máx del servidor (MB) |
| `--server-min-memory <mb>` | — | Memoria mín del servidor (MB) |
| `--jvm-args <args>` | — | Argumentos JVM adicionales para ambos |
| `--game-args <args>` | — | Argumentos de juego adicionales para el cliente |
| `--server-game-args <args>` | — | Argumentos adicionales para el servidor |
| `--fullscreen` | `false` | Iniciar cliente en pantalla completa |
| `--width <px>` | `854` | Ancho de ventana del cliente |
| `--height <px>` | `480` | Alto de ventana del cliente |
| `--port <port>` | Auto | Puerto MCP |
| `--mod-jar <path>` | — | JAR del mod para ambos lados |
| `--dry-run` | `false` | Mostrar plan sin ejecutar |

**Ejemplo:**

```bash
# Entorno completo: 4 GB cliente, 2 GB servidor, pantalla completa
minecraft-mod-mcp serve 1.21.11 --memory 4096 --server-memory 2048 --fullscreen
```

---

### Gestión de versiones

| Comando | Descripción |
|---------|-------------|
| `minecraft-mod-mcp list` | Listar todas las versiones de Minecraft soportadas |
| `minecraft-mod-mcp installed` | Listar versiones instaladas localmente |
| `minecraft-mod-mcp install <version> [--loader <l>]` | Descargar e instalar una versión |

---

### Gestión de cuentas

| Comando | Descripción |
|---------|-------------|
| `minecraft-mod-mcp auth login` | Iniciar sesión con cuenta Microsoft |
| `minecraft-mod-mcp auth offline <name>` | Crear cuenta sin conexión |
| `minecraft-mod-mcp auth list` | Listar cuentas configuradas |
| `minecraft-mod-mcp auth select <uuid>` | Seleccionar cuenta activa |
| `minecraft-mod-mcp auth remove <uuid>` | Eliminar una cuenta |

---

### Utilidades

| Comando | Descripción |
|---------|-------------|
| `minecraft-mod-mcp java` | Detectar versiones de Java instaladas |
| `minecraft-mod-mcp status` | Mostrar estado de conexión del mod MCP |
| `minecraft-mod-mcp sdk <version> [--loader <l>] [--no-build]` | Compilar SDK de mod para una versión |

---

## Argumentos JVM / Juego

Las opciones `--jvm-args` y `--game-args` aceptan argumentos separados por espacios. En shells que dividen por espacios, encierre el valor completo entre comillas:

```bash
minecraft-mod-mcp launch 1.21.11 --jvm-args "-XX:+UseG1GC -XX:MaxGCPauseMillis=50"
minecraft-mod-mcp server 1.21.11 --game-args "--port 25566 --max-players 10"
```

---

## Archivo de configuración JSON

Los valores predeterminados avanzados se pueden establecer en `~/.minecraft/mcp_launcher/config.json`:

```json
{
  "max_memory_mb": 4096,
  "min_memory_mb": 1024,
  "width": 1920,
  "height": 1080,
  "fullscreen": false,
  "java_args": "-XX:+UseG1GC",
  "game_args": "",
  "game_dir": "~/.minecraft/mcp_launcher/game",
  "mcp_port": 9876,
  "download_source": "bmclapi",
  "language": "es-ES"
}
```

Los flags de la CLI siempre tienen prioridad sobre los valores del archivo de configuración.

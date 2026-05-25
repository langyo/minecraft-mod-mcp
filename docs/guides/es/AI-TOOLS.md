# Guía de Integración de Herramientas de IA

**[English](../en/AI-TOOLS.md)** &bull; **[简体中文](../zhs/AI-TOOLS.md)** &bull; **[繁體中文](../zht/AI-TOOLS.md)** &bull; **[日本語](../ja/AI-TOOLS.md)** &bull; **[한국어](../ko/AI-TOOLS.md)** &bull; **[Français](../fr/AI-TOOLS.md)** &bull; **Español** &bull; **[Русский](../ru/AI-TOOLS.md)**

> **Consejo**: Puedes simplemente pedirle a tu asistente agente de IA que lea esta guía directamente desde la URL de este repositorio. En la mayoría de los casos, el agente configurará la conexión MCP automáticamente — no necesitas configuración manual.

Esta guía explica cómo configurar las principales herramientas de codificación con IA para conectarse al servidor MCP de Minecraft mediante HTTP.

## Endpoints HTTP de Minecraft MCP

El servidor MCP de Minecraft expone los siguientes endpoints HTTP (puerto por defecto: **9876**):

| Endpoint | Método | Descripción |
|----------|--------|-------------|
| `/api/status` | GET | Verificación de estado |
| `/api/cmd` | POST | Despacho de comandos JSON-RPC (cuerpo: `{"cmd":"...", "params":{...}}`) |
| `/api/screenshot` | GET | Toma una captura de pantalla, devuelve PNG en base64 |
| `/api/events` | GET | Flujo SSE (Server-Sent Events) para historial de llamadas en tiempo real |
| `/api/calls` | GET | Devuelve los últimos 50 eventos de llamada como array JSON |

> **Requisitos previos**: Asegúrate de que el daemon de Minecraft MCP esté en ejecución y que un cliente de Minecraft con el mod MCP esté conectado. Ejecuta `just daemon` y luego `just launch <version> <loader>`.

---

## Métodos de Integración

La mayoría de las herramientas de codificación con IA soportan el **Protocolo de Contexto de Modelo (MCP)** para conectarse a servidores externos. El servidor MCP de Minecraft se puede conectar mediante:

- **Transporte SSE**: Apunta el cliente MCP de la herramienta a `http://localhost:9876/api/events`
- **API REST HTTP**: Envía solicitudes POST directamente a `http://localhost:9876/api/cmd`

Las secciones siguientes proporcionan instrucciones de configuración específicas para cada herramienta.

---

## Herramientas de Agentes de Codificación

### Claude Code

Asistente de codificación con IA basado en terminal de Anthropic.

**Configuración**: Crea o edita `.mcp.json` en la raíz de tu proyecto:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

Alternativamente, usa `claude mcp add minecraft-mcp --transport sse http://localhost:9876/api/events`.

### Claude Desktop / Claude for IDE

La aplicación de escritorio y las versiones del plugin para VS Code/JetBrains IDE de Claude.

**Configuración**: Edita `claude_desktop_config.json`:

- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

Para **Claude for IDE** (VS Code / JetBrains), la configuración es la misma — usa el archivo `.mcp.json` en la raíz de tu proyecto.

### OpenCode

Agente de codificación de terminal de código abierto.

**Configuración**: Crea `.opencode.json` en la raíz de tu proyecto o edita `~/.config/opencode/config.json`:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Cursor

Editor de código con IA que prioriza la inteligencia artificial, con soporte para modelos personalizados.

**Configuración**: Crea `.cursor/mcp.json` en la raíz de tu proyecto:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "url": "http://localhost:9876/api/events",
      "transport": "sse"
    }
  }
}
```

O mediante la interfaz de usuario: **Cursor Settings → MCP → Add new MCP Server**, establece el tipo de transporte a **SSE** e introduce la URL.

### Cline

Extensión de codificación con IA para VS Code.

**Configuración**: Abre Configuración de VS Code (`Ctrl+,`), busca `cline.mcpServers`, o añade a `settings.json`:

```json
{
  "cline.mcpServers": {
    "minecraft-mcp": {
      "url": "http://localhost:9876/api/events",
      "transport": "sse"
    }
  }
}
```

### Roo Code

Extensión inteligente para VS Code para escritura y refactorización de código.

**Configuración**: Añade al archivo `settings.json` de VS Code (mismo formato que Cline):

```json
{
  "roo.mcpServers": {
    "minecraft-mcp": {
      "url": "http://localhost:9876/api/events",
      "transport": "sse"
    }
  }
}
```

### Kilo Code

Plugin eficiente para VS Code para generación de código y gestión de proyectos.

**Configuración**: Añade al archivo `settings.json` de VS Code:

```json
{
  "kilo.mcpServers": {
    "minecraft-mcp": {
      "url": "http://localhost:9876/api/events",
      "transport": "sse"
    }
  }
}
```

### GitHub Copilot

Programador de pares con IA de GitHub en VS Code.

**Configuración**: Crea `.github/copilot-instructions.md` en tu espacio de trabajo, o configura MCP mediante la configuración de VS Code:

```json
{
  "github.copilot.mcpServers": {
    "minecraft-mcp": {
      "url": "http://localhost:9876/api/events",
      "transport": "sse"
    }
  }
}
```

### GitHub Copilot CLI

GitHub Copilot para la línea de comandos.

**Configuración**: Establece variables de entorno o usa `gh copilot config`:

```bash
export MCP_SERVER_URL="http://localhost:9876/api/events"
```

### CodeBuddy / WorkBuddy

Herramienta de programación inteligente full-stack potenciada por IA.

**Configuración**: Crea `mcp.json` en la raíz de tu proyecto o espacio de trabajo:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "url": "http://localhost:9876/api/events",
      "transport": "sse"
    }
  }
}
```

### TRAE

Editor con IA capaz de completar de forma independiente diversas tareas de desarrollo.

**Configuración**: Navega a **Settings → MCP Servers → Add Server**:

- **Name**: `minecraft-mcp`
- **Transport**: SSE
- **URL**: `http://localhost:9876/api/events`

### ZCode

Combina potentes agentes de IA con cadenas de herramientas existentes.

**Configuración**: Edita `~/.zcode/config.json`:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Lingma

Asistente de programación inteligente.

**Configuración**: Navega a **Settings → MCP → Add Server**:

- **Name**: `minecraft-mcp`
- **Transport**: SSE
- **URL**: `http://localhost:9876/api/events`

### Qoder

Plataforma de programación con agentes para software del mundo real.

**Configuración**: Edita `~/.qoder/mcp.json`:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Droid

Agente de codificación con IA de terminal, de nivel empresarial, para flujos de trabajo completos.

**Configuración**: Edita `~/.droid/mcp.json`:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Crush

Herramienta de programación con IA para terminal, compatible con interfaces CLI y TUI.

**Configuración**: Edita `~/.crush/config.json`:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Goose

Herramienta de Agente de IA que soporta ejecución local y tareas de ingeniería automatizadas.

**Configuración**: Edita `~/.config/goose/mcp.json`:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Deep Code

Asistente de codificación potenciado por DeepSeek.

**Configuración**: Edita `~/.deepcode/config.json`:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Reasonix

Herramienta de codificación con IA enfocada en razonamiento.

**Configuración**: Edita `~/.reasonix/config.json`:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Langcli

Asistente de codificación con IA basado en CLI.

**Configuración**: Edita `~/.langcli/config.yaml`:

```yaml
mcp_servers:
  minecraft-mcp:
    type: sse
    url: http://localhost:9876/api/events
```

### Oh My Pi

Plataforma versátil de agentes de IA.

**Configuración**: Edita `~/.oh-my-pi/mcp.json`:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Pi

Compañero de codificación ligero con IA.

**Configuración**: Edita `~/.pi/config.json`:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

---

## Herramientas de Agentes Generales

### OpenClaw

Asistente de IA de código abierto que se ejecuta localmente con extensibilidad mediante Skills.

**Configuración**: Edita `openclaw.json` en tu espacio de trabajo:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Cherry Studio

IDE de aplicaciones de IA con soporte para múltiples integraciones de modelos.

**Configuración**: Navega a **Settings → MCP Servers → Add**:

- **Name**: `minecraft-mcp`
- **Transport**: SSE
- **URL**: `http://localhost:9876/api/events`

### Hermes Agent

Agente de IA de código abierto con auto-evolución y memoria persistente.

**Configuración**: Edita `~/.hermes/config.json`:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### AstrBot

Framework de bots potenciado por IA.

**Configuración**: Edita `astrbot_config.json`:

```json
{
  "mcp_servers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### nanobot

Agente de IA ligero para diversas tareas.

**Configuración**: Edita `~/.nanobot/config.json`:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

---

## Acceso Directo a la API REST HTTP

Para herramientas que no soportan nativamente el protocolo MCP, puedes interactuar con el servidor MCP de Minecraft directamente a través de su API REST HTTP:

```bash
# Verificación de estado
curl http://localhost:9876/api/status

# Ejecutar un comando
curl -X POST http://localhost:9876/api/cmd \
  -H "Content-Type: application/json" \
  -d '{"cmd":"screenshot","params":{}}'

# Tomar una captura de pantalla
curl http://localhost:9876/api/screenshot

# Suscribirse a eventos (flujo SSE)
curl http://localhost:9876/api/events
```

### Comandos Comunes

| Comando | Descripción |
|---------|-------------|
| `screenshot` | Toma una captura de pantalla de la ventana de Minecraft |
| `click` | Hace clic en las coordenadas (x, y) |
| `press_key` | Presiona una tecla del teclado |
| `type_text` | Escribe una cadena de texto |
| `scroll` | Realiza un desplazamiento con el ratón |
| `execute_command` | Ejecuta un comando slash de Minecraft |
| `get_player_info` | Obtiene la posición y el estado del jugador |
| `get_world_info` | Obtiene información del mundo |

---

## Solución de Problemas

1. **Conexión rechazada**: Asegúrate de que el daemon MCP esté en ejecución (`just daemon`) y que un cliente de Minecraft esté iniciado.
2. **Timeout de SSE**: Algunas herramientas pueden desconectarse del SSE tras un período de inactividad. Reinicia la herramienta o la conexión SSE.
3. **Conflicto de puerto**: Si el puerto 9876 está en uso, configura un puerto diferente mediante la variable de entorno `MCP_PORT` o la propiedad del sistema `mcp.server.port`.
4. **Firewall**: Asegúrate de que tu firewall permita conexiones a `localhost:9876`.

> Para problemas o preguntas, por favor abre un issue en el [repositorio de GitHub](https://github.com/langyo/minecraft-mod-mcp).

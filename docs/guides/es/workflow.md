# Sistema de automatización MCP Workflow

## Descripción general

MCP Workflow es un framework de automatización de Minecraft basado en YAML que reemplaza el anterior `run.py` basado en argumentos de línea de comandos.

**Filosofía central de diseño: Preview Click (clic con vista previa)** — antes de ejecutar un clic real, primero se dibuja un marcador de punto rojo en la captura de pantalla, se confirma visualmente y luego se ejecuta el clic. Esto evita adivinar coordenadas a ciegas.

## Inicio rápido

```bash
# Ejecución en seco (sin iniciar MC, solo valida el análisis YAML y la lógica de pasos)
python scripts/run_yaml.py workflows/smoke_test.yaml --dry-run --skip-setup

# Ejecución completa (inicia MC automáticamente + ejecuta todos los pasos)
python scripts/run_yaml.py workflows/smoke_test.yaml

# Omitir la fase de inicio si MC ya está en ejecución
python scripts/run_yaml.py workflows/smoke_test.yaml --skip-setup

# Ejecutar solo el paso 5
python scripts/run_yaml.py workflows/smoke_test.yaml --step 5

# Desactivar la integración del contenedor
python scripts/run_yaml.py workflows/smoke_test.yaml --no-container
```

## Flujo de Preview Click

Esta es la innovación central del sistema. El enfoque tradicional envía directamente las coordenadas para hacer clic; si las coordenadas son incorrectas, el clic se desperdicia. El flujo de vista previa es un proceso de dos etapas:

```
Paso 1: preview_click    → Pone en cola un marcador de punto rojo en (x,y), no realiza ninguna acción
Paso 2: screenshot       → Captura de pantalla + dibuja punto rojo/retícula/texto de coordenadas en la imagen
         ↓ La IA revisa la captura de pantalla anotada ↓
Paso 3: click            → Después de confirmar que las coordenadas son correctas, ejecuta el clic real
```

### Efectos de anotación del punto rojo

- **Círculo rojo** (radio configurable, predeterminado 10px)
- **Retícula** (líneas horizontal + vertical)
- **Etiqueta de coordenadas** `(426,236) Un jugador` con fondo negro semitransparente

### Ejemplo YAML

```yaml
# Paso 1: Vista previa de las coordenadas
- action: preview_click
  x: 426
  y: 236
  label: "Un jugador"
  radius: 10
  color: "#FF0000"
  comment: "Vista previa: dibujar un punto rojo en el botón Un jugador"

# Paso 2: Captura de pantalla (incluirá automáticamente el punto rojo)
- action: screenshot
  name: "preview_singleplayer"
  comment: "Revisión IA: ¿está el punto rojo sobre el botón?"

# Paso 3: Clic después de confirmar
- action: click
  x: 426
  y: 236
  comment: "Coordenadas confirmadas, ejecutar clic"
```

## Acciones compatibles

| Acción | Parámetros | Descripción |
|------|------|------|
| `wait` | `seconds` | Esperar el número especificado de segundos |
| `screenshot` | `name` | Capturar y guardar una captura de pantalla (dibuja automáticamente los marcadores de vista previa en cola) |
| **`preview_click`** | `x, y, label, radius, color` | **Poner en cola un marcador de punto rojo, no realiza clic** |
| `click` | `x, y` | Ejecutar un clic en las coordenadas |
| `click_btn_idx` | `index` | Hacer clic en un botón por índice de widget |
| `click_btn_id` | `button_id` | Hacer clic en un botón por ID |
| `ctrl_on` | - | Entrar en modo de control MCP (ratón desacoplado) |
| `ctrl_off` | - | Salir del modo de control |
| `key` | `key` | Pulsar una tecla (ej. `Escape`, `E`) |
| `paste` | `text, press_enter` | Pegar texto (evita problemas de IME) |
| `scroll` | `clicks` | Desplazar la rueda del ratón |
| `look_delta` | `dyaw, dpitch` | Rotar el ángulo de vista en el juego (no mueve el ratón) |
| `set_view_angle` | `yaw, pitch` | Establecer el ángulo de vista absoluto |
| `right_click` | - | Clic derecho |
| `enumerate_widgets` | - | Enumerar todos los widgets en la pantalla actual |
| `get_screen_buttons` | - | Obtener la lista de botones de la pantalla actual |
| `cmd` | `command` | Ejecutar un comando MC (ej. `/gamemode creative`) |
| `vision_check` | `prompt, expect, store_as` | Análisis visual de captura de pantalla por IA |

## Formato del workflow YAML

```yaml
name: "Nombre del workflow"
description: |
  Descripción multilínea explicando qué hace este workflow y qué valida.

setup:
  version: "1.21.7-forge-57.0.2"   # Versión de MC
  container: false                   # Si usar ventana contenedora
  wait_after_connect: 15             # Segundos de espera para que MC cargue después de conectar

steps:
  # Cada paso es una acción, ejecutada en orden
  - action: wait
    seconds: 15
    comment: "Esperar a que MC termine de cargar"

  - action: ctrl_on
    comment: "Entrar en modo de control"

  - action: screenshot
    name: "baseline"
    comment: "Captura de pantalla de referencia"
```

## Campos de configuración setup

| Campo | Tipo | Valor predeterminado | Descripción |
|------|------|--------|------|
| `version` | string | `"1.21.7-forge-57.0.2"` | Identificador de versión de MC |
| `container` | bool | `true` | Si usar integración de contenedor Win32 |
| `wait_after_connect` | int | `15` | Segundos de espera después de la conexión del mod (para que desaparezca la pantalla de inicio de Mojang) |

## Argumentos CLI

| Argumento | Descripción |
|------|------|
| `<workflow.yaml>` | Ruta al archivo de workflow YAML (obligatorio) |
| `--dry-run` | Modo de ejecución en seco, no envía comandos reales a MC |
| `--skip-setup` | Omitir el inicio de MC (usar cuando MC ya está en ejecución) |
| `--step N` | Ejecutar solo el paso N (indexado desde 1) |
| `--interactive` | Pausar después de cada paso y esperar confirmación |
| `--no-container` | Desactivar la ventana contenedora |

## Estructura de archivos

```
minecraft-neoforge-mcp/
├── workflows/                        # Definiciones de workflow YAML
│   └── smoke_test.yaml               # Prueba de humo: menú principal → en juego → rotación de vista
├── scripts/
│   ├── run_yaml.py                   # Punto de entrada del ejecutor YAML
│   ├── workflow_engine.py            # Motor principal (ejecución de acciones, gestión de estado, anotación de capturas)
│   └── run.py                       # Ejecutor CLI antiguo (aún utilizable)
├── mcp-common/                       # Código común del mod (entrada por reflexión, captura de pantalla, modo de control)
│   └── src/main/java/.../
│       ├── ReflectionHelper.java     # guiClick, preview_click, lookDelta, etc.
│       ├── McpMessageHandler.java    # Distribución de mensajes WebSocket
│       └── ReflectedInputHandler.java# Manejador de entrada
└── docs/
    └── workflow.md                   # Este documento
```

## Diferencias con el antiguo run.py

| Característica | run.py (Antiguo) | run_yaml.py (Nuevo) |
|------|-------------|-----------------|
| Formato | Argumentos CLI | Archivo YAML |
| Reutilización | Baja (escribir argumentos cada vez) | Alta (YAML versionable y compartible) |
| Preview click | No compatible | ✅ preview_click + anotación de captura |
| Comentarios estructurados | Ninguno | Cada paso tiene un campo comment |
| Recuperación de errores | Ninguna | Seguimiento de estado success/error por paso |
| Ramificación condicional | No compatible | vision_check / if_screen |

## Creación de workflows personalizados

1. Copiar `workflows/smoke_test.yaml` como plantilla
2. Modificar `name`, `description` y `steps`
3. Validar primero la sintaxis con `--dry-run --skip-setup`
4. Probar con una instancia MC real

### Patrón típico: Encontrar un botón y hacer clic

```yaml
# 1. Primero capturar la pantalla actual
- action: screenshot
  name: "current_screen"

# 2. Usar visión IA para encontrar las coordenadas del botón (o leer manualmente de los resultados enum)
- action: vision_check
  prompt: "Encontrar las coordenadas de píxeles del centro del botón 'Crear nuevo mundo'"
  store_as: "create_world_pos"

# 3. Vista previa de esa coordenada
- action: preview_click
  x: "${variables.create_world_pos.x}"   # TODO: referencia de variable por implementar
  y: "${variables.create_world_pos.y}"
  label: "Crear nuevo mundo"

# 4. Captura de pantalla para revisión
- action: screenshot
  name: "preview_create_world"

# 5. Confirmar y hacer clic
- action: click
  x: 350
  y: 420
```

## Notas importantes

1. **No entrar en modo de control durante la pantalla de inicio de Mojang** — esto hará que el juego se congele en el logo. `setup.wait_after_connect` debe ser ≥ 15 segundos.
2. **Preferir Robot (AWT) para las capturas de pantalla** sobre las capturas nativas de MC — las capturas nativas de MC pueden devolver fotogramas en caché.
3. **Dar suficiente espera después de cada clic** — las transiciones de GUI llevan tiempo, normalmente de 3 a 8 segundos.
4. **Los marcadores de preview_click se dibujan en la siguiente captura de pantalla** — es un mecanismo de cola, no un efecto inmediato.

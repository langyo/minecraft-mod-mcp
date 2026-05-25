# MCP Workflow Automation System

## Overview

MCP Workflow is a YAML-based Minecraft automation framework that replaces the previous CLI-argument-based `run.py`.

**Core design philosophy: Preview Click** — before executing a real click, first draw a red dot marker on the screenshot, visually confirm the position, then perform the actual click. This avoids blind coordinate guessing.

## Quick Start

```bash
# Dry run (no MC launch, validates YAML parsing and step logic only)
python scripts/run_yaml.py workflows/smoke_test.yaml --dry-run --skip-setup

# Full run (auto-launch MC + execute all steps)
python scripts/run_yaml.py workflows/smoke_test.yaml

# Skip startup phase when MC is already running
python scripts/run_yaml.py workflows/smoke_test.yaml --skip-setup

# Execute step 5 only
python scripts/run_yaml.py workflows/smoke_test.yaml --step 5

# Disable container embedding
python scripts/run_yaml.py workflows/smoke_test.yaml --no-container
```

## Preview Click Flow

This is the core innovation of the system. The traditional approach directly sends coordinates to click; if the coordinates are wrong, the click is wasted. The preview flow is a two-stage process:

```
Step 1: preview_click    → Queues a red dot marker at (x,y), no action taken
Step 2: screenshot       → Captures screen + draws red dot/crosshair/coordinate text on the image
         ↓ AI reviews the annotated screenshot ↓
Step 3: click            → After confirming the coordinates are correct, executes the real click
```

### Red Dot Annotation Effects

- **Red circle** (configurable radius, default 10px)
- **Crosshair** (horizontal + vertical lines)
- **Coordinate label** `(426,236) Singleplayer` with a semi-transparent black background

### YAML Example

```yaml
# Step 1: Preview the coordinate
- action: preview_click
  x: 426
  y: 236
  label: "Singleplayer"
  radius: 10
  color: "#FF0000"
  comment: "Preview: draw a red dot on the Singleplayer button"

# Step 2: Screenshot (will include the red dot automatically)
- action: screenshot
  name: "preview_singleplayer"
  comment: "AI review: is the red dot on the button?"

# Step 3: Click after confirmation
- action: click
  x: 426
  y: 236
  comment: "Coordinates confirmed, execute click"
```

## Supported Actions

| Action | Parameters | Description |
|------|------|------|
| `wait` | `seconds` | Wait for the specified number of seconds |
| `screenshot` | `name` | Capture and save a screenshot (automatically draws queued preview markers) |
| **`preview_click`** | `x, y, label, radius, color` | **Queue a red dot marker, does not perform a click** |
| `click` | `x, y` | Execute a coordinate click |
| `click_btn_idx` | `index` | Click a button by widget index |
| `click_btn_id` | `button_id` | Click a button by ID |
| `ctrl_on` | - | Enter MCP control mode (mouse decoupled) |
| `ctrl_off` | - | Exit control mode |
| `key` | `key` | Press a key (e.g. `Escape`, `E`) |
| `paste` | `text, press_enter` | Paste text (bypasses IME issues) |
| `scroll` | `clicks` | Scroll the mouse wheel |
| `look_delta` | `dyaw, dpitch` | Rotate in-game view angle (does not move mouse) |
| `set_view_angle` | `yaw, pitch` | Set absolute view angle |
| `right_click` | - | Right-click |
| `enumerate_widgets` | - | List all widgets on the current screen |
| `get_screen_buttons` | - | Get the current screen button list |
| `cmd` | `command` | Execute an MC command (e.g. `/gamemode creative`) |
| `vision_check` | `prompt, expect, store_as` | AI vision analysis of screenshot |

## YAML Workflow Format

```yaml
name: "Workflow Name"
description: |
  Multi-line description explaining what this workflow does and what it validates.

setup:
  version: "1.21.7-forge-57.0.2"   # MC version
  container: false                   # Whether to use container window
  wait_after_connect: 15             # Seconds to wait for MC to load after connecting

steps:
  # Each step is an action, executed in order
  - action: wait
    seconds: 15
    comment: "Wait for MC to finish loading"

  - action: ctrl_on
    comment: "Enter control mode"

  - action: screenshot
    name: "baseline"
    comment: "Baseline screenshot"
```

## Setup Configuration Fields

| Field | Type | Default | Description |
|------|------|--------|------|
| `version` | string | `"1.21.7-forge-57.0.2"` | MC version identifier |
| `container` | bool | `true` | Whether to use Win32 container embedding |
| `wait_after_connect` | int | `15` | Seconds to wait after mod connection (for the Mojang splash screen to disappear) |

## CLI Arguments

| Argument | Description |
|------|------|
| `<workflow.yaml>` | Path to the YAML workflow file (required) |
| `--dry-run` | Dry run mode, does not send actual commands to MC |
| `--skip-setup` | Skip MC startup (use when MC is already running) |
| `--step N` | Execute only step N (1-indexed) |
| `--interactive` | Pause after each step and wait for confirmation |
| `--no-container` | Disable container window |

## File Structure

```
minecraft-mcp/
├── workflows/                        # YAML workflow definitions
│   └── smoke_test.yaml               # Smoke test: main menu → in-game → view rotation
├── scripts/
│   ├── run_yaml.py                   # YAML runner entry point
│   ├── workflow_engine.py            # Core engine (action execution, state management, screenshot annotation)
│   └── run.py                       # Legacy CLI runner (still usable)
├── packages/common/                  # Mod common code (reflection input, screenshot, control mode)
│   └── src/main/java/.../
│       ├── ReflectionHelper.java     # guiClick, preview_click, lookDelta, etc.
│       ├── McpMessageHandler.java    # WebSocket message dispatch
│       └── ReflectedInputHandler.java# Input handler
└── docs/
    └── workflow.md                   # This document
```

## Differences from Legacy run.py

| Feature | run.py (Legacy) | run_yaml.py (New) |
|------|-------------|-----------------|
| Format | CLI arguments | YAML file |
| Reusability | Low (type arguments each time) | High (YAML is version-controlled and shareable) |
| Preview click | Not supported | ✅ preview_click + screenshot annotation |
| Structured comments | None | Each step has a comment field |
| Error recovery | None | Per-step success/error state tracking |
| Conditional branching | Not supported | vision_check / if_screen |

## Writing Custom Workflows

1. Copy `workflows/smoke_test.yaml` as a template
2. Modify `name`, `description`, and `steps`
3. Validate syntax first with `--dry-run --skip-setup`
4. Test with a real MC instance

### Common Pattern: Find a Button and Click It

```yaml
# 1. Screenshot the current screen first
- action: screenshot
  name: "current_screen"

# 2. Use AI vision to find button coordinates (or manually read from enum results)
- action: vision_check
  prompt: "Find the center pixel coordinates of the 'Create New World' button"
  store_as: "create_world_pos"

# 3. Preview that coordinate
- action: preview_click
  x: "${variables.create_world_pos.x}"   # TODO: variable reference to be implemented
  y: "${variables.create_world_pos.y}"
  label: "Create New World"

# 4. Screenshot for review
- action: screenshot
  name: "preview_create_world"

# 5. Confirm and click
- action: click
  x: 350
  y: 420
```

## Important Notes

1. **Do not enter control mode during the Mojang splash screen** — this will cause the game to freeze on the logo. `setup.wait_after_connect` should be ≥ 15 seconds.
2. **Prefer Robot (AWT) for screenshots** over MC native screenshots — MC native screenshots may return cached frames.
3. **Allow enough wait time after each click** — GUI transitions take time, typically 3–8 seconds.
4. **preview_click markers are drawn on the next screenshot** — this is a queue mechanism, not immediate.

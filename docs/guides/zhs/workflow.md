# MCP Workflow 自动化系统

## 概述

MCP Workflow 是基于 YAML 的 Minecraft 自动化操作框架，替代之前命令行参数式的 `run.py`。

**核心设计理念：预选点击（Preview Click）** — 在真正执行点击前，先在截图上绘制红点标记，经视觉确认后再执行，避免盲目猜测坐标。

## 快速开始

```bash
# 干跑（不启动 MC，只验证 YAML 解析和步骤逻辑）
python scripts/run_yaml.py workflows/smoke_test.yaml --dry-run --skip-setup

# 完整运行（自动启动 MC + 执行全部步骤）
python scripts/run_yaml.py workflows/smoke_test.yaml

# MC 已在运行时跳过启动阶段
python scripts/run_yaml.py workflows/smoke_test.yaml --skip-setup

# 只执行第 5 步
python scripts/run_yaml.py workflows/smoke_test.yaml --step 5

# 不使用容器嵌入
python scripts/run_yaml.py workflows/smoke_test.yaml --no-container
```

## 预选点击流程

这是本系统的核心创新。传统方式是直接发送坐标点击，如果坐标猜错了就白点了。预选流程分两步：

```
Step 1: preview_click    → 排队一个红点标记 (x,y)，不执行任何操作
Step 2: screenshot       → 截屏 + 在图片上绘制红点/十字线/坐标文字
         ↓ AI 审核带标注的截图 ↓
Step 3: click            → 确认坐标正确后，才执行真实点击
```

### 红点标注效果

- **红色圆圈** (可配置半径，默认 10px)
- **十字准线** (水平 + 垂直线)
- **坐标标签** `(426,236) 单人游戏` 带黑色半透明背景

### YAML 写法示例

```yaml
# 第一步：预选坐标
- action: preview_click
  x: 426
  y: 236
  label: "单人游戏"
  radius: 10
  color: "#FF0000"
  comment: "预选: 在单人游戏按钮位置画红点"

# 第二步：截图（会自动带上红点）
- action: screenshot
  name: "preview_singleplayer"
  comment: "AI 审核: 红点是否在按钮上？"

# 第三步：确认后点击
- action: click
  x: 426
  y: 236
  comment: "确认坐标正确，执行点击"
```

## 支持的动作

| 动作 | 参数 | 说明 |
|------|------|------|
| `wait` | `seconds` | 等待指定秒数 |
| `screenshot` | `name` | 截屏并保存（自动绘制排队中的预选标记） |
| **`preview_click`** | `x, y, label, radius, color` | **排队红点标记，不执行点击** |
| `click` | `x, y` | 执行坐标点击 |
| `click_btn_idx` | `index` | 按控件索引点击按钮 |
| `click_btn_id` | `button_id` | 按 ID 点击按钮 |
| `ctrl_on` | - | 进入 MCP 控制模式（鼠标解耦） |
| `ctrl_off` | - | 退出控制模式 |
| `key` | `key` | 按键（如 `Escape`, `E`） |
| `paste` | `text, press_enter` | 粘贴文本（绕过 IME 问题） |
| `scroll` | `clicks` | 滚轮滚动 |
| `look_delta` | `dyaw, dpitch` | 游戏内视角旋转（不移动鼠标） |
| `set_view_angle` | `yaw, pitch` | 设置绝对视角角度 |
| `right_click` | - | 右键点击 |
| `enumerate_widgets` | - | 列出当前界面所有控件 |
| `get_screen_buttons` | - | 获取当前屏幕按钮列表 |
| `cmd` | `command` | 执行 MC 命令（如 `/gamemode creative`） |
| `vision_check` | `prompt, expect, store_as` | AI 视觉分析截图 |

## YAML 工作流格式

```yaml
name: "工作流名称"
description: |
  多行描述，说明这个工作流做什么、验证什么。

setup:
  version: "1.21.7-forge-57.0.2"   # MC 版本
  container: false                   # 是否使用容器窗口
  wait_after_connect: 15             # 连接后等待 MC 加载的秒数

steps:
  # 每个 step 是一个动作，按顺序执行
  - action: wait
    seconds: 15
    comment: "等待 MC 加载完成"

  - action: ctrl_on
    comment: "进入控制模式"

  - action: screenshot
    name: "baseline"
    comment: "基线截图"
```

## setup 配置项

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `version` | string | `"1.21.7-forge-57.0.2"` | MC 版本标识 |
| `container` | bool | `true` | 是否使用 Win32 容器嵌入 |
| `wait_after_connect` | int | `15` | Mod 连接后等待秒数（等 Mojang 启动画面消失） |

## CLI 参数

| 参数 | 说明 |
|------|------|
| `<workflow.yaml>` | YAML 工作流文件路径（必填） |
| `--dry-run` | 干跑模式，不发送实际命令到 MC |
| `--skip-setup` | 跳过 MC 启动（MC 已在运行时使用） |
| `--step N` | 只执行第 N 步（1-indexed） |
| `--interactive` | 每步暂停等待确认 |
| `--no-container` | 不使用容器窗口 |

## 文件结构

```
minecraft-neoforge-mcp/
├── workflows/                        # YAML 工作流定义
│   └── smoke_test.yaml               # 冒烟测试：主菜单→游戏内→视角旋转
├── scripts/
│   ├── run_yaml.py                   # YAML 运行器入口
│   ├── workflow_engine.py            # 核心引擎（动作执行、状态管理、截图标注）
│   └── run.py                       # 旧版命令行 runner（仍可用）
├── mcp-common/                       # Mod 公共代码（反射输入、截图、控制模式）
│   └── src/main/java/.../
│       ├── ReflectionHelper.java     # guiClick, preview_click, lookDelta 等
│       ├── McpMessageHandler.java    # WebSocket 消息分发
│       └── ReflectedInputHandler.java# 输入处理器
└── docs/
    └── workflow.md                   # 本文档
```

## 与旧版 run.py 的区别

| 特性 | run.py (旧) | run_yaml.py (新) |
|------|-------------|-----------------|
| 格式 | 命令行参数 | YAML 文件 |
| 可复用性 | 低（每次手敲参数） | 高（YAML 可版本控制、分享） |
| 预选点击 | 不支持 | ✅ preview_click + 截图标注 |
| 结构化注释 | 无 | 每个 step 有 comment 字段 |
| 错误恢复 | 无 | 每步有 success/error 状态记录 |
| 条件分支 | 不支持 | vision_check / if_screen |

## 编写自定义工作流

1. 复制 `workflows/smoke_test.yaml` 作为模板
2. 修改 `name`、`description`、`steps`
3. 先用 `--dry-run --skip-setup` 验证语法
4. 用真实 MC 测试

### 典型模式：找到按钮并点击

```yaml
# 1. 先截图看当前界面
- action: screenshot
  name: "current_screen"

# 2. 用 AI 视觉找按钮坐标（或手动从 enum 结果中读取）
- action: vision_check
  prompt: "找到「创建新的世界」按钮的中心像素坐标"
  store_as: "create_world_pos"

# 3. 预选该坐标
- action: preview_click
  x: "${variables.create_world_pos.x}"   # TODO: 变量引用待实现
  y: "${variables.create_world_pos.y}"
  label: "创建新的世界"

# 4. 截图审核
- action: screenshot
  name: "preview_create_world"

# 5. 确认点击
- action: click
  x: 350
  y: 420
```

## 注意事项

1. **不要在 Mojang 启动画面期间进入控制模式** — 会导致游戏卡在 logo 上。`setup.wait_after_connect` 应 ≥ 15 秒。
2. **截图优先使用 Robot (AWT)** 而非 MC 原生截图 — MC 原生截图可能返回缓存帧。
3. **每次 click 后要给足够 wait** — GUI 切换需要时间，通常 3-8 秒。
4. **preview_click 的标记在下一个 screenshot 时绘制** — 这是队列机制，不是立即生效。

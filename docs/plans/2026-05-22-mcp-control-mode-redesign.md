# MCP 接管模式重设计

## 背景

当前 MCP 的操作模式存在多个问题：
1. 鼠标控制权不明确：MCP 操作与玩家操作冲突
2. 截图包含遮罩
3. 命令/聊天输入不可靠
4. 玩家无法明确感知 MCP 何时在操控

## 设计

### 状态机

```
正常游戏 ──ESC──▶ 暂停菜单 ──点击[MCP接管]──▶ MCP接管模式
   ▲                                         │
   └───────── 点击[恢复手动控制] ◀────────────┤
                    点击[打开系统菜单] ──▶ 暂停菜单
```

### MCP 接管模式

进入条件：玩家在暂停菜单点击「MCP接管」按钮（最后一个按钮）

**遮罩层：**
- 半透明遮罩覆盖整个游戏窗口
- 遮罩下方两个按钮：「恢复手动控制」「打开系统菜单」（国际化）
- 遮罩期间：所有 LWJGL 鼠标事件被完全拦截
- 只有遮罩上的两个按钮响应点击
- 截图时不包含遮罩，只截取遮罩下方的内容（3D + 原版/其他mod的GUI）

**操作模式（MCP 自动切换，玩家不感知）：**

| 模式 | 操作 | 鼠标 |
|------|------|------|
| 3D操纵 | 视角移动、玩家移动、挖掘、放置、切换物品栏、跳跃、潜行 | 不动鼠标 |
| GUI操作 | 点击按钮、输入文字、滚动列表、拖动 | 使用内部鼠标 |

### HTTP API 行为

| 状态 | 允许的请求 | 拒绝的请求 |
|------|-----------|-----------|
| 未接管 | `ping`, `status`, `screenshot` | 所有操作请求（click, press_key 等） |
| 接管模式 | 所有请求 | 无 |

未接管时收到操作请求 → 返回 `{"error": "not in control mode"}`

### 实现要点

1. **暂停菜单按钮注入**：通过 Forge 事件（`ScreenEvent.Init.Post`）向 PauseScreen 添加一个 Button
2. **遮罩渲染**：通过 `RenderGuiOverlayEvent` 或类似事件在游戏画面上方渲染半透明遮罩 + 按钮
3. **鼠标拦截**：接管模式下，通过 GLFW `glfwSetInputMode` + 反射设置 mouseGrabbed=false，同时用 GLFW cursor pos callback 或 Win32 钩子拦截鼠标事件
4. **截图穿透遮罩**：截图时临时隐藏遮罩，截取后再恢复
5. **按钮点击处理**：遮罩上的按钮通过 GLFW 鼠标回调或 Win32 命中测试来响应

### 3D 操纵模式操作列表

- `set_view_angle` / `look_delta` — 视角
- `press_key` (WASD/空格/Shift) — 移动
- `hotkey` (1-9) — 切换物品栏
- `right_click` / `use_item` — 使用物品
- `place_block` — 放置方块
- `left_click`（新） — 挖掘/攻击
- `jump` — 跳跃
- `sneak` — 潜行

### GUI 操纵模式操作列表

- `click` — 鼠标左键点击
- `right_click` — 鼠标右键点击
- `scroll` / `direct_scroll` — 滚轮
- `mouse_drag` — 拖动
- `type_text` / `paste_text` — 文字输入
- `press_key` (Escape/Enter/Tab 等) — 按键
- `hotkey` (Ctrl+A 等) — 组合键
- `click_button_index` / `click_button_id` — 按钮点击
- `select_list_item` — 列表选择
- `switch_tab` — 标签切换
- `enumerate_widgets` / `get_screen_buttons` — 读取GUI
- `open_chat` — 打开聊天
- `close_screen` — 关闭界面
- `pause_game` — 打开暂停菜单

### 遮罩上的按钮文本（国际化）

使用 MC 的翻译系统：
- 「恢复手动控制」→ `mcp.control.resume` = "Resume Manual Control" / "恢复手动控制"
- 「打开系统菜单」→ `mcp.control.system_menu` = "Open System Menu" / "打开系统菜单"
- 「MCP接管」(暂停菜单按钮) → `mcp.control.take_over` = "MCP Take Over" / "MCP 接管"

### 截图策略

截图时：
1. 临时标记 `hideOverlay = true`
2. 调用 `forceRenderOneFrame()` 渲染一帧
3. `glReadPixels` 读取帧缓冲
4. 恢复 `hideOverlay = false`

这样截图只包含游戏内容，不包含遮罩。

### 现有代码改动范围

1. **ReflectionHelper.java**：
   - 新增 `enterMcpControlMode()` — 进入接管模式
   - 修改 `exitMcpControlMode()` — 退出接管模式  
   - 修改截图逻辑 — 跳过遮罩渲染
   - 新增鼠标拦截逻辑

2. **ModDevMcpMod.java**：
   - `ScreenEvent.Init.Post` — 注入暂停菜单按钮
   - `RenderGuiOverlayEvent` — 渲染遮罩
   - 鼠标事件拦截

3. **McpMessageHandler.java**：
   - 操作请求增加接管模式检查
   - 未接管时返回错误

4. **新增**：`McpControlOverlay.java` — 遮罩渲染 + 按钮处理类

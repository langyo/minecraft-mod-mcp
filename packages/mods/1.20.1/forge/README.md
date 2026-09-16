# MCP-HELPER（mcpmod）

本目录整合了用户提供的 r4 修复源码，专用于 Minecraft 1.20.1 Forge。
`src/main/java` 中的同名类覆盖 `packages/common` 的反射实现；未覆盖的类和资源仍使用公共版本。
`client-overrides.gradle` 负责排除重复文件并执行 `reobfJar`，不要把这里的 `McBridge` 复制到公共模块。
构建使用上游固定的 Forge 47.4.10；原始 r4 工程使用 47.4.0。

在本目录以 Java 17 运行 `./gradlew build`（Windows 使用 `gradlew.bat build`）。
以下说明介绍整合的功能；生产环境下的游戏内行为仍需实际验证。

面向 AI 代理的 Minecraft 客户端桥接模组。它在游戏内起一个本地 HTTP 服务，
让外部程序（AI 代理、脚本、调试工具）能读取游戏状态、截图、模拟操作，
并在需要时**接管**鼠标与界面。

- 模组 ID：`mcpmod`
- 显示名：MCP-HELPER（保留原 ModDev MCP 的 `mcpmod` ID 和接口）
- 目标环境：**Minecraft 1.20.1 + Forge 47.4.x（客户端模组）**
- Java：17
- 协议：本地 HTTP/JSON 接口 + SSE 事件流（不是持久 WebSocket 长连接）
- 作者：langyo　许可证：MIT

> **本版（r4）要点**：单机按 `ESC` 默认不再冻结世界（`PauseScreen` 换成不暂停的等价菜单）；
> 控制模式默认改为 `shared`，**玩家可以自由转动视角**；新增 `open_to_lan`
> 可以一键把单机世界开到局域网，配合第二个客户端就能让 AI 拿到完全独立的视角。
> 细节见第六节。

---

## 一、安装

1. 安装 **Minecraft 1.20.1** 与 **Forge 47.4.x**。
2. 把构建产物放进 `.minecraft/mods/`：

   ```
   build/libs/MCP-HELPER-1.20.1-forge-0.3.0.jar
   ```

3. 启动游戏。模组初始化后约 **5 秒**，模组会在后台线程 `MCP-HTTP` 上启动服务。
4. 浏览器打开 `http://127.0.0.1:9876/debug` 可看到内置调试页；或用下面的接口自测。

> 这是**纯客户端模组**。单人和联机都能用，但联机时命令权限仍受服务器约束。

---

## 二、端口与访问

| 项目 | 说明 |
| --- | --- |
| 默认端口 | `9876` |
| 监听地址 | `0.0.0.0`（同一局域网的其它机器也能访问，见「安全提示」） |
| 指定端口 | JVM 参数 `-Dmcp.port=12345`，或环境变量 `MC_MCP_PORT=12345` |
| 服务启动时机 | 模组初始化后约 5 秒，后台线程启动 |

**注意：** 默认端口被占用时，本次启动的 HTTP 服务会直接失败（日志 `[MCP-MOD] HTTP server failed: ...`），
当前版本不会自动改用其它端口。遇到这种情况请用 `-Dmcp.port=` 显式换一个端口。

可选 JVM 参数（会体现在 `/api/status` 里）：

```
-Dmcp.port=9876        # 服务端口
-Dmcp.mod.version=...  # 覆盖 /api/status 的 version 字段
-Dmcp.mod.loader=...   # 覆盖 loader 字段
-Dmcp.mod.forge.version=...
```

---

## 三、HTTP 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/status` | 服务状态：`ok`、`version`、`loader`、`pid`、`port`、`uptime`、`control_mode`、`mouse_mode`、`player_can_look`、`no_pause` 等 |
| GET | `/api/screenshot` | 截取当前画面，返回原图与带 100px 网格坐标的 PNG（base64 data URL） |
| POST | `/api/cmd` | 统一调用入口，body 形如 `{"cmd":"方法名","params":{...}}` |
| GET | `/api/events` | SSE 事件流，推送模组侧调用事件；最多 4 个客户端，服务端保留约 300 秒 |
| GET | `/api/calls` | 最近 50 条调用记录（JSON 数组） |
| GET | `/` 或 `/debug` | 内置调试页面 |

调用示例：

```bash
curl http://127.0.0.1:9876/api/status

curl -X POST http://127.0.0.1:9876/api/cmd \
  -H 'Content-Type: application/json' \
  -d '{"cmd":"get_player_info"}'

curl -X POST http://127.0.0.1:9876/api/cmd \
  -H 'Content-Type: application/json' \
  -d '{"cmd":"execute_command","params":{"command":"time set day"}}'
```

---

## 四、可用方法（`/api/cmd` 的 `cmd`）

**状态 / 观测类**

`ping`、`screenshot`、`screenshot_to_file`（`params.path`）、`get_player_info`、
`get_world_info`、`debug_fields`、`get_screen_buttons`、`enumerate_widgets`。

**输入 / 操作类**

`click`（`x`、`y`、`button`）、`press_key`（`key`、`hold_seconds`）、`type_text`（`text`、`press_enter`）、
`paste_text`、`scroll`、`scroll_at`、`direct_scroll`、`select_list_item`、`mouse_drag` / `drag`、
`hotkey`（逗号分隔按键）、`right_click`、`use_item`、`place_block`、`switch_tab`、
`click_button_id`、`click_button_index`、`call_screen_method`。

**视角 / 世界 / 界面**

`set_view_angle`（`yaw`、`pitch`）、`look_delta`（`delta_yaw`、`delta_pitch`）、
`execute_command`（`command`）、`pause_game`、`open_chat`、`close_screen`、`release_mouse`、
`set_gamemode`（`mode`）。

**环境 / 限制开关（不需要控制模式）**

`get_connection_info`、`open_to_lan`（`port`、`allow_cheats`）、`set_no_pause`（`enabled`）、
`set_mouse_sharing`（`enabled`）。

**控制模式**

`enter_control_mode`（可选 `mode=shared|detached`）、`exit_control_mode`、`overlay_click`（`x`、`y`）。

---

## 五、控制模式：为什么需要它

模组默认处于**只读状态**，防止误操作。以下方法不需要控制模式即可调用：

```
ping / screenshot / get_player_info / get_world_info / debug_fields /
get_screen_buttons / enumerate_widgets / overlay_click /
get_connection_info / open_to_lan / set_no_pause / set_mouse_sharing /
enter_control_mode / exit_control_mode /
set_gamemode / release_mouse / pause_game / close_screen / open_chat
```

其余所有**输入类方法**（点击、按键、输入文字、拖动、视角、执行命令、截图存文件等）
必须先进入控制模式，否则返回：

```json
{"error":"not in control mode","hint":"Enter control mode via ESC > MCP Take Over"}
```

**进入方式：**

- 在游戏里按 `ESC` 打开暂停菜单，点击新增的 **「MCP Take Over」** 按钮；或
- 直接调用 `enter_control_mode`；或
- 在游戏里按 **`F8`** 快速切换（见下）。

**退出方式：**

- 按 **`F8`**；或
- 按 `ESC` 打开菜单，点击右上角的 **「恢复手动控制」** 图标按钮；或
- 调用 `exit_control_mode`；或
- 在控制模式下点 `overlay_click` 命中「系统菜单」区域，会退出控制模式并打开暂停菜单。

### 两种鼠标策略

`enter_control_mode` 现在有两种模式，用 `mode` 参数选择：

| 模式 | 行为 | 玩家能否用鼠标转视角 |
| --- | --- | --- |
| `shared`（**默认，推荐**） | 光标保持锁定，鼠标仍归玩家；AI 直接调用接口操作 | **能，完全自由** |
| `detached`（旧行为） | 每 tick 强制 `GLFW_CURSOR_NORMAL` 并清零鼠标增量 | 不能，只能靠 AI 的 `set_view_angle` |

```bash
# 默认就是 shared，不需要传参数
curl -X POST http://127.0.0.1:9876/api/cmd -H 'Content-Type: application/json' \
  -d '{"cmd":"enter_control_mode"}'

# 需要旧行为（AI 独占鼠标、绝对坐标拖动/滚动）时显式指定
curl -X POST http://127.0.0.1:9876/api/cmd -H 'Content-Type: application/json' \
  -d '{"cmd":"enter_control_mode","params":{"mode":"detached"}}'
```

`shared` 模式下界面点击不依赖真实光标：模组是直接把点击坐标派发给当前界面/控件的
（`McBridge.screenMouseClicked` / `pressWidget`），打开任何界面时原版本来就会自动释放光标，
所以 `click`、`click_button_id`、`overlay_click` 等照常可用；`set_view_angle` / `look_delta`
也是直接写玩家朝向，不经过鼠标。

需要 AI 用**绝对坐标**做原版级别的拖动/滚动时，可以先调用 `release_mouse` 显式释放光标，
或直接用 `detached` 模式；`set_mouse_sharing {"enabled":false}` 可以把本次游戏运行的默认策略切回 `detached`。

---

## 六、使用限制（重点）

### 1. 玩家按 ESC 暂停时，MCP 连接会断开吗？世界会冻结吗？

**连接不会断开；世界也（默认）不再冻结。**

- HTTP 服务跑在独立的 `MCP-HTTP` 线程上，**不受游戏 tick 和单机暂停影响**，暂停时照常接受请求。
- 默认开启了**「ESC 不暂停」**：玩家按 ESC 时，模组会用 Forge 的 `ScreenEvent.Opening`
  把原版 `PauseScreen` 换成一个行为完全相同的子类，只把 `isPauseScreen()` 覆写成 `false`。
  Minecraft 的暂停标志本来就是每帧由
  `hasSingleplayerServer() && screen.isPauseScreen() && !integratedServer.isPublished()`
  推导出来的，`isPauseScreen()` 一变，**客户端世界和集成本地服务器都会继续 tick**：
  实体照常移动、`execute_command` 立即结算，MCP 的操作不再"卡在暂停里"。
- 菜单本身、按钮、音效都还是原版的，只是不再冻结。想让世界暂停，暴露的开关是：

  ```bash
  # 恢复原版暂停行为
  curl -X POST http://127.0.0.1:9876/api/cmd -H 'Content-Type: application/json' \
    -d '{"cmd":"set_no_pause","params":{"enabled":false}}'
  ```

- 暂停时 `mc.screen != null`，HUD 上的悬浮按钮会隐藏，但接口本身照常可用；
  想操作暂停菜单本身，需要先进入控制模式（菜单里会多出一个 **「MCP Take Over」** 按钮）。
- 直接用 `/api/status` 或 `get_connection_info` 可以确认 `no_pause` / `game_paused` 当前状态。

> 注意：「ESC 不暂停」只在**单机、且世界还没开局域网**时介入。多人服务器和已开局域网的世界
> 本来就不会因为打开菜单而停摆，模组不会做任何替换。

### 2. 玩家移动时，MCP 连接会断开吗？

**不会断开。** 玩家移动只是游戏内状态变化，和 HTTP 服务没有关系，接口调用不会因此中断。

- 键盘移动（WASD）仍由游戏正常处理。
- 控制模式默认是 `shared`，**鼠标仍在玩家手里，转视角完全正常**；AI 需要动视角时用
  `set_view_angle` / `look_delta`，它直接写玩家朝向，不经过鼠标，双方互不干扰。
- 只有显式用 `detached` 模式（或多年前那种默认行为）时，鼠标才会被模组接管。
- 移动过程中调用会切换界面的方法（`open_chat`、`close_screen`、`pause_game`），画面状态会变化，属于预期行为。

### 3. 玩家在其他维度 / 切换维度时，MCP 连接会断开吗？

**不会断开。**

- 模组不缓存 `player` / `level`，每次请求都重新读取，因此切换维度后
  `get_player_info` 的 `dimension` 字段、`get_world_info` 会返回**新维度**的信息。
- 切换维度的瞬间（加载界面期间）`player` / `level` 可能短暂为 `null`，
  此时会返回 `{"name":null}` 或 `{"world_name":null}`，**稍后重试即可**，不是连接断开。
- 维度切换只影响世界数据内容，不影响 HTTP 服务存活。

### 4. 视角限制：现在默认已经解除

旧版本里"进入 MCP 控制模式后玩家无法转动视角"是**最明确的限制**，原因很清楚：

- 旧行为（现在叫 `detached`）为了保证 AI 能用绝对坐标点击，每 tick 强制
  `GLFW_CURSOR_NORMAL` + `mouseGrabbed = false`，并把累计鼠标位移清零；
  光标一旦释放，原版就不再累加鼠标增量，玩家自然转不了视角。

**现在的默认行为 `shared` 不再碰光标**：进入控制模式时模组什么都不做（没有界面时确保光标锁定，
有界面时原版自己会释放），玩家的鼠标、视角、灵敏度、`F5` 视角切换全部照常。AI 侧的能力没有缩水：

| AI 想要做的事 | `shared` 下是否可用 | 说明 |
| --- | --- | --- |
| `click` / `click_button_id` / `overlay_click` / `type_text` | ✅ | 直接派发到界面与控件，不依赖真实光标；打开界面时原版会释放光标 |
| `set_view_angle` / `look_delta` | ✅ | 直接写玩家朝向 |
| `right_click` / `use_item` / `place_block` / `hotkey` / `press_key` | ✅ | 走 `MouseHandler`/`KeyboardHandler` 注入 |
| 原版级别的绝对坐标拖动 / 精确定位滚动 | ⚠️ 需要先 `release_mouse` | 或改用 `enter_control_mode {"mode":"detached"}` |
| 操作 HUD 右上角的「恢复手动控制」悬浮按钮 | ❌（共享模式下光标锁着） | 改用 `F8`，或按 `ESC` 后点菜单上的同名按钮 |

想恢复旧行为：`enter_control_mode {"mode":"detached"}`，或者一次性把默认策略换掉
`set_mouse_sharing {"enabled":false}`。

### 5. 想要"真正独立的视角"：开局域网 + 第二个客户端

`shared` 模式解决的是"同一个客户端里，玩家和 AI 抢鼠标"的问题——它们始终共用同一个视角。
如果目标是**AI 拥有完全独立的相机**（各看各的、各走各的），唯一的办法是让 AI 作为
**第二名玩家**加入游戏：

1. 在原客户端调用一次开局域网（世界必须已经加载好）：

   ```bash
   curl -X POST http://127.0.0.1:9876/api/cmd -H 'Content-Type: application/json' \
     -d '{"cmd":"open_to_lan","params":{"port":"25565","allow_cheats":"true"}}'
   ```

   > 等价于游戏内 ESC → **对局域网开放**。返回里带 `port` 和 `hint`。

2. 用**第二个 Minecraft 客户端**（同样装本模组）连到 `127.0.0.1:25565`（跨机器就用主机局域网 IP）。
   第二个客户端要换一个 HTTP 端口，避免和第一个撞车：

   ```
   -Dmcp.port=9877
   ```

3. 之后 `9877` 上的那一份 MCP 就是"AI 玩家"：它有自己的 `player`、自己的相机，
   `set_view_angle` 只影响它自己；你的客户端视角完全自由。因为已经开局域网，
   第一个客户端按 ESC 也不再冻结世界，两边都不卡。

`get_connection_info` 会返回 `lan_published` / `lan_port`，方便确认是否已经开好。

### 6. 其它已知限制与注意事项

- **退出控制模式后有短暂抑制**：约 200ms 内输入会被忽略，overlay 点击有约 500ms 冷却，属正常保护。
- **「ESC 不暂停」的一个边角表现**：不暂停菜单是通过把 `isPauseScreen()` 改成 `false` 实现的，
  而原版 `LocalPlayer.handleNetherPortalClient()` 也会读这个值。所以**人正站在下界传送门里**
  打开 ESC 菜单时，菜单会被原版逻辑自动关掉（人已经进维度了，不影响游戏）。站着不动按 ESC 没有这个问题。
- **`F8` 是硬编码快捷键**：不进 Options → Controls 的重绑定列表；如果和其它模组冲突，
  请改用接口调用或菜单按钮。
- **`open_to_lan` 的端口不自动回退**：默认 `25565` 被占用时返回
  `{"ok":false,"error":"publishServer failed..."}`，换一个 `port` 再调即可。
  `open_to_lan` 只在单机本地世界有效，在多人服务器上会返回错误。
- **开局域网会短暂暴露服务**：世界会在局域网内可见（原版也会打一条聊天提示）。
  不想要了就正常退出世界/关游戏。
- **SSE 客户上限**：`/api/events` 最多 4 个并发客户端，超出返回 `503`；单条 SSE 连接服务端保留约 300 秒后关闭，客户端需自行重连。
- **端口占用不自动回退**：默认 `9876` 被占用时启动会失败，请用 `-Dmcp.port=` 指定端口。
- **安全提示**：服务监听 `0.0.0.0`，同一局域网内任何人都能访问这些接口。请勿在不可信网络下开启，
  需要时用防火墙限制访问，或只在本机使用。
- **命令权限**：`execute_command` 是模拟玩家发送命令，权限取决于是否单人作弊 / 服务器 OP，模组不会提权。
- **平台支持**：当前构建只针对 **1.20.1 + Forge**。代码大量使用反射并保留多版本兼容路径，
  但未经其它版本实机验证，不保证可用。
- **版本号显示**：`/api/status` 的 `version` 默认是 `unknown`，除非通过 `-Dmcp.mod.version=` 注入。

---

## 七、兼容性说明（给排查问题的人）

关键成员（`Minecraft.getInstance()`、`player`、`level`、连接与命令发送等）
已改为**编译期直接引用**，构建时由 reobf 自动改写为 SRG 名，因此在
Forge 生产环境（SRG 映射）和开发环境（official 映射）下都能正确解析；
原先的反射路径保留为兜底。这修复了「服务能起、`ping` 正常，但玩家信息为 `null`、
`execute_command` 返回 `no player`」一类问题。

排查日志：部分反射调试信息会写到用户目录下的 `mcp_debug.log`。

---

## 八、目录速查

```
src/main/java/xyz/langyo/minecraft/mcp/mod/      模组入口、Forge 事件、暂停菜单按钮、HUD 悬浮层
src/main/java/xyz/langyo/minecraft/mcp/common/    HTTP 服务、消息分发、反射桥接、输入注入、截图
src/main/resources/mcp-debug/                     内置调试页
src/main/resources/assets/mcpmod/                 图标与多语言文本
```

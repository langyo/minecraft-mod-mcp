# MCP 方案 B：Win32 API 接管架构

## 问题诊断

当前的「内部反射对抗」方案（方案 A）有根本性缺陷：

| 问题 | 原因 |
|------|------|
| **鼠标卡死 15 秒** | MC 的 GLFW 代码不断重置 `mouseGrabbed` 和 `cursor_mode`，我们用反射每 10 tick 重新设置，形成拉锯战 |
| **截图全部相同** | Robot 截图抓桌面合成缓存，GL readPixels 读后缓冲区未刷新，MC native 截图静默失败 |
| **点击不导航** | `onPress.accept()` 触发成功但渲染帧不更新，截图与实际状态脱节 |

**核心矛盾：在游戏引擎内部和 MC 原生代码打架，注定是消耗战。**

## 方案 B 总览

**不在游戏引擎内部打架，而是从 Windows API 层建立隔离墙。MCP mod 通过 JNA 直接调用 Win32 API 实现：**

```
                         ┌─────────────────────────────────────┐
                         │        Windows OS 内核层             │
                         │                                     │
  用户真实鼠标 ──────────┤→  WH_MOUSE_LL Hook (JNA 在 mod 内)   │
                         │     │   控制模式 ON?                 │
                         │     ├─→ YES: 阻断 (return 1)        │
                         │     │                               │
                         │     └─→ NO: 放行给系统              │
                         │                                     │
                         │  ┌──────────────────────────┐       │
                         │  │  MC 窗口 (GLFW/OpenGL)    │       │
                         │  │                          │       │
                         │  │  ┌────────────────────┐  │       │
                         │  │  │  遮罩覆盖层         │  │       │
                         │  │  │  (半透明灰色)       │  │       │
                         │  │  │  "MCP is operating  │  │       │
                         │  │  │   at localhost:9876"│  │       │
                         │  │  └────────────────────┘  │       │
                         │  │                          │       │
                         │  │  接收合成点击:            │       │
                         │  │  PostMessage(WM_LBUTTONDOWN)     │
                         │  │  PostMessage(WM_KEYDOWN)         │
                         │  └──────────────────────────┘       │
                         └─────────────────────────────────────┘
```

### 四大组件

| 组件 | 所在层 | 实现方式 | 职责 |
|------|--------|---------|------|
| **1. 鼠标钩子** | Win32 API | JNA `SetWindowsHookExW(WH_MOUSE_LL)` | 阻断/放行鼠标事件 |
| **2. 遮罩覆盖层** | Win32 API | JNA `CreateWindowExW(WS_EX_LAYERED)` + GDI | 灰色背景 + 运行状态文字 |
| **3. 合成输入注入** | Win32 API | `PostMessageW(WM_LBUTTONDOWN/UP)` 等 | 向 MC 窗口注入合成点击/按键 |
| **4. 截图** | GL/MC | MC 原生 Screenshot.takeScreenshot 或 glReadPixels | 截取 MC 渲染帧 |

---

## Phase 1: WH_MOUSE_LL 鼠标钩子（在模组内）

### 目标
完全替代反射式 `enterMcpControlMode`，不再和 MC 内部代码拉锯。

### 技术方案

```
McpWin32.installMouseHook()
  → SetWindowsHookExW(WH_MOUSE_LL, callback, NULL, 0)
  → 在专用线程运行 GetMessageW 消息泵
  → 回调内判断：控制模式 ON + 鼠标在 MC 窗口矩形内 → return 1 (阻断)

McpWin32.setControlMode(bool)  → 切换阻断/放行
McpWin32.uninstallMouseHook()  → UnhookWindowsHookEx
```

### 钩子回调逻辑

```
mouseHookProc(nCode, wParam, lParam):
  if nCode < 0: return CallNextHookEx(...)
  if not controlMode: return CallNextHookEx(...)

  ms = MSLLHOOKSTRUCT(lParam)
  if ms.x in mc_bounds and ms.y in mc_bounds:
    return 1   // 阻断：MC 永远不会收到此事件

  return CallNextHookEx(...)
```

### 与方案 A 的对比

| 特性 | 方案 A (反射抑制) | 方案 B (Win32 钩子) |
|------|------------------|---------------------|
| 鼠标冻结 | 频繁（15s+） | 永不 |
| 实现复杂度 | 反射 + GLFW 调用 | JNA SetWindowsHookExW |
| MC 是否感知 | 是（GLFW 不断重置） | 否（事件根本不送达） |
| 可维护性 | 差（MC 版本敏感） | 好（Win32 API 稳定） |
| 线程安全 | 问题多（RenderThread） | 专用线程 + 消息泵 |

---

## Phase 2: 遮罩覆盖层

### 目标
自动化操作时，MC 窗口上显示半透明灰色遮罩 + "MCP is operating at localhost:9876" 文字。

### 技术方案

```
McpWin32.showOverlay(port):
  → CreateWindowExW(
      WS_EX_LAYERED | WS_EX_TRANSPARENT | WS_EX_TOPMOST,
      "STATIC",
      NULL,
      WS_POPUP | WS_VISIBLE | SS_CENTER | SS_CENTERIMAGE,
      0, 0, mc_width, mc_height,
      mc_hwnd,   // 作为 MC 窗口的子窗口
      ...)
  → SetLayeredWindowAttributes(hwnd, 0x404040, 160, LWA_ALPHA)
  → SetWindowTextW(hwnd, "MCP is operating at localhost:9876")
  → 创建字体: CreateFontW(..., "Segoe UI", 24, ...)
  → 在 WM_CTLCOLORSTATIC 中设置字体和颜色

McpWin32.hideOverlay(): → DestroyWindow(overlay_hwnd)
McpWin32.updateOverlayText(text): → SetWindowTextW(overlay_hwnd, text)
```

### 视觉效果

```
┌─────────────────────────────────────────────────────┐
│                  MC 游戏窗口                         │
│                                                     │
│              ░░░░░░░░░░░░░░░░░░░░░░                 │
│              ░                    ░                 │
│              ░  MCP is operating  ░                 │
│              ░  at localhost:9876 ░                 │
│              ░                    ░                 │
│              ░░░░░░░░░░░░░░░░░░░░░░                 │
│                                                     │
│  (半透明灰色遮罩 + 白色文字居中)                     │
└─────────────────────────────────────────────────────┘
```

### 窗口样式选择

使用 `WS_EX_LAYERED` 而非 OpenGL 覆盖的原因：
- 不需要侵入 MC 渲染管线（不需要 mixin 或渲染 hook）
- 创建和销毁快速（Win32 原生窗口）
- 自动覆盖整个 MC 窗口（包括 OpenGL 渲染区域）
- `WS_EX_TRANSPARENT` 确保遮罩本身不拦截鼠标（鼠标由钩子处理）

---

## Phase 3: 合成输入注入

### 目标
替代反射式点击（`guiClick`/`onPress.accept`），改用 Win32 消息注入。

### 技术方案

```
// 坐标点击
McpWin32.injectClick(screenX, screenY):
  client = ScreenToClient(mc_hwnd, screenX, screenY)
  lParam = MAKELPARAM(client.x, client.y)
  PostMessageW(mc_hwnd, WM_LBUTTONDOWN, MK_LBUTTON, lParam)
  Sleep(50)
  PostMessageW(mc_hwnd, WM_LBUTTONUP, 0, lParam)

// 按键
McpWin32.injectKey(vk_code):
  PostMessageW(mc_hwnd, WM_KEYDOWN, vk_code, ...)
  Sleep(50)
  PostMessageW(mc_hwnd, WM_KEYUP, vk_code, ...)

// 滚轮
McpWin32.injectScroll(clicks):
  PostMessageW(mc_hwnd, WM_MOUSEWHEEL, clicks * WHEEL_DELTA << 16, ...)

// 鼠标滚轮（最新消息之前）
McpWin32.injectMousePos(clientX, clientY):
  lParam = MAKELPARAM(clientX, clientY)
  PostMessageW(mc_hwnd, WM_MOUSEMOVE, 0, lParam)
```

### 是否需要保留反射点击？

两种方式可并存：

| 操作 | 方式 | 适用场景 |
|------|------|---------|
| 菜单按钮点击 | 反射 `onPress.accept()` | 直接调用 MC 按钮回调，最可靠 |
| 游戏内右键（放置方块） | 反射或合成 | 两者皆可 |
| 文本输入（无 IME 问题） | 合成 WM_CHAR | 比 paste 更自然 |
| 快捷键（如 E 打开背包） | 合成 WM_KEYDOWN | 等效于真实按键 |
| 拖拽（如物品栏操作） | 合成 MouseDown + MouseMove + MouseUp | 需要序列化 |

**结论：保留反射点击（已验证工作），新增合成输入作为补充。**

---

## Phase 4: 截图修复

### 目标
解决截图一直返回相同帧的问题。

### 问题诊断（已确认）

从 debug log 已知：
- MC native `Screenshot.takeScreenshot()` 返回 null（consumer 内部抛异常）
- `glReadPixels` + `glFinish` + `glReadBuffer(GL_FRONT)` 全部返回相同 798021 字节

### 修复方案

#### 4.1 优先级：MC Native → GL → Win32 BitBlt

```java
takeScreenshot(mc, w, h):
  try native = takeMcNativeScreenshot(mc)    // MC 自己的方法
    if ok: return native
  try gl = takeGlScreenshot(mc, w, h)        // OpenGL 直读
    if ok: return gl
  try win = takeWin32BitBlt(mc, w, h)        // Win32 BitBlt 捕获
    if ok: return win
```

#### 4.2 修复 Native 路径（优先）

需要定位 `Screenshot.takeScreenshot` 为什么失败：
1. 加详细日志到 consumer 的异常处理
2. 检查 `NativeImage.writeToFile` 是否抛异常
3. 可能需要替换 `writeToFile` → `asByteArray` 或其他方法

#### 4.3 新增 Win32 BitBlt 捕获

作为 GL 路径的替代：
```java
takeWin32BitBlt(mc):
  hwnd = getWindowHandle(mc)
  hdcScreen = GetDC(NULL)
  hdcMem = CreateCompatibleDC(hdcScreen)
  hBitmap = CreateCompatibleBitmap(hdcScreen, w, h)
  SelectObject(hdcMem, hBitmap)
  PrintWindow(hwnd, hdcMem, PW_RENDERFULLCONTENT)  // or PW_CLIENTONLY
  // 然后从 hBitmap 读取像素到 byte[]
```

---

## 实现计划

### Step 1: McpWin32.java 扩展（JNA Win32 API）

```
新增方法：
  installMouseHook()          // SetWindowsHookExW(WH_MOUSE_LL)
  uninstallMouseHook()        // UnhookWindowsHookEx
  setHookControlMode(bool)    // 切换阻断/放行
  showOverlay(port)           // WS_EX_LAYERED 遮罩窗
  hideOverlay()               // 销毁遮罩窗
  updateOverlayText(text)     // 更新遮罩文字
  injectClick(x, y)           // PostMessage WM_LBUTTONDOWN/UP
  injectKey(vk)               // PostMessage WM_KEYDOWN/UP
  injectScroll(clicks)        // PostMessage WM_MOUSEWHEEL
  getWindowBounds()           // GetWindowRect
```

### Step 2: ReflectionHelper.java 简化

```
移除（不再需要）:
  - forceReleaseMouse()
  - applyMouseSuppress()
  - scheduleSuppressLoop()
  - enterMcpControlMode() (反射版)
  - exitMcpControlMode() (反射版)
  - resolveCache()
  - startPosSaver()
  - cachedMouseHandler / cachedMouseGrabbedField 等

简化为:
  - enterMcpControlMode() → 调用 McpWin32.setHookControlMode(true) + showOverlay()
  - exitMcpControlMode()  → 调用 McpWin32.setHookControlMode(false) + hideOverlay()
```

### Step 3: 截图修复

```
1. 先修复 MC native 路径（定位 consumer 异常根因）
2. 新增 Win32 BitBlt 作为最终 fallback
3. 移除 doScreenshot() 中的 forceReleaseMouse 调用（不再需要）
```

### Step 4: WebSocket 协议扩展

```
新增命令:
  mouse_hook_status    → 返回钩子状态
  overlay_show         → 显示遮罩
  overlay_hide         → 隐藏遮罩
  overlay_text text    → 更新遮罩文字
  inject_click x y     → 合成点击（Win32 方式）
  inject_key key       → 合成按键

保留命令:
  click        → 保持使用反射 guiClick（菜单按钮用）
  look_delta   → 保持使用反射 setPlayerRotation
  screenshot   → 修复后的截图
```

### Step 5: 测试验证

```
1. 安装钩子 → 控制模式 ON
2. 显示遮罩 → 验证视觉
3. MC 窗口内移动真实鼠标 → 鼠标自由不卡顿（终极目标）
4. 注入合成点击 → MC 正确响应
5. 截图 → 帧确实变化
6. 关闭遮罩 + 控制模式 OFF → MC 恢复正常
```

---

## 风险与缓解

| 风险 | 缓解措施 |
|------|---------|
| WH_MOUSE_LL 超时（Win10+ 限制） | 回调内不做重操作，只做边界检查 |
| JNA 线程安全 | 钩子回调在专用线程，MC 操作通过 PostMessage 序列化 |
| WS_EX_LAYERED 窗口性能 | 半透明窗口渲染成本低，GDI 绘制文字无性能问题 |
| 遮罩窗口覆盖 OpenGL 区域 | WS_EX_LAYERED 支持 GPU 合成，不影响 MC 渲染 |
| PostMessage 注入不生效 | GLFW 可能用 Raw Input；fallback 到 SendInput API |

---

## 预期效果

| 指标 | 方案 A (当前) | 方案 B (目标) |
|------|-------------|-------------|
| 鼠标卡顿 | 15s+ / 频繁 | 0 (永不) |
| 截图正确率 | 0% (全缓存) | 100% |
| 控制模式对 MC 性能影响 | 中 (每 10 tick 反射) | 零 (Win32 API 层) |
| 用户感知 | 鼠标冻结、UI 卡死 | 鼠标完全自由、遮罩清晰提示 |
| MC 版本兼容性 | 差 (反射字段名变化) | 好 (Win32 API 永恒) |

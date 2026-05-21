# Redstone Ready 世界创建 - 进展与计划

## 目标
通过 MCP（HTTP API）控制 Minecraft 1.21.7 Forge，在 UI 中创建 **Redstone Ready 超平坦创造模式世界**。

## 当前进展

### 已完成
- [x] **MCP HTTP 服务端**：完整实现了截图、点击、键盘、滚轮、拖拽、Tab切换等命令
- [x] **Python 客户端** (`scripts/mc_http.py`)：封装了所有 API 调用
- [x] **MC 启动脚本** (`scripts/launch_mc.py`)：支持版本 JSON 合并、classpath 构建、native 提取
- [x] **UI 导航到预设选择界面**已打通：
  - 标题 → 单人游戏 → 创建世界 → 更多 tab → 世界类型改为超平坦 → 自定义 → 预设
- [x] **switch_tab 命令**：通过反射调用 TabNavigationBar.selectTab(index, true)
- [x] **enumerate_widgets 命令**：列出当前屏幕所有控件（类型、坐标、大小、是否可点击）
- [x] **click_button 支持 CycleButton**：手动循环点击，返回 newIdx
- [x] **scroll_at 命令**：在指定坐标设鼠标位置 + 发 mouseMove + 发滚轮事件（3 步）

### 当前阻塞：预设列表滚动不生效
- **问题**：PresetFlatWorldScreen 的 PresetsList 控件不响应任何滚动操作
- **已尝试**：
  - `scroll` 命令（AWT Robot mouseWheel）→ 不生效（窗口最小化，OS 事件发不到 MC）
  - `inject_click` + mouse_wheel_down → 不生效
  - 键盘 Down/PageDown/End → 不滚动列表
  - 点击滚动条轨道 → 不生效
  - Win32 `mouse_event(MOUSEEVENTF_WHEEL)` → 滚到了别的窗口上
  - `scroll_at`（内部 setCursorPos + sendMouseMoveInternal + sendScroll）→ 仍不生效
- **根因分析**：
  - MC 窗口在 `Start-Process -WindowStyle Hidden` 启动时处于**最小化状态**
  - GLFW/LWJGL 的 scroll callback 在窗口最小化时可能不处理事件
  - MC 的 PresetsList 控件可能需要**窗口获得焦点**才处理 scroll 事件
  - 或者 `sendMouseMoveInternal` 找到的 callback 方法不对（需要确认 lambda$setup$0/1 是否为 cursor pos callback）

### 解决滚动问题的可能方案
1. **窗口前台化**：用 Win32 `ShowWindow(hwnd, SW_RESTORE)` + `SetForegroundWindow` 在 scroll_at 前先恢复窗口
2. **直接调用 Screen.mouseScrolled**：通过反射找到当前 Screen 的 mouseScrolled(double, double, double) 方法直接调用，绕过 GLFW 事件管线
3. **直接设置 PresetsList 选中索引**：通过反射找到 PresetsList 内部的 selected 索引字段，直接设值
4. **改用可见键盘导航**：如果 Down 键能在可见 5 项间切换，找到选中项后用某种方式触发翻页

## 代码修改清单

### 新增功能（已提交或待提交）
| 文件 | 修改内容 |
|------|---------|
| `McpProtocol.java` | 加 `scrollAt(int,int,int)` 和 `mouseDrag` 接口方法 |
| `McpHttpServer.java` | dispatchCmd 自动包装 String 响应为 JSON |
| `McpMessageHandler.java` | 加 `handleScrollAt`、`handleMouseDrag`、JSON 格式修复、methodName fallback |
| `ReflectedInputHandler.java` | 加 `scrollAt()`、`mouseDrag()` 实现 |
| `ReflectionHelper.java` | 加 `sendMouseMoveInternal()`、`sendMouseDrag()`、`switchTab()`、改进 `callScreenMethod` |
| `mc_http.py` | 加 `drag()`、`_post()` JSON fallback、screenshot 兼容 raw bytes |

### 待添加到版本控制的文件
- `scripts/mc_http.py` — Python MCP 客户端
- `scripts/launch_mc.py` — MC 启动脚本

## UI 控件映射

### CreateWorldScreen — 更多 tab (index=1)
| 索引 | 类型 | 位置 | 功能 |
|------|------|------|------|
| 0 | TabNavigationBar | (0,0) | Tab 栏 |
| 1 | Button | (59,214) | 创建新的世界 |
| 2 | Button | (217,214) | 取消 |
| **3** | **CycleButton** | **(58,35)** | **世界类型**（默认→超平坦→放大化…） |
| **4** | **Button** | **(218,35)** | **自定义**（世界类型为超平坦时可用） |
| 8 | CycleButton | (324,108) | 游戏模式 |
| 10 | CycleButton | (324,132) | 难度 |

### CreateFlatWorldScreen
| 索引 | 类型 | 位置 | 功能 |
|------|------|------|------|
| 2 | Button | (59,186) | 移除层面 |
| **3** | **Button** | **(217,186)** | **预设** |
| 4 | Button | (59,210) | 完成 |
| 5 | Button | (217,210) | 取消 |

### PresetFlatWorldScreen
| 索引 | 类型 | 位置 | 功能 |
|------|------|------|------|
| 0 | PresetsList | (0,80) 427x123 | 预设列表（可滚动，有滚动条） |
| 1 | Button | (58,212) | 使用预设 |
| 2 | Button | (218,212) | 取消 |

## 关键技术约束
- MC 窗口通过 `Start-Process -WindowStyle Hidden` 启动，处于最小化状态
- OS 层鼠标/键盘事件发不到最小化窗口
- 所有交互必须通过 MC 内部的 GLFW/LWJGL 反射管线
- 用户要求 Redstone Ready 必须通过**预设选择器 UI** 选中，不能手动输入预设字符串
- 单机仅允许一个 MC 实例

## 下一步
1. **解决滚动问题**（最高优先级）：
   - 方案 A：scroll_at 前先调用 Win32 ShowWindow 恢复窗口
   - 方案 B：反射直接调用 Screen.mouseScrolled(x, y, delta)
   - 方案 C：反射直接设置 PresetsList 的选中索引
2. 选中 Redstone Ready → 点击"使用预设"→ 完成 → 创建世界
3. 验证世界是创造模式 + Redstone Ready 超平坦

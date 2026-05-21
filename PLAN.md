# Redstone Ready 世界创建 - 完成报告

## 目标
通过 MCP（HTTP API）控制 Minecraft 1.21.7 Forge，在 UI 中创建 **Redstone Ready 超平坦创造模式世界**。

## 最终结果：成功 ✓

已成功通过 MCP UI 控制创建了 Redstone Ready 超平坦创造模式世界。
- **Biome**: minecraft:desert（沙漠，Redstone Ready 预设使用的生物群系）
- **Game Mode**: Creative（创造模式，已通过创造模式物品栏确认）
- **World Type**: Superflat with Redstone Ready preset
- **Spawn Position**: (-5.5, 56.0, 7.5) in overworld

## 完成的工作

### 核心突破
1. **direct_scroll 命令**：反射直接调用 `Screen.mouseScrolled(x, y, horizontal, vertical)` 绕过 GLFW 事件管线，解决了窗口最小化时滚动不生效的问题
2. **select_list_item 命令**：反射获取 PresetsList 的 Entry 列表，调用 `Entry.select()` 直接选中目标预设项，绕过了点击坐标偏移问题
3. **完整的 UI 自动化流程**：从标题屏幕到世界创建的 10+ 步操作全部自动化

### 操作流程（已验证）
```
TitleScreen → SelectWorldScreen → CreateWorldScreen
  → World tab: 设创造模式(w5×2) + 允许命令(w7)
  → More tab: 设超平坦(w3) + 自定义(w4) → CreateFlatWorldScreen
  → 预设(w3) → PresetFlatWorldScreen
  → select_list_item(index=7) → 使用预设(w1) → 完成(w4)
  → 创建新的世界 → 世界加载完成
```

### CreateWorldScreen — World tab (index=0)
| 索引 | 类型 | 功能 |
|------|------|------|
| 5 | CycleButton | 游戏模式（生存→极限→创造，需点2次） |
| 6 | CycleButton | 难度 |
| 7 | CycleButton | 允许命令（开/关） |

### CreateWorldScreen — More tab (index=1)
| 索引 | 类型 | 功能 |
|------|------|------|
| 3 | CycleButton | 世界类型（默认→超平坦→…） |
| 4 | Button | 自定义（超平坦时可用） |
| 8 | CycleButton | 游戏模式（仅有生存/极限2选项） |
| 10 | CycleButton | 难度 |

### CreateFlatWorldScreen
| 索引 | 类型 | 功能 |
|------|------|------|
| 3 | Button | 预设 |
| 4 | Button | 完成 |

### PresetFlatWorldScreen
| 索引 | 类型 | 功能 |
|------|------|------|
| 0 | PresetsList | 预设列表（9个预设，需滚动） |
| 1 | Button | 使用预设 |
| 2 | Button | 取消 |

### 预设列表（按索引）
| 索引 | 名称 |
|------|------|
| 0 | Classic Flat（经典平坦） |
| 1 | Tunneler's Dream（挖掘工的梦想） |
| 2 | Water World（水世界） |
| 3 | Overworld（主世界） |
| 4 | Snowy Kingdom（雪之王国） |
| 5 | Bottomless Pit（无底深渊） |
| 6 | Desert（沙漠） |
| **7** | **Redstone Ready（红石俱备）** |
| 8 | The Void（虚空） |

## 代码修改清单

| 文件 | 修改内容 |
|------|---------|
| `ReflectionHelper.java` | 加 `directScroll()`、`selectListItem()`、修复 `sendMouseDrag` |
| `McpMessageHandler.java` | 加 `handleDirectScroll`、`handleSelectListItem` dispatch 分支 |
| `McpHttpServer.java` | dispatchCmd 自动包装 String 响应为 JSON |
| `McpProtocol.java` | 加 `scrollAt(int,int,int)` 和 `mouseDrag` 接口方法 |
| `ReflectedInputHandler.java` | 加 `scrollAt()` 实现 |
| `mc_http.py` | Python MCP 客户端 |
| `launch_mc.py` | MC 启动脚本 |

## 关键技术要点
- **GL 事件绕过**：窗口最小化时 GLFW scroll callback 不处理事件，`direct_scroll` 通过反射直接调用 Screen.mouseScrolled 绕过
- **列表选中**：PresetsList 的点击坐标与 GUI 坐标有偏移，`select_list_item` 通过反射获取 Entry 并调用 select() 方法实现精确选中
- **游戏模式**：More tab 的游戏模式 CycleButton 仅有 2 选项（生存/极限），创造模式必须在 World tab 设置
- **允许命令**：World tab 的 w7 CycleButton 切换允许命令开/关
- **截图限制**：窗口最小化时 GL glReadPixels 读到白帧，需 Win32 ShowWindow 恢复后才能正确截图

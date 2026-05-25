# MCP Workflow 自動化系統

## 概述

MCP Workflow 是基於 YAML 的 Minecraft 自動化操作框架，替代之前命令列參數式的 `run.py`。

**核心設計理念：預選點擊（Preview Click）** — 在真正執行點擊前，先在截圖上繪製紅點標記，經視覺確認後再執行，避免盲目猜測座標。

## 快速開始

```bash
# 乾跑（不啟動 MC，只驗證 YAML 解析和步驟邏輯）
python scripts/run_yaml.py workflows/smoke_test.yaml --dry-run --skip-setup

# 完整運行（自動啟動 MC + 執行全部步驟）
python scripts/run_yaml.py workflows/smoke_test.yaml

# MC 已在運行時跳過啟動階段
python scripts/run_yaml.py workflows/smoke_test.yaml --skip-setup

# 只執行第 5 步
python scripts/run_yaml.py workflows/smoke_test.yaml --step 5

# 不使用容器嵌入
python scripts/run_yaml.py workflows/smoke_test.yaml --no-container
```

## 預選點擊流程

這是本系統的核心創新。傳統方式是直接發送座標點擊，如果座標猜錯了就白點了。預選流程分兩步：

```
Step 1: preview_click    → 排隊一個紅點標記 (x,y)，不執行任何操作
Step 2: screenshot       → 截圖 + 在圖片上繪製紅點/十字線/座標文字
         ↓ AI 審核帶標註的截圖 ↓
Step 3: click            → 確認座標正確後，才執行真實點擊
```

### 紅點標註效果

- **紅色圓圈** (可配置半徑，預設 10px)
- **十字準線** (水平 + 垂直線)
- **座標標籤** `(426,236) 單人遊戲` 帶黑色半透明背景

### YAML 寫法範例

```yaml
# 第一步：預選座標
- action: preview_click
  x: 426
  y: 236
  label: "單人遊戲"
  radius: 10
  color: "#FF0000"
  comment: "預選: 在單人遊戲按鈕位置畫紅點"

# 第二步：截圖（會自動帶上紅點）
- action: screenshot
  name: "preview_singleplayer"
  comment: "AI 審核: 紅點是否在按鈕上？"

# 第三步：確認後點擊
- action: click
  x: 426
  y: 236
  comment: "確認座標正確，執行點擊"
```

## 支援的動作

| 動作 | 參數 | 說明 |
|------|------|------|
| `wait` | `seconds` | 等待指定秒數 |
| `screenshot` | `name` | 截圖並儲存（自動繪製排隊中的預選標記） |
| **`preview_click`** | `x, y, label, radius, color` | **排隊紅點標記，不執行點擊** |
| `click` | `x, y` | 執行座標點擊 |
| `click_btn_idx` | `index` | 按控制項索引點擊按鈕 |
| `click_btn_id` | `button_id` | 按 ID 點擊按鈕 |
| `ctrl_on` | - | 進入 MCP 控制模式（滑鼠解耦） |
| `ctrl_off` | - | 離開控制模式 |
| `key` | `key` | 按鍵（如 `Escape`, `E`） |
| `paste` | `text, press_enter` | 貼上文字（繞過 IME 問題） |
| `scroll` | `clicks` | 滾輪滾動 |
| `look_delta` | `dyaw, dpitch` | 遊戲內視角旋轉（不移動滑鼠） |
| `set_view_angle` | `yaw, pitch` | 設定絕對視角角度 |
| `right_click` | - | 右鍵點擊 |
| `enumerate_widgets` | - | 列出目前介面所有控制項 |
| `get_screen_buttons` | - | 取得目前螢幕按鈕清單 |
| `cmd` | `command` | 執行 MC 指令（如 `/gamemode creative`） |
| `vision_check` | `prompt, expect, store_as` | AI 視覺分析截圖 |

## YAML 工作流程格式

```yaml
name: "工作流程名稱"
description: |
  多行描述，說明這個工作流程做什麼、驗證什麼。

setup:
  version: "1.21.7-forge-57.0.2"   # MC 版本
  container: false                   # 是否使用容器視窗
  wait_after_connect: 15             # 連線後等待 MC 載入的秒數

steps:
  # 每個 step 是一個動作，按順序執行
  - action: wait
    seconds: 15
    comment: "等待 MC 載入完成"

  - action: ctrl_on
    comment: "進入控制模式"

  - action: screenshot
    name: "baseline"
    comment: "基線截圖"
```

## setup 配置項

| 欄位 | 類型 | 預設值 | 說明 |
|------|------|--------|------|
| `version` | string | `"1.21.7-forge-57.0.2"` | MC 版本識別 |
| `container` | bool | `true` | 是否使用 Win32 容器嵌入 |
| `wait_after_connect` | int | `15` | Mod 連線後等待秒數（等 Mojang 啟動畫面消失） |

## CLI 參數

| 參數 | 說明 |
|------|------|
| `<workflow.yaml>` | YAML 工作流程檔案路徑（必填） |
| `--dry-run` | 乾跑模式，不發送實際指令到 MC |
| `--skip-setup` | 跳過 MC 啟動（MC 已在執行時使用） |
| `--step N` | 只執行第 N 步（1-indexed） |
| `--interactive` | 每步暫停等待確認 |
| `--no-container` | 不使用容器視窗 |

## 檔案結構

```
minecraft-mcp/
├── workflows/                        # YAML 工作流程定義
│   └── smoke_test.yaml               # 冒煙測試：主選單→遊戲內→視角旋轉
├── scripts/
│   ├── run_yaml.py                   # YAML 執行器入口
│   ├── workflow_engine.py            # 核心引擎（動作執行、狀態管理、截圖標註）
│   └── run.py                       # 舊版命令列 runner（仍可使用）
├── packages/common/                  # Mod 公共程式碼（反射輸入、截圖、控制模式）
│   └── src/main/java/.../
│       ├── ReflectionHelper.java     # guiClick, preview_click, lookDelta 等
│       ├── McpMessageHandler.java    # WebSocket 訊息分發
│       └── ReflectedInputHandler.java# 輸入處理器
└── docs/
    └── workflow.md                   # 本文件
```

## 與舊版 run.py 的區別

| 特性 | run.py (舊) | run_yaml.py (新) |
|------|-------------|-----------------|
| 格式 | 命令列參數 | YAML 檔案 |
| 可重複使用性 | 低（每次手敲參數） | 高（YAML 可版本控制、分享） |
| 預選點擊 | 不支援 | ✅ preview_click + 截圖標註 |
| 結構化註解 | 無 | 每個 step 有 comment 欄位 |
| 錯誤復原 | 無 | 每步有 success/error 狀態記錄 |
| 條件分支 | 不支援 | vision_check / if_screen |

## 編寫自訂工作流程

1. 複製 `workflows/smoke_test.yaml` 作為範本
2. 修改 `name`、`description`、`steps`
3. 先用 `--dry-run --skip-setup` 驗證語法
4. 用真實 MC 測試

### 典型模式：找到按鈕並點擊

```yaml
# 1. 先截圖看目前介面
- action: screenshot
  name: "current_screen"

# 2. 用 AI 視覺找按鈕座標（或手動從 enum 結果中讀取）
- action: vision_check
  prompt: "找到「建立新的世界」按鈕的中心像素座標"
  store_as: "create_world_pos"

# 3. 預選該座標
- action: preview_click
  x: "${variables.create_world_pos.x}"   # TODO: 變數引用待實現
  y: "${variables.create_world_pos.y}"
  label: "建立新的世界"

# 4. 截圖審核
- action: screenshot
  name: "preview_create_world"

# 5. 確認點擊
- action: click
  x: 350
  y: 420
```

## 注意事項

1. **不要在 Mojang 啟動畫面期間進入控制模式** — 會導致遊戲卡在 logo 上。`setup.wait_after_connect` 應 ≥ 15 秒。
2. **截圖優先使用 Robot (AWT)** 而非 MC 原生截圖 — MC 原生截圖可能返回快取幀。
3. **每次 click 後要給足夠 wait** — GUI 切換需要時間，通常 3-8 秒。
4. **preview_click 的標記在下一個 screenshot 時繪製** — 這是佇列機制，不是立即生效。

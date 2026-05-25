# MCP Workflow 自動化システム

## 概要

MCP Workflow は YAML ベースの Minecraft 自動操作フレームワークであり、従来のコマンドライン引数方式の `run.py` を置き換えます。

**中核設計理念：プレビュークリック（Preview Click）** — 実際にクリックを実行する前に、まずスクリーンショット上に赤い点のマーカーを描画し、視覚的に位置を確認してから実行します。これにより座標の推測ミスを防ぎます。

## クイックスタート

```bash
# ドライラン（MC を起動せず、YAML の解析とステップロジックのみ検証）
python scripts/run_yaml.py workflows/smoke_test.yaml --dry-run --skip-setup

# フル実行（MC を自動起動 + 全ステップ実行）
python scripts/run_yaml.py workflows/smoke_test.yaml

# MC が既に実行中の場合、起動フェーズをスキップ
python scripts/run_yaml.py workflows/smoke_test.yaml --skip-setup

# ステップ 5 のみ実行
python scripts/run_yaml.py workflows/smoke_test.yaml --step 5

# コンテナ埋め込みを無効化
python scripts/run_yaml.py workflows/smoke_test.yaml --no-container
```

## プレビュークリックの流れ

これは本システムの中核的な革新です。従来の方式では座標を直接送信してクリックしていましたが、座標が間違っていると無駄打ちになります。プレビューフローは 2 段階のプロセスです：

```
Step 1: preview_click    → 座標 (x,y) に赤い点マーカーをキューイング、操作は実行しない
Step 2: screenshot       → スクリーンショット + 画像上に赤い点/十字線/座標文字を描画
         ↓ AI が注釈付きスクリーンショットを確認 ↓
Step 3: click            → 座標が正しいことを確認後、実際のクリックを実行
```

### 赤い点の注釈効果

- **赤い円**（半径は設定可能、デフォルト 10px）
- **十字線**（水平 + 垂直線）
- **座標ラベル** `(426,236) シングルプレイ` 半透明の黒背景付き

### YAML 記述例

```yaml
# ステップ 1：座標をプレビュー
- action: preview_click
  x: 426
  y: 236
  label: "シングルプレイ"
  radius: 10
  color: "#FF0000"
  comment: "プレビュー: シングルプレイボタンの位置に赤い点を描画"

# ステップ 2：スクリーンショット（自動的に赤い点が付加される）
- action: screenshot
  name: "preview_singleplayer"
  comment: "AI 確認: 赤い点はボタン上にありますか？"

# ステップ 3：確認後にクリック
- action: click
  x: 426
  y: 236
  comment: "座標が正しいことを確認、クリックを実行"
```

## サポートされているアクション

| アクション | パラメータ | 説明 |
|------|------|------|
| `wait` | `seconds` | 指定秒数待機 |
| `screenshot` | `name` | スクリーンショットを撮影して保存（キューイングされたプレビューマーカーを自動描画） |
| **`preview_click`** | `x, y, label, radius, color` | **赤い点マーカーをキューイング、クリックは実行しない** |
| `click` | `x, y` | 座標クリックを実行 |
| `click_btn_idx` | `index` | ウィジェットインデックスでボタンをクリック |
| `click_btn_id` | `button_id` | ID でボタンをクリック |
| `ctrl_on` | - | MCP 制御モードに入る（マウス分離） |
| `ctrl_off` | - | 制御モードを終了 |
| `key` | `key` | キー押下（例: `Escape`, `E`） |
| `paste` | `text, press_enter` | テキストを貼り付け（IME 問題を回避） |
| `scroll` | `clicks` | マウスホイールスクロール |
| `look_delta` | `dyaw, dpitch` | ゲーム内視点回転（マウスは動かさない） |
| `set_view_angle` | `yaw, pitch` | 絶対視点角度を設定 |
| `right_click` | - | 右クリック |
| `enumerate_widgets` | - | 現在の画面の全ウィジェットを一覧表示 |
| `get_screen_buttons` | - | 現在の画面のボタン一覧を取得 |
| `cmd` | `command` | MC コマンドを実行（例: `/gamemode creative`） |
| `vision_check` | `prompt, expect, store_as` | AI によるスクリーンショットの視覚分析 |

## YAML ワークフロー形式

```yaml
name: "ワークフロー名"
description: |
  複数行の説明。このワークフローが何を行い、何を検証するかを記述します。

setup:
  version: "1.21.7-forge-57.0.2"   # MC バージョン
  container: false                   # コンテナウィンドウを使用するか
  wait_after_connect: 15             # 接続後に MC の読み込みを待つ秒数

steps:
  # 各 step はアクションで、順番に実行される
  - action: wait
    seconds: 15
    comment: "MC の読み込み完了を待機"

  - action: ctrl_on
    comment: "制御モードに入る"

  - action: screenshot
    name: "baseline"
    comment: "ベースラインスクリーンショット"
```

## setup 設定項目

| フィールド | 型 | デフォルト値 | 説明 |
|------|------|--------|------|
| `version` | string | `"1.21.7-forge-57.0.2"` | MC バージョン識別子 |
| `container` | bool | `true` | Win32 コンテナ埋め込みを使用するか |
| `wait_after_connect` | int | `15` | Mod 接続後の待機秒数（Mojang スプラッシュ画面が消えるまで） |

## CLI 引数

| 引数 | 説明 |
|------|------|
| `<workflow.yaml>` | YAML ワークフローファイルのパス（必須） |
| `--dry-run` | ドライランモード、MC に実際のコマンドを送信しない |
| `--skip-setup` | MC 起動をスキップ（MC が既に実行中の場合に使用） |
| `--step N` | ステップ N のみ実行（1-indexed） |
| `--interactive` | 各ステップで一時停止し確認を待つ |
| `--no-container` | コンテナウィンドウを無効化 |

## ファイル構造

```
minecraft-mcp/
├── workflows/                        # YAML ワークフロー定義
│   └── smoke_test.yaml               # スモークテスト：メインメニュー→ゲーム内→視点回転
├── scripts/
│   ├── run_yaml.py                   # YAML ランナーエントリポイント
│   ├── workflow_engine.py            # コアエンジン（アクション実行、状態管理、スクリーンショット注釈）
│   └── run.py                       # 旧コマンドラインランナー（引き続き使用可能）
├── packages/common/                  # Mod 共通コード（リフレクション入力、スクリーンショット、制御モード）
│   └── src/main/java/.../
│       ├── ReflectionHelper.java     # guiClick, preview_click, lookDelta など
│       ├── McpMessageHandler.java    # WebSocket メッセージ分散
│       └── ReflectedInputHandler.java# 入力ハンドラ
└── docs/
    └── workflow.md                   # 本文書
```

## 旧 run.py との違い

| 機能 | run.py (旧) | run_yaml.py (新) |
|------|-------------|-----------------|
| 形式 | コマンドライン引数 | YAML ファイル |
| 再利用性 | 低い（毎回引数を手入力） | 高い（YAML はバージョン管理・共有可能） |
| プレビュークリック | 非対応 | ✅ preview_click + スクリーンショット注釈 |
| 構造化コメント | なし | 各 step に comment フィールド |
| エラー復旧 | なし | ステップごとに success/error 状態を記録 |
| 条件分岐 | 非対応 | vision_check / if_screen |

## カスタムワークフローの作成

1. `workflows/smoke_test.yaml` をテンプレートとしてコピー
2. `name`、`description`、`steps` を修正
3. まず `--dry-run --skip-setup` で構文を検証
4. 実際の MC でテスト

### 典型的なパターン：ボタンを見つけてクリック

```yaml
# 1. まず現在の画面をスクリーンショット
- action: screenshot
  name: "current_screen"

# 2. AI ビジョンでボタンの座標を検出（または enum 結果から手動で読み取り）
- action: vision_check
  prompt: "「新しい世界を作成」ボタンの中心ピクセル座標を見つけてください"
  store_as: "create_world_pos"

# 3. その座標をプレビュー
- action: preview_click
  x: "${variables.create_world_pos.x}"   # TODO: 変数参照は実装予定
  y: "${variables.create_world_pos.y}"
  label: "新しい世界を作成"

# 4. レビュー用にスクリーンショット
- action: screenshot
  name: "preview_create_world"

# 5. 確認してクリック
- action: click
  x: 350
  y: 420
```

## 注意事項

1. **Mojang スプラッシュ画面の表示中に制御モードに入らないでください** — ゲームがロゴで停止します。`setup.wait_after_connect` は 15 秒以上に設定してください。
2. **スクリーンショットは Robot (AWT) を優先して使用** — MC ネイティブのスクリーンショットはキャッシュされたフレームを返す可能性があります。
3. **各 click の後は十分な wait を確保** — GUI の切り替えには時間がかかり、通常 3〜8 秒必要です。
4. **preview_click のマーカーは次の screenshot 時に描画されます** — これはキュー機構であり、即時反映ではありません。

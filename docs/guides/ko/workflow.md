# MCP Workflow 자동화 시스템

## 개요

MCP Workflow는 YAML 기반의 Minecraft 자동화 작업 프레임워크로, 기존 명령줄 인수 방식의 `run.py`를 대체합니다.

**핵심 설계 철학: 미리보기 클릭(Preview Click)** — 실제 클릭을 실행하기 전에 먼저 스크린샷에 빨간 점 마커를 그리고, 시각적으로 확인한 후에 실행합니다. 이를 통해 좌표를 맹목적으로 추측하는 것을 방지합니다.

## 빠른 시작

```bash
# 드라이 런 (MC를 실행하지 않고 YAML 파싱과 단계 로직만 검증)
python scripts/run_yaml.py workflows/smoke_test.yaml --dry-run --skip-setup

# 전체 실행 (MC 자동 실행 + 모든 단계 실행)
python scripts/run_yaml.py workflows/smoke_test.yaml

# MC가 이미 실행 중일 때 시작 단계 건너뛰기
python scripts/run_yaml.py workflows/smoke_test.yaml --skip-setup

# 5단계만 실행
python scripts/run_yaml.py workflows/smoke_test.yaml --step 5

# 컨테이너 임베딩 비활성화
python scripts/run_yaml.py workflows/smoke_test.yaml --no-container
```

## 미리보기 클릭 흐름

이것이 본 시스템의 핵심 혁신입니다. 기존 방식은 좌표를 직접 전송하여 클릭했지만, 좌표가 틀리면 허탕이 됩니다. 미리보기 흐름은 2단계 프로세스입니다:

```
Step 1: preview_click    → 좌표 (x,y)에 빨간 점 마커를 대기열에 추가, 아무 작업도 수행하지 않음
Step 2: screenshot       → 스크린샷 + 이미지에 빨간 점/십자선/좌표 텍스트 그리기
         ↓ AI가 주석이 달린 스크린샷을 검토 ↓
Step 3: click            → 좌표가 올바른지 확인한 후 실제 클릭 실행
```

### 빨간 점 주석 효과

- **빨간 원** (반경 설정 가능, 기본값 10px)
- **십자선** (수평 + 수직선)
- **좌표 레이블** `(426,236) 싱글플레이` 반투명 검은색 배경 포함

### YAML 작성 예시

```yaml
# 1단계: 좌표 미리보기
- action: preview_click
  x: 426
  y: 236
  label: "싱글플레이"
  radius: 10
  color: "#FF0000"
  comment: "미리보기: 싱글플레이 버튼 위치에 빨간 점 그리기"

# 2단계: 스크린샷 (자동으로 빨간 점이 포함됨)
- action: screenshot
  name: "preview_singleplayer"
  comment: "AI 검토: 빨간 점이 버튼 위에 있습니까?"

# 3단계: 확인 후 클릭
- action: click
  x: 426
  y: 236
  comment: "좌표 확인 완료, 클릭 실행"
```

## 지원되는 액션

| 액션 | 매개변수 | 설명 |
|------|------|------|
| `wait` | `seconds` | 지정된 초 동안 대기 |
| `screenshot` | `name` | 스크린샷을 캡처하여 저장 (대기 중인 미리보기 마커를 자동으로 그림) |
| **`preview_click`** | `x, y, label, radius, color` | **빨간 점 마커를 대기열에 추가, 클릭은 실행하지 않음** |
| `click` | `x, y` | 좌표 클릭 실행 |
| `click_btn_idx` | `index` | 위젯 인덱스로 버튼 클릭 |
| `click_btn_id` | `button_id` | ID로 버튼 클릭 |
| `ctrl_on` | - | MCP 제어 모드 진입 (마우스 분리) |
| `ctrl_off` | - | 제어 모드 종료 |
| `key` | `key` | 키 누르기 (예: `Escape`, `E`) |
| `paste` | `text, press_enter` | 텍스트 붙여넣기 (IME 문제 우회) |
| `scroll` | `clicks` | 마우스 휠 스크롤 |
| `look_delta` | `dyaw, dpitch` | 게임 내 시점 회전 (마우스는 움직이지 않음) |
| `set_view_angle` | `yaw, pitch` | 절대 시점 각도 설정 |
| `right_click` | - | 오른쪽 클릭 |
| `enumerate_widgets` | - | 현재 화면의 모든 위젯 나열 |
| `get_screen_buttons` | - | 현재 화면의 버튼 목록 가져오기 |
| `cmd` | `command` | MC 명령 실행 (예: `/gamemode creative`) |
| `vision_check` | `prompt, expect, store_as` | AI 비전 스크린샷 분석 |

## YAML 워크플로우 형식

```yaml
name: "워크플로우 이름"
description: |
  여러 줄 설명, 이 워크플로우가 무엇을 하고 무엇을 검증하는지 설명합니다.

setup:
  version: "1.21.7-forge-57.0.2"   # MC 버전
  container: false                   # 컨테이너 창 사용 여부
  wait_after_connect: 15             # 연결 후 MC 로딩을 기다리는 초

steps:
  # 각 step은 순서대로 실행되는 액션입니다
  - action: wait
    seconds: 15
    comment: "MC 로딩 완료 대기"

  - action: ctrl_on
    comment: "제어 모드 진입"

  - action: screenshot
    name: "baseline"
    comment: "기준 스크린샷"
```

## setup 구성 항목

| 필드 | 유형 | 기본값 | 설명 |
|------|------|--------|------|
| `version` | string | `"1.21.7-forge-57.0.2"` | MC 버전 식별자 |
| `container` | bool | `true` | Win32 컨테이너 임베딩 사용 여부 |
| `wait_after_connect` | int | `15` | Mod 연결 후 대기 초 (Mojang 시작 화면이 사라질 때까지) |

## CLI 인수

| 인수 | 설명 |
|------|------|
| `<workflow.yaml>` | YAML 워크플로우 파일 경로 (필수) |
| `--dry-run` | 드라이 런 모드, MC에 실제 명령을 보내지 않음 |
| `--skip-setup` | MC 시작 건너뛰기 (MC가 이미 실행 중일 때 사용) |
| `--step N` | N번째 단계만 실행 (1-indexed) |
| `--interactive` | 각 단계마다 일시 중지하고 확인 대기 |
| `--no-container` | 컨테이너 창 비활성화 |

## 파일 구조

```
minecraft-mcp/
├── workflows/                        # YAML 워크플로우 정의
│   └── smoke_test.yaml               # 스모크 테스트: 메인 메뉴→게임 내→시점 회전
├── scripts/
│   ├── run_yaml.py                   # YAML 실행기 진입점
│   ├── workflow_engine.py            # 핵심 엔진 (액션 실행, 상태 관리, 스크린샷 주석)
│   └── run.py                       # 구형 명령줄 실행기 (계속 사용 가능)
├── packages/common/                  # Mod 공통 코드 (리플렉션 입력, 스크린샷, 제어 모드)
│   └── src/main/java/.../
│       ├── ReflectionHelper.java     # guiClick, preview_click, lookDelta 등
│       ├── McpMessageHandler.java    # WebSocket 메시지 분배
│       └── ReflectedInputHandler.java# 입력 처리기
└── docs/
    └── workflow.md                   # 이 문서
```

## 구형 run.py와의 차이점

| 기능 | run.py (구형) | run_yaml.py (신형) |
|------|-------------|-----------------|
| 형식 | 명령줄 인수 | YAML 파일 |
| 재사용성 | 낮음 (매번 인수 직접 입력) | 높음 (YAML은 버전 관리 및 공유 가능) |
| 미리보기 클릭 | 지원 안 함 | ✅ preview_click + 스크린샷 주석 |
| 구조화된 주석 | 없음 | 각 step에 comment 필드 |
| 오류 복구 | 없음 | 단계별 success/error 상태 기록 |
| 조건 분기 | 지원 안 함 | vision_check / if_screen |

## 사용자 정의 워크플로우 작성

1. `workflows/smoke_test.yaml`을 템플릿으로 복사
2. `name`, `description`, `steps` 수정
3. 먼저 `--dry-run --skip-setup`으로 구문 검증
4. 실제 MC로 테스트

### 일반적인 패턴: 버튼을 찾아 클릭하기

```yaml
# 1. 먼저 현재 화면을 스크린샷
- action: screenshot
  name: "current_screen"

# 2. AI 비전으로 버튼 좌표 찾기 (또는 enum 결과에서 수동으로 읽기)
- action: vision_check
  prompt: "'새로운 세계 만들기' 버튼의 중심 픽셀 좌표를 찾으세요"
  store_as: "create_world_pos"

# 3. 해당 좌표 미리보기
- action: preview_click
  x: "${variables.create_world_pos.x}"   # TODO: 변수 참조 구현 예정
  y: "${variables.create_world_pos.y}"
  label: "새로운 세계 만들기"

# 4. 검토용 스크린샷
- action: screenshot
  name: "preview_create_world"

# 5. 확인 후 클릭
- action: click
  x: 350
  y: 420
```

## 주의사항

1. **Mojang 시작 화면이 표시되는 동안 제어 모드로 진입하지 마십시오** — 게임이 로고에서 멈춥니다. `setup.wait_after_connect`는 15초 이상이어야 합니다.
2. **스크린샷은 Robot (AWT)를 우선 사용** — MC 네이티브 스크린샷은 캐시된 프레임을 반환할 수 있습니다.
3. **각 click 후 충분한 wait 확보** — GUI 전환에는 시간이 필요하며, 일반적으로 3~8초입니다.
4. **preview_click의 마커는 다음 screenshot 시점에 그려집니다** — 이는 큐 메커니즘이며 즉시 반영되지 않습니다.

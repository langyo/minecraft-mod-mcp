# AI 도구 통합 가이드

이 가이드는 주요 AI 코딩 도구를 HTTP를 통해 Minecraft MCP 서버에 연결하는 방법을 설명합니다.

## Minecraft MCP HTTP 엔드포인트

Minecraft MCP 서버는 다음과 같은 HTTP 엔드포인트를 제공합니다 (기본 포트: **9876**):

| 엔드포인트 | 메서드 | 설명 |
|----------|--------|-------------|
| `/api/status` | GET | 상태 확인 |
| `/api/cmd` | POST | JSON-RPC 명령어 디스패치 (본문: `{"cmd":"...", "params":{...}}`) |
| `/api/screenshot` | GET | 스크린샷 촬영, PNG base64 반환 |
| `/api/events` | GET | 실시간 호출 내역을 위한 SSE(Server-Sent Events) 스트림 |
| `/api/calls` | GET | 최근 50개의 호출 이벤트를 JSON 배열로 반환 |

> **사전 준비**: Minecraft MCP 데몬이 실행 중이고 MCP 모드가 적용된 Minecraft 클라이언트가 연결되어 있어야 합니다. `just daemon`을 실행한 후 `just launch <version> <loader>`를 실행하세요.

---

## 통합 방법

대부분의 AI 코딩 도구는 외부 서버 연결을 위해 **Model Context Protocol (MCP)**을 지원합니다. Minecraft MCP 서버는 다음 방식으로 연결할 수 있습니다:

- **SSE 전송**: 도구의 MCP 클라이언트를 `http://localhost:9876/api/events`로 지정
- **HTTP REST API**: `http://localhost:9876/api/cmd`로 직접 POST 요청 전송

아래 섹션에서는 각 도구별 설정 방법을 안내합니다.

---

## 코딩 에이전트 도구

### Claude Code

Anthropic의 터미널 기반 AI 코딩 어시스턴트입니다.

**설정 방법**: 프로젝트 루트에 `.mcp.json` 파일을 생성하거나 편집합니다:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

또는 `claude mcp add minecraft-mcp --transport sse http://localhost:9876/api/events` 명령어를 사용할 수도 있습니다.

### Claude Desktop / Claude for IDE

Claude의 데스크톱 앱 및 VS Code/JetBrains IDE 플러그인 버전입니다.

**설정 방법**: `claude_desktop_config.json` 파일을 편집합니다:

- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

**Claude for IDE** (VS Code / JetBrains)의 경우 설정은 동일합니다 — 프로젝트 루트의 `.mcp.json` 파일을 사용하세요.

### OpenCode

오픈소스 터미널 코딩 에이전트입니다.

**설정 방법**: 프로젝트 루트에 `.opencode.json` 파일을 생성하거나 `~/.config/opencode/config.json` 파일을 편집합니다:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Cursor

커스텀 모델을 지원하는 AI 중심 코드 편집기입니다.

**설정 방법**: 프로젝트 루트에 `.cursor/mcp.json` 파일을 생성합니다:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "url": "http://localhost:9876/api/events",
      "transport": "sse"
    }
  }
}
```

또는 UI를 통해: **Cursor Settings → MCP → Add new MCP Server**에서 전송 유형을 **SSE**로 설정하고 URL을 입력합니다.

### Cline

VS Code AI 코딩 확장 프로그램입니다.

**설정 방법**: VS Code 설정(`Ctrl+,`)을 열고 `cline.mcpServers`를 검색하거나 `settings.json`에 추가합니다:

```json
{
  "cline.mcpServers": {
    "minecraft-mcp": {
      "url": "http://localhost:9876/api/events",
      "transport": "sse"
    }
  }
}
```

### Roo Code

코드 작성 및 리팩토링을 위한 지능형 VS Code 확장 프로그램입니다.

**설정 방법**: VS Code `settings.json`에 추가합니다 (Cline과 동일한 형식):

```json
{
  "roo.mcpServers": {
    "minecraft-mcp": {
      "url": "http://localhost:9876/api/events",
      "transport": "sse"
    }
  }
}
```

### Kilo Code

코드 생성 및 프로젝트 관리를 위한 효율적인 VS Code 플러그인입니다.

**설정 방법**: VS Code `settings.json`에 추가합니다:

```json
{
  "kilo.mcpServers": {
    "minecraft-mcp": {
      "url": "http://localhost:9876/api/events",
      "transport": "sse"
    }
  }
}
```

### GitHub Copilot

VS Code의 GitHub AI 페어 프로그래머입니다.

**설정 방법**: 작업 영역에 `.github/copilot-instructions.md` 파일을 생성하거나 VS Code 설정을 통해 MCP를 구성합니다:

```json
{
  "github.copilot.mcpServers": {
    "minecraft-mcp": {
      "url": "http://localhost:9876/api/events",
      "transport": "sse"
    }
  }
}
```

### GitHub Copilot CLI

명령줄용 GitHub Copilot입니다.

**설정 방법**: 환경 변수를 설정하거나 `gh copilot config`를 사용합니다:

```bash
export MCP_SERVER_URL="http://localhost:9876/api/events"
```

### CodeBuddy / WorkBuddy

AI 기반 풀스택 지능형 프로그래밍 도구입니다.

**설정 방법**: 프로젝트 루트 또는 작업 영역에 `mcp.json` 파일을 생성합니다:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "url": "http://localhost:9876/api/events",
      "transport": "sse"
    }
  }
}
```

### TRAE

다양한 개발 작업을 독립적으로 수행할 수 있는 AI 편집기입니다.

**설정 방법**: **설정 → MCP Servers → Add Server**로 이동합니다:

- **Name**: `minecraft-mcp`
- **Transport**: SSE
- **URL**: `http://localhost:9876/api/events`

### ZCode

강력한 AI 에이전트와 기존 도구 체인을 결합합니다.

**설정 방법**: `~/.zcode/config.json` 파일을 편집합니다:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Lingma

지능형 프로그래밍 어시스턴트입니다.

**설정 방법**: **설정 → MCP → Add Server**로 이동합니다:

- **Name**: `minecraft-mcp`
- **Transport**: SSE
- **URL**: `http://localhost:9876/api/events`

### Qoder

실무 소프트웨어를 위한 에이전트 프로그래밍 플랫폼입니다.

**설정 방법**: `~/.qoder/mcp.json` 파일을 편집합니다:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Droid

엔드투엔드 워크플로우를 위한 엔터프라이즈급 터미널 AI 코딩 에이전트입니다.

**설정 방법**: `~/.droid/mcp.json` 파일을 편집합니다:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Crush

CLI 및 TUI 인터페이스를 지원하는 터미널 AI 프로그래밍 도구입니다.

**설정 방법**: `~/.crush/config.json` 파일을 편집합니다:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Goose

로컬 실행 및 자동화된 엔지니어링 작업을 지원하는 AI 에이전트 도구입니다.

**설정 방법**: `~/.config/goose/mcp.json` 파일을 편집합니다:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Deep Code

DeepSeek 기반 코딩 어시스턴트입니다.

**설정 방법**: `~/.deepcode/config.json` 파일을 편집합니다:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Reasonix

추론 중심 AI 코딩 도구입니다.

**설정 방법**: `~/.reasonix/config.json` 파일을 편집합니다:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Langcli

CLI 기반 AI 코딩 어시스턴트입니다.

**설정 방법**: `~/.langcli/config.yaml` 파일을 편집합니다:

```yaml
mcp_servers:
  minecraft-mcp:
    type: sse
    url: http://localhost:9876/api/events
```

### Oh My Pi

다목적 AI 에이전트 플랫폼입니다.

**설정 방법**: `~/.oh-my-pi/mcp.json` 파일을 편집합니다:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Pi

경량 AI 코딩 동반 도구입니다.

**설정 방법**: `~/.pi/config.json` 파일을 편집합니다:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

---

## 범용 에이전트 도구

### OpenClaw

Skills 확장 기능으로 로컬에서 실행되는 오픈소스 AI 어시스턴트입니다.

**설정 방법**: 작업 영역의 `openclaw.json` 파일을 편집합니다:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### Cherry Studio

여러 모델 통합을 지원하는 AI 애플리케이션 IDE입니다.

**설정 방법**: **설정 → MCP Servers → Add**로 이동합니다:

- **Name**: `minecraft-mcp`
- **Transport**: SSE
- **URL**: `http://localhost:9876/api/events`

### Hermes Agent

영구 메모리를 갖춘 오픈소스 자가 진화형 AI 에이전트입니다.

**설정 방법**: `~/.hermes/config.json` 파일을 편집합니다:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### AstrBot

AI 기반 봇 프레임워크입니다.

**설정 방법**: `astrbot_config.json` 파일을 편집합니다:

```json
{
  "mcp_servers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

### nanobot

다양한 작업을 위한 경량 AI 에이전트입니다.

**설정 방법**: `~/.nanobot/config.json` 파일을 편집합니다:

```json
{
  "mcpServers": {
    "minecraft-mcp": {
      "type": "sse",
      "url": "http://localhost:9876/api/events"
    }
  }
}
```

---

## 직접 HTTP REST API 액세스

MCP 프로토콜을 기본 지원하지 않는 도구의 경우, Minecraft MCP 서버의 HTTP REST API를 통해 직접 상호작용할 수 있습니다:

```bash
# 상태 확인
curl http://localhost:9876/api/status

# 명령어 실행
curl -X POST http://localhost:9876/api/cmd \
  -H "Content-Type: application/json" \
  -d '{"cmd":"screenshot","params":{}}'

# 스크린샷 촬영
curl http://localhost:9876/api/screenshot

# 이벤트 구독 (SSE 스트림)
curl http://localhost:9876/api/events
```

### 주요 명령어

| 명령어 | 설명 |
|---------|-------------|
| `screenshot` | Minecraft 창의 스크린샷 촬영 |
| `click` | (x, y) 좌표 클릭 |
| `press_key` | 키보드 키 입력 |
| `type_text` | 텍스트 문자열 입력 |
| `scroll` | 마우스 스크롤 수행 |
| `execute_command` | Minecraft 슬래시 명령어 실행 |
| `get_player_info` | 플레이어 위치 및 상태 정보 조회 |
| `get_world_info` | 월드 정보 조회 |

---

## 문제 해결

1. **연결 거부됨**: MCP 데몬이 실행 중(`just daemon`)이고 Minecraft 클라이언트가 실행되었는지 확인하세요.
2. **SSE 타임아웃**: 일부 도구는 비활성 상태가 지속되면 SSE 연결이 끊어질 수 있습니다. 도구 또는 SSE 연결을 다시 시작하세요.
3. **포트 충돌**: 포트 9876이 사용 중인 경우, `MCP_PORT` 환경 변수 또는 시스템 속성 `mcp.server.port`를 통해 다른 포트를 설정하세요.
4. **방화벽**: 방화벽이 `localhost:9876`에 대한 연결을 허용하는지 확인하세요.

> 문제나 질문이 있으시면 [GitHub 저장소](https://github.com/langyo/minecraft-mcp)에 이슈를 등록해 주세요.

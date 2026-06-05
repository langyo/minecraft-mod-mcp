# NPM MCP CLI 사용 가이드

**[English](../en/CLI.md)** &bull; **[简体中文](../zhs/CLI.md)** &bull; **[繁體中文](../zht/CLI.md)** &bull; **[日本語](../ja/CLI.md)** &bull; **한국어** &bull; **[Français](../fr/CLI.md)** &bull; **[Español](../es/CLI.md)** &bull; **[Русский](../ru/CLI.md)**

> `minecraft-mod-mcp` 패키지는 Minecraft 클라이언트, 서버 실행, 버전 관리, 계정 관리, 모드 SDK 빌드를 모두 명령줄에서 수행할 수 있는 완전한 CLI를 제공합니다.

---

## 설치

```bash
npm install -g minecraft-mod-mcp
```

또는 설치 없이 직접 실행:

```bash
npx minecraft-mod-mcp
```

---

## 명령어

### MCP 서버

AI 도구 통합을 위한 MCP stdio 서버 시작:

```bash
minecraft-mod-mcp
minecraft-mod-mcp mcp [options]
```

| 옵션 | 설명 |
|--------|-------------|
| `--no-discover` | 실행 중인 Minecraft 모드를 스캔하지 않음 |
| `--discover-timeout <ms>` | 모드 발견 타임아웃 (기본값: 300000) |

---

### 클라이언트 실행 — `launch`

지정된 버전과 모드 로더로 Minecraft 클라이언트를 실행합니다.

```bash
minecraft-mod-mcp launch <version> [options]
```

| 옵션 | 기본값 | 설명 |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | 모드 로더 |
| `--mc-dir <path>` | 자동 | 게임 디렉토리 |
| `--java <path>` | 자동 감지 | Java 실행 파일 경로 |
| `--memory <mb>` | `2048` | 최대 JVM 메모리 풀 (MB) |
| `--min-memory <mb>` | `512` | 최소 JVM 메모리 풀 (MB) |
| `--jvm-args <args>` | — | 추가 JVM 인수 (공백으로 구분) |
| `--game-args <args>` | — | 추가 게임 인수 (공백으로 구분) |
| `--fullscreen` | `false` | 전체 화면 모드로 실행 |
| `--width <px>` | `854` | 창 너비 |
| `--height <px>` | `480` | 창 높이 |
| `--server <host>` | — | 실행 시 서버에 자동 연결 |
| `--server-port <port>` | `25565` | 서버 포트 |
| `--port <port>` | 자동 | MCP 모드 포트 |
| `--mod-jar <path>` | — | 주입할 모드 JAR |
| `--dry-run` | `false` | 명령어 출력만 하고 실행하지 않음 |

**예제:**

```bash
# 4GB 메모리, 전체 화면으로 실행
minecraft-mod-mcp launch 1.21.11 --memory 4096 --fullscreen --loader fabric

# 사용자 정의 JVM 인수로 실행하고 서버에 자동 연결
minecraft-mod-mcp launch 26.1.2 --jvm-args "-XX:+UseG1GC -Dfml.readTimeout=120" --server myserver.com

# 1280x720 창 모드로 실행
minecraft-mod-mcp launch 1.20.6 --width 1280 --height 720 --loader neoforge

# 실행 명령어 미리보기
minecraft-mod-mcp launch 1.21.11 --dry-run
```

---

### 독립 서버 — `server`

전용 Minecraft 서버를 실행합니다.

```bash
minecraft-mod-mcp server <version> [options]
```

| 옵션 | 기본값 | 설명 |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | 모드 로더 |
| `--java <path>` | 자동 감지 | Java 실행 파일 경로 |
| `--memory <mb>` | `1024` | 최대 JVM 메모리 풀 (MB) |
| `--min-memory <mb>` | — | 최소 JVM 메모리 풀 (MB) |
| `--jvm-args <args>` | — | 추가 JVM 인수 (공백으로 구분) |
| `--game-args <args>` | — | 추가 서버 인수 (공백으로 구분) |
| `--mod-jar <path>` | — | 서버 mods/에 복사할 모드 JAR |
| `--dry-run` | `false` | 명령어 출력만 하고 실행하지 않음 |

**예제:**

```bash
# 4GB 메모리로 서버 실행
minecraft-mod-mcp server 1.21.11 --memory 4096

# 사용자 정의 GC 튜닝으로 실행
minecraft-mod-mcp server 26.1.2 --jvm-args "-XX:+UseZGC -XX:+ZGenerational" --memory 8192

# 모드가 포함된 Fabric 서버
minecraft-mod-mcp server 1.21.11 --loader fabric --mod-jar ./path/to/mod.jar
```

---

### 통합 (서버 + 클라이언트) — `serve`

한 번의 명령어로: 서버 설치 + 서버 실행 + 클라이언트 실행 (자동 연결).

```bash
minecraft-mod-mcp serve <version> [options]
```

| 옵션 | 기본값 | 설명 |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | 모드 로더 |
| `--java <path>` | 자동 감지 | Java 실행 파일 경로 |
| `--memory <mb>` | `2048` | 클라이언트 최대 메모리 (MB) |
| `--min-memory <mb>` | — | 클라이언트 최소 메모리 (MB) |
| `--server-memory <mb>` | `1024` | 서버 최대 메모리 (MB) |
| `--server-min-memory <mb>` | — | 서버 최소 메모리 (MB) |
| `--jvm-args <args>` | — | 양쪽에 적용할 추가 JVM 인수 |
| `--game-args <args>` | — | 클라이언트 추가 게임 인수 |
| `--server-game-args <args>` | — | 서버 추가 인수 |
| `--fullscreen` | `false` | 클라이언트 전체 화면으로 실행 |
| `--width <px>` | `854` | 클라이언트 창 너비 |
| `--height <px>` | `480` | 클라이언트 창 높이 |
| `--port <port>` | 자동 | MCP 포트 |
| `--mod-jar <path>` | — | 양쪽에 주입할 모드 JAR |
| `--dry-run` | `false` | 계획만 출력하고 실행하지 않음 |

**예제:**

```bash
# 완전한 환경: 4GB 클라이언트, 2GB 서버, 전체 화면
minecraft-mod-mcp serve 1.21.11 --memory 4096 --server-memory 2048 --fullscreen
```

---

### 버전 관리

| 명령어 | 설명 |
|---------|-------------|
| `minecraft-mod-mcp list` | 지원되는 Minecraft 버전 목록 |
| `minecraft-mod-mcp installed` | 로컬에 설치된 버전 목록 |
| `minecraft-mod-mcp install <version> [--loader <l>]` | 버전 다운로드 및 설치 |

---

### 계정 관리

| 명령어 | 설명 |
|---------|-------------|
| `minecraft-mod-mcp auth login` | Microsoft 계정으로 로그인 |
| `minecraft-mod-mcp auth offline <name>` | 오프라인 계정 생성 |
| `minecraft-mod-mcp auth list` | 설정된 계정 목록 |
| `minecraft-mod-mcp auth select <uuid>` | 활성 계정 설정 |
| `minecraft-mod-mcp auth remove <uuid>` | 계정 제거 |

---

### 유틸리티

| 명령어 | 설명 |
|---------|-------------|
| `minecraft-mod-mcp java` | 설치된 Java 버전 감지 |
| `minecraft-mod-mcp status` | MCP 모드 연결 상태 표시 |
| `minecraft-mod-mcp sdk <version> [--loader <l>] [--no-build]` | 지정 버전의 모드 SDK 빌드 |

---

## JVM / 게임 인수

`--jvm-args` 및 `--game-args` 옵션은 공백으로 구분된 인수를 허용합니다. 공백으로 분리하는 셸에서는 전체 값을 따옴표로 묶으세요:

```bash
minecraft-mod-mcp launch 1.21.11 --jvm-args "-XX:+UseG1GC -XX:MaxGCPauseMillis=50"
minecraft-mod-mcp server 1.21.11 --game-args "--port 25566 --max-players 10"
```

---

## JSON 설정 파일

`~/.minecraft/mcp_launcher/config.json`에서 고급 기본값을 설정할 수 있습니다:

```json
{
  "max_memory_mb": 4096,
  "min_memory_mb": 1024,
  "width": 1920,
  "height": 1080,
  "fullscreen": false,
  "java_args": "-XX:+UseG1GC",
  "game_args": "",
  "game_dir": "~/.minecraft/mcp_launcher/game",
  "mcp_port": 9876,
  "download_source": "bmclapi",
  "language": "ko-KR"
}
```

CLI 플래그는 항상 설정 파일 값보다 우선합니다.

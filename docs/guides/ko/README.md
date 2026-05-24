<!-- markdownlint-disable MD033 MD041 MD036 -->
<div align="center">

<img src="../../logo.webp" alt="Minecraft MCP logo" width="200"/>

# Minecraft MCP

**멀티 버전, 멀티 모드로더 Minecraft MCP (모델 컨텍스트 프로토콜) 브릿지 모드**

[![License](https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue.svg)](../../LICENSE-MIT)
[![Rust](https://img.shields.io/badge/rust-1.85%2B-orange.svg)](https://www.rust-lang.org/)
[![Java](https://img.shields.io/badge/java-8--25-red.svg)](https://www.java.com/)
[![Python](https://img.shields.io/badge/python-3.10%2B-yellow.svg)](https://www.python.org/)
[![Version](https://img.shields.io/badge/version-0.1.0-lightgrey.svg)]()

**[English](../en/README.md)** &bull; **[简体中文](../zhs/README.md)** &bull; **[繁體中文](../zht/README.md)** &bull; **[日本語](../ja/README.md)** &bull; **[한국어](README.md)** &bull; **[Français](../fr/README.md)** &bull; **[Español](../es/README.md)** &bull; **[Русский](../ru/README.md)**

</div>
<!-- markdownlint-enable MD033 MD041 MD036 -->

> **버전 0.1.0** — 활발히 개발 중입니다. Rust 제어 서버, 24개의 모드 플러그인, YAML 워크플로 자동화 엔진이 작동합니다. CI 빌드는 Rust 검사와 1.21.7 Forge 모드에서 통과됩니다.

## Minecraft MCP란?

Minecraft MCP(Master Control Program)는 멀티 버전, 멀티 모드로더 Minecraft UI 자동화 프레임워크입니다. 세 가지 계층으로 구성됩니다:

- **Rust 제어 서버**(`packages/server/`) — WebSocket + TCP 서버로 스크린샷 캡처, 마우스/키보드 입력 주입, 비디오 스트리밍 제공
- **Java 모드 플러그인**(`packages/mods/`) — Forge, Fabric, NeoForge를 아우르는 24개의 모드 프로젝트로, MC 1.8.9부터 26.1.2까지 지원하며 공통 코드베이스(`packages/common/`)를 공유
- **Python 자동화**(`scripts/`) — "미리보기 클릭"(클릭 전 스크린샷에 좌표 시각적 확인) 기능이 있는 YAML 워크플로 엔진, 테스트 실행기, 빌드 자동화, 데몬 관리

## 지원 버전

| MC 버전 | Forge | Fabric | NeoForge |
|---------|:-----:|:------:|:--------:|
| 1.8.9 | ✓ | | |
| 1.9.4 | ✓ | | |
| 1.10.2 | ✓ | | |
| 1.11.2 | ✓ | | |
| 1.12.2 | ✓ | | |
| 1.13.2 | ✓ | | |
| 1.14.4 | ✓ | ✓ | |
| 1.15.2 | ✓ | ✓ | |
| 1.16.5 | ✓ | ✓ | |
| 1.17.1 | ✓ | ✓ | |
| 1.18.2 | ✓ | ✓ | |
| 1.19.4 | ✓ | ✓ | |
| 1.20.6 | ✓ | ✓ | ✓ |
| 1.21.7 | ✓ | | |
| 26.1.2 | ✓ | | ✓ |

## 빠른 시작

### 사전 준비

- Python 3.10+
- Rust 1.85+
- JDK 21 (Corretto 권장)

### 설치 및 빌드

```bash
# Python 종속성 설치
pip install -r scripts/requirements.txt

# 환경 확인
just check-env

# 전체 빌드 (코드 생성 + 캐시 + 모든 모드 빌드)
just full

# Rust 서버만 빌드
just build-server
```

### 실행

```bash
# 제어 서버 데몬 시작
just daemon

# Minecraft 버전 실행
just launch 1.21.7 forge

# 스모크 테스트 실행 (빌드 + 실행 + 스크린샷)
just smoke 1.21.7
```

## 아키텍처

```
┌─────────────────────────────────────┐
│          Rust 제어 서버               │
│  (axum WS/TCP, 스크린샷, 입력 주입)   │
└──────────────┬──────────────────────┘
               │ MCP 프로토콜 (WS/TCP)
┌──────────────▼──────────────────────┐
│         Java 모드 플러그인            │
│  (Forge / Fabric / NeoForge)        │
│  ReflectionHelper, InputHandler     │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│         Minecraft 클라이언트           │
│  (1.8.9 – 26.1.2, 24개 모드 변형)   │
└─────────────────────────────────────┘
```

## 문서

- **[워크플로 자동화](workflow.md)** — 미리보기 클릭 기능이 있는 YAML 기반 UI 자동화
- **[PLAN.md](../../PLAN.md)** — 완료된 테스트 케이스: Redstone Ready 세계 생성
- **[Workflows](../../workflows/)** — 선언적 YAML 테스트 정의

## 기여

이슈와 풀 리퀘스트를 환영합니다.

## 라이선스

다음 중 하나의 라이선스로 제공됩니다:

- Apache License, Version 2.0 ([LICENSE-APACHE](../../LICENSE-APACHE) 또는 http://www.apache.org/licenses/LICENSE-2.0)
- MIT License ([LICENSE-MIT](../../LICENSE-MIT) 또는 http://opensource.org/licenses/MIT)

선택에 따릅니다.

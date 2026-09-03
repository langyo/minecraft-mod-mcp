# Contributing to Minecraft Mod MCP

Thanks for your interest in contributing! This guide covers the development setup.

> **Just want to use the mod?** See the [README](README.md) for installation and AI connection instructions.

## Development Setup

### Prerequisites

- JDK 21 (Corretto recommended)
- Python 3.11+
- Node.js 20+

### Build

```bash
# Install dependencies
pip install -r scripts/requirements.txt

# Build everything (mods + MCP bridge)
just full
```

### Run

```bash
# Start the MCP daemon
just daemon

# Launch Minecraft with the mod for a specific version
just launch 1.21.7 forge

# Run an end-to-end smoke test
just smoke 1.21.7
```

## Project Structure

```
minecraft-mcp/
├── packages/
│   ├── common/                  # Shared Java library (HTTP server, reflection, input injection)
│   │   └── src/main/java/xyz/langyo/minecraft/mcp/common/
│   ├── mods/<version>/          # Per-version mod entry points (1.8.9 – 26.1.2)
│   └── minecraft-mod-mcp/       # TypeScript MCP bridge (npm package)
│       └── src/                 # MCP server, port discovery, transport handlers
├── scripts/                     # Python build/test/launch scripts
├── docs/
│   ├── guides/                  # User documentation (8 languages)
│   └── research/                # Technical research per version/loader
└── tests/                       # Test metadata and reference screenshots
```

## How It Works

1. The Java mod runs an HTTP server on port 9876 inside Minecraft
2. Java reflection handles cross-version compatibility (same code works for 1.8.9 through 26.1.2)
3. The TypeScript MCP bridge discovers the mod on the network and exposes MCP tools
4. AI tools connect via standard SSE-based MCP protocol

## Testing

```bash
# Smoke test a specific version
just smoke 1.21.7

# TypeScript unit tests
cd packages/minecraft-mod-mcp && npm test
```

## Release Process

1. Update version in `packages/minecraft-mod-mcp/package.json`
2. Run `just full` to build all artifacts
3. Tag and push: `git tag vX.Y.Z && git push --tags`
4. GitHub Actions publishes the npm package and GitHub Release

## Commit Conventions

All commit subjects and PR titles follow one format:

```
<gitmoji> <Capitalized English one-sentence summary ending with a period.>
```

- **Gitmoji** — from the [gitmoji.dev](https://gitmoji.dev) canonical set (e.g. `✨` new feature, `🐛` bugfix, `📝` docs, `🔧` config, `👷` CI), followed by exactly one space
- **Summary** — one plain English sentence: capitalized, ends with exactly one `.`, no CJK, **no conventional-commit prefix** (`feat:`, `fix:`, …), **no `Topic phrase:` colon-prefix shape**, **no version number**, **no filler**; detailed context belongs in the commit body
- **PR titles follow the same rule** — PRs are squash-merged into `dev`, so the PR title becomes the permanent commit subject
- `Revert "..."` subjects (from `git revert`) and squash suffixes ` (#123)` are exempt

Examples:

```
✨ AI-generated mod code can now control the game via MCP tools.
🐛 Fix crash when switching dimensions on Forge 1.21.7.
📝 Restructure documentation for modder-first experience.
```

Development commits on feature branches may still use a conventional-commit prefix for internal clarity (`feat:`, `fix:`, `docs:`, etc.) — they are squashed away at merge time anyway; the gitmoji format is preferred everywhere.

Check before pushing:

```bash
just lint-commits   # validates origin/dev..HEAD
```

CI enforces the same rules on every PR title and on every new commit pushed to `dev` (see `scripts/commit_lint.py`). AI coding agents must additionally follow [AGENTS.md](AGENTS.md).

---

## Issues & Pull Requests

### Reporting Bugs

Use the [Bug Report](https://github.com/langyo/minecraft-mod-mcp/issues/new?template=bug_report.md) template. Include:

- Minecraft version, modloader, and mod version
- Clear steps to reproduce
- Expected vs. actual behavior
- Relevant logs or screenshots

### Suggesting Features

Use the [Feature Request](https://github.com/langyo/minecraft-mod-mcp/issues/new?template=feature_request.md) template. Describe the problem first, then your proposed solution.

### Pull Requests

1. Create a feature branch from `dev` (`feat/<name>` / `fix/<name>` / `chore/<name>`)
2. Make your changes, following existing code style
3. Ensure `just full` builds successfully
4. Run `just smoke <version>` on at least one Minecraft version
5. Open a PR against `dev` using the [PR template](https://github.com/langyo/minecraft-mod-mcp/blob/dev/.github/PULL_REQUEST_TEMPLATE.md), with the title in the commit format above
6. Once checks pass, the PR is **squash-merged** into `dev` and the branch deleted

PRs target `dev` and are squash-merged, keeping `dev` history linear — one reviewed, gitmoji-formatted commit per change. The `master` branch receives periodic merges from `dev` (see [Commit Conventions](#commit-conventions)).

> **Since 2026-09-03** all changes land through PRs, including maintainer and agent changes; direct pushes to `dev`/`master` are no longer part of the workflow, and CI flags non-compliant direct pushes to `dev`.

### Code Style

- **Java**: Follow standard conventions, use reflection utilities from `common/` for cross-version compatibility
- **TypeScript**: Run `npm run lint` in `packages/minecraft-mod-mcp/`
- **Python**: Follow PEP 8
- No commented-out code; no secret/credential in commits

Please check existing issues and PRs before opening a new one to avoid duplicates.

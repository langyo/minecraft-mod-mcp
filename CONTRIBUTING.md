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

### Merge into `master`

All merges into `master` (PR merge, dev → master) must use a merge commit message following this format:

```
{emoji} {English summary}
```

- **Emoji** — must be one from [gitmoji.dev](https://gitmoji.dev) that matches the change (e.g. `🚀` for first release, `✨` for new feature, `🐛` for bugfix, `📝` for docs)
- **Summary** — concise one-line English, **no conventional-commit prefix** (no `feat:`, `fix:`, `chore:`, etc.), **no version number**, **no filler**

Examples:

```
✨ AI-generated mod code can now control the game via MCP tools
🐛 Fix crash when switching dimensions on Forge 1.21.7
📝 Restructure documentation for modder-first experience
```

### Development commits (non-master)

Development commits on feature branches or `dev` may use a conventional-commit prefix for internal clarity (`feat:`, `fix:`, `docs:`, etc.) — the strict rules above apply only to `master` merges.

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

1. Create a feature branch from `dev`
2. Make your changes, following existing code style
3. Ensure `just full` builds successfully
4. Run `just smoke <version>` on at least one Minecraft version
5. Open a PR against `dev` using the [PR template](https://github.com/langyo/minecraft-mod-mcp/blob/dev/.github/PULL_REQUEST_TEMPLATE.md)

PRs should target `dev`. The `master` branch receives periodic merges from `dev` (see [Commit Conventions](#commit-conventions)).

### Code Style

- **Java**: Follow standard conventions, use reflection utilities from `common/` for cross-version compatibility
- **TypeScript**: Run `npm run lint` in `packages/minecraft-mod-mcp/`
- **Python**: Follow PEP 8
- No commented-out code; no secret/credential in commits

Please check existing issues and PRs before opening a new one to avoid duplicates.

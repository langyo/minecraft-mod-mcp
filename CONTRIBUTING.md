# Contributing to Minecraft MCP

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

## Issues & Pull Requests

Issues and pull requests are welcome. Please check existing issues before opening a new one.

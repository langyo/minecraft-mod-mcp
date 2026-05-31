set windows-shell := ["pwsh", "-NoLogo", "-Command"]
set windows-powershell := true

base := absolute_path("")

# ============================================================
# Info
# ============================================================

# List all available MC versions
list:
    python scripts/jf.py list

# Show project status (mod JAR build state)
status:
    python scripts/jf.py status

# Environment check
check-env:
    python scripts/init.py --check-only

# ============================================================
# Build
# ============================================================

# Build all mod projects (with cache prep)
build *ARGS:
    python scripts/build_all.py {{ ARGS }}

# Build a single MC version
build-mod mc loader="forge":
    python scripts/build_all.py --mc {{ mc }} --loader {{ loader }} --no-cache -j 1

# Build mcp-common only
build-common:
    python scripts/build_common.py

# Prepare all caches
prepare-cache:
    python scripts/prepare_cache.py

# Full pipeline: generate + cache + build
full *ARGS:
    python scripts/generate_mods.py {{ ARGS }}
    python scripts/generate_sources.py {{ ARGS }}
    python scripts/prepare_cache.py
    python scripts/build_all.py {{ ARGS }}

# Generate mod projects for all versions
generate *ARGS:
    python scripts/generate_mods.py {{ ARGS }}
    python scripts/generate_sources.py {{ ARGS }}

# ============================================================
# Forge installation
# ============================================================

# Install Forge for all versions
install-forge *ARGS:
    python scripts/install_forge.py {{ ARGS }}

# Install Forge for a specific MC version
install-forge-version mc:
    python scripts/install_forge.py --mc {{ mc }}

# Build + install mod for a version
install-mod mc loader="forge":
    python scripts/jf.py install-mod {{ mc }} {{ loader }}

# ============================================================
# MC Launch (via mc_vtty daemon)
# ============================================================

# Start the VTTY daemon (WS + TCP servers)
daemon *ARGS:
    python scripts/mc_vtty.py {{ ARGS }}

# Send raw command to running daemon
send cmd:
    python scripts/mc_vtty.py --send '{{ cmd }}'

# Launch MC version via daemon (resolves short name to version_id)
# Usage: just launch 1.12.2         -> uses version_id from config
#        just launch 1.21.7 neoforge
launch mc loader="forge":
    python scripts/jf.py launch {{ mc }} {{ loader }}

# Take a screenshot via running daemon
snap name="manual":
    python scripts/jf.py snap {{ name }}

# List GUI buttons on current screen
buttons:
    python scripts/jf.py buttons

# Click a button by ID
click-id id:
    python scripts/jf.py click-id {{ id }}

# Send a chat command to MC
mc-command cmd:
    python scripts/jf.py mc-cmd {{ cmd }}

# Type text in MC
type-text text:
    python scripts/jf.py type-text {{ text }}

# Check daemon status
check:
    python scripts/jf.py check

# Kill MC process via daemon
kill:
    python scripts/jf.py kill

# ============================================================
# Testing
# ============================================================

# Smoke test a specific MC version (full E2E)
# Usage: just smoke 1.12.2
#        just smoke 1.21.7-forge-57.0.2
smoke mc *ARGS:
    python scripts/jf.py smoke {{ mc }} {{ ARGS }}

# Test a single version (build + launch + connect + screenshot)
test mc loader="forge":
    python scripts/test_version.py {{ mc }} --loader {{ loader }}

# Test all installed versions
test-all:
    python scripts/test_version.py --all

# Quick local smoke: install + launch + screenshot + kill (no daemon needed)
# Usage: just local-smoke 1.12.2
local-smoke mc loader="forge":
    python scripts/jf.py local-smoke {{ mc }} {{ loader }}

# ============================================================
# Direct MC launch (no daemon — blocks until MC exits)
# ============================================================

# Launch MC directly from version JSON (blocking)
# Usage: just run 1.12.2-forge1.12.2-14.23.5.2847
#        just run 1.21.7-forge-57.0.2 --mc-dir "C:\custom\.minecraft"
run version *ARGS:
    python scripts/launch_mc.py {{ version }} {{ ARGS }}

# Dry-run: print the launch command without executing
dry-run version:
    python scripts/launch_mc.py {{ version }} --dry-run

# ============================================================
# Utilities
# ============================================================

# ============================================================
# Release
# ============================================================

# Build all mods and publish JARs to GitHub Release
# Usage: just release v0.1.0
#        just release v0.1.0 --no-upload
#        just release v0.1.0 --loader forge --loader fabric
release tag *ARGS:
    python scripts/release.py {{ tag }} {{ ARGS }}

# ============================================================
# Cleanup
# ============================================================

# Show what would be deleted (dry-run)
clean-dry:
    python scripts/clean.py --dry-run

# Clean build caches and temp files (safe, no worlds)
clean:
    python scripts/clean.py

# Clean everything including test worlds (with confirmation)
clean-all:
    python scripts/clean.py --all

# Clean everything including worlds, no confirmation
clean-full:
    python scripts/clean.py --all --force

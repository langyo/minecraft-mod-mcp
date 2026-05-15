set windows-shell := ["pwsh", "-NoLogo", "-Command"]
set windows-powershell := true

base := absolute_path("")
mc-common-dir := base / "mcp-common"
mods-dir := base / "mods"
screenshots-dir := base / "screenshots"
mcp-jar := base / "build" / "libs" / "mcp-server-0.1.0.jar"
ws-port := env("MC_MCP_WS_PORT", "9876")
java-home := env("JAVA_HOME", "C:\\Program Files\\Amazon Corretto\\jdk21.0.8_9")
java := java-home + "\\bin\\java.exe"

# List all available MC versions
list:
    @python scripts/version_config.py 2> /dev/null || python -c "
import sys; sys.path.insert(0, 'scripts')
from version_config import ALL_VERSIONS, get_loaders
for mc in sorted(ALL_VERSIONS):
    loaders = get_loaders(mc)
    print(f'  {mc:10s} {\" \".join(loaders)}')
"

# Build MCP server (shadow jar)
build-server:
    gradlew.bat shadowJar --no-daemon

# Build mcp-common and publish to local maven
publish-common:
    cd mcp-common; ..\gradlew.bat publish --no-daemon

# Build all 76+ mod projects
build-all *ARGS:
    python scripts/build_all.py {{ ARGS }}

# Build a single mod version
build-mod mc loader="forge":
    python scripts/build_all.py --mc {{ mc }} --loader {{ loader }} --no-cache

# Prepare all caches
prepare-cache:
    python scripts/prepare_cache.py

# Generate mod projects for all versions
generate *ARGS:
    python scripts/generate_mods.py {{ ARGS }}
    python scripts/generate_sources.py {{ ARGS }}

# Start MCP server in foreground
server:
    {{ java }} -DMC_MCP_WS_PORT={{ ws-port }} -jar {{ mcp-jar }}

# Start MCP server in background (detached)
server-bg:
    start /B {{ java }} -DMC_MCP_WS_PORT={{ ws-port }} -jar {{ mcp-jar }}

# Stop MCP server
server-stop:
    -taskkill /FI "WINDOWTITLE eq mcp-server*" /F 2> $null; taskkill /FI "IMAGENAME eq java.exe" /FI "MEMUSAGE gt 100000" /F 2> $null

# Smoke test a specific MC version
smoke mc *ARGS:
    python scripts/smoke_test.py {{ mc }} {{ ARGS }}

# Smoke test without launching game (game already running)
smoke-no-launch mc:
    python scripts/smoke_test.py {{ mc }} --no-launch

# WS client for manual testing
ws *ARGS:
    python scripts/ws_client.py {{ ARGS }}

# Interactive WS session
ws-shell:
    python scripts/ws_client.py

# Take a screenshot via WS
screenshot path="":
    #!/usr/bin/env python
    import sys, os
    sys.path.insert(0, os.path.join(os.path.dirname("{{ base }}"), "scripts"))
    path = "{{ path }}" or f"screenshots/manual_{__import__('time').strftime('%H%M%S')}.png"
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    __import__('asyncio').run(__import__('importlib').import_module('ws_client').ws_send_action("screenshot", {"save_path": path}, path))

# Ping the game mod via WS
ping:
    python scripts/ws_client.py --action ping

# Full pipeline: generate + cache + build
full *ARGS:
    python scripts/generate_mods.py {{ ARGS }}
    python scripts/generate_sources.py {{ ARGS }}
    python scripts/prepare_cache.py
    python scripts/build_all.py {{ ARGS }}

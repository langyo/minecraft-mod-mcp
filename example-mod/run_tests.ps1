# Minecraft MCP Example Mod - Comprehensive Test Script
# Tests the full pipeline: MCP launch -> WS connection -> screenshot -> commands -> input

param(
    [int]$WsPort = 9879,
    [string]$ModPath = (Get-Location).Path,
    [string]$McRunDir = "$env:USERPROFILE\.mcbbs-memorial",
    [switch]$FullTest = $false
)

$ErrorActionPreference = "Stop"

function Write-Log($msg) {
    $ts = Get-Date -Format "HH:mm:ss.fff"
    Write-Host "[$ts] $msg" -ForegroundColor Cyan
}

function Send-McpRequest($id, $method, $params = @{}) {
    $body = @{
        jsonrpc = "2.0"
        id = $id
        method = $method
        params = $params
    } | ConvertTo-Json -Depth 5 -Compress
    Write-Log ">> [$id] $method"
    return $body
}

Write-Log "============================================"
Write-Log "  Minecraft MCP Example Mod Test Suite"
Write-Log "  Package: minecraft-mcp.langyo.xyz"
Write-Log "============================================"
Write-Log ""

$reqId = 1
$requests = @()

# Step 1: Initialize MCP connection
$requests += Send-McpRequest $reqId "initialize" @{}
$reqId++

# Step 2: List available tools
$requests += Send-McpRequest $reqId "tools/list" @{}
$reqId++

# Step 3: Check window info
$requests += Send-McpRequest $reqId "tools/call" @{ name = "get_window_info"; arguments = @{} }
$reqId++

if ($FullTest) {
    # Step 4: Launch game with example mod
    $requests += Send-McpRequest $reqId "tools/call" @{
        name = "launch_game"
        arguments = @{
            mod_jar_path = $ModPath
            mc_dir = $McRunDir
            max_memory_gb = 4
        }
    }
    $reqId++

    # Step 5: Wait for WebSocket connection from game
    $requests += Send-McpRequest $reqId "tools/call" @{
        name = "wait_for_screen"
        arguments = @{ timeout_seconds = 120 }
    }
    $reqId++

    # Step 6: Take screenshot via in-game mod
    $requests += Send-McpRequest $reqId "tools/call" @{
        name = "screenshot"
        arguments = @{ save_path = "$ModPath\screenshots\test_full.png" }
    }
    $reqId++

    # Step 7: Get player info
    $requests += Send-McpRequest $reqId "tools/call" @{
        name = "execute_command"
        arguments = @{ command = "gamerule doDayCycle false" }
    }
    $reqId++

    # Step 8: Set time to day
    $requests += Send-McpRequest $reqId "tools/call" @{
        name = "execute_command"
        arguments = @{ command = "time set 6000" }
    }
    $reqId++

    # Step 9: Give items to player
    $requests += Send-McpRequest $reqId "tools/call" @{
        name = "execute_command"
        arguments = @{ command = "give @p minecraft:diamond 64" }
    }
    $reqId++

    # Step 10: Simulate key press - open inventory (E)
    $requests += Send-McpRequest $reqId "tools/call" @{
        name = "press_key"
        arguments = @{ key = "e"; hold_seconds = 0.05 }
    }
    $reqId++

    # Step 11: Close inventory with ESC
    Start-Sleep -Milliseconds 500
    $requests += Send-McpRequest $reqId "tools/call" @{
        name = "press_key"
        arguments = @{ key = "escape"; hold_seconds = 0.05 }
    }
    $reqId++

    # Step 12: Type text into chat
    $requests += Send-McpRequest $reqId "tools/call" @{
        name = "type_text"
        arguments = @{ text = "[MCP Test] minecraft-mcp.langyo.xyz online!"; press_enter = $true }
    }
    $reqId++

    # Step 13: Hotkey - F3 for debug screen
    $requests += Send-McpRequest $reqId "tools/call" @{
        name = "hotkey"
        arguments = @{ keys = @("f3") }
    }
    $reqId++

    Start-Sleep -Milliseconds 1000

    # Step 14: Take another screenshot after actions
    $requests += Send-McpRequest $reqId "tools/call" @{
        name = "screenshot"
        arguments = @{ save_path = "$ModPath\screenshots\test_after_actions.png" }
    }
    $reqId++

    # Step 15: Scroll test
    $requests += Send-McpRequest $reqId "tools/call" @{
        name = "scroll"
        arguments = @{ clicks = 3 }
    }
    $reqId++

    # Step 16: Get player info from mod
    $requests += Send-McpRequest $reqId "tools/call" @{
        name = "get_player_info"
        arguments = @{}  # This would be handled by the mod's custom tool if registered
    }
    $reqId++
}

# Build the full request payload
$payload = ($requests -join "`n")

Write-Log ""
Write-Log "Generated $($requests.Count) MCP requests"
Write-Log "Port: $WsPort | FullTest: $FullTest"
Write-Log ""

# Execute via Java MCP server
$jarPath = "$ModPath\build\libs\mcp-server-0.1.0.jar"
if (-not (Test-Path $jarPath)) {
    Write-Log "Building MCP server first..."
    Push-Location $ModPath
    & .\gradlew.bat shadowJar 2>&1 | Select-Object -Last 5
    Pop-Location
}

Write-Log "Sending requests to MCP server..."
$env:MC_MCP_WS_PORT = $WsPort

$proc = New-Object System.Diagnostics.Process
$proc.StartInfo.FileName = "java"
$proc.StartInfo.Arguments = "-jar `"$jarPath`""
$proc.StartInfo.UseShellExecute = $false
$proc.StartInfo.RedirectStandardInput = $true
$proc.StartInfo.RedirectStandardOutput = $true
$proc.StartInfo.RedirectStandardError = $true
$proc.Start() | Out-Null

$proc.StandardInput.Write($payload)
$proc.StandardInput.Close()

$output = $proc.StandardOutput.ReadToEnd()
$errorOutput = $proc.StandardError.ReadToEnd()
$proc.WaitForExit(30000)

Write-Log ""
Write-Log "--- MCP Responses ---"
foreach ($line in ($output -split "`n" | Where-Object { $_.Trim() })) {
    try {
        $obj = $line | ConvertFrom-Json
        $mid = if ($obj.id) { "[$($obj.id)]" } else { "[sys]" }
        $method = ""
        if ($obj.result) { $method = "OK" }
        elseif ($obj.error) { $method = "ERR" }

        $color = if ($obj.error) { "Red" } else { "Green" }
        Write-Host "$mid $method" -NoNewline -ForegroundColor $color
        if ($obj.result -and $obj.result.content) {
            foreach ($c in $obj.result.content) {
                if ($c.type -eq "text") { Write-Host " : $($c.text)" -ForegroundColor $color }
                elseif ($c.type -eq "image") { Write-Host " : [image $((c.data.Length)) bytes]" -ForegroundColor Yellow }
            }
        } else {
            Write-Host ""
        }
    } catch {
        Write-Host $line -ForegroundColor Gray
    }
}

if ($errorOutput) {
    Write-Log ""
    Write-Log "--- STDERR ---"
    Write-Host $errorOutput -ForegroundColor DarkGray
}

Write-Log ""
Write-Log "Test complete. Exit code: $($proc.ExitCode)"

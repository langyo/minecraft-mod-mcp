# MCP End-to-End Integration Test
# Launches MC with mcp-mod + test-example-mod, runs full test suite via WebSocket

param(
    [int]$WsPort = 9879,
    [string]$ProjectRoot = (Get-Location).Path,
    [switch]$SkipLaunch = $false,
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"

function Write-Test($msg) { Write-Host "  [$((Get-Date).ToString('HH:mm:ss'))] $msg" -ForegroundColor Yellow }
function Write-Pass($msg) { Write-Host "  PASS: $msg" -ForegroundColor Green }
function Write-Fail($msg) { Write-Host "  FAIL: $msg" -ForegroundColor Red }
function Write-Skip($msg) { Write-Host "  SKIP: $msg" -ForegroundColor DarkGray }

$jarPath = "$ProjectRoot\build\libs\mcp-server-0.1.0.jar"
if (-not (Test-Path $jarPath)) {
    Write-Host "Building MCP server..." -ForegroundColor Cyan
    Push-Location $ProjectRoot
    & .\gradlew.bat shadowJar --quiet 2>&1 | Out-Null
    Pop-Location
}

$testResults = @()
$totalTests = 0
$passedTests = 0
$failedTests = 0

function Send-McpBatch($requests, $description) {
    Write-Test "--- $description ---"
    $payload = ($requests -join "`n")
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

    Start-Sleep -Milliseconds ($requests.Count * 500 + 2000)
    $output = $proc.StandardOutput.ReadToEnd()
    $proc.WaitForExit(10000) | Out-Null

    $results = @()
    foreach ($line in ($output -split "`n" | Where-Object { $_.Trim() -match '"result"|"error"' })) {
        try {
            $obj = $line | ConvertFrom-Json
            $results += $obj
        } catch {}
    }
    return $results
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Minecraft MCP E2E Test Suite" -ForegroundColor Cyan
Write-Host "  Mods: minecraft-mcp.langyo.xyz + test-example" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# Phase 1: Initialize
Write-Host "[Phase 1] MCP Server Initialization" -ForegroundColor Magenta
$initResults = Send-McpBatch @(
    '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
    '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
) "Initialize & List Tools"

$initOk = $false
foreach ($r in $initResults) {
    if ($r.id -eq 1 -and $r.result.protocolVersion) {
        Write-Pass "MCP protocol: $($r.result.protocolVersion)"
        $initOk = $true
        $totalTests++; $passedTests++
    }
    if ($r.id -eq 2 -and $r.result.tools) {
        $toolCount = $r.result.tools.Count
        Write-Pass "Tools registered: $toolCount"
        $totalTests++; $passedTests++
    }
}
if (-not $initOk) { Write-Fail "Init failed"; $failedTests++ }

# Phase 2: Connection check
Write-Host ""
Write-Host "[Phase 2] Connection Status" -ForegroundColor Magenta
$connResults = Send-McpBatch @(
    '{"jsonrpc":"2.0","id":10,"method":"tools/call","params":{"name":"get_window_info","arguments":{}}}'
) "Check WS Connection"

$mcConnected = $false
foreach ($r in $connResults) {
    if ($r.id -eq 10) {
        $text = ""
        if ($r.result.content) { $text = $r.result.content[0].text }
        if ($text -match '"mcConnected":true') {
            Write-Pass "MC Mod connected via WebSocket"
            $mcConnected = $true
            $totalTests++; $passedTests++
        } elseif ($text -match '"mcConnected":false') {
            Write-Fail "MC not connected - is game running with mod?"
            $mcConnected = $false
            $totalTests++; $failedTests++
        } else {
            Write-Fail "Unexpected response: $text"
            $totalTests++; $failedTests++
        }
    }
}

if (-not $mcConnected) {
    Write-Host ""
    Write-Host "MC not connected. Test suite requires running game." -ForegroundColor Red
    Write-Host "To run full test:" -ForegroundColor White
    Write-Host "  1. Launch MC with both mods (mcp-mod + test-example-mod)" -ForegroundColor White
    Write-Host "  2. Re-run this script" -ForegroundColor White
    exit 1
}

# Phase 3: Screenshot test
Write-Host ""
Write-Host "[Phase 3] Screenshot Pipeline" -ForegroundColor Magenta
$ssResults = Send-McpBatch @(
    '{"jsonrpc":"2.0","id":20,"method":"tools/call","params":{"name":"screenshot","arguments":{"save_path":"'$ProjectRoot'\screenshots\e2e_test.png"}}}'
) "Screenshot Capture"

foreach ($r in $ssResults) {
    if ($r.id -eq 20) {
        if ($r.result.content -and $r.result.content[0].type -eq "image") {
            $size = [math]::Round($r.result.content[0].data.Length / 1024)
            Write-Pass "Screenshot captured: ${size}KB base64"
            $totalTests++; $passedTests++
        } else {
            $txt = if ($r.result.content) { $r.result.content[0].text } else { "no content" }
            Write-Fail "Screenshot failed: $txt"
            $totalTests++; $failedTests++
        }
    }
}

# Phase 4: Command execution tests
Write-Host ""
Write-Host "[Phase 4] In-Game Commands" -ForegroundColor Magenta
$cmdResults = Send-McpBatch @(
    '{"jsonrpc":"2.0","id":30,"method":"tools/call","params":{"name":"execute_command","arguments":{"command":"mctest"}}}'
    '{"jsonrpc":"2.0","id":31,"method":"tools/call","params":{"name":"execute_command","arguments":{"command":"mcgive diamond"}}}'
    '{"jsonrpc":"2.0","id":32,"method":"tools/call","params":{"name":"execute_command","arguments":{"command":"mcinfo"}}}'
    '{"jsonrpc":"2.0","id":33,"method":"tools/call","params":{"name":"execute_command","arguments":{"command":"gamerule doDayCycle false"}}}'
    '{"jsonrpc":"2.0","id":34,"method":"tools/call","params":{"name":"execute_command","arguments":{"command":"time set 6000"}}}'
) "Command Execution"

$expectedCmds = @{
    30 = "mctest help"
    31 = "give diamond"
    32 = "player info"
    33 = "gamerule set"
    34 = "time set day"
}
foreach ($r in $cmdResults) {
    $id = $r.id
    if ($expectedCmds.ContainsKey($id)) {
        $desc = $expectedCmds[$id]
        $txt = if ($r.result.content) { $r.result.content[0].text } else { "" }
        if ($txt -notmatch "Error|error|fail") {
            Write-Pass "Command '$desc': OK"
            $totalTests++; $passedTests++
        } else {
            Write-Fail "Command '$desc': $txt"
            $totalTests++; $failedTests++
        }
    }
}

# Phase 5: Input simulation tests
Write-Host ""
Write-Host "[Phase 5] Input Simulation (via mod)" -ForegroundColor Magenta
$inputResults = Send-McpBatch @(
    '{"jsonrpc":"2.0","id":40,"method":"tools/call","params":{"name":"press_key","arguments":{"key":"e"}}}'
    '{"jsonrpc":"2.0","id":41,"method":"tools/call","params":{"name":"press_key","arguments":{"key":"escape"}}}'
    '{"jsonrpc":"2.0","id":42,"method":"tools/call","params":{"name":"type_text","arguments":{"text":"/mcinfo"}}}'
    '{"jsonrpc":"2.0","id":43,"method":"tools/call","params":{"name":"scroll","arguments":{"clicks":-3}}}'
    '{"jsonrpc":"2.0","id":44,"method":"tools/call","params":{"name":"hotkey","arguments":{"keys":["f3"]}}}'
) "Input Simulation"

$inputTests = @{
    40 = "press_key(e) - open inventory"
    41 = "press_key(esc) - close GUI"
    42 = "type_text(/mcinfo)"
    43 = "scroll(-3)"
    44 = "hotkey(F3 debug)"
}
Start-Sleep -Milliseconds 3000
foreach ($r in $inputResults) {
    $id = $r.id
    if ($inputTests.ContainsKey($id)) {
        $desc = $inputTests[$id]
        $txt = if ($r.result.content) { $r.result.content[0].text } else { "" }
        if ($txt -notmatch "Error|error|fail|timeout") {
            Write-Pass "$desc : OK"
            $totalTests++; $passedTests++
        } else {
            Write-Fail "$desc : $txt"
            $totalTests++; $failedTests++
        }
    }
}

# Phase 6: Player/World info
Write-Host ""
Write-Host "[Phase 6] State Queries" -ForegroundColor Magenta
$infoResults = Send-McpBatch @(
    '{"jsonrpc":"2.0","id":50,"method":"tools/call","params":{"name":"get_player_info","arguments":{}}}'
    '{"jsonrpc":"2.0","id":51,"method":"tools/call","params":{"name":"get_world_info","arguments":{}}}'
    '{"jsonrpc":"2.0","id":52,"method":"tools/call","params":{"name":"ping","arguments":{}}}'
) "State Queries"

foreach ($r in $infoResults) {
    $id = $r.id
    $txt = if ($r.result.content) { $r.result.content[0].text } else { "" }
    switch ($id) {
        50 {
            if ($txt -match "position|health|dimension") { Write-Pass "Player info: valid JSON"; $passedTests++ }
            else { Write-Fail "Player info: $txt"; $failedTests++ }
            $totalTests++
        }
        51 {
            if ($txt -match "worldName|dayTime|difficulty") { Write-Pass "World info: valid JSON"; $passedTests++ }
            else { Write-Fail "World info: $txt"; $failedTests++ }
            $totalTests++
        }
        52 {
            if ($txt -match "pong|timestamp") { Write-Pass "Ping: responsive"; $passedTests++ }
            else { Write-Fail "Ping: $txt"; $failedTests++ }
            $totalTests++
        }
    }
}

# Summary
Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  TEST SUMMARY" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Total:  $totalTests" -ForegroundColor White
Write-Host "  Passed: $passedTests" -ForegroundColor Green
Write-Host "  Failed: $failedTests" -ForegroundColor $(if ($failedTests -gt 0) { "Red" } else { "Green" })
$rate = if ($totalTests -gt 0) { [math]::Round($passedTests / $totalTests * 100) } else { 0 }
Write-Host "  Rate:   ${rate}%" -ForegroundColor $(if ($rate -ge 90) { "Green" } elseif ($rate -ge 50) { "Yellow" } else { "Red" })
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

exit $(if ($failedTests -gt 0) { 1 } else { 0 })

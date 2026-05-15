import subprocess, json, time, threading, sys

server_jar = r'D:\源代码\工程项目\2026TeaCon\minecraft-neoforge-mcp\build\libs\mcp-server-0.1.0.jar'
java = r'C:\Program Files\Amazon Corretto\jdk21.0.8_9\bin\java.exe'

results = {}

def send_and_wait(srv, tool, args, rid=1):
    cmd = json.dumps({"jsonrpc":"2.0","id":rid,"method":"tools/call","params":{"name":tool,"arguments":args}})
    print(f'  >>> {cmd}')
    srv.stdin.write(cmd + '\n')
    srv.stdin.flush()
    time.sleep(8)

print('[E2E] Starting MCP server...')
server = subprocess.Popen(
    [java, '-jar', server_jar],
    stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
    text=True, bufsize=1
)

def reader():
    for line in server.stdout:
        print(f'  [SRV] {line}', end='', flush=True)
threading.Thread(target=reader, daemon=True).start()
time.sleep(5)

print('[E2E] Launching MC...')
mc = subprocess.Popen(
    [sys.executable, 'scripts/launch_mc.py', '1.21.7-forge-57.0.2'],
    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1
)

def mc_reader():
    for line in mc.stdout:
        print(f'  [MC] {line}', end='', flush=True)
threading.Thread(target=mc_reader, daemon=True).start()

print('[E2E] Waiting for MC to connect (60s)...')
time.sleep(60)

print('[E2E] Sending ping...')
send_and_wait(server, 'ping', {}, rid=1)

print('[E2E] Sending get_window_info...')
send_and_wait(server, 'get_window_info', {}, rid=2)

print('[E2E] Sending screenshot...')
send_and_wait(server, 'screenshot', {}, rid=3)

print('[E2E] Done! Keeping alive 10s...')
time.sleep(10)

print('[E2E] Cleaning up...')
try:
    server.stdin.close()
except:
    pass
mc.terminate()
server.terminate()
print('[E2E] Test complete.')

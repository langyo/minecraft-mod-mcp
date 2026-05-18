"""Extract enumerate_widgets JSON from run.py output"""
import sys, os, time, json, re
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'scripts'))
from test_version import kill_all_java, _start_mcp_server, _send_server_cmd, _start_mc, clear_mods, install_mod

kill_all_java(); time.sleep(2)
clear_mods(); install_mod('1.21.7-forge-57.0.2', 'forge')
srv = _start_mcp_server(); time.sleep(3)
mc = _start_mc('1.21.7-forge-57.0.2')
for i in range(20):
    time.sleep(3)
    log = os.path.join(os.environ.get('APPDATA', ''), '.minecraft', 'mcp-launch-stdout.log')
    if os.path.exists(log):
        try:
            if 'MCP-WS' in open(log, encoding='utf-8', errors='replace').read():
                break
        except: pass

# Click singleplayer
_send_server_cmd(srv, 'click_button_index', {'index': 0})
time.sleep(3)

# Enumerate widgets
r = _send_server_cmd(srv, 'enumerate_widgets', {})
time.sleep(2)

# Extract JSON from response
txt = str(r)
if isinstance(r, dict) and 'result' in r:
    content = r['result'].get('content', [])
    if content:
        txt = content[0].get('text', str(r))

data = json.loads(txt)
print(f"Screen: {data.get('screen')}")
print(f"Total widgets: {data.get('total')}")
print(f"\n{'Idx':>4} {'Class':<25} {'X':>4} {'Y':>4} {'W':>4} {'H':>4} {'Press'}")
print("-" * 60)
for w in data.get('widgets', []):
    print(f"{w['i']:>4} {w['c']:<25} {w['x']:>4} {w['y']:>4} {w['w']:>4} {w['h']:>4} {str(w['press']):>5}")

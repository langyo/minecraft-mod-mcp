"""Quick test: get button list on SelectWorldScreen"""
import sys, os, time, json
sys.path.insert(0, os.path.dirname(__file__))
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

# Click singleplayer (btn 0 on TitleScreen)
r = _send_server_cmd(srv, 'click_button_id', {'button_id': 0})
time.sleep(3)
print('SINGLEPLAYER:', r)

# Get full buttons JSON
r = _send_server_cmd(srv, 'get_screen_buttons', {})
time.sleep(2)
txt = str(r)
if isinstance(r, dict) and 'result' in r:
    content = r['result'].get('content', [])
    if content:
        txt = content[0].get('text', str(r))
data = json.loads(txt)
print('SCREEN:', data.get('screen'))
btns = data.get('buttons', [])
for i, b in enumerate(btns):
    bid = b.get('id')
    bx = b.get('x')
    by = b.get('y')
    bw = b.get('w')
    bh = b.get('h')
    lbl = b.get('label', '')
    print(f"  [{i}] id={bid} pos=({bx},{by}) size={bw}x{bh} label={lbl}")

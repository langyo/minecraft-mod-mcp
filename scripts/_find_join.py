"""List methods on SelectWorldScreen and find the one to join world"""
import sys, os, time, json
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

# List all methods
r = _send_server_cmd(srv, 'call_screen_method', {'method': '__list__'})
time.sleep(3)

# r is the request ID string, not the response. We need to read from the server's callback.
# Let's just print what we got and also try common method names.
print(f"Raw response: {r}")

# Try each candidate method
for mname in ['joinSelectedWorld', 'join', 'enterWorld', 'loadAndPlay', 'play', 
               'confirmSelection', 'selectWorld', 'openScreen', 'start',
               'init', 'tick', 'updateButtonStatus']:
    r2 = _send_server_cmd(srv, 'call_screen_method', {'method': mname})
    time.sleep(1)
    txt = str(r2)
    if isinstance(r2, dict) and 'result' in r2:
        content = r2['result'].get('content', [])
        if content:
            txt = content[0].get('text', str(r2))
    if '"called":true' in txt or '"error"' not in txt.lower():
        print(f"*** SUCCESS: {mname} -> {txt}")
    else:
        # Only show if interesting
        if 'candidates' in txt and '[' in txt.split('candidates')[1][:10]:
            print(f"  {mname}: (not found)")

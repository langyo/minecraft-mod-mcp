import sys, os, time, hashlib
sys.path.insert(0, 'scripts')
from test_version import kill_all_java, _start_mcp_server, _send_server_cmd, _start_mc, clear_mods, install_mod
from pathlib import Path

kill_all_java(); time.sleep(2)
clear_mods(); install_mod('1.21.7-forge-57.0.2', 'forge')
srv = _start_mcp_server(); time.sleep(3)
mc = _start_mc('1.21.7-forge-57.0.2')
time.sleep(18)

ss_dir = Path('screenshots/final_test')
ss_dir.mkdir(exist_ok=True)

def ss(name):
    p = str(ss_dir / name)
    _send_server_cmd(srv, 'screenshot', {'save_path': p})
    time.sleep(6)
    for f in sorted(ss_dir.glob(name + '*'), key=lambda f: f.stat().st_mtime, reverse=True):
        if 0 < (time.time() - f.stat().st_mtime) < 15:
            d = f.read_bytes()
            h = hashlib.md5(d).hexdigest()[:12]
            print('  %s  %dKB  md5=%s' % (f.name, len(d)//1024, h))
            return h, len(d)
    return None, 0

print('=== Step 1: Baseline ===')
h1, sz1 = ss('01_baseline.png')
time.sleep(2)

print('\n=== Step 2: Enter control mode ===')
_send_server_cmd(srv, 'enter_control_mode', {})
time.sleep(2)
h2, sz2 = ss('02_ctrl_on.png')
print('  ctrl_on hash diff from baseline: %s' % ('DIFFERENT!' if h1 != h2 else 'SAME'))

print('\n=== Step 3: Click Singleplayer ===')
_send_server_cmd(srv, 'click', {'x': 426, 'y': 236})
print('  clicked (426,236)')
time.sleep(6)
h3, sz3 = ss('03_after_click.png')
print('  after_click vs baseline: %s' % ('DIFFERENT!' if h1 != h3 else 'SAME'))

print('\n=== Step 4: Enum widgets ===')
_send_server_cmd(srv, 'enumerate_widgets', {})
time.sleep(2)
h4, sz4 = ss('04_after_enum.png')

print('\n=== Step 5: Click world list ===')
_send_server_cmd(srv, 'click', {'x': 426, 'y': 200})
print('  clicked (426,200)')
time.sleep(8)
h5, sz5 = ss('05_after_world_click.png')
print('  world_click vs baseline: %s' % ('DIFFERENT!' if h1 != h5 else 'SAME'))

print('\n=== Step 6: Wait more + screenshot ===')
time.sleep(10)
h6, sz6 = ss('06_long_wait.png')
print('  long_wait vs baseline: %s' % ('DIFFERENT!' if h1 != h6 else 'SAME'))

print('\n=== Results ===')
all_h = [h1, h2, h3, h4, h5, h6]
unique = len(set(all_h))
print('Unique hashes: %d/6' % unique)
for i, h in enumerate(all_h):
    print('  [%d] %s' % (i+1, h))

# Check debug log for native error
user_home = os.path.expanduser('~')
log_path = os.path.join(user_home, 'mcp_debug.log')
if os.path.exists(log_path):
    lines = open(log_path, encoding='utf-8', errors='replace').readlines()
    err_lines = [l.strip() for l in lines if 'MCP-Native' in l or 'CONSUMER ERROR' in l or 'root cause' in l]
    if err_lines:
        print('\n=== Native Screenshot Errors ===')
        for el in err_lines[-10:]:
            print('  %s' % el)

_send_server_cmd(srv, 'exit_control_mode', {})
srv.kill(); mc.kill(); kill_all_java()

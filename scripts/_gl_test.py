import sys, os, time, hashlib
sys.path.insert(0, 'scripts')
from test_version import kill_all_java, _start_mcp_server, _send_server_cmd, _start_mc, clear_mods, install_mod
from pathlib import Path

kill_all_java(); time.sleep(2)
clear_mods(); install_mod('1.21.7-forge-57.0.2', 'forge')
srv = _start_mcp_server(); time.sleep(3)
mc = _start_mc('1.21.7-forge-57.0.2')
time.sleep(18)
print('MC loaded, taking 3 screenshots...')

ss_dir = Path('screenshots/gl_test')
ss_dir.mkdir(exist_ok=True)
hashes = []
for i in range(3):
    p = str(ss_dir / ('gl_%d.png' % i))
    _send_server_cmd(srv, 'screenshot', {'save_path': p})
    time.sleep(6)
    for f in sorted(ss_dir.glob('gl_%d_*' % i), key=lambda f: f.stat().st_mtime, reverse=True):
        if 0 < (time.time() - f.stat().st_mtime) < 15:
            d = f.read_bytes()
            h = hashlib.md5(d).hexdigest()[:12]
            hashes.append(h)
            sz = len(d) // 1024
            print('  [%d] %s %dKB md5=%s' % (i, f.name, sz, h))
            break
    time.sleep(3)

unique = len(set(hashes))
status = "GL WORKS - frames differ!" if unique > 1 else "STILL CACHED - all identical"
print('\nUnique: %d/3 - %s' % (unique, status))

_send_server_cmd(srv, 'exit_control_mode', {})
srv.kill()
mc.kill()
kill_all_java()

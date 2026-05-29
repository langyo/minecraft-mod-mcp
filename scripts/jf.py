"""Justfile helper — all justfile recipe logic lives here.

Usage: python scripts/jf.py <subcommand> [args...]

Subcommands:
  list              List all MC versions
  status            Show mod JAR build status
  launch <mc> [loader]   Launch MC version via daemon
  snap [name]       Take screenshot via daemon
  buttons           List GUI buttons on current screen
  click-id <id>     Click button by ID
  mc-cmd <cmd>      Send chat command to MC
  type-text <text>  Type text in MC
  check             Check daemon status
  kill              Kill MC via daemon
  smoke <mc> [args...]   Smoke test
  local-smoke <mc> [loader]  Quick local smoke test
  install-mod <mc> [loader]  Build + install mod for version
"""

import json
import sys
import os
import time
import glob

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from version_config import ALL_VERSIONS, get_loaders


def resolve_version_id(mc):
    info = ALL_VERSIONS.get(mc)
    if info:
        return info.get("version_id", mc)
    return mc


def send_tcp(cmd_dict, port=9877, timeout=120):
    import socket
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(timeout)
    try:
        sock.connect(("127.0.0.1", port))
        sock.sendall((json.dumps(cmd_dict) + "\n").encode())
        buf = b""
        while True:
            data = sock.recv(65536)
            if not data:
                break
            buf += data
            if b"\n" in buf:
                line, _ = buf.split(b"\n", 1)
                return json.loads(line)
    except (ConnectionRefusedError, socket.timeout, OSError) as e:
        return {"error": str(e)}
    finally:
        try:
            sock.close()
        except Exception:
            pass
    return {"error": "no response"}


def cmd_list(args):
    for mc in sorted(ALL_VERSIONS):
        loaders = get_loaders(mc)
        print(f"  {mc:10s} {' '.join(loaders)}")


def cmd_status(args):
    print("=== Mod JARs ===")
    base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    for mc in sorted(ALL_VERSIONS):
        for loader in get_loaders(mc):
            jars = glob.glob(os.path.join(base, "packages", "mods", mc, loader, "build", "libs", "*.jar"))
            jars = [j for j in jars if "sources" not in j and "javadoc" not in j]
            if jars:
                sz = os.path.getsize(jars[0]) // 1024
                print(f"  {mc:10s} {loader:8s} {sz:6d}KB")
            else:
                print(f"  {mc:10s} {loader:8s}   --- not built ---")


def cmd_launch(args):
    mc = args[0] if args else "1.21.7"
    loader = args[1] if len(args) > 1 else "forge"
    vid = resolve_version_id(mc)
    r = send_tcp({"cmd": "launch", "version": vid, "loader": loader})
    print(json.dumps(r, indent=2, ensure_ascii=False))


def cmd_snap(args):
    name = args[0] if args else "manual"
    r = send_tcp({"cmd": "screenshot", "name": name})
    print(json.dumps(r, indent=2, ensure_ascii=False))


def cmd_buttons(args):
    r = send_tcp({"cmd": "screen_buttons"})
    print(json.dumps(r, indent=2, ensure_ascii=False))


def cmd_click_id(args):
    bid = int(args[0]) if args else 0
    r = send_tcp({"cmd": "click_button_id", "id": bid})
    print(json.dumps(r, indent=2, ensure_ascii=False))


def cmd_mc_cmd(args):
    cmd = " ".join(args) if args else ""
    r = send_tcp({"cmd": "command", "command": cmd})
    print(json.dumps(r, indent=2, ensure_ascii=False))


def cmd_type_text(args):
    text = " ".join(args) if args else ""
    r = send_tcp({"cmd": "type_text", "text": text})
    print(json.dumps(r, indent=2, ensure_ascii=False))


def cmd_check(args):
    r = send_tcp({"cmd": "status"})
    print(json.dumps(r, indent=2, ensure_ascii=False))


def cmd_kill(args):
    r = send_tcp({"cmd": "kill"})
    print(json.dumps(r, indent=2, ensure_ascii=False))


def cmd_smoke(args):
    mc = args[0] if args else "1.21.7"
    vid = resolve_version_id(mc)
    import subprocess
    script = os.path.join(os.path.dirname(os.path.abspath(__file__)), "smoke_test.py")
    r = subprocess.run([sys.executable, script, vid] + args[1:])
    sys.exit(r.returncode)


def cmd_local_smoke(args):
    mc = args[0] if args else "1.12.2"
    loader = args[1] if len(args) > 1 else "forge"
    vid = resolve_version_id(mc)

    from test_version import clear_mods, install_mod, kill_all_java
    from mc_vtty import McVtty

    print(f"[LOCAL-SMOKE] {vid}/{loader}")
    kill_all_java()

    v = McVtty()
    v.start_ws_server()

    print("[1/4] Installing mod...")
    install_mod(mc, loader)
    time.sleep(1)

    print("[2/4] Launching MC...")
    r = v.launch(vid, loader)
    if "error" in r:
        print(f"  FAIL: {r}")
        v.kill()
        sys.exit(1)
    print(f"  OK: pid={r.get('pid')} ws={r.get('ws')}")

    print("[3/4] Taking screenshot...")
    time.sleep(5)
    r = v.screenshot("local_smoke")
    if "path" in r:
        print(f"  OK: {r['path']} ({r['size']//1024}KB)")
    else:
        print(f"  FAIL: {r}")

    print("[4/4] Done, killing MC...")
    v.kill()
    print("[LOCAL-SMOKE] Complete")


def cmd_install_mod(args):
    mc = args[0] if args else "1.12.2"
    loader = args[1] if len(args) > 1 else "forge"
    from test_version import clear_mods, install_mod, find_mod_jar
    jar = find_mod_jar(mc, loader)
    if not jar:
        print(f"No mod JAR found for {mc}/{loader}")
        sys.exit(1)
    clear_mods()
    ok = install_mod(mc, loader)
    print(f"{'OK' if ok else 'FAIL'}: {jar.name} -> mods/")


COMMANDS = {
    "list": cmd_list,
    "status": cmd_status,
    "launch": cmd_launch,
    "snap": cmd_snap,
    "buttons": cmd_buttons,
    "click-id": cmd_click_id,
    "mc-cmd": cmd_mc_cmd,
    "type-text": cmd_type_text,
    "check": cmd_check,
    "kill": cmd_kill,
    "smoke": cmd_smoke,
    "local-smoke": cmd_local_smoke,
    "install-mod": cmd_install_mod,
}


def main():
    if len(sys.argv) < 2 or sys.argv[1] not in COMMANDS:
        print(__doc__)
        sys.exit(1)
    cmd = sys.argv[1]
    COMMANDS[cmd](sys.argv[2:])


if __name__ == "__main__":
    main()

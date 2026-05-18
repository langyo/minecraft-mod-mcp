"""One-shot: start server, wait for mod, do ONE action, screenshot, exit.
Usage:
  python scripts/shot.py ss name          # just screenshot
  python scripts/shot.py click 512 200    # click then screenshot
  python scripts/shot.py key Escape       # press key then screenshot  
  python scripts/shot.py paste "hello"    # paste text then screenshot
  python scripts/shot.py launch           # only launch MC (no action)
"""
import sys, os, time, json, subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
MC_DIR = Path(os.environ.get("APPDATA", os.path.expanduser("~"))) / ".minecraft"
SS_DIR = ROOT / "screenshots" / "debug"
SS_DIR.mkdir(parents=True, exist_ok=True)

from test_version import kill_all_java, _start_mcp_server, _send_server_cmd, _start_mc, clear_mods, install_mod, find_mod_jar


def do_action(srv, parts):
    if not parts or parts[0] == "ss":
        name = parts[1] if len(parts) > 1 else "shot"
    elif parts[0] == "click":
        x, y = int(parts[1]), int(parts[2])
        _send_server_cmd(srv, "click", {"x": x, "y": y}); time.sleep(1)
        name = f"click_{x}_{y}"
    elif parts[0] == "key":
        k = parts[1]
        _send_server_cmd(srv, "press_key", {"key": k}); time.sleep(1)
        name = f"key_{k}"
    elif parts[0] == "paste":
        t = " ".join(parts[1:])
        _send_server_cmd(srv, "paste_text", {"text": t}); time.sleep(1)
        name = f"paste"
    elif parts[0] == "scroll":
        n = int(parts[1])
        _send_server_cmd(srv, "scroll", {"clicks": n}); time.sleep(0.5)
        name = f"scroll_{n}"
    else:
        print(f"Unknown action: {parts[0]}"); return None

    ts = int(time.time())
    path = str(SS_DIR / f"{name}_{ts}.png")
    _send_server_cmd(srv, "screenshot", {"save_path": path})
    time.sleep(5)

    for f in sorted(SS_DIR.glob(f"{name}_*.png"), key=os.path.getmtime, reverse=True):
        if time.time() - os.path.getmtime(f) < 15:
            p = str(f)
            print(f"SS: {p} ({os.path.getsize(p)//1024}KB)")
            return p
    print("SS: FAIL")
    return None


def main():
    args = sys.argv[1:]
    version = "1.21.7-forge-57.0.2"

    srv = _start_mcp_server()
    time.sleep(2)

    if args and args[0] == "launch":
        kill_all_java(); time.sleep(2)
        clear_mods()
        install_mod(version, "forge")
        mc = _start_mc(version)
        print(f"MC_LAUNCHED pid={mc.pid}")
        # Wait for connection
        deadline = time.time() + 120
        while time.time() < deadline:
            if mc.poll():
                print("MC_EXITED early"); return
            log = MC_DIR / "mcp-launch-stdout.log"
            if log.exists():
                try:
                    if "MCP-WS" in log.read_text(encoding="utf-8", errors="replace"):
                        print("CONNECTED"); break
                except: pass
            time.sleep(3)
        else:
            print("TIMEOUT waiting for mod"); return
        time.sleep(3)
        # Take initial screenshot
        do_action(srv, ["ss", "after_launch"])
        return

    # For non-launch commands, wait briefly for existing mod to reconnect
    print("Waiting for mod reconnect...")
    time.sleep(8)

    do_action(srv, args)

    try: srv.stdin.close()
    except: pass
    srv.kill()


if __name__ == "__main__":
    main()

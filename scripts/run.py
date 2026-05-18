"""Run a sequence of MCP actions with a single persistent server.
Usage:
  python scripts/run.py "ss start" "click 512 215" "ss after_sp" "click 512 350" "ss after_cw"
Each arg is "ACTION [ARGS]". Actions: ss, click, key, paste, scroll, wait.
Screenshots auto-saved. Server stays alive for entire sequence.
"""
import sys, os, time, json, subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
MC_DIR = Path(os.environ.get("APPDATA", os.path.expanduser("~"))) / ".minecraft"
SS_DIR = ROOT / "screenshots" / "debug"
SS_DIR.mkdir(parents=True, exist_ok=True)

from test_version import kill_all_java, _start_mcp_server, _send_server_cmd, _start_mc, clear_mods, install_mod


def do(srv, cmd_str):
    parts = cmd_str.strip().split(None, 1)
    if not parts: return
    cmd = parts[0].lower()
    rest = parts[1] if len(parts) > 1 else ""

    if cmd == "ss":
        name = rest or "shot"
        ts = int(time.time())
        path = str(SS_DIR / f"{name}_{ts}.png")
        _send_server_cmd(srv, "screenshot", {"save_path": path})
        time.sleep(6)
        found = False
        # Check both requested path AND server's default store location
        candidates = []
        for pattern in [f"{name}_*", "mc_*"]:
            for d in [SS_DIR, ROOT / "screenshots"]:
                candidates.extend(d.glob(f"{pattern}.png"))
        candidates.sort(key=os.path.getmtime, reverse=True)
        for f in candidates:
            age = time.time() - os.path.getmtime(f)
            if 0 < age < 20:
                print(f"  SS {name} -> {f} ({os.path.getsize(f)//1024}KB)")
                return str(f)
        print(f"  SS {name} -> FAIL (no recent png)"); return None

    elif cmd == "click":
        xy = rest.split()
        x, y = int(xy[0]), int(xy[1])
        _send_server_cmd(srv, "click", {"x": x, "y": y})
        print(f"  CLICK ({x},{y})"); time.sleep(2)

    elif cmd == "key":
        _send_server_cmd(srv, "press_key", {"key": rest})
        print(f"  KEY {rest}"); time.sleep(1.5)

    elif cmd == "paste":
        _send_server_cmd(srv, "paste_text", {"text": rest})
        print(f"  PASTE {rest!r}"); time.sleep(1.5)

    elif cmd == "scroll":
        n = int(rest)
        _send_server_cmd(srv, "scroll", {"clicks": n})
        print(f"  SCROLL {n}"); time.sleep(1)

    elif cmd == "execute_command" or cmd == "cmd":
        _send_server_cmd(srv, "execute_command", {"command": rest})
        print(f"  CMD {rest}"); time.sleep(2)

    elif cmd == "wait":
        t = float(rest) if rest else 2
        print(f"  WAIT {t}s"); time.sleep(t)

    else:
        print(f"  UNKNOWN: {cmd}")


def main():
    ACTIONS = {"ss", "click", "key", "paste", "scroll", "wait", "execute_command", "cmd"}
    args = sys.argv[1:]
    # First arg is version if its first word is not an action
    if args and args[0].split(None, 1)[0] not in ACTIONS and not args[0].startswith("-"):
        version = args.pop(0)
    else:
        version = "1.21.7-forge-57.0.2"
    actions = args

    if not actions:
        actions = ["ss start"]

    print(f"=== RUN: {len(actions)} actions ===")

    kill_all_java(); time.sleep(2)
    clear_mods(); install_mod(version, "forge")
    srv = _start_mcp_server(); time.sleep(3)
    mc = _start_mc(version)
    print(f"MC pid={mc.pid}")

    deadline = time.time() + 120
    while time.time() < deadline:
        if mc.poll(): print("MC EXITED"); return
        log = MC_DIR / "mcp-launch-stdout.log"
        if log.exists():
            try:
                if "MCP-WS" in log.read_text(encoding="utf-8", errors="replace"):
                    print("CONNECTED!"); break
            except: pass
        time.sleep(3)
    else:
        print("TIMEOUT"); return
    time.sleep(5)

    for i, act in enumerate(actions):
        print(f"\n[{i+1}/{len(actions)}] {act}")
        do(srv, act)

    try: srv.stdin.close()
    except: pass
    srv.kill()
    if mc.poll() is None: mc.kill()
    kill_all_java()
    print("\nDONE")


if __name__ == "__main__":
    main()

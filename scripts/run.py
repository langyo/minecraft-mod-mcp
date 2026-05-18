"""Run a sequence of MCP actions with a single persistent server.
Usage:
  python scripts/run.py "ss start" "click 512 215" "ss after_sp"
  python scripts/run.py --no-container "ss start"   # skip container embedding
Each arg is "ACTION [ARGS]". Actions: ss, click, key, paste, scroll, wait, cmd.
Screenshots auto-saved. Server stays alive for entire sequence.
"""
import sys, os, time, json, subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
MC_DIR = Path(os.environ.get("APPDATA", os.path.expanduser("~"))) / ".minecraft"
SS_DIR = ROOT / "screenshots" / "debug"
SS_DIR.mkdir(parents=True, exist_ok=True)
mc_pid = None  # set by main()

from test_version import kill_all_java, _start_mcp_server, _send_server_cmd, _start_mc, clear_mods, install_mod
from container import McpContainer


def do(srv, cmd_str, container=None):
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
        candidates = []
        for pattern in [f"{name}_*", "mc_*"]:
            for d in [SS_DIR, ROOT / "screenshots"]:
                candidates.extend(d.glob(f"{pattern}.png"))
        candidates.sort(key=os.path.getmtime, reverse=True)
        for f in candidates:
            age = time.time() - os.path.getmtime(f)
            if 0 < age < 20:
                print(f"  SS {name} -> {f} ({os.path.getsize(f)//1024}KB)")
                _pump(container)
                return str(f)
        print(f"  SS {name} -> FAIL (no recent png)")
        _pump(container)
        return None

    elif cmd == "click":
        xy = rest.split()
        x, y = int(xy[0]), int(xy[1])
        _send_server_cmd(srv, "click", {"x": x, "y": y})
        print(f"  CLICK ({x},{y})"); time.sleep(2); _pump(container)

    elif cmd == "key":
        _send_server_cmd(srv, "press_key", {"key": rest})
        print(f"  KEY {rest}"); time.sleep(1.5); _pump(container)

    elif cmd == "paste":
        _send_server_cmd(srv, "paste_text", {"text": rest})
        print(f"  PASTE {rest!r}"); time.sleep(1.5); _pump(container)

    elif cmd == "scroll":
        n = int(rest)
        _send_server_cmd(srv, "scroll", {"clicks": n})
        print(f"  SCROLL {n}"); time.sleep(1); _pump(container)

    elif cmd == "execute_command" or cmd == "cmd":
        _send_server_cmd(srv, "execute_command", {"command": rest})
        print(f"  CMD {rest}"); time.sleep(2); _pump(container)

    elif cmd == "wait":
        t = float(rest) if rest else 2
        print(f"  WAIT {t}s")
        deadline = time.time() + t
        while time.time() < deadline:
            _pump(container)
            time.sleep(0.05)

    elif cmd == "win32_borderless":
        _send_server_cmd(srv, "win32_borderless", {})
        print(f"  WIN32_BORDERLESS -> mod"); time.sleep(2)

    elif cmd == "win32_container":
        _send_server_cmd(srv, "win32_container", {})
        print(f"  WIN32_CONTAINER -> mod"); time.sleep(3)

    elif cmd == "win32_status":
        _send_server_cmd(srv, "win32_status", {})
        print(f"  WIN32_STATUS -> mod"); time.sleep(1)

    else:
        print(f"  UNKNOWN: {cmd}")
    _pump(container)


def _pump(container):
    """Pump container window messages to keep UI responsive."""
    if container:
        container.pump_messages()


def main():
    ACTIONS = {"ss", "click", "key", "paste", "scroll", "wait", "execute_command", "cmd", "win32_borderless", "win32_container", "win32_status"}
    args = sys.argv[1:]

    use_container = True
    if "--no-container" in args:
        use_container = False
        args.remove("--no-container")

    # First arg is version if its first word is not an action
    if args and args[0].split(None, 1)[0] not in ACTIONS and not args[0].startswith("-"):
        version = args.pop(0)
    else:
        version = "1.21.7-forge-57.0.2"
    actions = args

    if not actions:
        actions = ["ss start"]

    print(f"=== RUN: {len(actions)} actions (container={use_container}) ===")

    kill_all_java(); time.sleep(2)
    clear_mods(); install_mod(version, "forge")
    srv = _start_mcp_server(); time.sleep(3)
    mc = _start_mc(version)
    global mc_pid
    mc_pid = mc.pid
    print(f"MC pid={mc.pid}")

    container = None
    if use_container:
        print("Creating container window...")
        container = McpContainer()
        container.create(900, 600, f'MCP - MC {version}')
        print("Waiting for MC window...")
        try:
            container.embed_mc(mc.pid, timeout=30)
            print(f"MC embedded! (hwnd={container.mc_hwnd:#x})")
        except RuntimeError as e:
            print(f"Embed failed: {e}, continuing without container")
            container.destroy()
            container = None

    deadline = time.time() + 120
    while time.time() < deadline:
        if mc.poll(): print("MC EXITED"); return
        log = MC_DIR / "mcp-launch-stdout.log"
        if log.exists():
            try:
                if "MCP-WS" in log.read_text(encoding="utf-8", errors="replace"):
                    print("CONNECTED!"); break
            except: pass
        _pump(container)
        time.sleep(3)
    else:
        print("TIMEOUT"); return
    time.sleep(5)
    _pump(container)

    for i, act in enumerate(actions):
        print(f"\n[{i+1}/{len(actions)}] {act}")
        do(srv, act, container)

    if container:
        container.destroy()
    try: srv.stdin.close()
    except: pass
    srv.kill()
    if mc.poll() is None: mc.kill()
    kill_all_java()
    print("\nDONE")


if __name__ == "__main__":
    main()

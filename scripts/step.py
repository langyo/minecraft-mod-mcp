"""Step-by-step debug: single server, interactive via stdin commands.
Each line = one action + auto-screenshot.
Commands: click X Y | key K | paste T | scroll N | ss NAME | wait N | quit
"""
import sys, os, time, json, subprocess, threading
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
MC_DIR = Path(os.environ.get("APPDATA", os.path.expanduser("~"))) / ".minecraft"
SS_DIR = ROOT / "screenshots" / "debug"
SS_DIR.mkdir(parents=True, exist_ok=True)

from test_version import kill_all_java, _start_mcp_server, _send_server_cmd, _start_mc, clear_mods, install_mod


def ss(srv, name="step"):
    ts = int(time.time())
    path = str(SS_DIR / f"{name}_{ts}.png")
    _send_server_cmd(srv, "screenshot", {"save_path": path})
    time.sleep(5)
    # Check both debug dir and root screenshots dir
    for d in [SS_DIR, ROOT / "screenshots"]:
        for f in sorted(d.glob(f"{name}_*.png"), key=os.path.getmtime, reverse=True):
            if time.time() - os.path.getmtime(f) < 15:
                p = str(f)
                print(f"  SS -> {p} ({os.path.getsize(p)//1024}KB)")
                return p
    print("  SS -> FAIL"); return None


def main():
    version = sys.argv[1] if len(sys.argv) > 1 else "1.21.7-forge-57.0.2"

    print("=== LAUNCH ===")
    kill_all_java(); time.sleep(2)
    clear_mods(); install_mod(version, "forge")
    srv = _start_mcp_server(); time.sleep(3)
    mc = _start_mc(version)
    print(f"MC pid={mc.pid}")

    print("Waiting for mod...")
    deadline = time.time() + 120
    while time.time() < deadline:
        if mc.poll(): print("MC exited"); return
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

    print("\n=== READY ===")
    print("Type: click X Y | key K | paste T | scroll N | ss NAME | wait N | quit")

    step_n = 0
    try:
        while True:
            if mc.poll() is not None:
                print("MC EXITED"); break
            try:
                line = input("> ").strip()
            except EOFError: break
            if not line: continue
            step_n += 1
            parts = line.split(None, 1)
            cmd = parts[0].lower()
            rest = parts[1] if len(parts) > 1 else ""

            if cmd == "quit": break
            elif cmd == "ss": ss(srv, rest or f"step{step_n}")
            elif cmd == "click":
                xy = rest.split()
                if len(xy) >= 2:
                    _send_server_cmd(srv, "click", {"x": int(xy[0]), "y": int(xy[1])})
                    print(f"  clicked ({xy[0]}, {xy[1])})"); time.sleep(2)
                    ss(srv, f"step{step_n}_after_click")
                else: print("  usage: click X Y")
            elif cmd == "key":
                if rest:
                    _send_server_cmd(srv, "press_key", {"key": rest})
                    print(f"  key {rest}"); time.sleep(1.5)
                    ss(srv, f"step{step_n}_after_key")
            elif cmd == "paste":
                if rest:
                    _send_server_cmd(srv, "paste_text", {"text": rest})
                    print(f"  paste {rest!r}"); time.sleep(1.5)
                    ss(srv, f"step{step_n}_after_paste")
            elif cmd == "scroll":
                if rest.isdigit():
                    _send_server_cmd(srv, "scroll", {"clicks": int(rest)})
                    print(f"  scroll {rest}"); time.sleep(1)
                    ss(srv, f"step{step_n}_after_scroll")
            elif cmd == "wait":
                t = float(rest) if rest else 2
                print(f"  waiting {t}s..."); time.sleep(t)
            else: print(f"  unknown: {cmd}")
    finally:
        if mc.poll() is None: mc.kill()
        try: srv.stdin.close()
        except: pass
        srv.kill()
        kill_all_java()
        print("CLEANED UP")


if __name__ == "__main__":
    main()

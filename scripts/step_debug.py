"""Single-step interactive debugger for Minecraft MCP smoke test.
Usage:
  python scripts/step_debug.py 1.21.7-forge-57.0.2
Then it launches MC, takes a screenshot, prints path, and waits.
You inspect the screenshot, then type next command: click X Y | paste TEXT | key KEY | ss | quit
"""

import argparse
import json
import os
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCRIPTS = ROOT / "scripts"
MC_DIR = Path(os.environ.get("APPDATA", os.path.expanduser("~"))) / ".minecraft"

sys.path.insert(0, str(SCRIPTS))
from version_config import ALL_VERSIONS, get_jdk_home
from test_version import (
    kill_all_java, find_mod_jar, install_mod, clear_mods,
    _start_mcp_server, _send_server_cmd, _start_mc,
)

SS_DIR = ROOT / "screenshots" / "debug"
SS_DIR.mkdir(parents=True, exist_ok=True)

server_proc = None
mc_proc = None
ss_count = 0


def ss(name=""):
    global ss_count
    ss_count += 1
    label = f"{ss_count:02d}_{name}" if name else f"{ss_count:02d}"
    ts = int(time.time())
    path = str(SS_DIR / f"{label}_{ts}.png")
    _send_server_cmd(server_proc, "screenshot", {"save_path": path})
    time.sleep(4)
    for f in sorted(SS_DIR.glob(f"{label}_*.png"), key=os.path.getmtime, reverse=True):
        if time.time() - os.path.getmtime(f) < 15:
            p = str(f)
            print(f"  SCREENSHOT: {p} ({os.path.getsize(p)//1024}KB)")
            return p
    print("  SCREENSHOT: FAIL (no file found)")
    return None


def click(x, y):
    _send_server_cmd(server_proc, "click", {"x": int(x), "y": int(y)})
    print(f"  CLICK ({x}, {y})")
    time.sleep(1)


def key(k):
    _send_server_cmd(server_proc, "press_key", {"key": k})
    print(f"  KEY {k}")
    time.sleep(0.5)


def paste(text):
    _send_server_cmd(server_proc, "paste_text", {"text": text})
    print(f"  PASTE {text!r}")
    time.sleep(0.5)


def scroll(clicks):
    _send_server_cmd(server_proc, "scroll", {"clicks": int(clicks)})
    print(f"  SCROLL {clicks}")
    time.sleep(0.5)


def main():
    global server_proc, mc_proc
    parser = argparse.ArgumentParser()
    parser.add_argument("version", nargs="?", default="1.21.7-forge-57.0.2")
    args = parser.parse_args()

    version = args.version
    loader = "forge"
    if "neoforge" in version: loader = "neoforge"
    elif "fabric" in version: loader = "fabric"

    print(f"=== STEP DEBUG: {version} ===")

    kill_all_java()
    time.sleep(2)

    mod_jar = find_mod_jar(version, loader)
    if not mod_jar:
        print(f"ERROR: No mod JAR for {version}/{loader}")
        return
    print(f"Mod: {mod_jar.name}")

    clear_mods()
    install_mod(version, loader)
    server_proc = _start_mcp_server()
    time.sleep(3)
    mc_proc = _start_mc(version)
    if not mc_proc:
        print("ERROR: Failed to launch MC")
        return
    print(f"MC pid={mc_proc.pid}")

    print("Waiting for mod connection...")
    deadline = time.time() + 120
    while time.time() < deadline:
        if mc_proc.poll() is not None:
            print("ERROR: MC exited early"); return
        log = MC_DIR / "mcp-launch-stdout.log"
        if log.exists():
            try:
                if "MCP-WS" in log.read_text(encoding="utf-8", errors="replace"):
                    print("MOD CONNECTED!"); break
            except: pass
        time.sleep(3)
    else:
        print("ERROR: Mod did not connect"); return
    time.sleep(3)

    print("\n=== READY ===")
    print("Commands: ss [name] | click X Y | key K | paste T | scroll N | wait N | quit")
    print(f"Screenshots -> {SS_DIR}")

    try:
        while True:
            if mc_proc.poll() is not None:
                print("MC process exited."); break
            try:
                line = input("> ").strip()
            except EOFError:
                break
            if not line:
                continue
            parts = line.split(None, 1)
            cmd = parts[0].lower()
            rest = parts[1] if len(parts) > 1 else ""

            if cmd == "quit":
                break
            elif cmd == "ss":
                ss(rest)
            elif cmd == "click":
                xy = rest.split()
                click(xy[0], xy[1]) if len(xy) >= 2 else print("usage: click X Y")
            elif cmd == "key":
                key(rest) if rest else print("usage: key KEYNAME")
            elif cmd == "paste":
                paste(rest) if rest else print("usage: paste TEXT")
            elif cmd == "scroll":
                scroll(rest) if rest.isdigit() else print("usage: scroll N")
            elif cmd == "wait":
                t = float(rest) if rest else 2
                print(f"  waiting {t}s..."); time.sleep(t)
            else:
                print(f"unknown: {cmd}")
    finally:
        if mc_proc and mc_proc.poll() is None:
            mc_proc.kill()
        if server_proc:
            try: server_proc.stdin.close()
            except: pass
            server_proc.kill()
        kill_all_java()
        print("Cleaned up.")


if __name__ == "__main__":
    main()

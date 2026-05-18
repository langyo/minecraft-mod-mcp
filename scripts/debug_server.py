"""Persistent MCP debug server - runs in background, reads commands from a cmd file.
Usage:
  python scripts/debug_server.py start   # launches server in background
  python scripts/debug_server.py cmd click 512 200   # send command
  python scripts/debug_server.py ss main_menu       # screenshot
  python scripts/debug_server.py stop               # cleanup
"""
import sys, os, time, json, subprocess, signal, threading
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PID_FILE = ROOT / ".debug_server.pid"
CMD_FILE = ROOT / ".debug_server_cmd"
OUT_FILE = ROOT / ".debug_server_out"
SERVER_JAR = ROOT / "build" / "libs" / "mcp-server-0.1.0.jar"


def start():
    if PID_FILE.exists():
        pid = int(PID_FILE.read_text().strip())
        try:
            os.kill(pid, 0)
            print(f"Already running (pid {pid})"); return
        except: pass

    proc = subprocess.Popen(
        [sys.executable, str(ROOT / "scripts" / "_debug_runner.py")],
        stdout=open(OUT_FILE, "w"), stderr=subprocess.STDOUT,
    )
    PID_FILE.write_text(str(proc.pid))
    print(f"Started pid={proc.pid}, output -> {OUT_FILE}")
    time.sleep(3)


def send_cmd(raw):
    if not PID_FILE.exists():
        print("ERROR: server not running"); return
    CMD_FILE.write_text(raw.strip())
    # Wait for result
    time.sleep(5)
    if OUT_FILE.exists():
        lines = OUT_FILE.read_text(encoding="utf-8", errors="replace").splitlines()
        for l in lines[-20:]:
            if any(k in l for k in ["SCREENSHOT", "screenshot saved", "clicked", "Error:", "FAIL", "DONE", ">>>"]):
                print(l)


def stop():
    if PID_FILE.exists():
        pid = int(PID_FILE.read_text().strip())
        try: os.kill(pid, signal.SIGTERM)
        except: pass
        PID_FILE.unlink(missing_ok=True)
        print("Stopped")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: debug_server.py [start|stop|cmd ...|ss name]"); sys.exit(1)
    cmd = sys.argv[1]
    if cmd == "start": start()
    elif cmd == "stop": stop()
    elif cmd == "cmd": send_cmd(" ".join(sys.argv[2:]))
    elif cmd == "ss": send_cmd(f"ss {sys.argv[2] if len(sys.argv)>2 else ''}")
    else: print(f"Unknown: {cmd}")

"""Test: change MC window to WS_POPUP (borderless), verify screenshots still work."""
import ctypes, time, sys, os
from ctypes import wintypes
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))

from test_version import kill_all_java, _start_mcp_server, _send_server_cmd, _start_mc, clear_mods, install_mod

WS_POPUP = 0x80000000
GWL_STYLE = -16
SWP_FLAGS = 0x0002 | 0x0001 | 0x0004 | 0x0020
user32 = ctypes.windll.user32

def ss(srv, label):
    name = str(ROOT / "screenshots" / "debug" / f"{label}_{int(time.time())}.png")
    _send_server_cmd(srv, "screenshot", {"save_path": name})
    time.sleep(5)
    print(f"  SS {label} -> {name}")
    return name

def find_mc(pid):
    result = []
    def cb(hwnd, lp):
        wpid = wintypes.DWORD()
        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(wpid))
        if wpid.value == pid:
            buf = ctypes.create_unicode_buffer(512)
            user32.GetWindowTextW(hwnd, buf, 512)
            if buf.value.strip():
                result.append(hwnd)
        return True
    EP = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
    user32.EnumWindows(EP(cb), 0)
    return result[0] if result else None

print("Starting MC...")
kill_all_java(); time.sleep(1)
clear_mods(); install_mod("1.21.7-forge-57.0.2", "forge")
srv = _start_mcp_server(); time.sleep(3)
mc = _start_mc("1.21.7-forge-57.0.2")
print(f"MC pid={mc.pid}")

deadline = time.time() + 90
while time.time() < deadline:
    if mc.poll(): print("MC EXITED"); sys.exit(1)
    log = Path(os.environ["APPDATA"]) / ".minecraft" / "mcp-launch-stdout.log"
    if log.exists():
        if "MCP-WS" in log.read_text(encoding="utf-8", errors="replace"):
            print("CONNECTED!"); break
    time.sleep(3)
time.sleep(8)

print("\n--- SS1: before style change ---")
ss(srv, "popup_before")

hwnd = find_mc(mc.pid)
if not hwnd: print("NO MC WINDOW!"); mc.kill(); sys.exit(1)

old = user32.GetWindowLongW(hwnd, GWL_STYLE)
print(f"\nMC hwnd={hwnd:#x}, style={old:#x} -> WS_POPUP")
user32.SetWindowLongW(hwnd, GWL_STYLE, WS_POPUP)
user32.SetWindowPos(hwnd, 0, 0, 0, 0, 0, SWP_FLAGS)
time.sleep(3)

print("\n--- SS2: after WS_POPUP ---")
ss(srv, "popup_after")

# Restore
print("\nRestoring style...")
user32.SetWindowLongW(hwnd, GWL_STYLE, old)
user32.SetWindowPos(hwnd, 0, 0, 0, 0, 0, SWP_FLAGS)
time.sleep(3)

print("\n--- SS3: after restore ---")
ss(srv, "popup_restore")

srv.kill(); mc.kill(); kill_all_java()
print("\nDONE - check screenshots/popup_*.png")

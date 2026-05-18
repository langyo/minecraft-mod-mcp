"""Windows WH_MOUSE_LL hook daemon - intercepts mouse events before they reach MC window.

Architecture:
  - WH_MOUSE_LL: lowest-level mouse hook, fires before raw input / GLFW sees events
  - In control mode: blocks all mouse events whose position falls within MC window bounds
  - In normal mode: passes all events through
  - Accepts commands via stdin (one JSON per line) for click injection and control toggling

This completely eliminates the internal "tug of war" between:
  - MC's own GLFW code (grabbing mouse, warping cursor, enabling cursor)
  - Our reflection-based suppression (setting mouseGrabbed=true every 10 ticks)

Instead, MC never sees real mouse events at all when control is active.
Mouse is permanently free on the user's real desktop.

Commands (stdin, one JSON per line):
  {"cmd": "ctrl_on", "hwnd": 12345, "left": 0, "top": 0, "right": 854, "bottom": 480}
  {"cmd": "ctrl_off"}
  {"cmd": "click", "x": 426, "y": 236}
  {"cmd": "move", "x": 100, "y": 200}
  {"cmd": "status"}
  {"cmd": "shutdown"}

Responses (stdout, one JSON per line):
  {"ok": true, "control_mode": true}
  {"ok": false, "error": "..."}
"""
import sys
import json
import ctypes
import ctypes.wintypes
import threading
import time
import os

# Win32 constants
WH_MOUSE_LL = 14
WM_LBUTTONDOWN = 0x0201
WM_LBUTTONUP = 0x0202
WM_RBUTTONDOWN = 0x0204
WM_RBUTTONUP = 0x0205
WM_MOUSEMOVE = 0x0200
WM_MBUTTONDOWN = 0x0207
WM_MBUTTONUP = 0x0208
WM_LBUTTONDBLCLK = 0x0203

# MSLLHOOKSTRUCT
class POINT(ctypes.Structure):
    _fields_ = [("x", ctypes.c_long), ("y", ctypes.c_long)]

class MSLLHOOKSTRUCT(ctypes.Structure):
    _fields_ = [
        ("pt", POINT),
        ("mouseData", ctypes.c_uint),
        ("flags", ctypes.c_uint),
        ("time", ctypes.c_uint),
        ("dwExtraInfo", ctypes.c_ulong),
    ]

# Function types
HOOKPROC = ctypes.WINFUNCTYPE(ctypes.c_long, ctypes.c_int, ctypes.c_wintypes.WPARAM, ctypes.c_wintypes.LPARAM)

# Globals
hook_id = None
control_mode = False
mc_hwnd = 0
mc_bounds = (0, 0, 1, 1)  # left, top, right, bottom
user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32


def log(msg):
    print(json.dumps({"log": msg}), flush=True)


def respond(obj):
    print(json.dumps(obj), flush=True)


def get_window_rect(hwnd):
    class RECT(ctypes.Structure):
        _fields_ = [("left", ctypes.c_long), ("top", ctypes.c_long),
                     ("right", ctypes.c_long), ("bottom", ctypes.c_long)]
    r = RECT()
    user32.GetWindowRect(hwnd, ctypes.byref(r))
    return (r.left, r.top, r.right, r.bottom)


def mouse_hook_callback(nCode, wParam, lParam):
    global control_mode, mc_bounds
    if nCode < 0:
        return user32.CallNextHookEx(hook_id, nCode, wParam, lParam)
    if not control_mode:
        return user32.CallNextHookEx(hook_id, nCode, wParam, lParam)

    ms = ctypes.cast(lParam, ctypes.POINTER(MSLLHOOKSTRUCT)).contents
    x, y = ms.pt.x, ms.pt.y
    left, top, right, bottom = mc_bounds

    if left <= x <= right and top <= y <= bottom:
        return 1  # Block the event

    return user32.CallNextHookEx(hook_id, nCode, wParam, lParam)


# Keep reference alive to prevent GC
_hook_callback = HOOKPROC(mouse_hook_callback)


def install_hook():
    global hook_id
    module = kernel32.GetModuleHandleW(None)
    hook_id = user32.SetWindowsHookExW(WH_MOUSE_LL, _hook_callback, module, 0)
    if not hook_id:
        raise OSError(f"SetWindowsHookExW failed: {kernel32.GetLastError()}")
    log("hook installed MSG_LOOP_STARTING")


def uninstall_hook():
    global hook_id
    if hook_id:
        user32.UnhookWindowsHookEx(hook_id)
        hook_id = None


def inject_click_screen(x, y):
    """Post WM_LBUTTONDOWN/UP to MC window at screen coords."""
    global mc_hwnd
    if not mc_hwnd:
        respond({"ok": False, "error": "no mc hwnd set"})
        return
    lParam = (y << 16) | (x & 0xFFFF)
    user32.PostMessageW(mc_hwnd, WM_LBUTTONDOWN, 1, lParam)
    time.sleep(0.05)
    user32.PostMessageW(mc_hwnd, WM_LBUTTONUP, 0, lParam)
    respond({"ok": True, "clicked": True, "x": x, "y": y})


def inject_move_screen(x, y):
    """Post WM_MOUSEMOVE to MC window at screen coords."""
    global mc_hwnd
    if not mc_hwnd:
        respond({"ok": False, "error": "no mc hwnd set"})
        return
    lParam = (y << 16) | (x & 0xFFFF)
    user32.PostMessageW(mc_hwnd, WM_MOUSEMOVE, 0, lParam)
    respond({"ok": True, "moved": {"x": x, "y": y}})


def set_window_foreground():
    """Bring MC window to foreground."""
    global mc_hwnd
    if mc_hwnd:
        user32.SetForegroundWindow(mc_hwnd)


def cmd_loop():
    global control_mode, mc_hwnd, mc_bounds

    log("mouse_hook ready, waiting for commands")
    set_window_foreground()

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            msg = json.loads(line)
        except json.JSONDecodeError:
            respond({"ok": False, "error": "invalid JSON"})
            continue

        cmd = msg.get("cmd", "")

        if cmd == "ctrl_on":
            mc_hwnd = msg.get("hwnd", mc_hwnd)
            if mc_hwnd:
                mc_bounds = get_window_rect(mc_hwnd)
                log("ctrl_on: bounds=%s hwnd=%s" % (mc_bounds, hex(mc_hwnd)))
            control_mode = True
            respond({"ok": True, "control_mode": True, "bounds": list(mc_bounds)})

        elif cmd == "ctrl_off":
            control_mode = False
            respond({"ok": True, "control_mode": False})

        elif cmd == "status":
            respond({
                "ok": True,
                "control_mode": control_mode,
                "mc_hwnd": hex(mc_hwnd) if mc_hwnd else "none",
                "bounds": list(mc_bounds),
            })

        elif cmd == "click":
            x = msg.get("x", 0)
            y = msg.get("y", 0)
            inject_click_screen(x, y)

        elif cmd == "move":
            x = msg.get("x", 0)
            y = msg.get("y", 0)
            inject_move_screen(x, y)

        elif cmd == "shutdown":
            respond({"ok": True, "shutting_down": True})
            break

        elif cmd == "ping":
            respond({"ok": True, "pong": True})

        else:
            respond({"ok": False, "error": "unknown cmd: " + cmd})

    uninstall_hook()
    log("shutdown complete")


def main():
    try:
        install_hook()

        reader_thread = threading.Thread(target=cmd_loop, daemon=False)
        reader_thread.start()

        msg = ctypes.wintypes.MSG()
        while reader_thread.is_alive():
            result = user32.GetMessageW(ctypes.byref(msg), None, 0, 0)
            if result in (0, -1):
                break
            user32.TranslateMessage(ctypes.byref(msg))
            user32.DispatchMessageW(ctypes.byref(msg))

    except KeyboardInterrupt:
        pass
    except Exception as e:
        respond({"ok": False, "fatal": str(e)})
    finally:
        uninstall_hook()


if __name__ == "__main__":
    main()

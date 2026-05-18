"""Win32 container window that embeds the Minecraft GLFW window.
Provides input control: click container to focus MC, Ctrl+Esc to release.
"""
import ctypes
from ctypes import wintypes
import threading
import time
import os

user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32

# Fix argtypes for 64-bit compatibility
user32.DefWindowProcW.argtypes = [wintypes.HWND, wintypes.UINT, wintypes.WPARAM, wintypes.LPARAM]
user32.DefWindowProcW.restype = wintypes.LPARAM
user32.GetMessageW.argtypes = [wintypes.LPVOID, wintypes.HWND, wintypes.UINT, wintypes.UINT]
user32.GetMessageW.restype = wintypes.BOOL
user32.PeekMessageW.argtypes = [wintypes.LPVOID, wintypes.HWND, wintypes.UINT, wintypes.UINT, wintypes.UINT]
user32.PeekMessageW.restype = wintypes.BOOL
user32.DispatchMessageW.argtypes = [wintypes.LPVOID]
user32.DispatchMessageW.restype = wintypes.LPARAM
user32.TranslateMessage.argtypes = [wintypes.LPVOID]
user32.TranslateMessage.restype = wintypes.BOOL
user32.SetLayeredWindowAttributes.argtypes = [wintypes.HWND, wintypes.COLORREF, wintypes.BYTE, wintypes.DWORD]
user32.SetLayeredWindowAttributes.restype = wintypes.BOOL

# ── Win32 constants ──────────────────────────────────────────────
WS_OVERLAPPEDWINDOW = 0x00CF0000
WS_VISIBLE           = 0x10000000
WS_CHILD             = 0x40000000
WS_POPUP             = 0x80000000
WS_CAPTION           = 0x00C00000
WS_SIZEBOX           = 0x00040000
WS_SYSMENU           = 0x00080000
WS_MINIMIZEBOX       = 0x00020000
WS_MAXIMIZEBOX       = 0x00010000
WS_BORDER            = 0x00800000
WS_DLGFRAME          = 0x00400000

GWL_STYLE   = -16
GWL_EXSTYLE = -20
GWL_WNDPROC = -4

SWP_NOMOVE       = 0x0002
SWP_NOSIZE       = 0x0001
SWP_NOZORDER     = 0x0004
SWP_FRAMECHANGED = 0x0020
SWP_SHOWWINDOW   = 0x0040
SWP_NOACTIVATE   = 0x0010

SW_SHOW        = 5
SW_HIDE        = 0
CW_USEDEFAULT  = 0x80000000

WM_DESTROY      = 0x0002
WM_CLOSE        = 0x0010
WM_HOTKEY       = 0x0312
WM_LBUTTONDOWN  = 0x0201
WM_SETFOCUS     = 0x0007
WM_KILLFOCUS    = 0x0008
WM_ACTIVATE     = 0x0006
WM_MOUSEACTIVATE = 0x0021
WM_SIZE         = 0x0005
WM_WINDOWPOSCHANGING = 0x0046

MA_NOACTIVATE   = 3

MOD_CONTROL = 0x0002
MOD_NOREPEAT = 0x4000
VK_ESCAPE = 0x1B

# ── WNDPROC type ─────────────────────────────────────────────────
WNDPROC = ctypes.WINFUNCTYPE(wintypes.LPARAM, wintypes.HWND, wintypes.UINT, wintypes.WPARAM, wintypes.LPARAM)

# ── Container class ───────────────────────────────────────────────
class McpContainer:
    def __init__(self):
        self.hwnd = None
        self.mc_hwnd = None
        self.control_mode = False
        self.running = False
        self.msg_thread = None
        self._old_wndproc = None
        self._container_wndproc = None
        self.mc_style = 0
        self.mc_exstyle = 0

    def create(self, width=900, height=600, title='MCP Container'):
        """Create the container window."""
        inst = kernel32.GetModuleHandleW(None)

        # Register window class
        class_name = 'McpContainerClass'
        wndproc = WNDPROC(self._window_proc)
        self._container_wndproc = wndproc  # prevent GC

        wc = WNDCLASSEXW()
        wc.cbSize = ctypes.sizeof(WNDCLASSEXW)
        wc.lpfnWndProc = wndproc
        wc.hInstance = inst
        wc.lpszClassName = class_name
        wc.hbrBackground = ctypes.cast(16, wintypes.HANDLE)  # COLOR_WINDOW + 1 = light gray
        wc.hCursor = user32.LoadCursorW(0, 32512)  # IDC_ARROW
        wc.style = 0

        atom = user32.RegisterClassExW(ctypes.byref(wc))
        if not atom:
            raise OSError(f'RegisterClassExW failed: {kernel32.GetLastError()}')

        # Create window
        style = WS_OVERLAPPEDWINDOW | WS_VISIBLE
        self.hwnd = user32.CreateWindowExW(
            0, class_name, title, style,
            CW_USEDEFAULT, CW_USEDEFAULT, width, height,
            0, 0, inst, 0
        )
        if not self.hwnd:
            raise OSError(f'CreateWindowExW failed: {kernel32.GetLastError()}')

        # Center window on screen
        self._center_on_screen(width, height)

        user32.ShowWindow(self.hwnd, SW_SHOW)
        user32.UpdateWindow(self.hwnd)
        self.running = True

    def _center_on_screen(self, width, height):
        screen_w = user32.GetSystemMetrics(0)
        screen_h = user32.GetSystemMetrics(1)
        x = (screen_w - width) // 2
        y = (screen_h - height) // 2
        user32.SetWindowPos(self.hwnd, 0, max(0, x), max(0, y), 0, 0, SWP_NOSIZE | SWP_NOZORDER)

    def embed_mc(self, mc_pid, timeout=30):
        """Find MC's window by process ID. Minimal version for testing."""
        deadline = time.time() + timeout
        while time.time() < deadline:
            hwnd = self._find_window_by_pid(mc_pid)
            if hwnd is not None:
                self.mc_hwnd = hwnd
                break
            time.sleep(0.5)
        else:
            raise RuntimeError(f'No window found for PID {mc_pid} within {timeout}s')

        # Save original styles for cleanup
        self.mc_style = user32.GetWindowLongW(self.mc_hwnd, GWL_STYLE)
        self.mc_exstyle = user32.GetWindowLongW(self.mc_hwnd, GWL_EXSTYLE)
        self._start_hotkey_thread()
        return True

    def _find_window_by_pid(self, pid):
        """Find the main MC GLFW window for a process."""
        windows = []

        def enum_callback(hwnd, lparam):
            wpid = wintypes.DWORD()
            user32.GetWindowThreadProcessId(hwnd, ctypes.byref(wpid))
            if wpid.value == pid:
                title = ctypes.create_unicode_buffer(512)
                user32.GetWindowTextW(hwnd, title, 512)
                t = title.value
                if t.strip():
                    r = wintypes.RECT()
                    user32.GetWindowRect(hwnd, ctypes.byref(r))
                    w = r.right - r.left
                    h = r.bottom - r.top
                    windows.append((hwnd, t, w * h))
            return True

        EnumWindowsProc = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
        user32.EnumWindows(EnumWindowsProc(enum_callback), 0)

        if not windows:
            return None

        # Prefer "Minecraft" window, then largest window
        for hwnd, title, area in windows:
            if "minecraft" in title.lower():
                return hwnd
        # Fallback: return the largest window
        windows.sort(key=lambda x: x[2], reverse=True)
        return windows[0][0]

    def _get_container_client_size(self):
        """Get container's client area size."""
        r = wintypes.RECT()
        user32.GetClientRect(self.hwnd, ctypes.byref(r))
        return r.right, r.bottom

    def resize_to_fit_mc(self):
        """Resize the container to fit MC's content. Called after MC window size is known."""
        if not self.mc_hwnd:
            return
        r = wintypes.RECT()
        user32.GetWindowRect(self.mc_hwnd, ctypes.byref(r))
        mc_w = r.right - r.left
        mc_h = r.bottom - r.top

        # Account for container's non-client area
        container_style = user32.GetWindowLongW(self.hwnd, GWL_STYLE)
        container_exstyle = user32.GetWindowLongW(self.hwnd, GWL_EXSTYLE)
        rect = wintypes.RECT(0, 0, mc_w, mc_h)
        user32.AdjustWindowRectEx(ctypes.byref(rect), container_style, 0, container_exstyle)
        frame_w = rect.right - rect.left
        frame_h = rect.bottom - rect.top

        user32.SetWindowPos(self.hwnd, 0, 0, 0, frame_w, frame_h,
                            SWP_NOMOVE | SWP_NOZORDER)
        user32.SetWindowPos(self.mc_hwnd, 0, 0, 0, mc_w, mc_h, SWP_NOZORDER)

    def take_control(self):
        """Give input control to MC: hide overlay, enable MC."""
        if not self.mc_hwnd or self.control_mode:
            return
        self.control_mode = True
        # Hide our overlay
        user32.ShowWindow(self.hwnd, SW_HIDE)
        # Enable and focus MC
        user32.EnableWindow(self.mc_hwnd, True)
        user32.SetFocus(self.mc_hwnd)
        user32.SetForegroundWindow(self.mc_hwnd)

    def release_control(self):
        """Take input control away from MC: show overlay, disable MC."""
        if not self.mc_hwnd or not self.control_mode:
            return
        self.control_mode = False
        # Show our overlay on top
        user32.SetWindowPos(self.hwnd, self.mc_hwnd, 0, 0, 0, 0,
                            SWP_NOMOVE | SWP_NOSIZE | SWP_SHOWWINDOW)
        # Disable MC input
        user32.EnableWindow(self.mc_hwnd, False)

    def _window_proc(self, hwnd, msg, wparam, lparam):
        if msg == WM_LBUTTONDOWN and not self.control_mode:
            self.take_control()
            return 0
        if msg == WM_SIZE:
            if self.mc_hwnd:
                w = wparam & 0xFFFF
                h = (wparam >> 16) & 0xFFFF
                user32.SetWindowPos(self.mc_hwnd, 0, 0, 0, w, h, SWP_NOZORDER)
            return 0
        if msg == WM_CLOSE:
            self.running = False
            user32.DestroyWindow(self.hwnd)
            return 0
        if msg == WM_DESTROY:
            user32.PostQuitMessage(0)
            self.running = False
            return 0
        return user32.DefWindowProcW(hwnd, msg, wparam, lparam)

    def _start_hotkey_thread(self):
        """Start a background message loop for Ctrl+Esc hotkey."""
        self.msg_thread = threading.Thread(target=self._hotkey_loop, daemon=True)
        self.msg_thread.start()

    def _hotkey_loop(self):
        """Register Ctrl+Esc hotkey and run message loop to receive it."""
        # Create an invisible message-only window for hotkey
        inst = kernel32.GetModuleHandleW(None)
        msg_class = 'McpHotkeyClass'

        def msg_proc(hwnd, msg, wparam, lparam):
            if msg == WM_HOTKEY:
                if self.control_mode:
                    self.release_control()
                else:
                    self.take_control()
                return 0
            return user32.DefWindowProcW(hwnd, msg, wparam, lparam)

        cb = WNDPROC(msg_proc)
        wc = WNDCLASSEXW()
        wc.cbSize = ctypes.sizeof(WNDCLASSEXW)
        wc.lpfnWndProc = cb
        wc.hInstance = inst
        wc.lpszClassName = msg_class

        user32.RegisterClassExW(ctypes.byref(wc))
        msg_hwnd = user32.CreateWindowExW(0, msg_class, '', 0,
                                           0, 0, 0, 0, 0, 0, inst, 0)

        hotkey_id = 1
        if not user32.RegisterHotKey(msg_hwnd, hotkey_id, MOD_CONTROL | MOD_NOREPEAT, VK_ESCAPE):
            if not user32.RegisterHotKey(msg_hwnd, hotkey_id,
                                         0x0001 | 0x0002 | MOD_NOREPEAT, VK_ESCAPE):  # MOD_ALT | MOD_CONTROL
                print(f'[CONTAINER] Hotkey unavailable (Ctrl+Esc and Ctrl+Alt+Esc both taken)')
            else:
                print('[CONTAINER] Ctrl+Alt+Esc to toggle control')
        else:
            print('[CONTAINER] Ctrl+Esc to toggle control')

        msg = wintypes.MSG()
        while self.running:
            r = user32.GetMessageW(ctypes.byref(msg), 0, 0, 0)
            if r in (0, -1):
                break
            user32.TranslateMessage(ctypes.byref(msg))
            user32.DispatchMessageW(ctypes.byref(msg))

        user32.UnregisterHotKey(msg_hwnd, hotkey_id)
        if msg_hwnd:
            user32.DestroyWindow(msg_hwnd)

    def pump_messages(self):
        """Process pending window messages (non-blocking). Call periodically."""
        msg = wintypes.MSG()
        while user32.PeekMessageW(ctypes.byref(msg), 0, 0, 0, 1):  # PM_REMOVE
            user32.TranslateMessage(ctypes.byref(msg))
            user32.DispatchMessageW(ctypes.byref(msg))

    def destroy(self):
        """Clean up: restore MC window and destroy container."""
        self.running = False
        if self.mc_hwnd:
            try:
                user32.SetWindowLongW(self.mc_hwnd, GWL_STYLE, self.mc_style)
                user32.SetWindowLongW(self.mc_hwnd, GWL_EXSTYLE, self.mc_exstyle)
                user32.SetWindowPos(self.mc_hwnd, 0, 0, 0, 0, 0,
                                    SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_FRAMECHANGED)
                user32.EnableWindow(self.mc_hwnd, True)
                user32.ShowWindow(self.mc_hwnd, SW_SHOW)
            except Exception:
                pass
        if self.hwnd:
            user32.DestroyWindow(self.hwnd)
        self.hwnd = None
        self.mc_hwnd = None


# ── Win32 helper structs ──────────────────────────────────────────
class WNDCLASSEXW(ctypes.Structure):
    _fields_ = [
        ('cbSize',        wintypes.UINT),
        ('style',         wintypes.UINT),
        ('lpfnWndProc',   WNDPROC),
        ('cbClsExtra',    wintypes.INT),
        ('cbWndExtra',    wintypes.INT),
        ('hInstance',     wintypes.HINSTANCE),
        ('hIcon',         wintypes.HANDLE),
        ('hCursor',       wintypes.HANDLE),
        ('hbrBackground', wintypes.HANDLE),
        ('lpszMenuName',  wintypes.LPCWSTR),
        ('lpszClassName', wintypes.LPCWSTR),
        ('hIconSm',       wintypes.HANDLE),
    ]


if __name__ == '__main__':
    # Test: create a container for an existing notepad window
    import subprocess
    print('Creating container...')
    c = McpContainer()
    c.create(900, 600, 'MCP Container Test')

    # Launch notepad for testing
    np = subprocess.Popen(['notepad.exe'])
    time.sleep(1)

    try:
        c.embed_mc(np.pid)
        print('Embedded! Press Ctrl+Esc to toggle control, close window to exit.')
        while c.running:
            c.pump_messages()
            time.sleep(0.02)
    finally:
        c.destroy()
        np.terminate()

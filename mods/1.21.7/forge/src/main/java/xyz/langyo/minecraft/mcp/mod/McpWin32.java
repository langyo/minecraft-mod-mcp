package xyz.langyo.minecraft.mcp.mod;

import com.sun.jna.*;
import com.sun.jna.win32.StdCallLibrary;

/** Raw JNA Win32 API bindings for MC window management + container. */
public class McpWin32 {

    public interface MyUser32 extends StdCallLibrary {
        MyUser32 INSTANCE = Native.load("user32", MyUser32.class);

        long SetParent(long hWndChild, long hWndNewParent);
        long GetWindowLongW(long hWnd, int nIndex);
        long SetWindowLongW(long hWnd, int nIndex, long dwNewLong);
        boolean SetWindowPos(long hWnd, long hWndInsertAfter, int X, int Y, int cx, int cy, int uFlags);
        boolean EnableWindow(long hWnd, boolean bEnable);
        long SetFocus(long hWnd);
        boolean ShowWindow(long hWnd, int nCmdShow);
        boolean GetClientRect(long hWnd, RECT rect);
        boolean GetWindowRect(long hWnd, RECT rect);
        boolean AdjustWindowRectEx(RECT rect, long dwStyle, boolean bMenu, long dwExStyle);
        int GetSystemMetrics(int nIndex);

        // Container window creation
        long GetModuleHandleW(long lpModuleName);
        short RegisterClassExW(WNDCLASSEXW lpwcx);
        long CreateWindowExW(long dwExStyle, String lpClassName, String lpWindowName,
                            long dwStyle, int X, int Y, int nWidth, int nHeight,
                            long hWndParent, long hMenu, long hInstance, Pointer lpParam);
        long DefWindowProcW(long hWnd, int Msg, long wParam, long lParam);
        int GetMessageW(MSG lpMsg, long hWnd, int wMsgFilterMin, int wMsgFilterMax);
        boolean TranslateMessage(MSG lpMsg);
        long DispatchMessageW(MSG lpMsg);
        boolean PeekMessageW(MSG lpMsg, long hWnd, int wMsgFilterMin, int wMsgFilterMax, int wRemoveMsg);
        boolean PostQuitMessage(int nExitCode);
        boolean DestroyWindow(long hWnd);

        // Hotkey
        boolean RegisterHotKey(long hWnd, int id, int fsModifiers, int vk);
        boolean UnregisterHotKey(long hWnd, int id);

        // Input
        boolean SetForegroundWindow(long hWnd);
    }

    public interface MyKernel32 extends StdCallLibrary {
        MyKernel32 INSTANCE = Native.load("kernel32", MyKernel32.class);
        int GetLastError();
    }

    // ── Callback for window procedure ──────────────────────────────
    public interface WindowProc extends Callback {
        long callback(long hwnd, int msg, long wParam, long lParam);
    }

    // ── Win32 structs ──────────────────────────────────────────────
    public static class RECT extends Structure {
        public int left, top, right, bottom;
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("left", "top", "right", "bottom");
        }
    }

    public static class MSG extends Structure {
        public long hwnd;
        public int message;
        public long wParam;
        public long lParam;
        public int time;
        public POINT pt;
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("hwnd", "message", "wParam", "lParam", "time", "pt");
        }
    }

    public static class POINT extends Structure {
        public int x, y;
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("x", "y");
        }
    }

    public static class WNDCLASSEXW extends Structure {
        public int cbSize = size();
        public int style;
        public WindowProc lpfnWndProc;
        public int cbClsExtra;
        public int cbWndExtra;
        public long hInstance;
        public long hIcon;
        public long hCursor;
        public long hbrBackground;
        public String lpszMenuName;
        public String lpszClassName;
        public long hIconSm;
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("cbSize", "style", "lpfnWndProc", "cbClsExtra",
                    "cbWndExtra", "hInstance", "hIcon", "hCursor", "hbrBackground",
                    "lpszMenuName", "lpszClassName", "hIconSm");
        }
    }

    // ── Constants ──────────────────────────────────────────────────
    public static final long WS_POPUP = 0x80000000L;
    public static final long WS_OVERLAPPEDWINDOW = 0x00CF0000L;
    public static final long WS_VISIBLE = 0x10000000L;
    public static final long WS_CHILD = 0x40000000L;

    public static final int GWL_STYLE = -16;
    public static final int GWL_EXSTYLE = -20;

    public static final int SWP_NOMOVE = 0x0002;
    public static final int SWP_NOSIZE = 0x0001;
    public static final int SWP_NOZORDER = 0x0004;
    public static final int SWP_FRAMECHANGED = 0x0020;
    public static final int SWP_SHOWWINDOW = 0x0040;
    public static final int SWP_FLAGS = SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_FRAMECHANGED;

    public static final int SW_SHOW = 5;
    public static final int SW_HIDE = 0;

    public static final int WM_DESTROY = 0x0002;
    public static final int WM_CLOSE = 0x0010;
    public static final int WM_HOTKEY = 0x0312;
    public static final int WM_LBUTTONDOWN = 0x0201;
    public static final int WM_SIZE = 0x0005;

    public static final int SM_CXSCREEN = 0;
    public static final int SM_CYSCREEN = 1;

    public static final int PM_REMOVE = 1;

    public static final int MOD_CONTROL = 0x0002;
    public static final int MOD_NOREPEAT = 0x4000;
    public static final int VK_ESCAPE = 0x1B;

    private static final MyUser32 U32 = MyUser32.INSTANCE;
    private static final MyKernel32 K32 = MyKernel32.INSTANCE;

    // ── Container state ────────────────────────────────────────────
    private static long containerHwnd = 0;
    private static long mcHwnd = 0;
    private static long mcOriginalStyle = 0;
    private static volatile boolean controlMode = false;
    private static volatile boolean containerRunning = false;
    private static Thread containerThread = null;
    private static WindowProc wndProcRef = null; // prevent GC

    // ── Public API ─────────────────────────────────────────────────
    public static long getMcWindowHandle(long glfwWindow) {
        try {
            Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
            java.lang.reflect.Method m = glfw.getMethod("glfwGetWin32Window", long.class);
            return (long) m.invoke(null, glfwWindow);
        } catch (Exception e) {
            try {
                return (long) Class.forName("org.lwjgl.glfw.GLFWNativeWin32")
                        .getMethod("glfwGetWin32Window", long.class).invoke(null, glfwWindow);
            } catch (Exception e2) {
                System.err.println("[McpWin32] getMcWindowHandle failed: " + e2.getMessage());
                return 0;
            }
        }
    }

    public static long makeBorderless(long hwnd) {
        long oldStyle = U32.GetWindowLongW(hwnd, GWL_STYLE);
        U32.SetWindowLongW(hwnd, GWL_STYLE, WS_POPUP);
        U32.SetWindowPos(hwnd, 0, 0, 0, 0, 0, SWP_FLAGS | SWP_SHOWWINDOW);
        System.out.println("[McpWin32] makeBorderless: " + Long.toHexString(hwnd) +
                " old=" + Long.toHexString(oldStyle));
        return oldStyle;
    }

    public static void restoreStyle(long hwnd, long style) {
        U32.SetWindowLongW(hwnd, GWL_STYLE, style);
        U32.SetWindowPos(hwnd, 0, 0, 0, 0, 0, SWP_FLAGS | SWP_SHOWWINDOW);
    }

    public static void setParent(long hwnd, long newParent) {
        U32.SetParent(hwnd, newParent);
    }

    public static long getContainerHwnd() { return containerHwnd; }
    public static boolean isControlMode() { return controlMode; }

    // ── Container creation ─────────────────────────────────────────
    public static final int CONTAINER_MARGIN = 40;

    public static void createContainer(long mcHwndIn) {
        if (containerRunning) return;
        mcHwnd = mcHwndIn;
        System.out.println("[McpWin32] createContainer: mcHwnd=" + Long.toHexString(mcHwnd));

        // Get MC window position and size
        RECT mcRect = new RECT();
        if (!U32.GetWindowRect(mcHwnd, mcRect)) {
            System.err.println("[McpWin32] GetWindowRect failed: " + K32.GetLastError());
            return;
        }
        int mcW = mcRect.right - mcRect.left;
        int mcH = mcRect.bottom - mcRect.top;
        System.out.println("[McpWin32] MC window: " + mcW + "x" + mcH);

        int contW = mcW + CONTAINER_MARGIN * 2;
        System.out.println("[McpWin32] contW=" + contW);
        int contH = mcH + CONTAINER_MARGIN * 2;
        System.out.println("[McpWin32] contH=" + contH);

        int screenW = U32.GetSystemMetrics(SM_CXSCREEN);
        System.out.println("[McpWin32] screenW=" + screenW);
        int screenH = U32.GetSystemMetrics(SM_CYSCREEN);
        System.out.println("[McpWin32] screenH=" + screenH);
        int cx = Math.max(0, (screenW - contW) / 2);
        int cy = Math.max(0, (screenH - contH) / 2);

        System.out.println("[McpWin32] screenH=" + screenH + ", getting module handle...");
        System.out.flush();
        long inst = 0L; // skip GetModuleHandleW (hangs), use 0
        System.out.println("[McpWin32] Registering class...");
        System.out.flush();

        // Register window class
        WNDCLASSEXW wc = new WNDCLASSEXW();
        wc.style = 0;
        // Use a named inner class instead of lambda (prevents GC issues)
        class DefWndProc implements WindowProc {
            public long callback(long hwnd, int msg, long wParam, long lParam) {
                return U32.DefWindowProcW(hwnd, msg, wParam, lParam);
            }
        }
        wndProcRef = new DefWndProc();
        wc.lpfnWndProc = wndProcRef;
        wc.hInstance = inst;
        wc.lpszClassName = "McpContainerClass";
        wc.hCursor = 0; // default
        wc.hbrBackground = 16; // COLOR_WINDOW + 1

        short atom = U32.RegisterClassExW(wc);
        if (atom == 0) {
            System.err.println("[McpWin32] RegisterClassEx failed: " + K32.GetLastError());
            return;
        }
        System.out.println("[McpWin32] Class registered, atom=" + atom);

        containerHwnd = U32.CreateWindowExW(0, "McpContainerClass", "MCP Container",
                WS_OVERLAPPEDWINDOW, cx, cy, contW, contH, 0, 0, inst, null);
        if (containerHwnd == 0) {
            System.err.println("[McpWin32] CreateWindowEx failed: " + K32.GetLastError());
            return;
        }
        System.out.println("[McpWin32] Container window created: " + Long.toHexString(containerHwnd));

        // Save original MC style, make borderless, then reparent
        mcOriginalStyle = U32.GetWindowLongW(mcHwnd, GWL_STYLE);
        U32.SetWindowLongW(mcHwnd, GWL_STYLE, WS_POPUP);
        U32.SetWindowPos(mcHwnd, 0, 0, 0, 0, 0, SWP_FLAGS);

        // SetParent AFTER making borderless
        U32.SetParent(mcHwnd, containerHwnd);

        // Position MC inside container with margin
        U32.SetWindowPos(mcHwnd, 0, CONTAINER_MARGIN, CONTAINER_MARGIN, mcW, mcH, SWP_NOZORDER);

        U32.ShowWindow(containerHwnd, SW_SHOW);

        // Message loop on CURRENT thread (must be the thread that created the window)
        containerRunning = true;
        // Register hotkey on invisible window
        WNDCLASSEXW hotWc = new WNDCLASSEXW();
        WindowProc hotProcRef = (hwnd, msg, wParam, lParam) -> {
            if (msg == WM_HOTKEY) {
                if (controlMode) releaseControl();
                else takeControl();
                return 0L;
            }
            return U32.DefWindowProcW(hwnd, msg, wParam, lParam);
        };
        hotWc.lpfnWndProc = hotProcRef;
        hotWc.hInstance = inst;
        hotWc.lpszClassName = "McpHotkeyClass";
        U32.RegisterClassExW(hotWc);
        long hotHwnd = U32.CreateWindowExW(0, "McpHotkeyClass", "", 0,
                0, 0, 0, 0, 0, 0, inst, null);
        U32.RegisterHotKey(hotHwnd, 1, MOD_CONTROL | MOD_NOREPEAT, VK_ESCAPE);
        System.out.println("[McpWin32] Container ready, Ctrl+Esc to toggle");

        MSG msg = new MSG();
        while (containerRunning) {
            int r = U32.GetMessageW(msg, 0, 0, 0);
            if (r == 0 || r == -1) break;
            U32.TranslateMessage(msg);
            U32.DispatchMessageW(msg);
        }
        U32.UnregisterHotKey(hotHwnd, 1);
        U32.DestroyWindow(hotHwnd);
        System.out.println("[McpWin32] Message loop exited");

        System.out.println("[McpWin32] Container created: " + Long.toHexString(containerHwnd));
    }

    public static void destroyContainer() {
        containerRunning = false;
        if (mcHwnd != 0) {
            try {
                U32.SetParent(mcHwnd, 0);
                U32.SetWindowLongW(mcHwnd, GWL_STYLE, mcOriginalStyle);
                U32.SetWindowPos(mcHwnd, 0, 0, 0, 0, 0, SWP_FLAGS | SWP_SHOWWINDOW);
                U32.EnableWindow(mcHwnd, true);
                U32.ShowWindow(mcHwnd, SW_SHOW);
            } catch (Exception ignored) {}
        }
        if (containerHwnd != 0) {
            U32.DestroyWindow(containerHwnd);
        }
        containerHwnd = 0;
        mcHwnd = 0;
    }

    public static void takeControl() {
        if (mcHwnd == 0 || controlMode) return;
        controlMode = true;
        U32.EnableWindow(mcHwnd, true);
        U32.SetFocus(mcHwnd);
        U32.SetForegroundWindow(mcHwnd);
        System.out.println("[McpWin32] Control taken by MC");
    }

    public static void releaseControl() {
        if (mcHwnd == 0 || !controlMode) return;
        controlMode = false;
        U32.EnableWindow(mcHwnd, false);
        System.out.println("[McpWin32] Control released from MC");
    }
}

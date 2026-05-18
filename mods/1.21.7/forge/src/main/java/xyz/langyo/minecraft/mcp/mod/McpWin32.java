package xyz.langyo.minecraft.mcp.mod;

import com.sun.jna.*;
import com.sun.jna.win32.StdCallLibrary;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
        boolean RegisterHotKey(long hWnd, int id, int fsModifiers, int vk);
        boolean UnregisterHotKey(long hWnd, int id);
        boolean SetForegroundWindow(long hWnd);

        // Mouse hook
        long SetWindowsHookExW(int idHook, LowLevelMouseProc lpfn, long hMod, long dwThreadId);
        boolean UnhookWindowsHookEx(long hhk);
        long CallNextHookEx(long hhk, int nCode, long wParam, Pointer lParam);
        boolean PostMessageW(long hWnd, int Msg, long wParam, long lParam);
        boolean SendMessageW(long hWnd, int Msg, long wParam, long lParam);
        boolean ScreenToClient(long hWnd, POINT lpPoint);

        // Overlay / GDI
        boolean SetLayeredWindowAttributes(long hWnd, int crKey, byte bAlpha, int dwFlags);
        boolean InvalidateRect(long hWnd, RECT lpRect, boolean bErase);
        boolean UpdateWindow(long hWnd);
        boolean RedrawWindow(long hWnd, RECT lprcUpdate, long hrgnUpdate, int flags);
        int DrawTextW(long hDC, char[] lpString, int nCount, RECT lpRect, int uFormat);
        long BeginPaint(long hWnd, PAINTSTRUCT lpPaint);
        boolean EndPaint(long hWnd, PAINTSTRUCT lpPaint);
        long GetDC(long hWnd);
        int ReleaseDC(long hWnd, long hDC);
        long CreateCompatibleDC(long hdc);
        boolean DeleteDC(long hdc);
        long CreateCompatibleBitmap(long hdc, int cx, int cy);
        long CreateSolidBrush(int crColor);
        boolean DeleteObject(long ho);
        long SelectObject(long hdc, long ho);
        long CreateFontW(int nHeight, int nWidth, int nEscapement, int nOrientation,
                         int fnWeight, int fdwItalic, int fdwUnderline, int fdwStrikeOut,
                         int fdwCharSet, int fdwOutputPrecision, int fdwClipPrecision,
                         int fdwQuality, int fdwPitchAndFamily, String lpszFace);
        int SetTextColor(long hdc, int crColor);
        int SetBkColor(long hdc, int crColor);
        int SetBkMode(long hdc, int iBkMode);
        long FillRect(long hDC, RECT lprc, long hbr);
        boolean PrintWindow(long hWnd, long hdcBlt, int nFlags);
        long GetWindowDC(long hWnd);
        boolean BitBlt(long hdcDest, int xDest, int yDest, int wDest, int hDest,
                       long hdcSrc, int xSrc, int ySrc, int rop);
        int GetDIBits(long hdc, long hbmp, int uStartScan, int cScanLines,
                      Pointer lpvBits, BITMAPINFO lpbi, int uUsage);
    }

    public interface MyKernel32 extends StdCallLibrary {
        MyKernel32 INSTANCE = Native.load("kernel32", MyKernel32.class);
        int GetLastError();
        long GetModuleHandleW(String lpModuleName);
    }

    public interface MyGdi32 extends StdCallLibrary {
        MyGdi32 INSTANCE = Native.load("gdi32", MyGdi32.class);
    }

    public interface LowLevelMouseProc extends Callback {
        long callback(int nCode, long wParam, Pointer lParam);
    }

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

    public static class POINT extends Structure {
        public int x, y;
        public POINT() {}
        public POINT(int x, int y) { this.x = x; this.y = y; }
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("x", "y");
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

    public static class MSLLHOOKSTRUCT extends Structure {
        public POINT pt;
        public int mouseData;
        public int flags;
        public int time;
        public long dwExtraInfo;
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("pt", "mouseData", "flags", "time", "dwExtraInfo");
        }
    }

    public static class PAINTSTRUCT extends Structure {
        public long hdc;
        public boolean fErase;
        public RECT rcPaint = new RECT();
        public boolean fRestore;
        public boolean fIncUpdate;
        public byte[] rgbReserved = new byte[32];
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("hdc", "fErase", "rcPaint", "fRestore", "fIncUpdate", "rgbReserved");
        }
    }

    public static class BITMAPINFOHEADER extends Structure {
        public int biSize = size();
        public int biWidth;
        public int biHeight;
        public short biPlanes = 1;
        public short biBitCount = 32;
        public int biCompression = 0;
        public int biSizeImage = 0;
        public int biXPelsPerMeter = 0;
        public int biYPelsPerMeter = 0;
        public int biClrUsed = 0;
        public int biClrImportant = 0;
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("biSize", "biWidth", "biHeight", "biPlanes", "biBitCount",
                    "biCompression", "biSizeImage", "biXPelsPerMeter", "biYPelsPerMeter",
                    "biClrUsed", "biClrImportant");
        }
    }

    public static class BITMAPINFO extends Structure {
        public BITMAPINFOHEADER bmiHeader = new BITMAPINFOHEADER();
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("bmiHeader");
        }
    }

    // ── Constants ──────────────────────────────────────────────────
    public static final long WS_POPUP = 0x80000000L;
    public static final long WS_OVERLAPPEDWINDOW = 0x00CF0000L;
    public static final long WS_VISIBLE = 0x10000000L;
    public static final long WS_CHILD = 0x40000000L;

    public static final long WS_EX_LAYERED = 0x00080000L;
    public static final long WS_EX_TRANSPARENT = 0x00000020L;
    public static final long WS_EX_TOPMOST = 0x00000008L;

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
    public static final int WM_LBUTTONUP = 0x0202;
    public static final int WM_RBUTTONDOWN = 0x0204;
    public static final int WM_RBUTTONUP = 0x0205;
    public static final int WM_MOUSEMOVE = 0x0200;
    public static final int WM_MOUSEWHEEL = 0x020A;
    public static final int WM_KEYDOWN = 0x0100;
    public static final int WM_KEYUP = 0x0101;
    public static final int WM_CHAR = 0x0102;
    public static final int WM_SIZE = 0x0005;
    public static final int WM_PAINT = 0x000F;
    public static final int WM_ERASEBKGND = 0x0014;
    public static final int WM_CTLCOLORSTATIC = 0x0138;

    public static final int WH_MOUSE_LL = 14;
    public static final int WM_MOUSEMOVE_LL = 0x0200;
    public static final int WM_LBUTTONDOWN_LL = 0x0201;
    public static final int WM_LBUTTONUP_LL = 0x0202;
    public static final int WM_RBUTTONDOWN_LL = 0x0204;
    public static final int WM_RBUTTONUP_LL = 0x0205;
    public static final int WM_MOUSEWHEEL_LL = 0x020A;

    public static final int MK_LBUTTON = 0x0001;
    public static final int WHEEL_DELTA = 120;
    public static final int PW_RENDERFULLCONTENT = 0x00000002;
    public static final int DIB_RGB_COLORS = 0;
    public static final int SRCCOPY = 0x00CC0020;
    public static final int TRANSPARENT = 1;
    public static final int DT_CENTER = 0x00000025;
    public static final int DT_VCENTER = 0x00000004;
    public static final int DT_SINGLELINE = 0x00000020;
    public static final int DT_NOPREFIX = 0x00000800;
    public static final int LWA_ALPHA = 0x02;

    public static final int SM_CXSCREEN = 0;
    public static final int SM_CYSCREEN = 1;

    public static final int PM_REMOVE = 1;

    public static final int MOD_CONTROL = 0x0002;
    public static final int MOD_NOREPEAT = 0x4000;
    public static final int VK_ESCAPE = 0x1B;

    private static final MyUser32 U32 = MyUser32.INSTANCE;
    private static final MyKernel32 K32 = MyKernel32.INSTANCE;

    // ── State ──────────────────────────────────────────────────────
    private static long containerHwnd = 0;
    private static long mcHwnd = 0;
    private static long mcOriginalStyle = 0;
    private static volatile boolean containerRunning = false;
    private static Thread containerThread = null;
    private static WindowProc wndProcRef = null;

    // Mouse hook state
    private static volatile long mouseHookHandle = 0;
    private static volatile boolean hookControlMode = false;
    private static volatile Thread hookThread = null;
    private static LowLevelMouseProc mouseHookCallback = null;

    // Overlay state
    private static volatile long overlayHwnd = 0;
    private static volatile String overlayText = "";
    private static long overlayFont = 0;
    private static volatile int overlayAlpha = (byte) 160 & 0xFF;

    // ── Public API: Window Handle ──────────────────────────────────

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

    public static void setMcHwnd(long hwnd) {
        mcHwnd = hwnd;
        System.out.println("[McpWin32] setMcHwnd: " + Long.toHexString(hwnd));
    }

    public static long getMcHwnd() { return mcHwnd; }

    // ── Public API: Window Manipulation ────────────────────────────

    public static long makeBorderless(long hwnd) {
        long oldStyle = U32.GetWindowLongW(hwnd, GWL_STYLE);
        U32.SetWindowLongW(hwnd, GWL_STYLE, WS_POPUP);
        U32.SetWindowPos(hwnd, 0, 0, 0, 0, 0, SWP_FLAGS | SWP_SHOWWINDOW);
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
    public static boolean isControlMode() { return hookControlMode; }

    public static RECT getWindowBounds() {
        RECT r = new RECT();
        if (mcHwnd != 0) {
            U32.GetWindowRect(mcHwnd, r);
        }
        return r;
    }

    // ── Phase 1: WH_MOUSE_LL Mouse Hook ───────────────────────────

    public static synchronized boolean installMouseHook() {
        if (mouseHookHandle != 0) {
            System.out.println("[McpWin32] Mouse hook already installed");
            return true;
        }

        mouseHookCallback = new LowLevelMouseProc() {
            @Override
            public long callback(int nCode, long wParam, Pointer lParam) {
                if (nCode >= 0 && hookControlMode && mcHwnd != 0) {
                    try {
                        MSLLHOOKSTRUCT ms = Structure.newInstance(MSLLHOOKSTRUCT.class, lParam);
                        ms.read();

                        RECT mcRect = new RECT();
                        U32.GetWindowRect(mcHwnd, mcRect);
                        int mx = ms.pt.x;
                        int my = ms.pt.y;

                        if (mx >= mcRect.left && mx <= mcRect.right &&
                            my >= mcRect.top && my <= mcRect.bottom) {
                            return 1;
                        }
                    } catch (Exception e) {
                        // If anything goes wrong, don't block
                    }
                }
                return U32.CallNextHookEx(mouseHookHandle, nCode, wParam, lParam);
            }
        };

        final CountDownLatch installLatch = new CountDownLatch(1);
        final boolean[] success = {false};

        hookThread = new Thread(() -> {
            try {
                long hMod = K32.GetModuleHandleW(null);
                mouseHookHandle = U32.SetWindowsHookExW(WH_MOUSE_LL, mouseHookCallback, hMod, 0);
                if (mouseHookHandle == 0) {
                    int err = K32.GetLastError();
                    System.err.println("[McpWin32] SetWindowsHookEx failed: " + err);
                    success[0] = false;
                    installLatch.countDown();
                    return;
                }
                success[0] = true;
                System.out.println("[McpWin32] Mouse hook installed: " + Long.toHexString(mouseHookHandle));
                installLatch.countDown();

                MSG msg = new MSG();
                while (mouseHookHandle != 0) {
                    int r = U32.GetMessageW(msg, 0, 0, 0);
                    if (r == 0 || r == -1) break;
                    U32.TranslateMessage(msg);
                    U32.DispatchMessageW(msg);
                }
                System.out.println("[McpWin32] Hook message loop exited");
            } catch (Exception e) {
                System.err.println("[McpWin32] Hook thread error: " + e.getMessage());
                success[0] = false;
                installLatch.countDown();
            }
        }, "McpMouseHook");

        hookThread.setDaemon(true);
        hookThread.start();

        try {
            installLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
        return success[0];
    }

    public static synchronized boolean uninstallMouseHook() {
        if (mouseHookHandle == 0) return true;
        long handle = mouseHookHandle;
        mouseHookHandle = 0;
        boolean ok = U32.UnhookWindowsHookEx(handle);
        if (hookThread != null) {
            hookThread.interrupt();
            hookThread = null;
        }
        System.out.println("[McpWin32] Mouse hook uninstalled: " + ok);
        return ok;
    }

    public static void setHookControlMode(boolean enabled) {
        hookControlMode = enabled;
        System.out.println("[McpWin32] Hook control mode: " + enabled);
    }

    public static boolean isHookInstalled() {
        return mouseHookHandle != 0;
    }

    // ── Phase 2: Overlay Window ────────────────────────────────────

    private static Thread overlayThread = null;
    private static volatile boolean overlayRunning = false;
    private static WindowProc overlayWndProc = null;

    public static synchronized boolean showOverlay(String text, int port) {
        if (overlayHwnd != 0) {
            updateOverlayText(text);
            return true;
        }

        overlayText = text.isEmpty() ? "MCP is operating at localhost:" + port : text;
        overlayRunning = true;

        final CountDownLatch createLatch = new CountDownLatch(1);
        final boolean[] success = {false};

        overlayThread = new Thread(() -> {
            try {
                long inst = K32.GetModuleHandleW(null);

                overlayWndProc = new WindowProc() {
                    @Override
                    public long callback(long hwnd, int msg, long wParam, long lParam) {
                        if (msg == WM_PAINT) {
                            PAINTSTRUCT ps = new PAINTSTRUCT();
                            long hdc = U32.BeginPaint(hwnd, ps);
                            if (hdc != 0) {
                                try {
                                    RECT cr = new RECT();
                                    U32.GetClientRect(hwnd, cr);
                                    int w = cr.right - cr.left;
                                    int h = cr.bottom - cr.top;

                                    long grayBrush = U32.CreateSolidBrush(0x00404040);
                                    U32.FillRect(hdc, cr, grayBrush);
                                    U32.DeleteObject(grayBrush);

                                    if (overlayFont == 0) {
                                        overlayFont = U32.CreateFontW(
                                            -28, 0, 0, 0, 700,
                                            0, 0, 0, 1,
                                            0, 0, 2,
                                            0x00000022,
                                            "Segoe UI"
                                        );
                                    }
                                    U32.SelectObject(hdc, overlayFont);
                                    U32.SetTextColor(hdc, 0x00FFFFFF);
                                    U32.SetBkMode(hdc, TRANSPARENT);

                                    String currentText = overlayText;
                                    char[] chars = new char[currentText.length() + 1];
                                    System.arraycopy(currentText.toCharArray(), 0, chars, 0, currentText.length());

                                    RECT textRect = new RECT();
                                    textRect.left = 0;
                                    textRect.top = 0;
                                    textRect.right = w;
                                    textRect.bottom = h;
                                    U32.DrawTextW(hdc, chars, currentText.length(), textRect,
                                            DT_CENTER | DT_VCENTER | DT_SINGLELINE | DT_NOPREFIX);
                                } finally {
                                    U32.EndPaint(hwnd, ps);
                                }
                            }
                            return 0L;
                        }
                        if (msg == WM_ERASEBKGND) {
                            return 1L;
                        }
                        if (msg == WM_DESTROY) {
                            if (overlayFont != 0) {
                                U32.DeleteObject(overlayFont);
                                overlayFont = 0;
                            }
                            return 0L;
                        }
                        return U32.DefWindowProcW(hwnd, msg, wParam, lParam);
                    }
                };

                WNDCLASSEXW wc = new WNDCLASSEXW();
                wc.style = 0;
                wc.lpfnWndProc = overlayWndProc;
                wc.hInstance = inst;
                wc.lpszClassName = "McpOverlayClass";
                wc.hCursor = 0;

                short atom = U32.RegisterClassExW(wc);
                if (atom == 0) {
                    System.err.println("[McpWin32] Overlay RegisterClassEx failed: " + K32.GetLastError());
                    success[0] = false;
                    createLatch.countDown();
                    return;
                }

                RECT mcRect = new RECT();
                if (mcHwnd != 0) {
                    U32.GetWindowRect(mcHwnd, mcRect);
                }
                int mw = mcRect.right - mcRect.left;
                int mh = mcRect.bottom - mcRect.top;
                if (mw <= 0) mw = 800;
                if (mh <= 0) mh = 600;

                overlayHwnd = U32.CreateWindowExW(
                    WS_EX_LAYERED | WS_EX_TRANSPARENT | WS_EX_TOPMOST,
                    "McpOverlayClass",
                    "MCP Overlay",
                    WS_POPUP | WS_VISIBLE,
                    mcRect.left, mcRect.top, mw, mh,
                    0, 0, inst, null
                );

                if (overlayHwnd == 0) {
                    System.err.println("[McpWin32] Overlay CreateWindowEx failed: " + K32.GetLastError());
                    success[0] = false;
                    createLatch.countDown();
                    return;
                }

                U32.SetLayeredWindowAttributes(overlayHwnd, 0, (byte) overlayAlpha, LWA_ALPHA);
                U32.ShowWindow(overlayHwnd, SW_SHOW);
                U32.UpdateWindow(overlayHwnd);

                success[0] = true;
                System.out.println("[McpWin32] Overlay shown: " + Long.toHexString(overlayHwnd) + " size=" + mw + "x" + mh);
                createLatch.countDown();

                MSG msg2 = new MSG();
                while (overlayRunning && overlayHwnd != 0) {
                    int r = U32.GetMessageW(msg2, 0, 0, 0);
                    if (r == 0 || r == -1) break;
                    U32.TranslateMessage(msg2);
                    U32.DispatchMessageW(msg2);
                }
                System.out.println("[McpWin32] Overlay message loop exited");
            } catch (Exception e) {
                System.err.println("[McpWin32] Overlay thread error: " + e.getMessage());
                success[0] = false;
                createLatch.countDown();
            }
        }, "McpOverlay");

        overlayThread.setDaemon(true);
        overlayThread.start();

        try {
            createLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
        return success[0];
    }

    public static synchronized void hideOverlay() {
        overlayRunning = false;
        if (overlayHwnd != 0) {
            U32.DestroyWindow(overlayHwnd);
            overlayHwnd = 0;
        }
        if (overlayFont != 0) {
            U32.DeleteObject(overlayFont);
            overlayFont = 0;
        }
        overlayThread = null;
        System.out.println("[McpWin32] Overlay hidden");
    }

    public static void updateOverlayText(String text) {
        overlayText = text;
        if (overlayHwnd != 0) {
            U32.InvalidateRect(overlayHwnd, null, true);
        }
    }

    public static void updateOverlayPosition() {
        if (overlayHwnd == 0 || mcHwnd == 0) return;
        RECT mcRect = new RECT();
        U32.GetWindowRect(mcHwnd, mcRect);
        int mw = mcRect.right - mcRect.left;
        int mh = mcRect.bottom - mcRect.top;
        if (mw > 0 && mh > 0) {
            U32.SetWindowPos(overlayHwnd, 0, mcRect.left, mcRect.top, mw, mh,
                    SWP_NOZORDER | SWP_SHOWWINDOW);
        }
    }

    // ── Phase 3: Synthetic Input Injection ─────────────────────────

    public static boolean injectClick(int screenX, int screenY) {
        if (mcHwnd == 0) return false;
        POINT pt = new POINT(screenX, screenY);
        U32.ScreenToClient(mcHwnd, pt);
        long lParam = makeLParam(pt.x, pt.y);
        boolean d = U32.PostMessageW(mcHwnd, WM_LBUTTONDOWN, MK_LBUTTON, lParam);
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        boolean u = U32.PostMessageW(mcHwnd, WM_LBUTTONUP, 0, lParam);
        return d && u;
    }

    public static boolean injectRightClick(int screenX, int screenY) {
        if (mcHwnd == 0) return false;
        POINT pt = new POINT(screenX, screenY);
        U32.ScreenToClient(mcHwnd, pt);
        long lParam = makeLParam(pt.x, pt.y);
        boolean d = U32.PostMessageW(mcHwnd, WM_RBUTTONDOWN, 0, lParam);
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        boolean u = U32.PostMessageW(mcHwnd, WM_RBUTTONUP, 0, lParam);
        return d && u;
    }

    public static boolean injectMouseMove(int clientX, int clientY) {
        if (mcHwnd == 0) return false;
        long lParam = makeLParam(clientX, clientY);
        return U32.PostMessageW(mcHwnd, WM_MOUSEMOVE, 0, lParam);
    }

    public static boolean injectKey(int vkCode) {
        if (mcHwnd == 0) return false;
        long lParamDown = makeKeyLParam(vkCode, true);
        long lParamUp = makeKeyLParam(vkCode, false);
        boolean d = U32.PostMessageW(mcHwnd, WM_KEYDOWN, vkCode, lParamDown);
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        boolean u = U32.PostMessageW(mcHwnd, WM_KEYUP, vkCode, lParamUp);
        return d && u;
    }

    public static boolean injectChar(char ch) {
        if (mcHwnd == 0) return false;
        return U32.PostMessageW(mcHwnd, WM_CHAR, (long) ch, 0);
    }

    public static boolean injectScroll(int screenX, int screenY, int clicks) {
        if (mcHwnd == 0) return false;
        POINT pt = new POINT(screenX, screenY);
        U32.ScreenToClient(mcHwnd, pt);
        long lParam = makeLParam(pt.x, pt.y);
        long wParam = ((long)(clicks * WHEEL_DELTA)) << 16;
        return U32.PostMessageW(mcHwnd, WM_MOUSEWHEEL, wParam, lParam);
    }

    private static long makeLParam(int lo, int hi) {
        return ((long)hi & 0xFFFFL) << 16 | ((long)lo & 0xFFFFL);
    }

    private static long makeKeyLParam(int vk, boolean down) {
        int scanCode = 0;
        int flags = down ? 0 : (1 << 30 | 1 << 31);
        return ((long)flags << 16) | ((long)scanCode << 16);
    }

    // ── Phase 4: Win32 BitBlt Screenshot ───────────────────────────

    public static byte[] takeWin32Screenshot() {
        if (mcHwnd == 0) {
            System.err.println("[McpWin32] takeWin32Screenshot: no mcHwnd");
            return null;
        }

        try {
            RECT rc = new RECT();
            U32.GetWindowRect(mcHwnd, rc);
            int w = rc.right - rc.left;
            int h = rc.bottom - rc.top;
            if (w <= 0 || h <= 0) return null;

            long hdcScreen = U32.GetWindowDC(mcHwnd);
            if (hdcScreen == 0) return null;

            long hdcMem = U32.CreateCompatibleDC(hdcScreen);
            if (hdcMem == 0) { U32.ReleaseDC(mcHwnd, hdcScreen); return null; }

            long hBitmap = U32.CreateCompatibleBitmap(hdcScreen, w, h);
            if (hBitmap == 0) { U32.DeleteDC(hdcMem); U32.ReleaseDC(mcHwnd, hdcScreen); return null; }

            U32.SelectObject(hdcMem, hBitmap);

            boolean ok = U32.PrintWindow(mcHwnd, hdcMem, PW_RENDERFULLCONTENT);
            if (!ok) {
                ok = U32.BitBlt(hdcMem, 0, 0, w, h, hdcScreen, 0, 0, SRCCOPY);
            }

            if (!ok) {
                U32.DeleteObject(hBitmap);
                U32.DeleteDC(hdcMem);
                U32.ReleaseDC(mcHwnd, hdcScreen);
                return null;
            }

            BITMAPINFO bmi = new BITMAPINFO();
            bmi.bmiHeader.biWidth = w;
            bmi.bmiHeader.biHeight = -h;
            bmi.bmiHeader.biPlanes = 1;
            bmi.bmiHeader.biBitCount = 32;
            bmi.bmiHeader.biCompression = 0;
            bmi.bmiHeader.biSize = bmi.bmiHeader.size();

            int rowBytes = w * 4;
            int totalBytes = rowBytes * h;
            Memory pixelMem = new Memory(totalBytes);

            int scanLines = U32.GetDIBits(hdcMem, hBitmap, 0, h, pixelMem, bmi, DIB_RGB_COLORS);
            if (scanLines == 0) {
                U32.DeleteObject(hBitmap);
                U32.DeleteDC(hdcMem);
                U32.ReleaseDC(mcHwnd, hdcScreen);
                return null;
            }

            byte[] rawPixels = pixelMem.getByteArray(0, totalBytes);

            // Convert BGRA -> ARGB for BufferedImage, then PNG
            int[] pixels = new int[w * h];
            for (int i = 0; i < pixels.length; i++) {
                int b = rawPixels[i * 4] & 0xFF;
                int g = rawPixels[i * 4 + 1] & 0xFF;
                int r = rawPixels[i * 4 + 2] & 0xFF;
                int a = rawPixels[i * 4 + 3] & 0xFF;
                pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }

            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, w, h, pixels, 0, w);

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", baos);
            byte[] result = baos.toByteArray();

            U32.DeleteObject(hBitmap);
            U32.DeleteDC(hdcMem);
            U32.ReleaseDC(mcHwnd, hdcScreen);

            System.out.println("[McpWin32] BitBlt screenshot: " + result.length + " bytes " + w + "x" + h);
            return result;
        } catch (Exception e) {
            System.err.println("[McpWin32] takeWin32Screenshot error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // ── Container (existing) ───────────────────────────────────────

    public static final int CONTAINER_MARGIN = 40;

    public static void createContainer(long mcHwndIn) {
        if (containerRunning) return;
        mcHwnd = mcHwndIn;
        System.out.println("[McpWin32] createContainer: mcHwnd=" + Long.toHexString(mcHwnd));

        RECT mcRect = new RECT();
        if (!U32.GetWindowRect(mcHwnd, mcRect)) {
            System.err.println("[McpWin32] GetWindowRect failed: " + K32.GetLastError());
            return;
        }
        int mcW = mcRect.right - mcRect.left;
        int mcH = mcRect.bottom - mcRect.top;

        int contW = mcW + CONTAINER_MARGIN * 2;
        int contH = mcH + CONTAINER_MARGIN * 2;

        int screenW = U32.GetSystemMetrics(SM_CXSCREEN);
        int screenH = U32.GetSystemMetrics(SM_CYSCREEN);
        int cx = Math.max(0, (screenW - contW) / 2);
        int cy = Math.max(0, (screenH - contH) / 2);

        long inst = 0L;

        WNDCLASSEXW wc = new WNDCLASSEXW();
        wc.style = 0;
        class DefWndProc implements WindowProc {
            public long callback(long hwnd, int msg, long wParam, long lParam) {
                return U32.DefWindowProcW(hwnd, msg, wParam, lParam);
            }
        }
        wndProcRef = new DefWndProc();
        wc.lpfnWndProc = wndProcRef;
        wc.hInstance = inst;
        wc.lpszClassName = "McpContainerClass";
        wc.hCursor = 0;
        wc.hbrBackground = 16;

        short atom = U32.RegisterClassExW(wc);
        if (atom == 0) {
            System.err.println("[McpWin32] RegisterClassEx failed: " + K32.GetLastError());
            return;
        }

        containerHwnd = U32.CreateWindowExW(0, "McpContainerClass", "MCP Container",
                WS_OVERLAPPEDWINDOW, cx, cy, contW, contH, 0, 0, inst, null);
        if (containerHwnd == 0) {
            System.err.println("[McpWin32] CreateWindowEx failed: " + K32.GetLastError());
            return;
        }

        mcOriginalStyle = U32.GetWindowLongW(mcHwnd, GWL_STYLE);
        U32.SetWindowLongW(mcHwnd, GWL_STYLE, WS_POPUP);
        U32.SetWindowPos(mcHwnd, 0, 0, 0, 0, 0, SWP_FLAGS);
        U32.SetParent(mcHwnd, containerHwnd);
        U32.SetWindowPos(mcHwnd, 0, CONTAINER_MARGIN, CONTAINER_MARGIN, mcW, mcH, SWP_NOZORDER);
        U32.ShowWindow(containerHwnd, SW_SHOW);

        containerRunning = true;

        WNDCLASSEXW hotWc = new WNDCLASSEXW();
        WindowProc hotProcRef = (hwnd, msg, wParam, lParam) -> {
            if (msg == WM_HOTKEY) {
                if (hookControlMode) releaseControl();
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

        MSG msg = new MSG();
        while (containerRunning) {
            int r = U32.GetMessageW(msg, 0, 0, 0);
            if (r == 0 || r == -1) break;
            U32.TranslateMessage(msg);
            U32.DispatchMessageW(msg);
        }
        U32.UnregisterHotKey(hotHwnd, 1);
        U32.DestroyWindow(hotHwnd);
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
        if (mcHwnd == 0 || hookControlMode) return;
        hookControlMode = true;
        U32.EnableWindow(mcHwnd, true);
        U32.SetFocus(mcHwnd);
        U32.SetForegroundWindow(mcHwnd);
        System.out.println("[McpWin32] Control taken by MC");
    }

    public static void releaseControl() {
        if (mcHwnd == 0 || !hookControlMode) return;
        hookControlMode = false;
        U32.EnableWindow(mcHwnd, false);
        System.out.println("[McpWin32] Control released from MC");
    }
}

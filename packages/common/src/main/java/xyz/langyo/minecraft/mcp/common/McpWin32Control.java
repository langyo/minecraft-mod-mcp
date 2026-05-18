package xyz.langyo.minecraft.mcp.common;

import com.sun.jna.*;
import com.sun.jna.win32.StdCallLibrary;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class McpWin32Control implements McpPlatformControl {

    private interface MyUser32 extends StdCallLibrary {
        MyUser32 INSTANCE = Native.load("user32", MyUser32.class);

        long SetWindowsHookExW(int idHook, LowLevelMouseProc lpfn, long hMod, long dwThreadId);
        boolean UnhookWindowsHookEx(long hhk);
        long CallNextHookEx(long hhk, int nCode, long wParam, Pointer lParam);
        boolean GetWindowRect(long hWnd, RECT rect);
        long SetWindowsHookExA(int idHook, LowLevelMouseProc lpfn, long hMod, long dwThreadId);

        boolean SetLayeredWindowAttributes(long hWnd, int crKey, byte bAlpha, int dwFlags);
        boolean PostMessageW(long hWnd, int Msg, long wParam, long lParam);
        boolean ScreenToClient(long hWnd, POINT lpPoint);
        boolean InvalidateRect(long hWnd, RECT lpRect, boolean bErase);

        long BeginPaint(long hWnd, PAINTSTRUCT lpPaint);
        boolean EndPaint(long hWnd, PAINTSTRUCT lpPaint);
        long CreateFontW(int nHeight, int nWidth, int nEscapement, int nOrientation,
                         int fnWeight, int fdwItalic, int fdwUnderline, int fdwStrikeOut,
                         int fdwCharSet, int fdwOutputPrecision, int fdwClipPrecision,
                         int fdwQuality, int fdwPitchAndFamily, String lpszFace);
        int SetTextColor(long hdc, int crColor);
        int SetBkMode(long hdc, int iBkMode);
        int DrawTextW(long hDC, char[] lpString, int nCount, RECT lpRect, int uFormat);
        long FillRect(long hDC, RECT lprc, long hbr);
        long CreateSolidBrush(int crColor);
        boolean DeleteObject(long ho);
        long SelectObject(long hdc, long ho);
        boolean DestroyWindow(long hWnd);
        boolean ShowWindow(long hWnd, int nCmdShow);
        boolean UpdateWindow(long hWnd);
        long DefWindowProcW(long hWnd, int Msg, long wParam, long lParam);
        long CreateWindowExW(long dwExStyle, String lpClassName, String lpWindowName,
                            long dwStyle, int X, int Y, int nWidth, int nHeight,
                            long hWndParent, long hMenu, long hInstance, Pointer lpParam);
        short RegisterClassExW(WNDCLASSEXW lpwcx);
        int GetMessageW(MSG lpMsg, long hWnd, int wMsgFilterMin, int wMsgFilterMax);
        boolean TranslateMessage(MSG lpMsg);
        long DispatchMessageW(MSG lpMsg);
        long GetWindowDC(long hWnd);
        long CreateCompatibleDC(long hdc);
        boolean DeleteDC(long hdc);
        long CreateCompatibleBitmap(long hdc, int cx, int cy);
        boolean BitBlt(long hdcDest, int xDest, int yDest, int wDest, int hDest,
                       long hdcSrc, int xSrc, int ySrc, int rop);
        int GetDIBits(long hdc, long hbmp, int uStartScan, int cScanLines,
                      Pointer lpvBits, BITMAPINFO lpbi, int uUsage);
        int ReleaseDC(long hWnd, long hDC);
        boolean SetWindowPos(long hWnd, long hWndInsertAfter, int X, int Y, int cx, int cy, int uFlags);
        boolean GetClientRect(long hWnd, RECT rect);
        boolean PrintWindow(long hWnd, long hdcBlt, int nFlags);
        long SetParent(long hWndChild, long hWndNewParent);
        long GetWindowLongW(long hWnd, int nIndex);
        long SetWindowLongW(long hWnd, int nIndex, long dwNewLong);
        boolean EnableWindow(long hWnd, boolean bEnable);
        long SetFocus(long hWnd);
        boolean SetForegroundWindow(long hWnd);
        int GetSystemMetrics(int nIndex);
        boolean RegisterHotKey(long hWnd, int id, int fsModifiers, int vk);
        boolean UnregisterHotKey(long hWnd, int id);
        boolean PeekMessageW(MSG lpMsg, long hWnd, int wMsgFilterMin, int wMsgFilterMax, int wRemoveMsg);
        boolean PostQuitMessage(int nExitCode);
    }

    private interface MyKernel32 extends StdCallLibrary {
        MyKernel32 INSTANCE = Native.load("kernel32", MyKernel32.class);
        long GetModuleHandleW(String lpModuleName);
    }

    public interface LowLevelMouseProc extends Callback {
        long callback(int nCode, long wParam, Pointer lParam);
    }

    public interface WindowProc extends Callback {
        long callback(long hwnd, int msg, long wParam, long lParam);
    }

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
        protected java.util.List<String> getFieldOrder() { return java.util.Arrays.asList("x", "y"); }
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
        protected java.util.List<String> getFieldOrder() { return java.util.Arrays.asList("bmiHeader"); }
    }

    private static final int WH_MOUSE_LL = 14;
    private static final int WM_PAINT = 0x000F;
    private static final int WM_ERASEBKGND = 0x0014;
    private static final int WM_DESTROY = 0x0002;
    private static final int WM_LBUTTONDOWN = 0x0201;
    private static final int WM_LBUTTONUP = 0x0202;
    private static final int WM_RBUTTONDOWN = 0x0204;
    private static final int WM_RBUTTONUP = 0x0205;
    private static final int WM_MOUSEWHEEL = 0x020A;
    private static final int WM_KEYDOWN = 0x0100;
    private static final int WM_KEYUP = 0x0101;
    private static final int WM_CHAR = 0x0102;
    private static final int MK_LBUTTON = 0x0001;
    private static final int WHEEL_DELTA = 120;
    private static final int PW_RENDERFULLCONTENT = 2;
    private static final int SRCCOPY = 0x00CC0020;
    private static final int DIB_RGB_COLORS = 0;
    private static final int TRANSPARENT = 1;
    private static final int DT_CENTER = 0x25;
    private static final int DT_VCENTER = 0x04;
    private static final int DT_SINGLELINE = 0x20;
    private static final int DT_NOPREFIX = 0x800;
    private static final int LWA_ALPHA = 0x02;
    private static final long WS_POPUP = 0x80000000L;
    private static final long WS_VISIBLE = 0x10000000L;
    private static final long WS_EX_LAYERED = 0x80000L;
    private static final long WS_EX_TRANSPARENT = 0x20L;
    private static final long WS_EX_TOPMOST = 0x08L;
    private static final int SW_SHOW = 5;

    private static final long WS_OVERLAPPEDWINDOW = 0x00CF0000L;
    private static final long WS_CHILD = 0x40000000L;
    private static final int GWL_STYLE = -16;
    private static final int SWP_NOMOVE = 0x0002;
    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_FRAMECHANGED = 0x0020;
    private static final int SWP_SHOWWINDOW = 0x0040;
    private static final int SWP_FLAGS = SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_FRAMECHANGED;
    private static final int SM_CXSCREEN = 0;
    private static final int SM_CYSCREEN = 1;
    private static final int WM_HOTKEY = 0x0312;
    private static final int MOD_CONTROL = 0x0002;
    private static final int MOD_NOREPEAT = 0x4000;
    private static final int VK_ESCAPE = 0x1B;
    private static final int PM_REMOVE = 1;
    private static final int CONTAINER_MARGIN = 40;

    private static final MyUser32 U = MyUser32.INSTANCE;
    private static final MyKernel32 K = MyKernel32.INSTANCE;

    private volatile long mouseHookHandle;
    private volatile boolean controlMode;
    private volatile Thread hookThread;
    private LowLevelMouseProc mouseHookCallback;

    private volatile long overlayHwnd;
    private volatile String overlayText = "";
    private long overlayFont;
    private volatile Thread overlayThread;
    private volatile boolean overlayRunning;

    private volatile long mcHwnd;

    private volatile long containerHwnd;
    private volatile long mcOriginalStyle;
    private volatile boolean containerRunning;
    private volatile Thread containerThread;
    private volatile WindowProc containerWndProc;

    @Override
    public String getPlatformName() { return "win32"; }

    @Override
    public boolean installMouseHook(long mcNativeWindowHandle) {
        if (mouseHookHandle != 0) return true;
        mcHwnd = mcNativeWindowHandle;

        mouseHookCallback = (nCode, wParam, lParam) -> {
            if (nCode >= 0 && controlMode && mcHwnd != 0) {
                try {
                    Pointer base = lParam;
                    int ptX = base.getInt(0);
                    int ptY = base.getInt(4);
                    RECT r = new RECT();
                    U.GetWindowRect(mcHwnd, r);
                    if (ptX >= r.left && ptX <= r.right && ptY >= r.top && ptY <= r.bottom) {
                        return 1L;
                    }
                } catch (Exception ignored) {}
            }
            return U.CallNextHookEx(mouseHookHandle, nCode, wParam, lParam);
        };

        CountDownLatch latch = new CountDownLatch(1);
        boolean[] ok = {false};

        hookThread = new Thread(() -> {
            try {
                long hMod = K.GetModuleHandleW(null);
                mouseHookHandle = U.SetWindowsHookExW(WH_MOUSE_LL, mouseHookCallback, hMod, 0);
                if (mouseHookHandle == 0) { ok[0] = false; latch.countDown(); return; }
                ok[0] = true;
                ReflectionHelper.dbg("McpWin32Control: hook installed " + Long.toHexString(mouseHookHandle));
                latch.countDown();
                MSG msg = new MSG();
                while (mouseHookHandle != 0) {
                    int r = U.GetMessageW(msg, 0, 0, 0);
                    if (r == 0 || r == -1) break;
                    U.TranslateMessage(msg);
                    U.DispatchMessageW(msg);
                }
            } catch (Exception e) {
                ok[0] = false;
                latch.countDown();
            }
        }, "McpMouseHook");
        hookThread.setDaemon(true);
        hookThread.start();

        try { latch.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { return false; }
        return ok[0];
    }

    @Override
    public boolean uninstallMouseHook() {
        if (mouseHookHandle == 0) return true;
        long h = mouseHookHandle;
        mouseHookHandle = 0;
        U.UnhookWindowsHookEx(h);
        if (hookThread != null) { hookThread.interrupt(); hookThread = null; }
        return true;
    }

    @Override
    public void setControlMode(boolean enabled) { controlMode = enabled; }

    @Override
    public boolean isControlMode() { return controlMode; }

    @Override
    public boolean isHookInstalled() { return mouseHookHandle != 0; }

    @Override
    public boolean showOverlay(String text, int port) {
        if (overlayHwnd != 0) { updateOverlayText(text); return true; }
        overlayText = text.isEmpty() ? "MCP is operating at localhost:" + port : text;
        overlayRunning = true;

        CountDownLatch latch = new CountDownLatch(1);
        boolean[] ok = {false};

        WindowProc wndProc = (hwnd, msg, wParam, lParam) -> {
            if (msg == WM_PAINT) {
                PAINTSTRUCT ps = new PAINTSTRUCT();
                long hdc = U.BeginPaint(hwnd, ps);
                if (hdc != 0) {
                    try {
                        RECT cr = new RECT();
                        U.GetClientRect(hwnd, cr);
                        int w = cr.right - cr.left;
                        int h = cr.bottom - cr.top;
                        long brush = U.CreateSolidBrush(0x00404040);
                        RECT fill = new RECT(); fill.left = 0; fill.top = 0; fill.right = w; fill.bottom = h;
                        U.FillRect(hdc, fill, brush);
                        U.DeleteObject(brush);
                        if (overlayFont == 0) {
                            overlayFont = U.CreateFontW(-28, 0, 0, 0, 700, 0, 0, 0, 1, 0, 0, 2, 0x22, "Segoe UI");
                        }
                        U.SelectObject(hdc, overlayFont);
                        U.SetTextColor(hdc, 0x00FFFFFF);
                        U.SetBkMode(hdc, TRANSPARENT);
                        String t = overlayText;
                        char[] chars = new char[t.length() + 1];
                        System.arraycopy(t.toCharArray(), 0, chars, 0, t.length());
                        RECT tr = new RECT(); tr.left = 0; tr.top = 0; tr.right = w; tr.bottom = h;
                        U.DrawTextW(hdc, chars, t.length(), tr, DT_CENTER | DT_VCENTER | DT_SINGLELINE | DT_NOPREFIX);
                    } finally {
                        U.EndPaint(hwnd, ps);
                    }
                }
                return 0L;
            }
            if (msg == WM_ERASEBKGND) return 1L;
            if (msg == WM_DESTROY) { if (overlayFont != 0) { U.DeleteObject(overlayFont); overlayFont = 0; } return 0L; }
            return U.DefWindowProcW(hwnd, msg, wParam, lParam);
        };

        overlayThread = new Thread(() -> {
            try {
                long inst = K.GetModuleHandleW(null);
                WNDCLASSEXW wc = new WNDCLASSEXW();
                wc.lpfnWndProc = wndProc;
                wc.hInstance = inst;
                wc.lpszClassName = "McpOverlay";
                U.RegisterClassExW(wc);

                RECT mcRect = new RECT();
                if (mcHwnd != 0) U.GetWindowRect(mcHwnd, mcRect);
                int mw = mcRect.right - mcRect.left;
                int mh = mcRect.bottom - mcRect.top;
                if (mw <= 0) mw = 800;
                if (mh <= 0) mh = 600;

                overlayHwnd = U.CreateWindowExW(
                    WS_EX_LAYERED | WS_EX_TRANSPARENT | WS_EX_TOPMOST,
                    "McpOverlay", "", WS_POPUP | WS_VISIBLE,
                    mcRect.left, mcRect.top, mw, mh, 0, 0, inst, null);
                if (overlayHwnd == 0) { ok[0] = false; latch.countDown(); return; }

                U.SetLayeredWindowAttributes(overlayHwnd, 0, (byte) 160, LWA_ALPHA);
                U.ShowWindow(overlayHwnd, SW_SHOW);
                U.UpdateWindow(overlayHwnd);
                ok[0] = true;
                latch.countDown();

                MSG m = new MSG();
                while (overlayRunning && overlayHwnd != 0) {
                    int r = U.GetMessageW(m, 0, 0, 0);
                    if (r == 0 || r == -1) break;
                    U.TranslateMessage(m);
                    U.DispatchMessageW(m);
                }
            } catch (Exception e) { ok[0] = false; latch.countDown(); }
        }, "McpOverlay");
        overlayThread.setDaemon(true);
        overlayThread.start();

        try { latch.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { return false; }
        return ok[0];
    }

    @Override
    public void hideOverlay() {
        overlayRunning = false;
        if (overlayHwnd != 0) { U.DestroyWindow(overlayHwnd); overlayHwnd = 0; }
        if (overlayFont != 0) { U.DeleteObject(overlayFont); overlayFont = 0; }
    }

    @Override
    public void updateOverlayText(String text) {
        overlayText = text;
        if (overlayHwnd != 0) U.InvalidateRect(overlayHwnd, null, true);
    }

    @Override
    public boolean injectClick(int screenX, int screenY) {
        if (mcHwnd == 0) return false;
        POINT pt = new POINT(screenX, screenY);
        U.ScreenToClient(mcHwnd, pt);
        long lp = makeLParam(pt.x, pt.y);
        U.PostMessageW(mcHwnd, WM_LBUTTONDOWN, MK_LBUTTON, lp);
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        return U.PostMessageW(mcHwnd, WM_LBUTTONUP, 0, lp);
    }

    @Override
    public boolean injectRightClick(int screenX, int screenY) {
        if (mcHwnd == 0) return false;
        POINT pt = new POINT(screenX, screenY);
        U.ScreenToClient(mcHwnd, pt);
        long lp = makeLParam(pt.x, pt.y);
        U.PostMessageW(mcHwnd, WM_RBUTTONDOWN, 0, lp);
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        return U.PostMessageW(mcHwnd, WM_RBUTTONUP, 0, lp);
    }

    @Override
    public boolean injectKey(int vkCode) {
        if (mcHwnd == 0) return false;
        boolean d = U.PostMessageW(mcHwnd, WM_KEYDOWN, vkCode, 0);
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        return d && U.PostMessageW(mcHwnd, WM_KEYUP, vkCode, 0);
    }

    @Override
    public boolean injectChar(char ch) {
        if (mcHwnd == 0) return false;
        return U.PostMessageW(mcHwnd, WM_CHAR, ch, 0);
    }

    @Override
    public boolean injectScroll(int screenX, int screenY, int clicks) {
        if (mcHwnd == 0) return false;
        POINT pt = new POINT(screenX, screenY);
        U.ScreenToClient(mcHwnd, pt);
        long wp = ((long)(clicks * WHEEL_DELTA)) << 16;
        return U.PostMessageW(mcHwnd, WM_MOUSEWHEEL, wp, makeLParam(pt.x, pt.y));
    }

    @Override
    public byte[] takePlatformScreenshot() {
        if (mcHwnd == 0) return null;
        try {
            RECT rc = new RECT();
            U.GetWindowRect(mcHwnd, rc);
            int w = rc.right - rc.left;
            int h = rc.bottom - rc.top;
            if (w <= 0 || h <= 0) return null;

            long hdcScreen = U.GetWindowDC(mcHwnd);
            if (hdcScreen == 0) return null;
            long hdcMem = U.CreateCompatibleDC(hdcScreen);
            long hBmp = U.CreateCompatibleBitmap(hdcScreen, w, h);
            U.SelectObject(hdcMem, hBmp);
            boolean ok = U.PrintWindow(mcHwnd, hdcMem, PW_RENDERFULLCONTENT);
            if (!ok) {
                ok = U.BitBlt(hdcMem, 0, 0, w, h, hdcScreen, 0, 0, SRCCOPY);
            }
            if (!ok) { U.DeleteObject(hBmp); U.DeleteDC(hdcMem); U.ReleaseDC(mcHwnd, hdcScreen); return null; }

            BITMAPINFO bmi = new BITMAPINFO();
            bmi.bmiHeader.biWidth = w;
            bmi.bmiHeader.biHeight = -h;
            bmi.bmiHeader.biSize = bmi.bmiHeader.size();
            int rowBytes = w * 4;
            Memory px = new Memory((long) rowBytes * h);
            int scan = U.GetDIBits(hdcMem, hBmp, 0, h, px, bmi, DIB_RGB_COLORS);
            if (scan == 0) { U.DeleteObject(hBmp); U.DeleteDC(hdcMem); U.ReleaseDC(mcHwnd, hdcScreen); return null; }

            byte[] raw = px.getByteArray(0, rowBytes * h);
            int[] pixels = new int[w * h];
            for (int i = 0; i < pixels.length; i++) {
                int b = raw[i * 4] & 0xFF;
                int g = raw[i * 4 + 1] & 0xFF;
                int r = raw[i * 4 + 2] & 0xFF;
                int a = raw[i * 4 + 3] & 0xFF;
                pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, w, h, pixels, 0, w);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", baos);
            byte[] result = baos.toByteArray();

            U.DeleteObject(hBmp);
            U.DeleteDC(hdcMem);
            U.ReleaseDC(mcHwnd, hdcScreen);
            return result;
        } catch (Exception e) {
            ReflectionHelper.dbg("McpWin32Control.takePlatformScreenshot: " + e.getMessage());
            return null;
        }
    }

    @Override
    public long resolveNativeWindowHandle(long glfwOrLwjglHandle) {
        try {
            Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
            java.lang.reflect.Method m = glfw.getMethod("glfwGetWin32Window", long.class);
            return (long) m.invoke(null, glfwOrLwjglHandle);
        } catch (Exception e) {
            try {
                return (long) Class.forName("org.lwjgl.glfw.GLFWNativeWin32")
                        .getMethod("glfwGetWin32Window", long.class).invoke(null, glfwOrLwjglHandle);
            } catch (Exception e2) {
                ReflectionHelper.dbg("McpWin32Control: resolveNativeWindowHandle failed: " + e2.getMessage());
                return glfwOrLwjglHandle;
            }
        }
    }

    @Override
    public void updateOverlayPosition() {
        if (overlayHwnd == 0 || mcHwnd == 0) return;
        RECT mcRect = new RECT();
        U.GetWindowRect(mcHwnd, mcRect);
        int mw = mcRect.right - mcRect.left;
        int mh = mcRect.bottom - mcRect.top;
        if (mw > 0 && mh > 0) {
            U.SetWindowPos(overlayHwnd, 0, mcRect.left, mcRect.top, mw, mh,
                    SWP_NOZORDER | SWP_SHOWWINDOW);
        }
    }

    @Override
    public long makeBorderless(long nativeHandle) {
        long oldStyle = U.GetWindowLongW(nativeHandle, GWL_STYLE);
        U.SetWindowLongW(nativeHandle, GWL_STYLE, WS_POPUP);
        U.SetWindowPos(nativeHandle, 0, 0, 0, 0, 0, SWP_FLAGS | SWP_SHOWWINDOW);
        return oldStyle;
    }

    @Override
    public void restoreWindowStyle(long nativeHandle, long originalStyle) {
        U.SetWindowLongW(nativeHandle, GWL_STYLE, originalStyle);
        U.SetWindowPos(nativeHandle, 0, 0, 0, 0, 0, SWP_FLAGS | SWP_SHOWWINDOW);
    }

    @Override
    public String createContainer(long nativeHandle) {
        if (containerRunning) return "container: already running hwnd=" + Long.toHexString(containerHwnd);
        mcHwnd = nativeHandle;

        RECT mcRect = new RECT();
        U.GetWindowRect(mcHwnd, mcRect);
        int mcW = mcRect.right - mcRect.left;
        int mcH = mcRect.bottom - mcRect.top;
        int contW = mcW + CONTAINER_MARGIN * 2;
        int contH = mcH + CONTAINER_MARGIN * 2;
        int screenW = U.GetSystemMetrics(SM_CXSCREEN);
        int screenH = U.GetSystemMetrics(SM_CYSCREEN);
        int cx = Math.max(0, (screenW - contW) / 2);
        int cy = Math.max(0, (screenH - contH) / 2);

        CountDownLatch latch = new CountDownLatch(1);
        String[] result = {"error: unknown"};

        containerThread = new Thread(() -> {
            try {
                long inst = K.GetModuleHandleW(null);

                containerWndProc = (hwnd, msg, wParam, lParam) -> U.DefWindowProcW(hwnd, msg, wParam, lParam);

                WNDCLASSEXW wc = new WNDCLASSEXW();
                wc.lpfnWndProc = containerWndProc;
                wc.hInstance = inst;
                wc.lpszClassName = "McpContainer";
                wc.hbrBackground = 16;
                U.RegisterClassExW(wc);

                containerHwnd = U.CreateWindowExW(0, "McpContainer", "MCP Container",
                        WS_OVERLAPPEDWINDOW, cx, cy, contW, contH, 0, 0, inst, null);
                if (containerHwnd == 0) {
                    result[0] = "error: CreateWindowEx failed";
                    latch.countDown();
                    return;
                }

                mcOriginalStyle = U.GetWindowLongW(mcHwnd, GWL_STYLE);
                U.SetWindowLongW(mcHwnd, GWL_STYLE, WS_POPUP);
                U.SetWindowPos(mcHwnd, 0, 0, 0, 0, 0, SWP_FLAGS);
                U.SetParent(mcHwnd, containerHwnd);
                U.SetWindowPos(mcHwnd, 0, CONTAINER_MARGIN, CONTAINER_MARGIN, mcW, mcH, SWP_NOZORDER);
                U.ShowWindow(containerHwnd, SW_SHOW);

                WindowProc hotProc = (hwnd, msg2, wParam2, lParam2) -> {
                    if (msg2 == WM_HOTKEY) {
                        setControlMode(!isControlMode());
                        return 0L;
                    }
                    return U.DefWindowProcW(hwnd, msg2, wParam2, lParam2);
                };
                WNDCLASSEXW hotWc = new WNDCLASSEXW();
                hotWc.lpfnWndProc = hotProc;
                hotWc.hInstance = inst;
                hotWc.lpszClassName = "McpHotkey";
                U.RegisterClassExW(hotWc);
                long hotHwnd = U.CreateWindowExW(0, "McpHotkey", "", 0,
                        0, 0, 0, 0, 0, 0, inst, null);
                U.RegisterHotKey(hotHwnd, 1, MOD_CONTROL | MOD_NOREPEAT, VK_ESCAPE);

                containerRunning = true;
                result[0] = "container: hwnd=" + Long.toHexString(containerHwnd) + " mcHwnd=" + Long.toHexString(mcHwnd);
                latch.countDown();

                MSG m = new MSG();
                while (containerRunning) {
                    int r = U.GetMessageW(m, 0, 0, 0);
                    if (r == 0 || r == -1) break;
                    U.TranslateMessage(m);
                    U.DispatchMessageW(m);
                }
                U.UnregisterHotKey(hotHwnd, 1);
                U.DestroyWindow(hotHwnd);
            } catch (Exception e) {
                result[0] = "error: " + e.getMessage();
                latch.countDown();
            }
        }, "McpContainer");
        containerThread.setDaemon(true);
        containerThread.start();

        try { latch.await(8, TimeUnit.SECONDS); } catch (InterruptedException e) { return "timeout"; }
        return result[0];
    }

    @Override
    public void destroyContainer() {
        containerRunning = false;
        if (mcHwnd != 0) {
            try {
                U.SetParent(mcHwnd, 0);
                if (mcOriginalStyle != 0) {
                    U.SetWindowLongW(mcHwnd, GWL_STYLE, mcOriginalStyle);
                    U.SetWindowPos(mcHwnd, 0, 0, 0, 0, 0, SWP_FLAGS | SWP_SHOWWINDOW);
                }
                U.EnableWindow(mcHwnd, true);
                U.ShowWindow(mcHwnd, SW_SHOW);
            } catch (Exception ignored) {}
        }
        if (containerHwnd != 0) {
            U.DestroyWindow(containerHwnd);
            containerHwnd = 0;
        }
    }

    @Override
    public String getStatus() {
        return "platform=win32 hwnd=" + Long.toHexString(mcHwnd) +
                " hook=" + isHookInstalled() +
                " control=" + controlMode +
                " overlay=" + Long.toHexString(overlayHwnd);
    }

    private static long makeLParam(int lo, int hi) {
        return ((long)hi & 0xFFFFL) << 16 | ((long)lo & 0xFFFFL);
    }
}

package xyz.langyo.minecraft.mcp.common;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

public class McpX11Control implements McpPlatformControl {

    private interface X11 extends Library {
        X11 INSTANCE = Native.load("X11", X11.class);

        long XOpenDisplay(String name);
        int XCloseDisplay(long display);
        int XGrabPointer(long display, long window, boolean ownerEvents,
                         int eventMask, int pointerMode, int keyboardMode,
                         long confineTo, long cursor, long time);
        void XUngrabPointer(long display, long time);
        long XDefaultRootWindow(long display);
        int XFlush(long display);
        int XSync(long display, boolean discard);
        long XCreateSimpleWindow(long display, long parent, int x, int y,
                                 int width, int height, int borderWidth,
                                 long border, long background);
        int XMapWindow(long display, long window);
        int XUnmapWindow(long display, long window);
        int XDestroyWindow(long display, long window);
        int XMoveResizeWindow(long display, long window, int x, int y, int width, int height);
        long XCreateGC(long display, long drawable, long valuemask, Pointer values);
        int XFreeGC(long display, long gc);
        int XDrawString(long display, long drawable, long gc, int x, int y, String string, int length);
        int XFillRectangle(long display, long drawable, long gc, int x, int y, int width, int height);
        long XDefaultScreen(long display);
        long XScreenOfDisplay(long display, int screen);
        int XWidthOfScreen(long screen);
        int XHeightOfScreen(long screen);
        long XLoadQueryFont(long display, String name);
        int XFree(long data);
        int XSetForeground(long display, long gc, long foreground);
        int XSetBackground(long display, long gc, long background);
        int XSetFont(long display, long gc, long font);
        long XInternAtom(long display, String atomName, boolean onlyIfExists);
        int XSetWindowBackground(long display, long window, long backgroundPixel);
        int XStoreName(long display, long window, String name);
        int XSelectInput(long display, long window, long eventMask);
        int XSendEvent(long display, long window, boolean propagate, long eventMask, Pointer eventSend);
        int XWarpPointer(long display, long srcW, long destW, int srcX, int srcY,
                         int srcWidth, int srcHeight, int destX, int destY);
        int XGetGeometry(long display, long drawable, PointerByReference root,
                         IntByReference x, IntByReference y,
                         IntByReference width, IntByReference height,
                         IntByReference borderWidth, IntByReference depth);
        int XQueryPointer(long display, long window, PointerByReference rootReturn,
                          PointerByReference childReturn, IntByReference rootXReturn,
                          IntByReference rootYReturn, IntByReference winXReturn,
                          IntByReference winYReturn, IntByReference maskReturn);
    }

    private interface XFixes extends Library {
        XFixes INSTANCE = Native.load("Xfixes", XFixes.class);
        int XFixesHideCursor(long display, long window);
        int XFixesShowCursor(long display, long window);
    }

    private static final X11 X = X11.INSTANCE;
    private static final XFixes XF = XFixes.INSTANCE;

    private static final int GrabModeAsync = 1;
    private static final int ButtonPressMask = 1 << 2;
    private static final int ButtonReleaseMask = 1 << 3;
    private static final int PointerMotionMask = 1 << 6;
    private static final int ExposureMask = 1 << 15;
    private static final int StructureNotifyMask = 1 << 17;

    private volatile long display;
    private volatile long mcWindow;
    private volatile boolean controlMode;
    private volatile boolean hookInstalled;
    private volatile long overlayWindow;
    private volatile String overlayText = "";
    private volatile long overlayGC;
    private volatile long overlayFont;

    @Override
    public String getPlatformName() { return "x11"; }

    @Override
    public synchronized boolean installMouseHook(long mcNativeWindowHandle) {
        if (hookInstalled) return true;
        try {
            display = X.XOpenDisplay(null);
            if (display == 0) {
                ReflectionHelper.dbg("McpX11: XOpenDisplay failed");
                return false;
            }
            mcWindow = mcNativeWindowHandle;
            hookInstalled = true;
            ReflectionHelper.dbg("McpX11: hooked display=" + Long.toHexString(display) + " mcWin=" + Long.toHexString(mcWindow));
            return true;
        } catch (Exception e) {
            ReflectionHelper.dbg("McpX11: installMouseHook failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public synchronized boolean uninstallMouseHook() {
        if (!hookInstalled) return true;
        if (controlMode) setControlMode(false);
        if (display != 0) {
            X.XCloseDisplay(display);
            display = 0;
        }
        hookInstalled = false;
        return true;
    }

    @Override
    public void setControlMode(boolean enabled) {
        if (!hookInstalled || display == 0 || mcWindow == 0) {
            controlMode = enabled;
            return;
        }
        if (enabled && !controlMode) {
            int mask = ButtonPressMask | ButtonReleaseMask | PointerMotionMask;
            int result = X.XGrabPointer(display, mcWindow, false, mask,
                    GrabModeAsync, GrabModeAsync, 0, 0, 0L);
            ReflectionHelper.dbg("McpX11: XGrabPointer result=" + result);
            X.XFlush(display);
            controlMode = true;
        } else if (!enabled && controlMode) {
            X.XUngrabPointer(display, 0L);
            X.XFlush(display);
            controlMode = false;
        }
    }

    @Override
    public boolean isControlMode() { return controlMode; }

    @Override
    public boolean isHookInstalled() { return hookInstalled; }

    @Override
    public synchronized boolean showOverlay(String text, int port) {
        if (display == 0) {
            display = X.XOpenDisplay(null);
            if (display == 0) return false;
        }
        if (overlayWindow != 0) {
            updateOverlayText(text);
            return true;
        }

        overlayText = text.isEmpty() ? "MCP is operating at localhost:" + port : text;
        long root = X.XDefaultRootWindow(display);

        IntByReference x = new IntByReference(), y = new IntByReference();
        IntByReference w = new IntByReference(), h = new IntByReference();
        IntByReference bw = new IntByReference(), depth = new IntByReference();
        PointerByReference rootRet = new PointerByReference();
        X.XGetGeometry(display, mcWindow != 0 ? mcWindow : root, rootRet, x, y, w, h, bw, depth);

        int screenW = w.getValue();
        int screenH = h.getValue();

        overlayWindow = X.XCreateSimpleWindow(display, mcWindow != 0 ? mcWindow : root,
                0, 0, screenW, screenH, 0, 0, 0x404040L);
        if (overlayWindow == 0) return false;

        X.XSelectInput(display, overlayWindow, ExposureMask | StructureNotifyMask);
        X.XMapWindow(display, overlayWindow);
        X.XFlush(display);

        overlayGC = X.XCreateGC(display, overlayWindow, 0, null);
        overlayFont = X.XLoadQueryFont(display, "-*-helvetica-bold-r-*-*-24-*-*-*-*-*-*-*");
        if (overlayFont == 0) overlayFont = X.XLoadQueryFont(display, "fixed");
        if (overlayFont != 0) X.XSetFont(display, overlayGC, overlayFont);
        X.XSetForeground(display, overlayGC, 0xFFFFFF);

        ReflectionHelper.dbg("McpX11: overlay shown " + screenW + "x" + screenH);
        return true;
    }

    @Override
    public synchronized void hideOverlay() {
        if (display == 0 || overlayWindow == 0) return;
        X.XUnmapWindow(display, overlayWindow);
        X.XDestroyWindow(display, overlayWindow);
        if (overlayGC != 0) X.XFreeGC(display, overlayGC);
        if (overlayFont != 0) X.XFree(overlayFont);
        overlayWindow = 0;
        overlayGC = 0;
        overlayFont = 0;
        X.XFlush(display);
    }

    @Override
    public void updateOverlayText(String text) {
        overlayText = text;
    }

    @Override
    public boolean injectClick(int screenX, int screenY) {
        ReflectionHelper.dbg("McpX11: injectClick not implemented (use reflection guiClick)");
        return false;
    }

    @Override
    public boolean injectRightClick(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean injectKey(int vkOrKeyCode) {
        return false;
    }

    @Override
    public boolean injectChar(char ch) {
        return false;
    }

    @Override
    public boolean injectScroll(int screenX, int screenY, int clicks) {
        return false;
    }

    @Override
    public byte[] takePlatformScreenshot() { return null; }

    @Override
    public String getStatus() {
        return "platform=x11 display=" + Long.toHexString(display) +
                " mcWin=" + Long.toHexString(mcWindow) +
                " control=" + controlMode +
                " overlay=" + Long.toHexString(overlayWindow);
    }
}

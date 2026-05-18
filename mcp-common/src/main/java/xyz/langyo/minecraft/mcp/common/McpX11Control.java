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
        int XSelectInput(long display, long window, long eventMask);
        int XStoreName(long display, long window, String name);
        int XGetGeometry(long display, long drawable, PointerByReference root,
                         IntByReference x, IntByReference y,
                         IntByReference width, IntByReference height,
                         IntByReference borderWidth, IntByReference depth);
        long XGetImage(long display, long drawable, int x, int y,
                       int width, int height, long planeMask, int format);
        int XDestroyImage(long image);
        long XAllPlanes();
    }

    private static final X11 X = X11.INSTANCE;

    private static final int GrabModeAsync = 1;
    private static final int ButtonPressMask = 1 << 2;
    private static final int ButtonReleaseMask = 1 << 3;
    private static final int PointerMotionMask = 1 << 6;
    private static final int ExposureMask = 1 << 15;
    private static final int StructureNotifyMask = 1 << 17;
    private static final int ZPixmap = 2;

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
    public long resolveNativeWindowHandle(long glfwOrLwjglHandle) {
        return glfwOrLwjglHandle;
    }

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
        hideOverlay();
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
        long parent = mcWindow != 0 ? mcWindow : root;

        IntByReference x = new IntByReference(), y = new IntByReference();
        IntByReference w = new IntByReference(), h = new IntByReference();
        IntByReference bw = new IntByReference(), depth = new IntByReference();
        PointerByReference rootRet = new PointerByReference();
        X.XGetGeometry(display, parent, rootRet, x, y, w, h, bw, depth);

        int winW = w.getValue() > 0 ? w.getValue() : 800;
        int winH = h.getValue() > 0 ? h.getValue() : 600;

        overlayWindow = X.XCreateSimpleWindow(display, parent,
                0, 0, winW, winH, 0, 0, 0x404040L);
        if (overlayWindow == 0) return false;

        X.XSelectInput(display, overlayWindow, ExposureMask | StructureNotifyMask);
        X.XMapWindow(display, overlayWindow);
        X.XFlush(display);

        overlayGC = X.XCreateGC(display, overlayWindow, 0, null);
        String[] fontNames = {
            "-*-helvetica-bold-r-*-*-24-*-*-*-*-*-*-*",
            "-*-dejavu-sans-bold-r-*-*-24-*-*-*-*-*-*-*",
            "fixed",
            "*"
        };
        overlayFont = 0;
        for (String fn : fontNames) {
            overlayFont = X.XLoadQueryFont(display, fn);
            if (overlayFont != 0) break;
        }
        if (overlayFont != 0) X.XSetFont(display, overlayGC, overlayFont);
        X.XSetForeground(display, overlayGC, 0xFFFFFF);

        ReflectionHelper.dbg("McpX11: overlay shown " + winW + "x" + winH + " font=" + overlayFont);
        return true;
    }

    @Override
    public synchronized void hideOverlay() {
        if (display == 0 || overlayWindow == 0) return;
        X.XUnmapWindow(display, overlayWindow);
        X.XDestroyWindow(display, overlayWindow);
        if (overlayGC != 0) X.XFreeGC(display, overlayGC);
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
    public void updateOverlayPosition() {
        if (display == 0 || overlayWindow == 0 || mcWindow == 0) return;
        IntByReference x = new IntByReference(), y = new IntByReference();
        IntByReference w = new IntByReference(), h = new IntByReference();
        IntByReference bw = new IntByReference(), depth = new IntByReference();
        PointerByReference rootRet = new PointerByReference();
        if (X.XGetGeometry(display, mcWindow, rootRet, x, y, w, h, bw, depth) != 0
                && w.getValue() > 0 && h.getValue() > 0) {
            X.XMoveResizeWindow(display, overlayWindow, 0, 0, w.getValue(), h.getValue());
        }
    }

    @Override
    public boolean injectClick(int screenX, int screenY) {
        ReflectionHelper.dbg("McpX11: injectClick not yet implemented");
        return false;
    }

    @Override
    public boolean injectRightClick(int screenX, int screenY) { return false; }

    @Override
    public boolean injectKey(int vkOrKeyCode) { return false; }

    @Override
    public boolean injectChar(char ch) { return false; }

    @Override
    public boolean injectScroll(int screenX, int screenY, int clicks) { return false; }

    @Override
    public byte[] takePlatformScreenshot() {
        if (display == 0 || mcWindow == 0) return null;
        try {
            IntByReference x = new IntByReference(), y = new IntByReference();
            IntByReference w = new IntByReference(), h = new IntByReference();
            IntByReference bw = new IntByReference(), depth = new IntByReference();
            PointerByReference rootRet = new PointerByReference();
            if (X.XGetGeometry(display, mcWindow, rootRet, x, y, w, h, bw, depth) == 0) return null;
            int width = w.getValue();
            int height = h.getValue();
            if (width <= 0 || height <= 0) return null;

            long image = X.XGetImage(display, mcWindow, 0, 0, width, height, X.XAllPlanes(), ZPixmap);
            if (image == 0) return null;

            int dataOffset = 32; // XImage struct: bytes_per_line at offset 16, data at offset 24 on 64-bit
            Pointer imgPtr = new Pointer(image);
            long dataPtr = imgPtr.getLong(24);
            int bytesPerLine = imgPtr.getInt(16);

            if (dataPtr == 0 || bytesPerLine <= 0) {
                X.XDestroyImage(image);
                return null;
            }

            Pointer data = new Pointer(dataPtr);
            byte[] raw = data.getByteArray(0, bytesPerLine * height);

            int[] pixels = new int[width * height];
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    int idx = row * bytesPerLine + col * 4;
                    if (idx + 3 >= raw.length) continue;
                    int b = raw[idx] & 0xFF;
                    int g = raw[idx + 1] & 0xFF;
                    int r = raw[idx + 2] & 0xFF;
                    int a = raw[idx + 3] & 0xFF;
                    pixels[row * width + col] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }

            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, width, height, pixels, 0, width);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", baos);
            byte[] result = baos.toByteArray();
            X.XDestroyImage(image);
            ReflectionHelper.dbg("McpX11: screenshot " + result.length + " bytes " + width + "x" + height);
            return result;
        } catch (Exception e) {
            ReflectionHelper.dbg("McpX11: screenshot failed: " + e.getMessage());
            return null;
        }
    }

    @Override
    public long makeBorderless(long nativeHandle) { return 0; }

    @Override
    public void restoreWindowStyle(long nativeHandle, long originalStyle) {}

    @Override
    public String createContainer(long nativeHandle) { return "error: container not supported on X11"; }

    @Override
    public void destroyContainer() {}

    @Override
    public String getStatus() {
        return "platform=x11 display=" + Long.toHexString(display) +
                " mcWin=" + Long.toHexString(mcWindow) +
                " control=" + controlMode +
                " overlay=" + Long.toHexString(overlayWindow);
    }
}

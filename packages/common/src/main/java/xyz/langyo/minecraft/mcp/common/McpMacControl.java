package xyz.langyo.minecraft.mcp.common;

import com.sun.jna.*;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

public class McpMacControl implements McpPlatformControl {

    private interface CoreGraphics extends Library {
        CoreGraphics INSTANCE = Native.load("CoreGraphics", CoreGraphics.class);

        long CGWindowListCreateImage(PointerByReference screenRect, int listOption, int windowID, int imageOption);
        int CGMainDisplayID();
        void CGRelease(long obj);
        Pointer CGDataProviderCopyData(long provider);
        long CGImageGetDataProvider(long image);
        int CGImageGetWidth(long image);
        int CGImageGetHeight(long image);
        int CGImageGetBytesPerRow(long image);
        int CGImageGetBitsPerPixel(long image);
        void CGDataProviderRelease(long provider);
        void CGImageRelease(long image);
    }

    private interface CoreFoundation extends Library {
        CoreFoundation INSTANCE = Native.load("CoreFoundation", CoreFoundation.class);
        int CFDataGetLength(Pointer theData);
        Pointer CFDataGetBytePtr(Pointer theData);
        void CFRelease(Pointer cf);
    }

    private interface Libc extends Library {
        Libc INSTANCE = Native.load("c", Libc.class);
        void free(Pointer ptr);
    }

    private static final int kCGWindowListOptionIncludingWindow = 1 << 3;
    private static final int kCGWindowImageNominalResolution = 1 << 2;
    private static final int kCGNullWindowID = 0;

    private volatile boolean controlMode;
    private volatile boolean hookInstalled;
    private volatile String overlayText = "";
    private volatile boolean overlayVisible;
    private volatile long mcNativeWindow;

    @Override
    public String getPlatformName() { return "macos"; }

    @Override
    public long resolveNativeWindowHandle(long glfwOrLwjglHandle) {
        return glfwOrLwjglHandle;
    }

    @Override
    public boolean installMouseHook(long mcNativeWindowHandle) {
        this.mcNativeWindow = mcNativeWindowHandle;
        hookInstalled = true;
        ReflectionHelper.dbg("McpMac: installMouseHook registered (control mode will use CGAssociateMouseAndMouseCursorPosition)");
        return true;
    }

    @Override
    public boolean uninstallMouseHook() {
        if (controlMode) setControlMode(false);
        hookInstalled = false;
        return true;
    }

    @Override
    public void setControlMode(boolean enabled) {
        controlMode = enabled;
        ReflectionHelper.dbg("McpMac: setControlMode " + enabled + " (decoupling cursor via CGAssociateMouseAndMouseCursorPosition)");
    }

    @Override
    public boolean isControlMode() { return controlMode; }

    @Override
    public boolean isHookInstalled() { return hookInstalled; }

    @Override
    public boolean showOverlay(String text, int port) {
        overlayText = text.isEmpty() ? "MCP is operating at localhost:" + port : text;
        overlayVisible = true;
        ReflectionHelper.dbg("McpMac: showOverlay text='" + overlayText + "' (TODO: NSWindow overlay via JNA ObjC bridge)");
        return true;
    }

    @Override
    public void hideOverlay() {
        overlayVisible = false;
    }

    @Override
    public void updateOverlayText(String text) {
        overlayText = text;
    }

    @Override
    public void updateOverlayPosition() {}

    @Override
    public boolean injectClick(int screenX, int screenY) {
        ReflectionHelper.dbg("McpMac: injectClick(" + screenX + "," + screenY + ") not yet implemented");
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
        try {
            if (mcNativeWindow == 0) return null;
            int windowID = (int) mcNativeWindow;
            long image = CoreGraphics.INSTANCE.CGWindowListCreateImage(
                    null,
                    kCGWindowListOptionIncludingWindow,
                    windowID,
                    kCGWindowImageNominalResolution);
            if (image == 0) return null;

            int w = CoreGraphics.INSTANCE.CGImageGetWidth(image);
            int h = CoreGraphics.INSTANCE.CGImageGetHeight(image);
            int bpr = CoreGraphics.INSTANCE.CGImageGetBytesPerRow(image);
            int bpp = CoreGraphics.INSTANCE.CGImageGetBitsPerPixel(image);

            if (w <= 0 || h <= 0) {
                CoreGraphics.INSTANCE.CGImageRelease(image);
                return null;
            }

            long provider = CoreGraphics.INSTANCE.CGImageGetDataProvider(image);
            long dataRef = 0;
            try {
                Pointer dataPtr = CoreGraphics.INSTANCE.CGDataProviderCopyData(provider);
                if (dataPtr == null) { CoreGraphics.INSTANCE.CGImageRelease(image); return null; }
                int dataLen = CoreFoundation.INSTANCE.CFDataGetLength(dataPtr);
                Pointer bytes = CoreFoundation.INSTANCE.CFDataGetBytePtr(dataPtr);
                if (bytes == null || dataLen <= 0) {
                    CoreFoundation.INSTANCE.CFRelease(dataPtr);
                    CoreGraphics.INSTANCE.CGImageRelease(image);
                    return null;
                }

                byte[] raw = bytes.getByteArray(0, Math.min(dataLen, bpr * h));
                int[] pixels = new int[w * h];
                int channels = bpp / 8;
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int srcIdx = y * bpr + x * channels;
                        if (srcIdx + channels > raw.length) continue;
                        int r, g, b, a;
                        if (channels == 4) {
                            a = raw[srcIdx] & 0xFF;
                            r = raw[srcIdx + 1] & 0xFF;
                            g = raw[srcIdx + 2] & 0xFF;
                            b = raw[srcIdx + 3] & 0xFF;
                        } else if (channels == 3) {
                            r = raw[srcIdx] & 0xFF;
                            g = raw[srcIdx + 1] & 0xFF;
                            b = raw[srcIdx + 2] & 0xFF;
                            a = 255;
                        } else {
                            continue;
                        }
                        pixels[y * w + x] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                }

                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                img.setRGB(0, 0, w, h, pixels, 0, w);
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(img, "png", baos);
                byte[] result = baos.toByteArray();
                CoreFoundation.INSTANCE.CFRelease(dataPtr);
                CoreGraphics.INSTANCE.CGImageRelease(image);
                ReflectionHelper.dbg("McpMac: screenshot " + result.length + " bytes " + w + "x" + h);
                return result;
            } finally {
                // provider released by image release
            }
        } catch (Exception e) {
            ReflectionHelper.dbg("McpMac: screenshot failed: " + e.getMessage());
            return null;
        }
    }

    @Override
    public long makeBorderless(long nativeHandle) { return 0; }

    @Override
    public void restoreWindowStyle(long nativeHandle, long originalStyle) {}

    @Override
    public String createContainer(long nativeHandle) { return "error: container not supported on macOS"; }

    @Override
    public void destroyContainer() {}

    @Override
    public String getStatus() {
        return "platform=macos control=" + controlMode + " overlay=" + overlayVisible + " hook=" + hookInstalled;
    }
}

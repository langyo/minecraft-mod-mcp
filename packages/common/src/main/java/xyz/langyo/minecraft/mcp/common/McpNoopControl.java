package xyz.langyo.minecraft.mcp.common;

public class McpNoopControl implements McpPlatformControl {

    @Override
    public String getPlatformName() { return "noop"; }

    @Override
    public boolean installMouseHook(long mcNativeWindowHandle) { return false; }

    @Override
    public boolean uninstallMouseHook() { return true; }

    @Override
    public void setControlMode(boolean enabled) {}

    @Override
    public boolean isControlMode() { return false; }

    @Override
    public boolean isHookInstalled() { return false; }

    @Override
    public boolean showOverlay(String text, int port) { return false; }

    @Override
    public void hideOverlay() {}

    @Override
    public void updateOverlayText(String text) {}

    @Override
    public void updateOverlayPosition() {}

    @Override
    public boolean injectClick(int screenX, int screenY) { return false; }

    @Override
    public boolean injectRightClick(int screenX, int screenY) { return false; }

    @Override
    public boolean injectKey(int vkOrKeyCode) { return false; }

    @Override
    public boolean injectChar(char ch) { return false; }

    @Override
    public boolean injectScroll(int screenX, int screenY, int clicks) { return false; }

    @Override
    public byte[] takePlatformScreenshot() { return null; }

    @Override
    public String getStatus() { return "platform=noop (no native control available)"; }

    @Override
    public long resolveNativeWindowHandle(long glfwOrLwjglHandle) { return glfwOrLwjglHandle; }

    @Override
    public long makeBorderless(long nativeHandle) { return 0; }

    @Override
    public void restoreWindowStyle(long nativeHandle, long originalStyle) {}

    @Override
    public String createContainer(long nativeHandle) { return "error: not supported on this platform"; }

    @Override
    public void destroyContainer() {}
}

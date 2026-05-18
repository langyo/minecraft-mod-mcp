package xyz.langyo.minecraft.mcp.common;

public interface McpPlatformControl {

    String getPlatformName();

    boolean installMouseHook(long mcNativeWindowHandle);

    boolean uninstallMouseHook();

    void setControlMode(boolean enabled);

    boolean isControlMode();

    boolean isHookInstalled();

    boolean showOverlay(String text, int port);

    void hideOverlay();

    void updateOverlayText(String text);

    boolean injectClick(int screenX, int screenY);

    boolean injectRightClick(int screenX, int screenY);

    boolean injectKey(int vkOrKeyCode);

    boolean injectChar(char ch);

    boolean injectScroll(int screenX, int screenY, int clicks);

    byte[] takePlatformScreenshot();

    String getStatus();
}

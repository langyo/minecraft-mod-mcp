package xyz.langyo.minecraft.mcp.common;

import com.google.gson.Gson;

public final class McpProtocol {

    public static final Gson GSON = new Gson();

    private McpProtocol() {}

    public interface MinecraftInput {
        void click(int x, int y, String button);
        void pressKey(String key, float holdSeconds);
        void typeText(String text);
        void pasteText(String text);
        void scroll(int clicks);
        void scrollAt(int x, int y, int clicks);
        void mouseDrag(int x1, int y1, int x2, int y2, String button);
        void hotkey(String[] keys);
        void setViewAngle(float yaw, float pitch);
        void lookDelta(float deltaYaw, float deltaPitch);
        void rightClick();
        byte[] screenshot();
        String executeCommand(String command);
        String getPlayerInfo();
        String getWorldInfo();
        String debugFields();
    }
}

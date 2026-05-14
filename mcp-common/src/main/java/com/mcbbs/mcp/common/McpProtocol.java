package com.mcbbs.mcp.common;

import com.google.gson.Gson;

public final class McpProtocol {

    public static final Gson GSON = new Gson();

    private McpProtocol() {}

    public interface MinecraftInput {
        void click(int x, int y, String button);
        void pressKey(String key, float holdSeconds);
        void typeText(String text);
        void scroll(int clicks);
        void hotkey(String[] keys);
        byte[] screenshot();
        String executeCommand(String command);
        String getPlayerInfo();
        String getWorldInfo();
    }

    public interface ModLifecycle {
        void onInit(MinecraftInput input, Object wsClient);
        void onTick();
        void onShutdown();
    }
}

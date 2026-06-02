package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.fabricmc.api.ClientModInitializer;

public class ModDevMcpMod implements ClientModInitializer {
    public static ModDevMcpMod INSTANCE;
    private McpHttpServer httpServer;
    private ReflectedInputHandler handler;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        int port = McpConfig.getServerPort();
        httpServer = new McpHttpServer(handler, port);
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                httpServer.start();
            } catch (Exception e) {
                System.err.println("[MCP-MOD] HTTP server failed: " + e.getMessage());
            }
        }, "MCP-HTTP").start();
    }

    public void onClientTick() {}

    public void onInGameHudRender(Object ctx, float tickDelta) {}
    public void onScreenRender(Object ctx, Object screen, int mouseX, int mouseY, float tickDelta) {}
    public boolean onMouseButtonEvent(Object mc, double mx, double my, int button) { return false; }
}

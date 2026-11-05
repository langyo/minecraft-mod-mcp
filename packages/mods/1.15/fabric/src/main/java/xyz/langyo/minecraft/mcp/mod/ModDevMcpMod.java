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
        // CurseForge / hand-installed instances launch without
        // -Dmcp.mod.version; fall back to the version fabric.mod.json
        // carries so /api/status stops reporting "unknown" (issue #75).
        if (System.getProperty("mcp.mod.version") == null) {
            try {
                net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getModContainer("mcpmod")
                    .ifPresent(c -> System.setProperty("mcp.mod.version",
                        c.getMetadata().getVersion().getFriendlyString()));
            } catch (Throwable ignored) {}
        }
        handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        int port = McpConfig.getServerPort();
        httpServer = new McpHttpServer(handler, port);
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                try { Object mc = ReflectionHelper.getMinecraftInstance(); if (mc != null) ReflectionHelper.setMinecraftInstance(mc); } catch (Exception ignored) {}
                httpServer.start();
            } catch (Exception e) {
                System.err.println("[MCP-MOD] HTTP server failed: " + e.getMessage());
            }
        }, "MCP-HTTP").start();
    }

    public void onClientTick(Object minecraft) {
        try {
            // Production Fabric runs intermediary class names, so the string
            // based Class.forName bootstrap in ReflectionCache cannot find
            // MinecraftClient there. The mixin hands us the live client
            // instance every tick; register it first (issue #75).
            if (minecraft != null) ReflectionHelper.setMinecraftInstance(minecraft);
            Object mc = ReflectionHelper.getMinecraftInstance();
            ReflectionHelper.tickMouseRelease(mc);
            ReflectionHelper.tickMcpControlMode(mc);
        } catch (Exception ignored) {}
    }

    // ---- render hooks called by the version mixins / events (no-ops on this era) ----
    public void onInGameHudRender(Object ctx, float tickDelta) {}

    public void onScreenRender(Object ctx, Object screen, int mouseX, int mouseY, float tickDelta) {}

    public void onScreenRender(Object screen, int mouseX, int mouseY, float tickDelta) {}

    public boolean onMouseButtonEvent(Object mc, double mx, double my, int button) {
        return false;
    }

    public boolean onMouseClicked(double mx, double my, int button) {
        return false;
    }
}

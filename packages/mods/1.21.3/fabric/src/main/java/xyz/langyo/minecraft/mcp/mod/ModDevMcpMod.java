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
                try { Object mc = ReflectionHelper.getMinecraftInstance(); if (mc != null) ReflectionHelper.setMinecraftInstance(mc); } catch (Exception ignored) {}
                httpServer.start();
            } catch (Exception e) {
                System.err.println("[MCP-MOD] HTTP server failed: " + e.getMessage());
            }
        }, "MCP-HTTP").start();
    }

    public void onClientTick() {
        try {
            Object mc = ReflectionHelper.getMinecraftInstance();
            if (mc != null) ReflectionHelper.setMinecraftInstance(mc);
            ReflectionHelper.tickMouseRelease(mc);
            ReflectionHelper.tickMcpControlMode(mc);
        } catch (Exception ignored) {}
    }

    // ===== top-right overlay (resume / transfer buttons) =====
        private McpRenderer makeRenderer(Object ctx) {
        return new McpRenderer() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {
                if (ctx instanceof net.minecraft.client.gui.DrawContext) {
                    ((net.minecraft.client.gui.DrawContext) ctx).fill(x1, y1, x2, y2, color);
                }
            }
            @Override public int drawString(Object font, String text, int x, int y, int color, boolean shadow) { return 0; }
            @Override public int getStringWidth(Object font, String text) { return 0; }
        };
    }

    private static double[] scaledMouse(Object mcObj) {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        double mx = mc.mouse.getX();
        double my = mc.mouse.getY();
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        return new double[]{mx, my, w, h};
    }

    private void renderHudOverlay(Object ctx) {
        try {
            if (xyz.langyo.minecraft.mcp.common.ReflectionHelper.isScreenshotInProgress()) return;
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.currentScreen != null || !xyz.langyo.minecraft.mcp.common.ReflectionHelper.isMcpControlMode()) return;
            double[] m = scaledMouse(mc);
            xyz.langyo.minecraft.mcp.common.McpOverlayLogic.renderResumeButton(
                    makeRenderer(ctx), null, "", (int) m[2], (int) m[3], (int) m[0], (int) m[1]);
        } catch (Exception ignored) {}
    }

    private void renderScreenOverlay(Object ctx, Object screen, int mx, int my) {
        try {
            if (ctx == null || screen == null || xyz.langyo.minecraft.mcp.common.ReflectionHelper.isScreenshotInProgress()) return;
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            int sw = ((net.minecraft.client.gui.screen.Screen) screen).width;
            int sh = ((net.minecraft.client.gui.screen.Screen) screen).height;
            if (xyz.langyo.minecraft.mcp.common.ReflectionHelper.isMcpControlMode()) {
                xyz.langyo.minecraft.mcp.common.McpOverlayLogic.renderResumeButton(
                        makeRenderer(ctx), null, "", sw, sh, mx, my);
            } else if (mc.world != null) {
                xyz.langyo.minecraft.mcp.common.McpOverlayLogic.renderTransferButton(
                        makeRenderer(ctx), null, "", sw, sh, mx, my);
            }
        } catch (Exception ignored) {}
    }

    private boolean clickOverlay(Object mcObj, double mx, double my, int button) {
        try {
            if (button != 0) return false;
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (xyz.langyo.minecraft.mcp.common.ReflectionHelper.isMcpControlMode()) {
                String r = xyz.langyo.minecraft.mcp.common.ReflectionHelper.handleOverlayClick((int) mx, (int) my, mc);
                return !"blocked".equals(r) && !"cooldown".equals(r) && !"not_in_control_mode".equals(r);
            }
            if (mc.world != null && mc.currentScreen != null) {
                String r = xyz.langyo.minecraft.mcp.common.ReflectionHelper.handleTransferOverlayClick((int) mx, (int) my, mc);
                return "transfer_to_mcp".equals(r);
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ---- render hooks called by the version mixins / events ----
    public void onInGameHudRender(Object ctx, float tickDelta) {
        renderHudOverlay(ctx);
    }

    public void onScreenRender(Object ctx, Object screen, int mouseX, int mouseY, float tickDelta) {
        renderScreenOverlay(ctx, screen, mouseX, mouseY);
    }

    public void onScreenRender(Object screen, int mouseX, int mouseY, float tickDelta) {
        renderScreenOverlay(null, screen, mouseX, mouseY);
    }

    public boolean onMouseButtonEvent(Object mc, double mx, double my, int button) {
        return clickOverlay(mc, mx, my, button);
    }

    public boolean onMouseClicked(double mx, double my, int button) {
        return clickOverlay(null, mx, my, button);
    }
}

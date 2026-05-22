package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.TranslatableText;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class ModDevMcpMod implements ClientModInitializer {
    private McpHttpServer httpServer;
    volatile String debugUrl = null;
    volatile boolean chatSent = false;
    private boolean lastLeftDown = false;
    private Screen lastPatchedScreen = null;

    private static McpRenderer wrapRenderer(MinecraftClient mc) {
        return new McpRenderer() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {
                DrawableHelper.fill(x1, y1, x2, y2, color);
            }
            @Override public int drawString(Object font, String text, int x, int y, int color, boolean shadow) {
                if (shadow) {
                    return mc.textRenderer.drawWithShadow(text, x, y, color);
                } else {
                    return mc.textRenderer.draw(text, x, y, color);
                }
            }
            @Override public int getStringWidth(Object font, String text) {
                return mc.textRenderer.getStringWidth(text);
            }
        };
    }

    @Override
    public void onInitializeClient() {
        boolean depsOk = false;
        try { Class.forName("com.sun.jna.Library"); depsOk = true; } catch (Exception ignored) {}
        if (!depsOk) {
            System.err.println("[MCP-MOD] JNA not on classpath. External control unavailable.");
            return;
        }
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                ReflectedInputHandler handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
                int port = McpConfig.getServerPort();
                httpServer = new McpHttpServer(handler, port);
                httpServer.start();
                debugUrl = "http://127.0.0.1:" + port + "/debug";
                System.out.println("[MCP-MOD] Debug page: " + debugUrl);
            } catch (Exception e) { System.err.println("[MCP-MOD] HTTP server failed: " + e.getMessage()); }
        }, "MCP-HTTP").start();

        HudRenderCallback.EVENT.register(tickDelta -> {
            if (debugUrl == null && !ReflectionHelper.isMouseReleaseActive()) return;
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.currentScreen == null) {
                    ReflectionHelper.tickMouseRelease(mc);
                    ReflectionHelper.tickMcpControlMode(mc);
                    if (!ReflectionHelper.isScreenshotInProgress()) {
                        ReflectionHelper.cacheFrameFromRenderThread(mc);
                    }
                    if (ReflectionHelper.isMcpControlMode() && !ReflectionHelper.isScreenshotInProgress()) {
                        int w = mc.getWindow().getScaledWidth();
                        int h = mc.getWindow().getScaledHeight();
                        double mx = mc.mouse.getX() * w / mc.getWindow().getWidth();
                        double my = mc.mouse.getY() * h / mc.getWindow().getHeight();
                        McpOverlayLogic.renderResumeButton(wrapRenderer(mc), mc.textRenderer, new TranslatableText("mcpmod.control.resume").asString(), w, h, (int) mx, (int) my);
                    }
                } else if (ReflectionHelper.isMcpControlMode()) {
                    ReflectionHelper.cacheFrameFromRenderThread(mc);
                    int w = mc.getWindow().getScaledWidth();
                    int h = mc.getWindow().getScaledHeight();
                    double mx = mc.mouse.getX() * w / mc.getWindow().getWidth();
                    double my = mc.mouse.getY() * h / mc.getWindow().getHeight();
                    McpOverlayLogic.renderResumeButton(wrapRenderer(mc), mc.textRenderer, new TranslatableText("mcpmod.control.resume").asString(), w, h, (int) mx, (int) my);
                } else if (mc.world != null && mc.currentScreen != null && !(mc.currentScreen instanceof GameMenuScreen)) {
                    int w = mc.getWindow().getScaledWidth();
                    int h = mc.getWindow().getScaledHeight();
                    double mx = mc.mouse.getX() * w / mc.getWindow().getWidth();
                    double my = mc.mouse.getY() * h / mc.getWindow().getHeight();
                    McpOverlayLogic.renderTransferButton(wrapRenderer(mc), mc.textRenderer, new TranslatableText("mcpmod.control.pause_button").asString(), w, h, (int) mx, (int) my);
                }
            } catch (Exception ignored) {}
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (debugUrl == null) return;
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                ReflectionHelper.tickMouseRelease(mc);
                ReflectionHelper.tickMcpControlMode(mc);
                ReflectionHelper.tickVideoCapture(mc);
                McpPlatformControl ctrl = McpControlFactory.get();
                if (ctrl instanceof McpWin32Control w32ctrl) {
                    if (w32ctrl.getMcHwnd() == 0) {
                        long glfwHandle = mc.getWindow().getHandle();
                        w32ctrl.ensureHwndFromGlfw(glfwHandle);
                    }
                }
            } catch (Exception ignored) {}

            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                Screen current = mc.currentScreen;
                if (current instanceof GameMenuScreen && current != lastPatchedScreen) {
                    lastPatchedScreen = current;
                    McpScreenHelper.patchPauseScreen(current, new McpScreenHelper.ButtonFactory() {
                        @Override public Object createButton(String translationKey, Runnable onClick, int x, int y, int w, int h) {
                            return new ButtonWidget(x, y, w, h, new TranslatableText(translationKey).asString(), btn -> onClick.run());
                        }
                    });
                }
                if (current == null) {
                    lastPatchedScreen = null;
                }
            } catch (Exception ignored) {}

            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                long window = mc.getWindow().getHandle();
                boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;
                if (leftDown && !lastLeftDown) {
                    if (ReflectionHelper.isMcpControlMode()) {
                        int w = mc.getWindow().getScaledWidth();
                        int h = mc.getWindow().getScaledHeight();
                        double mx = mc.mouse.getX() * w / mc.getWindow().getWidth();
                        double my = mc.mouse.getY() * h / mc.getWindow().getHeight();
                        ReflectionHelper.handleOverlayClick((int) mx, (int) my, mc);
                    } else if (mc.world != null && mc.currentScreen != null && !(mc.currentScreen instanceof GameMenuScreen)) {
                        int w = mc.getWindow().getScaledWidth();
                        int h = mc.getWindow().getScaledHeight();
                        double mx = mc.mouse.getX() * w / mc.getWindow().getWidth();
                        double my = mc.mouse.getY() * h / mc.getWindow().getHeight();
                        ReflectionHelper.handleTransferOverlayClick((int) mx, (int) my, mc);
                    }
                }
                lastLeftDown = leftDown;
            } catch (Exception ignored) {}

            if (chatSent) return;
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.inGameHud == null) return;
                chatSent = true;
                String url = debugUrl;
                mc.inGameHud.getChatHud().addMessage(new LiteralText("[MCP] Debug page: " + url).formatted(Formatting.WHITE));
            } catch (Exception ignored) {}
        });
    }
}

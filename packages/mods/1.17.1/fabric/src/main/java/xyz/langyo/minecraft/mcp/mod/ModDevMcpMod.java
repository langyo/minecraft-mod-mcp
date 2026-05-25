package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.ClickEvent;

public class ModDevMcpMod implements ClientModInitializer {
    public static ModDevMcpMod INSTANCE;
    McpHttpServer httpServer;
    volatile String debugUrl = null;
    volatile boolean chatSent = false;

    private static McpRenderer wrapRenderer(MatrixStack ms, MinecraftClient mc) {
        return new McpRenderer() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {
                DrawableHelper.fill(ms, x1, y1, x2, y2, color);
            }
            @Override public int drawString(Object font, String text, int x, int y, int color, boolean shadow) {
                TextRenderer tr = mc.textRenderer;
                if (shadow) {
                    tr.drawWithShadow(ms, text, x, y, color);
                } else {
                    tr.draw(ms, text, x, y, color);
                }
                return tr.getWidth(text);
            }
            @Override public int getStringWidth(Object font, String text) {
                return mc.textRenderer.getWidth(text);
            }
        };
    }

    static double getMouseX(MinecraftClient mc) {
        return mc.mouse.getX() * mc.getWindow().getScaledWidth() / (double) mc.getWindow().getWidth();
    }

    static double getMouseY(MinecraftClient mc) {
        return mc.mouse.getY() * mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight();
    }

    public void onInGameHudRender(MatrixStack ms, float tickDelta) {
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
                    int mx = (int) getMouseX(mc);
                    int my = (int) getMouseY(mc);
                    McpOverlayLogic.renderResumeButton(wrapRenderer(ms, mc), mc.textRenderer,
                            new TranslatableText("mcpmod.control.resume").getString(), w, h, mx, my);
                }
            }
        } catch (Exception ignored) {}
    }

    public void onScreenRender(MatrixStack ms, Screen screen, int mouseX, int mouseY, float tickDelta) {
        if (ReflectionHelper.isScreenshotInProgress()) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            int w = mc.getWindow().getScaledWidth();
            int h = mc.getWindow().getScaledHeight();
            int mx = (int) getMouseX(mc);
            int my = (int) getMouseY(mc);
            if (ReflectionHelper.isMcpControlMode()) {
                ReflectionHelper.cacheFrameFromRenderThread(mc);
                String resumeLabel = new TranslatableText("mcpmod.control.resume").getString();
                if (resumeLabel.equals("mcpmod.control.resume")) resumeLabel = "Resume Control";
                McpOverlayLogic.renderResumeButton(wrapRenderer(ms, mc), mc.textRenderer, resumeLabel, w, h, mx, my);
            } else if (mc.world != null && screen != null) {
                String transferLabel = new TranslatableText("mcpmod.control.pause_button").getString();
                if (transferLabel.equals("mcpmod.control.pause_button")) transferLabel = "Transfer to MCP";
                McpOverlayLogic.renderTransferButton(wrapRenderer(ms, mc), mc.textRenderer, transferLabel, w, h, mx, my);
            }
        } catch (Exception ignored) {}
    }

    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            int mx = (int) mouseX;
            int my = (int) mouseY;
            if (ReflectionHelper.shouldSuppressInput()) return true;
            if (ReflectionHelper.isMcpControlMode()) {
                if (button == 0) {
                    String result = ReflectionHelper.handleOverlayClick(mx, my, mc);
                    if (!result.equals("blocked") && !result.equals("cooldown") && !result.equals("not_in_control_mode")) {
                        return true;
                    }
                }
                return true;
            }
            if (mc.world != null && mc.currentScreen != null && !(mc.currentScreen instanceof GameMenuScreen) && button == 0) {
                if (ReflectionHelper.handleTransferOverlayClick(mx, my, mc).equals("transfer_to_mcp")) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public boolean onMouseButtonEvent(MinecraftClient mc, double mx, double my, int button) {
        return onMouseClicked(mx, my, button);
    }

    public void onClientTick() {
        if (INSTANCE == null || INSTANCE.debugUrl == null) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            ReflectionHelper.tickMouseRelease(mc);
            ReflectionHelper.tickMcpControlMode(mc);
            ReflectionHelper.tickVideoCapture(mc);
        } catch (Exception ignored) {}
        if (INSTANCE.chatSent) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.inGameHud == null || mc.inGameHud.getChatHud() == null) return;
            INSTANCE.chatSent = true;
            String url = INSTANCE.debugUrl;
            Text msg = new LiteralText("")
                .append(new LiteralText("[MCP] Debug page: ").setStyle(Style.EMPTY.withColor(0xFFFFFF)))
                .append(new LiteralText(url).setStyle(Style.EMPTY
                    .withColor(0xFFFFFF)
                    .withUnderline(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                ));
            mc.inGameHud.getChatHud().addMessage(msg);
        } catch (Exception ignored) {}
    }

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                ReflectedInputHandler handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
                int port = McpConfig.getServerPort();
                httpServer = new McpHttpServer(handler, port);
                httpServer.start();
                debugUrl = "http://127.0.0.1:" + port + "/debug";
                System.out.println("[MCP-MOD] Debug page: " + debugUrl);
            } catch (Exception e) {
                System.err.println("[MCP-MOD] HTTP server failed: " + e.getMessage());
            } catch (Error e) {
                System.err.println("[MCP-MOD] HTTP server error: " + e.getMessage());
            }
        }, "MCP-HTTP").start();
    }
}

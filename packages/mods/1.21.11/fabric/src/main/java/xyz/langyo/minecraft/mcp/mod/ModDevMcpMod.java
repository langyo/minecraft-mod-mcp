package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent;
import org.lwjgl.glfw.GLFW;

public class ModDevMcpMod implements ClientModInitializer {
    public static ModDevMcpMod INSTANCE;
    McpHttpServer httpServer;
    volatile String debugUrl = null;
    volatile boolean chatSent = false;
    private volatile boolean prevLeftPressed = false;
    private static org.lwjgl.glfw.GLFWCursorPosCallbackI originalCursorCallback = null;
    private static org.lwjgl.glfw.GLFWMouseButtonCallbackI originalMouseButtonCallback = null;
    private static boolean glfwInterceptorsInstalled = false;
    private static boolean wasInControlMode = false;
    private static volatile boolean suppressCursorCallback = false;

    private static McpRenderer wrapRenderer(net.minecraft.client.gui.DrawContext ctx, MinecraftClient mc) {
        return new McpRenderer() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {
                ctx.fill(x1, y1, x2, y2, color);
            }
            @Override public int drawString(Object font, String text, int x, int y, int color, boolean shadow) {
                ctx.drawText(mc.textRenderer, text, x, y, color, shadow);
                return mc.textRenderer.getWidth(text);
            }
            @Override public int getStringWidth(Object font, String text) {
                return mc.textRenderer.getWidth(text);
            }
        };
    }

    static double getMouseX(MinecraftClient mc) {
        if (ReflectionHelper.isMcpControlMode() && mc.currentScreen == null) {
            double[] xpos = new double[1];
            org.lwjgl.glfw.GLFW.glfwGetCursorPos(mc.getWindow().getHandle(), xpos, new double[1]);
            return xpos[0] * mc.getWindow().getScaledWidth() / (double) mc.getWindow().getWidth();
        }
        return mc.mouse.getX() * mc.getWindow().getScaledWidth() / (double) mc.getWindow().getWidth();
    }

    static double getMouseY(MinecraftClient mc) {
        if (ReflectionHelper.isMcpControlMode() && mc.currentScreen == null) {
            double[] ypos = new double[1];
            org.lwjgl.glfw.GLFW.glfwGetCursorPos(mc.getWindow().getHandle(), new double[1], ypos);
            return ypos[0] * mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight();
        }
        return mc.mouse.getY() * mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight();
    }

    private static void ensureGlfwInterceptors(MinecraftClient mc) {
        if (glfwInterceptorsInstalled) return;
        try {
            long handle = mc.getWindow().getHandle();

            originalCursorCallback = GLFW.glfwSetCursorPosCallback(handle, (window, xpos, ypos) -> {
                if (suppressCursorCallback) {
                    suppressCursorCallback = false;
                    return;
                }
                if (ReflectionHelper.isMcpControlMode()) {
                    MinecraftClient curMc = MinecraftClient.getInstance();
                    if (curMc != null && curMc.currentScreen == null) {
                        return;
                    }
                }
                if (originalCursorCallback != null) {
                    originalCursorCallback.invoke(window, xpos, ypos);
                }
            });

            originalMouseButtonCallback = GLFW.glfwSetMouseButtonCallback(handle, (window, button, action, mods) -> {
                if (ReflectionHelper.isMcpControlMode()) {
                    MinecraftClient curMc = MinecraftClient.getInstance();
                    if (curMc != null && curMc.currentScreen == null) {
                        if (button == GLFW.GLFW_MOUSE_BUTTON_1 && action == GLFW.GLFW_PRESS) {
                            int mx = (int) getMouseX(curMc);
                            int my = (int) getMouseY(curMc);
                            String result = ReflectionHelper.handleOverlayClick(mx, my, curMc);
                            System.out.println("[MCP-MOD] GLFW click mx=" + mx + " my=" + my + " result=" + result);
                        }
                        if (button == GLFW.GLFW_MOUSE_BUTTON_1) return;
                    }
                }
                if (originalMouseButtonCallback != null) {
                    originalMouseButtonCallback.invoke(window, button, action, mods);
                }
            });

            glfwInterceptorsInstalled = true;
            System.out.println("[MCP-MOD] GLFW interceptors installed");
        } catch (Exception e) {
            System.err.println("[MCP-MOD] GLFW interceptor failed: " + e.getMessage());
        }
    }

    private static void forceGlfwMouseFree(MinecraftClient mc) {
        try {
            long handle = mc.getWindow().getHandle();
            if (GLFW.glfwGetInputMode(handle, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_NORMAL) {
                GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            }
        } catch (Exception ignored) {}
    }

    private static void forceGlfwMouseGrab(MinecraftClient mc) {
        try {
            long handle = mc.getWindow().getHandle();
            suppressCursorCallback = true;
            GLFW.glfwSetCursorPos(handle, mc.getWindow().getWidth() / 2.0, mc.getWindow().getHeight() / 2.0);
            GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        } catch (Exception ignored) {}
    }

    public void onInGameHudRender(net.minecraft.client.gui.DrawContext ctx, float tickDelta) {
        if (debugUrl == null && !ReflectionHelper.isMouseReleaseActive()) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen == null) {
                ReflectionHelper.tickMouseRelease(mc);
                ReflectionHelper.tickMcpControlMode(mc);

                if (ReflectionHelper.isMcpControlMode()) {
                    ensureGlfwInterceptors(mc);
                    forceGlfwMouseFree(mc);
                } else if (glfwInterceptorsInstalled) {
                    long handle = mc.getWindow().getHandle();
                    if (GLFW.glfwGetInputMode(handle, GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_NORMAL) {
                        forceGlfwMouseGrab(mc);
                    }
                }

                if (!ReflectionHelper.isScreenshotInProgress()) {
                    ReflectionHelper.cacheFrameFromRenderThread(mc);
                }

                if (ReflectionHelper.isMcpControlMode() && !ReflectionHelper.isScreenshotInProgress()) {
                    int w = mc.getWindow().getScaledWidth();
                    int h = mc.getWindow().getScaledHeight();
                    int mx = (int) getMouseX(mc);
                    int my = (int) getMouseY(mc);
                    McpOverlayLogic.renderResumeButton(wrapRenderer(ctx, mc), mc.textRenderer,
                            "Resume Control", w, h, mx, my);
                }
            }
        } catch (Exception ignored) {}
    }

    public void onScreenRender(net.minecraft.client.gui.DrawContext ctx, Screen screen, int mouseX, int mouseY, float tickDelta) {
        if (ReflectionHelper.isScreenshotInProgress()) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (ReflectionHelper.isMcpControlMode() && screen instanceof GameMenuScreen) {
                mc.setScreen(null);
                return;
            }
            int w = mc.getWindow().getScaledWidth();
            int h = mc.getWindow().getScaledHeight();
            int mx = (int) getMouseX(mc);
            int my = (int) getMouseY(mc);
            if (ReflectionHelper.isMcpControlMode()) {
                ReflectionHelper.cacheFrameFromRenderThread(mc);
                McpOverlayLogic.renderResumeButton(wrapRenderer(ctx, mc), mc.textRenderer,
                        "Resume Control", w, h, mx, my);
            } else if (mc.world != null && screen != null) {
                McpOverlayLogic.renderTransferButton(wrapRenderer(ctx, mc), mc.textRenderer,
                        "Transfer to MCP", w, h, mx, my);
            }
                McpOverlayLogic.renderPortInfo(wrapRenderer(ctx, mc), mc.textRenderer, w, h, httpServer);
        } catch (Exception ignored) {}
    }

    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            int mx = (int) mouseX;
            int my = (int) mouseY;
            System.out.println("[MCP-MOD] onMouseClicked mx=" + mx + " my=" + my + " btn=" + button
                + " screen=" + (mc.currentScreen != null ? mc.currentScreen.getClass().getSimpleName() : "null")
                + " world=" + (mc.world != null) + " ctrl=" + ReflectionHelper.isMcpControlMode());

            if (ReflectionHelper.isWaitingForRelease()) return true;
            if (ReflectionHelper.shouldSuppressInput()) return true;

            if (ReflectionHelper.isMcpControlMode()) {
                if (button == 0) {
                    String result = ReflectionHelper.handleOverlayClick(mx, my, mc);
                    System.out.println("[MCP-MOD] handleOverlayClick result=" + result);
                    if (!result.equals("blocked") && !result.equals("cooldown") && !result.equals("not_in_control_mode")) {
                        return true;
                    }
                }
                return false;
            }

            if (mc.world != null && mc.currentScreen != null && button == 0) {
                String result = ReflectionHelper.handleTransferOverlayClick(mx, my, mc);
                System.out.println("[MCP-MOD] handleTransferOverlayClick result=" + result + " mx=" + mx + " my=" + my);
                if (result.equals("transfer_to_mcp")) {
                    return true;
                }
            }
        } catch (Exception e) {
            System.err.println("[MCP-MOD] onMouseClicked error: " + e.getMessage());
        }
        return false;
    }

    public void onClientTick() {
        if (INSTANCE == null || INSTANCE.debugUrl == null) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (ReflectionHelper.isWaitingForRelease()) {
                long window = mc.getWindow().getHandle();
                if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_1) != GLFW.GLFW_PRESS) {
                    ReflectionHelper.clearWaitingForRelease();
                }
                return;
            }
            ReflectionHelper.tickMouseRelease(mc);
            ReflectionHelper.tickMcpControlMode(mc);
            ReflectionHelper.tickVideoCapture(mc);
            boolean inCtrl = ReflectionHelper.isMcpControlMode();
            if (inCtrl && mc.currentScreen == null) {
                ensureGlfwInterceptors(mc);
                forceGlfwMouseFree(mc);
            } else if (wasInControlMode && !inCtrl && mc.currentScreen == null) {
                forceGlfwMouseGrab(mc);
            }
            wasInControlMode = inCtrl;
        } catch (Exception ignored) {}

        handleOverlayMousePoll();

        if (INSTANCE.chatSent) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.inGameHud == null || mc.inGameHud.getChatHud() == null) return;
            INSTANCE.chatSent = true;
            String url = INSTANCE.debugUrl;
            Text msg = Text.empty()
                .append(Text.literal("[MCP] Debug page: ").styled(s -> s.withColor(0xFFFFFF)))
                .append(Text.literal(url).styled(s -> s
                    .withColor(0xFFFFFF)
                    .withUnderline(true)
                    .withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(url)))
                ));
            mc.inGameHud.getChatHud().addMessage(msg);
        } catch (Exception ignored) {}
    }

    private void handleOverlayMousePoll() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getWindow() == null) return;
            long handle = mc.getWindow().getHandle();
            boolean pressed = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            if (pressed && !prevLeftPressed && mc.currentScreen == null) {
                if (ReflectionHelper.isMcpControlMode()) {
                    int mx = (int) getMouseX(mc);
                    int my = (int) getMouseY(mc);
                    String result = ReflectionHelper.handleOverlayClick(mx, my, mc);
                    System.out.println("[MCP-MOD] poll click mx=" + mx + " my=" + my + " result=" + result);
                }
            }
            prevLeftPressed = pressed;
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

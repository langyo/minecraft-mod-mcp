package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class ModDevMcpMod implements ClientModInitializer {
    public static ModDevMcpMod INSTANCE;
    McpHttpServer httpServer;
    volatile String debugUrl = null;
    volatile boolean chatSent = false;
    private volatile boolean prevLeftPressed = false;
    private static org.lwjgl.glfw.GLFWCursorPosCallbackI originalCursorCallback = null;
    private static org.lwjgl.glfw.GLFWMouseButtonCallbackI originalMouseButtonCallback = null;
    private static boolean cursorInterceptorInstalled = false;
    private static boolean mouseButtonInterceptorInstalled = false;
    private static java.lang.reflect.Field mousePosXField = null;
    private static java.lang.reflect.Field mousePosYField = null;

    private static McpRenderer wrapRenderer(MatrixStack ms, MinecraftClient mc) {
        return new McpRenderer() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {
                DrawableHelper.fill(ms, x1, y1, x2, y2, color);
            }
            @Override public int drawString(Object font, String text, int x, int y, int color, boolean shadow) {
                TextRenderer tr = mc.textRenderer;
                if (shadow) {
                    return tr.drawWithShadow(ms, text, x, y, color);
                } else {
                    return tr.draw(ms, text, x, y, color);
                }
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

    private static void ensureMouseReflection() {
        if (mousePosXField != null) return;
        try {
            java.lang.reflect.Field[] fields = net.minecraft.client.Mouse.class.getDeclaredFields();
            java.lang.reflect.Field firstDouble = null, secondDouble = null;
            for (java.lang.reflect.Field f : fields) {
                if (f.getType() == double.class) {
                    if (firstDouble == null) firstDouble = f;
                    else if (secondDouble == null) { secondDouble = f; break; }
                }
            }
            if (firstDouble != null) { firstDouble.setAccessible(true); mousePosXField = firstDouble; }
            if (secondDouble != null) { secondDouble.setAccessible(true); mousePosYField = secondDouble; }
        } catch (Exception e) {
            System.err.println("[MCP-MOD] Mouse reflection failed: " + e.getMessage());
        }
    }

    private static void ensureCursorInterceptor(MinecraftClient mc) {
        if (cursorInterceptorInstalled) return;
        try {
            long handle = mc.getWindow().getHandle();
            ensureMouseReflection();

            originalCursorCallback = GLFW.glfwSetCursorPosCallback(handle, (window, xpos, ypos) -> {
                if (ReflectionHelper.isMcpControlMode()) {
                    if (mousePosXField != null) {
                        try { mousePosXField.setDouble(mc.mouse, xpos); } catch (Exception ignored) {}
                    }
                    if (mousePosYField != null) {
                        try { mousePosYField.setDouble(mc.mouse, ypos); } catch (Exception ignored) {}
                    }
                } else if (originalCursorCallback != null) {
                    originalCursorCallback.invoke(window, xpos, ypos);
                }
            });

            cursorInterceptorInstalled = true;
            System.out.println("[MCP-MOD] Cursor interceptor installed");
        } catch (Exception e) {
            System.err.println("[MCP-MOD] Cursor interceptor failed: " + e.getMessage());
        }
    }

    private static void ensureMouseButtonInterceptor(MinecraftClient mc) {
        if (mouseButtonInterceptorInstalled) return;
        try {
            long handle = mc.getWindow().getHandle();
            if (handle == 0) return;

            originalMouseButtonCallback = GLFW.glfwSetMouseButtonCallback(handle, (window, button, action, mods) -> {
                if (ReflectionHelper.isWaitingForRelease()) {
                    if (button == 0 && action == 0) {
                        ReflectionHelper.clearWaitingForRelease();
                        if (originalMouseButtonCallback != null) {
                            originalMouseButtonCallback.invoke(window, button, action, mods);
                        }
                    }
                    return;
                }
                if (button == 0 && action == 1 && ReflectionHelper.isMcpControlMode()) {
                    MinecraftClient mc2 = MinecraftClient.getInstance();
                    double mx = getMouseX(mc2);
                    double my = getMouseY(mc2);
                    String result = ReflectionHelper.handleOverlayClick((int) mx, (int) my, mc2);
                    if (!result.equals("blocked") && !result.equals("cooldown") && !result.equals("not_in_control_mode")) {
                        if (!ReflectionHelper.isMcpControlMode() && mc2.currentScreen == null) {
                            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                            try { mc2.mouse.lockCursor(); } catch (Exception ignored2) {}
                        }
                        return;
                    }
                }
                if (originalMouseButtonCallback != null) {
                    originalMouseButtonCallback.invoke(window, button, action, mods);
                }
            });

            mouseButtonInterceptorInstalled = true;
            System.out.println("[MCP-MOD] MouseButton interceptor installed");
        } catch (Exception e) {
            System.err.println("[MCP-MOD] MouseButton interceptor failed: " + e.getMessage());
        }
    }

    private static void forceGlfwMouseFree(MinecraftClient mc) {
        ensureCursorInterceptor(mc);
        ensureMouseButtonInterceptor(mc);
        try {
            long handle = mc.getWindow().getHandle();
            if (GLFW.glfwGetInputMode(handle, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_NORMAL) {
                GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            }
        } catch (Exception ignored) {}
    }

    public void onInGameHudRender(MatrixStack ms, float tickDelta) {
        if (debugUrl == null && !ReflectionHelper.isMouseReleaseActive()) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen == null) {
                ReflectionHelper.tickMouseRelease(mc);
                ReflectionHelper.tickMcpControlMode(mc);

                if (ReflectionHelper.isMcpControlMode()) {
                    forceGlfwMouseFree(mc);
                }

                if (!ReflectionHelper.isScreenshotInProgress()) {
                    ReflectionHelper.cacheFrameFromRenderThread(mc);
                }

                if (ReflectionHelper.isMcpControlMode() && !ReflectionHelper.isScreenshotInProgress()) {
                    int w = mc.getWindow().getScaledWidth();
                    int h = mc.getWindow().getScaledHeight();
                    int mx = (int) getMouseX(mc);
                    int my = (int) getMouseY(mc);
                    McpOverlayLogic.renderResumeButton(wrapRenderer(ms, mc), mc.textRenderer,
                            "Resume Control", w, h, mx, my);
                }
            }
        } catch (Exception ignored) {}
    }

    public void onScreenRender(MatrixStack ms, Screen screen, int mouseX, int mouseY, float tickDelta) {
        if (ReflectionHelper.isScreenshotInProgress()) return;
        if (screen == null) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (ReflectionHelper.isMcpControlMode() && screen instanceof GameMenuScreen) {
                mc.openScreen(null);
                return;
            }
            if (mc.world != null) {
                if (ReflectionHelper.isMcpControlMode()) {
                    ensureCursorInterceptor(mc);
                    ensureMouseButtonInterceptor(mc);
                    ReflectionHelper.cacheFrameFromRenderThread(mc);
                }
                int w = mc.getWindow().getScaledWidth();
                int h = mc.getWindow().getScaledHeight();
                McpOverlayLogic.renderPortInfo(wrapRenderer(ms, mc), mc.textRenderer, w, h, httpServer);
                int mx = (int) getMouseX(mc);
                int my = (int) getMouseY(mc);
                if (ReflectionHelper.isMcpControlMode()) {
                    McpOverlayLogic.renderResumeButton(wrapRenderer(ms, mc), mc.textRenderer,
                            "Resume Control", w, h, mx, my);
                } else {
                    McpOverlayLogic.renderTransferButton(wrapRenderer(ms, mc), mc.textRenderer,
                            "Transfer to MCP", w, h, mx, my);
                }
            }
        } catch (Exception ignored) {}
    }

    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            ensureMouseButtonInterceptor(mc);
            if (ReflectionHelper.isWaitingForRelease()) return true;
            if (ReflectionHelper.isMcpControlMode()) return false;
            if (mc.world != null && mc.currentScreen != null && button == 0) {
                if (ReflectionHelper.handleTransferOverlayClick((int) mouseX, (int) mouseY, mc).equals("transfer_to_mcp")) {
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
        } catch (Exception ignored) {}

        handleOverlayMousePoll();

        if (INSTANCE.chatSent) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.inGameHud == null) return;
            INSTANCE.chatSent = true;
            String url = INSTANCE.debugUrl;
            mc.inGameHud.getChatHud().addMessage(
                    new LiteralText("[MCP] Debug page: " + url).formatted(Formatting.WHITE));
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
                    ReflectionHelper.handleOverlayClick(mx, my, mc);
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

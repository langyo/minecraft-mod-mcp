package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.Window;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.text.TranslatableText;
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
    private static boolean glfwInterceptorsInstalled = false;
    private static boolean wasInControlMode = false;
    private static volatile boolean suppressCursorCallback = false;
    private static java.lang.reflect.Field mousePosXField = null;
    private static java.lang.reflect.Field mousePosYField = null;

    private static McpRenderer wrapRenderer(MinecraftClient mc) {
        return new McpRenderer() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {
                DrawableHelper.fill(x1, y1, x2, y2, color);
            }
            @Override public int drawString(Object font, String text, int x, int y, int color, boolean shadow) {
                TextRenderer tr = mc.textRenderer;
                if (shadow) {
                    return tr.drawWithShadow(text, x, y, color);
                } else {
                    return tr.draw(text, x, y, color);
                }
            }
            @Override public int getStringWidth(Object font, String text) {
                return mc.textRenderer.getStringWidth(text);
            }
        };
    }

    static double getMouseX(MinecraftClient mc) {
        return mc.mouse.getX() * mc.window.getScaledWidth() / (double) mc.window.getWidth();
    }

    static double getMouseY(MinecraftClient mc) {
        return mc.mouse.getY() * mc.window.getScaledHeight() / (double) mc.window.getHeight();
    }

    private static void initMouseReflection(MinecraftClient mc) {
        if (mousePosXField != null) return;
        try {
            java.lang.reflect.Field[] fields = mc.mouse.getClass().getDeclaredFields();
            java.lang.reflect.Field firstDouble = null, secondDouble = null;
            for (java.lang.reflect.Field f : fields) {
                if (f.getType() == double.class) {
                    if (firstDouble == null) firstDouble = f;
                    else if (secondDouble == null) { secondDouble = f; break; }
                }
            }
            if (firstDouble != null) { firstDouble.setAccessible(true); mousePosXField = firstDouble; }
            if (secondDouble != null) { secondDouble.setAccessible(true); mousePosYField = secondDouble; }
        } catch (Exception ignored) {}
    }

    private static void ensureGlfwInterceptors(MinecraftClient mc) {
        if (glfwInterceptorsInstalled) return;
        initMouseReflection(mc);
        try {
            long handle = mc.window.getHandle();

            originalCursorCallback = GLFW.glfwSetCursorPosCallback(handle, (window, xpos, ypos) -> {
                MinecraftClient curMc = MinecraftClient.getInstance();
                if (curMc == null) return;
                if (suppressCursorCallback) {
                    suppressCursorCallback = false;
                    return;
                }
                if (ReflectionHelper.isMcpControlMode() && curMc.currentScreen == null) {
                    if (mousePosXField != null) {
                        try { mousePosXField.setDouble(curMc.mouse, xpos); } catch (Exception ignored) {}
                    }
                    if (mousePosYField != null) {
                        try { mousePosYField.setDouble(curMc.mouse, ypos); } catch (Exception ignored) {}
                    }
                    return;
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
                            if (!result.equals("blocked") && !result.equals("cooldown") && !result.equals("not_in_control_mode")) {
                                if (!ReflectionHelper.isMcpControlMode() && curMc.currentScreen == null) {
                                    GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                                }
                                return;
                            }
                        }
                        if (button == GLFW.GLFW_MOUSE_BUTTON_1) return;
                    }
                }
                if (originalMouseButtonCallback != null) {
                    originalMouseButtonCallback.invoke(window, button, action, mods);
                }
            });

            glfwInterceptorsInstalled = true;
        } catch (Exception ignored) {}
    }

    private static void forceGlfwMouseFree(MinecraftClient mc) {
        ensureGlfwInterceptors(mc);
        try {
            long handle = mc.window.getHandle();
            if (GLFW.glfwGetInputMode(handle, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_NORMAL) {
                GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            }
        } catch (Exception ignored) {}
    }

    private static void forceGlfwMouseGrab(MinecraftClient mc) {
        try {
            long handle = mc.window.getHandle();
            suppressCursorCallback = true;
            GLFW.glfwSetCursorPos(handle, mc.window.getWidth() / 2.0, mc.window.getHeight() / 2.0);
            GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        } catch (Exception ignored) {}
    }

    public void onInGameHudRender(InGameHud hud, float tickDelta) {
        if (debugUrl == null && !ReflectionHelper.isMouseReleaseActive()) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen == null) {
                ReflectionHelper.tickMouseRelease(mc);
                ReflectionHelper.tickMcpControlMode(mc);

                if (ReflectionHelper.isMcpControlMode()) {
                    forceGlfwMouseFree(mc);
                } else if (glfwInterceptorsInstalled) {
                    long handle = mc.window.getHandle();
                    if (GLFW.glfwGetInputMode(handle, GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_NORMAL) {
                        forceGlfwMouseGrab(mc);
                    }
                }

                if (!ReflectionHelper.isScreenshotInProgress()) {
                    ReflectionHelper.cacheFrameFromRenderThread(mc);
                }

                if (ReflectionHelper.isMcpControlMode() && !ReflectionHelper.isScreenshotInProgress()) {
                    int w = mc.window.getScaledWidth();
                    int h = mc.window.getScaledHeight();
                    int mx = (int) getMouseX(mc);
                    int my = (int) getMouseY(mc);
                    McpOverlayLogic.renderResumeButton(wrapRenderer(mc), mc.textRenderer,
                            "Resume Control", w, h, mx, my);
                }
            }
        } catch (Exception ignored) {}
    }

    public void onScreenRender(Screen screen, int mouseX, int mouseY, float tickDelta) {
        if (ReflectionHelper.isScreenshotInProgress()) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (ReflectionHelper.isMcpControlMode() && screen instanceof GameMenuScreen) {
                mc.openScreen(null);
                return;
            }
            int w = mc.window.getScaledWidth();
            int h = mc.window.getScaledHeight();
            if (httpServer != null) {
                int port = httpServer.getPort();
                String portText = "MCP Port at " + port;
                int y = h - 12 - mc.textRenderer.fontHeight * 2;
                mc.textRenderer.draw(portText, 2, y, 0xFFCCCCCC);
                }
            int mx = (int) getMouseX(mc);
            int my = (int) getMouseY(mc);
            if (ReflectionHelper.isMcpControlMode()) {
                ReflectionHelper.cacheFrameFromRenderThread(mc);
                McpOverlayLogic.renderResumeButton(wrapRenderer(mc), mc.textRenderer,
                        "Resume Control", w, h, mx, my);
            } else if (mc.world != null && screen != null) {
                McpOverlayLogic.renderTransferButton(wrapRenderer(mc), mc.textRenderer,
                        "Transfer to MCP", w, h, mx, my);
            }
        } catch (Exception ignored) {}
    }

    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (ReflectionHelper.isWaitingForRelease()) return true;
            if (ReflectionHelper.shouldSuppressInput()) return true;
            int mx = (int) mouseX;
            int my = (int) mouseY;
            if (ReflectionHelper.isMcpControlMode()) {
                if (button == 0) {
                    String result = ReflectionHelper.handleOverlayClick(mx, my, mc);
                    if (!result.equals("blocked") && !result.equals("cooldown") && !result.equals("not_in_control_mode")) {
                        return true;
                    }
                }
                return false;
            }
            if (mc.world != null && mc.currentScreen != null && button == 0) {
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
            if (ReflectionHelper.isWaitingForRelease()) {
                long window = mc.window.getHandle();
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
            if (mc.window == null) return;
            long handle = mc.window.getHandle();
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

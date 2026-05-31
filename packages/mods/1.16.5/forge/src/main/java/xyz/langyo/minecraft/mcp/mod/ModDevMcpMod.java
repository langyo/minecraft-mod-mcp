package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.minecraftforge.fml.common.Mod;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.IngameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.Style;
import com.mojang.blaze3d.matrix.MatrixStack;
import org.lwjgl.glfw.GLFW;

@Mod("mcpmod")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    McpHttpServer httpServer;
    volatile String debugUrl = null;
    volatile boolean chatSent = false;

    private static org.lwjgl.glfw.GLFWCursorPosCallbackI originalCursorCallback = null;
    private static org.lwjgl.glfw.GLFWMouseButtonCallbackI originalMouseButtonCallback = null;
    private static boolean cursorInterceptorInstalled = false;
    private static java.lang.reflect.Field mousePosXField = null;
    private static java.lang.reflect.Field mousePosYField = null;

    private static void ensureCursorInterceptor(Minecraft mc) {
        if (cursorInterceptorInstalled) return;
        try {
            long handle = mc.getMainWindow().getHandle();
            originalCursorCallback = GLFW.glfwSetCursorPosCallback(handle, (window, xpos, ypos) -> {
                if (ReflectionHelper.isMcpControlMode()) {
                    try {
                        if (mousePosXField != null) mousePosXField.setDouble(mc.mouseHelper, xpos);
                        if (mousePosYField != null) mousePosYField.setDouble(mc.mouseHelper, ypos);
                    } catch (Exception ignored) {}
                } else if (originalCursorCallback != null) {
                    originalCursorCallback.invoke(window, xpos, ypos);
                }
            });
            java.lang.reflect.Field[] fields = mc.mouseHelper.getClass().getDeclaredFields();
            java.lang.reflect.Field firstDouble = null, secondDouble = null;
            for (java.lang.reflect.Field f : fields) {
                if (f.getType() == double.class) {
                    if (firstDouble == null) firstDouble = f;
                    else if (secondDouble == null) { secondDouble = f; break; }
                }
            }
            if (firstDouble != null) { firstDouble.setAccessible(true); mousePosXField = firstDouble; }
            if (secondDouble != null) { secondDouble.setAccessible(true); mousePosYField = secondDouble; }
            cursorInterceptorInstalled = true;
        } catch (Exception e) {
            System.err.println("[MCP-MOD] Cursor interceptor failed: " + e.getMessage());
        }
    }

    private static void forceGlfwMouseFree(Minecraft mc) {
        ensureCursorInterceptor(mc);
        try {
            long hwnd = mc.getMainWindow().getHandle();
            if (GLFW.glfwGetInputMode(hwnd, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_NORMAL) {
                GLFW.glfwSetInputMode(hwnd, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            }
        } catch (Exception ignored) {}
    }

    private static McpRenderer wrapRenderer(MatrixStack ms, Minecraft mc) {
        return new McpRenderer() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {
                AbstractGui.fill(ms, x1, y1, x2, y2, color);
            }
            @Override public int drawString(Object font, String text, int x, int y, int color, boolean shadow) {
                FontRenderer fr = mc.fontRenderer;
                if (shadow) {
                    return fr.drawStringWithShadow(ms, text, x, y, color);
                } else {
                    return fr.drawString(ms, text, x, y, color);
                }
            }
            @Override public int getStringWidth(Object font, String text) {
                return mc.fontRenderer.getStringWidth(text);
            }
        };
    }

    private static void renderScreenButton(MatrixStack ms, Minecraft mc, Screen screen) {
        int w = mc.getMainWindow().getScaledWidth();
        int h = mc.getMainWindow().getScaledHeight();
        double mx = getMouseX(mc);
        double my = getMouseY(mc);
        if (ReflectionHelper.isMcpControlMode()) {
            ReflectionHelper.cacheFrameFromRenderThread(mc);
            McpOverlayLogic.renderResumeButton(wrapRenderer(ms, mc), mc.fontRenderer, new TranslationTextComponent("mcpmod.control.resume").getString(), w, h, (int) mx, (int) my);
        } else if (screen != null) {
            McpOverlayLogic.renderTransferButton(wrapRenderer(ms, mc), mc.fontRenderer, new TranslationTextComponent("mcpmod.control.pause_button").getString(), w, h, (int) mx, (int) my);
        }
    }

    private static double getMouseX(Minecraft mc) {
        return mc.mouseHelper.getMouseX() * mc.getMainWindow().getScaledWidth() / mc.getMainWindow().getWidth();
    }

    private static double getMouseY(Minecraft mc) {
        return mc.mouseHelper.getMouseY() * mc.getMainWindow().getScaledHeight() / mc.getMainWindow().getHeight();
    }

    @SuppressWarnings("removal")
    public ModDevMcpMod() {
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

        MinecraftForge.EVENT_BUS.addListener((GuiOpenEvent event) -> {
            if (ReflectionHelper.isMcpControlMode() && event.getGui() instanceof IngameMenuScreen) {
                event.setCanceled(true);
            }
        });

        MinecraftForge.EVENT_BUS.addListener((RenderGameOverlayEvent.Post event) -> {
            if (event.getType() != RenderGameOverlayEvent.ElementType.CHAT) return;
            if (debugUrl == null && !ReflectionHelper.isMouseReleaseActive()) return;
            try {
                Minecraft mc = Minecraft.getInstance();
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
                        MatrixStack ms = new MatrixStack();
                        int w = mc.getMainWindow().getScaledWidth();
                        int h = mc.getMainWindow().getScaledHeight();
                        double mx = getMouseX(mc);
                        double my = getMouseY(mc);
                        McpOverlayLogic.renderResumeButton(wrapRenderer(ms, mc), mc.fontRenderer, new TranslationTextComponent("mcpmod.control.resume").getString(), w, h, (int) mx, (int) my);
                    }
                }
            } catch (Exception ignored) {}
        });

        MinecraftForge.EVENT_BUS.addListener((GuiScreenEvent.DrawScreenEvent.Post event) -> {
            if (ReflectionHelper.isScreenshotInProgress()) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                Screen screen = event.getGui();
                if (screen == null) return;
                if (ReflectionHelper.isMcpControlMode() && screen instanceof IngameMenuScreen) {
                    mc.currentScreen = null;
                    return;
                }
                if (mc.world != null) {
                    MatrixStack ms = new MatrixStack();
                    renderScreenButton(ms, mc, screen);
                }
            } catch (Exception e) { e.printStackTrace(); }
        });

        MinecraftForge.EVENT_BUS.addListener((GuiScreenEvent.MouseClickedEvent.Pre event) -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                ensureMouseButtonInterceptor(mc);
                if (ReflectionHelper.isWaitingForRelease()) {
                    event.setCanceled(true);
                    return;
                }
                if (ReflectionHelper.isMcpControlMode()) {
                    return;
                }
                if (mc.world != null && mc.currentScreen != null && event.getButton() == 0) {
                    double mx = getMouseX(mc);
                    double my = getMouseY(mc);
                    if (ReflectionHelper.handleTransferOverlayClick((int) mx, (int) my, mc).equals("transfer_to_mcp")) {
                        event.setCanceled(true);
                    }
                }
            } catch (Exception ignored) {}
        });

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (INSTANCE == null || INSTANCE.debugUrl == null) return;
            if (event.phase != TickEvent.Phase.END) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                if (ReflectionHelper.isWaitingForRelease()) {
                    long window = mc.getMainWindow().getHandle();
                    if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_1) != GLFW.GLFW_PRESS) {
                        ReflectionHelper.clearWaitingForRelease();
                    }
                    return;
                }
                ReflectionHelper.tickMouseRelease(mc);
                ReflectionHelper.tickMcpControlMode(mc);
                ReflectionHelper.tickVideoCapture(mc);
            } catch (Exception ignored) {}
            if (INSTANCE.chatSent) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.ingameGUI == null) return;
                INSTANCE.chatSent = true;
                String url = INSTANCE.debugUrl;
                mc.ingameGUI.getChatGUI().printChatMessage(new StringTextComponent("[MCP] Debug page: " + url).mergeStyle(TextFormatting.WHITE));
            } catch (Exception ignored) {}
        });
    }

    private static boolean mouseButtonInterceptorInstalled = false;

    private static void ensureMouseButtonInterceptor(Minecraft mc) {
        if (mouseButtonInterceptorInstalled) return;
        try {
            long handle = mc.getMainWindow().getHandle();
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
                    Minecraft mc2 = Minecraft.getInstance();
                    double mx = getMouseX(mc2);
                    double my = getMouseY(mc2);
                    String result = ReflectionHelper.handleOverlayClick((int) mx, (int) my, mc2);
                    if (!result.equals("blocked") && !result.equals("cooldown") && !result.equals("not_in_control_mode")) {
                        if (!ReflectionHelper.isMcpControlMode() && mc2.currentScreen == null) {
                            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                            try { mc2.mouseHelper.grabMouse(); } catch (Exception ignored2) {}
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
}

package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import org.lwjgl.glfw.GLFW;

@Mod("mcpmod")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    McpHttpServer httpServer;
    volatile String debugUrl = null;
    volatile boolean chatSent = false;

    private static org.lwjgl.glfw.GLFWCursorPosCallbackI originalCursorCallback = null;
    private static org.lwjgl.glfw.GLFWMouseButtonCallbackI originalMouseButtonCallback = null;
    private static boolean mouseInterceptorInstalled = false;
    private static java.lang.reflect.Field mousePosXField = null;
    private static java.lang.reflect.Field mousePosYField = null;

    private static void ensureMouseInterceptor(Minecraft mc) {
        if (mouseInterceptorInstalled) return;
        try {
            long handle = mc.getWindow().getWindow();
            originalCursorCallback = GLFW.glfwSetCursorPosCallback(handle, (window, xpos, ypos) -> {
                if (ReflectionHelper.isMcpControlMode()) {
                    try {
                        if (mousePosXField != null) mousePosXField.setDouble(mc.mouseHandler, xpos);
                        if (mousePosYField != null) mousePosYField.setDouble(mc.mouseHandler, ypos);
                    } catch (Exception ignored) {}
                    return;
                }
                if (originalCursorCallback != null) {
                    originalCursorCallback.invoke(window, xpos, ypos);
                }
            });
            java.lang.reflect.Field[] fields = mc.mouseHandler.getClass().getDeclaredFields();
            java.lang.reflect.Field firstDouble = null, secondDouble = null;
            for (java.lang.reflect.Field f : fields) {
                if (f.getType() == double.class) {
                    if (firstDouble == null) firstDouble = f;
                    else if (secondDouble == null) { secondDouble = f; break; }
                }
            }
            if (firstDouble != null) { firstDouble.setAccessible(true); mousePosXField = firstDouble; }
            if (secondDouble != null) { secondDouble.setAccessible(true); mousePosYField = secondDouble; }
            mouseInterceptorInstalled = true;
        } catch (Exception e) {
            System.err.println("[MCP-MOD] Mouse interceptor failed: " + e.getMessage());
        }
    }

    private static McpRenderer wrapRenderer(GuiGraphics g, Minecraft mc) {
        return new McpRenderer() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {
                g.fill(x1, y1, x2, y2, color);
            }
            @Override public int drawString(Object font, String text, int x, int y, int color, boolean shadow) {
                g.drawString(mc.font, text, x, y, color, shadow);
                return mc.font.width(text);
            }
            @Override public int getStringWidth(Object font, String text) {
                return mc.font.width(text);
            }
        };
    }

    private static void withFullScissor(GuiGraphics g, int w, int h, Runnable action) {
        try {
            Object stack = g.getClass().getMethod("getScissorStack").invoke(g);
            java.lang.reflect.Method peekM = null, pushM = null, popM = null;
            for (java.lang.reflect.Method m : stack.getClass().getDeclaredMethods()) {
                if (m.getName().equals("peek")) peekM = m;
                if (m.getName().equals("push") && m.getParameterCount() == 1) pushM = m;
                if (m.getName().equals("pop") && m.getParameterCount() == 0) popM = m;
            }
            Object saved = peekM != null ? peekM.invoke(stack) : null;
            if (popM != null) popM.invoke(stack);
            Object fullRect = new net.minecraft.client.gui.navigation.ScreenRectangle(0, 0, w, h);
            if (pushM != null) pushM.invoke(stack, fullRect);
            action.run();
            if (popM != null) popM.invoke(stack);
            if (saved != null && pushM != null) pushM.invoke(stack, saved);
        } catch (Exception e) {
            action.run();
        }
    }

    private static void renderScreenButton(GuiGraphics g, Minecraft mc, Screen screen) {
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        double mx = getMouseX(mc);
        double my = getMouseY(mc);
        if (ReflectionHelper.isMcpControlMode()) {
            ReflectionHelper.cacheFrameFromRenderThread(mc);
            McpOverlayLogic.renderResumeButton(wrapRenderer(g, mc), mc.font, Component.translatable("mcpmod.control.resume").getString(), w, h, (int) mx, (int) my);
        } else if (screen != null) {
            McpOverlayLogic.renderTransferButton(wrapRenderer(g, mc), mc.font, Component.translatable("mcpmod.control.pause_button").getString(), w, h, (int) mx, (int) my);
        }
    }

    private static double getMouseX(Minecraft mc) {
        return mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
    }

    private static double getMouseY(Minecraft mc) {
        return mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
    }

    @SuppressWarnings("removal")
    public ModDevMcpMod() {
        INSTANCE = this;
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);

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

        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Opening event) -> {
            if (ReflectionHelper.isMcpControlMode() && event.getScreen() instanceof PauseScreen) {
                event.setCanceled(true);
            }
        });

        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.MouseButtonPressed.Pre event) -> {
            if (!ReflectionHelper.isMcpControlMode()) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                double mx = getMouseX(mc);
                double my = getMouseY(mc);
                String result = ReflectionHelper.handleOverlayClick((int) mx, (int) my, mc);
                if (!result.equals("blocked") && !result.equals("cooldown") && !result.equals("not_in_control_mode")) {
                    event.setCanceled(true);
                    if (!ReflectionHelper.isMcpControlMode() && mc.screen == null) {
                        GLFW.glfwSetInputMode(mc.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                        try { mc.mouseHandler.grabMouse(); } catch (Exception ignored2) {}
                    }
                }
                    McpOverlayLogic.renderPortInfo(wrapRenderer(g, mc), mc.font, w, h, httpServer);
            } catch (Exception ignored) {}
        });

        MinecraftForge.EVENT_BUS.addListener((CustomizeGuiOverlayEvent.Chat event) -> {
            if (debugUrl == null && !ReflectionHelper.isMouseReleaseActive()) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == null) {
                    ensureMouseInterceptor(mc);
                    ReflectionHelper.tickMouseRelease(mc);
                    ReflectionHelper.tickMcpControlMode(mc);

                    if (!ReflectionHelper.isScreenshotInProgress()) {
                        ReflectionHelper.cacheFrameFromRenderThread(mc);
                    }

                    if (ReflectionHelper.isMcpControlMode() && !ReflectionHelper.isScreenshotInProgress()) {
                        GuiGraphics g = event.getGuiGraphics();
                        int w = mc.getWindow().getGuiScaledWidth();
                        int h = mc.getWindow().getGuiScaledHeight();
                        double mx = getMouseX(mc);
                        double my = getMouseY(mc);
                        McpOverlayLogic.renderResumeButton(wrapRenderer(g, mc), mc.font, Component.translatable("mcpmod.control.resume").getString(), w, h, (int) mx, (int) my);
                    }
                }
            } catch (Exception ignored) {}
        });

        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Render.Post event) -> {
            if (ReflectionHelper.isScreenshotInProgress()) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                Screen screen = event.getScreen();
                if (screen == null) return;
                if (ReflectionHelper.isMcpControlMode() && screen instanceof PauseScreen) {
                    mc.screen = null;
                    return;
                }
                if (mc.level != null) {
                    GuiGraphics sg = event.getGuiGraphics();
                    int sw = mc.getWindow().getGuiScaledWidth();
                    int sh = mc.getWindow().getGuiScaledHeight();
                    renderScreenButton(sg, mc, screen);
                }
            } catch (Exception e) { e.printStackTrace(); }
        });

        MinecraftForge.EVENT_BUS.addListener((InputEvent.MouseButton.Pre event) -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (ReflectionHelper.isWaitingForRelease()) {
                    event.setCanceled(true);
                    return;
                }
                if (ReflectionHelper.isMcpControlMode()) {
                    return;
                }
                if (mc.level != null && mc.screen != null && event.getButton() == 0) {
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
                    long window = mc.getWindow().getWindow();
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
                if (mc.gui == null || mc.gui.getChat() == null) return;
                INSTANCE.chatSent = true;
                String url = INSTANCE.debugUrl;
                Component msg = Component.empty()
                    .append(Component.literal("[MCP] Debug page: ").withStyle(st -> st.withColor(0xFFFFFF)))
                    .append(Component.literal(url).withStyle(st -> st
                        .withColor(0xFFFFFF)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                    ));
                mc.gui.getChat().addMessage(msg);
            } catch (Exception ignored) {}
        });
    }

    private void setup(final FMLCommonSetupEvent event) {
        Minecraft mc = Minecraft.getInstance();
        try {
            long handle = mc.getWindow().getWindow();
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
                        if (!ReflectionHelper.isMcpControlMode() && mc2.screen == null) {
                            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                            try { mc2.mouseHandler.grabMouse(); } catch (Exception ignored3) {}
                        }
                        return;
                    }
                }
                if (originalMouseButtonCallback != null) {
                    originalMouseButtonCallback.invoke(window, button, action, mods);
                }
            });
            System.out.println("[MCP-MOD] MouseButton interceptor installed in setup");
        } catch (Exception e) {
            System.err.println("[MCP-MOD] MouseButton interceptor failed: " + e.getMessage());
        }
    }
}

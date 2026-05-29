package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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

    private static org.lwjgl.glfw.GLFWMouseButtonCallbackI originalMouseButtonCallback = null;

    private static McpRenderer wrapRenderer(GuiGraphicsExtractor g, Minecraft mc) {
        return new McpRenderer() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {
                g.fill(x1, y1, x2, y2, color);
            }
            @Override public int drawString(Object font, String text, int x, int y, int color, boolean shadow) {
                try {
                    for (java.lang.reflect.Method m : mc.font.getClass().getMethods()) {
                        if (m.getName().equals("drawInBatch") && m.getParameterCount() >= 6) {
                            Class<?>[] pts = m.getParameterTypes();
                            if (pts[0] == String.class) {
                                Object[] args = new Object[pts.length];
                                args[0] = text;
                                args[1] = (float) x;
                                args[2] = (float) y;
                                args[3] = color;
                                args[4] = shadow;
                                for (int i = 5; i < pts.length; i++) args[i] = getDefault(pts[i]);
                                m.invoke(mc.font, args);
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {}
                return mc.font.width(text);
            }
            @SuppressWarnings("unused")
            private static Object getDefault(Class<?> c) {
                if (c == boolean.class) return false;
                if (c == int.class) return 0;
                if (c == float.class) return 0f;
                return null;
            }
            @Override public int getStringWidth(Object font, String text) {
                return mc.font.width(text);
            }
        };
    }

    private static void withFullScissor(GuiGraphicsExtractor g, int w, int h, Runnable action) {
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

    private static void renderScreenButton(GuiGraphicsExtractor g, Minecraft mc, Screen screen) {
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        double mx = mc.mouseHandler.xpos() * w / mc.getWindow().getScreenWidth();
        double my = mc.mouseHandler.ypos() * h / mc.getWindow().getScreenHeight();
        if (ReflectionHelper.isMcpControlMode()) {
            ReflectionHelper.cacheFrameFromRenderThread(mc);
            McpOverlayLogic.renderResumeButton(wrapRenderer(g, mc), mc.font, Component.translatable("mcpmod.control.resume").getString(), w, h, (int) mx, (int) my);
        } else if (screen != null) {
            McpOverlayLogic.renderTransferButton(wrapRenderer(g, mc), mc.font, Component.translatable("mcpmod.control.pause_button").getString(), w, h, (int) mx, (int) my);
        }
    }

    @SuppressWarnings("removal")
    private static void registerInputInterception() {
        NeoForge.EVENT_BUS.addListener((InputEvent.MouseButton.Pre event) -> {
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
                    double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
                    double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
                    if (ReflectionHelper.handleTransferOverlayClick((int) mx, (int) my, mc).equals("transfer_to_mcp")) {
                        event.setCanceled(true);
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    private static void tick(Minecraft mc) {
        try {
            ReflectionHelper.tickMouseRelease(mc);
            ReflectionHelper.tickMcpControlMode(mc);
            ReflectionHelper.tickVideoCapture(mc);
        } catch (Exception ignored) {}
    }

    public ModDevMcpMod(IEventBus modBus) {
        INSTANCE = this;
        modBus.addListener(this::setup);

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

        NeoForge.EVENT_BUS.addListener((CustomizeGuiOverlayEvent.Chat event) -> {
            if (debugUrl == null && !ReflectionHelper.isMouseReleaseActive()) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == null) {
                    tick(mc);

                    if (!ReflectionHelper.isScreenshotInProgress()) {
                        ReflectionHelper.cacheFrameFromRenderThread(mc);
                    }

                    if (ReflectionHelper.isMcpControlMode() && !ReflectionHelper.isScreenshotInProgress()) {
                        GuiGraphicsExtractor g = event.getGuiGraphics();
                        int w = mc.getWindow().getGuiScaledWidth();
                        int h = mc.getWindow().getGuiScaledHeight();
                        double mx = mc.mouseHandler.xpos() * w / mc.getWindow().getScreenWidth();
                        double my = mc.mouseHandler.ypos() * h / mc.getWindow().getScreenHeight();
                        McpOverlayLogic.renderResumeButton(wrapRenderer(g, mc), mc.font, Component.translatable("mcpmod.control.resume").getString(), w, h, (int) mx, (int) my);
                    }

                    if (!INSTANCE.chatSent && INSTANCE.debugUrl != null) {
                        INSTANCE.chatSent = true;
                        try {
                            String url = INSTANCE.debugUrl;
                            Component msg = Component.empty().append(Component.literal("[MCP] Debug page: ").withStyle(s -> s.withColor(0xFFFFFF))).append(Component.literal(url).withStyle(s -> s.withColor(0xFFFFFF).withUnderlined(true).withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(url)))));
                            Object chat = mc.gui.getChat();
                            boolean sent = false;
                            for (java.lang.reflect.Method m : chat.getClass().getMethods()) {
                                if (m.getName().equals("addMessage") && m.getParameterCount() >= 1 && m.getParameterTypes()[0].isAssignableFrom(msg.getClass())) {
                                    Object[] args = new Object[m.getParameterCount()];
                                    args[0] = msg;
                                    m.invoke(chat, args);
                                    sent = true;
                                    break;
                                }
                            }
                            if (!sent) System.out.println("[MCP-MOD] Debug page: " + url);
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        });

        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Post event) -> {
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
                    GuiGraphicsExtractor sg = event.getGuiGraphics();
                    int sw = mc.getWindow().getGuiScaledWidth();
                    int sh = mc.getWindow().getGuiScaledHeight();
                    renderScreenButton(sg, mc, screen);
                }
            } catch (Exception e) { e.printStackTrace(); }
        });

        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            if (INSTANCE == null || INSTANCE.debugUrl == null) return;
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
        });

        registerInputInterception();
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
                    double mx = mc2.mouseHandler.xpos() * mc2.getWindow().getGuiScaledWidth() / mc2.getWindow().getScreenWidth();
                    double my = mc2.mouseHandler.ypos() * mc2.getWindow().getGuiScaledHeight() / mc2.getWindow().getScreenHeight();
                    String result = ReflectionHelper.handleOverlayClick((int) mx, (int) my, mc2);
                    if (!result.equals("blocked") && !result.equals("cooldown") && !result.equals("not_in_control_mode")) {
                        if (!ReflectionHelper.isMcpControlMode() && mc2.screen == null) {
                            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                            try { mc2.mouseHandler.grabMouse(); } catch (Exception ignored2) {}
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

package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
        McpOverlayLogic.renderPortInfo(wrapRenderer(g, mc), mc.font, w, h, INSTANCE.httpServer);
        double mx = mc.mouseHandler.xpos() * w / mc.getWindow().getScreenWidth();
        double my = mc.mouseHandler.ypos() * h / mc.getWindow().getScreenHeight();
        if (ReflectionHelper.isMcpControlMode()) {
            ReflectionHelper.cacheFrameFromRenderThread(mc);
            McpOverlayLogic.renderResumeButton(wrapRenderer(g, mc), mc.font, Component.translatable("mcpmod.control.resume").getString(), w, h, (int) mx, (int) my);
        } else if (screen != null) {
            McpOverlayLogic.renderTransferButton(wrapRenderer(g, mc), mc.font, Component.translatable("mcpmod.control.pause_button").getString(), w, h, (int) mx, (int) my);
        }
    }

    public ModDevMcpMod(IEventBus modBus) {
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

        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) -> {
            if (debugUrl == null && !ReflectionHelper.isMouseReleaseActive()) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == null) {
                    ReflectionHelper.tickMouseRelease(mc);
                    ReflectionHelper.tickMcpControlMode(mc);

                    if (!ReflectionHelper.isScreenshotInProgress()) {
                        ReflectionHelper.cacheFrameFromRenderThread(mc);
                    }

                    if (ReflectionHelper.isMcpControlMode() && !ReflectionHelper.isScreenshotInProgress()) {
                        GuiGraphics g = event.getGuiGraphics();
                        int w = mc.getWindow().getGuiScaledWidth();
                        int h = mc.getWindow().getGuiScaledHeight();
                        double mx = mc.mouseHandler.xpos() * w / mc.getWindow().getScreenWidth();
                        double my = mc.mouseHandler.ypos() * h / mc.getWindow().getScreenHeight();
                        McpOverlayLogic.renderResumeButton(wrapRenderer(g, mc), mc.font, Component.translatable("mcpmod.control.resume").getString(), w, h, (int) mx, (int) my);
                    }
                }
            } catch (Exception ignored) {}
        });

        NeoForge.EVENT_BUS.addListener((ScreenEvent.Opening event) -> {
            if (ReflectionHelper.isMcpControlMode() && event.getScreen() instanceof PauseScreen) {
                event.setCanceled(true);
            }
        });

        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Post event) -> {
            if (ReflectionHelper.isScreenshotInProgress()) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                Screen screen = event.getScreen();
                if (screen == null) return;
                if (mc.level != null) {
                    GuiGraphics sg = event.getGuiGraphics();
                    renderScreenButton(sg, mc, screen);
                }
            } catch (Exception e) { e.printStackTrace(); }
        });

        NeoForge.EVENT_BUS.addListener((InputEvent.MouseButton.Pre event) -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (event.getButton() != 0 || event.getAction() != 1) return;
                if (ReflectionHelper.isWaitingForRelease()) {
                    event.setCanceled(true);
                    return;
                }
                double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
                double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
                if (ReflectionHelper.isMcpControlMode()) {
                    String result = ReflectionHelper.handleOverlayClick((int) mx, (int) my, mc);
                    if (!result.equals("blocked") && !result.equals("cooldown") && !result.equals("not_in_control_mode")) {
                        event.setCanceled(true);
                        if (!ReflectionHelper.isMcpControlMode() && mc.screen == null) {
                            long window = mc.getWindow().getWindow();
                            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                            try { mc.mouseHandler.grabMouse(); } catch (Exception ignored) {}
                        }
                    }
                    return;
                }
                if (mc.level != null && mc.screen != null) {
                    if (ReflectionHelper.handleTransferOverlayClick((int) mx, (int) my, mc).equals("transfer_to_mcp")) {
                        event.setCanceled(true);
                    }
                }
            } catch (Exception ignored) {}
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

}

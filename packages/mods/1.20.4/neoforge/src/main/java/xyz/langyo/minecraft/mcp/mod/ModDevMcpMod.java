package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod("mcpmod")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpHttpServer httpServer;

    public ModDevMcpMod(IEventBus modBus) {
        INSTANCE = this;
        modBus.addListener(this::commonSetup);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        ReflectedInputHandler handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        int port = McpConfig.getServerPort();
        httpServer = new McpHttpServer(handler, port);
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                httpServer.start();
            } catch (Exception e) {
                System.err.println("[MCP-MOD] HTTP server failed: " + e.getMessage());
            }
        }, "MCP-HTTP").start();
    }

    // ===== top-right overlay (resume / transfer buttons) =====
    private static boolean prevLeftDown = false;

        private static McpRenderer makeRenderer(final Object graphics) {
        return new McpRenderer() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {
                ((net.minecraft.client.gui.GuiGraphics) graphics).fill(x1, y1, x2, y2, color);
            }
            @Override public int drawString(Object font, String text, int x, int y, int color, boolean shadow) { return 0; }
            @Override public int getStringWidth(Object font, String text) { return 0; }
        };
    }

    private static int[] scaledMouse(net.minecraft.client.Minecraft mc) {
        long win = mc.getWindow().getWindow();
        double[] px = new double[1], py = new double[1];
        org.lwjgl.glfw.GLFW.glfwGetCursorPos(win, px, py);
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        int mx = (int) (px[0] * w / (double) mc.getWindow().getWidth());
        int my = (int) (py[0] * h / (double) mc.getWindow().getHeight());
        return new int[]{mx, my};
    }

    private void tickOverlay() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            xyz.langyo.minecraft.mcp.common.ReflectionHelper.tickMouseRelease(mc);
            xyz.langyo.minecraft.mcp.common.ReflectionHelper.tickMcpControlMode(mc);
        } catch (Exception ignored) {}
    }

    private void renderHudOverlay(Object graphics) {
        try {
            if (xyz.langyo.minecraft.mcp.common.ReflectionHelper.isScreenshotInProgress()) return;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.screen != null || !xyz.langyo.minecraft.mcp.common.ReflectionHelper.isMcpControlMode()) return;
            int[] m = scaledMouse(mc);
            long win = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
            boolean left = org.lwjgl.glfw.GLFW.glfwGetMouseButton(win, 0) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            if (left && !prevLeftDown) {
                xyz.langyo.minecraft.mcp.common.ReflectionHelper.handleOverlayClick(m[0], m[1], mc);
            }
            prevLeftDown = left;
            if (!xyz.langyo.minecraft.mcp.common.ReflectionHelper.isScreenshotInProgress()) {
                xyz.langyo.minecraft.mcp.common.McpOverlayLogic.renderResumeButton(
                        makeRenderer(graphics), null, "",
                        mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), m[0], m[1]);
            }
        } catch (Exception ignored) {}
    }

    private void renderScreenOverlay(Object graphics, net.minecraft.client.gui.screens.Screen screen, int mx, int my) {
        try {
            if (screen == null || xyz.langyo.minecraft.mcp.common.ReflectionHelper.isScreenshotInProgress()) return;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            // The GuiGraphics handed to ScreenEvent.Render may not be flushed
            // into the final frame on some NeoForge builds — gg eras draw with
            // their own graphics over the shared buffer source and flush
            // explicitly; ms eras draw immediately via a fresh PoseStack.
                        net.minecraft.client.gui.GuiGraphics g = new net.minecraft.client.gui.GuiGraphics(
                    mc, mc.renderBuffers().bufferSource());

            if (xyz.langyo.minecraft.mcp.common.ReflectionHelper.isMcpControlMode()) {
                xyz.langyo.minecraft.mcp.common.McpOverlayLogic.renderResumeButton(
                        makeRenderer(g), null, "", screen.width, screen.height, mx, my);
            } else if (mc.level != null) {
                xyz.langyo.minecraft.mcp.common.McpOverlayLogic.renderTransferButton(
                        makeRenderer(g), null, "", screen.width, screen.height, mx, my);
            }
                        g.flush();

        } catch (Exception ignored) {}
    }

    private boolean clickOverlay(int mx, int my) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (xyz.langyo.minecraft.mcp.common.ReflectionHelper.isMcpControlMode()) {
                String r = xyz.langyo.minecraft.mcp.common.ReflectionHelper.handleOverlayClick(mx, my, mc);
                return !"blocked".equals(r) && !"cooldown".equals(r) && !"not_in_control_mode".equals(r);
            }
            if (mc.level != null && mc.screen != null) {
                String r = xyz.langyo.minecraft.mcp.common.ReflectionHelper.handleTransferOverlayClick(mx, my, mc);
                return "transfer_to_mcp".equals(r);
            }
        } catch (Exception ignored) {}
        return false;
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onHudOverlay(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        renderHudOverlay(event.getGuiGraphics());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onScreenRender(net.neoforged.neoforge.client.event.ScreenEvent.Render.Pre event) {
        renderScreenOverlay(makeRenderer(event.getGuiGraphics()), event.getScreen(), (int) event.getMouseX(), (int) event.getMouseY());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onScreenClick(net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() == 0 && clickOverlay((int) event.getMouseX(), (int) event.getMouseY())) {
            event.setCanceled(true);
        }
    }
    @net.neoforged.bus.api.SubscribeEvent
    public void onScreenInit(net.neoforged.neoforge.client.event.ScreenEvent.Init.Post event) {
        if (xyz.langyo.minecraft.mcp.common.ReflectionHelper.isMcpControlMode()) return;
        net.minecraft.client.gui.screens.Screen s = event.getScreen();
        if (!(s instanceof net.minecraft.client.gui.screens.PauseScreen)) return;
        try {
            net.minecraft.client.gui.components.Button button = net.minecraft.client.gui.components.Button.builder(
                    net.minecraft.network.chat.Component.literal("MCP Take Over"),
                    btn -> xyz.langyo.minecraft.mcp.common.ControlModeHelper.enterMcpControlMode(
                            net.minecraft.client.Minecraft.getInstance()))
                    .bounds(s.width - 104, 44, 100, 20).build();
            for (Class<?> c = s.getClass(); c != null; c = c.getSuperclass()) {
                for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals("addRenderableWidget") && m.getParameterCount() == 1) {
                        m.setAccessible(true);
                        m.invoke(s, button);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}
    }
    @net.neoforged.bus.api.SubscribeEvent
    public void onClientTick(net.neoforged.neoforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase == net.neoforged.neoforge.event.TickEvent.Phase.END) tickOverlay();
    }
}

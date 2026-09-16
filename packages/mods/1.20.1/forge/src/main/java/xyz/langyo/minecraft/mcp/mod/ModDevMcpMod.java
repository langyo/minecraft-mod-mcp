package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod("mcpmod")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpHttpServer httpServer;
    private boolean resumeSoundOnNextTick;

    public ModDevMcpMod() {
        INSTANCE = this;
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
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
            // 共享鼠标模式下光标被锁定，HUD 上的悬浮按钮点不到；
            // 此时退出控制模式用 ESC 菜单里的按钮，或按 F8。
            if (!xyz.langyo.minecraft.mcp.common.ControlModeHelper.isMouseDetached()) return;
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

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onHudOverlay(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        renderHudOverlay(event.getGuiGraphics());
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onScreenRender(net.minecraftforge.client.event.ScreenEvent.Render.Pre event) {
        renderScreenOverlay(makeRenderer(event.getGuiGraphics()), event.getScreen(), (int) event.getMouseX(), (int) event.getMouseY());
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onScreenClick(net.minecraftforge.client.event.ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() == 0 && clickOverlay((int) event.getMouseX(), (int) event.getMouseY())) {
            event.setCanceled(true);
        }
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onScreenInit(net.minecraftforge.client.event.ScreenEvent.Init.Post event) {
        if (xyz.langyo.minecraft.mcp.common.ReflectionHelper.isMcpControlMode()) return;
        net.minecraft.client.gui.screens.Screen s = event.getScreen();
        if (!(s instanceof net.minecraft.client.gui.screens.PauseScreen)) return;
        try {
            net.minecraft.client.gui.components.Button button = net.minecraft.client.gui.components.Button.builder(
                    net.minecraft.network.chat.Component.literal("MCP Take Over"),
                    btn -> xyz.langyo.minecraft.mcp.common.ControlModeHelper.enterMcpControlMode(
                            net.minecraft.client.Minecraft.getInstance()))
                    .bounds(s.width - 104, 44, 100, 20).build();
            event.addListener(button);
        } catch (Exception ignored) {}
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        tickOverlay();
        if (resumeSoundOnNextTick) {
            resumeSoundOnNextTick = false;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen
                    && PauseGuardHelper.shouldBypassPause(mc)) {
                mc.getSoundManager().resume();
            }
        }
    }

    /**
     * 单机 ESC 不冻结世界：把原版 PauseScreen 换成 isPauseScreen()=false 的等价菜单。
     * 原理见 PauseGuardHelper。已开局域网的世界本来就不会暂停，这里会自动跳过。
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onScreenOpening(net.minecraftforge.client.event.ScreenEvent.Opening event) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null) return;
            net.minecraft.client.gui.screens.Screen newScreen = event.getNewScreen();
            if (newScreen == null) return;
            if (!xyz.langyo.minecraft.mcp.common.PauseGuardHelper.shouldBypassPause(mc)) return;
            net.minecraft.client.gui.screens.Screen wrapped = xyz.langyo.minecraft.mcp.common.PauseGuardHelper.wrap(newScreen);
            if (wrapped != null && wrapped != newScreen) {
                event.setNewScreen(wrapped);
                // pauseGame() pauses sound after setScreen returns.
                resumeSoundOnNextTick = true;
            }
        } catch (Throwable ignored) {}
    }

    /** F8：在“MCP 控制模式”和“手动控制”之间快速切换（共享鼠标模式下尤其有用）。 */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onKeyInput(net.minecraftforge.client.event.InputEvent.Key event) {
        try {
            if (event.getAction() != org.lwjgl.glfw.GLFW.GLFW_PRESS) return;
            if (event.getKey() != org.lwjgl.glfw.GLFW.GLFW_KEY_F8) return;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null || mc.level == null) return;
            if (xyz.langyo.minecraft.mcp.common.ControlModeHelper.isMcpControlMode()) {
                xyz.langyo.minecraft.mcp.common.ControlModeHelper.exitMcpControlMode(mc);
            } else {
                xyz.langyo.minecraft.mcp.common.ControlModeHelper.enterMcpControlMode(mc);
            }
        } catch (Throwable ignored) {}
    }
}

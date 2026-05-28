package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MouseHelper;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

@Mod(modid = "mcpmod", name = "ModDev MCP", version = "1.0")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpHttpServer httpServer;
    volatile String debugUrl = null;
    volatile boolean chatSent = false;
    private static boolean prevMouseButton0 = false;

    @Mod.Instance("mcpmod")
    public static ModDevMcpMod instance;

    private static McpRenderer wrapRenderer(final Minecraft mc) {
        return new McpRenderer() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {
                net.minecraft.client.gui.Gui.drawRect(x1, y1, x2, y2, color);
            }
            @Override public int drawString(Object font, String text, int x, int y, int color, boolean shadow) {
                if (shadow) {
                    return mc.fontRendererObj.drawStringWithShadow(text, x, y, color);
                } else {
                    return mc.fontRendererObj.drawString(text, x, y, color);
                }
            }
            @Override public int getStringWidth(Object font, String text) {
                return mc.fontRendererObj.getStringWidth(text);
            }
        };
    }

    private static int getMouseX(Minecraft mc) {
        ScaledResolution sr = new ScaledResolution(mc);
        return Mouse.getX() * sr.getScaledWidth() / mc.displayWidth;
    }

    private static int getMouseY(Minecraft mc) {
        ScaledResolution sr = new ScaledResolution(mc);
        return sr.getScaledHeight() - Mouse.getY() * sr.getScaledHeight() / mc.displayHeight - 1;
    }

    private static boolean isPauseMenu(GuiScreen screen) {
        return screen instanceof GuiIngameMenu;
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
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

        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onGuiInitPre(GuiScreenEvent.InitGuiEvent.Pre event) {
        if (ReflectionHelper.isMcpControlMode() && isPauseMenu(event.gui)) {
            event.setCanceled(true);
            try { Minecraft.getMinecraft().currentScreen = null; } catch (Exception ignored) {}
        }
    }

    private static MouseHelper originalMouseHelper;
    private static NoGrabMouseHelper noGrabMouseHelper;

    private static class NoGrabMouseHelper extends net.minecraft.util.MouseHelper {
        @Override
        public void grabMouseCursor() {}
        @Override
        public void ungrabMouseCursor() {
            try { Mouse.setGrabbed(false); } catch (Exception ignored) {}
        }
        @Override
        public void mouseXYChange() {
            deltaX = 0;
            deltaY = 0;
        }
    }

    private static void forceLwjgl2MouseFree() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (!ReflectionHelper.isMcpControlMode()) {
                if (originalMouseHelper != null && !(mc.mouseHelper instanceof NoGrabMouseHelper)) {
                    // already restored
                } else if (originalMouseHelper != null) {
                    mc.mouseHelper = originalMouseHelper;
                    originalMouseHelper = null;
                }
                return;
            }
            if (originalMouseHelper == null && mc.mouseHelper != null && !(mc.mouseHelper instanceof NoGrabMouseHelper)) {
                originalMouseHelper = mc.mouseHelper;
            }
            if (mc.mouseHelper instanceof NoGrabMouseHelper) {
                // already installed
            } else if (originalMouseHelper != null) {
                if (noGrabMouseHelper == null) noGrabMouseHelper = new NoGrabMouseHelper();
                mc.mouseHelper = noGrabMouseHelper;
            }
            if (Mouse.isGrabbed()) {
                Mouse.setGrabbed(false);
            }
        } catch (Exception e) {
            System.err.println("[MCP-MOD] forceLwjgl2MouseFree error: " + e.getMessage());
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.CHAT) return;
        if (debugUrl == null && !ReflectionHelper.isMouseReleaseActive() && !ReflectionHelper.isMcpControlMode()) return;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            boolean overlayMouse0 = false;
            ReflectionHelper.tickMouseRelease(mc);
            ReflectionHelper.tickMcpControlMode(mc);

            if (ReflectionHelper.isMcpControlMode()) {
                overlayMouse0 = Mouse.isButtonDown(0);
                forceLwjgl2MouseFree();
            }

            if (mc.currentScreen == null) {
                if (!ReflectionHelper.isScreenshotInProgress()) {
                    ReflectionHelper.cacheFrameFromRenderThread(mc);
                }

                if (ReflectionHelper.isMcpControlMode() && !ReflectionHelper.isScreenshotInProgress()) {
                    ScaledResolution sr = new ScaledResolution(mc);
                    int w = sr.getScaledWidth();
                    int h = sr.getScaledHeight();
                    int mx = getMouseX(mc);
                    int my = getMouseY(mc);
                    if (overlayMouse0 && !prevMouseButton0) {
                        ReflectionHelper.handleOverlayClick(mx, my, mc);
                    }
                    McpOverlayLogic.renderResumeButton(wrapRenderer(mc), mc.fontRendererObj, net.minecraft.client.resources.I18n.format("mcpmod.control.resume"), w, h, mx, my);
                }
            }
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (ReflectionHelper.isScreenshotInProgress()) return;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            GuiScreen screen = event.gui;
            ScaledResolution sr = new ScaledResolution(mc);
            int w = sr.getScaledWidth();
            int h = sr.getScaledHeight();
            int mx = getMouseX(mc);
            int my = getMouseY(mc);

            if (ReflectionHelper.isMcpControlMode()) {
                GL11.glScissor(0, 0, mc.displayWidth, mc.displayHeight);
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                forceLwjgl2MouseFree();
                ReflectionHelper.cacheFrameFromRenderThread(mc);
                McpOverlayLogic.renderResumeButton(wrapRenderer(mc), mc.fontRendererObj, net.minecraft.client.resources.I18n.format("mcpmod.control.resume"), w, h, mx, my);
            } else if (mc.theWorld != null && screen != null) {
                GL11.glScissor(0, 0, mc.displayWidth, mc.displayHeight);
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                McpOverlayLogic.renderTransferButton(wrapRenderer(mc), mc.fontRendererObj, "", w, h, mx, my);
            }
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public void onGuiMouseInputPre(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (ReflectionHelper.isMcpControlMode()) {
            if (org.lwjgl.input.Mouse.getEventButton() == 0 && org.lwjgl.input.Mouse.getEventButtonState()) {
                Minecraft mc = Minecraft.getMinecraft();
                int mx = getMouseX(mc);
                int my = getMouseY(mc);
                String result = ReflectionHelper.handleOverlayClick(mx, my, mc);
                if (!"blocked".equals(result) && !"cooldown".equals(result) && !"not_in_control_mode".equals(result)) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (INSTANCE == null || INSTANCE.debugUrl == null) return;

        if (ReflectionHelper.isMcpControlMode()) {
            if (event.phase == TickEvent.Phase.START) {
                forceLwjgl2MouseFree();
            } else {
                Minecraft mc = Minecraft.getMinecraft();
                ReflectionHelper.tickMouseRelease(mc);
                ReflectionHelper.tickMcpControlMode(mc);
                ReflectionHelper.tickVideoCapture(mc);
                forceLwjgl2MouseFree();
                if (mc.currentScreen == null) {
                    boolean mouse0 = Mouse.isButtonDown(0);
                    if (mouse0 && !prevMouseButton0) {
                        int mx = getMouseX(mc);
                        int my = getMouseY(mc);
                        ReflectionHelper.handleOverlayClick(mx, my, mc);
                    }
                    prevMouseButton0 = mouse0;
                }
            }
            return;
        }

        if (!ReflectionHelper.isMcpControlMode() && event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getMinecraft();
            if (originalMouseHelper != null) {
                mc.mouseHelper = originalMouseHelper;
                originalMouseHelper = null;
                if (mc.currentScreen == null) {
                    try { mc.mouseHelper.grabMouseCursor(); } catch (Exception ignored) {}
                }
            }
        }

        if (event.phase != TickEvent.Phase.END) return;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            ReflectionHelper.tickMouseRelease(mc);
            ReflectionHelper.tickMcpControlMode(mc);
            ReflectionHelper.tickVideoCapture(mc);

            boolean mouse0 = Mouse.isButtonDown(0);
            if (mc.theWorld != null && mc.currentScreen != null && mouse0 && !prevMouseButton0) {
                int mx = getMouseX(mc);
                int my = getMouseY(mc);
                ReflectionHelper.handleTransferOverlayClick(mx, my, mc);
            }
            prevMouseButton0 = mouse0;
        } catch (Exception ignored) {}
        if (INSTANCE.chatSent) return;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.ingameGUI == null) return;
            INSTANCE.chatSent = true;
            String url = INSTANCE.debugUrl;
            mc.ingameGUI.getChatGUI().printChatMessage(
                new ChatComponentText(EnumChatFormatting.WHITE + "[MCP] Debug page: " + url));
        } catch (Exception ignored) {}
    }
}

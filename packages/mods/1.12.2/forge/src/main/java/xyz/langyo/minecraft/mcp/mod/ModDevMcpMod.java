package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.realms.RealmsSharedConstants;
import org.lwjgl.input.Mouse;

@Mod(modid = "mcpmod", name = "ModDev MCP", version = "1.0")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpHttpServer httpServer;
    volatile String debugUrl = null;
    volatile boolean chatSent = false;

    @Mod.Instance("mcpmod")
    public static ModDevMcpMod instance;

    private static McpRenderer wrapRenderer(final Minecraft mc) {
        return new McpRenderer() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {
                net.minecraft.client.gui.Gui.drawRect(x1, y1, x2, y2, color);
            }
            @Override public int drawString(Object font, String text, int x, int y, int color, boolean shadow) {
                if (shadow) {
                    return mc.fontRenderer.drawStringWithShadow(text, x, y, color);
                } else {
                    return mc.fontRenderer.drawString(text, x, y, color);
                }
            }
            @Override public int getStringWidth(Object font, String text) {
                return mc.fontRenderer.getStringWidth(text);
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

        boolean depsOk = false;
        try {
            Class.forName("com.sun.jna.Library");
            depsOk = true;
        } catch (Exception ignored) {}
        if (!depsOk) {
            System.err.println("[MCP-MOD] JNA not on classpath. Use launch_mc.py.");
        }

        if (depsOk) {
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

        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        try {
            if (isPauseMenu(event.getGui())) {
                McpScreenHelper.patchPauseScreen(event.getGui(), new McpScreenHelper.ButtonFactory() {
                    @Override public Object createButton(String translationKey, Runnable onClick, int x, int y, int w, int h) {
                        GuiButton btn = new GuiButton(-999, x, y, w, h, translationKey) {
                            @Override public boolean mousePressed(Minecraft mc2, int mx, int my) {
                                if (super.mousePressed(mc2, mx, my)) {
                                    onClick.run();
                                    return true;
                                }
                                return false;
                            }
                        };
                        return btn;
                    }
                });
            }
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.CHAT) return;
        if (debugUrl == null && !ReflectionHelper.isMouseReleaseActive()) return;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen == null) {
                ReflectionHelper.tickMouseRelease(mc);
                ReflectionHelper.tickMcpControlMode(mc);

                if (!ReflectionHelper.isScreenshotInProgress()) {
                    ReflectionHelper.cacheFrameFromRenderThread(mc);
                }

                if (ReflectionHelper.isMcpControlMode() && !ReflectionHelper.isScreenshotInProgress()) {
                    ScaledResolution sr = new ScaledResolution(mc);
                    int w = sr.getScaledWidth();
                    int h = sr.getScaledHeight();
                    int mx = getMouseX(mc);
                    int my = getMouseY(mc);
                    McpOverlayLogic.renderResumeButton(wrapRenderer(mc), mc.fontRenderer, "[MCP] Resume", w, h, mx, my);
                }
            }
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (ReflectionHelper.isScreenshotInProgress()) return;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            GuiScreen screen = event.getGui();
            ScaledResolution sr = new ScaledResolution(mc);
            int w = sr.getScaledWidth();
            int h = sr.getScaledHeight();
            int mx = getMouseX(mc);
            int my = getMouseY(mc);

            if (ReflectionHelper.isMcpControlMode()) {
                ReflectionHelper.cacheFrameFromRenderThread(mc);
                McpOverlayLogic.renderResumeButton(wrapRenderer(mc), mc.fontRenderer, "[MCP] Resume", w, h, mx, my);
            } else if (mc.world != null && screen != null && !isPauseMenu(screen)) {
                McpOverlayLogic.renderTransferButton(wrapRenderer(mc), mc.fontRenderer, "[MCP] Transfer", w, h, mx, my);
            }
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public void onMouseInput(MouseEvent event) {
        try {
            if (event.getButton() != 0) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (ReflectionHelper.shouldSuppressInput()) {
                event.setCanceled(true);
                return;
            }
            if (ReflectionHelper.isMcpControlMode()) {
                int mx = getMouseX(mc);
                int my = getMouseY(mc);
                String result = ReflectionHelper.handleOverlayClick(mx, my, mc);
                if (!result.equals("blocked") && !result.equals("cooldown") && !result.equals("not_in_control_mode")) {
                    event.setCanceled(true);
                    return;
                }
                event.setCanceled(true);
                return;
            }
            if (mc.world != null && mc.currentScreen != null && !(mc.currentScreen instanceof GuiIngameMenu)) {
                int mx = getMouseX(mc);
                int my = getMouseY(mc);
                if (ReflectionHelper.handleTransferOverlayClick(mx, my, mc).equals("transfer_to_mcp")) {
                    event.setCanceled(true);
                }
            }
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (INSTANCE == null || INSTANCE.debugUrl == null) return;
        if (event.phase != TickEvent.Phase.END) return;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            ReflectionHelper.tickMouseRelease(mc);
            ReflectionHelper.tickMcpControlMode(mc);
            ReflectionHelper.tickVideoCapture(mc);
            McpPlatformControl ctrl = McpControlFactory.get();
            if (ctrl instanceof McpWin32Control) {
                McpWin32Control w32ctrl = (McpWin32Control) ctrl;
                if (w32ctrl.getMcHwnd() == 0) {
                    w32ctrl.ensureHwndFromLwjgl2Display();
                }
            }
        } catch (Exception ignored) {}
        if (INSTANCE.chatSent) return;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.ingameGUI == null) return;
            INSTANCE.chatSent = true;
            String url = INSTANCE.debugUrl;
            mc.ingameGUI.getChatGUI().printChatMessage(
                new TextComponentString(TextFormatting.WHITE + "[MCP] Debug page: " + url));
        } catch (Exception ignored) {}
    }
}

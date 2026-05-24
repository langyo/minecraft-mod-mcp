package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.glfw.GLFW;

@Mod("mcpmod")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    McpHttpServer httpServer;
    volatile String debugUrl = null;
    volatile boolean chatSent = false;

    private static McpRenderer wrapRenderer(Minecraft mc) {
        return new McpRenderer() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {
                net.minecraft.client.gui.Gui.drawRect(x1, y1, x2, y2, color);
            }
            @Override public int drawString(Object font, String text, int x, int y, int color, boolean shadow) {
                FontRenderer fr = mc.fontRenderer;
                if (shadow) {
                    return fr.drawStringWithShadow(text, x, y, color);
                } else {
                    return fr.drawString(text, x, y, color);
                }
            }
            @Override public int getStringWidth(Object font, String text) {
                return mc.fontRenderer.getStringWidth(text);
            }
        };
    }

    private static double getMouseX(Minecraft mc) {
        return mc.mouseHelper.getMouseX() * mc.mainWindow.getScaledWidth() / mc.mainWindow.getWidth();
    }

    private static double getMouseY(Minecraft mc) {
        return mc.mouseHelper.getMouseY() * mc.mainWindow.getScaledHeight() / mc.mainWindow.getHeight();
    }

    private static String translate(String key) {
        return net.minecraft.client.resources.I18n.format(key);
    }

    private static void forceGlfwMouseFree(Minecraft mc) {
        try {
            long hwnd = mc.mainWindow.getHandle();
            if (GLFW.glfwGetInputMode(hwnd, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_NORMAL) {
                GLFW.glfwSetInputMode(hwnd, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            }
        } catch (Exception ignored) {}
    }

    private static int transferCooldown = 0;

    private static boolean isPauseMenu(GuiScreen screen) {
        return screen instanceof GuiIngameMenu;
    }

    @SuppressWarnings("removal")
    public ModDevMcpMod() {
        INSTANCE = this;
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);

        boolean depsOk = false;
        try {
            Class.forName("com.sun.jna.Library");
            depsOk = true;
        } catch (ClassNotFoundException e) {
            System.err.println("[MCP-MOD] JNA not on classpath. Use launch_mc.py.");
        } catch (Error e) {
            System.err.println("[MCP-MOD] Dependency error: " + e.getMessage());
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

        MinecraftForge.EVENT_BUS.addListener((GuiScreenEvent.InitGuiEvent.Pre event) -> {
            if (ReflectionHelper.isMcpControlMode() && event.getGui() instanceof GuiIngameMenu) {
                event.setCanceled(true);
                try { Minecraft.getInstance().currentScreen = null; } catch (Exception ignored) {}
            }
        });

        MinecraftForge.EVENT_BUS.addListener((GuiScreenEvent.InitGuiEvent.Post event) -> {
            try {
                if (event.getGui() instanceof GuiIngameMenu) {
                    McpScreenHelper.patchPauseScreen(event.getGui(), new McpScreenHelper.ButtonFactory() {
                        @Override public Object createButton(String translationKey, Runnable onClick, int x, int y, int w, int h) {
                            String displayText = translate(translationKey);
                            return new GuiButton(-999, x, y, w, h, displayText) {
                                @Override public void onClick(double mouseX, double mouseY) {
                                    try {
                                        Minecraft mc = Minecraft.getInstance();
                                        ReflectionHelper.enterMcpControlMode(mc);
                                        mc.displayGuiScreen(null);
                                    } catch (Exception ignored) {}
                                }
                            };
                        }
                    });
                }
            } catch (Exception ignored) {}
        });

        MinecraftForge.EVENT_BUS.addListener((RenderGameOverlayEvent.Post event) -> {
            if (event.getType() != RenderGameOverlayEvent.ElementType.CHAT) return;
            if (debugUrl == null && !ReflectionHelper.isMouseReleaseActive() && !ReflectionHelper.isMcpControlMode()) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                ReflectionHelper.tickMouseRelease(mc);
                ReflectionHelper.tickMcpControlMode(mc);

                if (ReflectionHelper.isMcpControlMode()) {
                    forceGlfwMouseFree(mc);
                }

                if (mc.currentScreen == null) {
                    if (!ReflectionHelper.isScreenshotInProgress()) {
                        ReflectionHelper.cacheFrameFromRenderThread(mc);
                    }

                    if (ReflectionHelper.isMcpControlMode() && !ReflectionHelper.isScreenshotInProgress()) {
                        int w = mc.mainWindow.getScaledWidth();
                        int h = mc.mainWindow.getScaledHeight();
                        double mx = getMouseX(mc);
                        double my = getMouseY(mc);
                        String label = translate("mcpmod.control.resume");
                        McpOverlayLogic.renderResumeButton(wrapRenderer(mc), mc.fontRenderer, label, w, h, (int) mx, (int) my);
                    }
                }
            } catch (Exception ignored) {}
        });

        MinecraftForge.EVENT_BUS.addListener((GuiScreenEvent.DrawScreenEvent.Post event) -> {
            if (ReflectionHelper.isScreenshotInProgress()) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                GuiScreen screen = event.getGui();
                int w = mc.mainWindow.getScaledWidth();
                int h = mc.mainWindow.getScaledHeight();
                double mx = getMouseX(mc);
                double my = getMouseY(mc);

                if (ReflectionHelper.isMcpControlMode()) {
                    forceGlfwMouseFree(mc);
                    ReflectionHelper.cacheFrameFromRenderThread(mc);
                    String label = translate("mcpmod.control.resume");
                    McpOverlayLogic.renderResumeButton(wrapRenderer(mc), mc.fontRenderer, label, w, h, (int) mx, (int) my);
                } else if (mc.world != null && screen != null && !isPauseMenu(screen)) {
                    String label = translate("mcpmod.control.pause_button");
                    McpOverlayLogic.renderTransferButton(wrapRenderer(mc), mc.fontRenderer, label, w, h, (int) mx, (int) my);
                }
            } catch (Exception ignored) {}
        });

        MinecraftForge.EVENT_BUS.addListener((InputEvent.MouseInputEvent event) -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (ReflectionHelper.isMcpControlMode()) {
                    if (event.getButton() == 0 && event.getAction() == 1) {
                        double mx = getMouseX(mc);
                        double my = getMouseY(mc);
                        ReflectionHelper.handleOverlayClick((int) mx, (int) my, mc);
                    }
                    event.setCanceled(true);
                    return;
                }
                if (ReflectionHelper.shouldSuppressInput()) {
                    event.setCanceled(true);
                    return;
                }
                if (transferCooldown == 0 && mc.world != null && mc.currentScreen != null && !(mc.currentScreen instanceof GuiIngameMenu) && event.getButton() == 0 && event.getAction() == 1) {
                    double mx = getMouseX(mc);
                    double my = getMouseY(mc);
                    if (ReflectionHelper.handleTransferOverlayClick((int) mx, (int) my, mc).equals("transfer_to_mcp")) {
                        event.setCanceled(true);
                    }
                }
            } catch (Exception ignored) {}
        });

        MinecraftForge.EVENT_BUS.addListener((GuiScreenEvent.MouseInputEvent event) -> {
            if (ReflectionHelper.isMcpControlMode()) {
                event.setCanceled(true);
            }
        });

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (INSTANCE == null || INSTANCE.debugUrl == null) return;

            if (ReflectionHelper.isMcpControlMode()) {
                transferCooldown = 10;
                if (event.phase == TickEvent.Phase.START) {
                    try { forceGlfwMouseFree(Minecraft.getInstance()); } catch (Exception ignored) {}
                } else {
                    try {
                        Minecraft mc = Minecraft.getInstance();
                        ReflectionHelper.tickMouseRelease(mc);
                        ReflectionHelper.tickMcpControlMode(mc);
                        ReflectionHelper.tickVideoCapture(mc);
                        forceGlfwMouseFree(mc);
                        McpPlatformControl ctrl = McpControlFactory.get();
                        if (ctrl instanceof McpWin32Control) {
                            McpWin32Control w32ctrl = (McpWin32Control) ctrl;
                            if (w32ctrl.getMcHwnd() == 0) {
                                long glfwHandle = mc.mainWindow.getHandle();
                                w32ctrl.ensureHwndFromGlfw(glfwHandle);
                            }
                        }
                    } catch (Exception ignored) {}
                }
                return;
            }

            if (event.phase != TickEvent.Phase.END) return;
            if (transferCooldown > 0) transferCooldown--;
            try {
                Minecraft mc = Minecraft.getInstance();
                ReflectionHelper.tickMouseRelease(mc);
                ReflectionHelper.tickMcpControlMode(mc);
                ReflectionHelper.tickVideoCapture(mc);
                McpPlatformControl ctrl = McpControlFactory.get();
                if (ctrl instanceof McpWin32Control) {
                    McpWin32Control w32ctrl = (McpWin32Control) ctrl;
                    if (w32ctrl.getMcHwnd() == 0) {
                        long glfwHandle = mc.mainWindow.getHandle();
                        w32ctrl.ensureHwndFromGlfw(glfwHandle);
                    }
                }

                if (mc.world != null && mc.currentScreen != null && !(mc.currentScreen instanceof GuiIngameMenu)) {
                    if (GLFW.glfwGetMouseButton(mc.mainWindow.getHandle(), GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS) {
                        double mx = getMouseX(mc);
                        double my = getMouseY(mc);
                        ReflectionHelper.handleTransferOverlayClick((int) mx, (int) my, mc);
                    }
                }
            } catch (Exception ignored) {}
            if (INSTANCE.chatSent) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.ingameGUI == null) return;
                INSTANCE.chatSent = true;
                String url = INSTANCE.debugUrl;
                mc.ingameGUI.getChatGUI().printChatMessage(new TextComponentString(TextFormatting.WHITE + "[MCP] Debug page: " + url));
            } catch (Exception ignored) {}
        });
    }

    private void setup(final FMLCommonSetupEvent event) {
    }
}

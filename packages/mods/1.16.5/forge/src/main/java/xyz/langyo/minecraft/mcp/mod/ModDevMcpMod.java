package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.common.MinecraftForge;
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

@Mod("mcpmod")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    McpHttpServer httpServer;
    volatile String debugUrl = null;
    volatile boolean chatSent = false;

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

    private static double getMouseX(Minecraft mc) {
        return mc.mouseHelper.getMouseX() * mc.getMainWindow().getScaledWidth() / mc.getMainWindow().getWidth();
    }

    private static double getMouseY(Minecraft mc) {
        return mc.mouseHelper.getMouseY() * mc.getMainWindow().getScaledHeight() / mc.getMainWindow().getHeight();
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

        MinecraftForge.EVENT_BUS.addListener((GuiScreenEvent.InitGuiEvent.Post event) -> {
            try {
                if (event.getGui() instanceof IngameMenuScreen) {
                    McpScreenHelper.patchPauseScreen(event.getGui(), new McpScreenHelper.ButtonFactory() {
                        @Override public Object createButton(String translationKey, Runnable onClick, int x, int y, int w, int h) {
                            return new Button(x, y, w, h, new TranslationTextComponent(translationKey), btn -> onClick.run());
                        }
                    });
                }
            } catch (Exception ignored) {}
        });

        MinecraftForge.EVENT_BUS.addListener((RenderGameOverlayEvent.Post event) -> {
            if (event.getType() != RenderGameOverlayEvent.ElementType.CHAT) return;
            if (debugUrl == null && !ReflectionHelper.isMouseReleaseActive()) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.currentScreen == null) {
                    ReflectionHelper.tickMouseRelease(mc);
                    ReflectionHelper.tickMcpControlMode(mc);

                    if (!ReflectionHelper.isScreenshotInProgress()) {
                        ReflectionHelper.cacheFrameFromRenderThread(mc);
                    }

                    if (ReflectionHelper.isMcpControlMode() && !ReflectionHelper.isScreenshotInProgress()) {
                        int w = mc.getMainWindow().getScaledWidth();
                        int h = mc.getMainWindow().getScaledHeight();
                        double mx = getMouseX(mc);
                        double my = getMouseY(mc);
                        McpOverlayLogic.renderResumeButton(wrapRenderer(event.getMatrixStack(), mc), mc.fontRenderer, new TranslationTextComponent("mcpmod.control.resume").getString(), w, h, (int) mx, (int) my);
                    }
                }
            } catch (Exception ignored) {}
        });

        MinecraftForge.EVENT_BUS.addListener((GuiScreenEvent.DrawScreenEvent.Post event) -> {
            if (ReflectionHelper.isScreenshotInProgress()) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                Screen screen = event.getGui();
                int w = mc.getMainWindow().getScaledWidth();
                int h = mc.getMainWindow().getScaledHeight();
                double mx = getMouseX(mc);
                double my = getMouseY(mc);

                if (ReflectionHelper.isMcpControlMode()) {
                    ReflectionHelper.cacheFrameFromRenderThread(mc);
                    McpOverlayLogic.renderResumeButton(wrapRenderer(event.getMatrixStack(), mc), mc.fontRenderer, new TranslationTextComponent("mcpmod.control.resume").getString(), w, h, (int) mx, (int) my);
                } else if (mc.world != null && screen != null && !(screen instanceof IngameMenuScreen)) {
                    McpOverlayLogic.renderTransferButton(wrapRenderer(event.getMatrixStack(), mc), mc.fontRenderer, new TranslationTextComponent("mcpmod.control.pause_button").getString(), w, h, (int) mx, (int) my);
                }
            } catch (Exception ignored) {}
        });

        MinecraftForge.EVENT_BUS.addListener((InputEvent.MouseInputEvent event) -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (ReflectionHelper.shouldSuppressInput()) { event.setCanceled(true); return; }
                if (ReflectionHelper.isMcpControlMode()) {
                    if (event.getButton() == 0) {
                        double mx = getMouseX(mc);
                        double my = getMouseY(mc);
                        String result = ReflectionHelper.handleOverlayClick((int) mx, (int) my, mc);
                        if (!result.equals("blocked") && !result.equals("cooldown") && !result.equals("not_in_control_mode")) {
                            event.setCanceled(true);
                            return;
                        }
                    }
                    event.setCanceled(true);
                    return;
                }
                if (mc.world != null && mc.currentScreen != null && !(mc.currentScreen instanceof IngameMenuScreen) && event.getButton() == 0) {
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
                ReflectionHelper.tickMouseRelease(mc);
                ReflectionHelper.tickMcpControlMode(mc);
                ReflectionHelper.tickVideoCapture(mc);
                McpPlatformControl ctrl = McpControlFactory.get();
                if (ctrl instanceof McpWin32Control) {
                    McpWin32Control w32ctrl = (McpWin32Control) ctrl;
                    if (w32ctrl.getMcHwnd() == 0) {
                        long glfwHandle = mc.getMainWindow().getHandle();
                        w32ctrl.ensureHwndFromGlfw(glfwHandle);
                    }
                }
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

    private void setup(final FMLCommonSetupEvent event) {
    }
}

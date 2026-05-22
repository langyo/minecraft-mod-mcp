package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;

@Mod("mcpmod")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    McpHttpServer httpServer;
    volatile String debugUrl = null;
    volatile boolean chatSent = false;

    private static McpRenderer wrapRenderer(GuiGraphicsExtractor g, Minecraft mc) {
        return new McpRenderer() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {
                g.fill(x1, y1, x2, y2, color);
            }
            @Override public int drawString(Object font, String text, int x, int y, int color, boolean shadow) {
                g.text(mc.font, text, x, y, color);
                return mc.font.width(text);
            }
            @Override public int getStringWidth(Object font, String text) {
                return mc.font.width(text);
            }
        };
    }

    @SuppressWarnings("removal")
    private static void registerInputInterception() {
        InputEvent.MouseButton.Pre.BUS.addListener(event -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (ReflectionHelper.shouldSuppressInput()) return true;
                if (ReflectionHelper.isMcpControlMode()) {
                    if (event.getButton() == 0) {
                        double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
                        double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
                        String result = ReflectionHelper.handleOverlayClick((int) mx, (int) my, mc);
                        if (!result.equals("blocked") && !result.equals("cooldown") && !result.equals("not_in_control_mode")) {
                            return true;
                        }
                    }
                    return true;
                }
                if (mc.level != null && mc.screen != null && !(mc.screen instanceof PauseScreen) && event.getButton() == 0) {
                    double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
                    double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
                    if (ReflectionHelper.handleTransferOverlayClick((int) mx, (int) my, mc).equals("transfer_to_mcp")) {
                        return true;
                    }
                }
                return false;
            } catch (Exception ignored) { return false; }
        });
    }

    private static void tick(Minecraft mc) {
        try {
            ReflectionHelper.tickMouseRelease(mc);
            ReflectionHelper.tickMcpControlMode(mc);
            ReflectionHelper.tickVideoCapture(mc);
        } catch (Exception ignored) {}
    }

    public ModDevMcpMod() {
        INSTANCE = this;
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

        ScreenEvent.Init.Pre.BUS.addListener(event -> {
            if (ReflectionHelper.isMcpControlMode() && event.getScreen() instanceof PauseScreen) {
                try {
                    for (java.lang.reflect.Method m : event.getClass().getMethods()) {
                        if (m.getName().equals("setCanceled") || m.getName().equals("setCancelled")) {
                            m.invoke(event, true);
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }
        });

        ScreenEvent.Init.Post.BUS.addListener(event -> {
            try {
                if (event.getScreen() instanceof PauseScreen pauseScreen) {
                    McpScreenHelper.patchPauseScreen(pauseScreen, new McpScreenHelper.ButtonFactory() {
                        @Override public Object createButton(String translationKey, Runnable onClick, int x, int y, int w, int h) {
                            return Button.builder(Component.translatable(translationKey), btn -> onClick.run()).bounds(x, y, w, h).build();
                        }
                    });
                }
            } catch (Exception ignored) {}
        });

        CustomizeGuiOverlayEvent.Chat.BUS.addListener(event -> {
            if (debugUrl == null && !ReflectionHelper.isMouseReleaseActive()) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                if (ReflectionHelper.isMcpControlMode() && mc.screen instanceof PauseScreen) {
                    mc.screen = null;
                }
                if (mc.screen == null) {
                    tick(mc);

                    if (!ReflectionHelper.isScreenshotInProgress()) {
                        ReflectionHelper.cacheFrameFromRenderThread(mc);
                    }

                    if (ReflectionHelper.isMcpControlMode() && !ReflectionHelper.isScreenshotInProgress()) {
                        int w = mc.getWindow().getGuiScaledWidth();
                        int h = mc.getWindow().getGuiScaledHeight();
                        double mx = mc.mouseHandler.xpos() * w / mc.getWindow().getScreenWidth();
                        double my = mc.mouseHandler.ypos() * h / mc.getWindow().getScreenHeight();
                        McpOverlayLogic.renderResumeButton(wrapRenderer(event.getGuiGraphics(), mc), mc.font, Component.translatable("mcpmod.control.resume").getString(), w, h, (int) mx, (int) my);
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

        ScreenEvent.Render.Post.BUS.addListener(event -> {
            if (ReflectionHelper.isScreenshotInProgress()) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                if (ReflectionHelper.isMcpControlMode()) {
                    if (mc.screen instanceof PauseScreen) mc.screen = null;
                    ReflectionHelper.cacheFrameFromRenderThread(mc);
                    int w = mc.getWindow().getGuiScaledWidth();
                    int h = mc.getWindow().getGuiScaledHeight();
                    double mx = mc.mouseHandler.xpos() * w / mc.getWindow().getScreenWidth();
                    double my = mc.mouseHandler.ypos() * h / mc.getWindow().getScreenHeight();
                    McpOverlayLogic.renderResumeButton(wrapRenderer(event.getGuiGraphics(), mc), mc.font, Component.translatable("mcpmod.control.resume").getString(), w, h, (int) mx, (int) my);
                } else if (mc.level != null && event.getScreen() != null && !(event.getScreen() instanceof PauseScreen)) {
                    int w = mc.getWindow().getGuiScaledWidth();
                    int h = mc.getWindow().getGuiScaledHeight();
                    double mx = mc.mouseHandler.xpos() * w / mc.getWindow().getScreenWidth();
                    double my = mc.mouseHandler.ypos() * h / mc.getWindow().getScreenHeight();
                    McpOverlayLogic.renderTransferButton(wrapRenderer(event.getGuiGraphics(), mc), mc.font, Component.translatable("mcpmod.control.pause_button").getString(), w, h, (int) mx, (int) my);
                }
            } catch (Exception ignored) {}
        });

        registerInputInterception();
    }
}

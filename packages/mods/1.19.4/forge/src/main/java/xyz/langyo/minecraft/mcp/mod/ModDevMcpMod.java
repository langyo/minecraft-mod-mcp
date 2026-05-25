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
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import org.lwjgl.glfw.GLFW;

@Mod("mcpmod")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    McpHttpServer httpServer;
    volatile String debugUrl = null;
    volatile boolean chatSent = false;

    private static org.lwjgl.glfw.GLFWMouseButtonCallbackI originalMouseButtonCallback = null;
    private static org.lwjgl.glfw.GLFWCursorPosCallbackI originalCursorCallback = null;
    private static boolean mouseInterceptorInstalled = false;
    private static java.lang.reflect.Field mousePosXField = null;
    private static java.lang.reflect.Field mousePosYField = null;

    private static void ensureMouseInterceptor(Minecraft mc) {
        if (mouseInterceptorInstalled) return;
        try {
            long handle = mc.getWindow().getWindow();
            originalMouseButtonCallback = GLFW.glfwSetMouseButtonCallback(handle, (window, button, action, mods) -> {
                if (ReflectionHelper.isMcpControlMode()) {
                    if (button == 0 && action == 1) {
                        double mx = getMouseX(mc);
                        double my = getMouseY(mc);
                        ReflectionHelper.handleOverlayClick((int) mx, (int) my, mc);
                    }
                    return;
                }
                if (originalMouseButtonCallback != null) {
                    originalMouseButtonCallback.invoke(window, button, action, mods);
                }
            });
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

    private static McpRenderer wrapRenderer(PoseStack ps, Minecraft mc) {
        return new McpRenderer() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {
                GuiComponent.fill(ps, x1, y1, x2, y2, color);
            }
            @Override public int drawString(Object font, String text, int x, int y, int color, boolean shadow) {
                Font f = mc.font;
                if (shadow) {
                    f.drawShadow(ps, text, x, y, color);
                } else {
                    f.draw(ps, text, x, y, color);
                }
                return f.width(text);
            }
            @Override public int getStringWidth(Object font, String text) {
                return mc.font.width(text);
            }
        };
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

        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Opening event) -> {
            if (ReflectionHelper.isMcpControlMode() && event.getScreen() instanceof PauseScreen) {
                event.setCanceled(true);
            }
        });

        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> {
            try {
                if (event.getScreen() instanceof PauseScreen) {
                    Screen screen = event.getScreen();
                    AbstractWidget widest = null;
                    int widestW = 0;
                    Class<?> clazz = screen.getClass();
                    while (clazz != null) {
                        for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                            f.setAccessible(true);
                            Object val;
                            try { val = f.get(screen); } catch (Exception ex) { continue; }
                            if (val instanceof java.util.List) {
                                for (Object item : (java.util.List<?>) val) {
                                    if (item instanceof AbstractWidget) {
                                        AbstractWidget aw = (AbstractWidget) item;
                                        if (aw.getWidth() >= 150 && aw.getWidth() >= widestW) {
                                            widest = aw;
                                            widestW = aw.getWidth();
                                        }
                                    }
                                }
                            }
                        }
                        clazz = clazz.getSuperclass();
                    }
                    if (widest == null) return;
                    int x = widest.getX();
                    int y = widest.getY();
                    int w = widest.getWidth();
                    int h = widest.getHeight();
                    int gap = 8;
                    int leftW = (w - gap) / 2;
                    int rightW = w - gap - leftW;
                    widest.setX(x + leftW + gap);
                    widest.setWidth(rightW);
                    String transferKey = ReflectionHelper.getMcpControlPauseTransferTranslationKey();
                    Button transferBtn = Button.builder(Component.translatable(transferKey), btn -> {
                        try {
                            Minecraft mc = Minecraft.getInstance();
                            ReflectionHelper.enterMcpControlMode(mc);
                            mc.setScreen(null);
                        } catch (Exception ignored) {}
                    }).bounds(x, y, leftW, h).build();
                    event.addListener(transferBtn);
                }
            } catch (Exception ignored) {}
        });

        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.MouseButtonPressed.Pre event) -> {
            if (!ReflectionHelper.isMcpControlMode()) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                double mx = getMouseX(mc);
                double my = getMouseY(mc);
                ReflectionHelper.handleOverlayClick((int) mx, (int) my, mc);
            } catch (Exception ignored) {}
            event.setCanceled(true);
        });

        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.MouseDragged.Pre event) -> {
            if (ReflectionHelper.isMcpControlMode()) event.setCanceled(true);
        });

        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.MouseButtonReleased.Pre event) -> {
            if (ReflectionHelper.isMcpControlMode()) event.setCanceled(true);
        });

        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.MouseScrolled.Pre event) -> {
            if (ReflectionHelper.isMcpControlMode()) event.setCanceled(true);
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
                        int w = mc.getWindow().getGuiScaledWidth();
                        int h = mc.getWindow().getGuiScaledHeight();
                        double mx = getMouseX(mc);
                        double my = getMouseY(mc);
                        McpOverlayLogic.renderResumeButton(wrapRenderer(event.getPoseStack(), mc), mc.font, Component.translatable("mcpmod.control.resume").getString(), w, h, (int) mx, (int) my);
                    }
                }
            } catch (Exception ignored) {}
        });

        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Render.Post event) -> {
            if (ReflectionHelper.isScreenshotInProgress()) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                Screen screen = event.getScreen();
                int w = mc.getWindow().getGuiScaledWidth();
                int h = mc.getWindow().getGuiScaledHeight();
                double mx = getMouseX(mc);
                double my = getMouseY(mc);

                if (ReflectionHelper.isMcpControlMode()) {
                    ReflectionHelper.cacheFrameFromRenderThread(mc);
                    McpOverlayLogic.renderResumeButton(wrapRenderer(event.getPoseStack(), mc), mc.font, Component.translatable("mcpmod.control.resume").getString(), w, h, (int) mx, (int) my);
                } else if (mc.level != null && screen != null && !(screen instanceof PauseScreen)) {
                    McpOverlayLogic.renderTransferButton(wrapRenderer(event.getPoseStack(), mc), mc.font, Component.translatable("mcpmod.control.pause_button").getString(), w, h, (int) mx, (int) my);
                }
            } catch (Exception ignored) {}
        });

        MinecraftForge.EVENT_BUS.addListener((InputEvent.MouseButton.Pre event) -> {
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
                if (mc.level != null && mc.screen != null && !(mc.screen instanceof PauseScreen) && event.getButton() == 0) {
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
                        long glfwHandle = mc.getWindow().getWindow();
                        w32ctrl.ensureHwndFromGlfw(glfwHandle);
                    }
                }
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
    }
}

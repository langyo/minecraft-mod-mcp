package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.IngameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;

import org.lwjgl.glfw.GLFW;

@Mod("mcpmod")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    McpHttpServer httpServer;
    volatile String debugUrl = null;
    volatile boolean chatSent = false;

    private static org.lwjgl.glfw.GLFWCursorPosCallbackI originalCursorCallback = null;
    private static org.lwjgl.glfw.GLFWMouseButtonCallbackI originalMouseButtonCallback = null;
    private static boolean cursorInterceptorInstalled = false;
    private static java.lang.reflect.Field mousePosXField = null;
    private static java.lang.reflect.Field mousePosYField = null;

    private static void ensureCursorInterceptor(Minecraft mc) {
        if (cursorInterceptorInstalled) return;
        try {
            long handle = mc.mainWindow.getHandle();
            originalCursorCallback = GLFW.glfwSetCursorPosCallback(handle, (window, xpos, ypos) -> {
                if (ReflectionHelper.isMcpControlMode()) {
                    try {
                        if (mousePosXField != null) mousePosXField.setDouble(mc.mouseHelper, xpos);
                        if (mousePosYField != null) mousePosYField.setDouble(mc.mouseHelper, ypos);
                    } catch (Exception ignored) {}
                } else if (originalCursorCallback != null) {
                    originalCursorCallback.invoke(window, xpos, ypos);
                }
            });
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
            java.lang.reflect.Field[] fields = mc.mouseHelper.getClass().getDeclaredFields();
            java.lang.reflect.Field firstDouble = null, secondDouble = null;
            for (java.lang.reflect.Field f : fields) {
                if (f.getType() == double.class) {
                    if (firstDouble == null) firstDouble = f;
                    else if (secondDouble == null) { secondDouble = f; break; }
                }
            }
            if (firstDouble != null) { firstDouble.setAccessible(true); mousePosXField = firstDouble; }
            if (secondDouble != null) { secondDouble.setAccessible(true); mousePosYField = secondDouble; }
            cursorInterceptorInstalled = true;
        } catch (Exception e) {
            System.err.println("[MCP-MOD] Cursor interceptor failed: " + e.getMessage());
        }
    }

    private static void forceGlfwMouseFree(Minecraft mc) {
        ensureCursorInterceptor(mc);
        try {
            long hwnd = mc.mainWindow.getHandle();
            if (GLFW.glfwGetInputMode(hwnd, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_NORMAL) {
                GLFW.glfwSetInputMode(hwnd, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            }
        } catch (Exception ignored) {}
    }

    private static McpRenderer wrapRenderer(Minecraft mc) {
        return new McpRenderer() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {
                net.minecraft.client.gui.AbstractGui.fill(x1, y1, x2, y2, color);
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

    private static void withFullScissor(Runnable action) {
        try {
            boolean wasEnabled = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            if (wasEnabled) org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            action.run();
            if (wasEnabled) org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        } catch (Exception e) {
            action.run();
        }
    }

    private static void renderScreenButton(Minecraft mc, Screen screen) {
        int w = mc.mainWindow.getScaledWidth();
        int h = mc.mainWindow.getScaledHeight();
        double mx = getMouseX(mc);
        double my = getMouseY(mc);
        if (ReflectionHelper.isMcpControlMode()) {
            forceGlfwMouseFree(mc);
            ReflectionHelper.cacheFrameFromRenderThread(mc);
            McpOverlayLogic.renderResumeButton(wrapRenderer(mc), mc.fontRenderer, new TranslationTextComponent("mcpmod.control.resume").getFormattedText(), w, h, (int) mx, (int) my);
        } else if (!(screen instanceof IngameMenuScreen)) {
            McpOverlayLogic.renderTransferButton(wrapRenderer(mc), mc.fontRenderer, new TranslationTextComponent("mcpmod.control.pause_button").getFormattedText(), w, h, (int) mx, (int) my);
        }
    }

    private static double getMouseX(Minecraft mc) {
        return mc.mouseHelper.getMouseX() * mc.mainWindow.getScaledWidth() / mc.mainWindow.getWidth();
    }

    private static double getMouseY(Minecraft mc) {
        return mc.mouseHelper.getMouseY() * mc.mainWindow.getScaledHeight() / mc.mainWindow.getHeight();
    }

    @SuppressWarnings("removal")
    public ModDevMcpMod() {
        INSTANCE = this;
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);

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

        MinecraftForge.EVENT_BUS.addListener((GuiOpenEvent event) -> {
            if (ReflectionHelper.isMcpControlMode() && event.getGui() instanceof IngameMenuScreen) {
                event.setCanceled(true);
            }
        });

        MinecraftForge.EVENT_BUS.addListener((GuiScreenEvent.InitGuiEvent.Post event) -> {
            try {
                if (event.getGui() instanceof IngameMenuScreen) {
                    McpScreenHelper.patchPauseScreen(event.getGui(), new McpScreenHelper.ButtonFactory() {
                        @Override public Object createButton(String translationKey, Runnable onClick, int x, int y, int w, int h) {
                            return new net.minecraft.client.gui.widget.button.Button(x, y, w, h, new TranslationTextComponent(translationKey).getFormattedText(), btn -> onClick.run());
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

                    if (ReflectionHelper.isMcpControlMode()) {
                        forceGlfwMouseFree(mc);
                    }

                    if (!ReflectionHelper.isScreenshotInProgress()) {
                        ReflectionHelper.cacheFrameFromRenderThread(mc);
                    }

                    if (ReflectionHelper.isMcpControlMode() && !ReflectionHelper.isScreenshotInProgress()) {
                        int w = mc.mainWindow.getScaledWidth();
                        int h = mc.mainWindow.getScaledHeight();
                        double mx = getMouseX(mc);
                        double my = getMouseY(mc);
                        withFullScissor(() -> McpOverlayLogic.renderResumeButton(wrapRenderer(mc), mc.fontRenderer, new TranslationTextComponent("mcpmod.control.resume").getFormattedText(), w, h, (int) mx, (int) my));
                    }
                }
            } catch (Exception ignored) {}
        });

        MinecraftForge.EVENT_BUS.addListener((GuiScreenEvent.DrawScreenEvent.Post event) -> {
            if (ReflectionHelper.isScreenshotInProgress()) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                Screen screen = event.getGui();
                if (screen == null) return;
                if (ReflectionHelper.isMcpControlMode() && screen instanceof IngameMenuScreen) {
                    mc.currentScreen = null;
                    return;
                }
                if (mc.world != null) {
                    withFullScissor(() -> renderScreenButton(mc, screen));
                }
            } catch (Exception e) { e.printStackTrace(); }
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
            } catch (Exception ignored) {}
            if (INSTANCE.chatSent) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.ingameGUI == null) return;
                INSTANCE.chatSent = true;
                String url = INSTANCE.debugUrl;
                mc.ingameGUI.getChatGUI().printChatMessage(new StringTextComponent("[MCP] Debug page: " + url).applyTextStyle(TextFormatting.WHITE));
            } catch (Exception ignored) {}
        });
    }

    private void setup(final FMLCommonSetupEvent event) {
    }
}

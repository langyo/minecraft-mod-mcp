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

    private static void renderScreenButton(Minecraft mc, GuiScreen screen) {
        int w = mc.mainWindow.getScaledWidth();
        int h = mc.mainWindow.getScaledHeight();
        double mx = getMouseX(mc);
        double my = getMouseY(mc);
        if (ReflectionHelper.isMcpControlMode()) {
            forceGlfwMouseFree(mc);
            ReflectionHelper.cacheFrameFromRenderThread(mc);
            String label = translate("mcpmod.control.resume");
            McpOverlayLogic.renderResumeButton(wrapRenderer(mc), mc.fontRenderer, label, w, h, (int) mx, (int) my);
        } else if (!(screen instanceof GuiIngameMenu)) {
            String label = translate("mcpmod.control.pause_button");
            McpOverlayLogic.renderTransferButton(wrapRenderer(mc), mc.fontRenderer, label, w, h, (int) mx, (int) my);
        }
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
                        handleClick(mc, button, action);
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

private static void restoreGlfwMouseGrab(Minecraft mc) {
        try {
            long hwnd = mc.mainWindow.getHandle();
            if (mc.currentScreen == null && mc.mouseHelper != null) {
                GLFW.glfwSetInputMode(hwnd, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
            }
        } catch (Exception ignored) {}
    }

    private static int transferCooldown = 0;
    private static boolean mouseHelperDumped = false;

    private static void handleClick(Minecraft mc, int button, int action) {
        if (button != 0 || action != 1) return;
        double mx = getMouseX(mc);
        double my = getMouseY(mc);
        System.out.println("[MCP-MOD] handleClick at (" + (int)mx + "," + (int)my + ") mcpMode=" + ReflectionHelper.isMcpControlMode() + " screen=" + (mc.currentScreen != null ? mc.currentScreen.getClass().getSimpleName() : "null"));

        if (ReflectionHelper.isMcpControlMode()) {
            String result = ReflectionHelper.handleOverlayClick((int) mx, (int) my, mc);
            System.out.println("[MCP-MOD] Overlay click result: " + result);
        } else if (mc.world != null && mc.currentScreen != null && !(mc.currentScreen instanceof GuiIngameMenu)) {
            String result = ReflectionHelper.handleTransferOverlayClick((int) mx, (int) my, mc);
            System.out.println("[MCP-MOD] Transfer click result: " + result);
        }
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

        MinecraftForge.EVENT_BUS.addListener((GuiScreenEvent.InitGuiEvent.Post event) -> {
            try {
                if (event.getGui() instanceof GuiIngameMenu) {
                    System.out.println("[MCP-MOD] GuiIngameMenu detected, attempting patch...");
                    McpScreenHelper.patchPauseScreen(event.getGui(), new McpScreenHelper.ButtonFactory() {
                        @Override public Object createButton(String translationKey, Runnable onClick, int x, int y, int w, int h) {
                            String displayText = translate(translationKey);
                            System.out.println("[MCP-MOD] Creating transfer button: " + displayText + " at (" + x + "," + y + ") " + w + "x" + h);
                            return new GuiButton(-999, x, y, w, h, displayText) {
                                @Override public void onClick(double mouseX, double mouseY) {
                                    try {
                                        onClick.run();
                                    } catch (Exception e) {
                                        System.err.println("[MCP-MOD] Transfer button error: " + e.getMessage());
                                    }
                                }
                            };
                        }
                    });
                }
            } catch (Exception e) {
                System.err.println("[MCP-MOD] Pause screen patch error: " + e.getMessage());
                e.printStackTrace();
            }
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
                if (screen == null) return;
                if (ReflectionHelper.isMcpControlMode() && screen instanceof GuiIngameMenu) {
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
                if (ReflectionHelper.isMcpControlMode()) {
                    handleClick(mc, event.getButton(), event.getAction());
                    return;
                }
                if (ReflectionHelper.shouldSuppressInput()) {
                    return;
                }
                if (mc.world != null && mc.currentScreen != null && !(mc.currentScreen instanceof GuiIngameMenu)) {
                    if (event.getButton() == 0 && event.getAction() == 1) {
                        handleClick(mc, event.getButton(), event.getAction());
                    }
                }
            } catch (Exception ignored) {}
        });

        MinecraftForge.EVENT_BUS.addListener((GuiScreenEvent.MouseInputEvent event) -> {
            if (ReflectionHelper.isMcpControlMode()) {
                try {
                    Minecraft mc = Minecraft.getInstance();
                    if (GLFW.glfwGetMouseButton(mc.mainWindow.getHandle(), GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS) {
                        handleClick(mc, 0, 1);
                    }
                } catch (Exception ignored) {}
                try { event.setCanceled(true); } catch (Exception ignored) {}
            }
        });

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (INSTANCE == null || INSTANCE.debugUrl == null) return;

            Minecraft mc;
            try { mc = Minecraft.getInstance(); } catch (Exception e) { return; }

            if (ReflectionHelper.isMcpControlMode()) {
                transferCooldown = 10;
                if (event.phase == TickEvent.Phase.START) {
                    try { forceGlfwMouseFree(mc); } catch (Exception ignored) {}
                } else {
                    try {
                        if (!mouseHelperDumped && mc.mouseHelper != null) {
                            mouseHelperDumped = true;
                            System.out.println("[MCP-MOD] MouseHelper class: " + mc.mouseHelper.getClass().getName());
                            for (java.lang.reflect.Field f : mc.mouseHelper.getClass().getDeclaredFields()) {
                                f.setAccessible(true);
                                try {
                                    System.out.println("[MCP-MOD]   " + f.getName() + " (" + f.getType().getSimpleName() + ") = " + f.get(mc.mouseHelper));
                                } catch (Exception ignored2) {}
                            }
                        }
                        ReflectionHelper.tickMouseRelease(mc);
                        ReflectionHelper.tickMcpControlMode(mc);
                        ReflectionHelper.tickVideoCapture(mc);
                        forceGlfwMouseFree(mc);
                    } catch (Exception ignored) {}
                }
                return;
            }

            if (event.phase != TickEvent.Phase.END) return;
            if (transferCooldown > 0) {
                if (transferCooldown == 10) {
                    try { restoreGlfwMouseGrab(mc); } catch (Exception ignored) {}
                }
                transferCooldown--;
            }
            try {
                ReflectionHelper.tickMouseRelease(mc);
                ReflectionHelper.tickMcpControlMode(mc);
                ReflectionHelper.tickVideoCapture(mc);
            } catch (Exception ignored) {}
            if (INSTANCE.chatSent) return;
            try {
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

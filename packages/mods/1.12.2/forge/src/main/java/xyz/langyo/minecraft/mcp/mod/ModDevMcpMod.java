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
import net.minecraft.util.MouseHelper;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

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
            } else if (originalMouseHelper != null) {
                if (noGrabMouseHelper == null) noGrabMouseHelper = new NoGrabMouseHelper();
                mc.mouseHelper = noGrabMouseHelper;
            }
            if (Mouse.isGrabbed()) {
                Mouse.setGrabbed(false);
            }
            while (Mouse.next()) {}
        } catch (Exception ignored) {}
    }

    private static java.util.Map<String, String> translations = new java.util.HashMap<>();
    private static boolean translationsLoaded = false;

    private static synchronized void ensureTranslationsLoaded() {
        if (translationsLoaded) return;
        translationsLoaded = true;
        translations.put("mcpmod.control.overlay", "MCP is controlling the game");
        translations.put("mcpmod.control.transfer", "Transfer control to MCP");
        translations.put("mcpmod.control.resume", "Resume Manual Control");
        translations.put("mcpmod.control.menu", "System Menu");
        translations.put("mcpmod.control.pause_button", "MCP Take Over");
        try {
            Minecraft mc = Minecraft.getMinecraft();
            String locale = mc.getLanguageManager().getCurrentLanguage().getLanguageCode();
            String langFile = "/assets/mcpmod/lang/" + locale + ".lang";
            java.io.InputStream is = ModDevMcpMod.class.getResourceAsStream(langFile);
            if (is == null) {
                langFile = "/assets/mcpmod/lang/" + locale.toLowerCase() + ".lang";
                is = ModDevMcpMod.class.getResourceAsStream(langFile);
            }
            if (is != null) {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        translations.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                    }
                }
                reader.close();
            }
        } catch (Exception ignored) {}
    }

    private static String translate(String key) {
        ensureTranslationsLoaded();
        String t = translations.get(key);
        return t != null ? t : key;
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
    public void onGuiInitPre(GuiScreenEvent.InitGuiEvent.Pre event) {
        if (ReflectionHelper.isMcpControlMode() && isPauseMenu(event.getGui())) {
            event.setCanceled(true);
            try { Minecraft.getMinecraft().currentScreen = null; } catch (Exception ignored) {}
        }
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        try {
            if (isPauseMenu(event.getGui())) {
                McpScreenHelper.patchPauseScreen(event.getGui(), new McpScreenHelper.ButtonFactory() {
                    @Override public Object createButton(String translationKey, Runnable onClick, int x, int y, int w, int h) {
                        String displayText = translate(translationKey);
                        GuiButton btn = new GuiButton(-999, x, y, w, h, displayText) {
                            @Override public boolean mousePressed(Minecraft mc2, int mx, int my) {
                                if (super.mousePressed(mc2, mx, my)) {
                                    try {
                                        Minecraft mc = Minecraft.getMinecraft();
                                        ReflectionHelper.enterMcpControlMode(mc);
                                        mc.displayGuiScreen(null);
                                    } catch (Exception ignored) {}
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
        if (debugUrl == null && !ReflectionHelper.isMouseReleaseActive() && !ReflectionHelper.isMcpControlMode()) return;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            ReflectionHelper.tickMouseRelease(mc);
            ReflectionHelper.tickMcpControlMode(mc);

            if (ReflectionHelper.isMcpControlMode()) {
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
                    String label = translate("mcpmod.control.resume");
                    McpOverlayLogic.renderResumeButton(wrapRenderer(mc), mc.fontRenderer, label, w, h, mx, my);
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
                GL11.glScissor(0, 0, mc.displayWidth, mc.displayHeight);
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                forceLwjgl2MouseFree();
                ReflectionHelper.cacheFrameFromRenderThread(mc);
                String label = translate("mcpmod.control.resume");
                McpOverlayLogic.renderResumeButton(wrapRenderer(mc), mc.fontRenderer, label, w, h, mx, my);
            } else if (mc.world != null && screen != null && !isPauseMenu(screen)) {
                GL11.glScissor(0, 0, mc.displayWidth, mc.displayHeight);
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                String label = translate("mcpmod.control.pause_button");
                McpOverlayLogic.renderTransferButton(wrapRenderer(mc), mc.fontRenderer, label, w, h, mx, my);
            }
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public void onMouseInput(MouseEvent event) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (ReflectionHelper.isMcpControlMode()) {
                event.setCanceled(true);
                return;
            }
            if (ReflectionHelper.shouldSuppressInput()) {
                event.setCanceled(true);
                return;
            }
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public void onGuiMouseInputPre(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (ReflectionHelper.isMcpControlMode()) {
            event.setCanceled(true);
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
                McpPlatformControl ctrl = McpControlFactory.get();
                if (ctrl instanceof McpWin32Control) {
                    McpWin32Control w32ctrl = (McpWin32Control) ctrl;
                    if (w32ctrl.getMcHwnd() == 0) {
                        w32ctrl.ensureHwndFromLwjgl2Display();
                    }
                }
                if (Mouse.isButtonDown(0)) {
                    int mx = getMouseX(mc);
                    int my = getMouseY(mc);
                    ReflectionHelper.handleOverlayClick(mx, my, mc);
                }
            }
            return;
        }

        if (!ReflectionHelper.isMcpControlMode() && event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen == null && originalMouseHelper != null) {
                mc.mouseHelper = originalMouseHelper;
                originalMouseHelper = null;
            }
        }

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

            if (mc.world != null && mc.currentScreen != null && !(mc.currentScreen instanceof GuiIngameMenu) && Mouse.isButtonDown(0)) {
                int mx = getMouseX(mc);
                int my = getMouseY(mc);
                ReflectionHelper.handleTransferOverlayClick(mx, my, mc);
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

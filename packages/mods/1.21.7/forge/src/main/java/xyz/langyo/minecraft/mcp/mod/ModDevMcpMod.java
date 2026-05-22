package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
    private final boolean dependenciesAvailable;

    private static volatile int btnResumeX, btnResumeY, btnResumeW, btnResumeH;
    private static volatile int btnMenuX, btnMenuY, btnMenuW, btnMenuH;
    private static volatile int btnTransferX, btnTransferY, btnTransferW, btnTransferH;

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

    @SuppressWarnings("removal")
    private static void registerTickListener() {
        TickEvent.ClientTickEvent.BUS.addListener(event -> {
            if (INSTANCE == null || INSTANCE.debugUrl == null) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                ReflectionHelper.tickMouseRelease(mc);
                ReflectionHelper.tickMcpControlMode(mc);
                ReflectionHelper.tickVideoCapture(mc);
                McpPlatformControl ctrl = McpControlFactory.get();
                if (ctrl instanceof McpWin32Control w32ctrl) {
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
                    .append(Component.literal("[MCP] Debug page: ").withStyle(s -> s.withColor(0xFFFFFF)))
                    .append(Component.literal(url).withStyle(s -> s
                        .withColor(0xFFFFFF)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(url)))
                    ));
                mc.gui.getChat().addMessage(msg);
            } catch (Exception ignored) {}
        });
    }

    private static List<Field> allFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) fields.add(f);
            c = c.getSuperclass();
        }
        return fields;
    }

    private static boolean isBottomWideButton(Object obj) {
        if (!(obj instanceof Button btn)) return false;
        return btn.getY() >= 180 && btn.getWidth() >= 150;
    }

    private static boolean addRenderableWidget(Screen screen, Button widget) {
        try {
            for (Class<?> c = screen.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (!m.getName().equals("addRenderableWidget") || m.getParameterCount() != 1) continue;
                    if (!m.getParameterTypes()[0].isAssignableFrom(widget.getClass())) continue;
                    m.setAccessible(true);
                    m.invoke(screen, widget);
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean addToNamedList(Screen screen, String fieldName, Object widget) {
        try {
            for (Field f : allFields(screen.getClass())) {
                if (!f.getName().equals(fieldName)) continue;
                f.setAccessible(true);
                Object val = f.get(screen);
                if (!(val instanceof List<?> list)) continue;
                @SuppressWarnings("unchecked")
                List<Object> mutable = (List<Object>) list;
                if (!mutable.contains(widget)) mutable.add(widget);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static void patchPauseButtons(PauseScreen screen) {
        try {
            Button originalQuit = null;
            for (Field f : allFields(screen.getClass())) {
                f.setAccessible(true);
                Object val = f.get(screen);
                if (isBottomWideButton(val)) {
                    originalQuit = (Button) val;
                    break;
                }
            }
            if (originalQuit == null) {
                for (Field f : allFields(screen.getClass())) {
                    f.setAccessible(true);
                    Object val = f.get(screen);
                    if (val instanceof List<?> list) {
                        for (Object entry : list) {
                            if (isBottomWideButton(entry)) {
                                originalQuit = (Button) entry;
                                break;
                            }
                        }
                    }
                    if (originalQuit != null) break;
                }
            }
            if (originalQuit == null) return;

            int x = originalQuit.getX();
            int y = originalQuit.getY();
            int w = originalQuit.getWidth();
            int h = originalQuit.getHeight();
            int gap = 8;
            int leftW = (w - gap) / 2;
            int rightW = w - gap - leftW;

            originalQuit.setX(x + leftW + gap);
            originalQuit.setWidth(rightW);

            Button transfer = Button.builder(Component.translatable(ReflectionHelper.getMcpControlPauseTransferTranslationKey()), btn -> {
                try {
                    Minecraft mc = Minecraft.getInstance();
                    ReflectionHelper.enterMcpControlMode(mc);
                    mc.setScreen(null);
                } catch (Exception ignored) {}
            }).bounds(x, y, leftW, h).build();

            boolean added = addRenderableWidget(screen, transfer);
            if (!added) {
                addToNamedList(screen, "renderables", transfer);
                addToNamedList(screen, "children", transfer);
                addToNamedList(screen, "narratables", transfer);
            }
        } catch (Exception ignored) {}
    }

    private static void renderOverlay(GuiGraphics g, Minecraft mc, int screenW, int screenH, int mouseX, int mouseY) {
        Component label = Component.translatable("mcpmod.control.resume");
        int textW = mc.font.width(label);
        int pad = 4;
        int btnW = textW + pad * 2 + 4;
        int btnH = 16;
        int margin = 4;
        int bx = screenW - btnW - margin;
        int by = margin;

        btnResumeX = bx; btnResumeY = by; btnResumeW = btnW; btnResumeH = btnH;

        ReflectionHelper.setOverlayButtonBounds(bx, by, btnW, btnH, 0, 0, 0, 0);

        boolean hover = mouseX >= bx && mouseX <= bx + btnW && mouseY >= by && mouseY <= by + btnH;
        int bg = hover ? 0xDD666666 : 0xBB444444;
        g.fill(bx, by, bx + btnW, by + btnH, bg);
        g.fill(bx, by, bx + btnW, by, 0xFF888888);
        g.fill(bx, by + btnH - 1, bx + btnW, by + btnH, 0xFF333333);
        g.drawString(mc.font, label, bx + pad + 2, by + (btnH - 8) / 2, 0xFFFFFFFF, false);
    }

    private static void renderTransferOverlay(GuiGraphics g, Minecraft mc, int screenW, int screenH, int mouseX, int mouseY) {
        Component label = Component.translatable("mcpmod.control.pause_button");
        int textW = mc.font.width(label);
        int pad = 4;
        int btnW = textW + pad * 2 + 4;
        int btnH = 16;
        int margin = 4;
        int bx = screenW - btnW - margin;
        int by = margin;

        btnTransferX = bx; btnTransferY = by; btnTransferW = btnW; btnTransferH = btnH;
        ReflectionHelper.setTransferButtonBounds(bx, by, btnW, btnH);

        boolean hover = mouseX >= bx && mouseX <= bx + btnW && mouseY >= by && mouseY <= by + btnH;
        int bg = hover ? 0xDD446644 : 0xBB335533;
        g.fill(bx, by, bx + btnW, by + btnH, bg);
        g.fill(bx, by, bx + btnW, by, 0xFF88AA88);
        g.fill(bx, by + btnH - 1, bx + btnW, by + btnH, 0xFF336633);
        g.drawString(mc.font, label, bx + pad + 2, by + (btnH - 8) / 2, 0xFFFFFFFF, false);
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
        dependenciesAvailable = depsOk;

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

        ScreenEvent.Init.Post.BUS.addListener(event -> {
            try {
                if (event.getScreen() instanceof PauseScreen pauseScreen) {
                    patchPauseButtons(pauseScreen);
                }
            } catch (Exception ignored) {}
        });

        CustomizeGuiOverlayEvent.Chat.BUS.addListener(event -> {
            if (debugUrl == null && !ReflectionHelper.isMouseReleaseActive()) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == null) {
                    ReflectionHelper.tickMouseRelease(mc);
                    ReflectionHelper.tickMcpControlMode(mc);

                    if (!ReflectionHelper.isScreenshotInProgress()) {
                        ReflectionHelper.cacheFrameFromRenderThread(mc);
                    }

                    if (ReflectionHelper.isMcpControlMode() && !ReflectionHelper.isScreenshotInProgress()) {
                        int w = mc.getWindow().getGuiScaledWidth();
                        int h = mc.getWindow().getGuiScaledHeight();
                        double mx = mc.mouseHandler.xpos() * w / mc.getWindow().getScreenWidth();
                        double my = mc.mouseHandler.ypos() * h / mc.getWindow().getScreenHeight();
                        renderOverlay(event.getGuiGraphics(), mc, w, h, (int) mx, (int) my);
                    }
                }
            } catch (Exception ignored) {}
        });

        ScreenEvent.Render.Post.BUS.addListener(event -> {
            if (ReflectionHelper.isScreenshotInProgress()) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                Screen screen = event.getScreen();
                int w = mc.getWindow().getGuiScaledWidth();
                int h = mc.getWindow().getGuiScaledHeight();
                double mx = mc.mouseHandler.xpos() * w / mc.getWindow().getScreenWidth();
                double my = mc.mouseHandler.ypos() * h / mc.getWindow().getScreenHeight();

                if (ReflectionHelper.isMcpControlMode()) {
                    ReflectionHelper.cacheFrameFromRenderThread(mc);
                    renderOverlay(event.getGuiGraphics(), mc, w, h, (int) mx, (int) my);
                } else if (mc.level != null && screen != null && !(screen instanceof PauseScreen)) {
                    renderTransferOverlay(event.getGuiGraphics(), mc, w, h, (int) mx, (int) my);
                }
            } catch (Exception ignored) {}
        });

        registerInputInterception();
        registerTickListener();
    }
}

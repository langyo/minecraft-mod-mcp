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
import net.minecraft.Util;

@Mod("mcpmod")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    McpHttpServer httpServer;
    volatile String debugUrl = null;
    volatile boolean chatSent = false;
    private final boolean dependenciesAvailable;

    private static volatile int btnResumeX, btnResumeY, btnResumeW, btnResumeH;
    private static volatile int btnMenuX, btnMenuY, btnMenuW, btnMenuH;
    private static volatile int urlX, urlY, urlW, urlH;

    @SuppressWarnings("removal")
    private static void registerInputInterception() {
        InputEvent.MouseButton.Pre.BUS.addListener(event -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (!ReflectionHelper.isMcpControlMode()) return false;
                if (event.getButton() == 0) {
                    double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
                    double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
                    String result = ReflectionHelper.handleOverlayClick((int) mx, (int) my, mc);
                    if (!result.equals("blocked") && !result.equals("cooldown") && !result.equals("not_in_control_mode")) {
                        return false;
                    }
                    if (mx >= urlX && mx <= urlX + urlW && my >= urlY && my <= urlY + urlH) {
                        String url = INSTANCE != null ? INSTANCE.debugUrl : null;
                        if (url != null) Util.getPlatform().openUri(java.net.URI.create(url));
                        return true;
                    }
                }
                return true;
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
                    .append(Component.literal("[MCP] Debug page: ").withStyle(s -> s.withColor(0x55FF55)))
                    .append(Component.literal(url).withStyle(s -> s
                        .withColor(0x5555FF)
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
        g.fill(0, 0, screenW, screenH, 0x88404040);

        Component title = Component.translatable("mcpmod.control.overlay");
        int textW = mc.font.width(title);
        g.drawString(mc.font, title, (screenW - textW) / 2, Math.max(20, screenH / 5), 0xFFFFFFFF, true);

        String debugUrl = INSTANCE != null ? INSTANCE.debugUrl : null;
        if (debugUrl != null) {
            Component urlLabel = Component.literal(debugUrl).withStyle(s -> s
                .withColor(0x55FF55)
                .withUnderlined(true)
            );
            int urlTextW = mc.font.width(urlLabel);
            int cx = (screenW - urlTextW) / 2;
            int cy = Math.max(20, screenH / 5) + 14;
            urlX = cx; urlY = cy; urlW = urlTextW; urlH = 9;
            g.drawString(mc.font, urlLabel, cx, cy, 0xFF55FF55, false);
        }

        int btnW = 150, btnH = 20, gap = 10;
        int totalW = btnW * 2 + gap;
        int startX = (screenW - totalW) / 2;
        int btnY = screenH - 40;

        int rx = startX, ry = btnY;
        int mx = startX + btnW + gap, my = btnY;

        btnResumeX = rx; btnResumeY = ry; btnResumeW = btnW; btnResumeH = btnH;
        btnMenuX = mx; btnMenuY = my; btnMenuW = btnW; btnMenuH = btnH;

        ReflectionHelper.setOverlayButtonBounds(rx, ry, btnW, btnH, mx, my, btnW, btnH);

        boolean hoverResume = mouseX >= rx && mouseX <= rx + btnW && mouseY >= ry && mouseY <= ry + btnH;
        boolean hoverMenu = mouseX >= mx && mouseX <= mx + btnW && mouseY >= my && mouseY <= my + btnH;

        int bgNormal = 0xFF555555;
        int bgHover = 0xFF777777;
        g.fill(rx, ry, rx + btnW, ry + btnH, hoverResume ? bgHover : bgNormal);
        g.fill(rx, ry, rx + btnW, ry, 0xFFAAAAAA);
        g.fill(rx, ry + btnH - 1, rx + btnW, ry + btnH, 0xFF333333);
        g.fill(rx, ry, rx, ry + btnH, 0xFF999999);
        g.fill(rx + btnW, ry, rx + btnW, ry + btnH, 0xFF444444);
        Component resumeText = Component.translatable("mcpmod.control.resume");
        int rtw = mc.font.width(resumeText);
        g.drawString(mc.font, resumeText, rx + (btnW - rtw) / 2, ry + (btnH - 8) / 2, 0xFFFFFFFF, false);

        g.fill(mx, my, mx + btnW, my + btnH, hoverMenu ? bgHover : bgNormal);
        g.fill(mx, my, mx + btnW, my, 0xFFAAAAAA);
        g.fill(mx, my + btnH - 1, mx + btnW, my + btnH, 0xFF333333);
        g.fill(mx, my, mx, my + btnH, 0xFF999999);
        g.fill(mx + btnW, my, mx + btnW, my + btnH, 0xFF444444);
        Component menuText = Component.translatable("mcpmod.control.menu");
        int mtw = mc.font.width(menuText);
        g.drawString(mc.font, menuText, mx + (btnW - mtw) / 2, my + (btnH - 8) / 2, 0xFFFFFFFF, false);
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
            if (!ReflectionHelper.isMcpControlMode()) return;
            if (ReflectionHelper.isScreenshotInProgress()) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                if (!ReflectionHelper.isScreenshotInProgress()) {
                    ReflectionHelper.cacheFrameFromRenderThread(mc);
                }
                int w = mc.getWindow().getGuiScaledWidth();
                int h = mc.getWindow().getGuiScaledHeight();
                double mx = mc.mouseHandler.xpos() * w / mc.getWindow().getScreenWidth();
                double my = mc.mouseHandler.ypos() * h / mc.getWindow().getScreenHeight();
                renderOverlay(event.getGuiGraphics(), mc, w, h, (int) mx, (int) my);
            } catch (Exception ignored) {}
        });

        registerInputInterception();
        registerTickListener();
    }
}

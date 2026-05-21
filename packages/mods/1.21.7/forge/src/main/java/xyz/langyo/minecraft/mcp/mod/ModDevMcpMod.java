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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
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

    @SuppressWarnings("removal")
    private static void registerInputInterception() {
        InputEvent.MouseButton.Pre.BUS.addListener(event -> {
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (!ReflectionHelper.isMcpControlMode()) return false;
                if (event.getButton() != 0) return true;
                int w = mc.getWindow().getGuiScaledWidth();
                int h = mc.getWindow().getGuiScaledHeight();
                int guiX = (int) (mc.mouseHandler.xpos() * w / mc.getWindow().getGuiScaledWidth());
                int guiY = (int) (mc.mouseHandler.ypos() * h / mc.getWindow().getGuiScaledHeight());
                ReflectionHelper.handleOverlayClick(guiX, guiY, mc);
                return true;
            } catch (Exception ignored) { return false; }
        });
    }

    @SuppressWarnings("removal")
    private static void registerTickListener() {
        TickEvent.ClientTickEvent.BUS.addListener(event -> {
            if (INSTANCE == null || INSTANCE.debugUrl == null) return;
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                ReflectionHelper.tickMouseRelease(mc);
                ReflectionHelper.tickMcpControlMode(mc);
                ReflectionHelper.tickVideoCapture(mc);
                xyz.langyo.minecraft.mcp.common.McpPlatformControl ctrl = xyz.langyo.minecraft.mcp.common.McpControlFactory.get();
                if (ctrl instanceof xyz.langyo.minecraft.mcp.common.McpWin32Control w32ctrl) {
                    if (w32ctrl.getMcHwnd() == 0) {
                        long glfwHandle = mc.getWindow().getWindow();
                        w32ctrl.ensureHwndFromGlfw(glfwHandle);
                    }
                }
            } catch (Exception ignored) {}
            if (INSTANCE.chatSent) return;
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
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
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    ReflectionHelper.enterMcpControlMode(mc);
                    ReflectionHelper.closeScreen(mc);
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

        CustomizeGuiOverlayEvent.DebugText.BUS.addListener(event -> {
            if (debugUrl != null && event.getSide() == CustomizeGuiOverlayEvent.DebugText.Side.Left) {
                event.getText().add("\u00a7a[MCP] " + debugUrl);
            }
        });

        ScreenEvent.Render.Post.BUS.addListener(event -> {
            if (debugUrl == null) return;
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                ReflectionHelper.tickMouseRelease(mc);
                ReflectionHelper.tickMcpControlMode(mc);
                net.minecraft.client.gui.GuiGraphics g = event.getGuiGraphics();
                String text = "[MCP] " + debugUrl;
                g.drawString(mc.font, text, 4, 4, 0xFF55FF55, true);
                ReflectionHelper.cacheFrameFromRenderThread(mc);
            } catch (Exception ignored) {}
        });

        ScreenEvent.Init.Post.BUS.addListener(event -> {
            try {
                if (event.getScreen() instanceof PauseScreen pauseScreen) {
                    patchPauseButtons(pauseScreen);
                }
            } catch (Exception ignored) {}
        });

        CustomizeGuiOverlayEvent.DebugText.BUS.addListener(event -> {
            if (debugUrl == null && !ReflectionHelper.isMouseReleaseActive()) return;
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.screen == null) {
                    ReflectionHelper.tickMouseRelease(mc);
                    ReflectionHelper.tickMcpControlMode(mc);
                }
            } catch (Exception ignored) {}
        });

        registerInputInterception();
        registerTickListener();

        CustomizeGuiOverlayEvent.Chat.BUS.addListener(event -> {
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (ReflectionHelper.shouldRenderMcpControlOverlay(mc) && !ReflectionHelper.isScreenshotInProgress()) {
                    GuiGraphics g = event.getGuiGraphics();
                    int w = event.getWindow().getGuiScaledWidth();
                    int h = event.getWindow().getGuiScaledHeight();
                    g.fill(0, 0, w, h, 0x88404040);

                    Component title = Component.translatable(ReflectionHelper.getMcpControlOverlayTranslationKey());
                    int textW = mc.font.width(title);
                    g.drawString(mc.font, title, (w - textW) / 2, Math.max(20, h / 5), 0xFFFFFFFF, true);

                    int btnW = 150;
                    int btnH = 20;
                    int gap = 10;
                    int totalW = btnW * 2 + gap;
                    int startX = (w - totalW) / 2;
                    int btnY = h - 40;

                    int mouseX = (int) (mc.mouseHandler.xpos() * w / mc.getWindow().getGuiScaledWidth());
                    int mouseY = (int) (mc.mouseHandler.ypos() * h / mc.getWindow().getGuiScaledHeight());

                    int resumeX = startX;
                    int menuX = startX + btnW + gap;

                    boolean hoverResume = mouseX >= resumeX && mouseX <= resumeX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
                    boolean hoverMenu = mouseX >= menuX && mouseX <= menuX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

                    int resumeColor = hoverResume ? 0xFF555555 : 0xFF333333;
                    int menuColor = hoverMenu ? 0xFF555555 : 0xFF333333;

                    g.fill(resumeX, btnY, resumeX + btnW, btnY + btnH, resumeColor);
                    g.fill(menuX, btnY, menuX + btnW, btnY + btnH, menuColor);

                    int border = 0xFFAAAAAA;
                    g.fill(resumeX, btnY, resumeX + btnW, btnY + 1, border);
                    g.fill(resumeX, btnY + btnH - 1, resumeX + btnW, btnY + btnH, border);
                    g.fill(resumeX, btnY, resumeX + 1, btnY + btnH, border);
                    g.fill(resumeX + btnW - 1, btnY, resumeX + btnW, btnY + btnH, border);
                    g.fill(menuX, btnY, menuX + btnW, btnY + 1, border);
                    g.fill(menuX, btnY + btnH - 1, menuX + btnW, btnY + btnH, border);
                    g.fill(menuX, btnY, menuX + 1, btnY + btnH, border);
                    g.fill(menuX + btnW - 1, btnY, menuX + btnW, btnY + btnH, border);

                    Component resumeText = Component.translatable("mcpmod.control.resume");
                    Component menuText = Component.translatable("mcpmod.control.menu");
                    int resumeTextW = mc.font.width(resumeText);
                    int menuTextW = mc.font.width(menuText);
                    g.drawString(mc.font, resumeText, resumeX + (btnW - resumeTextW) / 2, btnY + (btnH - 8) / 2, 0xFFFFFFFF, false);
                    g.drawString(mc.font, menuText, menuX + (btnW - menuTextW) / 2, btnY + (btnH - 8) / 2, 0xFFFFFFFF, false);

                    ReflectionHelper.setOverlayButtonBounds(resumeX, btnY, btnW, btnH, menuX, btnY, btnW, btnH);
                }
                if (debugUrl != null) {
                    ReflectionHelper.cacheFrameFromRenderThread(mc);
                }
            } catch (Exception ignored) {}
        });
    }
}

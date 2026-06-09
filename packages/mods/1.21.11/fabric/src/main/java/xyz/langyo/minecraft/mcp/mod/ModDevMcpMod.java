package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;


public class ModDevMcpMod implements ClientModInitializer {
    public static ModDevMcpMod INSTANCE;
    private McpHttpServer httpServer;
    private ReflectedInputHandler handler;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        int port = McpConfig.getServerPort();
        httpServer = new McpHttpServer(handler, port);

        new Thread(() -> {
            try {
                Thread.sleep(5000);
                try {
                    Object mc = MinecraftClient.getInstance();
                    if (mc != null) ReflectionHelper.setMinecraftInstance(mc);
                } catch (Exception ignored) {}
                httpServer.start();
            } catch (Exception e) {
            }
        }, "MCP-HTTP").start();
    }

    private static McpRenderer wrapRenderer(DrawContext ctx) {
        return new McpRenderer() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {
                ctx.fill(x1, y1, x2, y2, color);
            }
            @Override public int drawString(Object font, String text, int x, int y, int color, boolean shadow) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (shadow) {
                    ctx.drawText(mc.textRenderer, text, x, y, color, true);
                } else {
                    ctx.drawText(mc.textRenderer, text, x, y, color, false);
                }
                return mc.textRenderer.getWidth(text);
            }
            @Override public int getStringWidth(Object font, String text) {
                MinecraftClient mc = MinecraftClient.getInstance();
                return mc.textRenderer.getWidth(text);
            }
        };
    }

    private static int getGuiWidth() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.getWindow().getScaledWidth();
    }

    private static int getGuiHeight() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.getWindow().getScaledHeight();
    }

    private static double getMouseX() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.mouse.getX() * (double) mc.getWindow().getScaledWidth() / (double) mc.getWindow().getWidth();
    }

    private static double getMouseY() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.mouse.getY() * (double) mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight();
    }

    private int compatWarningTicks = 0;

    public void onClientTick() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (ReflectionHelper.isMcpControlMode()) {
                ReflectionHelper.tickMcpControlMode(mc);
            }
            Screen screen = mc.currentScreen;
            if (screen != null && screen.getClass().getSimpleName().equals("class_405")) {
                compatWarningTicks++;
                if (compatWarningTicks == 40) {
                    try {
                        Object children = null;
                        for (java.lang.reflect.Field f : screen.getClass().getSuperclass().getDeclaredFields()) {
                            if (java.util.List.class.isAssignableFrom(f.getType())) {
                                f.setAccessible(true);
                                Object v = f.get(screen);
                                if (v instanceof java.util.List && !((java.util.List<?>)v).isEmpty()) {
                                    children = v;
                                    break;
                                }
                            }
                        }
                        if (children instanceof java.util.List) {
                            for (Object child : (java.util.List<?>)children) {
                                String cname = child.getClass().getSimpleName();
                                if (cname.equals("class_12231") || cname.equals("ButtonWidget")) {
                                    for (java.lang.reflect.Method m : child.getClass().getDeclaredMethods()) {
                                        if (m.getName().equals("method_25403") || (m.getParameterCount() == 0 && m.getReturnType() == boolean.class)) {
                                            m.setAccessible(true);
                                            Boolean hovered = (Boolean)m.invoke(child);
                                            if (hovered != null && hovered) continue;
                                        }
                                    }
                                    for (java.lang.reflect.Method m : child.getClass().getMethods()) {
                                        if (m.getParameterCount() == 2 && m.getParameterTypes()[0] == double.class && m.getParameterTypes()[1] == double.class) {
                                            java.lang.reflect.Field xf = null, yf = null;
                                            for (java.lang.reflect.Field ff : child.getClass().getDeclaredFields()) {
                                                if (ff.getType() == int.class) { ff.setAccessible(true); try { int val = ff.getInt(child); if (val > 0 && val < 500) { if (xf == null) xf = ff; else if (yf == null) yf = ff; } } catch (Exception ignored) {} }
                                            }
                                            if (xf != null && yf != null) {
                                                xf.setAccessible(true); yf.setAccessible(true);
                                                int bx = xf.getInt(child), by = yf.getInt(child);
                                                try { mc.mouse.lockCursor(); } catch (Exception ignored) {}
                                                for (java.lang.reflect.Method mm : mc.mouse.getClass().getMethods()) {
                                                    if (mm.getName().equals("method_1605") || (mm.getParameterCount() == 2 && mm.getParameterTypes()[0] == long.class && mm.getParameterTypes()[1] == double.class)) {
                                                        mm.setAccessible(true); mm.invoke(mc.mouse, mc.getWindow().getHandle(), (double)bx); break;
                                                    }
                                                    if (mm.getName().equals("method_1606") || (mm.getParameterCount() == 2 && mm.getParameterTypes()[0] == long.class && mm.getParameterTypes()[1] == double.class)) {
                                                        mm.setAccessible(true); mm.invoke(mc.mouse, mc.getWindow().getHandle(), (double)by);
                                                    }
                                                }
                                                m.setAccessible(true); m.invoke(child, (double)bx, (double)by);
                                                break;
                                            }
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            } else {
                compatWarningTicks = 0;
            }
        } catch (Exception ignored) {}
    }

    public void onInGameHudRender(Object ctx, float tickDelta) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (ReflectionHelper.isMcpControlMode() && mc.currentScreen == null) {
                int w = getGuiWidth();
                int h = getGuiHeight();
                int mx = (int) getMouseX();
                int my = (int) getMouseY();
                McpOverlayLogic.renderResumeButton(wrapRenderer((DrawContext) ctx), mc.textRenderer, "Resume", w, h, mx, my);
            }
        } catch (Exception ignored) {}
    }

    public void onScreenRender(Object ctx, Object screen, int mouseX, int mouseY, float tickDelta) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            int w = getGuiWidth();
            int h = getGuiHeight();
            int mx = (int) getMouseX();
            int my = (int) getMouseY();
            boolean ctrl = ReflectionHelper.isMcpControlMode();
            boolean hasWorld = mc.world != null;
            if (ctrl) {
                McpOverlayLogic.renderResumeButton(wrapRenderer((DrawContext) ctx), mc.textRenderer, "Resume", w, h, mx, my);
            } else if (hasWorld && screen != null) {
                McpOverlayLogic.renderTransferButton(wrapRenderer((DrawContext) ctx), mc.textRenderer, "", w, h, mx, my);
            }
        } catch (Exception e) {
        }
    }

    public boolean onMouseClicked(double mx, double my, int button) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            int imx = (int) mx;
            int imy = (int) my;
            if (ReflectionHelper.isMcpControlMode()) {
                String result = ReflectionHelper.handleOverlayClick(imx, imy, mc);
                return !"blocked".equals(result) && !"cooldown".equals(result) && !"not_in_control_mode".equals(result);
            } else if (mc.world != null) {
                String result = ReflectionHelper.handleTransferOverlayClick(imx, imy, mc);
                return "transfer_to_mcp".equals(result);
            }
        } catch (Exception ignored) {}
        return false;
    }
}

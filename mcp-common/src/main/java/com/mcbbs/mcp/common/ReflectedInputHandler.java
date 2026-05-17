package com.mcbbs.mcp.common;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.imageio.ImageIO;

public class ReflectedInputHandler extends McpMessageHandler implements McpProtocol.MinecraftInput {

    private final RenderThreadExecutor executor;

    public ReflectedInputHandler(RenderThreadExecutor executor) {
        super();
        this.executor = executor;
        this.minecraftInput = this;
    }

    public static void executeOnRenderThread(Runnable task) {
        try {
            Object mc = ReflectionHelper.getMinecraftInstance();
            try {
                Method execute = mc.getClass().getMethod("execute", Runnable.class);
                execute.invoke(mc, task);
            } catch (NoSuchMethodException e) {
                mc.getClass().getMethod("addScheduledTask", Runnable.class).invoke(mc, task);
            }
        } catch (Exception e) {
            System.err.println("[ReflectedInputHandler] Failed to schedule: " + e.getMessage());
        }
    }

    private Object mc() { return ReflectionHelper.getMinecraftInstance(); }

    private long getWindowHandle() {
        Object mc = mc();
        if (ReflectionHelper.hasWindow(mc)) return ReflectionHelper.getWindowHandle(mc);
        return 0;
    }

    private Object getWindow(Object mc) {
        try {
            try { return mc.getClass().getMethod("getWindow").invoke(mc); }
            catch (NoSuchMethodException ignored) {}
            try { return mc.getClass().getMethod("getMainWindow").invoke(mc); }
            catch (NoSuchMethodException ignored) {}
            for (Field f : mc.getClass().getDeclaredFields()) {
                String tn = f.getType().getSimpleName();
                if (tn.contains("Window") || tn.contains("MainWindow")) {
                    try { f.setAccessible(true); return f.get(mc); } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private int getWidth() {
        Object mc = mc();
        Object window = getWindow(mc);
        if (window != null) {
            for (String fname : new String[]{"width", "cachedWidth", "framebufferWidth",
                    "field_198122_i", "field_198131_r", "field_198120_g", "field_198127_n"}) {
                try {
                    Field f = window.getClass().getDeclaredField(fname);
                    f.setAccessible(true);
                    int v = f.getInt(window);
                    if (v > 0) return v;
                } catch (Exception ignored) {}
            }
            try {
                return ((Number) window.getClass().getMethod("getWidth").invoke(window)).intValue();
            } catch (Exception ignored) {}
        }
        return ReflectionHelper.getDisplayWidth(mc);
    }

    private int getHeight() {
        Object mc = mc();
        Object window = getWindow(mc);
        if (window != null) {
            for (String fname : new String[]{"height", "cachedHeight", "framebufferHeight",
                    "field_198123_j", "field_198132_s", "field_198121_h", "field_198128_o"}) {
                try {
                    Field f = window.getClass().getDeclaredField(fname);
                    f.setAccessible(true);
                    int v = f.getInt(window);
                    if (v > 0) return v;
                } catch (Exception ignored) {}
            }
            try {
                return ((Number) window.getClass().getMethod("getHeight").invoke(window)).intValue();
            } catch (Exception ignored) {}
        }
        return ReflectionHelper.getDisplayHeight(mc);
    }

    @Override
    public void click(int x, int y, String button) {
        executor.executeOnRenderThread(() -> {
            try {
                long h = getWindowHandle();
                if (h == 0) return;
                int b = "right".equals(button) ? 1 : "middle".equals(button) ? 2 : 0;
                ReflectionHelper.setCursorPos(h, x, y);
                Thread.sleep(50);
                ReflectionHelper.sendMouseButton(h, b, 1);
                Thread.sleep(50);
                ReflectionHelper.sendMouseButton(h, b, 0);
            } catch (Exception e) { ReflectionHelper.dbg("click err: " + e.getMessage()); }
        });
    }

    @Override
    public void pressKey(String key, float holdSeconds) {
        executor.executeOnRenderThread(() -> {
            try {
                long h = getWindowHandle();
                if (h == 0) return;
                int c = GlfwKeys.keyCode(key);
                if (c < 0) return;
                ReflectionHelper.sendKey(h, c, 1);
                Thread.sleep(holdSeconds > 0 ? (long)(holdSeconds * 1000) : 30);
                ReflectionHelper.sendKey(h, c, 0);
            } catch (Exception e) { System.err.println("[Input] Key: " + e.getMessage()); }
        });
    }

    @Override
    public void typeText(String text) {
        executor.executeOnRenderThread(() -> {
            try {
                long h = getWindowHandle();
                if (h == 0) return;
                for (char ch : text.toCharArray()) {
                    int code = GlfwKeys.charCode(ch);
                    if (code > 0) {
                        boolean shift = Character.isUpperCase(ch) || isShiftChar(ch);
                        if (shift) { ReflectionHelper.sendKey(h, GlfwKeys.keyCode("shift"), 1); Thread.sleep(5); }
                        ReflectionHelper.sendKey(h, code, 1); Thread.sleep(25);
                        ReflectionHelper.sendKey(h, code, 0);
                        if (shift) { Thread.sleep(5); ReflectionHelper.sendKey(h, GlfwKeys.keyCode("shift"), 0); }
                    }
                    Thread.sleep(20);
                }
            } catch (Exception e) { System.err.println("[Input] Type: " + e.getMessage()); }
        });
    }

    @Override
    public void scroll(int clicks) {
        executor.executeOnRenderThread(() -> {
            try { ReflectionHelper.sendScroll(getWindowHandle(), clicks * 1.0); }
            catch (Exception ignored) {}
        });
    }

    @Override
    public void hotkey(String[] keys) {
        executor.executeOnRenderThread(() -> {
            try {
                long h = getWindowHandle();
                if (h == 0) return;
                int[] codes = new int[keys.length];
                for (int i = 0; i < keys.length; i++) codes[i] = GlfwKeys.keyCode(keys[i]);
                for (int c : codes) { ReflectionHelper.sendKey(h, c, 1); Thread.sleep(5); }
                Thread.sleep(80);
                for (int i = codes.length - 1; i >= 0; i--) ReflectionHelper.sendKey(h, codes[i], 0);
            } catch (Exception e) { System.err.println("[Input] Hotkey: " + e.getMessage()); }
        });
    }

    @Override
    public byte[] screenshot() {
        try {
            Object m = mc();
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return createDummyScreenshot("dims=" + w + "x" + h);
            }
            byte[] result = ReflectionHelper.takeScreenshot(m, w, h);
            if (result == null) return createDummyScreenshot("null");
            return result;
        } catch (Exception e) {
            try { return createDummyScreenshot("err:" + e.getMessage()); } catch (Exception e2) { return null; }
        }
    }

    private static byte[] createDummyScreenshot(String msg) throws Exception {
        BufferedImage img = new BufferedImage(1600, 200, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics g = img.getGraphics();
        g.setColor(java.awt.Color.RED);
        g.fillRect(0, 0, 1600, 200);
        g.setColor(java.awt.Color.WHITE);
        g.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 11));
        int y = 15;
        int maxLen = Math.min(msg.length(), 1600);
        for (int i = 0; i < maxLen; i += 140) {
            String line = msg.substring(i, Math.min(i + 140, msg.length()));
            g.drawString(line, 5, y);
            y += 14;
            if (y > 195) break;
        }
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    @Override
    public String executeCommand(String command) { return ReflectionHelper.sendCommand(mc(), command); }

    @Override
    public String getPlayerInfo() { return ReflectionHelper.getPlayerInfo(mc()); }

    @Override
    public String getWorldInfo() { return ReflectionHelper.getWorldInfo(mc()); }

    private static final String SHIFT_CHARS = "!@#$%^&*()_+{}|:<>?~";

    private static boolean isShiftChar(char ch) { return SHIFT_CHARS.indexOf(ch) >= 0; }
}

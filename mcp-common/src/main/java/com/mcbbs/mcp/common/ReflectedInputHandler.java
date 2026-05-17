package com.mcbbs.mcp.common;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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

    private int getWidth() {
        Object mc = mc();
        if (ReflectionHelper.hasWindow(mc)) {
            try {
                Object window = mc.getClass().getMethod("getWindow").invoke(mc);
                Field w = window.getClass().getDeclaredField("width");
                w.setAccessible(true);
                return w.getInt(window);
            } catch (Exception e) { return ReflectionHelper.getDisplayWidth(mc); }
        }
        return ReflectionHelper.getDisplayWidth(mc);
    }

    private int getHeight() {
        Object mc = mc();
        if (ReflectionHelper.hasWindow(mc)) {
            try {
                Object window = mc.getClass().getMethod("getWindow").invoke(mc);
                Field h = window.getClass().getDeclaredField("height");
                h.setAccessible(true);
                return h.getInt(window);
            } catch (Exception e) { return ReflectionHelper.getDisplayHeight(mc); }
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
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) throw new RuntimeException("bad dims w=" + w + " h=" + h);
            byte[] result = ReflectionHelper.takeScreenshot(mc(), w, h);
            if (result == null) throw new RuntimeException("takeScreenshot returned null");
            return result;
        } catch (Exception e) { throw new RuntimeException("screenshot failed", e); }
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

package xyz.langyo.minecraft.mcp.common;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class ReflectionHelper {

    private static String[] mainThreadResult = new String[1];
    private static CountDownLatch mainThreadLatch;

    private ReflectionHelper() {}

    public static String runOnMainThread(Object mc, java.util.function.Supplier<String> task) {
        try {
            mainThreadResult[0] = null;
            mainThreadLatch = new CountDownLatch(1);
            Runnable wrapper = () -> {
                try { mainThreadResult[0] = task.get(); } catch (Exception e) { mainThreadResult[0] = "{\"error\":\"" + e.getMessage() + "\"}"; }
                if (mainThreadLatch != null) mainThreadLatch.countDown();
            };
            Method execMethod = null;
            for (Method m : mc.getClass().getMethods()) {
                if (m.getName().equals("execute") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == Runnable.class) {
                    execMethod = m; break;
                }
            }
            if (execMethod != null) {
                execMethod.invoke(mc, wrapper);
            } else {
                dbg("no execute() found, running inline");
                wrapper.run();
            }
            if (mainThreadLatch.await(5, TimeUnit.SECONDS)) {
                return mainThreadResult[0] != null ? mainThreadResult[0] : "{\"error\":\"timeout\"}";
            }
            return "{\"error\":\"main thread timeout\"}";
        } catch (Exception e) {
            dbg("runOnMainThread err: " + e.getMessage());
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static Object getMinecraftInstance() {
        return ReflectionCache.getMinecraftInstance();
    }

    public static void setCachedWindowHandle(long handle) {
        WindowHelper.setCachedWindowHandle(handle);
    }

    public static long getWindowHandle(Object mc) {
        return WindowHelper.getWindowHandle(mc);
    }

    public static boolean hasWindow(Object mc) {
        return WindowHelper.hasWindow(mc);
    }

    public static int getDisplayWidth(Object mc) {
        return WindowHelper.getDisplayWidth(mc);
    }

    public static int getDisplayHeight(Object mc) {
        return WindowHelper.getDisplayHeight(mc);
    }

    public static int getGlfwWindowSize(Object mc, boolean isWidth) {
        return WindowHelper.getGlfwWindowSize(mc, isWidth);
    }

    public static int getLwjgl2DisplaySize(boolean isWidth) {
        return WindowHelper.getLwjgl2DisplaySize(isWidth);
    }

    public static String getPlayerInfo(Object mc) {
        return PlayerWorldHelper.getPlayerInfo(mc);
    }

    public static String guiClick(Object mc, int x, int y, int button) {
        return ScreenInteractionHelper.guiClick(mc, x, y, button);
    }

    public static String getScreenButtons(Object mc) {
        return ScreenInteractionHelper.getScreenButtons(mc);
    }

    public static String clickButtonById(Object mc, int buttonId) {
        return ScreenInteractionHelper.clickButtonById(mc, buttonId);
    }

    public static String enumerateWidgets(Object mc) {
        return ScreenInteractionHelper.enumerateWidgets(mc);
    }

    public static String clickButtonByIndex(Object mc, int index) {
        return ScreenInteractionHelper.clickButtonByIndex(mc, index);
    }

    public static String callScreenMethod(Object mc, String methodName) {
        return ScreenInteractionHelper.callScreenMethod(mc, methodName);
    }

    public static String guiKeyPress(Object mc, int keyCode, int scanCode, int action, int modifiers) {
        return ScreenInteractionHelper.guiKeyPress(mc, keyCode, scanCode, action, modifiers);
    }

    public static String guiCharType(Object mc, char ch, int modifiers) {
        return ScreenInteractionHelper.guiCharType(mc, ch, modifiers);
    }

    public static String getWorldInfo(Object mc) {
        return PlayerWorldHelper.getWorldInfo(mc);
    }

    public static String getDimensionId(Object player) throws Exception {
        return PlayerWorldHelper.getDimensionId(player);
    }

    public static String getDifficultyKey(Object level) throws Exception {
        return PlayerWorldHelper.getDifficultyKey(level);
    }

    public static String getGameType(Object mc) throws Exception {
        return PlayerWorldHelper.getGameType(mc);
    }

    public static String sendCommand(Object mc, String cmd) {
        return PlayerWorldHelper.sendCommand(mc, cmd);
    }

    public static byte[] takeScreenshot(Object mc, int width, int height) {
        return ScreenshotHelper.takeScreenshot(mc, width, height);
    }

    public static void sendKey(long handle, int key, int action) {
        InputInjectionHelper.sendKey(handle, key, action);
    }

    public static Object getMouseHandler(Object mc) {
        return ReflectionCache.getMouseHandler(mc);
    }

    public static void sendMouseButton(long handle, int button, int action) {
        InputInjectionHelper.sendMouseButton(handle, button, action);
    }

    public static void sendScroll(long handle, double scrollY) {
        InputInjectionHelper.sendScroll(handle, scrollY);
    }

    public static void sendMouseMoveInternal(long handle, double x, double y) {
        InputInjectionHelper.sendMouseMoveInternal(handle, x, y);
    }

    public static String directScroll(Object mc, double mouseX, double mouseY, double delta) {
        return ScreenInteractionHelper.directScroll(mc, mouseX, mouseY, delta);
    }

    public static String selectListItem(Object mc, int targetIndex) {
        return ScreenInteractionHelper.selectListItem(mc, targetIndex);
    }

    public static void sendMouseDrag(long handle, int x1, int y1, int x2, int y2, int button, int steps) {
        InputInjectionHelper.sendMouseDrag(handle, x1, y1, x2, y2, button, steps);
    }

    public static void setCursorPos(long handle, double x, double y) {
        InputInjectionHelper.setCursorPos(handle, x, y);
    }

    public static boolean isLwjgl3() { return ReflectionCache.isLwjgl3(); }

    public static String debugFields(Object mc) {
        return PlayerWorldHelper.debugFields(mc);
    }

    public static String pasteText(Object mc, String text) {
        return ScreenInteractionHelper.pasteText(mc, text);
    }

    public static String setPlayerRotation(Object mc, float yaw, float pitch) {
        return PlayerWorldHelper.setPlayerRotation(mc, yaw, pitch);
    }

    public static String deltaPlayerRotation(Object mc, float deltaYaw, float deltaPitch) {
        return PlayerWorldHelper.deltaPlayerRotation(mc, deltaYaw, deltaPitch);
    }

    public static String doRightClick(Object mc) {
        return PlayerWorldHelper.doRightClick(mc);
    }

    public static String doUseItem(Object mc) {
        return PlayerWorldHelper.doUseItem(mc);
    }

    public static String doPlaceBlock(Object mc) {
        return PlayerWorldHelper.doPlaceBlock(mc);
    }

    public static String openChatScreen(Object mc) {
        return PlayerWorldHelper.openChatScreen(mc);
    }

    public static String closeScreen(Object mc) {
        return PlayerWorldHelper.closeScreen(mc);
    }

    public static String switchTab(Object mc, int tabIndex) {
        return PlayerWorldHelper.switchTab(mc, tabIndex);
    }

    public static String releaseMouse(Object mc) {
        return ControlModeHelper.releaseMouse(mc);
    }

    public static String setGameMode(Object mc, String gameMode) {
        return PlayerWorldHelper.setGameMode(mc, gameMode);
    }

    public static String openPauseMenu(Object mc) {
        return PlayerWorldHelper.openPauseMenu(mc);
    }

    public static void setEventLogger(java.util.function.Consumer<String[]> logger) { ControlModeHelper.setEventLogger(logger); }

    public static boolean shouldSuppressInput() {
        return ControlModeHelper.shouldSuppressInput();
    }

    public static String enterMcpControlMode(Object mc) {
        return ControlModeHelper.enterMcpControlMode(mc);
    }

    public static String exitMcpControlMode(Object mc) {
        return ControlModeHelper.exitMcpControlMode(mc);
    }

    public static boolean isMcpControlMode() { return ControlModeHelper.isMcpControlMode(); }

    public static void setOverlayButtonBounds(int rx, int ry, int rw, int rh, int mx, int my, int mw, int mh) {
        ControlModeHelper.setOverlayButtonBounds(rx, ry, rw, rh, mx, my, mw, mh);
    }

    public static void setTransferButtonBounds(int x, int y, int w, int h) {
        ControlModeHelper.setTransferButtonBounds(x, y, w, h);
    }

    public static String handleTransferOverlayClick(int guiX, int guiY, Object mc) {
        return ControlModeHelper.handleTransferOverlayClick(guiX, guiY, mc);
    }

    public static String handleOverlayClick(int guiX, int guiY, Object mc) {
        return ControlModeHelper.handleOverlayClick(guiX, guiY, mc);
    }

    public static boolean isScreenshotInProgress() {
        return ScreenshotHelper.isScreenshotInProgress();
    }

    public static String getMcpControlPauseTransferTranslationKey() { return ControlModeHelper.getMcpControlPauseTransferTranslationKey(); }

    public static void cacheFrameFromRenderThread(Object mc) {
        ScreenshotHelper.cacheFrameFromRenderThread(mc);
    }

    public static boolean isMouseReleaseActive() { return ControlModeHelper.isMouseReleaseActive(); }

    public static void tickMouseRelease(Object mc) {
    }

    public static void tickMcpControlMode(Object mc) {
        ControlModeHelper.tickMcpControlMode(mc);
    }

    public static void setVideoCaptureActive(boolean v) { ScreenshotHelper.setVideoCaptureActive(v); }
    public static boolean isVideoCaptureActive() { return ScreenshotHelper.isVideoCaptureActive(); }

    public static byte[] captureFrameJpeg(Object mc) {
        return ScreenshotHelper.captureFrameJpeg(mc);
    }

    public static void tickVideoCapture(Object mc) {
        ScreenshotHelper.tickVideoCapture(mc);
    }

    private static java.io.BufferedWriter dbgWriter;
    private static long dbgWriterOpenTime;
    private static final Map<String, Long> dbgRateLimit = new ConcurrentHashMap<>();
    private static volatile boolean dbgEnabled = true;

    static void dbg(String msg) {
        if (!dbgEnabled) return;
        try {
            long now = System.currentTimeMillis();
            if (dbgWriter == null || now - dbgWriterOpenTime > 300000) {
                if (dbgWriter != null) try { dbgWriter.close(); } catch (Exception ignored) {}
                String home = System.getProperty("user.home");
                dbgWriter = new java.io.BufferedWriter(new java.io.FileWriter(home + java.io.File.separator + "mcp_debug.log", true));
                dbgWriterOpenTime = now;
            }
            dbgWriter.write(now + " " + msg + "\n");
            dbgWriter.flush();
        } catch (Exception e) {
            try { System.err.println("[MCP-DBG] " + msg); } catch (Exception ignored2) {}
        }
    }

    static void dbgR(String msg) {
        if (!dbgEnabled) return;
        long now = System.currentTimeMillis();
        Long last = dbgRateLimit.get(msg);
        if (last != null && now - last < 5000) return;
        dbgRateLimit.put(msg, now);
        dbg(msg);
    }
}

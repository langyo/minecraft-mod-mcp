package xyz.langyo.minecraft.mcp.common;

import java.awt.Robot;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ControlModeHelper {

    private static volatile boolean mcpControlMode = false;
    private static volatile long mcpControlModeEnterTime = 0;
    private static volatile long mcpControlModeExitTime = 0;
    private static volatile java.util.function.Consumer<String[]> eventLogger = null;

    private static volatile int overlayResumeX = -999, overlayResumeY = -999, overlayResumeW, overlayResumeH;
    private static volatile int overlayMenuX = -999, overlayMenuY = -999, overlayMenuW, overlayMenuH;
    private static volatile int overlayTransferX = -999, overlayTransferY = -999, overlayTransferW, overlayTransferH;

    private static volatile boolean mouseReleaseActive = false;
    private static volatile boolean waitingForRelease = false;

    private static int GLFW_CURSOR_VAL = -1;
    private static int GLFW_CURSOR_NORMAL_VAL = -1;
    private static boolean cursorCacheInit = false;
    private static Field mouseGrabbedField = null;
    private static Field accumulatedDXField = null;
    private static Field accumulatedDYField = null;
    private static boolean mouseFieldsInit = false;
    private static boolean hadScreenOnEnter = false;

    /**
     * 鼠标共享模式（默认 true，新行为）：
     * true  = 玩家保留鼠标，控制模式下仍可自由转动视角；输入类接口照常工作。
     * false = 旧行为：MCP 独占鼠标，每 tick 强制释放光标，玩家无法转视角。
     */
    private static volatile boolean mouseSharing = true;

    /** 本次控制模式实际采用的鼠标策略；由进入/切换时确定，tick 只认它。 */
    private static volatile boolean mouseDetached = false;

    /** 单机按 ESC 是否绕过暂停、保持世界运行（默认 true）。 */
    private static volatile boolean noPause = true;

    private ControlModeHelper() {}

    public static void setEventLogger(java.util.function.Consumer<String[]> logger) { eventLogger = logger; }

    private static void logModEvent(String method, String detail) {
        java.util.function.Consumer<String[]> l = eventLogger;
        if (l != null) try { l.accept(new String[]{method, detail}); } catch (Exception ignored) {}
    }

    public static boolean isMcpControlMode() { return mcpControlMode; }

    public static boolean isMouseSharing() { return mouseSharing; }

    public static boolean isMouseDetached() { return mouseDetached; }

    public static boolean isNoPause() { return noPause; }

    public static void setNoPause(boolean enabled) {
        noPause = enabled;
        if (!enabled) PauseGuardHelper.reset();
        logModEvent("set_no_pause", enabled ? "world keeps running on ESC" : "vanilla pause restored");
    }

    /** 切换控制模式默认使用的鼠标策略；若当前正处于控制模式，立即生效。 */
    public static String setMouseSharing(Object mc, boolean shared) {
        mouseSharing = shared;
        if (mcpControlMode) {
            mouseDetached = !shared;
            mouseReleaseActive = !shared;
            try {
                if (shared) grabMouseIfPlaying(mc);
                else forceCursorAndReleaseMouse(mc);
            } catch (Exception e) {
                return JsonHelper.error(e.getMessage());
            }
        }
        logModEvent("set_mouse_sharing", shared ? "shared" : "detached");
        return JsonHelper.builder()
                .put("mouse_sharing", shared)
                .put("player_can_look", shared)
                .build();
    }

    public static boolean isWaitingForRelease() { return waitingForRelease; }

    public static void clearWaitingForRelease() { waitingForRelease = false; }

    public static boolean shouldSuppressInput() {
        if (!mcpControlMode && mcpControlModeExitTime > 0) {
            return System.currentTimeMillis() - mcpControlModeExitTime < 200;
        }
        return false;
    }

    public static String enterMcpControlMode(Object mc) {
        return enterMcpControlMode(mc, !mouseSharing);
    }

    /**
     * 进入 MCP 控制模式。
     *
     * @param detachMouse true 使用旧行为（释放光标、玩家无法转视角）；
     *                    false 使用共享鼠标（玩家照常转视角，AI 仍可操作界面与视角）。
     */
    public static String enterMcpControlMode(Object mc, boolean detachMouse) {
        ReflectionCache.initClassCache();
        try {
            mcpControlMode = true;
            mcpControlModeEnterTime = System.currentTimeMillis();
            try {
                Object s = ReflectionCache.getCurrentScreen(mc);
                hadScreenOnEnter = (s != null);
            } catch (Exception ignored) {}
            mouseDetached = detachMouse;
            mouseReleaseActive = detachMouse;
            if (detachMouse) {
                forceCursorAndReleaseMouse(mc);
            } else {
                grabMouseIfPlaying(mc);
            }
            logModEvent("enter_control_mode", detachMouse ? "MCP took control (detached mouse)" : "MCP took control (shared mouse)");
            ReflectionHelper.dbg("enterMcpControlMode: detachMouse=" + detachMouse);
            return JsonHelper.builder()
                    .put("control_mode", true)
                    .put("platform", "internal")
                    .put("mouse", detachMouse ? "detached" : "shared")
                    .put("player_can_look", !detachMouse)
                    .put("hook", false)
                    .build();
        } catch (Exception e) { return JsonHelper.error(e.getMessage()); }
    }

    public static String exitMcpControlMode(Object mc) {
        try {
            mcpControlMode = false;
            mcpControlModeExitTime = System.currentTimeMillis();
            logModEvent("exit_control_mode", "Manual control restored");
            mouseReleaseActive = false;
            mouseDetached = false;
            waitingForRelease = true;
            if (ReflectionCache.LWJGL3) {
                try {
                    Object mh = ReflectionCache.getMouseHandler(mc);
                    if (mh != null) {
                        if (McBridge.isMinecraft(mc)) {
                            McBridge.setMouseGrabbed(mc, true);
                        } else {
                            initMouseFields(mh);
                            for (Method m : mh.getClass().getMethods()) {
                                String n = m.getName();
                                if (m.getParameterCount() == 0 && (n.equals("lockCursor") || n.equals("method_1611") || n.equals("func_224798_b"))) {
                                    m.setAccessible(true);
                                    m.invoke(mh);
                                    break;
                                }
                            }
                            if (mouseGrabbedField != null) {
                                mouseGrabbedField.setBoolean(mh, true);
                            }
                        }
                    }
                    long handle = WindowHelper.getWindowHandle(mc);
                    if (handle != 0) {
                        initCursorCache();
                        int GLFW_CURSOR_DISABLED = 212995;
                        try {
                            Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
                            GLFW_CURSOR_DISABLED = glfw.getField("GLFW_CURSOR_DISABLED").getInt(null);
                        } catch (Exception ignored) {}
                        Method glfwSetInputMode = ReflectionCache.getGlfwSetInputModeMethod();
                        if (glfwSetInputMode != null) {
                            glfwSetInputMode.invoke(null, handle, GLFW_CURSOR_VAL, GLFW_CURSOR_DISABLED);
                        }
                    }
                    ReflectionHelper.dbg("exitMcpControlMode: locked cursor via lockCursor + GLFW_CURSOR_DISABLED");
                } catch (Exception ce) {
                    ReflectionHelper.dbg("exitMcpControlMode: failed: " + ce.getMessage());
                }
            } else {
                try {
                    Class<?> mouseClass = Class.forName("org.lwjgl.input.Mouse");
                    mouseClass.getMethod("setGrabbed", boolean.class).invoke(null, true);
                    ReflectionHelper.dbg("exitMcpControlMode LWJGL2: cursor grabbed");
                } catch (Exception e) {
                    ReflectionHelper.dbg("exitMcpControlMode LWJGL2: " + e.getMessage());
                }
            }
            ReflectionHelper.dbg("exitMcpControlMode: OFF");
            return JsonHelper.kv("control_mode", false);
        } catch (Exception e) { return JsonHelper.error(e.getMessage()); }
    }

    public static void setOverlayButtonBounds(int rx, int ry, int rw, int rh, int mx, int my, int mw, int mh) {
        overlayResumeX = rx; overlayResumeY = ry; overlayResumeW = rw; overlayResumeH = rh;
        overlayMenuX = mx; overlayMenuY = my; overlayMenuW = mw; overlayMenuH = mh;
    }

    public static void setTransferButtonBounds(int x, int y, int w, int h) {
        overlayTransferX = x; overlayTransferY = y; overlayTransferW = w; overlayTransferH = h;
    }

    public static String handleTransferOverlayClick(int guiX, int guiY, Object mc) {
        if (mcpControlMode) return "already_in_control_mode";
        if (System.currentTimeMillis() - mcpControlModeExitTime < 500) return "cooldown";
        boolean hit = guiX >= overlayTransferX && guiX <= overlayTransferX + overlayTransferW
                   && guiY >= overlayTransferY && guiY <= overlayTransferY + overlayTransferH;
        if (hit) {
            enterMcpControlMode(mc);
            try {
                Object screen = ReflectionCache.getCurrentScreen(mc);
                if (screen != null) {
                    String cn = screen.getClass().getName().toLowerCase();
                    if (cn.contains("ingamemenu") || cn.contains("pausemenu") || cn.contains("pausescreen")
                            || cn.contains("gamemenu") || cn.contains("options")) {
                        McBridge.setScreen(mc, null);
                    }
                }
            } catch (Exception e) {
                System.err.println("[MCP] handleTransferOverlayClick closeScreen failed: " + e.getMessage());
            }
            return "transfer_to_mcp";
        }
        return "missed";
    }

    public static String handleOverlayClick(int guiX, int guiY, Object mc) {
        if (!mcpControlMode) return "not_in_control_mode";
        if (System.currentTimeMillis() - mcpControlModeEnterTime < 300) return "cooldown";
        boolean hitResume = guiX >= overlayResumeX && guiX <= overlayResumeX + overlayResumeW
                         && guiY >= overlayResumeY && guiY <= overlayResumeY + overlayResumeH;
        boolean hitMenu = guiX >= overlayMenuX && guiX <= overlayMenuX + overlayMenuW
                       && guiY >= overlayMenuY && guiY <= overlayMenuY + overlayMenuH;
        if (hitResume) {
            exitMcpControlMode(mc);
            return "resume_manual";
        }
        if (hitMenu) {
            exitMcpControlMode(mc);
            ReflectionHelper.openPauseMenu(mc);
            return "system_menu";
        }
        return "blocked";
    }

    public static boolean isMouseReleaseActive() { return mouseReleaseActive; }

    public static void tickMcpControlMode(Object mc) {
        if (!mcpControlMode) return;
        // 共享鼠标模式：不碰光标、不清零鼠标增量，玩家可以正常用鼠标转动视角。
        if (!mouseDetached) return;
        try {
            if (ReflectionCache.LWJGL3) {
                forceCursorAndReleaseMouse(mc);
            }
            Object mouseHandler = ReflectionCache.getMouseHandler(mc);
            if (mouseHandler != null) {
                if (McBridge.isMinecraft(mc)) {
                    if (McBridge.isMouseGrabbed(mc)) McBridge.setMouseGrabbed(mc, false);
                } else {
                    initMouseFields(mouseHandler);
                    if (mouseGrabbedField != null && mouseGrabbedField.getBoolean(mouseHandler)) {
                        mouseGrabbedField.setBoolean(mouseHandler, false);
                    }
                }
                if (accumulatedDXField != null) accumulatedDXField.setDouble(mouseHandler, 0.0);
                if (accumulatedDYField != null) accumulatedDYField.setDouble(mouseHandler, 0.0);
            }
        } catch (Exception e) {
            ReflectionHelper.dbg("tickMcpControlMode: " + e.getMessage());
        }
    }

    public static String releaseMouse(Object mc) {
        ReflectionCache.initClassCache();
        try {
            long handle = WindowHelper.getWindowHandle(mc);
            if (handle == 0 || !ReflectionCache.LWJGL3) return JsonHelper.error("no window handle");
            Method glfwSetInputMode = ReflectionCache.getGlfwSetInputModeMethod();
            if (glfwSetInputMode != null) {
                glfwSetInputMode.invoke(null, handle, ReflectionCache.getGlfwCursor(), ReflectionCache.getGlfwCursorNormal());
            } else {
                Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
                glfw.getMethod("glfwSetInputMode", long.class, int.class, int.class)
                    .invoke(null, handle, glfw.getField("GLFW_CURSOR").getInt(null), glfw.getField("GLFW_CURSOR_NORMAL").getInt(null));
            }
            Object mouseHandler = ReflectionCache.getMouseHandler(mc);
            if (McBridge.isMinecraft(mc)) {
                McBridge.setMouseGrabbed(mc, false);
            } else if (mouseHandler != null) {
                for (Field f : ReflectionCache.getAllFields(mouseHandler.getClass())) {
                    String fn = f.getName().toLowerCase();
                    if (fn.contains("grabbed") && f.getType() == boolean.class) {
                        f.setAccessible(true);
                        f.setBoolean(mouseHandler, false);
                    }
                }
            }
            mouseReleaseActive = true;
            return JsonHelper.builder().put("mouse_released", true).put("continuous", true).build();
        } catch (Exception e) {
            return JsonHelper.error(e.getMessage());
        }
    }

    /**
     * 共享鼠标模式进入控制模式时调用：只要当前没有打开界面、也确实在世界里，
     * 就把光标重新锁定，交给玩家用鼠标转视角。有界面时界面自己会释放光标，
     * AI 的绝对坐标点击不受影响。
     */
    static void grabMouseIfPlaying(Object mc) {
        try {
            Object screen = ReflectionCache.getCurrentScreen(mc);
            if (screen != null) return;
            if (McBridge.getLevel(mc) == null) return;
            McBridge.setMouseGrabbed(mc, true);
        } catch (Throwable ignored) {}
    }

    static void forceCursorAndReleaseMouse(Object mc) throws Exception {
        if (ReflectionCache.LWJGL3) {
            long handle = WindowHelper.getWindowHandle(mc);
            if (handle == 0) return;
            initCursorCache();
            Method glfwSetInputMode = ReflectionCache.getGlfwSetInputModeMethod();
            if (glfwSetInputMode != null) {
                glfwSetInputMode.invoke(null, handle, GLFW_CURSOR_VAL, GLFW_CURSOR_NORMAL_VAL);
            }
        } else {
            try {
                Class<?> mouseClass = Class.forName("org.lwjgl.input.Mouse");
                Method setGrabbed = mouseClass.getMethod("setGrabbed", boolean.class);
                setGrabbed.invoke(null, false);
            } catch (Exception e) {
                ReflectionHelper.dbg("forceCursor LWJGL2: " + e.getMessage());
            }
        }
        Object mouseHandler = ReflectionCache.getMouseHandler(mc);
        if (McBridge.isMinecraft(mc)) {
            McBridge.setMouseGrabbed(mc, false);
        }
        if (mouseHandler != null) {
            initMouseFields(mouseHandler);
            if (!McBridge.isMinecraft(mc) && mouseGrabbedField != null) mouseGrabbedField.setBoolean(mouseHandler, false);
            if (accumulatedDXField != null) accumulatedDXField.setDouble(mouseHandler, 0.0);
            if (accumulatedDYField != null) accumulatedDYField.setDouble(mouseHandler, 0.0);
        }
    }

    private static void initCursorCache() throws Exception {
        if (cursorCacheInit) return;
        ReflectionCache.initClassCache();
        GLFW_CURSOR_VAL = ReflectionCache.getGlfwCursor();
        GLFW_CURSOR_NORMAL_VAL = ReflectionCache.getGlfwCursorNormal();
        cursorCacheInit = true;
    }

    private static void initMouseFields(Object mouseHandler) throws Exception {
        if (mouseFieldsInit) return;
        mouseFieldsInit = true;
        List<Field> booleanFields = new ArrayList<>();
        for (Field f : ReflectionCache.getAllFields(mouseHandler.getClass())) {
            String fn = f.getName().toLowerCase();
            if (f.getType() == boolean.class && (fn.contains("grabbed") || fn.contains("cursorlocked"))) {
                f.setAccessible(true);
                mouseGrabbedField = f;
                ReflectionHelper.dbg("cursor: found mouseGrabbed=" + f.getName());
            }
            if (f.getType() == double.class && (fn.contains("accumulateddx") || fn.contains("cursordeltax"))) {
                f.setAccessible(true);
                accumulatedDXField = f;
                ReflectionHelper.dbg("cursor: found accDX=" + f.getName());
            }
            if (f.getType() == double.class && (fn.contains("accumulateddy") || fn.contains("cursordeltay"))) {
                f.setAccessible(true);
                accumulatedDYField = f;
                ReflectionHelper.dbg("cursor: found accDY=" + f.getName());
            }
            if (f.getType() == boolean.class && fn.contains("cursor")) {
                booleanFields.add(f);
            }
        }
        if (mouseGrabbedField == null) {
            for (Field f : ReflectionCache.getAllFields(mouseHandler.getClass())) {
                if (f.getType() == boolean.class) {
                    f.setAccessible(true);
                    booleanFields.add(f);
                    ReflectionHelper.dbg("cursor: candidate boolean field: " + f.getName());
                }
            }
            if (booleanFields.size() == 1) {
                mouseGrabbedField = booleanFields.get(0);
                ReflectionHelper.dbg("cursor: using sole boolean field as mouseGrabbed: " + mouseGrabbedField.getName());
            }
        }
        if (mouseGrabbedField == null) {
            ReflectionHelper.dbg("cursor: WARNING - mouseGrabbedField not found, camera rotation suppression will not work");
        }
    }

    public static String getMcpControlPauseTransferTranslationKey() { return "mcpmod.control.transfer"; }
}

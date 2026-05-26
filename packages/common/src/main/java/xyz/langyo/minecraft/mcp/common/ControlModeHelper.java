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

    private static int GLFW_CURSOR_VAL = -1;
    private static int GLFW_CURSOR_NORMAL_VAL = -1;
    private static boolean cursorCacheInit = false;
    private static Field mouseGrabbedField = null;
    private static Field accumulatedDXField = null;
    private static Field accumulatedDYField = null;
    private static boolean mouseFieldsInit = false;

    private ControlModeHelper() {}

    public static void setEventLogger(java.util.function.Consumer<String[]> logger) { eventLogger = logger; }

    private static void logModEvent(String method, String detail) {
        java.util.function.Consumer<String[]> l = eventLogger;
        if (l != null) try { l.accept(new String[]{method, detail}); } catch (Exception ignored) {}
    }

    public static boolean isMcpControlMode() { return mcpControlMode; }

    public static boolean shouldSuppressInput() {
        if (!mcpControlMode && mcpControlModeExitTime > 0) {
            return System.currentTimeMillis() - mcpControlModeExitTime < 200;
        }
        return false;
    }

    public static String enterMcpControlMode(Object mc) {
        ReflectionCache.initClassCache();
        try {
            mcpControlMode = true;
            mcpControlModeEnterTime = System.currentTimeMillis();
            mouseReleaseActive = false;
            forceCursorAndReleaseMouse(mc);
            logModEvent("enter_control_mode", "MCP took control");
            ReflectionHelper.dbg("enterMcpControlMode: cursor forced to NORMAL, no hook");
            return JsonHelper.builder().put("control_mode", true).put("platform", "internal").put("hook", false).build();
        } catch (Exception e) { return JsonHelper.error(e.getMessage()); }
    }

    public static String exitMcpControlMode(Object mc) {
        try {
            ReflectionHelper.dbg("exitMcpControlMode: CALLED FROM " + Thread.currentThread().getName() + " - " + new Exception().getStackTrace()[1]);
            mcpControlMode = false;
            mcpControlModeExitTime = System.currentTimeMillis();
            logModEvent("exit_control_mode", "Manual control restored");
            mouseReleaseActive = false;
            if (ReflectionCache.LWJGL3) {
                long glfwHandle = WindowHelper.getWindowHandle(mc);
                Method glfwSetInputMode = ReflectionCache.getGlfwSetInputModeMethod();
                if (glfwHandle != 0 && glfwSetInputMode != null) {
                    try {
                        Object screen = ReflectionCache.getCurrentScreen(mc);
                        if (screen != null) {
                            glfwSetInputMode.invoke(null, glfwHandle, ReflectionCache.getGlfwCursor(), ReflectionCache.getGlfwCursorNormal());
                            ReflectionHelper.dbg("exitMcpControlMode: screen open (" + screen.getClass().getSimpleName() + "), cursor NORMAL");
                        } else {
                            glfwSetInputMode.invoke(null, glfwHandle, ReflectionCache.getGlfwCursor(), ReflectionCache.getGlfwCursorDisabled());
                            Object mh = ReflectionCache.getMouseHandler(mc);
                            if (mh != null) initMouseFields(mh);
                            if (mouseGrabbedField != null && mh != null) {
                                mouseGrabbedField.setBoolean(mh, true);
                            }
                            ReflectionHelper.dbg("exitMcpControlMode: no screen, cursor DISABLED + mouse grabbed");
                        }
                    } catch (Exception ce) {
                        ReflectionHelper.dbg("exitMcpControlMode: glfwSetInputMode failed: " + ce.getMessage());
                    }
                }
            } else {
                try {
                    Object screen = ReflectionCache.getCurrentScreen(mc);
                    Class<?> mouseClass = Class.forName("org.lwjgl.input.Mouse");
                    if (screen != null) {
                        mouseClass.getMethod("setGrabbed", boolean.class).invoke(null, false);
                        ReflectionHelper.dbg("exitMcpControlMode LWJGL2: screen open, cursor ungrabbed");
                    } else {
                        mouseClass.getMethod("setGrabbed", boolean.class).invoke(null, true);
                        ReflectionHelper.dbg("exitMcpControlMode LWJGL2: no screen, mouse grabbed");
                    }
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
        boolean hit = guiX >= overlayTransferX && guiX <= overlayTransferX + overlayTransferW
                   && guiY >= overlayTransferY && guiY <= overlayTransferY + overlayTransferH;
        if (hit) {
            enterMcpControlMode(mc);
            return "transfer_to_mcp";
        }
        return "missed";
    }

    public static String handleOverlayClick(int guiX, int guiY, Object mc) {
        if (!mcpControlMode) return "not_in_control_mode";
        if (System.currentTimeMillis() - mcpControlModeEnterTime < 1000) return "cooldown";
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
            return "system_menu";
        }
        return "blocked";
    }

    public static boolean isMouseReleaseActive() { return mouseReleaseActive; }

    public static void tickMcpControlMode(Object mc) {
        if (!mcpControlMode) return;
        try {
            forceCursorAndReleaseMouse(mc);
            Object mouseHandler = ReflectionCache.getMouseHandler(mc);
            if (mouseHandler != null) {
                initMouseFields(mouseHandler);
                if (mouseGrabbedField != null && mouseGrabbedField.getBoolean(mouseHandler)) {
                    mouseGrabbedField.setBoolean(mouseHandler, false);
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
            if (mouseHandler != null) {
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
        if (mouseHandler != null) {
            initMouseFields(mouseHandler);
            if (mouseGrabbedField != null) mouseGrabbedField.setBoolean(mouseHandler, false);
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
            if ((fn.contains("grabbed")) && f.getType() == boolean.class) {
                f.setAccessible(true);
                mouseGrabbedField = f;
                ReflectionHelper.dbg("cursor: found mouseGrabbed=" + f.getName());
            }
            if (fn.contains("accumulateddx") && f.getType() == double.class) {
                f.setAccessible(true);
                accumulatedDXField = f;
                ReflectionHelper.dbg("cursor: found accDX=" + f.getName());
            }
            if (fn.contains("accumulateddy") && f.getType() == double.class) {
                f.setAccessible(true);
                accumulatedDYField = f;
                ReflectionHelper.dbg("cursor: found accDY=" + f.getName());
            }
            if (f.getType() == boolean.class && f.getName().toLowerCase().contains("cursor")) {
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

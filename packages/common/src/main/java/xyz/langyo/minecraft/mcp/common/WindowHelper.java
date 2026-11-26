package xyz.langyo.minecraft.mcp.common;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class WindowHelper {

    private static long cachedWindowHandle = 0;

    private WindowHelper() {}

    public static void setCachedWindowHandle(long handle) {
        if (handle != 0) cachedWindowHandle = handle;
    }

    public static long getWindowHandle(Object mc) {
        if (cachedWindowHandle != 0) return cachedWindowHandle;
        try {
            Object window = findWindowObject(mc);
            if (window == null) return 0;
            long handle = extractHandleFromWindow(window);
            if (handle != 0) { cachedWindowHandle = handle; }
            return handle;
        } catch (Exception e) {
            return 0;
        }
    }

    public static boolean hasWindow(Object mc) {
        // Presence of a getWindow method/field is not enough: the GLFW paths
        // must only run against a real, verified window object, so callers
        // without one (LWJGL2 eras, Cleanroom 1.12.2) get a 0 handle and fall
        // back to non-GLFW input (issue #76 B7).
        try { return findWindowObject(mc) != null; } catch (Exception e) { return false; }
    }

    public static int getDisplayWidth(Object mc) {
        int v = ReflectionCache.getIntFieldByNames(mc, "displayWidth", "field_71443_c", "width");
        return v > 0 ? v : 0;
    }

    public static int getDisplayHeight(Object mc) {
        int v = ReflectionCache.getIntFieldByNames(mc, "displayHeight", "field_71440_d", "height");
        return v > 0 ? v : 0;
    }

    public static int getGlfwWindowSize(Object mc, boolean isWidth) {
        if (!ReflectionCache.LWJGL3) return 0;
        try {
            // The GL reads that consume this size (glReadPixels) target the
            // context that is current on the calling thread, and
            // glfwGetCurrentContext answers exactly that by reading GLFW's
            // per-thread slot - it dereferences nothing. Take it first: a bad
            // reflected handle must never reach glfwGetFramebufferSize, which
            // reads through the pointer and takes the whole JVM down
            // natively (issue #101).
            long handle = getCurrentContextHandle();
            if (handle == 0) handle = getWindowHandle(mc);
            if (handle == 0) return 0;
            Class<?> glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
            Class<?> intBufClass = Class.forName("java.nio.IntBuffer");
            // GLFW writes through the buffer's native address, so these must
            // be direct: a heap IntBuffer reports the array base offset (0x10
            // on JDK 9+) and glfwGetFramebufferSize crashes the JVM writing
            // to it.
            java.nio.IntBuffer wBuf = directIntBuffer();
            java.nio.IntBuffer hBuf = directIntBuffer();
            try {
                glfwClass.getMethod("glfwGetFramebufferSize", long.class, intBufClass, intBufClass)
                    .invoke(null, handle, wBuf, hBuf);
            } catch (NoSuchMethodException e) {
                glfwClass.getMethod("glfwGetWindowSize", long.class, intBufClass, intBufClass)
                    .invoke(null, handle, wBuf, hBuf);
            }
            return isWidth ? wBuf.get(0) : hBuf.get(0);
        } catch (Exception e) { return 0; }
    }

    private static java.nio.IntBuffer directIntBuffer() {
        return java.nio.ByteBuffer.allocateDirect(4).order(java.nio.ByteOrder.nativeOrder()).asIntBuffer();
    }

    public static int getLwjgl2DisplaySize(boolean isWidth) {
        if (ReflectionCache.LWJGL3) return 0;
        try {
            Class<?> displayClass = Class.forName("org.lwjgl.opengl.Display");
            String method = isWidth ? "getWidth" : "getHeight";
            return (Integer) displayClass.getMethod(method).invoke(null);
        } catch (Exception e) { return 0; }
    }

    /**
     * A class may only be treated as a GLFW window holder when it is not
     * identifiable as something else that also exposes 0-arg long+int
     * accessors: WorldClient keeps a long seed plus int dimensions and
     * java.io.File a long length, and their values were previously used as
     * GLFW handles, crashing glfwSetInputMode natively on Cleanroom's
     * LWJGL3 1.12.2 where no Window object exists at all (issue #76 B7).
     * The shape fallback stays for Fabric intermediary runtimes, whose real
     * Window class is named class_1041.
     */
    static boolean isWindowHolderType(Class<?> clazz) {
        String cn = clazz.getName();
        if (cn.startsWith("com.mojang.blaze3d.") || cn.startsWith("org.lwjgl.")) return true;
        if (cn.startsWith("java.") || cn.contains("World") || cn.contains("Level")) return false;
        return clazz.getSimpleName().contains("Window") || isWindowType(clazz);
    }

    static Object findWindowObject(Object mc) throws Exception {
        for (String name : new String[]{"getWindow", "getMainWindow"}) {
            try {
                Object w = mc.getClass().getMethod(name).invoke(mc);
                if (w == null || isWindowHolderType(w.getClass())) return w;
            } catch (NoSuchMethodException ignored) {}
        }
        java.lang.reflect.Field discovered = ReflectionCache.getDiscoveredField("window");
        if (discovered != null && isWindowHolderType(discovered.getType())) {
            try { discovered.setAccessible(true); return discovered.get(mc); } catch (Exception ignored) {}
        }
        for (Field f : ReflectionCache.getAllFields(mc.getClass())) {
            f.setAccessible(true);
            String tn = f.getType().getSimpleName();
            if (tn.contains("Window") || tn.contains("MainWindow")) {
                try { return f.get(mc); } catch (Exception ignored) {}
            }
        }
        for (Field f : ReflectionCache.getAllFields(mc.getClass())) {
            f.setAccessible(true);
            Class<?> ft = f.getType();
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) && isWindowHolderType(ft)) {
                try { return f.get(mc); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static boolean isWindowType(Class<?> clazz) {
        boolean hasLongReturn = false;
        boolean hasIntReturn = false;
        for (Method m : ReflectionCache.getAllMethods(clazz)) {
            if (m.getParameterCount() == 0) {
                if (m.getReturnType() == long.class) hasLongReturn = true;
                if (m.getReturnType() == int.class) hasIntReturn = true;
            }
        }
        return hasLongReturn && hasIntReturn;
    }

    private static long extractHandleFromWindow(Object window) throws Exception {
        // Trusted names first (issue #101). Every mapping era names the
        // handle accessors: getWindow / window (Mojang names at runtime on
        // NeoForge, on Forge from 1.20.6 and on the unobfuscated 26.x Fabric
        // builds), m_85439_ / f_85349_ (Forge 1.17-1.20.4 SRG),
        // func_198092_i / field_198119_f (Forge 1.13-1.16 SRG) and
        // method_4490 / field_5187 (production Fabric).
        boolean trusted = false;
        for (String methodName : new String[]{"getWindow", "handle", "getHandle", "method_4490", "m_85439_", "func_198092_i"}) {
            try {
                Object result = window.getClass().getMethod(methodName).invoke(window);
                if (result instanceof Number) {
                    trusted = true;
                    if (((Number) result).longValue() != 0) return ((Number) result).longValue();
                }
            } catch (Exception ignored) {}
        }
        for (Field f : ReflectionCache.getAllFields(window.getClass())) {
            String fn = f.getName();
            if (f.getType() == long.class && (fn.equals("window") || fn.equals("field_5187") || fn.equals("f_85349_") || fn.equals("field_198119_f"))) {
                try { f.setAccessible(true); long val = f.getLong(window); trusted = true; if (val != 0) return val; } catch (Exception ignored) {}
            }
        }
        // A trusted accessor that exists but still reads 0 means the window
        // is not created yet: report no handle rather than fall through.
        if (trusted) return 0;
        // The shape scan is the last resort, never the first pick: it runs
        // over every 0-arg long member in unspecified order, so a mod-added
        // one (an uptime counter, a frame stamp) wins the race and is handed
        // to GLFW as a GLFWwindow*, which reads through it and takes the JVM
        // down natively (issue #101). Only instance members can carry a
        // per-window handle, and only trust what the scan digs up when the
        // value can be a pointer at all.
        for (Method m : ReflectionCache.getAllMethods(window.getClass())) {
            if (m.getParameterCount() == 0 && m.getReturnType() == long.class && !Modifier.isStatic(m.getModifiers())) {
                try { m.setAccessible(true); Object r = m.invoke(window); if (r instanceof Number && looksLikeWindowHandle(((Number)r).longValue())) return ((Number)r).longValue(); } catch (Exception ignored) {}
            }
        }
        for (Field f : ReflectionCache.getAllFields(window.getClass())) {
            if (f.getType() == long.class && !Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                long val = f.getLong(window);
                if (looksLikeWindowHandle(val)) return val;
            }
        }
        return 0;
    }

    /**
     * A long dug up by the shape scan can be anything; treat it as a window
     * pointer only when it can be one. Native heap pointers are positive
     * and in practice sit far above the first 4 GiB on a 64-bit JVM, while
     * the values picked up by mistake are small counters (0x7b1156 in
     * issue #101). A rejected handle only disables the GLFW paths for the
     * session; a wrong one crashes the game.
     */
    static boolean looksLikeWindowHandle(long value) {
        if (value <= 0) return false;
        return !"64".equals(System.getProperty("sun.arch.data.model")) || value >= (1L << 32);
    }

    /** The GLFWwindow* whose GL context is current on this thread, or 0. */
    private static long getCurrentContextHandle() {
        try {
            Object handle = Class.forName("org.lwjgl.glfw.GLFW").getMethod("glfwGetCurrentContext").invoke(null);
            return handle instanceof Number ? ((Number) handle).longValue() : 0;
        } catch (Exception e) { return 0; }
    }
}

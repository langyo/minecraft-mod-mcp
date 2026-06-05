package xyz.langyo.minecraft.mcp.common;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
        for (String name : new String[]{"getWindow", "getMainWindow"}) {
            try { mc.getClass().getMethod(name); return true; } catch (NoSuchMethodException ignored) {}
        }
        for (Field f : mc.getClass().getDeclaredFields()) {
            String tn = f.getType().getSimpleName();
            if (tn.contains("Window") || tn.contains("MainWindow")) return true;
        }
        return false;
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
            long handle = getWindowHandle(mc);
            if (handle == 0) return 0;
            Class<?> glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
            Class<?> intBufClass = Class.forName("java.nio.IntBuffer");
            Object wBuf = java.nio.IntBuffer.allocate(1);
            Object hBuf = java.nio.IntBuffer.allocate(1);
            try {
                glfwClass.getMethod("glfwGetFramebufferSize", long.class, intBufClass, intBufClass)
                    .invoke(null, handle, wBuf, hBuf);
            } catch (NoSuchMethodException e) {
                glfwClass.getMethod("glfwGetWindowSize", long.class, intBufClass, intBufClass)
                    .invoke(null, handle, wBuf, hBuf);
            }
            return isWidth ? ((java.nio.IntBuffer)wBuf).get(0) : ((java.nio.IntBuffer)hBuf).get(0);
        } catch (Exception e) { return 0; }
    }

    public static int getLwjgl2DisplaySize(boolean isWidth) {
        if (ReflectionCache.LWJGL3) return 0;
        try {
            Class<?> displayClass = Class.forName("org.lwjgl.opengl.Display");
            String method = isWidth ? "getWidth" : "getHeight";
            return (Integer) displayClass.getMethod(method).invoke(null);
        } catch (Exception e) { return 0; }
    }

    private static Object findWindowObject(Object mc) throws Exception {
        for (String name : new String[]{"getWindow", "getMainWindow"}) {
            try { return mc.getClass().getMethod(name).invoke(mc); } catch (NoSuchMethodException ignored) {}
        }
        java.lang.reflect.Field discovered = ReflectionCache.getDiscoveredField("window");
        if (discovered != null) { try { discovered.setAccessible(true); return discovered.get(mc); } catch (Exception ignored) {} }
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
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) && isWindowType(ft)) {
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
        for (Method m : ReflectionCache.getAllMethods(window.getClass())) {
            if (m.getParameterCount() == 0 && m.getReturnType() == long.class) {
                try { m.setAccessible(true); Object r = m.invoke(window); if (r instanceof Number && ((Number)r).longValue() != 0) return ((Number)r).longValue(); } catch (Exception ignored) {}
            }
        }
        for (String methodName : new String[]{"handle", "getHandle", "getWindow"}) {
            try {
                Object result = window.getClass().getMethod(methodName).invoke(window);
                if (result instanceof Number) {
                    long val = ((Number) result).longValue();
                    if (val != 0) return val;
                }
            } catch (NoSuchMethodException ignored) {}
        }
        for (Field f : ReflectionCache.getAllFields(window.getClass())) {
            if (f.getType() == long.class) {
                f.setAccessible(true);
                long val = f.getLong(window);
                if (val != 0) return val;
            }
        }
        return 0;
    }
}

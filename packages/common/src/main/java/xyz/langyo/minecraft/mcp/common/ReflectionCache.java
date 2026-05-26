package xyz.langyo.minecraft.mcp.common;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ReflectionCache {

    private static final Map<Class<?>, List<Method>> methodCache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, List<Field>> fieldCache = new ConcurrentHashMap<>();

    private static Class<?> glfwClass, gl11Class, gl30Class, displayClass, mcClass;
    private static Method mcGetInstanceMethod;
    private static Method glfwSetInputModeMethod, glfwSetCursorPosMethod;
    private static int GLFW_CURSOR, GLFW_CURSOR_NORMAL, GLFW_CURSOR_DISABLED;
    private static volatile boolean classCacheInit = false;

    static final boolean LWJGL3;

    static {
        boolean v = false;
        try { Class.forName("org.lwjgl.glfw.GLFW"); v = true; } catch (ClassNotFoundException e) {}
        LWJGL3 = v;
    }

    private ReflectionCache() {}

    static void initClassCache() {
        if (classCacheInit) return;
        try {
            try { mcClass = Class.forName("net.minecraft.client.Minecraft"); } catch (Exception ignored) {}
            if (mcClass != null) {
                try { mcGetInstanceMethod = mcClass.getMethod("getInstance"); } catch (Exception ignored) {}
                if (mcGetInstanceMethod == null) try { mcGetInstanceMethod = mcClass.getMethod("getMinecraft"); } catch (Exception ignored) {}
                if (mcGetInstanceMethod == null) {
                    for (Method m : mcClass.getMethods()) {
                        if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == mcClass && m.getParameterCount() == 0) {
                            mcGetInstanceMethod = m; break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        try { glfwClass = Class.forName("org.lwjgl.glfw.GLFW"); } catch (Exception ignored) {}
        try { gl11Class = Class.forName("org.lwjgl.opengl.GL11"); } catch (Exception ignored) {}
        try { gl30Class = Class.forName("org.lwjgl.opengl.GL30"); } catch (Exception ignored) {}
        try { displayClass = Class.forName("org.lwjgl.opengl.Display"); } catch (Exception ignored) {}
        if (glfwClass != null) {
            try {
                GLFW_CURSOR = glfwClass.getDeclaredField("GLFW_CURSOR").getInt(null);
                GLFW_CURSOR_NORMAL = glfwClass.getDeclaredField("GLFW_CURSOR_NORMAL").getInt(null);
                GLFW_CURSOR_DISABLED = glfwClass.getDeclaredField("GLFW_CURSOR_DISABLED").getInt(null);
                try { glfwSetInputModeMethod = glfwClass.getMethod("glfwSetInputMode", long.class, int.class, int.class); } catch (Exception ignored) {}
                try { glfwSetCursorPosMethod = glfwClass.getMethod("glfwSetCursorPos", long.class, double.class, double.class); } catch (Exception ignored) {}
            } catch (Exception ignored) {}
        }
        classCacheInit = true;
    }

    static boolean isLwjgl3() { return LWJGL3; }

    static Class<?> getGlfwClass() { return glfwClass; }
    static Class<?> getGl11Class() { return gl11Class; }
    static Class<?> getGl30Class() { return gl30Class; }
    static Class<?> getDisplayClass() { return displayClass; }
    static Class<?> getMcClass() { return mcClass; }
    static Method getMcGetInstanceMethod() { return mcGetInstanceMethod; }
    static Method getGlfwSetInputModeMethod() { return glfwSetInputModeMethod; }
    static Method getGlfwSetCursorPosMethod() { return glfwSetCursorPosMethod; }
    static int getGlfwCursor() { return GLFW_CURSOR; }
    static int getGlfwCursorNormal() { return GLFW_CURSOR_NORMAL; }
    static int getGlfwCursorDisabled() { return GLFW_CURSOR_DISABLED; }

    static Object getMinecraftInstance() {
        initClassCache();
        try {
            if (mcGetInstanceMethod != null) return mcGetInstanceMethod.invoke(null);
            throw new RuntimeException("No Minecraft static getter found");
        } catch (Exception e) {
            throw new RuntimeException("Failed to get Minecraft instance", e);
        }
    }

    static List<Method> getAllMethods(Class<?> clazz) {
        if (clazz.getName().startsWith("org.lwjgl.") || clazz.getName().startsWith("com.mojang.blaze3d.")) {
            return collectMethods(clazz);
        }
        return methodCache.computeIfAbsent(clazz, ReflectionCache::collectMethods);
    }

    static List<Field> getAllFields(Class<?> clazz) {
        if (clazz.getName().startsWith("org.lwjgl.") || clazz.getName().startsWith("com.mojang.blaze3d.")) {
            return collectFields(clazz);
        }
        return fieldCache.computeIfAbsent(clazz, ReflectionCache::collectFields);
    }

    private static List<Method> collectMethods(Class<?> clazz) {
        List<Method> methods = new ArrayList<>();
        Class<?> cur = clazz;
        while (cur != null && cur != Object.class) {
            for (Method m : cur.getDeclaredMethods()) methods.add(m);
            cur = cur.getSuperclass();
        }
        return methods;
    }

    private static List<Field> collectFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> cur = clazz;
        while (cur != null && cur != Object.class) {
            for (Field f : cur.getDeclaredFields()) fields.add(f);
            cur = cur.getSuperclass();
        }
        return fields;
    }

    static Object fieldOrNull(Object obj, String fieldName) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) { return null; }
    }

    static String invokeString(Object obj, String methodName) {
        try {
            Object r = obj.getClass().getMethod(methodName).invoke(obj);
            return r != null ? r.toString() : "";
        } catch (Exception e) { return ""; }
    }

    static Object invokeOrNull(Object obj, String methodName) {
        try { return obj.getClass().getMethod(methodName).invoke(obj); }
        catch (Exception e) { return null; }
    }

    static double getDouble(Object obj, String... names) throws Exception {
        for (String name : names) {
            try {
                for (Method m : getAllMethods(obj.getClass())) {
                    if (m.getName().equals(name) && m.getParameterCount() == 0 && (m.getReturnType() == double.class || m.getReturnType() == float.class)) {
                        m.setAccessible(true);
                        Object v = m.invoke(obj);
                        return ((Number) v).doubleValue();
                    }
                }
            } catch (Exception ignored) {}
            try {
                for (Field f : getAllFields(obj.getClass())) {
                    if (f.getName().equals(name) && (f.getType() == double.class || f.getType() == float.class)) {
                        f.setAccessible(true);
                        return f.getDouble(obj);
                    }
                }
            } catch (Exception ignored) {}
        }
        return 0.0;
    }

    static Object getPlayer(Object mc) throws Exception {
        try { return mc.getClass().getMethod("player").invoke(mc); } catch (NoSuchMethodException ignored) {}
        for (Field f : getAllFields(mc.getClass())) {
            String n = f.getName();
            if (n.equals("player") || n.equals("thePlayer") || n.equals("field_71439_g")) {
                try {
                    f.setAccessible(true);
                    Object p = f.get(mc);
                    if (p != null) return p;
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    static Object getLevel(Object mc) throws Exception {
        try { return mc.getClass().getMethod("level").invoke(mc); } catch (NoSuchMethodException ignored) {}
        try { return mc.getClass().getMethod("world").invoke(mc); } catch (NoSuchMethodException ignored) {}
        for (Field f : getAllFields(mc.getClass())) {
            String n = f.getName();
            if (n.equals("theWorld") || n.equals("field_71441_f") || n.equals("level") || n.equals("world")) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(mc);
                    if (v != null) return v;
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    static Object getPlayerLevel(Object player) throws Exception {
        try { return player.getClass().getMethod("level").invoke(player); } catch (NoSuchMethodException ignored) {}
        try { return player.getClass().getMethod("world").invoke(player); } catch (NoSuchMethodException ignored) {}
        for (Field f : getAllFields(player.getClass())) {
            String n = f.getName();
            if (n.equals("theWorld") || n.equals("field_70170_p") || n.equals("level") || n.equals("world")) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(player);
                    if (v != null) return v;
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    static Object getCurrentScreen(Object mc) throws Exception {
        for (Field f : getAllFields(mc.getClass())) {
            String n = f.getName();
            if (n.equals("currentScreen") || n.equals("screen") || n.equals("field_71462_r") || n.equals("field_175283_aN")) {
                try { f.setAccessible(true); return f.get(mc); } catch (Exception ignored) {}
            }
        }
        try { return mc.getClass().getMethod("screen").invoke(mc); } catch (Exception ignored) {}
        return null;
    }

    static Object castParam(Class<?> type, double value) {
        if (type == int.class) return (int) value;
        if (type == long.class) return (long) value;
        if (type == float.class) return (float) value;
        if (type == double.class) return value;
        if (type == short.class) return (short) value;
        if (type == byte.class) return (byte) value;
        return value;
    }

    static Object defaultParam(Class<?> type) {
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        if (type == double.class) return 0.0;
        if (type == boolean.class) return false;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';
        return null;
    }

    static int getIntFieldByNames(Object obj, String... names) {
        Class<?> cl = obj.getClass();
        for (String name : names) {
            try {
                Field f = cl.getDeclaredField(name);
                f.setAccessible(true);
                return f.getInt(obj);
            } catch (Exception ignored) {}
        }
        return -1;
    }

    static String srgSafe(String name) {
        switch (name) {
            case "yRot": return "146127";
            case "xRot": return "146118";
            case "yRotO": return "146128";
            case "xRotO": return "146119";
            case "yawRot": return "36076";
            case "oYRot": return "36080";
            case "oXRot": return "36081";
            default: return name;
        }
    }

    static Object findAndInvoke(Class<?> target, String name, Object[] args) throws Exception {
        for (Method m : target.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                m.setAccessible(true);
                return m.invoke(null, args);
            }
        }
        throw new NoSuchMethodException(name);
    }

    static Method findMouseButtonMethod(Class<?> mouseHandlerClass) {
        Method exactMatch = null;
        Method namedMatch = null;
        Method fallback = null;
        for (Method m : mouseHandlerClass.getDeclaredMethods()) {
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length != 4 || pt[0] != long.class || pt[1] != int.class || pt[2] != int.class || pt[3] != int.class) continue;
            if (Modifier.isStatic(m.getModifiers())) continue;
            String name = m.getName();
            if (name.equals("mouseButton")) return m;
            if (name.equals("onPress") && exactMatch == null) exactMatch = m;
            if (!name.startsWith("lambda$") && (name.contains("mouseButton") || name.contains("onMouse")) && namedMatch == null) namedMatch = m;
            if (fallback == null) fallback = m;
        }
        if (exactMatch != null) return exactMatch;
        if (namedMatch != null) return namedMatch;
        return fallback;
    }

    static Object getMouseHandler(Object mc) {
        for (String name : new String[]{"mouseHandler", "mouse"}) {
            try { return mc.getClass().getField(name).get(mc); } catch (Exception ignored) {}
            try { return mc.getClass().getDeclaredField(name).get(mc); } catch (Exception ignored) {}
        }
        for (Field f : getAllFields(mc.getClass())) {
            if (f.getType().getSimpleName().contains("MouseHandler") || f.getType().getSimpleName().contains("Mouse")) {
                try { f.setAccessible(true); return f.get(mc); } catch (Exception ignored) {}
            }
        }
        for (Field f : getAllFields(mc.getClass())) {
            String tn = f.getType().getName();
            if (tn.contains("Mouse") && !tn.contains("MouseEvent")) {
                try { f.setAccessible(true); return f.get(mc); } catch (Exception ignored) {}
            }
        }
        for (Field f : getAllFields(mc.getClass())) {
            String tn = f.getType().getSimpleName();
            if (tn.startsWith("class_") && hasDoubleField(f.getType()) && !tn.equals(mc.getClass().getSimpleName())) {
                try { f.setAccessible(true); return f.get(mc); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static boolean hasDoubleField(Class<?> clazz) {
        for (Field f : getAllFields(clazz)) {
            if (f.getType() == double.class) return true;
        }
        return false;
    }

    static Object getClipboardManager(Object mc) {
        try { return mc.getClass().getField("clipboardHandler").get(mc); } catch (Exception e1) {}
        try { return mc.getClass().getMethod("getClipboardHandler").invoke(mc); } catch (Exception e2) {}
        try { return mc.getClass().getField("clipboardManager").get(mc); } catch (Exception e3) {}
        for (Field f : getAllFields(mc.getClass())) {
            String fn = f.getType().getSimpleName();
            if ((fn.contains("Clipboard") || fn.contains("clipboard")) && !fn.contains("Handler")) {
                try { f.setAccessible(true); return f.get(mc); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    static int readIntField(Object obj, java.util.List<Field> fields, int index) {
        if (index < fields.size()) try { fields.get(index).setAccessible(true); return fields.get(index).getInt(obj); } catch (Exception e) {}
        return 0;
    }
}

package xyz.langyo.minecraft.mcp.common;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ReflectionCache {

    private static final Map<Class<?>, List<Method>> methodCache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, List<Field>> fieldCache = new ConcurrentHashMap<>();

    private static Class<?> glfwClass, gl11Class, gl30Class, displayClass, mcClass;
    private static Method mcGetInstanceMethod;
    private static volatile Object cachedMcInstance;
    private static Method glfwSetInputModeMethod, glfwSetCursorPosMethod;
    private static int GLFW_CURSOR, GLFW_CURSOR_NORMAL, GLFW_CURSOR_DISABLED;
    private static volatile boolean classCacheInit = false;

    private static final Map<String, Field> discoveredFields = new ConcurrentHashMap<>();
    private static final Map<String, Method> discoveredMethods = new ConcurrentHashMap<>();
    private static volatile boolean fieldsDiscovered = false;

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
            if (mcClass == null) try { mcClass = Class.forName("net.minecraft.client.MinecraftClient"); } catch (Exception ignored) {}
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

    public static void setMinecraftInstance(Object instance) {
        cachedMcInstance = instance;
        if (instance != null) discoverFields(instance);
    }

    static void discoverFields(Object mc) {
        if (fieldsDiscovered) return;
        fieldsDiscovered = true;
        try {
            Class<?> mcClazz = mc.getClass();
            List<Field> allFields = getAllFields(mcClazz);
            Map<String, List<Field>> candidates = new java.util.LinkedHashMap<>();
            candidates.put("mouseHandler", new ArrayList<>());
            candidates.put("keyboardHandler", new ArrayList<>());
            candidates.put("window", new ArrayList<>());
            candidates.put("player", new ArrayList<>());
            candidates.put("level", new ArrayList<>());
            candidates.put("gameMode", new ArrayList<>());
            candidates.put("screen", new ArrayList<>());

            for (Field f : allFields) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Class<?> ft = f.getType();
                if (ft == mcClazz) continue;
                if (ft.isPrimitive()) continue;
                if (ft.getName().startsWith("java.")) continue;

                boolean matched = false;
                try { if (isMouseHandlerType(ft)) { candidates.get("mouseHandler").add(f); matched = true; } } catch (Throwable ignored) {}
                try { if (isKeyboardHandlerType(ft)) { candidates.get("keyboardHandler").add(f); matched = true; } } catch (Throwable ignored) {}
                try { if (!matched && isWindowType(ft)) { candidates.get("window").add(f); matched = true; } } catch (Throwable ignored) {}
                try { if (!matched && isPlayerType(ft)) { candidates.get("player").add(f); matched = true; } } catch (Throwable ignored) {}
                try { if (!matched && isLevelType(ft)) { candidates.get("level").add(f); matched = true; } } catch (Throwable ignored) {}
                try { if (!matched && isGameModeType(ft)) { candidates.get("gameMode").add(f); matched = true; } } catch (Throwable ignored) {}
                try { if (!matched && isScreenType(ft)) { candidates.get("screen").add(f); } } catch (Throwable ignored) {}
            }

            Set<Field> used = new HashSet<>();
            for (Map.Entry<String, List<Field>> entry : candidates.entrySet()) {
                for (Field f : entry.getValue()) {
                    if (used.add(f)) {
                        discoveredFields.put(entry.getKey(), f);
                        dbg("discovered " + entry.getKey() + " field: " + f.getName() + " type=" + f.getType().getName());
                        break;
                    }
                }
            }

            for (Method m : getAllMethods(mcClazz)) {
                if (m.getParameterCount() == 0 && m.getReturnType() != void.class) {
                    String rtName = m.getReturnType().getName();
                    if (!discoveredMethods.containsKey("getMainRenderTarget")
                            && (rtName.contains("RenderTarget") || rtName.contains("Framebuffer") || rtName.contains("FrameBuffer"))) {
                        discoveredMethods.put("getMainRenderTarget", m);
                        dbg("discovered getMainRenderTarget: " + m.getName() + " ret=" + rtName);
                    }
                }
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == Runnable.class
                        && !m.getName().startsWith("lambda$")) {
                    if (!discoveredMethods.containsKey("execute")) {
                        discoveredMethods.put("execute", m);
                        dbg("discovered execute: " + m.getName());
                    }
                }
            }
        } catch (Exception e) {
            dbg("discoverFields failed: " + e.getMessage());
        }
    }

    private static void dbg(String msg) {
        System.out.println("[MCP-Discovery] " + msg);
    }

    static Field getDiscoveredField(String name) { return discoveredFields.get(name); }
    static Method getDiscoveredMethod(String name) { return discoveredMethods.get(name); }

    private static boolean isPlayerType(Class<?> clazz) {
        int doubleCount = 0;
        boolean hasStringReturn = false;
        for (Method m : getAllMethods(clazz)) {
            if (m.getParameterCount() == 0) {
                if (m.getReturnType() == double.class) doubleCount++;
                if (m.getReturnType() == String.class) hasStringReturn = true;
            }
        }
        return doubleCount >= 5 && hasStringReturn;
    }

    private static boolean isLevelType(Class<?> clazz) {
        for (Method m : getAllMethods(clazz)) {
            if (m.getParameterCount() == 0 && m.getReturnType().isEnum()) return true;
        }
        for (Method m : getAllMethods(clazz)) {
            String n = m.getName();
            if (m.getParameterCount() == 0 && (n.equals("getDifficulty") || n.equals("difficulty"))) return true;
        }
        int entityCount = 0;
        for (Method m : getAllMethods(clazz)) {
            if (m.getParameterCount() <= 1) {
                String rt = m.getReturnType().getName();
                if (rt.contains("Entity") || rt.contains("Player")) entityCount++;
            }
        }
        return entityCount >= 3;
    }

    private static boolean isScreenType(Class<?> clazz) {
        boolean hasRender = false;
        boolean hasBoolReturn = false;
        boolean hasVoidReturn = false;
        for (Method m : getAllMethods(clazz)) {
            Class<?>[] pts = m.getParameterTypes();
            if (pts.length == 4 && !pts[0].isPrimitive() && pts[0].getName().startsWith("net.minecraft.")
                    && pts[1] == int.class && pts[2] == int.class && pts[3] == float.class
                    && m.getReturnType() == void.class) hasRender = true;
            if (m.getParameterCount() == 0 && m.getReturnType() == boolean.class
                    && !Modifier.isStatic(m.getModifiers())) hasBoolReturn = true;
            if (m.getParameterCount() == 0 && m.getReturnType() == void.class
                    && !Modifier.isStatic(m.getModifiers())) hasVoidReturn = true;
        }
        if (hasRender) return true;
        boolean hasMouseClicked = false;
        hasBoolReturn = false;
        hasVoidReturn = false;
        for (Method m : getAllMethods(clazz)) {
            Class<?>[] pts = m.getParameterTypes();
            if (pts.length == 3 && m.getReturnType() == boolean.class
                    && pts[0] == double.class && pts[1] == double.class && pts[2] == int.class) hasMouseClicked = true;
            if (m.getParameterCount() == 0 && m.getReturnType() == boolean.class) hasBoolReturn = true;
            if (m.getParameterCount() == 0 && m.getReturnType() == void.class) hasVoidReturn = true;
        }
        return hasMouseClicked && hasBoolReturn && hasVoidReturn;
    }

    private static boolean isMouseHandlerType(Class<?> clazz) {
        for (Method m : getAllMethods(clazz)) {
            Class<?>[] pts = m.getParameterTypes();
            if (pts.length == 4 && pts[0] == long.class && pts[1] == int.class
                    && pts[2] == int.class && pts[3] == int.class
                    && !Modifier.isStatic(m.getModifiers())) return true;
        }
        return false;
    }

    static boolean isKeyboardHandlerType(Class<?> clazz) {
        for (Method m : getAllMethods(clazz)) {
            Class<?>[] pts = m.getParameterTypes();
            if (pts.length == 5 && pts[0] == long.class && pts[1] == int.class
                    && pts[2] == int.class && pts[3] == int.class && pts[4] == int.class
                    && !Modifier.isStatic(m.getModifiers())) return true;
        }
        return false;
    }

    private static boolean isWindowType(Class<?> clazz) {
        boolean hasLongReturn = false;
        boolean hasIntReturn = false;
        for (Method m : getAllMethods(clazz)) {
            if (m.getParameterCount() == 0) {
                if (m.getReturnType() == long.class) hasLongReturn = true;
                if (m.getReturnType() == int.class) hasIntReturn = true;
            }
        }
        return hasLongReturn && hasIntReturn;
    }

    private static boolean isGameModeType(Class<?> clazz) {
        for (Method m : getAllMethods(clazz)) {
            String n = m.getName();
            if (n.equals("getPlayerMode") || n.equals("getGameMode") || n.equals("getCurrentGameType")
                    || n.equals("getGameModeForPlayer")) return true;
        }
        boolean hasEnumReturn = false;
        for (Method m : getAllMethods(clazz)) {
            if (m.getParameterCount() == 0 && m.getReturnType().isEnum()) hasEnumReturn = true;
        }
        boolean hasPlayerParam = false;
        boolean hasHandParam = false;
        for (Method m : getAllMethods(clazz)) {
            for (Class<?> pt : m.getParameterTypes()) {
                String ptn = pt.getName();
                if (ptn.contains("Player") || ptn.contains("player")) hasPlayerParam = true;
                if (ptn.contains("Hand") || ptn.contains("hand")) hasHandParam = true;
            }
        }
        return hasEnumReturn && (hasPlayerParam || hasHandParam);
    }

    static Object getMinecraftInstance() {
        if (cachedMcInstance != null) return cachedMcInstance;
        initClassCache();
        try {
            if (mcGetInstanceMethod != null) {
                Object instance = mcGetInstanceMethod.invoke(null);
                if (instance != null) { cachedMcInstance = instance; return instance; }
            }
            if (mcClass != null) {
                for (Field f : getAllFields(mcClass)) {
                    if (f.getType() == mcClass && Modifier.isStatic(f.getModifiers())) {
                        try { f.setAccessible(true); Object v = f.get(null); if (v != null) { cachedMcInstance = v; return v; } } catch (Exception ignored) {}
                    }
                }
            }
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
        try {
            for (Class<?> iface : clazz.getInterfaces()) {
                for (Method m : iface.getMethods()) {
                    if (methods.stream().noneMatch(em -> em.getName().equals(m.getName())
                            && Arrays.equals(em.getParameterTypes(), m.getParameterTypes()))) {
                        methods.add(m);
                    }
                }
            }
        } catch (Throwable ignored) {}
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
                try { f.setAccessible(true); Object p = f.get(mc); if (p != null) return p; } catch (Exception ignored) {}
            }
        }
        Field discovered = discoveredFields.get("player");
        if (discovered != null) { try { discovered.setAccessible(true); return discovered.get(mc); } catch (Exception ignored) {} }
        return null;
    }

    static Object getLevel(Object mc) throws Exception {
        try { return mc.getClass().getMethod("level").invoke(mc); } catch (NoSuchMethodException ignored) {}
        try { return mc.getClass().getMethod("world").invoke(mc); } catch (NoSuchMethodException ignored) {}
        for (Field f : getAllFields(mc.getClass())) {
            String n = f.getName();
            if (n.equals("theWorld") || n.equals("field_71441_f") || n.equals("level") || n.equals("world")) {
                try { f.setAccessible(true); Object v = f.get(mc); if (v != null) return v; } catch (Exception ignored) {}
            }
        }
        Field discovered = discoveredFields.get("level");
        if (discovered != null) { try { discovered.setAccessible(true); return discovered.get(mc); } catch (Exception ignored) {} }
        return null;
    }

    static Object getPlayerLevel(Object player) throws Exception {
        try { return player.getClass().getMethod("level").invoke(player); } catch (NoSuchMethodException ignored) {}
        try { return player.getClass().getMethod("world").invoke(player); } catch (NoSuchMethodException ignored) {}
        for (Field f : getAllFields(player.getClass())) {
            String n = f.getName();
            if (n.equals("theWorld") || n.equals("field_70170_p") || n.equals("level") || n.equals("world")) {
                try { f.setAccessible(true); Object v = f.get(player); if (v != null) return v; } catch (Exception ignored) {}
            }
        }
        for (Field f : getAllFields(player.getClass())) {
            if (!Modifier.isStatic(f.getModifiers()) && !f.getType().isPrimitive() && !f.getType().getName().startsWith("java.")) {
                try { f.setAccessible(true); Object v = f.get(player); if (v != null && isLevelType(v.getClass())) return v; } catch (Exception ignored) {}
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
        Field discovered = discoveredFields.get("screen");
        if (discovered != null) { try { discovered.setAccessible(true); return discovered.get(mc); } catch (Exception ignored) {} }
        for (Field f : getAllFields(mc.getClass())) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
                Object val = f.get(mc);
                if (val == null) continue;
                if (isScreenInstance(val)) return val;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static boolean isScreenInstance(Object obj) {
        try {
            Class<?> clazz = obj.getClass();
            boolean hasVoidNoArg = false;
            boolean hasBoolNoArg = false;
            boolean hasMouseClicked = false;
            for (Method m : getAllMethods(clazz)) {
                if (Modifier.isStatic(m.getModifiers())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (m.getParameterCount() == 0) {
                    if (m.getReturnType() == void.class) hasVoidNoArg = true;
                    if (m.getReturnType() == boolean.class) hasBoolNoArg = true;
                }
                if (pts.length == 3 && m.getReturnType() == boolean.class
                        && pts[0] == double.class && pts[1] == double.class && pts[2] == int.class) hasMouseClicked = true;
            }
            return hasVoidNoArg && hasBoolNoArg && hasMouseClicked;
        } catch (Throwable t) {
            return false;
        }
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
        Field discovered = discoveredFields.get("mouseHandler");
        if (discovered != null) { try { discovered.setAccessible(true); return discovered.get(mc); } catch (Exception ignored) {} }
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

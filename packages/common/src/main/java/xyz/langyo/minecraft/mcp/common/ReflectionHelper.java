package xyz.langyo.minecraft.mcp.common;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

public final class ReflectionHelper {

    private static final boolean LWJGL3;
    private static final boolean HAS_VULKAN;
    private static Robot awtRobot;

    private static Class<?> glfwClass, gl11Class, gl30Class, displayClass, mcClass;
    private static Method mcGetInstanceMethod;
    private static Method glfwSetInputModeMethod, glfwSetCursorPosMethod;
    private static int GLFW_CURSOR, GLFW_CURSOR_NORMAL, GLFW_CURSOR_DISABLED;
    private static volatile boolean classCacheInit = false;

    static void initClassCache() {
        if (classCacheInit) return;
        try {
            try { mcClass = Class.forName("net.minecraft.client.Minecraft"); } catch (Exception ignored) {}
            if (mcClass != null) {
                try { mcGetInstanceMethod = mcClass.getMethod("getInstance"); } catch (Exception ignored) {}
                if (mcGetInstanceMethod == null) try { mcGetInstanceMethod = mcClass.getMethod("getMinecraft"); } catch (Exception ignored) {}
                if (mcGetInstanceMethod == null) {
                    for (Method m : mcClass.getMethods()) {
                        if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && m.getReturnType() == mcClass && m.getParameterCount() == 0) {
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

    static {
        boolean v = false;
        try { Class.forName("org.lwjgl.glfw.GLFW"); v = true; } catch (ClassNotFoundException e) {}
        LWJGL3 = v;
        boolean vk = false;
        try { Class.forName("org.lwjgl.vulkan.VK"); vk = true; } catch (ClassNotFoundException e) {}
        HAS_VULKAN = vk;
        try { awtRobot = new Robot(); awtRobot.setAutoDelay(10); } catch (Exception e) {}
    }

    private ReflectionHelper() {}

    private static String[] mainThreadResult = new String[1];
    private static CountDownLatch mainThreadLatch;

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
        initClassCache();
        try {
            if (mcGetInstanceMethod != null) return mcGetInstanceMethod.invoke(null);
            throw new RuntimeException("No Minecraft static getter found");
        } catch (Exception e) {
            throw new RuntimeException("Failed to get Minecraft instance", e);
        }
    }

    private static long cachedWindowHandle = 0;

    public static long getWindowHandle(Object mc) {
        if (cachedWindowHandle != 0) return cachedWindowHandle;
        try {
            Object window = null;
            try { window = mc.getClass().getMethod("getWindow").invoke(mc); }
            catch (NoSuchMethodException e) {
                try { window = mc.getClass().getMethod("getMainWindow").invoke(mc); }
                catch (NoSuchMethodException e2) {
                    for (Field f : mc.getClass().getDeclaredFields()) {
                        if (f.getType().getSimpleName().contains("Window") || f.getType().getSimpleName().contains("MainWindow")) {
                            f.setAccessible(true);
                            window = f.get(mc);
                            break;
                        }
                    }
                }
            }
            if (window == null) return 0;
            for (String methodName : new String[]{"handle", "getHandle", "getWindow"}) {
                try {
                    Object result = window.getClass().getMethod(methodName).invoke(window);
                    if (result instanceof Number) {
                        long val = ((Number) result).longValue();
                        if (val != 0) { cachedWindowHandle = val; return val; }
                    }
                } catch (NoSuchMethodException ignored) {}
            }
            for (Field f : window.getClass().getDeclaredFields()) {
                if (f.getType() == long.class) {
                    f.setAccessible(true);
                    long val = f.getLong(window);
                    if (val != 0) { cachedWindowHandle = val; return val; }
                }
            }
            return 0;
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

    private static int getIntFieldByNames(Object obj, String... names) {
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

    public static int getDisplayWidth(Object mc) {
        int v = getIntFieldByNames(mc, "displayWidth", "field_71443_c", "width");
        return v > 0 ? v : 0;
    }

    public static int getDisplayHeight(Object mc) {
        int v = getIntFieldByNames(mc, "displayHeight", "field_71440_d", "height");
        return v > 0 ? v : 0;
    }

    public static int getGlfwWindowSize(Object mc, boolean isWidth) {
        if (!LWJGL3) return 0;
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
        if (LWJGL3) return 0;
        try {
            Class<?> displayClass = Class.forName("org.lwjgl.opengl.Display");
            String method = isWidth ? "getWidth" : "getHeight";
            return (Integer) displayClass.getMethod(method).invoke(null);
        } catch (Exception e) { return 0; }
    }

    public static String getPlayerInfo(Object mc) {
        try {
            Object player = getPlayer(mc);
            if (player == null) return "{\"name\":null}";
            String name = invokeString(player, "getName");
            double health = getDouble(player, "getHealth");
            double x = getDouble(player, "getX", "posX");
            double y = getDouble(player, "getY", "posY");
            double z = getDouble(player, "getZ", "posZ");
            String dim = getDimensionId(player);
            return String.format("{\"name\":\"%s\",\"health\":%.1f,\"pos\":\"%.1f %.1f %.1f\",\"dimension\":\"%s\"}",
                    name, health, x, y, z, dim);
        } catch (Exception e) {
            return "{\"name\":null,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String guiClick(Object mc, int x, int y, int button) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen == null) return "{\"error\":\"no screen\"}";
            String screenName = screen.getClass().getSimpleName();
            double gx = (double) x;
            double gy = (double) y;
            dbg("guiClick: gui(" + (int)gx + "," + (int)gy + ") screen=" + screenName);
            StringBuilder results = new StringBuilder();
            for (Method m : getAllMethods(screen.getClass())) {
                String n = m.getName();
                Class<?>[] pt = m.getParameterTypes();
                if (m.getParameterCount() == 3 && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
                    boolean isMouseLike = (pt[0] == double.class && pt[1] == double.class && pt[2] == int.class);
                    boolean isIntMouse = (pt[0] == int.class && pt[1] == int.class && pt[2] == int.class);
                    if (isMouseLike || isIntMouse) {
                        dbg("guiClick: trying handler " + n + " params=" + java.util.Arrays.toString(pt) + " from " + m.getDeclaringClass().getSimpleName());
                        try {
                            m.setAccessible(true);
                            Object result = isMouseLike ? m.invoke(screen, gx, gy, button) : m.invoke(screen, (int)gx, (int)gy, button);
                            if (results.length() > 0) results.append(",");
                            results.append("\"").append(n).append("\":").append(result);
                            if (Boolean.TRUE.equals(result)) break;
                        } catch (Exception e) {
                            dbg("guiClick: " + n + " failed: " + e.getMessage());
                        }
                    }
                }
            }
            String widgetResult = "";
            for (Field f : getAllFields(screen.getClass())) {
                String fn = f.getName();
                if (fn.equals("children") || fn.equals("renderables") || fn.equals("widgets")) {
                    f.setAccessible(true);
                    Object list = f.get(screen);
                    if (list instanceof java.util.List) {
                        for (Object w : (java.util.List<?>) list) {
                            try {
                                int wx=0, wy=0, ww=0, wh=0;
                                for (Field wf : getAllFields(w.getClass())) {
                                    try { wf.setAccessible(true);
                                        String wfn = wf.getName();
                                        if (wfn.equals("x")) wx = wf.getInt(w);
                                        else if (wfn.equals("y")) wy = wf.getInt(w);
                                        else if (wfn.equals("width")) ww = wf.getInt(w);
                                        else if (wfn.equals("height")) wh = wf.getInt(w);
                                    } catch(Exception ignored){}
                                }
                                // Check bounds with some tolerance
                                if (wx <= gx && gx < wx + ww && wy <= gy && gy < wy + wh) {
                                    dbg("guiClick: hit widget " + w.getClass().getSimpleName() + " at (" + wx + "," + wy + ")+" + ww + "x" + wh);
                                    double relX = gx - wx;
                                    double relY = gy - wy;
                                    boolean widgetClicked = false;
                                    for (String priority : new String[]{"mouseClicked","mouseReleased"}) {
                                        for (Method bm : getAllMethods(w.getClass())) {
                                            if (!bm.getName().equals(priority)) continue;
                                            Class<?>[] bpt = bm.getParameterTypes();
                                            if (bm.getParameterCount() == 3
                                                    && bpt[0] == double.class && bpt[1] == double.class && bpt[2] == int.class
                                                    && bm.getReturnType() == boolean.class) {
                                                try {
                                                    bm.setAccessible(true);
                                                    Object r = bm.invoke(w, relX, relY, button);
                                                    dbg("guiClick: widget." + bm.getName() + "(" + (int)relX + "," + (int)relY + ")=" + r);
                                                    if (Boolean.TRUE.equals(r)) { widgetClicked = true; break; }
                                                } catch (Exception e) {
                                                    dbg("guiClick: widget." + bm.getName() + " failed: " + e.getMessage());
                                                }
                                            }
                                        }
                                        if (widgetClicked) break;
                                    }
                                    if (!widgetClicked) {
                                        for (Method bm : getAllMethods(w.getClass())) {
                                            Class<?>[] bpt = bm.getParameterTypes();
                                            if (bm.getParameterCount() == 3
                                                    && bpt[0] == double.class && bpt[1] == double.class && bpt[2] == int.class
                                                    && bm.getReturnType() == boolean.class) {
                                                try {
                                                    bm.setAccessible(true);
                                                    Object r = bm.invoke(w, relX, relY, button);
                                                    dbg("guiClick: widget." + bm.getName() + "(" + (int)relX + "," + (int)relY + ")=" + r);
                                                    if (Boolean.TRUE.equals(r)) { widgetClicked = true; break; }
                                                } catch (Exception e) {
                                                    dbg("guiClick: widget." + bm.getName() + " failed: " + e.getMessage());
                                                }
                                            }
                                        }
                                    }
                                    if (!widgetClicked) {
                                        for (Method bm : getAllMethods(w.getClass())) {
                                            if ((bm.getName().equals("onPress") || bm.getName().equals("onClick"))
                                                    && bm.getParameterCount() <= 2) {
                                            bm.setAccessible(true);
                                            Class<?>[] bpt = bm.getParameterTypes();
                                            Object[] bargs = new Object[bpt.length];
                                            for (int bi = 0; bi < bpt.length; bi++) {
                                                if (bpt[bi] == double.class) bargs[bi] = 0.0;
                                                else if (bpt[bi] == float.class) bargs[bi] = 0.0f;
                                                else if (bpt[bi] == int.class) bargs[bi] = 0;
                                                else if (bpt[bi] == long.class) bargs[bi] = 0L;
                                                else if (bpt[bi] == boolean.class) bargs[bi] = false;
                                                else if (bpt[bi].isInstance(w)) bargs[bi] = w;
                                                else bargs[bi] = null;
                                            }
                                            bm.invoke(w, bargs);
                                            widgetResult = ",\"widget\":\"" + w.getClass().getSimpleName() + "\",\"widget_method\":\"" + bm.getName() + "\"";
                                            }
                                        }
                                    }
                                    if (!widgetClicked) {
                                        for (Field bf : getAllFields(w.getClass())) {
                                        if (bf.getName().equals("onPress")) {
                                            bf.setAccessible(true);
                                            Object ph = bf.get(w);
                                            if (ph != null) {
                                                for (Method hm : getAllMethods(ph.getClass())) {
                                                    if (hm.getName().equals("accept") && hm.getParameterCount() == 1) {
                                                        hm.setAccessible(true); hm.invoke(ph, w);
                                                        widgetResult = ",\"widget\":\"" + w.getClass().getSimpleName() + "\",\"via\":\"onPress.accept\"";
                                                    } else if (hm.getParameterCount() == 0 && (hm.getName().equals("run") || hm.getName().equals("accept"))) {
                                                        hm.setAccessible(true); hm.invoke(ph);
                                                        widgetResult = ",\"widget\":\"" + w.getClass().getSimpleName() + "\",\"via\":\"" + hm.getName() + "()\"";
                                                    }
                                                }
                                            }
                                            }
                                        }
                                    }
                                }
                            } catch(Exception ignored){}
                        }
                    }
                }
            }
            if (results.length() > 0 || !widgetResult.isEmpty())
                return "{\"clicked\":true,\"screen\":\"" + screenName + "\",\"gui\":[" + (int)gx + "," + (int)gy + "],\"results\":{" + results.toString() + "}" + widgetResult + "}";
            return "{\"error\":\"no click method on " + screen.getClass().getName() + "\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String getScreenButtons(Object mc) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen == null) return "{\"error\":\"no screen\"}";
            StringBuilder sb = new StringBuilder("{\"screen\":\"" + screen.getClass().getSimpleName() + "\",\"buttons\":[");
            boolean first = true;
            for (Field f : getAllFields(screen.getClass())) {
                String fn = f.getName();
                if (fn.equals("buttonList") || fn.equals("buttons") || fn.equals("field_146292_n") || fn.equals("children") || fn.equals("renderables") || fn.contains("button")) {
                    try {
                        f.setAccessible(true);
                        Object list = f.get(screen);
                        if (list instanceof java.util.List) {
                            for (Object btn : (java.util.List<?>) list) {
                                if (first) first = false; else sb.append(",");
                                int id = 0, x = 0, y = 0, w = 0, h = 0;
                                String label = "";
                                Class<?> bc = btn.getClass();
                                java.util.List<Field> intFields = new java.util.ArrayList<>();
                                java.util.List<Field> strFields = new java.util.ArrayList<>();
                                for (Field bf : getAllFields(bc)) {
                                    if (bf.getType() == int.class) intFields.add(bf);
                                    else if (bf.getType() == String.class) strFields.add(bf);
                                }
                                for (Field bf : intFields) {
                                    String bfn = bf.getName();
                                    bf.setAccessible(true);
                                    try {
                                        if (bfn.equals("id") || bfn.contains("146127") || bfn.endsWith("_k")) id = bf.getInt(btn);
                                        else if (bfn.equals("x") || bfn.contains("146120") || bfn.endsWith("_f")) x = bf.getInt(btn);
                                        else if (bfn.equals("y") || (bfn.contains("121") && !bfn.contains("146")) || bfn.endsWith("_g")) y = bf.getInt(btn);
                                        else if (bfn.equals("width") || bfn.contains("146118") || bfn.endsWith("_e")) w = bf.getInt(btn);
                                        else if (bfn.equals("height") || bfn.contains("119") && !bfn.contains("146") || bfn.endsWith("_h")) h = bf.getInt(btn);
                                    } catch (Exception ignored) {}
                                }
                                if (x == 0 && y == 0 && w == 0 && intFields.size() >= 5) {
                                    x = readIntField(btn, intFields, 1);
                                    y = readIntField(btn, intFields, 2);
                                    w = readIntField(btn, intFields, 3);
                                    h = readIntField(btn, intFields, 4);
                                }
                                for (Field bf : strFields) {
                                    String bfn = bf.getName();
                                    bf.setAccessible(true);
                                    try {
                                        if (bfn.contains("displayString") || bfn.contains("146126") || bfn.endsWith("_o"))
                                            label = String.valueOf(bf.get(btn));
                                    } catch (Exception ignored) {}
                                }
                                if (label.isEmpty() && !strFields.isEmpty()) {
                                    try { strFields.get(0).setAccessible(true); label = String.valueOf(strFields.get(0).get(btn)); } catch (Exception ignored) {}
                                }
                                sb.append(String.format("{\"id\":%d,\"x\":%d,\"y\":%d,\"w\":%d,\"h\":%d,\"label\":\"%s\"}",
                                        id, x, y, w, h, label.replace("\"", "'")));
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
            sb.append("]}");
            return sb.toString();
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private static int readIntField(Object obj, java.util.List<Field> fields, int index) {
        if (index < fields.size()) try { fields.get(index).setAccessible(true); return fields.get(index).getInt(obj); } catch (Exception e) {}
        return 0;
    }

    public static String clickButtonById(Object mc, int buttonId) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen == null) return "{\"error\":\"no screen\"}";
            for (Field f : getAllFields(screen.getClass())) {
                String fn = f.getName();
                if (fn.equals("buttonList") || fn.equals("buttons") || fn.equals("field_146292_n") || fn.equals("children") || fn.equals("renderables") || fn.contains("button")) {
                    f.setAccessible(true);
                    Object list = f.get(screen);
                    if (list instanceof java.util.List) {
                        for (Object btn : (java.util.List<?>) list) {
                            int id = 0;
                            for (Field bf : getAllFields(btn.getClass())) {
                                String bfn = bf.getName();
                                if (bfn.equals("id") || bfn.contains("146127") || bfn.endsWith("_k")) {
                                    try { bf.setAccessible(true); id = bf.getInt(btn); } catch (Exception ignored) {}
                                }
                            }
                            if (id == buttonId) {
                                dbg("clickButtonById: found button id=" + buttonId + " class=" + btn.getClass().getSimpleName());
                                // Try button's own onPress/onClick/pressAction first
                                for (Method bm : getAllMethods(btn.getClass())) {
                                    String bn = bm.getName();
                                    if ((bn.equals("onPress") || bn.equals("onClick") || bn.equals("pressAction")
                                            || bn.contains("Press") || bn.contains("Click"))
                                            && bm.getParameterCount() <= 2) {
                                        try {
                                            bm.setAccessible(true);
                                            Class<?>[] bpt = bm.getParameterTypes();
                                            Object[] bargs = new Object[bpt.length];
                                            for (int bi = 0; bi < bpt.length; bi++) {
                                                if (bpt[bi] == double.class) bargs[bi] = 0.0;
                                                else if (bpt[bi] == float.class) bargs[bi] = 0.0f;
                                                else bargs[bi] = 0;
                                            }
                                            bm.invoke(btn, bargs);
                                            return "{\"clicked\":true,\"button_id\":" + buttonId + ",\"btn_method\":\"" + bn + "\"}";
                                        } catch (Exception e) { dbg("btn invoke fail " + bn + ": " + e.getMessage()); }
                                    }
                                }
                                // Try onPress field (lambda/Consumer)
                                for (Field bf : getAllFields(btn.getClass())) {
                                    String bfn = bf.getName();
                                    if (bfn.equals("onPress") || bfn.contains("onPress")) {
                                        try {
                                            bf.setAccessible(true);
                                            Object pressHandler = bf.get(btn);
                                            if (pressHandler != null) {
                                                dbg("clickButtonById: invoking onPress handler " + pressHandler.getClass().getName());
                                                for (Method hm : getAllMethods(pressHandler.getClass())) {
                                                    if (hm.getName().equals("accept") && hm.getParameterCount() == 1) {
                                                        hm.setAccessible(true);
                                                        hm.invoke(pressHandler, btn);
                                                        return "{\"clicked\":true,\"button_id\":" + buttonId + ",\"via\":\"onPress.accept\"}";
                                                    }
                                                }
                                                // Try no-arg accept
                                                for (Method hm : getAllMethods(pressHandler.getClass())) {
                                                    if ((hm.getName().equals("accept") || hm.getName().equals("run") || hm.getName().equals("get"))
                                                            && hm.getParameterCount() == 0) {
                                                        hm.setAccessible(true);
                                                        hm.invoke(pressHandler);
                                                        return "{\"clicked\":true,\"button_id\":" + buttonId + ",\"via\":\"" + hm.getName() + "()\"}";
                                                    }
                                                }
                                            }
                                        } catch (Exception e) { dbg("onPress field fail: " + e.getMessage()); }
                                    }
                                }
                                for (Method m : getAllMethods(screen.getClass())) {
                                    if ((m.getName().contains("actionPerformed") || m.getName().contains("func_146284"))
                                            && m.getParameterCount() == 1) {
                                        try {
                                            m.setAccessible(true);
                                            m.invoke(screen, btn);
                                            return "{\"clicked\":true,\"button_id\":" + buttonId + ",\"method\":\"" + m.getName() + "\"}";
                                        } catch (Exception e) { dbg("clickButtonById invoke fail " + m.getName() + ": " + e.getMessage()); }
                                    }
                                }
                                // Type-based search: any single-param method accepting button type
                                for (Method m : getAllMethods(screen.getClass())) {
                                    if (m.getParameterCount() == 1) {
                                        Class<?> pt = m.getParameterTypes()[0];
                                        if (pt.isAssignableFrom(btn.getClass())) {
                                            try {
                                                m.setAccessible(true);
                                                m.invoke(screen, btn);
                                                return "{\"clicked\":true,\"button_id\":" + buttonId + ",\"method\":\"" + m.getName() + "\"}";
                                            } catch (Exception e) { dbg("clickButtonById typeMatch fail " + m.getName() + ": " + e.getMessage()); }
                                        }
                                    }
                                }
                                return "{\"clicked\":true,\"button_id\":" + buttonId + ",\"method\":\"direct\"}";
                            }
                        }
                    }
                }
            }
            return "{\"error\":\"button " + buttonId + " not found\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String enumerateWidgets(Object mc) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen == null) return "{\"error\":\"no screen\"}";
            StringBuilder sb = new StringBuilder("{\"screen\":\"" + screen.getClass().getSimpleName() + "\",\"widgets\":[");
            int idx = 0;
            boolean first = true;
            boolean found = false;
            for (String fn : new String[]{"renderables", "children", "widgets"}) {
                for (Field f : getAllFields(screen.getClass())) {
                    if (f.getName().equals(fn)) {
                        try {
                            f.setAccessible(true);
                            Object list = f.get(screen);
                            if (list instanceof java.util.List) {
                                for (Object w : (java.util.List<?>) list) {
                                    if (first) first = false; else sb.append(",");
                                    String cls = w.getClass().getSimpleName();
                                    int x=0,y=0,w2=0,h2=0;
                                    boolean hasOnPress = false;
                                    for (Field wf : getAllFields(w.getClass())) {
                                        try { wf.setAccessible(true);
                                            String wfn = wf.getName();
                                            if (wfn.equals("x")) x = wf.getInt(w);
                                            else if (wfn.equals("y")) y = wf.getInt(w);
                                            else if (wfn.equals("width")) w2 = wf.getInt(w);
                                            else if (wfn.equals("height")) h2 = wf.getInt(w);
                                            else if (wfn.equals("onPress") && wf.get(w) != null) hasOnPress = true;
                                        } catch(Exception ignored){}
                                    }
                                    for (Method wm : getAllMethods(w.getClass())) {
                                        if (wm.getName().equals("onPress")) hasOnPress = true;
                                    }
                                    sb.append(String.format("{\"i\":%d,\"c\":\"%s\",\"x\":%d,\"y\":%d,\"w\":%d,\"h\":%d,\"press\":%b}",
                                            idx, cls, x, y, w2, h2, hasOnPress));
                                    idx++;
                                }
                                found = true;
                            }
                        } catch (Exception ignored) {}
                        break;
                    }
                }
                if (found) break;
            }
            sb.append("],\"total\":" + idx + "}");
            return sb.toString();
        } catch (Exception e) { return "{\"error\":\"" + e.getMessage() + "\"}"; }
    }

    public static String clickButtonByIndex(Object mc, int index) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen == null) return "{\"error\":\"no screen\"}";
            String[] fieldNames = new String[]{"renderables", "children", "widgets"};
            Object targetWidget = null;
            for (String fn : fieldNames) {
                try {
                    for (Field f : getAllFields(screen.getClass())) {
                        if (f.getName().equals(fn)) {
                            f.setAccessible(true);
                            Object list = f.get(screen);
                            if (list instanceof java.util.List) {
                                java.util.List<?> wl = (java.util.List<?>) list;
                                if (index < wl.size()) {
                                    targetWidget = wl.get(index);
                                    break;
                                }
                            }
                        }
                    }
                    if (targetWidget != null) break;
                } catch (Exception ignored) {}
            }
            if (targetWidget == null) {
                for (Field f : getAllFields(screen.getClass())) {
                    String fn = f.getName();
                    if (fn.contains("button")) {
                        f.setAccessible(true);
                        Object list = f.get(screen);
                        if (list instanceof java.util.List) {
                            java.util.List<?> wl = (java.util.List<?>) list;
                            if (index < wl.size()) {
                                targetWidget = wl.get(index);
                                break;
                            }
                        }
                    }
                }
            }
            if (targetWidget == null) return "{\"error\":\"index " + index + " out of range\"}";
            Object widget = targetWidget;
            dbg("clickButtonByIndex: index=" + index + " class=" + widget.getClass().getSimpleName());
            // Special handling for CycleButton: directly cycle the value
            String widgetClassName = widget.getClass().getSimpleName();
            dbg("clickButtonByIndex: checking CycleButton: className=" + widgetClassName + " match=" + widgetClassName.equals("CycleButton"));
            if (widgetClassName.equals("CycleButton")) {
                dbg("clickButtonByIndex: ENTERED CycleButton handler");
                try {
                    // Find and call cycleValue() method
                    for (Method cm : getAllMethods(widget.getClass())) {
                        if (cm.getName().equals("cycleValue") && cm.getParameterCount() == 0) {
                            cm.setAccessible(true);
                            cm.invoke(widget);
                            return "{\"clicked\":true,\"index\":" + index + ",\"via\":\"cycleValue()\",\"class\":\"CycleButton\"}";
                        }
                    }
                    // Fallback: manually increment index and trigger onValueChange
                    Field idxField = null, valField = null, valsField = null, onChangeField = null;
                    for (Field df : getAllFields(widget.getClass())) {
                        String dn = df.getName();
                        if (dn.equals("index")) idxField = df;
                        else if (dn.equals("value")) valField = df;
                        else if (dn.equals("values")) valsField = df;
                        else if (dn.equals("onValueChange")) onChangeField = df;
                    }
                    dbg("clickBtn CB: idxField=" + (idxField != null) + " valsField=" + (valsField != null) + " valField=" + (valField != null) + " onChangeField=" + (onChangeField != null));
                    if (idxField != null && valsField != null) {
                        idxField.setAccessible(true);
                        valsField.setAccessible(true);
                        int curIdx = idxField.getInt(widget);
                        Object vals = valsField.get(widget);
                        dbg("clickBtn CB: curIdx=" + curIdx + " vals type=" + (vals != null ? vals.getClass().getName() : "null"));
                        // Get values list from ValueListSupplier
                        java.util.List<?> valueList = null;
                        if (vals instanceof java.util.List) {
                            valueList = (java.util.List<?>) vals;
                        } else {
                            // ValueListSupplier - call values() or get() method
                            for (Method m : getAllMethods(vals.getClass())) {
                                String mn = m.getName();
                                dbg("clickBtn CB: vals method " + mn + "(" + m.getParameterCount() + ") ret=" + m.getReturnType().getSimpleName());
                                if ((mn.equals("values") || mn.equals("get") || mn.equals("apply") || mn.equals("getAll"))
                                        && m.getParameterCount() == 0 && java.util.List.class.isAssignableFrom(m.getReturnType())) {
                                    m.setAccessible(true);
                                    valueList = (java.util.List<?>) m.invoke(vals);
                                    break;
                                }
                            }
                            // Also try any no-arg method returning List
                            if (valueList == null) {
                                for (Method m : getAllMethods(vals.getClass())) {
                                    if (m.getParameterCount() == 0 && java.util.List.class.isAssignableFrom(m.getReturnType())) {
                                        m.setAccessible(true);
                                        valueList = (java.util.List<?>) m.invoke(vals);
                                        break;
                                    }
                                }
                            }
                        }
                        int size = valueList != null ? valueList.size() : 0;
                        dbg("clickBtn CB: valueList size=" + size);
                        if (size > 0) {
                            int newIdx = (curIdx + 1) % size;
                            dbg("clickBtn CB: cycling from " + curIdx + " to " + newIdx);
                            idxField.setInt(widget, newIdx);
                            // Get new value from the valueList
                            Object newVal = null;
                            if (valueList != null && newIdx < valueList.size()) {
                                newVal = valueList.get(newIdx);
                            }
                            dbg("clickBtn CB: newVal=" + (newVal != null ? newVal.toString() : "null"));
                            if (valField != null) { valField.setAccessible(true); valField.set(widget, newVal); }
                            // Trigger onValueChange callback
                            if (onChangeField != null) {
                                onChangeField.setAccessible(true);
                                Object onChange = onChangeField.get(widget);
                                dbg("clickBtn CB: onChange=" + (onChange != null ? onChange.getClass().getName() : "null"));
                                if (onChange != null) {
                                    for (Method om : getAllMethods(onChange.getClass())) {
                                        dbg("clickBtn CB: onChange method " + om.getName() + "(" + om.getParameterCount() + ")");
                                    }
                                    for (Method om : getAllMethods(onChange.getClass())) {
                                        if (om.getName().equals("onValueChange") && om.getParameterCount() == 3) {
                                            om.setAccessible(true); om.invoke(onChange, widget, newVal, null); break;
                                        } else if (om.getName().equals("onValueChange") && om.getParameterCount() == 2) {
                                            om.setAccessible(true); om.invoke(onChange, widget, newVal); break;
                                        } else if (om.getName().equals("accept") && om.getParameterCount() >= 1) {
                                            om.setAccessible(true);
                                            if (om.getParameterCount() == 1) om.invoke(onChange, widget);
                                            else if (om.getParameterCount() == 3) om.invoke(onChange, widget, newVal, null);
                                            break;
                                        }
                                    }
                                }
                            }
                            return "{\"clicked\":true,\"index\":" + index + ",\"via\":\"manual_cycle\",\"newIdx\":" + newIdx + ",\"class\":\"CycleButton\"}";
                        }
                    }
                } catch (Exception e) { dbg("CycleButton cycle fail: " + e.getMessage()); }
            }
            // Try onPress field for non-CycleButton widgets
            for (Field bf : getAllFields(widget.getClass())) {
                String bfn = bf.getName();
                if (bfn.equals("onPress")) {
                    try {
                        bf.setAccessible(true);
                        Object pressHandler = bf.get(widget);
                        if (pressHandler != null) {
                            for (Method hm : getAllMethods(pressHandler.getClass())) {
                                if (hm.getName().equals("accept") && hm.getParameterCount() == 1) {
                                    hm.setAccessible(true); hm.invoke(pressHandler, widget);
                                    return "{\"clicked\":true,\"index\":" + index + ",\"via\":\"onPress.accept\",\"class\":\"" + widget.getClass().getSimpleName() + "\"}";
                                }
                            }
                            for (Method hm : getAllMethods(pressHandler.getClass())) {
                                if (hm.getName().equals("onPress") && hm.getParameterCount() == 1) {
                                    hm.setAccessible(true); hm.invoke(pressHandler, widget);
                                    return "{\"clicked\":true,\"index\":" + index + ",\"via\":\"onPress.onPress\",\"class\":\"" + widget.getClass().getSimpleName() + "\"}";
                                }
                            }
                            for (Method hm : getAllMethods(pressHandler.getClass())) {
                                if (hm.getParameterCount() == 0 && (hm.getName().equals("run") || hm.getName().equals("accept"))) {
                                    hm.setAccessible(true); hm.invoke(pressHandler);
                                    return "{\"clicked\":true,\"index\":" + index + ",\"via\":\"" + hm.getName() + "()\",\"class\":\"" + widget.getClass().getSimpleName() + "\"}";
                                }
                            }
                        }
                    } catch (Exception e) { dbg("onPress field fail: " + e.getMessage()); }
                }
            }
            // Try onPress/onClick method
            for (Method bm : getAllMethods(widget.getClass())) {
                String bn = bm.getName();
                if ((bn.equals("onPress") || bn.equals("onClick") || bn.equals("pressAction"))
                        && bm.getParameterCount() <= 2) {
                    try {
                        bm.setAccessible(true);
                        Class<?>[] bpt = bm.getParameterTypes();
                        Object[] bargs = new Object[bpt.length];
                        for (int bi = 0; bi < bpt.length; bi++) {
                            if (bpt[bi] == double.class) bargs[bi] = 0.0;
                            else if (bpt[bi] == float.class) bargs[bi] = 0.0f;
                            else if (bpt[bi] == int.class) bargs[bi] = 0;
                            else if (bpt[bi] == long.class) bargs[bi] = 0L;
                            else if (bpt[bi] == boolean.class) bargs[bi] = false;
                            else if (bpt[bi].isInstance(widget)) bargs[bi] = widget;
                            else bargs[bi] = null;
                        }
                        bm.invoke(widget, bargs);
                        return "{\"clicked\":true,\"index\":" + index + ",\"method\":\"" + bn + "\",\"class\":\"" + widget.getClass().getSimpleName() + "\"}";
                    } catch (Exception e) { dbg("btn invoke fail " + bn + ": " + e.getMessage()); }
                }
            }
            return "{\"error\":\"no press method on index " + index + " (" + widget.getClass().getSimpleName() + ")\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String callScreenMethod(Object mc, String methodName) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen == null) return "{\"error\":\"no screen\"}";
            String sn = screen.getClass().getSimpleName();
            if (methodName.equals("*") || methodName.equals("__list__")) {
                StringBuilder sb = new StringBuilder("{\"screen\":\"" + sn + "\",\"methods\":[");
                boolean first = true;
                for (Method m : getAllMethods(screen.getClass())) {
                    if (java.lang.reflect.Modifier.isPublic(m.getModifiers()) && m.getParameterCount() <= 1) {
                        if (first) first = false; else sb.append(",");
                        sb.append("\"").append(m.getName()).append("(").append(m.getParameterCount()).append(")\"");
                    }
                }
                sb.append("]}");
                return sb.toString();
            }
            for (Method m : getAllMethods(screen.getClass())) {
                if (m.getName().equals(methodName) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    Object result = m.invoke(screen);
                    return "{\"called\":true,\"screen\":\"" + sn + "\",\"method\":\"" + methodName + "\",\"result\":" + (result == null ? "null" : "\"" + result + "\"") + "}";
                }
            }
            StringBuilder available = new StringBuilder();
            for (Method m : getAllMethods(screen.getClass())) {
                if (m.getName().toLowerCase().contains(methodName.toLowerCase())
                        && m.getParameterCount() <= 1
                        && java.lang.reflect.Modifier.isPublic(m.getModifiers())) {
                    if (available.length() > 0) available.append(",");
                    available.append(m.getName()).append("(").append(m.getParameterCount()).append(")");
                }
            }
            return "{\"error\":\"method '" + methodName + "' not found on " + sn + "\",\"candidates\":[" + available.toString() + "]}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String guiKeyPress(Object mc, int keyCode, int scanCode, int action, int modifiers) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen != null) {
                for (Method m : getAllMethods(screen.getClass())) {
                    String n = m.getName();
                    if ((n.equals("keyPressed") || n.equals("func_73864_a") || n.contains("keyPressed"))
                            && m.getParameterCount() >= 3) {
                        try {
                            m.setAccessible(true);
                            Object[] args = new Object[m.getParameterCount()];
                            args[0] = keyCode;
                            args[1] = scanCode;
                            args[2] = modifiers;
                            for (int i = 3; i < args.length; i++) args[i] = 0;
                            Object result = m.invoke(screen, args);
                            return "{\"keyPressed\":true,\"result\":" + result + "}";
                        } catch (Exception ignored) {}
                    }
                }
            }
            Object kbHandler = null;
            try { kbHandler = mc.getClass().getField("keyboardHandler").get(mc); } catch (Exception ignored) {}
            if (kbHandler == null) {
                try { kbHandler = mc.getClass().getMethod("keyboardHandler").invoke(mc); } catch (Exception ignored) {}
            }
            if (kbHandler != null) {
                for (Method m : kbHandler.getClass().getDeclaredMethods()) {
                    if (m.getName().equals("keyPress") && m.getParameterCount() == 5) {
                        long handle = 0;
                        try { handle = getWindowHandle(mc); } catch (Exception ignored) {}
                        dbg("guiKeyPress: keyPress(" + handle + "," + keyCode + "," + scanCode + "," + action + "," + modifiers + ") kb=" + kbHandler.getClass().getSimpleName());
                        m.setAccessible(true);
                        m.invoke(kbHandler, handle, keyCode, scanCode, action, modifiers);
                        return "{\"keyPressed\":true,\"via\":\"keyboardHandler\"}";
                    }
                }
            }
            return "{\"error\":\"no key input method found\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String guiCharType(Object mc, char ch, int modifiers) {
        try {
            Object screen = getCurrentScreen(mc);
            dbg("guiCharType: ch=" + ch + " screen=" + (screen != null ? screen.getClass().getSimpleName() : "null"));
            if (screen != null) {
                for (Method m : getAllMethods(screen.getClass())) {
                    String n = m.getName();
                    Class<?>[] pt = m.getParameterTypes();
                    if (m.getParameterCount() == 2 && pt[0] == char.class && pt[1] == int.class) {
                        try {
                            m.setAccessible(true);
                            m.invoke(screen, ch, modifiers);
                            return "{\"charTyped\":true,\"method\":\"" + n + "\"}";
                        } catch (Exception e) {
                            dbg("guiCharType: " + n + " failed: " + e.getMessage());
                        }
                    }
                }
                String charStr = String.valueOf(ch);
                for (Method m : getAllMethods(screen.getClass())) {
                    String n = m.getName();
                    Class<?>[] pt = m.getParameterTypes();
                    if ((n.equals("insertText") || n.equals("charTyped")) && m.getParameterCount() == 2
                            && pt[0] == String.class && pt[1] == boolean.class) {
                        try {
                            m.setAccessible(true);
                            m.invoke(screen, charStr, false);
                            return "{\"charTyped\":true,\"method\":\"" + n + "(String)\"}";
                        } catch (Exception e) {
                            dbg("guiCharType: " + n + "(String) failed: " + e.getMessage());
                        }
                    }
                }
            }
            Object kbHandler = null;
            try { kbHandler = mc.getClass().getField("keyboardHandler").get(mc); } catch (Exception ignored) {}
            if (kbHandler == null) {
                try { kbHandler = mc.getClass().getMethod("keyboardHandler").invoke(mc); } catch (Exception ignored) {}
            }
            if (kbHandler != null) {
                long handle = getWindowHandle(mc);
                for (Method m : kbHandler.getClass().getDeclaredMethods()) {
                    String n = m.getName();
                    if (m.getParameterCount() >= 2 && m.getParameterTypes()[0] == long.class
                            && m.getParameterTypes()[1] == char.class) {
                        try {
                            m.setAccessible(true);
                            if (m.getParameterCount() == 3) m.invoke(kbHandler, handle, ch, modifiers);
                            else m.invoke(kbHandler, handle, ch);
                            return "{\"charTyped\":true,\"via\":\"kb." + n + "\"}";
                        } catch (Exception ignored) {}
                    }
                }
                for (Method m : kbHandler.getClass().getDeclaredMethods()) {
                    String n = m.getName().toLowerCase();
                    if (m.getParameterCount() == 2 && m.getParameterTypes()[0] == char.class
                            && (n.contains("char") || n.contains("type"))) {
                        try {
                            m.setAccessible(true);
                            m.invoke(kbHandler, ch, modifiers);
                            return "{\"charTyped\":true,\"via\":\"kb2." + m.getName() + "\"}";
                        } catch (Exception ignored) {}
                    }
                }
            }
            return "{\"error\":\"no charTyped method\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private static Object getCurrentScreen(Object mc) throws Exception {
        for (Field f : getAllFields(mc.getClass())) {
            String n = f.getName();
            if (n.equals("currentScreen") || n.equals("screen") || n.equals("field_71462_r") || n.equals("field_175283_aN")) {
                try { f.setAccessible(true); return f.get(mc); } catch (Exception ignored) {}
            }
        }
        try { return mc.getClass().getMethod("screen").invoke(mc); } catch (Exception ignored) {}
        return null;
    }

    public static double getGuiScale(Object mc) {
        try {
            Object gs = null;
            for (Field f : getAllFields(mc.getClass())) {
                String n = f.getName();
                if (n.contains("guiScale") || n.equals("field_85159_q") || n.equals("field_82502_R")) {
                    try { f.setAccessible(true); gs = f.get(mc); break; } catch (Exception ignored) {}
                }
            }
            if (gs instanceof Number) return ((Number) gs).doubleValue();
            // Try GameSettings
            for (Field f : getAllFields(mc.getClass())) {
                String tn = f.getType().getSimpleName();
                if (tn.contains("GameSettings") || tn.contains("Settings") || tn.equals("field_71450_a")) {
                    try {
                        f.setAccessible(true);
                        Object settings = f.get(mc);
                        if (settings != null) {
                            for (Field sf : getAllFields(settings.getClass())) {
                                if (sf.getName().contains("guiScale") || sf.getName().equals("field_85159_q")) {
                                    sf.setAccessible(true);
                                    Object v = sf.get(settings);
                                    if (v instanceof Number) return ((Number) v).doubleValue();
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) { dbg("getGuiScale err: " + e.getMessage()); }
        return 2.0; // default Normal scale
    }

    private static final Map<Class<?>, List<Method>> methodCache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, List<Field>> fieldCache = new ConcurrentHashMap<>();

    private static List<Method> getAllMethods(Class<?> clazz) {
        if (clazz.getName().startsWith("org.lwjgl.") || clazz.getName().startsWith("com.mojang.blaze3d.")) {
            List<Method> methods = new java.util.ArrayList<>();
            Class<?> cur = clazz;
            while (cur != null && cur != Object.class) {
                for (Method m : cur.getDeclaredMethods()) methods.add(m);
                cur = cur.getSuperclass();
            }
            return methods;
        }
        return methodCache.computeIfAbsent(clazz, c -> {
            List<Method> methods = new java.util.ArrayList<>();
            Class<?> cur = c;
            while (cur != null && cur != Object.class) {
                for (Method m : cur.getDeclaredMethods()) methods.add(m);
                cur = cur.getSuperclass();
            }
            return methods;
        });
    }

    private static Object castParam(Class<?> type, double value) {
        if (type == int.class) return (int) value;
        if (type == long.class) return (long) value;
        if (type == float.class) return (float) value;
        if (type == double.class) return value;
        if (type == short.class) return (short) value;
        if (type == byte.class) return (byte) value;
        return value;
    }

    private static Object defaultParam(Class<?> type) {
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

    public static String getWorldInfo(Object mc) {
        try {
            Object level = getLevel(mc);
            if (level == null) return "{\"world_name\":null}";
            String worldName = "unknown";
            String difficulty = "normal";
            String gameType = "survival";

            Object server = invokeOrNull(level, "getServer");
            if (server != null) {
                Object wd = invokeOrNull(server, "getWorldData");
                if (wd != null) worldName = invokeString(wd, "getLevelName");
            }
            difficulty = getDifficultyKey(level);
            gameType = getGameType(mc);

            return String.format("{\"world_name\":\"%s\",\"difficulty\":\"%s\",\"gametype\":\"%s\"}",
                    worldName, difficulty, gameType);
        } catch (Exception e) {
            return "{\"world_name\":null,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String getDimensionId(Object player) throws Exception {
        Object level = getPlayerLevel(player);
        if (level == null) return "overworld";
        Object provider = fieldOrNull(level, "provider");
        if (provider != null) {
            Object dimId = fieldOrNull(provider, "dimensionId");
            if (dimId != null) return String.valueOf(dimId);
        }
        try {
            Object dim = level.getClass().getMethod("dimension").invoke(level);
            try { return (String) dim.getClass().getMethod("identifier").invoke(dim); } catch (NoSuchMethodException ignored) {}
            try { return dim.getClass().getMethod("location").invoke(dim).toString(); } catch (NoSuchMethodException ignored) {}
            try { return dim.getClass().getMethod("getRegistryName").invoke(dim).toString(); } catch (NoSuchMethodException ignored) {}
        } catch (NoSuchMethodException ignored) {}
        return "overworld";
    }

    public static String getDifficultyKey(Object level) throws Exception {
        Object diff = level.getClass().getMethod("getDifficulty").invoke(level);
        try { return (String) diff.getClass().getMethod("getSerializedName").invoke(diff); } catch (NoSuchMethodException ignored) {}
        try { return (String) diff.getClass().getMethod("getName").invoke(diff); } catch (NoSuchMethodException ignored) {}
        try { return ((Enum<?>) diff).name().toLowerCase(); } catch (ClassCastException ignored) {}
        return "normal";
    }

    public static String getGameType(Object mc) throws Exception {
        try {
            Object gm = mc.getClass().getMethod("gameMode").invoke(mc);
            Object pt = gm.getClass().getMethod("getPlayerMode").invoke(gm);
            return (String) pt.getClass().getMethod("getName").invoke(pt);
        } catch (NoSuchMethodException e) { return "survival"; }
    }

    public static String sendCommand(Object mc, String cmd) {
        try {
            Object player = getPlayer(mc);
            if (player == null) return "{\"error\":\"no player\"}";
            String msg = cmd.startsWith("/") ? cmd : "/" + cmd;
            // Try sendChatMessage on player (works in most versions)
            for (Method m : getAllMethods(player.getClass())) {
                if ((m.getName().equals("sendChatMessage") || m.getName().contains("sendChat") || m.getName().contains("func_146158_b"))
                        && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) {
                    try { m.setAccessible(true); m.invoke(player, msg); return "{\"sent\":true,\"method\":\"" + m.getName() + "\"}"; }
                    catch (Exception ignored) {}
                }
            }
            // Try connection-based approach: get connection, send CPacketChatMessage
            Object conn = null;
            try { conn = player.getClass().getMethod("connection").invoke(player); }
            catch (NoSuchMethodException ignored) {
                conn = fieldOrNull(player, "connection");
                if (conn == null) conn = fieldOrNull(player, "field_71174_a");
            }
            if (conn != null) {
                dbg("sendCommand: found connection " + conn.getClass().getName());
                for (Method m : getAllMethods(conn.getClass())) {
                    String mn = m.getName().toLowerCase();
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class
                            && (mn.contains("chat") || mn.contains("send") || mn.contains("command"))) {
                        try { m.setAccessible(true); m.invoke(conn, msg); return "{\"sent\":true,\"method\":\"conn." + m.getName() + "\"}"; }
                        catch (Exception ignored) {}
                    }
                }
                for (Method m : getAllMethods(conn.getClass())) {
                    if ((m.getName().equals("sendPacket") || m.getName().contains("sendPacket") || m.getName().contains("func_147297_a"))
                            && m.getParameterCount() == 1) {
                        try {
                            for (String pktName : new String[]{
                                "net.minecraft.network.play.client.CPacketChatMessage",
                                "net.minecraft.network.protocol.game.ServerboundChatPacket",
                                "net.minecraft.network.protocol.game.ServerboundChatCommandPacket"
                            }) {
                                try {
                                    Class<?> pktClass = Class.forName(pktName);
                                    Object packet = pktClass.getConstructor(String.class).newInstance(msg);
                                    m.setAccessible(true); m.invoke(conn, packet);
                                    return "{\"sent\":true,\"method\":\"packet:" + pktName + "\"}";
                                } catch (ClassNotFoundException ignored2) {}
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            // Last resort: try any single-String method on player that looks command-like
            for (Method m : getAllMethods(player.getClass())) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class
                        && (m.getName().toLowerCase().contains("chat") || m.getName().toLowerCase().contains("command")
                            || m.getName().toLowerCase().contains("message") || m.getName().toLowerCase().contains("send"))) {
                    try { m.setAccessible(true); m.invoke(player, msg); return "{\"sent\":true,\"method\":\"" + m.getName() + "\"}"; }
                    catch (Exception ignored) {}
                }
            }
            return "{\"error\":\"no command method found\",\"player_class\":\"" + player.getClass().getName() + "\"}";
        } catch (Exception e) { return "{\"error\":\"" + e.getMessage() + "\"}"; }
    }

    public static byte[] takeScreenshot(Object mc, int width, int height) {
        screenshotInProgress = true;
        try {
            return takeScreenshot0(mc, width, height);
        } finally {
            screenshotInProgress = false;
        }
    }

    private static void forceRenderOneFrame(Object mc) {
        try {
            Object gameRenderer = null;
            for (Method m : mc.getClass().getMethods()) {
                if (m.getName().equals("gameRenderer") && m.getParameterCount() == 0) {
                    m.setAccessible(true); gameRenderer = m.invoke(mc); break;
                }
            }
            if (gameRenderer == null) {
                for (Field f : getAllFields(mc.getClass())) {
                    if (f.getName().equals("gameRenderer")) { f.setAccessible(true); gameRenderer = f.get(mc); break; }
                }
            }
            if (gameRenderer == null) { dbg("forceRender: no gameRenderer"); return; }
            for (Method m : getAllMethods(gameRenderer.getClass())) {
                if (m.getName().equals("render") && m.getParameterCount() == 2) {
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts[0] == float.class && pts[1] == long.class) {
                        m.setAccessible(true); m.invoke(gameRenderer, 1.0f, System.nanoTime());
                        dbg("forceRender: called GameRenderer.render()"); return;
                    }
                }
            }
            dbg("forceRender: render(float,long) not found");
        } catch (Exception e) { dbg("forceRender: " + e.getMessage()); }
    }

    private static byte[] takeScreenshot0(Object mc, int width, int height) {
        byte[] cached = cachedScreenshot;
        if (cached != null && System.currentTimeMillis() - cachedScreenshotTime < 2000) {
            dbg("takeScreenshot: returning cached screenshot " + cached.length + " bytes (age " + (System.currentTimeMillis() - cachedScreenshotTime) + "ms)");
            return cached;
        }
        dbg("takeScreenshot: no recent cached screenshot, forcing render + reading on render thread");
        final int w = width, h = height;
        final byte[][] resultHolder = new byte[1][];
        final CountDownLatch latch = new CountDownLatch(1);
        Runnable task = new Runnable() {
            public void run() {
                try {
                    dbg("takeScreenshot0: on thread " + Thread.currentThread().getName());
                    forceRenderOneFrame(mc);
                    try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                    suppressGlDebug(true);
                    byte[] r;
                    try { r = takeGlScreenshot0(mc, w, h); } finally { suppressGlDebug(false); }
                    resultHolder[0] = r;
                    if (r != null) {
                        cachedScreenshot = r;
                        cachedScreenshotTime = System.currentTimeMillis();
                        dbg("takeScreenshot0: captured " + r.length + " bytes via forceRender");
                    }
                } catch (Exception e) { dbg("takeScreenshot0: " + e.getMessage()); }
                latch.countDown();
            }
        };
        boolean isSameThread = false;
        for (Method m : getAllMethods(mc.getClass())) {
            if (m.getName().equals("isSameThread") && m.getParameterCount() == 0) {
                try { m.setAccessible(true); isSameThread = (boolean) m.invoke(mc); } catch (Exception ignored) {}
                break;
            }
        }
        if (isSameThread) {
            task.run();
        } else {
            for (Method m : mc.getClass().getMethods()) {
                if (m.getName().equals("execute") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == Runnable.class) {
                    try { m.setAccessible(true); m.invoke(mc, task); } catch (Exception e) { dbg("takeScreenshot0: execute failed: " + e.getMessage()); return null; }
                    break;
                }
            }
        }
        try { latch.await(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        return resultHolder[0];
    }

    private static byte[] takeScreenshotOnMainThread(Object mc, int width, int height) throws Exception {
        final int w = width, h = height;
        final byte[][] resultHolder = new byte[1][];
        final Exception[] errorHolder = new Exception[1];
        final CountDownLatch latch = new CountDownLatch(1);
        Runnable capturer = new Runnable() {
            public void run() {
                dbg("takeScreenshotOnMainThread: capturer running on thread: " + Thread.currentThread().getName());
                try {
                    Object rt = null;
                    try { Method m = mc.getClass().getMethod("getMainRenderTarget"); rt = m.invoke(mc); } catch (Exception ignored) {}
                    int texId = 0, fboId = 0;
                    if (rt != null) {
                        for (Field f : getAllFields(rt.getClass())) { 
                            f.setAccessible(true);
                            Object val = f.get(rt);
                            if (val != null && val.getClass().getName().contains("GlTexture")) {
                                for (Field idf : getAllFields(val.getClass())) {
                                    if (idf.getName().equals("id") && idf.getType() == int.class) { idf.setAccessible(true); int tid = idf.getInt(val); if (tid > 0) texId = tid; }
                                }
                                for (Method gm : val.getClass().getMethods()) {
                                    if (gm.getName().equals("getFbo") && gm.getParameterCount() == 2) {
                                        try { Object dsa = resolveDSA(); Field df = null;
                                            for (Field df2 : getAllFields(rt.getClass())) { if (df2.getName().contains("depth") && df2.getName().contains("Texture")) { df2.setAccessible(true); df = df2; break; } }
                                            Object dt = df != null ? df.get(rt) : null;
                                            if (dsa != null) { int fb = (Integer) gm.invoke(val, dsa, dt); if (fb > 0) fboId = fb; }
                                        } catch (Exception ignored) {}
                                        break;
                                    }
                                }
                                break;
                            }
                        }
                    }
                    Class<?> gl11 = Class.forName("org.lwjgl.opengl.GL11");
                    int GL_RGBA = gl11.getDeclaredField("GL_RGBA").getInt(null), GL_UB = gl11.getDeclaredField("GL_UNSIGNED_BYTE").getInt(null);
                    for (Method m : gl11.getMethods()) { if (m.getName().equals("glFinish") && m.getParameterCount() == 0) { try { m.setAccessible(true); m.invoke(null); } catch (Exception ignored) {} break; } }
                    dbg("mainThread: texId=" + texId + " fboId=" + fboId);
                    byte[] texResult = null;
                    if (texId > 0) {
                        try { texResult = readTextureViaGetTexImage(texId, w, h); } catch (Exception e) { dbg("mainThread: glGetTexImage failed: " + e.getMessage()); }
                    }
                    if (texResult != null) { resultHolder[0] = texResult; return; }
                    ByteBuffer bb = ByteBuffer.allocateDirect(w * h * 4);
                    boolean ok = false;
                    if (fboId > 0) {
                        try {
                            Class<?> gl30 = Class.forName("org.lwjgl.opengl.GL30");
                            int GL_READ_FB = gl30.getDeclaredField("GL_READ_FRAMEBUFFER").getInt(null);
                            for (Method m : gl30.getMethods()) { if (m.getName().equals("glBindFramebuffer") && m.getParameterCount() == 2) { m.setAccessible(true); m.invoke(null, GL_READ_FB, fboId); break; } }
                        } catch (Exception ignored) {}
                    }
                    for (Method m : gl11.getMethods()) {
                        if (m.getName().equals("glReadPixels") && m.getParameterCount() == 7) {
                            Class<?>[] pts = m.getParameterTypes();
                            if (pts[6] == java.nio.ByteBuffer.class) { m.setAccessible(true); m.invoke(null, 0, 0, w, h, GL_RGBA, GL_UB, bb); ok = true; break; }
                        }
                    }
                    if (!ok) throw new RuntimeException("glReadPixels not found on main thread");
                    bb.rewind(); int[] raw = new int[w * h];
                    for (int i = 0; i < raw.length; i++) { int r = bb.get() & 0xFF, g = bb.get() & 0xFF, b = bb.get() & 0xFF, a = bb.get() & 0xFF; raw[i] = (a << 24) | (r << 16) | (g << 8) | b; }
                    int[] flipped = new int[w * h];
                    for (int y2 = 0; y2 < h; y2++) for (int x2 = 0; x2 < w; x2++) flipped[y2 * w + x2] = raw[(h - 1 - y2) * w + x2];
                    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); img.setRGB(0, 0, w, h, flipped, 0, w);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream(); ImageIO.write(img, "png", baos);
                    resultHolder[0] = baos.toByteArray();
                } catch (Exception e) { errorHolder[0] = e; }
                finally { latch.countDown(); }
            }
        };
        Method execMethod = null;
        String threadName = Thread.currentThread().getName();
        dbg("takeScreenshotOnMainThread: current thread=" + threadName);
        boolean isSameThread = false;
        for (Method m : getAllMethods(mc.getClass())) {
            if (m.getName().equals("isSameThread") && m.getParameterCount() == 0) {
                try { m.setAccessible(true); isSameThread = (boolean) m.invoke(mc); } catch (Exception ignored) {}
                break;
            }
        }
        dbg("takeScreenshotOnMainThread: isSameThread=" + isSameThread);
        if (isSameThread) {
            dbg("takeScreenshotOnMainThread: already on main thread, running directly");
            capturer.run();
        } else {
            for (Method m : mc.getClass().getMethods()) { if (m.getName().equals("execute") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == Runnable.class) { execMethod = m; break; } }
            if (execMethod == null) {
                for (Method m : getAllMethods(mc.getClass())) {
                    if (m.getName().equals("execute") && m.getParameterCount() == 1) {
                        Class<?> pt = m.getParameterTypes()[0];
                        if (pt == Runnable.class || pt.getName().equals("java.lang.Runnable")) { execMethod = m; break; }
                    }
                }
            }
            if (execMethod == null) throw new RuntimeException("no execute(Runnable) method found");
            dbg("takeScreenshotOnMainThread: found execute method: " + execMethod);
            execMethod.setAccessible(true); execMethod.invoke(mc, capturer);
        }
        if (!latch.await(5, TimeUnit.SECONDS)) throw new RuntimeException("main thread screenshot timeout");
        if (errorHolder[0] != null) throw errorHolder[0];
        if (resultHolder[0] == null || resultHolder[0].length == 0) return null;
        return resultHolder[0];
    }

    private static byte[] takeMcNativeScreenshot(Object mc) {
        try {
            Class<?> screenshotClass;
            try { screenshotClass = Class.forName("net.minecraft.client.Screenshot"); }
            catch (ClassNotFoundException e) { return null; }

            Object renderTarget;
            try { renderTarget = mc.getClass().getMethod("getMainRenderTarget").invoke(mc); }
            catch (Exception e) { System.err.println("[MCP-Native] no getMainRenderTarget: " + e.getMessage()); return null; }
            if (renderTarget == null) { System.err.println("[MCP-Native] renderTarget is null"); return null; }

            Class<?> nativeImageClass;
            try { nativeImageClass = Class.forName("com.mojang.blaze3d.platform.NativeImage"); }
            catch (ClassNotFoundException e) { return null; }

            Method takeScreenshot = null;
            for (Method m : screenshotClass.getDeclaredMethods()) {
                if (m.getName().equals("takeScreenshot") && m.getParameterCount() == 2
                        && m.getParameterTypes()[1] == java.util.function.Consumer.class) {
                    takeScreenshot = m;
                    break;
                }
            }
            if (takeScreenshot == null) return null;

            final Object[] imageHolder = new Object[1];
            final Exception[] errorHolder = new Exception[1];
            final Class<?> niClass = nativeImageClass;
            java.util.function.Consumer<Object> consumer = new java.util.function.Consumer<Object>() {
                @Override
                public void accept(Object nativeImage) {
                    try {
                        java.io.File tmpFile = java.io.File.createTempFile("mcp_screenshot_", ".png");
                        tmpFile.deleteOnExit();
                        niClass.getMethod("writeToFile", java.io.File.class).invoke(nativeImage, tmpFile);
                        byte[] data = new byte[(int) tmpFile.length()];
                        java.io.FileInputStream fis = new java.io.FileInputStream(tmpFile);
                        try { fis.read(data); } finally { fis.close(); }
                        tmpFile.delete();
                        imageHolder[0] = data;
        } catch (Exception e) {
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            throw new RuntimeException("glReadPixels failed: " + cause.getClass().getSimpleName() + ": " + cause.getMessage(), e);
        }
                }
            };

            takeScreenshot.setAccessible(true);
            takeScreenshot.invoke(null, renderTarget, consumer);

            if (errorHolder[0] != null) {
                Throwable err = errorHolder[0];
                System.err.println("[MCP-Native] consumer error: " + err.getMessage());
                while (err.getCause() != null) { err = err.getCause(); }
                System.err.println("[MCP-Native] root cause: " + err.getClass().getName() + ": " + err.getMessage());
                dbg("takeScreenshot: MC native CONSUMER ERROR - " + errorHolder[0].getMessage());
                return null;
            }
            return (byte[]) imageHolder[0];
        } catch (Exception e) {
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            System.err.println("[MCP-Native] failed: " + e.getMessage() + " root=" + cause.getClass().getName() + ":" + cause.getMessage());
            dbg("takeScreenshot: MC native EXCEPTION - " + e.getMessage());
            return null;
        }
    }

    private static byte[] takeGlScreenshot(Object mc, int width, int height) throws Exception {
        return takeGlScreenshot0(mc, width, height);
    }

    private static byte[] takeGlScreenshot0(Object mc, int width, int height) throws Exception {
        if (width <= 0 || height <= 0) throw new RuntimeException("bad dims " + width + "x" + height);
        Object fb = null;
        try { Method m = mc.getClass().getMethod("getMainRenderTarget"); fb = m.invoke(mc); } catch (NoSuchMethodException e) {}
        int colorTexId = 0;
        int fboId = 0;
        if (fb != null) {
            try {
                for (Field f : getAllFields(fb.getClass())) {
                    String fn = f.getName(); f.setAccessible(true);
                    if (f.getType() == int.class) {
                        int fv = f.getInt(fb);
                        if ((fn.equals("frameBufferId")||fn.equals("fbo")||fn.equals("framebuffer")||fn.contains("Fbo")||fn.contains("frameBuffer")) && fv > 0) fboId = fv;
                    }
                }
                if (fboId == 0) {
                    for (Field cf : getAllFields(fb.getClass())) {
                        if (java.lang.reflect.Modifier.isStatic(cf.getModifiers())) continue;
                        cf.setAccessible(true); Object colorTex = cf.get(fb);
                        if (colorTex == null) continue;
                        Class<?> texClass = colorTex.getClass();
                        if (texClass.getName().contains("GpuTexture") || texClass.getName().contains("GlTexture")) {
                            for (Field idf : getAllFields(texClass)) {
                                if (idf.getName().equals("id") && idf.getType() == int.class) {
                                    idf.setAccessible(true); int tid = idf.getInt(colorTex);
                                    if (tid > 0) colorTexId = tid;
                                }
                            }
                            for (Method gm : texClass.getMethods()) {
                                if (gm.getName().equals("getFbo") && gm.getParameterCount() == 2) {
                                    try {
                                        Object dsa = resolveDSA();
                                        Field depthField = null;
                                        for (Field df2 : getAllFields(fb.getClass())) {
                                            if (df2.getName().contains("depth") && df2.getName().contains("Texture")) { df2.setAccessible(true); depthField = df2; break; }
                                        }
                                        Object depthTex = depthField != null ? depthField.get(fb) : null;
                                        if (dsa != null) { int fbo = (Integer) gm.invoke(colorTex, dsa, depthTex); if (fbo > 0) fboId = fbo; }
                                    } catch (Exception ex) { dbg("takeGl: getFbo failed: " + ex.getMessage()); }
                                    break;
                                }
                            }
                            break;
                        }
                    }
                }
                dbgR("takeGl: fboId=" + fboId + " texId=" + colorTexId);
            } catch (Exception e) { dbg("takeGl: failed to get fboId: " + e.getMessage()); }
        }
        if (fb != null) {
            try {
                Field fw = fb.getClass().getDeclaredField("width"); fw.setAccessible(true); int fbw = fw.getInt(fb);
                Field fh = fb.getClass().getDeclaredField("height"); fh.setAccessible(true); int fbh = fh.getInt(fb);
                if (fbw > 0 && fbh > 0) { width = fbw; height = fbh; }
            } catch (Exception ignored) {}
        }
        int w = width, h = height;
        if (fboId > 0) {
            try { byte[] r = readFboDirect(fboId, w, h, fb); if (r != null) return r; }
            catch (Exception ex) { dbg("takeGl: FBO direct read failed: " + ex.getMessage()); }
        }
        if (colorTexId > 0) {
            try { byte[] r = readTextureViaGetTexImage(colorTexId, w, h); if (r != null) return r; }
            catch (Exception ex) { dbg("takeGl: glGetTexImage failed: " + ex.getMessage()); }
        }
        ByteBuffer bb = ByteBuffer.allocateDirect(w * h * 4);
        if (fb != null) {
            try { for (Method bm : fb.getClass().getMethods()) { if (bm.getName().equals("blitToScreen") && bm.getParameterCount() == 0) { bm.setAccessible(true); bm.invoke(fb); break; } } }
            catch (Exception ignored) {}
        }
        doGlReadPixels(0, 0, w, h, bb, fboId);
        bb.rewind(); int[] raw = new int[w * h];
        for (int i = 0; i < raw.length; i++) { int r = bb.get() & 0xFF, g = bb.get() & 0xFF, b = bb.get() & 0xFF, a = bb.get() & 0xFF; raw[i] = (a << 24) | (r << 16) | (g << 8) | b; }
        int[] flipped = new int[w * h];
        for (int y2 = 0; y2 < h; y2++) for (int x2 = 0; x2 < w; x2++) flipped[y2 * w + x2] = raw[(h - 1 - y2) * w + x2];
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); img.setRGB(0, 0, w, h, flipped, 0, w);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos); byte[] result = baos.toByteArray();
        if (result.length == 0) throw new RuntimeException("empty PNG w=" + w + " h=" + h);
        return result;
    }

    private static Object resolveDSA() {
        try {
            Class<?> dsaCore = Class.forName("com.mojang.blaze3d.opengl.DirectStateAccess$Core");
            for (Field f : dsaCore.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    try { f.setAccessible(true); Object v = f.get(null); if (v != null) return v; } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        try {
            Class<?> dsaEmu = Class.forName("com.mojang.blaze3d.opengl.DirectStateAccess$Emulated");
            for (Field f : dsaEmu.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    try { f.setAccessible(true); Object v = f.get(null); if (v != null) return v; } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        try {
            Class<?> dsaIface = Class.forName("com.mojang.blaze3d.opengl.DirectStateAccess");
            for (Method m : dsaIface.getMethods()) {
                if ((m.getName().equals("getInstance") || m.getName().equals("instance")) && m.getParameterCount() == 0) {
                    try { m.setAccessible(true); return m.invoke(null); } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        try {
            Object renderSystem = null;
            for (Method m : Class.forName("com.mojang.blaze3d.systems.RenderSystem").getMethods()) {
                if (m.getName().equals("getDevice") && m.getParameterCount() == 0) {
                    try { m.setAccessible(true); renderSystem = m.invoke(null); break; } catch (Exception ignored) {}
                }
            }
            if (renderSystem != null) {
                for (Method m : renderSystem.getClass().getMethods()) {
                    if (m.getName().equals("directStateAccess") && m.getParameterCount() == 0) {
                        try { m.setAccessible(true); return m.invoke(renderSystem); } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static byte[] pixelsToPng(ByteBuffer bb, int w, int h) throws Exception {
        bb.rewind();
        int[] raw = new int[w * h];
        for (int i = 0; i < raw.length; i++) {
            int r = bb.get() & 0xFF;
            int g = bb.get() & 0xFF;
            int b = bb.get() & 0xFF;
            int a = bb.get() & 0xFF;
            raw[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        int[] flipped = new int[w * h];
        for (int y2 = 0; y2 < h; y2++) {
            for (int x2 = 0; x2 < w; x2++) {
                flipped[y2 * w + x2] = raw[(h - 1 - y2) * w + x2];
            }
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, flipped, 0, w);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        byte[] result = baos.toByteArray();
        if (result.length == 0) throw new RuntimeException("empty PNG w=" + w + " h=" + h);
        return result;
    }

    private static byte[] readFboDirect(int fboId, int w, int h, Object fb) throws Exception {
        if (!LWJGL3) { try { Class.forName("org.lwjgl.opengl.Display").getMethod("makeCurrent").invoke(null); } catch (Exception ignored) {} }
        Class<?> gl11 = Class.forName("org.lwjgl.opengl.GL11");
        int GL_RGBA = gl11.getDeclaredField("GL_RGBA").getInt(null);
        int GL_UB = gl11.getDeclaredField("GL_UNSIGNED_BYTE").getInt(null);
        int GL_FRONT = 0x0404;
        for (Method m : gl11.getMethods()) { if (m.getName().equals("glFinish") && m.getParameterCount() == 0) { try { m.setAccessible(true); m.invoke(null); } catch (Exception ignored) {}; break; } }
        for (Method m : gl11.getMethods()) {
            if (m.getName().equals("glReadBuffer") && m.getParameterCount() == 1) { try { m.setAccessible(true); m.invoke(null, GL_FRONT); } catch (Exception ignored) {}; break; }
        }
        for (Method m : gl11.getMethods()) { if (m.getName().equals("glFinish") && m.getParameterCount() == 0) { try { m.setAccessible(true); m.invoke(null); } catch (Exception ignored) {}; break; } }
        ByteBuffer bb = ByteBuffer.allocateDirect(w * h * 4);
        boolean ok = false;
        for (Method m : gl11.getMethods()) {
            if (m.getName().equals("glReadPixels") && m.getParameterCount() == 7) {
                Class<?>[] pts = m.getParameterTypes();
                if (pts[6] == java.nio.ByteBuffer.class) { m.setAccessible(true); m.invoke(null, 0, 0, w, h, GL_RGBA, GL_UB, bb); ok = true; break; }
            }
        }
        if (!ok) throw new RuntimeException("glReadPixels not found");
        bb.rewind(); int[] raw = new int[w * h];
        for (int i = 0; i < raw.length; i++) { int r = bb.get() & 0xFF, g = bb.get() & 0xFF, b = bb.get() & 0xFF, a = bb.get() & 0xFF; raw[i] = (a << 24) | (r << 16) | (g << 8) | b; }
        int[] flipped = new int[w * h];
        for (int y2 = 0; y2 < h; y2++) for (int x2 = 0; x2 < w; x2++) flipped[y2 * w + x2] = raw[(h - 1 - y2) * w + x2];
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); img.setRGB(0, 0, w, h, flipped, 0, w);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(); ImageIO.write(img, "png", baos); byte[] result = baos.toByteArray();
        if (result.length == 0) throw new RuntimeException("empty PNG from FBO read"); return result;
    }

    private static long getAddressOfBuffer(ByteBuffer bb) throws Exception {
        try {
            Class<?> muClass = Class.forName("org.lwjgl.system.MemoryUtil");
            for (Method m : muClass.getMethods()) {
                if (m.getName().equals("memAddress") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == java.nio.ByteBuffer.class) {
                    m.setAccessible(true);
                    return (Long) m.invoke(null, bb);
                }
            }
        } catch (Exception ignored) {}
        throw new RuntimeException("Cannot get buffer address: MemoryUtil.memAddress not found");
    }

    private static void suppressGlDebug(boolean suppress) {
        try {
            Class<?> gl43 = Class.forName("org.lwjgl.opengl.GL43");
            int GL_DEBUG_OUTPUT = gl43.getDeclaredField("GL_DEBUG_OUTPUT").getInt(null);
            Class<?> gl11 = Class.forName("org.lwjgl.opengl.GL11");
            if (suppress) {
                for (Method m : gl11.getMethods()) {
                    if (m.getName().equals("glDisable") && m.getParameterCount() == 1) {
                        m.setAccessible(true); m.invoke(null, GL_DEBUG_OUTPUT); break;
                    }
                }
            } else {
                for (Method m : gl11.getMethods()) {
                    if (m.getName().equals("glEnable") && m.getParameterCount() == 1) {
                        m.setAccessible(true); m.invoke(null, GL_DEBUG_OUTPUT); break;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static byte[] readTextureViaGetTexImageFrom(Class<?> glClass, int texId, int w, int h) throws Exception {
        int GL_TEXTURE_2D = glClass.getDeclaredField("GL_TEXTURE_2D").getInt(null);
        int GL_RGBA = glClass.getDeclaredField("GL_RGBA").getInt(null);
        int GL_UNSIGNED_BYTE = glClass.getDeclaredField("GL_UNSIGNED_BYTE").getInt(null);
        int prevTex = 0;
        for (Method m : glClass.getMethods()) {
            if (m.getName().equals("glGetInteger") && m.getParameterCount() == 1) {
                try { m.setAccessible(true); prevTex = (Integer) m.invoke(null, GL_TEXTURE_2D); } catch (Exception ignored) {}
                break;
            }
        }
        for (Method m : glClass.getMethods()) {
            if (m.getName().equals("glBindTexture") && m.getParameterCount() == 2) {
                try { m.setAccessible(true); m.invoke(null, GL_TEXTURE_2D, texId); } catch (Exception ignored) {}
                break;
            }
        }
        for (Method m : glClass.getMethods()) {
            if (m.getName().equals("glFinish") && m.getParameterCount() == 0) {
                try { m.setAccessible(true); m.invoke(null); } catch (Exception ignored) {}
                break;
            }
        }
        ByteBuffer bb = ByteBuffer.allocateDirect(w * h * 4);
        for (Method m : glClass.getMethods()) {
            if (m.getName().equals("glGetTexImage")) {
                m.setAccessible(true);
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length == 5 && pts[4] == java.nio.ByteBuffer.class) {
                    m.invoke(null, GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, bb);
                } else if (pts.length == 5 && pts[4] == long.class) {
                    m.invoke(null, GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, getAddressOfBuffer(bb));
                } else if (pts.length == 6) {
                    Object[] args = new Object[6];
                    args[0] = GL_TEXTURE_2D; args[1] = 0; args[2] = GL_RGBA; args[3] = GL_UNSIGNED_BYTE;
                    if (pts[4] == int.class) args[4] = 0; else args[4] = 0L;
                    if (pts[5] == java.nio.ByteBuffer.class) args[5] = bb;
                    else if (pts[5] == long.class) args[5] = getAddressOfBuffer(bb);
                    m.invoke(null, args);
                } else if (pts.length == 5 && pts[4].isArray() && pts[4].getComponentType() == float.class) {
                    float[] floatBuf = new float[w * h * 4];
                    m.invoke(null, GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, floatBuf);
                    bb = ByteBuffer.allocate(w * h * 4);
                    for (float fv : floatBuf) bb.put((byte) (fv * 255));
                    bb.rewind();
                } else {
                    throw new RuntimeException("glGetTexImage sig mismatch: " + java.util.Arrays.toString(pts));
                }
                for (Method bm : glClass.getMethods()) {
                    if (bm.getName().equals("glBindTexture") && bm.getParameterCount() == 2) {
                        try { bm.setAccessible(true); bm.invoke(null, GL_TEXTURE_2D, prevTex); } catch (Exception ignored) {}
                        break;
                    }
                }
                return pixelsToPng(bb, w, h);
            }
        }
        throw new RuntimeException("glGetTexImage not found in " + glClass.getName());
    }

    private static byte[] readTextureViaGetTexImage(int texId, int w, int h) throws Exception {
        if (!LWJGL3) {
            try {
                Class<?> displayClass = Class.forName("org.lwjgl.opengl.Display");
                try { displayClass.getMethod("makeCurrent").invoke(null); } catch (Exception ignored) {}
            } catch (Exception ignored) {}
        }
        Class<?> gl11 = Class.forName("org.lwjgl.opengl.GL11");
        int GL_TEXTURE_2D = gl11.getDeclaredField("GL_TEXTURE_2D").getInt(null);
        int GL_RGBA = gl11.getDeclaredField("GL_RGBA").getInt(null);
        int GL_UNSIGNED_BYTE = gl11.getDeclaredField("GL_UNSIGNED_BYTE").getInt(null);

        int prevTex = 0;
        for (Method m : gl11.getMethods()) {
            if (m.getName().equals("glGetInteger") && m.getParameterCount() == 1) {
                try { m.setAccessible(true); prevTex = (Integer) m.invoke(null, GL_TEXTURE_2D); } catch (Exception ignored) {}
                break;
            }
        }

        for (Method m : gl11.getMethods()) {
            if (m.getName().equals("glBindTexture") && m.getParameterCount() == 2) {
                try { m.setAccessible(true); m.invoke(null, GL_TEXTURE_2D, texId); } catch (Exception ignored) {}
                break;
            }
        }

        for (Method m : gl11.getMethods()) {
            if (m.getName().equals("glFinish") && m.getParameterCount() == 0) {
                try { m.setAccessible(true); m.invoke(null); } catch (Exception ignored) {}
                break;
            }
        }

        ByteBuffer bb = ByteBuffer.allocateDirect(w * h * 4);
        boolean gotPixels = false;
        for (Method m : gl11.getMethods()) {
            if (m.getName().equals("glGetTexImage")) {
                try {
                    m.setAccessible(true);
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts.length == 5 && pts[4] == java.nio.ByteBuffer.class) {
                        m.invoke(null, GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, bb);
                        gotPixels = true;
                    } else if (pts.length == 5 && pts[4] == long.class) {
                        long addr = getAddressOfBuffer(bb);
                        m.invoke(null, GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, addr);
                        gotPixels = true;
                    } else if (pts.length == 6) {
                        Object[] args = new Object[6];
                        args[0] = GL_TEXTURE_2D; args[1] = 0; args[2] = GL_RGBA; args[3] = GL_UNSIGNED_BYTE;
                        if (pts[4] == int.class) args[4] = 0; else args[4] = 0L;
                        if (pts[5] == java.nio.ByteBuffer.class) args[5] = bb;
                        else if (pts[5] == long.class) args[5] = getAddressOfBuffer(bb);
                        m.invoke(null, args);
                        gotPixels = true;
                    } else {
                        if (pts.length == 5 && pts[4].isArray() && pts[4].getComponentType() == float.class) {
                            float[] floatBuf = new float[w * h * 4];
                            m.invoke(null, GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, floatBuf);
                            bb = ByteBuffer.allocate(w * h * 4);
                            for (float fv : floatBuf) bb.put((byte) (fv * 255));
                            bb.rewind();
                            gotPixels = true;
                        } else {
                            dbgR("readTexImg: glGetTexImage sig mismatch: " + java.util.Arrays.toString(pts));
                        }
                    }
                } catch (Exception e) {
                    dbgR("readTexImg: glGetTexImage failed: " + e.getMessage());
                }
                break;
            }
        }
        if (!gotPixels) throw new RuntimeException("glGetTexImage method not found");

        for (Method m : gl11.getMethods()) {
            if (m.getName().equals("glBindTexture") && m.getParameterCount() == 2) {
                try { m.setAccessible(true); m.invoke(null, GL_TEXTURE_2D, prevTex); } catch (Exception ignored) {}
                break;
            }
        }

        return pixelsToPng(bb, w, h);
    }

    private static void doGlReadPixels(int x, int y, int w, int h, ByteBuffer bb, int fboId) throws Exception {
        if (!LWJGL3) {
            try {
                Class<?> displayClass = Class.forName("org.lwjgl.opengl.Display");
                try { displayClass.getMethod("makeCurrent").invoke(null); } catch (Exception ignored) {}
            } catch (Exception ignored) {}
        }
        Class<?> gl11 = Class.forName("org.lwjgl.opengl.GL11");
        int GL_RGBA = gl11.getDeclaredField("GL_RGBA").getInt(null);
        int GL_UB = gl11.getDeclaredField("GL_UNSIGNED_BYTE").getInt(null);

        for (Method m : gl11.getMethods()) {
            if (m.getName().equals("glFinish") && m.getParameterCount() == 0) {
                try { m.setAccessible(true); m.invoke(null); } catch (Exception ignored) {}
                break;
            }
        }

        if (fboId > 0) {
            try {
                Class<?> gl30 = Class.forName("org.lwjgl.opengl.GL30");
                int GL_READ_FRAMEBUFFER = gl30.getDeclaredField("GL_READ_FRAMEBUFFER").getInt(null);
                for (Method m : gl30.getMethods()) {
                    if (m.getName().equals("glBindFramebuffer") && m.getParameterCount() == 2) {
                        try { m.setAccessible(true); m.invoke(null, GL_READ_FRAMEBUFFER, fboId);
                            } catch (Exception ignored) {}
                        break;
                    }
                }
            } catch (ClassNotFoundException e) {
                try {
                    Class<?> gl21 = Class.forName("org.lwjgl.opengl.GL21");
                    int GL_READ_FRAMEBUFFER = gl21.getDeclaredField("GL_READ_FRAMEBUFFER").getInt(null);
                    for (Method m : gl21.getMethods()) {
                        if (m.getName().equals("glBindFramebuffer") && m.getParameterCount() == 2) {
                            try { m.setAccessible(true); m.invoke(null, GL_READ_FRAMEBUFFER, fboId); } catch (Exception ignored) {}
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }
        } else {
            int GL_BACK = 0x0405;
            for (Method m : gl11.getMethods()) {
                if (m.getName().equals("glReadBuffer") && m.getParameterCount() == 1) {
                    try { m.setAccessible(true); m.invoke(null, GL_BACK); }
                    catch (Exception ignored3) {}
                    break;
                }
            }
        }

        for (Method m : gl11.getMethods()) {
            if (m.getName().equals("glFinish") && m.getParameterCount() == 0) {
                try { m.setAccessible(true); m.invoke(null); } catch (Exception ignored) {}
                break;
            }
        }

        for (Method m : gl11.getMethods()) {
            if (m.getName().equals("glReadPixels") && m.getParameterCount() == 7) {
                Class<?>[] pts = m.getParameterTypes();
                if (pts[6] == java.nio.ByteBuffer.class) {
                    try {
                        m.setAccessible(true);
                        m.invoke(null, x, y, w, h, GL_RGBA, GL_UB, bb);
                        return;
                    } catch (java.lang.reflect.InvocationTargetException ite) {
                        System.err.println("[MCP-GL] ByteBuffer invoke failed: " + ite.getCause());
                        throw ite;
                    }
                }
            }
        }
        throw new NoSuchMethodException("glReadPixels(int,int,int,int,int,int,ByteBuffer)");
    }

    private static Object findAndInvoke(Class<?> target, String name, Object[] args) throws Exception {
        for (Method m : target.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                m.setAccessible(true);
                return m.invoke(null, args);
            }
        }
        throw new NoSuchMethodException(name);
    }

    public static void sendKey(long handle, int key, int action) {
        if (LWJGL3) {
            try {
                Object mc = getMinecraftInstance();
                Object kbHandler = mc.getClass().getField("keyboardHandler").get(mc);
                kbHandler.getClass().getMethod("keyPress", long.class, int.class, int.class, int.class, int.class)
                    .invoke(kbHandler, handle, key, 0, action, 0);
                return;
            } catch (NoSuchFieldException nsfe) {
                try {
                    Object mc = getMinecraftInstance();
                    Object kbHandler = mc.getClass().getMethod("keyboardHandler").invoke(mc);
                    kbHandler.getClass().getMethod("keyPress", long.class, int.class, int.class, int.class, int.class)
                        .invoke(kbHandler, handle, key, 0, action, 0);
                    return;
                } catch (Exception e2) {
                    System.err.println("[Input] sendKey GLFW: " + e2.getMessage());
                }
            } catch (Exception e) {
                System.err.println("[Input] sendKey GLFW: " + e.getMessage());
            }
        }
        try {
            Robot r = awtRobot;
            if (r == null) return;
            int vk = glfwToAwt(key);
            if (vk < 0) return;
            if (action == 1) r.keyPress(vk);
            else r.keyRelease(vk);
        } catch (Exception e) { System.err.println("[Input] sendKey: " + e.getMessage()); }
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

    private static Method findMouseButtonMethod(Class<?> mouseHandlerClass) {
        Method fallback = null;
        for (Method m : mouseHandlerClass.getDeclaredMethods()) {
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length != 4 || pt[0] != long.class || pt[1] != int.class || pt[2] != int.class || pt[3] != int.class) continue;
            if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
            String name = m.getName();
            if (name.equals("onPress")) return m;
            if (name.startsWith("lambda$setup$")) {
                if (fallback == null) fallback = m;
                continue;
            }
            if (name.contains("mouseButton") || name.contains("onMouse") || name.contains("button")) {
                if (fallback == null) fallback = m;
            }
        }
        return fallback;
    }

    public static Object getMouseHandler(Object mc) {
        try { return mc.getClass().getField("mouseHandler").get(mc); } catch (Exception e) {}
        try { return mc.getClass().getDeclaredField("mouseHandler").get(mc); } catch (Exception e) {}
        for (Field f : mc.getClass().getDeclaredFields()) {
            if (f.getType().getSimpleName().contains("MouseHandler") || f.getType().getSimpleName().contains("Mouse")) {
                try { f.setAccessible(true); return f.get(mc); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    public static void sendMouseButton(long handle, int button, int action) {
        if (LWJGL3 && handle != 0) {
            try {
                Object mc = getMinecraftInstance();
                Object mouseHandler = getMouseHandler(mc);
                if (mouseHandler != null) {
                    Method target = findMouseButtonMethod(mouseHandler.getClass());
                    if (target != null) {
                        target.setAccessible(true);
                        target.invoke(mouseHandler, handle, button, action, 0);
                        return;
                    }
                }
                try {
                    Class<?> glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
                    Method glfwSetInputMode = glfwClass.getMethod("glfwSetInputMode", long.class, int.class, int.class);
                    int GLFW_STICKY_MOUSE_BUTTONS = glfwClass.getDeclaredField("GLFW_STICKY_MOUSE_BUTTONS").getInt(null);
                    glfwSetInputMode.invoke(null, handle, GLFW_STICKY_MOUSE_BUTTONS, 1);
                    Class<?>MouseButtonCallback = null;
                    for (Method cbm : glfwClass.getDeclaredMethods()) {
                        if (cbm.getName().equals("glfwSetMouseButtonCallback") && cbm.getParameterCount() == 2) {
                            cbm.invoke(null, handle, (java.lang.reflect.Proxy.newProxyInstance(
                                Thread.currentThread().getContextClassLoader(),
                                new Class<?>[]{ Class.forName("org.lwjgl.glfw.GLFWMouseButtonCallbackI") },
                                (proxy, method, args) -> null
                            )));
                            break;
                        }
                    }
                } catch (Exception ignored) {}
                dbg("sendMouseButton: NO matching method found!");
            } catch (Exception e) {
                dbg("sendMouseButton GLFW: " + e.getMessage());
            }
        }
        try {
            Robot r = awtRobot;
            if (r == null) return;
            int mask = button == 1 ? InputEvent.BUTTON2_DOWN_MASK
                     : button == 2 ? InputEvent.BUTTON3_DOWN_MASK
                     : InputEvent.BUTTON1_DOWN_MASK;
            if (action == 1) r.mousePress(mask);
            else r.mouseRelease(mask);
        } catch (Exception e) { System.err.println("[Input] sendMouseButton: " + e.getMessage()); }
    }

    public static void sendScroll(long handle, double scrollY) {
        if (LWJGL3) {
            try {
                Object mc = getMinecraftInstance();
                Object mouseHandler = mc.getClass().getField("mouseHandler").get(mc);
                for (Method m : mouseHandler.getClass().getDeclaredMethods()) {
                    String name = m.getName();
                    if ((name.equals("lambda$setup$2") || name.equals("lambda$setup$3") || name.contains("scroll"))
                        && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                        Class<?>[] pt = m.getParameterTypes();
                        if (pt.length == 3 && pt[0] == long.class && pt[1] == double.class && pt[2] == double.class) {
                            m.setAccessible(true);
                            m.invoke(mouseHandler, handle, 0.0, scrollY);
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[Input] sendScroll GLFW: " + e.getMessage());
            }
        }
        try {
            Robot r = awtRobot;
            if (r == null) return;
            r.mouseWheel((int) scrollY);
        } catch (Exception e) { System.err.println("[Input] sendScroll: " + e.getMessage()); }
    }

    public static void sendMouseMoveInternal(long handle, double x, double y) {
        if (LWJGL3) {
            try {
                Object mc = getMinecraftInstance();
                Object mouseHandler = mc.getClass().getField("mouseHandler").get(mc);
                for (Method m : mouseHandler.getClass().getDeclaredMethods()) {
                    String name = m.getName();
                    if ((name.contains("cursor") || name.equals("lambda$setup$0") || name.equals("lambda$setup$1"))
                        && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                        Class<?>[] pt = m.getParameterTypes();
                        if (pt.length == 3 && pt[0] == long.class && pt[1] == double.class && pt[2] == double.class) {
                            m.setAccessible(true);
                            m.invoke(mouseHandler, handle, x, y);
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[Input] sendMouseMove GLFW: " + e.getMessage());
            }
        }
    }

    public static String directScroll(Object mc, double mouseX, double mouseY, double delta) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen == null) return "{\"error\":\"no screen\"}";
            String sn = screen.getClass().getSimpleName();
            double mx = mouseX, my = mouseY;
            if (mx < 0 || my < 0) {
                try {
                    Object mh = mc.getClass().getField("mouseHandler").get(mc);
                    for (Field f : getAllFields(mh.getClass())) {
                        if (f.getName().equals("xpos") || f.getName().equals("field_192635_i")) {
                            try { f.setAccessible(true); mx = f.getDouble(mh); } catch (Exception ignored) {}
                        }
                    }
                    for (Field f : getAllFields(mh.getClass())) {
                        if (f.getName().equals("ypos") || f.getName().equals("field_192636_j")) {
                            try { f.setAccessible(true); my = f.getDouble(mh); } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
            }
            double guiScale = 1.0;
            try {
                Object window = null;
                for (Method m : mc.getClass().getMethods()) {
                    if (m.getName().equals("getWindow") && m.getParameterCount() == 0) {
                        window = m.invoke(mc); break;
                    }
                }
                if (window != null) {
                    for (Method m : window.getClass().getMethods()) {
                        if (m.getName().equals("getGuiScale") && m.getParameterCount() == 0) {
                            guiScale = ((Number) m.invoke(window)).doubleValue();
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}
            double scaledX = mx / guiScale;
            double scaledY = my / guiScale;
            Method target = null;
            for (Method m : getAllMethods(screen.getClass())) {
                if (m.getName().equals("mouseScrolled")) {
                    Class<?>[] pt = m.getParameterTypes();
                    if (pt.length == 4 && pt[0] == double.class && pt[1] == double.class
                        && pt[2] == double.class && pt[3] == double.class) {
                        target = m; break;
                    }
                    if (pt.length == 3 && pt[0] == double.class && pt[1] == double.class && pt[2] == double.class) {
                        target = m; break;
                    }
                }
            }
            if (target == null) return "{\"error\":\"mouseScrolled not found on " + sn + "\"}";
            target.setAccessible(true);
            if (target.getParameterCount() == 4) {
                target.invoke(screen, scaledX, scaledY, 0.0, delta);
            } else {
                target.invoke(screen, scaledX, scaledY, delta);
            }
            return "{\"direct_scroll\":true,\"screen\":\"" + sn + "\",\"mouseX\":" + scaledX + ",\"mouseY\":" + scaledY + ",\"delta\":" + delta + ",\"guiScale\":" + guiScale + "}";
        } catch (Exception e) {
            return "{\"error\":\"" + (e.getMessage() != null ? e.getMessage().replace("\\","\\\\").replace("\"","\\\"") : "null") + "\"}";
        }
    }

    public static String selectListItem(Object mc, int targetIndex) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen == null) return "{\"error\":\"no screen\"}";
            String sn = screen.getClass().getSimpleName();

            Method childrenMethod = null;
            for (Method m : getAllMethods(screen.getClass())) {
                if (m.getName().equals("children") && m.getParameterCount() == 0) {
                    childrenMethod = m; break;
                }
            }
            if (childrenMethod == null) return "{\"error\":\"no children() on " + sn + "\"}";
            childrenMethod.setAccessible(true);
            Object children = childrenMethod.invoke(screen);
            if (!(children instanceof java.util.List)) return "{\"error\":\"children not a List\"}";
            java.util.List<?> childList = (java.util.List<?>) children;

            Object listWidget = null;
            for (Object child : childList) {
                String cn = child.getClass().getSimpleName();
                if (cn.contains("List") || cn.contains("SelectionList")) {
                    listWidget = child; break;
                }
            }
            if (listWidget == null) return "{\"error\":\"no list widget in children\"}";

            String lcn = listWidget.getClass().getName();
            dbg("selectListItem: found " + lcn);

            int setSize = 0;
            for (Method m : getAllMethods(listWidget.getClass())) {
                if (m.getName().equals("getRowCount") && m.getParameterCount() == 0) {
                    try { m.setAccessible(true); setSize = ((Number) m.invoke(listWidget)).intValue(); } catch (Exception ignored) {}
                    break;
                }
            }
            if (setSize == 0) {
                try {
                    for (Method m : getAllMethods(listWidget.getClass())) {
                        if (m.getName().equals("size") && m.getParameterCount() == 0) {
                            m.setAccessible(true); setSize = ((Number) m.invoke(listWidget)).intValue(); break;
                        }
                    }
                } catch (Exception ignored) {}
            }
            if (setSize > 0 && targetIndex >= setSize) return "{\"error\":\"index " + targetIndex + " >= size " + setSize + "\"}";

            boolean selected = false;
            boolean setSelectedCalled = false;
            for (Method m : getAllMethods(listWidget.getClass())) {
                String mn = m.getName();
                if ((mn.equals("setSelected") || mn.equals("selectItemIndex") || mn.equals("setSelectedIndex"))
                    && m.getParameterCount() == 1) {
                    Class<?> pt = m.getParameterTypes()[0];
                    try {
                        m.setAccessible(true);
                        if (pt == int.class) m.invoke(listWidget, targetIndex);
                        else if (pt == Integer.class) m.invoke(listWidget, Integer.valueOf(targetIndex));
                        setSelectedCalled = true;
                        dbg("selectListItem: called " + mn + "(" + targetIndex + ")");
                        break;
                    } catch (Exception e) { dbg("selectListItem: " + mn + " failed: " + e.getMessage()); }
                }
            }

            if (!setSelectedCalled) {
                for (Field f : getAllFields(listWidget.getClass())) {
                    String fn = f.getName();
                    if ((fn.equals("selected") || fn.equals("selectedIndex") || fn.equals("selectedItem") || fn.contains("selectedRow"))
                        && (f.getType() == int.class || f.getType() == Integer.class)) {
                        try {
                            f.setAccessible(true);
                            f.setInt(listWidget, targetIndex);
                            selected = true;
                            dbg("selectListItem: set field " + fn + "=" + targetIndex);
                            break;
                        } catch (Exception e) { dbg("selectListItem: field " + fn + " failed: " + e.getMessage()); }
                    }
                }
            }

            if (!selected) {
                try {
                    for (Method m : getAllMethods(listWidget.getClass())) {
                        if (m.getName().equals("ensureVisible") && m.getParameterCount() == 1) {
                            m.setAccessible(true);
                            m.invoke(listWidget, targetIndex);
                            dbg("selectListItem: ensureVisible(" + targetIndex + ")");
                        }
                    }
                } catch (Exception ignored) {}
            }

            {
                try {
                    java.util.List<?> entries = null;
                    for (Method m : getAllMethods(listWidget.getClass())) {
                        if (m.getName().equals("children") && m.getParameterCount() == 0) {
                            m.setAccessible(true);
                            Object result = m.invoke(listWidget);
                            if (result instanceof java.util.List) { entries = (java.util.List<?>) result; break; }
                        }
                    }
                    if (entries == null) {
                        for (Field f : getAllFields(listWidget.getClass())) {
                            if (java.util.List.class.isAssignableFrom(f.getType())) {
                                try { f.setAccessible(true); entries = (java.util.List<?>) f.get(listWidget); } catch (Exception ignored) {}
                                if (entries != null) break;
                            }
                        }
                    }
                    if (entries != null && targetIndex < entries.size()) {
                        Object entry = entries.get(targetIndex);
                        dbg("selectListItem: got entry " + targetIndex + " of " + entries.size() + ": " + entry.getClass().getName());
                        for (Method m : getAllMethods(entry.getClass())) {
                            String mn = m.getName();
                            if ((mn.equals("select") || mn.equals("onClick") || mn.equals("onSelect") || mn.equals("setSelected"))
                                && m.getParameterCount() == 0) {
                                try {
                                    m.setAccessible(true);
                                    m.invoke(entry);
                                    selected = true;
                                    dbg("selectListItem: called " + mn + "() on entry " + targetIndex);
                                    break;
                                } catch (Exception e) { dbg("selectListItem: " + mn + "() failed: " + e.getMessage()); }
                            }
                        }
                    } else if (entries != null) {
                        dbg("selectListItem: entries.size=" + entries.size() + " targetIndex=" + targetIndex);
                    } else {
                        dbg("selectListItem: no entries found on " + lcn);
                    }
                } catch (Exception e) { dbg("selectListItem: entry fallback: " + e.getMessage()); }
            }

            if (!selected) {
                StringBuilder methods = new StringBuilder();
                for (Method m : getAllMethods(listWidget.getClass())) {
                    if (m.getName().toLowerCase().contains("select") || m.getName().toLowerCase().contains("row") || m.getName().toLowerCase().contains("index")) {
                        methods.append(m.getName()).append("(").append(m.getParameterCount()).append(") ");
                    }
                }
                StringBuilder fields = new StringBuilder();
                for (Field f : getAllFields(listWidget.getClass())) {
                    fields.append(f.getName()).append(":").append(f.getType().getSimpleName()).append(" ");
                }
                return "{\"error\":\"could not select on " + lcn + "\",\"methods\":\"" + methods + "\",\"fields\":\"" + fields + "\"}";
            }

            for (Method m : getAllMethods(screen.getClass())) {
                if (m.getName().equals("updateButtonValidity") && m.getParameterCount() == 1) {
                    try { m.setAccessible(true); m.invoke(screen, true); } catch (Exception ignored) {}
                    break;
                }
            }

            return "{\"selectListItem\":true,\"screen\":\"" + sn + "\",\"listWidget\":\"" + listWidget.getClass().getSimpleName() + "\",\"index\":" + targetIndex + ",\"setSize\":" + setSize + "}";
        } catch (Exception e) {
            return "{\"error\":\"" + (e.getMessage() != null ? e.getMessage().replace("\\","\\\\").replace("\"","\\\"") : "null") + "\"}";
        }
    }

    public static void sendMouseDrag(long handle, int x1, int y1, int x2, int y2, int button, int steps) {
        setCursorPos(handle, x1, y1);
        try { Thread.sleep(20); } catch (Exception ignored) {}
        sendMouseButton(handle, button, 1);
        try { Thread.sleep(20); } catch (Exception ignored) {}
        if (steps < 1) steps = 1;
        for (int i = 1; i <= steps; i++) {
            int cx = x1 + (x2 - x1) * i / steps;
            int cy = y1 + (y2 - y1) * i / steps;
            setCursorPos(handle, cx, cy);
            try { Thread.sleep(10); } catch (Exception ignored) {}
        }
        sendMouseButton(handle, button, 0);
    }

    public static void setCursorPos(long handle, double x, double y) {
        initClassCache();
        if (LWJGL3) {
            try {
                if (glfwSetCursorPosMethod != null) {
                    glfwSetCursorPosMethod.invoke(null, handle, x, y);
                } else {
                    Method setPos = glfwClass.getMethod("glfwSetCursorPos", long.class, double.class, double.class);
                    setPos.invoke(null, handle, x, y);
                }

                Object mc = getMinecraftInstance();
                Object mouseHandler = mc.getClass().getField("mouseHandler").get(mc);
                for (Field f : mouseHandler.getClass().getDeclaredFields()) {
                    if (f.getType() == double.class) {
                        String name = f.getName().toLowerCase();
                        if (name.contains("xpos") || name.equals("x")) {
                            f.setAccessible(true);
                            f.setDouble(mouseHandler, x);
                        } else if (name.contains("ypos") || name.equals("y")) {
                            f.setAccessible(true);
                            f.setDouble(mouseHandler, y);
                        }
                    }
                }
                return;
            } catch (Exception e) {
                System.err.println("[Input] setCursorPos GLFW: " + e.getMessage());
            }
        }
        try {
            Robot r = awtRobot;
            if (r == null) return;
            int wx = 0, wy = 0;
            if (LWJGL3) {
                try {
                    Class<?> glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
                    Method getX = glfwClass.getMethod("glfwGetWindowPosX", long.class);
                    Method getY = glfwClass.getMethod("glfwGetWindowPosY", long.class);
                    wx = ((Number) getX.invoke(null, handle)).intValue();
                    wy = ((Number) getY.invoke(null, handle)).intValue();
                } catch (Exception ignored) {}
            } else {
                try {
                    Class<?> displayClass = Class.forName("org.lwjgl.opengl.Display");
                    wx = ((Number) displayClass.getMethod("getX").invoke(null)).intValue();
                    wy = ((Number) displayClass.getMethod("getY").invoke(null)).intValue();
                } catch (Exception ignored) {}
            }
            r.mouseMove(wx + (int) x, wy + (int) y);
        } catch (Exception e) { System.err.println("[Input] setCursorPos: " + e.getMessage()); }
    }

    private static int glfwToAwt(int glfwKey) {
        if (glfwKey >= 'A' && glfwKey <= 'Z') return KeyEvent.VK_A + (glfwKey - 'A');
        if (glfwKey >= '0' && glfwKey <= '9') return KeyEvent.VK_0 + (glfwKey - '0');
        switch (glfwKey) {
            case 0xFF0D: return KeyEvent.VK_ENTER;
            case 0xFF1B: return KeyEvent.VK_ESCAPE;
            case 0xFF09: return KeyEvent.VK_TAB;
            case 0x0020: return KeyEvent.VK_SPACE;
            case 0xFF08: return KeyEvent.VK_BACK_SPACE;
            case 0xFFFF: return KeyEvent.VK_DELETE;
            case 0xFF52: return KeyEvent.VK_UP;
            case 0xFF54: return KeyEvent.VK_DOWN;
            case 0xFF51: return KeyEvent.VK_LEFT;
            case 0xFF53: return KeyEvent.VK_RIGHT;
            case 0xFFE1: return KeyEvent.VK_SHIFT;
            case 0xFFE3: return KeyEvent.VK_CONTROL;
            case 0xFFE9: return KeyEvent.VK_ALT;
            case 0xFFBE: return KeyEvent.VK_F1;
            case 0xFFBF: return KeyEvent.VK_F2;
            case 0xFFC0: return KeyEvent.VK_F3;
            case 0xFFC1: return KeyEvent.VK_F4;
            case 0xFFC2: return KeyEvent.VK_F5;
            case 0xFFC3: return KeyEvent.VK_F6;
            case 0xFFC4: return KeyEvent.VK_F7;
            case 0xFFC5: return KeyEvent.VK_F8;
            case 0xFFC6: return KeyEvent.VK_F9;
            case 0xFFC7: return KeyEvent.VK_F10;
            case 0xFFC8: return KeyEvent.VK_F11;
            case 0xFFC9: return KeyEvent.VK_F12;
            case 0x002E: return KeyEvent.VK_PERIOD;
            case 0x002C: return KeyEvent.VK_COMMA;
            case 0x002D: return KeyEvent.VK_MINUS;
            case 0x003D: return KeyEvent.VK_EQUALS;
            case 0x005B: return KeyEvent.VK_OPEN_BRACKET;
            case 0x005D: return KeyEvent.VK_CLOSE_BRACKET;
            case 0x003B: return KeyEvent.VK_SEMICOLON;
            case 0x0060: return KeyEvent.VK_BACK_QUOTE;
            default: return -1;
        }
    }

    public static byte[] takeWindowScreenshot() {
        try {
            Robot r = awtRobot;
            if (r == null) return null;
            int wx = 0, wy = 0, ww = 800, wh = 600;
            if (!LWJGL3) {
                try {
                    Class<?> displayClass = Class.forName("org.lwjgl.opengl.Display");
                    wx = (Integer) displayClass.getMethod("getX").invoke(null);
                    wy = (Integer) displayClass.getMethod("getY").invoke(null);
                    ww = (Integer) displayClass.getMethod("getWidth").invoke(null);
                    wh = (Integer) displayClass.getMethod("getHeight").invoke(null);
                } catch (Exception e) {
                    return null;
                }
            } else {
                Object mc = getMinecraftInstance();
                if (!hasWindow(mc)) return null;
                long handle = getWindowHandle(mc);
                if (handle == 0) return null;
                try {
                    Class<?> glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
                    Thread.sleep(100);
                    wx = ((Number) glfwClass.getMethod("glfwGetWindowPosX", long.class).invoke(null, handle)).intValue();
                    wy = ((Number) glfwClass.getMethod("glfwGetWindowPosY", long.class).invoke(null, handle)).intValue();
                    int[] wArr = {0}, hArr = {0};
                    try {
                        Class<?> intBufClass = Class.forName("java.nio.IntBuffer");
                        Object wBuf = java.nio.IntBuffer.allocate(1);
                        Object hBuf = java.nio.IntBuffer.allocate(1);
                        glfwClass.getMethod("glfwGetWindowSize", long.class, intBufClass, intBufClass)
                                .invoke(null, handle, wBuf, hBuf);
                        ww = ((java.nio.IntBuffer) wBuf).get(0);
                        wh = ((java.nio.IntBuffer) hBuf).get(0);
                    } catch (Exception e2) {
                        Object window2 = null;
                        try { window2 = mc.getClass().getMethod("getWindow").invoke(mc); }
                        catch (NoSuchMethodException nsme) {
                            try { window2 = mc.getClass().getMethod("getMainWindow").invoke(mc); }
                            catch (Exception ignored) {}
                        }
                        if (window2 != null) {
                            for (String fn : new String[]{"width", "cachedWidth"}) {
                                try { Field wf = window2.getClass().getDeclaredField(fn); wf.setAccessible(true); ww = wf.getInt(window2); break; } catch (Exception ignored) {}
                            }
                            for (String fn : new String[]{"height", "cachedHeight"}) {
                                try { Field hf = window2.getClass().getDeclaredField(fn); hf.setAccessible(true); wh = hf.getInt(window2); break; } catch (Exception ignored) {}
                            }
                        }
                    }
                } catch (Exception e) {
                    return null;
                }
            }
            if (ww <= 0 || wh <= 0) return null;
            Rectangle rect = new Rectangle(wx, wy, ww, wh);
            BufferedImage img = r.createScreenCapture(rect);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] takePlatformScreenshot() {
        return McpControlFactory.get().takePlatformScreenshot();
    }

    public static void lwjgl2PressKey(int keyCode) {}

    public static void lwjgl2ReleaseKey(int keyCode) {}

    public static void lwjgl2MouseNext() {}

    public static void lwjgl2SetMouseButton(int button, boolean pressed) {}

    private static Object getPlayer(Object mc) throws Exception {
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

    private static Object getLevel(Object mc) throws Exception {
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

    private static Object getPlayerLevel(Object player) throws Exception {
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

    private static List<Field> getAllFields(Class<?> clazz) {
        if (clazz.getName().startsWith("org.lwjgl.") || clazz.getName().startsWith("com.mojang.blaze3d.")) {
            List<Field> fields = new java.util.ArrayList<>();
            Class<?> cur = clazz;
            while (cur != null && cur != Object.class) {
                for (Field f : cur.getDeclaredFields()) fields.add(f);
                cur = cur.getSuperclass();
            }
            return fields;
        }
        return fieldCache.computeIfAbsent(clazz, c -> {
            List<Field> fields = new java.util.ArrayList<>();
            Class<?> cur = c;
            while (cur != null && cur != Object.class) {
                for (Field f : cur.getDeclaredFields()) fields.add(f);
                cur = cur.getSuperclass();
            }
            return fields;
        });
    }

    private static Object fieldOrNull(Object obj, String fieldName) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) { return null; }
    }

    private static String invokeString(Object obj, String methodName) {
        try {
            Object r = obj.getClass().getMethod(methodName).invoke(obj);
            return r != null ? r.toString() : "";
        } catch (Exception e) { return ""; }
    }

    private static Object invokeOrNull(Object obj, String methodName) {
        try { return obj.getClass().getMethod(methodName).invoke(obj); }
        catch (Exception e) { return null; }
    }

    private static double getDouble(Object obj, String... names) throws Exception {
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

    public static boolean isLwjgl3() { return LWJGL3; }

    public static String debugFields(Object mc) {
        StringBuilder sb = new StringBuilder("{\"class\":\"").append(mc.getClass().getName()).append("\",");
        sb.append("\"fields\":[");
        List<Field> all = getAllFields(mc.getClass());
        boolean first = true;
        for (Field f : all) {
            String n = f.getName();
            if (n.contains("Player") || n.contains("player") || n.contains("field_71439") ||
                n.contains("World") || n.contains("world") || n.contains("field_71441") || n.contains("field_71435")) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(mc);
                    if (!first) sb.append(",");
                    sb.append("{\"name\":\"").append(n).append("\",\"value\":\"").append(v != null ? v.getClass().getSimpleName() : "null").append("\"}");
                    first = false;
                } catch (Exception e) {
                    if (!first) sb.append(",");
                    sb.append("{\"name\":\"").append(n).append("\",\"error\":\"").append(e.getMessage()).append("\"}");
                    first = false;
                }
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    public static String pasteText(Object mc, String text) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen != null) {
                try {
                    Object clipboardManager = getClipboardManager(mc);
                    if (clipboardManager != null) {
                        for (Method m : getAllMethods(clipboardManager.getClass())) {
                            if ((m.getName().equals("setClipboard") || m.getName().contains("setClipboard"))
                                    && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) {
                                m.setAccessible(true);
                                m.invoke(clipboardManager, text);
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {}
                for (Method m : getAllMethods(screen.getClass())) {
                    if ((m.equals("paste") || m.getName().contains("paste"))
                            && m.getParameterCount() == 1 && m.getParameterTypes()[0] == CharSequence.class) {
                        try {
                            m.setAccessible(true);
                            m.invoke(screen, text);
                            return "{\"pasted\":true,\"method\":\"paste(CharSequence)\"}";
                        } catch (Exception ignored) {}
                    }
                    if ((m.getName().equals("paste") || m.getName().contains("paste"))
                            && m.getParameterCount() == 0) {
                        try {
                            m.setAccessible(true);
                            m.invoke(screen);
                            return "{\"pasted\":true,\"method\":\"paste()\"}";
                        } catch (Exception ignored) {}
                    }
                }
            }
            java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new java.awt.datatransfer.StringSelection(text), null);
            Thread.sleep(50);
            if (screen != null) {
                for (Method m : getAllMethods(screen.getClass())) {
                    if ((m.getName().equals("paste") || m.getName().contains("paste")) && m.getParameterCount() == 0) {
                        try {
                            m.setAccessible(true);
                            m.invoke(screen);
                            return "{\"pasted\":true,\"method\":\"awt_paste\"}";
                        } catch (Exception ignored) {}
                    }
                }
            }
            hotkey(mc, new String[]{"ctrl", "v"});
            return "{\"pasted\":true,\"method\":\"hotkey_ctrl_v\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private static Object getClipboardManager(Object mc) {
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

    private static void hotkey(Object mc, String[] keys) {
        try {
            long h = getWindowHandle(mc);
            if (h == 0) return;
            int[] codes = new int[keys.length];
            for (int i = 0; i < keys.length; i++) codes[i] = GlfwKeys.keyCode(keys[i]);
            for (int c : codes) { sendKey(h, c, 1); Thread.sleep(5); }
            Thread.sleep(80);
            for (int i = codes.length - 1; i >= 0; i--) sendKey(h, codes[i], 0);
        } catch (Exception e) { System.err.println("[ReflectionHelper] hotkey: " + e.getMessage()); }
    }

    public static String setPlayerRotation(Object mc, float yaw, float pitch) {
        try {
            Object player = getPlayer(mc);
            if (player == null) return "{\"error\":\"no player\"}";
            setRotField(player, "yRot", yaw);
            setRotField(player, "xRot", pitch);
            setRotField(player, "yawRot", yaw);
            setRotField(player, "xRotO", pitch);
            setRotField(player, "yRotO", yaw);
            setRotField(player, "oYRot", yaw);
            setRotField(player, "oXRot", pitch);
            return "{\"rot_set\":true,\"yaw\":" + yaw + ",\"pitch\":" + pitch + "}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String deltaPlayerRotation(Object mc, float deltaYaw, float deltaPitch) {
        try {
            Object player = getPlayer(mc);
            if (player == null) return "{\"error\":\"no player\"}";
            float currentYaw = getRotField(player, "yRot");
            float currentPitch = getRotField(player, "xRot");
            float newYaw = currentYaw + deltaYaw;
            float newPitch = Math.max(-90f, Math.min(90f, currentPitch + deltaPitch));
            setPlayerRotation(mc, newYaw, newPitch);
            return "{\"rot_delta\":true,\"from\":[" + currentYaw + "," + currentPitch + "],\"to\":[" + newYaw + "," + newPitch + "]}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private static void setRotField(Object player, String fieldName, float value) throws Exception {
        for (Field f : getAllFields(player.getClass())) {
            if (f.getName().equals(fieldName) || f.getName().contains(srgSafe(fieldName))) {
                if (f.getType() == float.class || f.getType() == double.class) {
                    f.setAccessible(true);
                    if (f.getType() == double.class) f.setDouble(player, (double)value);
                    else f.setFloat(player, value);
                    return;
                }
            }
        }
    }

    private static float getRotField(Object player, String fieldName) throws Exception {
        for (Field f : getAllFields(player.getClass())) {
            if (f.getName().equals(fieldName) || f.getName().contains(srgSafe(fieldName))) {
                if (f.getType() == float.class || f.getType() == double.class) {
                    f.setAccessible(true);
                    if (f.getType() == double.class) return (float)f.getDouble(player);
                    return f.getFloat(player);
                }
            }
        }
        return 0f;
    }

    private static String srgSafe(String name) {
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

    public static String doRightClick(Object mc) {
        try {
            long handle = getWindowHandle(mc);
            Object mouseHandler = getMouseHandler(mc);
            if (mouseHandler != null && handle != 0) {
                Method target = findMouseButtonMethod(mouseHandler.getClass());
                if (target != null) {
                    target.setAccessible(true);
                    target.invoke(mouseHandler, handle, 1, 1, 0);
                    Thread.sleep(50);
                    target.invoke(mouseHandler, handle, 1, 0, 0);
                    return "{\"right_click\":true,\"via\":\"mouseHandler\"}";
                }
            }
            sendMouseButton(handle, 1, 1);
            Thread.sleep(100);
            sendMouseButton(handle, 1, 0);
            return "{\"right_click\":true,\"via\":\"sendMouseButton\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String doUseItem(Object mc) {
        try {
            for (Method m : getAllMethods(mc.getClass())) {
                String mn = m.getName();
                if ((mn.equals("startUseItem") || mn.equals("rightClickMouse") || mn.equals("func_147121_ag") || mn.equals("func_147118_ci"))
                        && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                        m.invoke(mc);
                        return "{\"use_item\":true,\"method\":\"" + mn + "\"}";
                    } catch (Exception ignored) {}
                }
            }
            for (Method m : getAllMethods(mc.getClass())) {
                String mn = m.getName().toLowerCase();
                if (m.getParameterCount() == 0 && mn.contains("useitem") && !mn.contains("tick") && !mn.contains("render")) {
                    try {
                        m.setAccessible(true);
                        m.invoke(mc);
                        return "{\"use_item\":true,\"method\":\"" + m.getName() + "\"}";
                    } catch (Exception ignored) {}
                }
            }
            return "{\"error\":\"no useItem method found\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String doPlaceBlock(Object mc) {
        try {
            Object player = getPlayer(mc);
            if (player == null) return "{\"error\":\"no player\"}";
            Object gameMode = null;
            try { gameMode = mc.getClass().getMethod("gameMode").invoke(mc); } catch (Exception ignored) {}
            if (gameMode == null) {
                for (Field f : getAllFields(mc.getClass())) {
                    String fn = f.getName().toLowerCase();
                    if (fn.contains("gamemode") || fn.contains("interaction") || fn.contains("multiplayer")) {
                        try { f.setAccessible(true); gameMode = f.get(mc); dbg("doPlaceBlock: found gameMode via field " + f.getName()); break; } catch (Exception ignored) {}
                    }
                }
            }
            if (gameMode == null) return "{\"error\":\"no gameMode\"}";
            double px = 0, py = 0, pz = 0;
            for (Method m : getAllMethods(player.getClass())) {
                if ((m.getName().equals("getX") || m.getName().equals("func_223148_aQ")) && m.getParameterCount() == 0) { m.setAccessible(true); px = ((Number)m.invoke(player)).doubleValue(); }
                if ((m.getName().equals("getY") || m.getName().equals("func_223149_e")) && m.getParameterCount() == 0) { m.setAccessible(true); py = ((Number)m.invoke(player)).doubleValue(); }
                if ((m.getName().equals("getZ") || m.getName().equals("func_223143_g")) && m.getParameterCount() == 0) { m.setAccessible(true); pz = ((Number)m.invoke(player)).doubleValue(); }
            }
            int bx = (int)Math.floor(px), by = (int)Math.floor(py) - 1, bz = (int)Math.floor(pz);
            dbg("doPlaceBlock: player at " + px + "," + py + "," + pz + " placing at " + bx + "," + by + "," + bz);
            Class<?> vec3Class = Class.forName("net.minecraft.world.phys.Vec3");
            Object hitVec = vec3Class.getConstructor(double.class, double.class, double.class).newInstance(px, by + 1.0, pz);
            Class<?> dirClass = Class.forName("net.minecraft.core.Direction");
            Object dirUp = null;
            for (Object d : (Enum[])dirClass.getMethod("values").invoke(null)) { if (((Enum)d).name().equals("UP")) { dirUp = d; break; } }
            Class<?> bpClass = Class.forName("net.minecraft.core.BlockPos");
            Object blockPos = bpClass.getConstructor(int.class, int.class, int.class).newInstance(bx, by, bz);
            Class<?> bhrClass = Class.forName("net.minecraft.world.phys.BlockHitResult");
            Object hitResult = bhrClass.getConstructor(vec3Class, dirClass, bpClass, boolean.class).newInstance(hitVec, dirUp, blockPos, false);
            Class<?> handClass = Class.forName("net.minecraft.world.InteractionHand");
            Object mainHand = null;
            for (Object h : (Enum[])handClass.getMethod("values").invoke(null)) { if (((Enum)h).name().equals("MAIN_HAND")) { mainHand = h; break; } }
            boolean placed = false;
            for (Method m : getAllMethods(gameMode.getClass())) {
                String mn = m.getName();
                if ((mn.equals("useItemOn") || mn.equals("func_180517_b") || mn.contains("useItemOn"))
                        && m.getParameterCount() >= 3) {
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts.length == 3 && pts[0].isInstance(player) && pts[1] == handClass) {
                        try { m.setAccessible(true); m.invoke(gameMode, player, mainHand, hitResult); placed = true; dbg("doPlaceBlock: OK via " + mn + "(3)"); break; }
                        catch (Exception ignored) {}
                    }
                }
                if (!placed && (mn.equals("useItemOn") || mn.equals("func_180517_b") || mn.contains("useItemOn"))
                        && m.getParameterCount() >= 4) {
                    try { m.setAccessible(true); m.invoke(gameMode, player, mainHand, hitResult); placed = true; dbg("doPlaceBlock: OK via " + mn); break; }
                    catch (Exception ignored) {}
                }
            }
            if (!placed) {
                for (Method m : getAllMethods(gameMode.getClass())) {
                    String mn = m.getName().toLowerCase();
                    if (mn.contains("useitem") && !mn.contains("continue") && m.getParameterCount() >= 2) {
                        try { m.setAccessible(true); m.invoke(gameMode, player, mainHand, hitResult); placed = true; dbg("doPlaceBlock: OK via " + m.getName()); break; }
                        catch (Exception ignored) {}
                    }
                }
            }
            if (!placed) return "{\"error\":\"no useItemOn method found on gameMode\"}";
            return "{\"placed\":true,\"at\":[" + bx + "," + by + "," + bz + "]}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String openChatScreen(Object mc) {
        try {
            Class<?> chatScreenClass = null;
            for (String cn : new String[]{
                "net.minecraft.client.gui.screens.ChatScreen",
                "net.minecraft.client.gui.screen.ChatScreen",
                "net.minecraft.client.gui.screen.inventory.ChatScreen"
            }) {
                try { chatScreenClass = Class.forName(cn); break; } catch (ClassNotFoundException ignored) {}
            }
            if (chatScreenClass == null) return "{\"error\":\"ChatScreen class not found\"}";
            Object chatScreen = null;
            try { chatScreen = chatScreenClass.getConstructor(String.class).newInstance(""); }
            catch (Exception ignored) {
                try { chatScreen = chatScreenClass.getConstructor().newInstance(); }
                catch (Exception ignored2) {}
            }
            if (chatScreen == null) return "{\"error\":\"ChatScreen instantiation failed\"}";
            for (Method m : getAllMethods(mc.getClass())) {
                if (m.getName().equals("setScreen") && m.getParameterCount() == 1) {
                    m.setAccessible(true);
                    m.invoke(mc, chatScreen);
                    return "{\"chat_opened\":true,\"screen\":\"" + chatScreenClass.getSimpleName() + "\"}";
                }
            }
            return "{\"error\":\"setScreen method not found\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String closeScreen(Object mc) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen == null) return "{\"error\":\"no screen to close\"}";
            String sn = screen.getClass().getSimpleName();
            boolean keyPressedWorked = false;
            int GLFW_KEY_ESCAPE = 256;
            for (Method m : getAllMethods(screen.getClass())) {
                String n = m.getName();
                if ((n.equals("keyPressed") || n.contains("keyPressed"))
                        && m.getParameterCount() >= 3) {
                    try {
                        m.setAccessible(true);
                        Object[] args = new Object[m.getParameterCount()];
                        args[0] = GLFW_KEY_ESCAPE;
                        args[1] = 0;
                        args[2] = 0;
                        for (int i = 3; i < args.length; i++) args[i] = 0;
                        Object result = m.invoke(screen, args);
                        if (Boolean.TRUE.equals(result)) keyPressedWorked = true;
                    } catch (Exception ignored) {}
                }
            }
            if (!keyPressedWorked) {
                for (Method m : getAllMethods(mc.getClass())) {
                    if (m.getName().equals("setScreen") && m.getParameterCount() == 1) {
                        m.setAccessible(true);
                        m.invoke(mc, (Object) null);
                        return "{\"screen_closed\":true,\"method\":\"setScreen(null)\",\"was\":\"" + sn + "\"}";
                    }
                }
            }
            return "{\"screen_closed\":true,\"method\":\"keyPressed(ESCAPE)\",\"was\":\"" + sn + "\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String switchTab(Object mc, int tabIndex) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen == null) return "{\"error\":\"no screen\"}";
            Object tabBar = null;
            for (Field f : getAllFields(screen.getClass())) {
                String fn = f.getName();
                if (fn.equals("tabNavigationBar") || fn.contains("tabNavigation") || fn.contains("tabBar")) {
                    try {
                        f.setAccessible(true);
                        tabBar = f.get(screen);
                        if (tabBar != null) break;
                    } catch (Exception ignored) {}
                }
            }
            if (tabBar == null) {
                for (Field f : getAllFields(screen.getClass())) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(screen);
                        if (val != null && val.getClass().getSimpleName().equals("TabNavigationBar")) {
                            tabBar = val;
                            break;
                        }
                    } catch (Exception ignored) {}
                }
            }
            if (tabBar == null) return "{\"error\":\"no TabNavigationBar found on " + screen.getClass().getSimpleName() + "\"}";
            for (Method m : getAllMethods(tabBar.getClass())) {
                if (m.getName().equals("selectTab") && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                        m.invoke(tabBar, tabIndex, true);
                        return "{\"switched\":true,\"tab\":" + tabIndex + ",\"screen\":\"" + screen.getClass().getSimpleName() + "\"}";
                    } catch (Exception e) {
                        return "{\"error\":\"selectTab failed: " + e.getMessage() + "\"}";
                    }
                }
            }
            for (Method m : getAllMethods(tabBar.getClass())) {
                String mn = m.getName();
                if (mn.contains("select") && m.getParameterCount() >= 1) {
                    try {
                        m.setAccessible(true);
                        if (m.getParameterCount() == 1) {
                            m.invoke(tabBar, tabIndex);
                        } else if (m.getParameterCount() == 2) {
                            m.invoke(tabBar, tabIndex, true);
                        }
                        return "{\"switched\":true,\"tab\":" + tabIndex + ",\"via\":\"" + mn + "\"}";
                    } catch (Exception e) {
                        return "{\"error\":\"" + mn + " failed: " + e.getMessage() + "\"}";
                    }
                }
            }
            return "{\"error\":\"no selectTab method on TabNavigationBar\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String releaseMouse(Object mc) {
        initClassCache();
        try {
            long handle = getWindowHandle(mc);
            if (handle == 0 || !LWJGL3) return "{\"error\":\"no window handle\"}";
            if (glfwSetInputModeMethod != null) {
                glfwSetInputModeMethod.invoke(null, handle, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
            } else {
                Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
                glfw.getMethod("glfwSetInputMode", long.class, int.class, int.class)
                    .invoke(null, handle, glfw.getField("GLFW_CURSOR").getInt(null), glfw.getField("GLFW_CURSOR_NORMAL").getInt(null));
            }
            Object mouseHandler = getMouseHandler(mc);
            if (mouseHandler != null) {
                for (Field f : getAllFields(mouseHandler.getClass())) {
                    String fn = f.getName().toLowerCase();
                    if (fn.contains("grabbed") && f.getType() == boolean.class) {
                        f.setAccessible(true);
                        f.setBoolean(mouseHandler, false);
                    }
                }
            }
            mouseReleaseActive = true;
            return "{\"mouse_released\":true,\"continuous\":true}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String setGameMode(Object mc, String gameMode) {
        try {
            Object server = null;
            for (Field f : getAllFields(mc.getClass())) {
                String n = f.getName();
                if (n.equals("singleplayerServer") || n.contains("integratedServer")) {
                    try { f.setAccessible(true); server = f.get(mc); } catch (Exception ignored) {}
                }
            }
            if (server == null) return "{\"error\":\"no singleplayer server\"}";
            // Get the player's ServerPlayer entity via server
            for (Method m : getAllMethods(server.getClass())) {
                if (m.getName().equals("getPlayerList") && m.getParameterCount() == 0) {
                    Object playerList = m.invoke(server);
                    // Get all players
                    for (Method pm : getAllMethods(playerList.getClass())) {
                        if ((pm.getName().equals("getPlayers") || pm.getName().equals("getPlayerList"))
                                && pm.getParameterCount() == 0 && java.util.List.class.isAssignableFrom(pm.getReturnType())) {
                            java.util.List<?> players = (java.util.List<?>) pm.invoke(playerList);
                            if (!players.isEmpty()) {
                                Object serverPlayer = players.get(0);
                                // Set game type on the server player
                                for (Method sm : getAllMethods(serverPlayer.getClass())) {
                                    if (sm.getName().equals("setGameMode") || sm.getName().equals("setGameType") || sm.getName().equals("setPlayerMode")) {
                                        if (sm.getParameterCount() == 1) {
                                            Class<?> pt = sm.getParameterTypes()[0];
                                            // Try to find the GameType enum value
                                            for (String gtClass : new String[]{
                                                "net.minecraft.world.level.GameType",
                                                "net.minecraft.world.GameType",
                                                "net.minecraft.server.level.GameType"
                                            }) {
                                                try {
                                                    Class<?> gc = Class.forName(gtClass);
                                                    for (Object e : gc.getEnumConstants()) {
                                                        String en = ((Enum<?>) e).name().toUpperCase();
                                                        if (en.equals(gameMode.toUpperCase()) || en.startsWith(gameMode.toUpperCase().substring(0, 3))) {
                                                            sm.setAccessible(true);
                                                            sm.invoke(serverPlayer, e);
                                                            return "{\"gamemode_set\":true,\"mode\":\"" + en + "\"}";
                                                        }
                                                    }
                                                } catch (ClassNotFoundException ignored) {}
                                            }
                                            // Try integer-based game mode (0=survival, 1=creative, 2=adventure, 3=spectator)
                                            int modeId = gameMode.toLowerCase().equals("creative") ? 1 :
                                                         gameMode.toLowerCase().equals("adventure") ? 2 :
                                                         gameMode.toLowerCase().equals("spectator") ? 3 : 0;
                                            if (pt == int.class) {
                                                sm.setAccessible(true);
                                                sm.invoke(serverPlayer, modeId);
                                                return "{\"gamemode_set\":true,\"mode_id\":" + modeId + "}";
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Try executing command directly on server
            for (Method m : getAllMethods(server.getClass())) {
                if ((m.getName().equals("execute") || m.getName().equals("runCommand"))
                        && m.getParameterCount() >= 1 && m.getParameterTypes()[0] == String.class) {
                    try {
                        m.setAccessible(true);
                        Object result = m.invoke(server, "gamemode " + gameMode + " Player");
                        return "{\"gamemode_set\":true,\"via\":\"server.execute\"}";
                    } catch (Exception ignored) {}
                }
            }
            // Try getCommands().performPrefixedCommand()
            for (Method m : getAllMethods(server.getClass())) {
                if (m.getName().equals("getCommands") && m.getParameterCount() == 0) {
                    Object commands = m.invoke(server);
                    for (Method cm : getAllMethods(commands.getClass())) {
                        if ((cm.getName().equals("performPrefixedCommand") || cm.getName().equals("execute"))
                                && cm.getParameterCount() >= 2) {
                            try {
                                Class<?>[] pts = cm.getParameterTypes();
                                Object source = null;
                                // Create CommandSourceStack from server
                                for (Method sm : getAllMethods(server.getClass())) {
                                    if (sm.getName().equals("createCommandSourceStack") && sm.getParameterCount() == 0) {
                                        source = sm.invoke(server); break;
                                    }
                                }
                                if (source != null) {
                                    cm.setAccessible(true);
                                    cm.invoke(commands, source, "gamemode " + gameMode);
                                    return "{\"gamemode_set\":true,\"via\":\"commands.performPrefixed\"}";
                                }
                            } catch (Exception e) { dbg("setGameMode cmd fail: " + e.getMessage()); }
                        }
                    }
                }
            }
            return "{\"error\":\"could not set gamemode\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String openPauseMenu(Object mc) {
        try {
            for (Method m : getAllMethods(mc.getClass())) {
                String mn = m.getName();
                if (m.getParameterCount() == 0 && (mn.equals("pauseGame") || mn.equals("func_147108_a"))) {
                    try {
                        m.setAccessible(true);
                        m.invoke(mc);
                        return "{\"paused\":true,\"method\":\"" + mn + "\"}";
                    } catch (Exception ignored) {}
                }
            }
            for (Method m : getAllMethods(mc.getClass())) {
                String mn = m.getName();
                if (m.getParameterCount() == 1 && mn.equals("pause")) {
                    try {
                        m.setAccessible(true);
                        m.invoke(mc, false);
                        return "{\"paused\":true,\"method\":\"" + mn + "\"}";
                    } catch (Exception ignored) {}
                }
            }
            Class<?> screenClass = null;
            for (String cn : new String[]{
                "net.minecraft.client.gui.screens.PauseScreen",
                "net.minecraft.client.gui.screens.GameMenuScreen",
                "net.minecraft.client.gui.screen.IngameMenuScreen"
            }) {
                try { screenClass = Class.forName(cn); break; } catch (ClassNotFoundException ignored) {}
            }
            if (screenClass != null) {
                Object screen = null;
                try { screen = screenClass.getConstructor(boolean.class).newInstance(true); }
                catch (Exception ignored) {
                    try { screen = screenClass.getConstructor().newInstance(); }
                    catch (Exception ignored2) {}
                }
            if (screen != null) {
                for (Method m : getAllMethods(mc.getClass())) {
                        if (m.getName().equals("setScreen") && m.getParameterCount() == 1) {
                            try {
                                m.setAccessible(true);
                                m.invoke(mc, screen);
                                return "{\"paused\":true,\"method\":\"setScreen(" + screenClass.getSimpleName() + ")\"}";
                            } catch (Exception ignored3) {}
                        }
                    }
                }
            }
            return "{\"error\":\"no pause method found\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private static volatile boolean mcpControlMode = false;

    public static String enterMcpControlMode(Object mc) {
        initClassCache();
        try {
            mcpControlMode = true;
            mouseReleaseActive = false;
            forceCursorNormal = false;
            McpPlatformControl ctrl = McpControlFactory.get();

            long nativeHwnd = 0;
            long glfwHandle = getWindowHandle(mc);
            if (glfwHandle != 0 && LWJGL3 && glfwClass != null) {
                try {
                    nativeHwnd = (long) glfwClass.getMethod("glfwGetWin32Window", long.class).invoke(null, glfwHandle);
                    if (glfwSetInputModeMethod != null) {
                        glfwSetInputModeMethod.invoke(null, glfwHandle, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                        dbg("enterMcpControlMode: GLFW cursor set to NORMAL");
                    }
                } catch (Exception e1) {
                    try {
                        nativeHwnd = (long) Class.forName("org.lwjgl.glfw.GLFWNativeWin32")
                                .getMethod("glfwGetWin32Window", long.class).invoke(null, glfwHandle);
                    } catch (Exception e2) {
                        dbg("enterMcpControlMode: native hwnd resolution failed (non-Windows?): " + e2.getMessage());
                    }
                }
            }
            if (nativeHwnd == 0) nativeHwnd = glfwHandle;

            boolean hookOk = ctrl.installMouseHook(nativeHwnd);
            dbg("enterMcpControlMode: platform=" + ctrl.getPlatformName() + " hook=" + hookOk + " hwnd=" + Long.toHexString(nativeHwnd));
            ctrl.setControlMode(true);
            return "{\"control_mode\":true,\"platform\":\"" + ctrl.getPlatformName() + "\",\"hook\":" + hookOk + "}";
        } catch (Exception e) { return "{\"error\":\"" + e.getMessage() + "\"}"; }
    }

    public static String exitMcpControlMode(Object mc) {
        try {
            mcpControlMode = false;
            mouseReleaseActive = false;
            forceCursorNormal = false;
            McpPlatformControl ctrl = McpControlFactory.get();
            ctrl.setControlMode(false);
            ctrl.uninstallMouseHook();

            long glfwHandle = getWindowHandle(mc);
            if (glfwHandle != 0 && LWJGL3 && glfwSetInputModeMethod != null) {
                try {
                    glfwSetInputModeMethod.invoke(null, glfwHandle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                    dbg("exitMcpControlMode: GLFW cursor set back to DISABLED");
                } catch (Exception ce) {
                    dbg("exitMcpControlMode: glfwSetInputMode failed: " + ce.getMessage());
                }
            }

            dbg("exitMcpControlMode: OFF platform=" + ctrl.getPlatformName());
            return "{\"control_mode\":false}";
        } catch (Exception e) { return "{\"error\":\"" + e.getMessage() + "\"}"; }
    }

    public static boolean isMcpControlMode() { return mcpControlMode; }

    public static String getMcpControlOverlayTranslationKey() { return "mcpmod.control.overlay"; }

    public static String getMcpControlPauseTransferTranslationKey() { return "mcpmod.control.transfer"; }

    public static boolean shouldRenderMcpControlOverlay(Object mc) {
        return mcpControlMode;
    }

    public static boolean shouldBlockUserMouseInput(Object mc) {
        return mcpControlMode;
    }

    public static boolean shouldBlockUserKeyboardInput(Object mc) {
        return mcpControlMode;
    }

    public static void handleMcpOverlayClicked(Object mc) {
        exitMcpControlMode(mc);
    }

    private static volatile boolean forceCursorNormal = false;
    private static Method glfwHideWindowMethod;
    private static Method glfwShowWindowMethod;
    private static int GLFW_CURSOR_VAL = -1;
    private static int GLFW_CURSOR_NORMAL_VAL = -1;
    private static boolean cursorCacheInit = false;
    private static Field mouseGrabbedField = null;
    private static Field accumulatedDXField = null;
    private static Field accumulatedDYField = null;
    private static boolean mouseFieldsInit = false;
    private static volatile boolean lastForceState = false;
    private static volatile boolean windowHidden = false;
    private static int savedWindowX = 0;
    private static int savedWindowY = 0;

    private static void initCursorCache() throws Exception {
        if (cursorCacheInit) return;
        initClassCache();
        if (glfwSetInputModeMethod == null && glfwClass != null) {
            glfwSetInputModeMethod = glfwClass.getMethod("glfwSetInputMode", long.class, int.class, int.class);
        }
        GLFW_CURSOR_VAL = GLFW_CURSOR;
        GLFW_CURSOR_NORMAL_VAL = GLFW_CURSOR_NORMAL;
        if (glfwClass != null) {
            try { glfwHideWindowMethod = glfwClass.getMethod("glfwHideWindow", long.class); } catch (Exception ignored) {}
            try { glfwShowWindowMethod = glfwClass.getMethod("glfwShowWindow", long.class); } catch (Exception ignored) {}
        }
        cursorCacheInit = true;
    }

    private static void initMouseFields(Object mouseHandler) throws Exception {
        if (mouseFieldsInit) return;
        mouseFieldsInit = true;
        for (Field f : getAllFields(mouseHandler.getClass())) {
            String fn = f.getName().toLowerCase();
            if ((fn.contains("grabbed")) && f.getType() == boolean.class) {
                f.setAccessible(true);
                mouseGrabbedField = f;
                dbg("cursor: found mouseGrabbed=" + f.getName());
            }
            if (fn.contains("accumulateddx") && f.getType() == double.class) {
                f.setAccessible(true);
                accumulatedDXField = f;
                dbg("cursor: found accDX=" + f.getName());
            }
            if (fn.contains("accumulateddy") && f.getType() == double.class) {
                f.setAccessible(true);
                accumulatedDYField = f;
                dbg("cursor: found accDY=" + f.getName());
            }
        }
    }

    public static void setForceCursorNormal(boolean v) { forceCursorNormal = v; }

    public static boolean isForceCursorNormal() { return forceCursorNormal; }

    public static boolean isWindowHidden() { return windowHidden; }

    public static void hideWindow(Object mc) {
        if (windowHidden) return;
        try {
            long handle = getWindowHandle(mc);
            dbg("cursor: hideWindow v2 called, handle=" + handle + " LWJGL3=" + LWJGL3);
            if (handle == 0 || !LWJGL3) return;
            McpPlatformControl ctrl = McpControlFactory.get();
            dbg("cursor: ctrl type=" + ctrl.getClass().getSimpleName());
            if (ctrl instanceof McpWin32Control) {
                McpWin32Control w32 = (McpWin32Control) ctrl;
                w32.ensureHwndFromGlfw(handle);
                long hwnd = w32.getMcHwnd();
                dbg("cursor: hwnd=" + hwnd);
                if (hwnd != 0) {
                    int[] rect = w32.getWindowRect(hwnd);
                    if (rect != null) {
                        savedWindowX = rect[0];
                        savedWindowY = rect[1];
                    }
                    w32.moveWindowOffscreen(hwnd);
                    windowHidden = true;
                    dbg("cursor: window moved offscreen (saved " + savedWindowX + "," + savedWindowY + ")");
                    return;
                }
            }
            windowHidden = true;
            dbg("cursor: hideWindow no-op (not Win32 or no hwnd)");
        } catch (Exception e) {
            dbg("cursor: hideWindow failed: " + e.getMessage());
        }
    }

    public static void showWindow(Object mc) {
        if (!windowHidden) return;
        windowHidden = false;
        dbg("cursor: showWindow skipped (no-op for testing)");
    }

    public static void tickForceCursorNormal(Object mc) {
        if (!forceCursorNormal) {
            if (lastForceState) {
                showWindow(mc);
                lastForceState = false;
                cursorApplied = false;
            }
            return;
        }
        try {
            if (!isMcReady(mc)) return;
            if (!windowHidden) hideWindow(mc);
            if (!cursorApplied) {
                forceCursorAndReleaseMouse(mc);
                cursorApplied = true;
                dbg("cursor: GLFW_CURSOR_NORMAL applied once");
            } else {
                Object mouseHandler = getMouseHandler(mc);
                if (mouseHandler != null) {
                    initMouseFields(mouseHandler);
                    if (mouseGrabbedField != null) mouseGrabbedField.setBoolean(mouseHandler, false);
                    if (accumulatedDXField != null) accumulatedDXField.setDouble(mouseHandler, 0.0);
                    if (accumulatedDYField != null) accumulatedDYField.setDouble(mouseHandler, 0.0);
                }
            }
            lastForceState = true;
        } catch (Exception e) {
            dbg("cursor: " + e.getMessage());
        }
    }

    private static volatile boolean cursorApplied = false;
    private static volatile long forceCursorNormalStartTime = 0;
    private static final long READY_DELAY_MS = 10000;

    private static boolean isMcReady(Object mc) {
        if (forceCursorNormalStartTime == 0) {
            forceCursorNormalStartTime = System.currentTimeMillis();
        }
        if (System.currentTimeMillis() - forceCursorNormalStartTime < READY_DELAY_MS) {
            return false;
        }
        return getWindowHandle(mc) != 0;
    }

    public static String showMcpOverlay(String text, int port) {
        McpPlatformControl ctrl = McpControlFactory.get();
        boolean ok = ctrl.showOverlay(text, port);
        return "{\"overlay_shown\":" + ok + ",\"text\":\"" + text + "\",\"port\":" + port + ",\"platform\":\"" + ctrl.getPlatformName() + "\"}";
    }

    public static String hideMcpOverlay() {
        McpControlFactory.get().hideOverlay();
        return "{\"overlay_hidden\":true}";
    }

    public static String updateMcpOverlayText(String text) {
        McpControlFactory.get().updateOverlayText(text);
        return "{\"overlay_text_updated\":true}";
    }

    public static String platformInjectClick(int x, int y) {
        McpPlatformControl ctrl = McpControlFactory.get();
        if (ctrl instanceof McpWin32Control) {
            McpWin32Control w32 = (McpWin32Control) ctrl;
            try {
                Object mc = getMinecraftInstance();
                long glfwHandle = getWindowHandle(mc);
                dbg("platformInjectClick: glfwHandle=" + glfwHandle);
                if (glfwHandle != 0) {
                    boolean ensured = w32.ensureHwndFromGlfw(glfwHandle);
                    dbg("platformInjectClick: ensureHwnd=" + ensured);
                }
            } catch (Exception e) {
                dbg("platformInjectClick: mc resolve failed: " + e.getMessage());
            }
            boolean ok = w32.injectClickClient(x, y);
            return "{\"inject_click\":" + ok + ",\"x\":" + x + ",\"y\":" + y + "}";
        }
        boolean ok = ctrl.injectClick(x, y);
        return "{\"inject_click\":" + ok + ",\"x\":" + x + ",\"y\":" + y + "}";
    }

    public static String platformInjectKey(int vk) {
        McpPlatformControl ctrl = McpControlFactory.get();
        boolean ok = ctrl.injectKey(vk);
        return "{\"inject_key\":" + ok + ",\"vk\":" + vk + "}";
    }

    public static String getHookStatus() {
        McpPlatformControl ctrl = McpControlFactory.get();
        return "{\"platform\":\"" + ctrl.getPlatformName() +
                "\",\"hook_installed\":" + ctrl.isHookInstalled() +
                ",\"control_mode\":" + ctrl.isControlMode() + "}";
    }

    public static boolean isLWJGL3() { return LWJGL3; }

    private static volatile boolean mouseReleaseActive = false;
    private static volatile boolean screenshotInProgress = false;
    private static volatile byte[] cachedScreenshot = null;
    private static volatile long cachedScreenshotTime = 0;

    public static void setMouseReleaseActive(boolean v) { mouseReleaseActive = v; }

    private static long lastCacheFrameLog = 0;
    public static void cacheFrameFromRenderThread(Object mc) {
        if (System.currentTimeMillis() - cachedScreenshotTime < 1000) return;
        try {
            Object rt = null;
            try { Method m = mc.getClass().getMethod("getMainRenderTarget"); rt = m.invoke(mc); }
            catch (Exception e) { if (System.currentTimeMillis() - lastCacheFrameLog > 5000) { dbg("cacheFrame: getMainRenderTarget failed: " + e.getMessage()); lastCacheFrameLog = System.currentTimeMillis(); } return; }
            if (rt == null) return;
            int w = 0, h = 0;
            for (Field f : getAllFields(rt.getClass())) {
                f.setAccessible(true);
                if (f.getName().equals("width") && f.getType() == int.class) { try { w = f.getInt(rt); } catch (Exception ignored) {} }
                if (f.getName().equals("height") && f.getType() == int.class) { try { h = f.getInt(rt); } catch (Exception ignored) {} }
            }
            if (w <= 0 || h <= 0) return;
            int texId = 0;
            for (Field f : getAllFields(rt.getClass())) {
                f.setAccessible(true);
                Object val = f.get(rt);
                if (val != null && val.getClass().getName().contains("GlTexture")) {
                    for (Field idf : getAllFields(val.getClass())) {
                        if (idf.getName().equals("id") && idf.getType() == int.class) { idf.setAccessible(true); texId = idf.getInt(val); }
                    }
                    break;
                }
            }
            if (texId <= 0) { if (System.currentTimeMillis() - lastCacheFrameLog > 5000) { dbg("cacheFrame: no texId found, rt=" + rt.getClass().getName()); lastCacheFrameLog = System.currentTimeMillis(); } return; }
            suppressGlDebug(true);
            byte[] result = null;
            try { result = readTextureViaGetTexImage(texId, w, h); }
            catch (Exception texEx) {
                try {
                    Class<?> gl11c = Class.forName("org.lwjgl.opengl.GL11C");
                    result = readTextureViaGetTexImageFrom(gl11c, texId, w, h);
                } catch (Exception ignored) {}
            } finally { suppressGlDebug(false); }
            if (result != null && result.length > 0) {
                cachedScreenshot = result;
                cachedScreenshotTime = System.currentTimeMillis();
                if (System.currentTimeMillis() - lastCacheFrameLog > 10000) { dbg("cacheFrame: cached " + result.length + " bytes"); lastCacheFrameLog = System.currentTimeMillis(); }
            }
        } catch (Exception e) { if (System.currentTimeMillis() - lastCacheFrameLog > 5000) { dbg("cacheFrame: exception: " + e.getMessage()); lastCacheFrameLog = System.currentTimeMillis(); } }
    }

    public static byte[] getCachedScreenshot() {
        return cachedScreenshot;
    }
    public static boolean isMouseReleaseActive() { return mouseReleaseActive; }

    private static void forceCursorAndReleaseMouse(Object mc) throws Exception {
        long handle = getWindowHandle(mc);
        if (handle == 0 || !LWJGL3) return;
        initCursorCache();
        if (glfwSetInputModeMethod != null) {
            glfwSetInputModeMethod.invoke(null, handle, GLFW_CURSOR_VAL, GLFW_CURSOR_NORMAL_VAL);
        }
        Object mouseHandler = getMouseHandler(mc);
        if (mouseHandler != null) {
            initMouseFields(mouseHandler);
            if (mouseGrabbedField != null) mouseGrabbedField.setBoolean(mouseHandler, false);
            if (accumulatedDXField != null) accumulatedDXField.setDouble(mouseHandler, 0.0);
            if (accumulatedDYField != null) accumulatedDYField.setDouble(mouseHandler, 0.0);
        }
    }

    public static void tickMouseRelease(Object mc) {
        if (!mouseReleaseActive || screenshotInProgress) return;
        try {
            Object screen = getCurrentScreen(mc);
            if (screen != null) return;
            forceCursorAndReleaseMouse(mc);
        } catch (Exception ignored) {}
    }

    public static void tickMcpControlMode(Object mc) {
        if (!mcpControlMode) return;
        try {
            tickForceCursorNormal(mc);
            forceCursorAndReleaseMouse(mc);
        } catch (Exception e) {
            dbg("tickMcpControlMode: " + e.getMessage());
        }
    }

    private static volatile boolean videoCaptureActive = false;
    private static int videoFrameCounter = 0;
    private static final int VIDEO_FRAME_SKIP = 6;

    public static void setVideoCaptureActive(boolean v) { videoCaptureActive = v; }
    public static boolean isVideoCaptureActive() { return videoCaptureActive; }

    public static byte[] captureFrameJpeg(Object mc) {
        try {
            int w = getGlfwWindowSize(mc, true);
            int h = getGlfwWindowSize(mc, false);
            if (w <= 0 || h <= 0) return null;
            ByteBuffer bb = ByteBuffer.allocateDirect(w * h * 4);
            doGlReadPixels(0, 0, w, h, bb, 0);
            bb.rewind();
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int r = bb.get() & 0xFF;
                    int g = bb.get() & 0xFF;
                    int b = bb.get() & 0xFF;
                    bb.get();
                    img.setRGB(x, h - 1 - y, (r << 16) | (g << 8) | b);
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    public static void tickVideoCapture(Object mc) {
        if (!videoCaptureActive) return;
        if (++videoFrameCounter % VIDEO_FRAME_SKIP != 0) return;
        try {
            byte[] jpeg = captureFrameJpeg(mc);
            if (jpeg != null && jpeg.length > 0) {
                McpWebSocketClient.sendVideoFrame(jpeg);
            }
        } catch (Exception e) {
            dbg("video: " + e.getMessage());
        }
    }
}

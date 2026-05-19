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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

public final class ReflectionHelper {

    private static final boolean LWJGL3;
    private static final boolean HAS_VULKAN;
    private static Robot awtRobot;

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
        try {
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft");
            try {
                return mc.getMethod("getInstance").invoke(null);
            } catch (NoSuchMethodException e) {
                try {
                    return mc.getMethod("getMinecraft").invoke(null);
                } catch (NoSuchMethodException e2) {
                    for (Method m : mc.getMethods()) {
                        if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && m.getReturnType() == mc && m.getParameterCount() == 0) {
                            return m.invoke(null);
                        }
                    }
                    throw new RuntimeException("No static getter found on " + mc.getName());
                }
            }
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
            double scale = getGuiScale(mc);
            double gx = x / scale;
            double gy = y / scale;
            dbg("guiClick: raw(" + x + "," + y + ") -> gui(" + (int)gx + "," + (int)gy + ") scale=" + scale + " screen=" + screenName);
            dbg("guiClick: listing all boolean methods on " + screen.getClass().getName());
            for (Method dm : getAllMethods(screen.getClass())) {
                if (dm.getParameterCount() <= 4 && (dm.getReturnType() == boolean.class)) {
                    dbg("guiClick: bool method: " + dm.getName() + java.util.Arrays.toString(dm.getParameterTypes()) + " from " + dm.getDeclaringClass().getSimpleName());
                }
            }
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
                                    for (Method bm : getAllMethods(w.getClass())) {
                                        Class<?>[] bpt = bm.getParameterTypes();
                                        if (bm.getParameterCount() == 3
                                                && bpt[0] == double.class && bpt[1] == double.class && bpt[2] == int.class
                                                && bm.getReturnType() == boolean.class) {
                                            try {
                                                bm.setAccessible(true);
                                                Object r = bm.invoke(w, relX, relY, button);
                                                dbg("guiClick: widget." + bm.getName() + "(" + (int)relX + "," + (int)relY + ")=" + r);
                                                if (Boolean.TRUE.equals(r)) widgetClicked = true;
                                            } catch (Exception e) {
                                                dbg("guiClick: widget." + bm.getName() + " failed: " + e.getMessage());
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
                return "{\"clicked\":true,\"screen\":\"" + screenName + "\",\"gui\":[" + (int)gx + "," + (int)gy + "],\"scale\":" + scale + ",\"results\":{" + results.toString() + "}" + widgetResult + "}";
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
            for (Field f : getAllFields(screen.getClass())) {
                String fn = f.getName();
                if (fn.equals("children") || fn.equals("renderables") || fn.equals("widgets")) {
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
                            // Also check methods
                            for (Method wm : getAllMethods(w.getClass())) {
                                if (wm.getName().equals("onPress")) hasOnPress = true;
                            }
                            sb.append(String.format("{\"i\":%d,\"c\":\"%s\",\"x\":%d,\"y\":%d,\"w\":%d,\"h\":%d,\"press\":%b}",
                                    idx, cls, x, y, w2, h2, hasOnPress));
                            idx++;
                        }
                    }
                }
            }
            sb.append("],\"total\":" + idx + "}");
            return sb.toString();
        } catch (Exception e) { return "{\"error\":\"" + e.getMessage() + "\"}"; }
    }

    public static String clickButtonByIndex(Object mc, int index) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen == null) return "{\"error\":\"no screen\"}";
            int idx = 0;
            for (Field f : getAllFields(screen.getClass())) {
                String fn = f.getName();
                if (fn.equals("children") || fn.equals("renderables") || fn.equals("widgets") || fn.contains("button")) {
                    f.setAccessible(true);
                    Object list = f.get(screen);
                    if (list instanceof java.util.List) {
                        for (Object widget : (java.util.List<?>) list) {
                            if (idx == index) {
                                dbg("clickButtonByIndex: index=" + index + " class=" + widget.getClass().getSimpleName());
                                for (Field df : getAllFields(widget.getClass())) {
                                    if (df.getName().equals("onPress") || df.getName().contains("press")) {
                                        try { df.setAccessible(true); Object pv = df.get(widget); dbg("clickBtn: field " + df.getName() + "=" + (pv != null ? pv.getClass().getSimpleName() : "null") + " on " + df.getDeclaringClass().getSimpleName()); } catch (Exception ignored) {}
                                    }
                                }
                                // Try onPress method
                                for (Method bm : getAllMethods(widget.getClass())) {
                                    String bn = bm.getName();
                                    if ((bn.equals("onPress") || bn.equals("onClick") || bn.equals("pressAction"))
                                            && bm.getParameterCount() <= 2) {
                                        dbg("clickBtn: found " + bn + " params=" + java.util.Arrays.toString(bm.getParameterTypes()) + " on " + widget.getClass().getSimpleName() + " declared=" + bm.getDeclaringClass().getSimpleName());
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
                                            dbg("clickBtn: invoke OK");
                                            return "{\"clicked\":true,\"index\":" + index + ",\"method\":\"" + bn + "\",\"class\":\"" + widget.getClass().getSimpleName() + "\"}";
                                        } catch (Exception e) { dbg("btn invoke fail " + bn + ": " + e.getMessage()); }
                                    }
                                }
                                // Try onPress field
                                for (Field bf : getAllFields(widget.getClass())) {
                                    String bfn = bf.getName();
                                    if (bfn.equals("onPress")) {
                                        try {
                                            bf.setAccessible(true);
                                            Object pressHandler = bf.get(widget);
                                            if (pressHandler != null) {
                                                for (Method hm : getAllMethods(pressHandler.getClass())) {
                                                    if (hm.getName().equals("accept") && hm.getParameterCount() == 1) {
                                                        hm.setAccessible(true);
                                                        hm.invoke(pressHandler, widget);
                                                        return "{\"clicked\":true,\"index\":" + index + ",\"via\":\"onPress.accept\"}";
                                                    }
                                                    if (hm.getParameterCount() == 0 && (hm.getName().equals("run") || hm.getName().equals("accept"))) {
                                                        hm.setAccessible(true);
                                                        hm.invoke(pressHandler);
                                                        return "{\"clicked\":true,\"index\":" + index + ",\"via\":\"" + hm.getName() + "()\"}";
                                                    }
                                                }
                                            }
                                        } catch (Exception ignored) {}
                                    }
                                }
                                return "{\"error\":\"no press method on index " + index + " (" + widget.getClass().getSimpleName() + ")\"}";
                            }
                            idx++;
                        }
                    }
                }
            }
            return "{\"error\":\"index " + index + " out of range (total " + idx + " widgets)\"}";
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
            if (screen != null) {
                for (Method m : getAllMethods(screen.getClass())) {
                    String n = m.getName();
                    if ((n.equals("charTyped") || n.contains("charTyped")) && m.getParameterCount() >= 2) {
                        try {
                            m.setAccessible(true);
                            Object[] args = new Object[m.getParameterCount()];
                            args[0] = ch;
                            args[1] = modifiers;
                            for (int i = 2; i < args.length; i++) args[i] = 0;
                            m.invoke(screen, args);
                            return "{\"charTyped\":true}";
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

    private static List<Method> getAllMethods(Class<?> clazz) {
        List<Method> methods = new java.util.ArrayList<>();
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            for (Method m : c.getDeclaredMethods()) {
                methods.add(m);
            }
            c = c.getSuperclass();
        }
        return methods;
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
                // Try conn.sendPacket or similar
                for (Method m : getAllMethods(conn.getClass())) {
                    if ((m.getName().equals("sendPacket") || m.getName().contains("sendPacket") || m.getName().contains("func_147297_a"))
                            && m.getParameterCount() == 1) {
                        try {
                            // Construct CPacketChatMessage
                            Class<?> pktClass = Class.forName("net.minecraft.network.play.client.CPacketChatMessage");
                            Object packet = pktClass.getConstructor(String.class).newInstance(msg);
                            m.setAccessible(true); m.invoke(conn, packet);
                            return "{\"sent\":true,\"method\":\"packet\"}";
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
        String failChain = "";
        try {
            dbg("takeScreenshot: trying MC native Screenshot.takeScreenshot()...");
            byte[] nativeResult = takeMcNativeScreenshot(mc);
            if (nativeResult != null) { dbg("takeScreenshot: OK via MC native " + nativeResult.length + " bytes"); return nativeResult; }
        } catch (Exception e) {
            failChain += "native:" + e.getMessage() + " ";
            dbg("takeScreenshot: MC native FAILED - " + e.getMessage());
        }
        try {
            dbg("takeScreenshot: trying glReadPixels + glFinish...");
            byte[] glResult = takeGlScreenshot(mc, width, height);
            if (glResult != null) { dbg("takeScreenshot: OK via glReadPixels " + glResult.length + " bytes"); return glResult; }
        } catch (Exception e) {
            Throwable c = e; while (c.getCause() != null) c = c.getCause();
            failChain += "gl:" + c.getClass().getSimpleName() + ":" + c.getMessage() + " ";
            dbg("takeScreenshot: glReadPixels FAILED - " + failChain);
        }
        try {
            dbg("takeScreenshot: trying Robot window capture...");
            byte[] winResult = takeWindowScreenshot();
            if (winResult != null) { dbg("takeScreenshot: OK via Robot window " + winResult.length + " bytes"); return winResult; }
        } catch (Exception e) {
            failChain += "window:" + e.getMessage() + " ";
            dbg("takeScreenshot: Robot window FAILED - " + e.getMessage());
        }
        try {
            dbg("takeScreenshot: trying platform native capture...");
            byte[] platResult = takePlatformScreenshot();
            if (platResult != null) { dbg("takeScreenshot: OK via platform native " + platResult.length + " bytes"); return platResult; }
        } catch (Exception e) {
            failChain += "platform:" + e.getMessage() + " ";
            dbg("takeScreenshot: platform native FAILED - " + e.getMessage());
        }
        throw new RuntimeException("ALL failed (" + failChain + ") w=" + width + " h=" + height);
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
        if (width <= 0 || height <= 0) {
            throw new RuntimeException("bad dims " + width + "x" + height);
        }

        try {
            Method m = mc.getClass().getMethod("getMainRenderTarget");
            Object fb = m.invoke(mc);
            if (fb != null) {
                int origW = width, origH = height;
                try {
                    Field fw = fb.getClass().getDeclaredField("width");
                    fw.setAccessible(true);
                    int fbw = fw.getInt(fb);
                    Field fh = fb.getClass().getDeclaredField("height");
                    fh.setAccessible(true);
                    int fbh = fh.getInt(fb);
                    if (fbw > 0 && fbh > 0) { width = fbw; height = fbh; }
                } catch (Exception fbEx) {
                    for (Field f : fb.getClass().getDeclaredFields()) {
                        if (f.getType() == int.class) {
                            try { f.setAccessible(true); } catch (Exception ignored) {}
                        }
                    }
                    try {
                        for (Method mm : fb.getClass().getMethods()) {
                            if (mm.getName().contains("width") && mm.getParameterCount() == 0) {
                                int v = ((Number) mm.invoke(fb)).intValue();
                                if (v > 0) { width = v; break; }
                            }
                        }
                    } catch (Exception ignored) {}
                    try {
                        for (Method mm : fb.getClass().getMethods()) {
                            if (mm.getName().contains("height") && mm.getParameterCount() == 0) {
                                int v = ((Number) mm.invoke(fb)).intValue();
                                if (v > 0) { height = v; break; }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (NoSuchMethodException e) {}

        int w = width;
        int h = height;
        ByteBuffer bb = ByteBuffer.allocateDirect(w * h * 4);
        try {
            doGlReadPixels(0, 0, w, h, bb);
        } catch (Exception e) {
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            throw new RuntimeException("glReadPixels failed: " + cause.getClass().getName() + ": " + cause.getMessage(), e);
        }
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
        try { ImageIO.write(img, "png", baos); } catch (java.io.IOException e) {
            throw new RuntimeException("ImageIO.write failed", e);
        }
        byte[] result = baos.toByteArray();
        if (result.length == 0) throw new RuntimeException("empty PNG w=" + w + " h=" + h);
        return result;
    }

    private static void doGlReadPixels(int x, int y, int w, int h, ByteBuffer bb) throws Exception {
        if (!LWJGL3) {
            try {
                Class<?> displayClass = Class.forName("org.lwjgl.opengl.Display");
                try {
                    displayClass.getMethod("makeCurrent").invoke(null);
                } catch (Exception ignored) {}
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

        try {
            int GL_FRONT = 0x0404;
            for (Method m : gl11.getMethods()) {
                if (m.getName().equals("glReadBuffer") && m.getParameterCount() == 1) {
                    try { m.setAccessible(true); m.invoke(null, GL_FRONT); dbg("doGlRead: set read buffer to FRONT"); }
                    catch (Exception ignored) {}
                    break;
                }
            }
        } catch (Exception ignored) {}

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

    static void dbg(String msg) {
        try {
            String home = System.getProperty("user.home");
            java.io.FileWriter fw = new java.io.FileWriter(home + java.io.File.separator + "mcp_debug.log", true);
            fw.write(System.currentTimeMillis() + " " + msg + "\n");
            fw.close();
        } catch (Exception e) {
            try {
                System.err.println("[MCP-DBG] " + msg + " (write err: " + e.getMessage() + ")");
            } catch (Exception ignored2) {}
        }
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

    public static void setCursorPos(long handle, double x, double y) {
        if (LWJGL3) {
            try {
                Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
                Method setPos = glfw.getMethod("glfwSetCursorPos", long.class, double.class, double.class);
                setPos.invoke(null, handle, x, y);

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
        List<Field> fields = new java.util.ArrayList<>();
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                fields.add(f);
            }
            c = c.getSuperclass();
        }
        return fields;
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
                Field f = obj.getClass().getDeclaredField(name);
                f.setAccessible(true);
                return f.getDouble(obj);
            } catch (NoSuchFieldException ignored) {}
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

    private static volatile boolean mcpControlMode = false;

    public static String enterMcpControlMode(Object mc) {
        try {
            mcpControlMode = true;
            McpPlatformControl ctrl = McpControlFactory.get();

            long nativeHwnd = 0;
            long glfwHandle = getWindowHandle(mc);
            if (glfwHandle != 0 && LWJGL3) {
                try {
                    Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
                    nativeHwnd = (long) glfw.getMethod("glfwGetWin32Window", long.class).invoke(null, glfwHandle);
                    try {
                        int GLFW_CURSOR_NORMAL = glfw.getField("GLFW_CURSOR_NORMAL").getInt(null);
                        int GLFW_CURSOR = glfw.getField("GLFW_CURSOR").getInt(null);
                        glfw.getMethod("glfwSetInputMode", long.class, int.class, int.class)
                            .invoke(null, glfwHandle, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                        dbg("enterMcpControlMode: GLFW cursor set to NORMAL");
                    } catch (Exception ce) {
                        dbg("enterMcpControlMode: glfwSetInputMode failed: " + ce.getMessage());
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
            forceCursorNormal = true;
            dbg("enterMcpControlMode: platform=" + ctrl.getPlatformName() + " hook=" + hookOk + " hwnd=" + Long.toHexString(nativeHwnd));
            return "{\"control_mode\":true,\"platform\":\"" + ctrl.getPlatformName() + "\",\"hook\":" + hookOk + "}";
        } catch (Exception e) { return "{\"error\":\"" + e.getMessage() + "\"}"; }
    }

    public static String exitMcpControlMode(Object mc) {
        try {
            mcpControlMode = false;
            forceCursorNormal = false;
            McpPlatformControl ctrl = McpControlFactory.get();
            ctrl.setControlMode(false);
            ctrl.uninstallMouseHook();

            long glfwHandle = getWindowHandle(mc);
            if (glfwHandle != 0 && LWJGL3) {
                try {
                    Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
                    int GLFW_CURSOR_DISABLED = glfw.getField("GLFW_CURSOR_DISABLED").getInt(null);
                    int GLFW_CURSOR = glfw.getField("GLFW_CURSOR").getInt(null);
                    glfw.getMethod("glfwSetInputMode", long.class, int.class, int.class)
                        .invoke(null, glfwHandle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
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

    private static volatile boolean forceCursorNormal = false;
    private static Method glfwSetInputModeMethod;
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
        Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
        GLFW_CURSOR_VAL = glfw.getField("GLFW_CURSOR").getInt(null);
        GLFW_CURSOR_NORMAL_VAL = glfw.getField("GLFW_CURSOR_NORMAL").getInt(null);
        glfwSetInputModeMethod = glfw.getMethod("glfwSetInputMode", long.class, int.class, int.class);
        try { glfwHideWindowMethod = glfw.getMethod("glfwHideWindow", long.class); } catch (Exception ignored) {}
        try { glfwShowWindowMethod = glfw.getMethod("glfwShowWindow", long.class); } catch (Exception ignored) {}
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
            if (handle == 0 || !LWJGL3) return;
            initCursorCache();
            McpPlatformControl ctrl = McpControlFactory.get();
            if (ctrl instanceof McpWin32Control) {
                McpWin32Control w32 = (McpWin32Control) ctrl;
                w32.ensureHwndFromGlfw(handle);
                long hwnd = w32.getMcHwnd();
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
            if (glfwHideWindowMethod != null) {
                glfwHideWindowMethod.invoke(null, handle);
                windowHidden = true;
                dbg("cursor: window hidden via glfwHideWindow (fallback)");
            }
        } catch (Exception e) {
            dbg("cursor: hideWindow failed: " + e.getMessage());
        }
    }

    public static void showWindow(Object mc) {
        if (!windowHidden) return;
        try {
            long handle = getWindowHandle(mc);
            if (handle == 0 || !LWJGL3) return;
            initCursorCache();
            McpPlatformControl ctrl = McpControlFactory.get();
            if (ctrl instanceof McpWin32Control) {
                McpWin32Control w32 = (McpWin32Control) ctrl;
                long hwnd = w32.getMcHwnd();
                if (hwnd != 0) {
                    w32.moveWindowBack(hwnd, savedWindowX, savedWindowY);
                    windowHidden = false;
                    dbg("cursor: window moved back onscreen");
                    return;
                }
            }
            if (glfwShowWindowMethod != null) {
                glfwShowWindowMethod.invoke(null, handle);
                windowHidden = false;
                dbg("cursor: window shown via glfwShowWindow (fallback)");
            }
        } catch (Exception e) {
            dbg("cursor: showWindow failed: " + e.getMessage());
        }
    }

    public static void tickForceCursorNormal(Object mc) {
        if (!forceCursorNormal) {
            if (lastForceState) {
                showWindow(mc);
                lastForceState = false;
            }
            return;
        }
        try {
            long handle = getWindowHandle(mc);
            if (handle == 0 || !LWJGL3) return;
            initCursorCache();

            hideWindow(mc);

            glfwSetInputModeMethod.invoke(null, handle, GLFW_CURSOR_VAL, GLFW_CURSOR_NORMAL_VAL);

            Object mouseHandler = getMouseHandler(mc);
            if (mouseHandler != null) {
                initMouseFields(mouseHandler);
                if (mouseGrabbedField != null) {
                    mouseGrabbedField.setBoolean(mouseHandler, false);
                }
                if (accumulatedDXField != null) accumulatedDXField.setDouble(mouseHandler, 0.0);
                if (accumulatedDYField != null) accumulatedDYField.setDouble(mouseHandler, 0.0);
            }

            lastForceState = true;
        } catch (Exception e) {
            dbg("cursor: " + e.getMessage());
        }
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
            doGlReadPixels(0, 0, w, h, bb);
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

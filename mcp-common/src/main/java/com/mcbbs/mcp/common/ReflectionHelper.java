package com.mcbbs.mcp.common;

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

    public static long getWindowHandle(Object mc) {
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
                        if (val != 0) return val;
                    }
                } catch (NoSuchMethodException ignored) {}
            }
            for (Field f : window.getClass().getDeclaredFields()) {
                if (f.getType() == long.class) {
                    f.setAccessible(true);
                    long val = f.getLong(window);
                    if (val != 0) return val;
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
            try {
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
            } catch (Exception e) {
                Class<?> glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
                String getter = isWidth ? "glfwGetWindowWidth" : "glfwGetWindowHeight";
                return ((Number) glfwClass.getMethod(getter, long.class).invoke(null, handle)).intValue();
            }
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
            Object conn = null;
            try { conn = player.getClass().getMethod("connection").invoke(player); }
            catch (NoSuchMethodException ignored) {
                conn = fieldOrNull(player, "connection");
                if (conn == null) conn = fieldOrNull(player, "sendQueue");
                if (conn == null) conn = fieldOrNull(player, "field_71174_a");
            }
            if (conn != null) {
                try { conn.getClass().getMethod("sendCommand", String.class).invoke(conn, cmd); return "sent: " + cmd; }
                catch (NoSuchMethodException ignored) {}
            }
            for (Method m : player.getClass().getMethods()) {
                if ((m.getName().contains("chat") || m.getName().contains("Chat")) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) {
                    m.invoke(player, "/" + cmd);
                    return "sent: " + cmd;
                }
            }
            return "{\"error\":\"no command method found\"}";
        } catch (Exception e) { return "{\"error\":\"" + e.getMessage() + "\"}"; }
    }

    public static byte[] takeScreenshot(Object mc, int width, int height) {
        try {
            byte[] nativeResult = takeMcNativeScreenshot(mc);
            if (nativeResult != null) return nativeResult;
        } catch (Exception e) {
            System.err.println("[MCP-SS] native failed: " + e.getMessage());
        }
        try {
            byte[] winResult = takeWindowScreenshot();
            if (winResult != null) return winResult;
        } catch (Exception e) {
            System.err.println("[MCP-SS] window failed: " + e.getMessage());
        }
        try {
            byte[] glResult = takeGlScreenshot(mc, width, height);
            if (glResult != null) return glResult;
        } catch (Exception e) {
            System.err.println("[MCP-SS] gl failed: " + e.getMessage());
        }
        System.err.println("[MCP-SS] ALL screenshot methods failed (w=" + width + " h=" + height + ")");
        return null;
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
                        errorHolder[0] = e;
                    }
                }
            };

            takeScreenshot.setAccessible(true);
            takeScreenshot.invoke(null, renderTarget, consumer);

            if (errorHolder[0] != null) {
                System.err.println("[MCP-Native] consumer error: " + errorHolder[0].getMessage());
                return null;
            }
            return (byte[]) imageHolder[0];
        } catch (Exception e) {
            System.err.println("[MCP-Native] failed: " + e.getMessage());
            return null;
        }
    }

    private static byte[] takeGlScreenshot(Object mc, int width, int height) {
        try {
            return takeGlScreenshot0(mc, width, height);
        } catch (Exception e) {
            System.err.println("[MCP-GL] failed: " + e.getMessage());
            return null;
        }
    }

    private static byte[] takeGlScreenshot0(Object mc, int width, int height) throws Exception {
        if (width <= 0 || height <= 0) {
            System.err.println("[MCP-GL] bad dims: " + width + "x" + height);
            return null;
        }

        try {
            Method m = mc.getClass().getMethod("getMainRenderTarget");
            Object fb = m.invoke(mc);
            if (fb != null) {
                try {
                    Field fw = fb.getClass().getDeclaredField("width");
                    fw.setAccessible(true);
                    int fbw = fw.getInt(fb);
                    Field fh = fb.getClass().getDeclaredField("height");
                    fh.setAccessible(true);
                    int fbh = fh.getInt(fb);
                    if (fbw > 0 && fbh > 0) { width = fbw; height = fbh; }
                } catch (Exception fbEx) {
                    try {
                        int fbw = (Integer) fb.getClass().getMethod("getViewWidth").invoke(fb);
                        int fbh = (Integer) fb.getClass().getMethod("getViewHeight").invoke(fb);
                        if (fbw > 0 && fbh > 0) { width = fbw; height = fbh; }
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
            System.err.println("[MCP-GL] glReadPixels failed: " + e.getMessage());
            return null;
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
        try { ImageIO.write(img, "png", baos); } catch (java.io.IOException e) { return null; }
        return baos.toByteArray();
    }

    private static void doGlReadPixels(int x, int y, int w, int h, ByteBuffer bb) throws Exception {
        Class<?> gl11 = Class.forName("org.lwjgl.opengl.GL11");
        int GL_RGBA = gl11.getDeclaredField("GL_RGBA").getInt(null);
        int GL_UB = gl11.getDeclaredField("GL_UNSIGNED_BYTE").getInt(null);
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

    private static Object getMouseHandler(Object mc) {
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
            try {
                Class<?> glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
                Method getX = glfwClass.getMethod("glfwGetWindowPosX", long.class);
                Method getY = glfwClass.getMethod("glfwGetWindowPosY", long.class);
                wx = ((Number) getX.invoke(null, handle)).intValue();
                wy = ((Number) getY.invoke(null, handle)).intValue();
            } catch (Exception ignored) {}
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
            Object mc = getMinecraftInstance();
            if (!hasWindow(mc)) return null;
            long handle = getWindowHandle(mc);
            if (handle == 0) return null;
            int wx = 0, wy = 0, ww = 800, wh = 600;
            try {
                Class<?> glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
                wx = ((Number) glfwClass.getMethod("glfwGetWindowPosX", long.class).invoke(null, handle)).intValue();
                wy = ((Number) glfwClass.getMethod("glfwGetWindowPosY", long.class).invoke(null, handle)).intValue();
                int[] wArr = {0}, hArr = {0};
                try {
                    Class<?> intBufClass = Class.forName("org.lwjgl.system.MemoryUtil");
                    Object wBuf = intBufClass.getMethod("memAllocInt", int.class).invoke(null, 1);
                    Object hBuf = intBufClass.getMethod("memAllocInt", int.class).invoke(null, 1);
                    glfwClass.getMethod("glfwGetWindowSize", long.class, Class.forName("java.nio.IntBuffer"), Class.forName("java.nio.IntBuffer"))
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

    public static void lwjgl2PressKey(int keyCode) {}

    public static void lwjgl2ReleaseKey(int keyCode) {}

    public static void lwjgl2MouseNext() {}

    public static void lwjgl2SetMouseButton(int button, boolean pressed) {}

    private static Object getPlayer(Object mc) throws Exception {
        try { return mc.getClass().getMethod("player").invoke(mc); } catch (NoSuchMethodException ignored) {}
        try {
            Field f = mc.getClass().getDeclaredField("player");
            f.setAccessible(true);
            Object p = f.get(mc);
            if (p != null) return p;
        } catch (Exception ignored) {}
        Object f = fieldOrNull(mc, "thePlayer");
        if (f != null) return f;
        return fieldOrNull(mc, "field_71439_g");
    }

    private static Object getLevel(Object mc) throws Exception {
        try { return mc.getClass().getMethod("level").invoke(mc); } catch (NoSuchMethodException ignored) {}
        try { return mc.getClass().getMethod("world").invoke(mc); } catch (NoSuchMethodException ignored) {}
        Object f = fieldOrNull(mc, "theWorld");
        if (f != null) return f;
        return fieldOrNull(mc, "field_71441_f");
    }

    private static Object getPlayerLevel(Object player) throws Exception {
        try { return player.getClass().getMethod("level").invoke(player); } catch (NoSuchMethodException ignored) {}
        try { return player.getClass().getMethod("world").invoke(player); } catch (NoSuchMethodException ignored) {}
        Object f = fieldOrNull(player, "theWorld");
        if (f != null) return f;
        return fieldOrNull(player, "field_70170_p");
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
}

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
            Object window = mc.getClass().getMethod("getWindow").invoke(mc);
            if (window == null) return 0;
            try {
                return ((Number) window.getClass().getMethod("handle").invoke(window)).longValue();
            } catch (NoSuchMethodException e) {
                return ((Number) window.getClass().getMethod("getHandle").invoke(window)).longValue();
            }
        } catch (NoSuchMethodException e) {
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public static boolean hasWindow(Object mc) {
        try { mc.getClass().getMethod("getWindow"); return true; }
        catch (NoSuchMethodException e) { return false; }
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
        if (HAS_VULKAN) {
            byte[] vk = takeWindowScreenshot();
            if (vk != null) return vk;
        }
        try {
            if (width <= 0 || height <= 0) return null;

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

            final int w = width;
            final int h = height;
            final ByteBuffer bb = ByteBuffer.allocateDirect(w * h * 4);
            final CountDownLatch latch = new CountDownLatch(1);
            final Exception[] captureError = new Exception[1];

            Runnable captureTask = new Runnable() {
                @Override
                public void run() {
                    try {
                        doGlReadPixels(0, 0, w, h, bb);
                        bb.rewind();
                    } catch (Exception ex) {
                        captureError[0] = ex;
                    } finally {
                        latch.countDown();
                    }
                }
            };

            boolean scheduled = false;
            for (Method m : mc.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == Runnable.class) {
                    try {
                        m.setAccessible(true);
                        m.invoke(mc, captureTask);
                        scheduled = true;
                        break;
                    } catch (Exception ignored) {}
                }
            }
            if (!scheduled) {
                captureTask.run();
            }

            if (!latch.await(10, TimeUnit.SECONDS)) {
                return takeWindowScreenshot();
            }
            if (captureError[0] != null) {
                return takeWindowScreenshot();
            }

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
            return baos.toByteArray();
        } catch (Exception e) {
            return takeWindowScreenshot();
        }
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
        try {
            Robot r = awtRobot;
            if (r == null) return;
            int vk = glfwToAwt(key);
            if (vk < 0) return;
            if (action == 1) r.keyPress(vk);
            else r.keyRelease(vk);
        } catch (Exception e) { System.err.println("[Input] sendKey: " + e.getMessage()); }
    }

    public static void sendMouseButton(long handle, int button, int action) {
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
        try {
            Robot r = awtRobot;
            if (r == null) return;
            r.mouseWheel((int) scrollY);
        } catch (Exception e) { System.err.println("[Input] sendScroll: " + e.getMessage()); }
    }

    public static void setCursorPos(long handle, double x, double y) {
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
                    Object window = mc.getClass().getMethod("getWindow").invoke(mc);
                    Field wf = window.getClass().getDeclaredField("width");
                    wf.setAccessible(true);
                    ww = wf.getInt(window);
                    Field hf = window.getClass().getDeclaredField("height");
                    hf.setAccessible(true);
                    wh = hf.getInt(window);
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

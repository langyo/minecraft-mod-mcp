package com.mcbbs.mcp.common;

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

    static {
        boolean v = false;
        try { Class.forName("org.lwjgl.glfw.GLFW"); v = true; } catch (ClassNotFoundException e) {}
        LWJGL3 = v;
    }

    private ReflectionHelper() {}

    public static Object getMinecraftInstance() {
        try {
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft");
            try {
                return mc.getMethod("getInstance").invoke(null);
            } catch (NoSuchMethodException e) {
                return mc.getMethod("getMinecraft").invoke(null);
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

    public static int getDisplayWidth(Object mc) {
        try {
            Field f = mc.getClass().getDeclaredField("displayWidth");
            f.setAccessible(true);
            return f.getInt(mc);
        } catch (Exception e) { return 0; }
    }

    public static int getDisplayHeight(Object mc) {
        try {
            Field f = mc.getClass().getDeclaredField("displayHeight");
            f.setAccessible(true);
            return f.getInt(mc);
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

            try {
                Method execute = mc.getClass().getMethod("execute", Runnable.class);
                execute.invoke(mc, captureTask);
            } catch (NoSuchMethodException e) {
                try {
                    mc.getClass().getMethod("addScheduledTask", Runnable.class).invoke(mc, captureTask);
                } catch (NoSuchMethodException e2) {
                    captureTask.run();
                }
            }

            if (!latch.await(3, TimeUnit.SECONDS)) return null;
            if (captureError[0] != null) return null;

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
            return null;
        }
    }

    private static void doGlReadPixels(int x, int y, int w, int h, ByteBuffer bb) throws Exception {
        if (LWJGL3) {
            Class<?> gl11 = Class.forName("org.lwjgl.opengl.GL11");
            int GL_RGBA = gl11.getDeclaredField("GL_RGBA").getInt(null);
            int GL_UB = gl11.getDeclaredField("GL_UNSIGNED_BYTE").getInt(null);
            findAndInvoke(gl11, "glReadPixels", new Object[]{x, y, w, h, GL_RGBA, GL_UB, bb});
        } else {
            Class<?> gl11 = Class.forName("org.lwjgl.opengl.GL11");
            int GL_RGBA = gl11.getDeclaredField("GL_RGBA").getInt(null);
            int GL_UB = Class.forName("org.lwjgl.GL11").getDeclaredField("GL_UNSIGNED_BYTE").getInt(null);
            findAndInvoke(gl11, "glReadPixels", new Object[]{x, y, w, h, GL_RGBA, GL_UB, bb});
        }
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

    public static void sendKey(long handle, int key, int action) {}

    public static void sendMouseButton(long handle, int button, int action) {}

    public static void sendScroll(long handle, double scrollY) {}

    public static void setCursorPos(long handle, double x, double y) {}

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

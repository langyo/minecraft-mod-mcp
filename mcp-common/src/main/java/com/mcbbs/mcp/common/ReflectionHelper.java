package com.mcbbs.mcp.common;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

public final class ReflectionHelper {

    private static final boolean LWJGL3;

    static {
        boolean lwjgl3 = false;
        try {
            Class.forName("org.lwjgl.glfw.GLFW");
            lwjgl3 = true;
        } catch (ClassNotFoundException e) {}
        LWJGL3 = lwjgl3;
    }

    private ReflectionHelper() {}

    public static Object getMinecraftInstance() {
        try {
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft");
            try {
                Method m = mc.getMethod("getInstance");
                return m.invoke(null);
            } catch (NoSuchMethodException e) {
                Method m = mc.getMethod("getMinecraft");
                return m.invoke(null);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get Minecraft instance", e);
        }
    }

    public static long getWindowHandle(Object mc) {
        try {
            Method getWindow = mc.getClass().getMethod("getWindow");
            Object window = getWindow.invoke(mc);
            if (window == null) return 0;
            try {
                Method m = window.getClass().getMethod("handle");
                return ((Number) m.invoke(window)).longValue();
            } catch (NoSuchMethodException e) {
                Method m = window.getClass().getMethod("getHandle");
                return ((Number) m.invoke(window)).longValue();
            }
        } catch (NoSuchMethodException e) {
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public static boolean hasWindow(Object mc) {
        try {
            mc.getClass().getMethod("getWindow");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public static int getDisplayWidth(Object mc) {
        try {
            Field f = mc.getClass().getDeclaredField("displayWidth");
            f.setAccessible(true);
            return f.getInt(mc);
        } catch (Exception e) {
            return 0;
        }
    }

    public static int getDisplayHeight(Object mc) {
        try {
            Field f = mc.getClass().getDeclaredField("displayHeight");
            f.setAccessible(true);
            return f.getInt(mc);
        } catch (Exception e) {
            return 0;
        }
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
                Object worldData = invokeOrNull(server, "getWorldData");
                if (worldData != null) {
                    worldName = invokeString(worldData, "getLevelName");
                }
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
        Object level = player.getClass().getMethod("level").invoke(player);
        Object dim = level.getClass().getMethod("dimension").invoke(level);
        try {
            return (String) dim.getClass().getMethod("identifier").invoke(dim);
        } catch (NoSuchMethodException e) {}
        try {
            Object loc = dim.getClass().getMethod("location").invoke(dim);
            return loc.toString();
        } catch (NoSuchMethodException e) {}
        try {
            Object key = dim.getClass().getMethod("getRegistryName").invoke(dim);
            return key.toString();
        } catch (NoSuchMethodException e) {}
        return "overworld";
    }

    public static String getDifficultyKey(Object level) throws Exception {
        Object diff = level.getClass().getMethod("getDifficulty").invoke(level);
        try {
            return (String) diff.getClass().getMethod("getSerializedName").invoke(diff);
        } catch (NoSuchMethodException e) {}
        try {
            return (String) diff.getClass().getMethod("getKey").invoke(diff);
        } catch (NoSuchMethodException e) {}
        return (String) diff.getClass().getMethod("getDifficultyKey").invoke(diff);
    }

    public static String getGameType(Object mc) throws Exception {
        try {
            Object gm = mc.getClass().getMethod("gameMode").invoke(mc);
            Object pt = gm.getClass().getMethod("getPlayerMode").invoke(gm);
            return (String) pt.getClass().getMethod("getName").invoke(pt);
        } catch (NoSuchMethodException e) {
            return "survival";
        }
    }

    public static String sendCommand(Object mc, String cmd) {
        try {
            Object player = getPlayer(mc);
            if (player == null) return "{\"error\":\"no player\"}";
            try {
                Object conn = player.getClass().getMethod("connection").invoke(player);
                conn.getClass().getMethod("sendCommand", String.class).invoke(conn, cmd);
                return "sent: " + cmd;
            } catch (NoSuchMethodException e) {
                player.getClass().getMethod("sendChatMessage", String.class).invoke(player, "/" + cmd);
                return "sent: " + cmd;
            }
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static byte[] takeScreenshot(Object mc, int width, int height) {
        try {
            if (width <= 0 || height <= 0) return null;

            try {
                Method m = mc.getClass().getMethod("getMainRenderTarget");
                Object fb = m.invoke(mc);
                if (fb != null) {
                    Field fw = fb.getClass().getDeclaredField("width");
                    fw.setAccessible(true);
                    int fbw = fw.getInt(fb);
                    Field fh = fb.getClass().getDeclaredField("height");
                    fh.setAccessible(true);
                    int fbh = fh.getInt(fb);
                    if (fbw > 0 && fbh > 0) {
                        width = fbw;
                        height = fbh;
                    }
                }
            } catch (NoSuchMethodException e) {}

            final int w = width;
            final int h = height;
            final ByteBuffer bb = ByteBuffer.allocateDirect(w * h * 4);
            final Object[] result = new Object[1];

            Runnable captureTask = new Runnable() {
                @Override
                public void run() {
                    try {
                        glReadPixels(0, 0, w, h, bb);
                        bb.rewind();
                        result[0] = "ok";
                    } catch (Exception ex) {
                        result[0] = ex.getMessage();
                    }
                }
            };

            try {
                Method execute = mc.getClass().getMethod("execute", Runnable.class);
                execute.invoke(mc, captureTask);
            } catch (NoSuchMethodException e) {
                try {
                    Method schedTask = mc.getClass().getMethod("addScheduledTask", Runnable.class);
                    schedTask.invoke(mc, captureTask);
                } catch (NoSuchMethodException e2) {
                    captureTask.run();
                }
            }

            Thread.sleep(200);

            if (!"ok".equals(result[0])) return null;

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
            e.printStackTrace();
            return null;
        }
    }

    private static void glReadPixels(int x, int y, int w, int h, ByteBuffer bb) throws Exception {
        if (LWJGL3) {
            Class<?> gl11 = Class.forName("org.lwjgl.opengl.GL11");
            int GL_RGBA = gl11.getDeclaredField("GL_RGBA").getInt(null);
            int GL_UNSIGNED_BYTE = gl11.getDeclaredField("GL_UNSIGNED_BYTE").getInt(null);
            Method m = gl11.getMethod("glReadPixels", int.class, int.class, int.class, int.class, int.class, int.class, ByteBuffer.class);
            m.invoke(null, x, y, w, h, GL_RGBA, GL_UNSIGNED_BYTE, bb);
        } else {
            Class<?> gl11 = Class.forName("org.lwjgl.opengl.GL11");
            int GL_RGBA = gl11.getDeclaredField("GL_RGBA").getInt(null);
            int GL_UNSIGNED_BYTE = Class.forName("org.lwjgl.GL11").getDeclaredField("GL_UNSIGNED_BYTE").getInt(null);
            Method m = gl11.getMethod("glReadPixels", int.class, int.class, int.class, int.class, int.class, int.class, ByteBuffer.class);
            m.invoke(null, x, y, w, h, GL_RGBA, GL_UNSIGNED_BYTE, bb);
        }
    }

    public static void sendKey(long handle, int key, int action) {
        if (!LWJGL3) return;
        try {
            Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
            Class<?> cbIface = Class.forName("org.lwjgl.glfw.GLFWKeyCallbackI");
            Method setCb = glfw.getMethod("glfwSetKeyCallback", long.class, cbIface);
            Object cb = java.lang.reflect.Proxy.newProxyInstance(
                    glfw.getClassLoader(), new Class<?>[]{cbIface},
                    (proxy, method, args) -> null);
            Object stored = setCb.invoke(null, handle, cb);
            Method invoke = stored.getClass().getMethod("invoke", long.class, int.class, int.class, int.class, long.class);
            invoke.invoke(stored, handle, key, 0, action, 0L);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendMouseButton(long handle, int button, int action) {
        if (!LWJGL3) return;
        try {
            Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
            Class<?> cbIface = Class.forName("org.lwjgl.glfw.GLFWMouseButtonCallbackI");
            Method setCb = glfw.getMethod("glfwSetMouseButtonCallback", long.class, cbIface);
            Object cb = java.lang.reflect.Proxy.newProxyInstance(
                    glfw.getClassLoader(), new Class<?>[]{cbIface},
                    (proxy, method, args) -> null);
            Object stored = setCb.invoke(null, handle, cb);
            Method invoke = stored.getClass().getMethod("invoke", long.class, int.class, int.class, double.class);
            invoke.invoke(stored, handle, button, action, 0.0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendScroll(long handle, double scrollY) {
        if (!LWJGL3) return;
        try {
            Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
            Class<?> cbIface = Class.forName("org.lwjgl.glfw.GLFWScrollCallbackI");
            Method setCb = glfw.getMethod("glfwSetScrollCallback", long.class, cbIface);
            Object cb = java.lang.reflect.Proxy.newProxyInstance(
                    glfw.getClassLoader(), new Class<?>[]{cbIface},
                    (proxy, method, args) -> null);
            Object stored = setCb.invoke(null, handle, cb);
            Method invoke = stored.getClass().getMethod("invoke", long.class, double.class, double.class);
            invoke.invoke(stored, handle, 0.0, scrollY);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setCursorPos(long handle, double x, double y) {
        if (!LWJGL3) return;
        try {
            Class.forName("org.lwjgl.glfw.GLFW")
                    .getMethod("glfwSetCursorPos", long.class, double.class, double.class)
                    .invoke(null, handle, x, y);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void lwjgl2PressKey(int keyCode) {
        if (LWJGL3) return;
        try {
            Class.forName("org.lwjgl.input.Keyboard")
                    .getMethod("pressKey", int.class, boolean.class)
                    .invoke(null, keyCode, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void lwjgl2ReleaseKey(int keyCode) {
        if (LWJGL3) return;
        try {
            Class.forName("org.lwjgl.input.Keyboard")
                    .getMethod("releaseKey", int.class)
                    .invoke(null, keyCode);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void lwjgl2MouseNext() {
        if (LWJGL3) return;
        try {
            Class.forName("org.lwjgl.input.Mouse").getMethod("next").invoke(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Object getPlayer(Object mc) throws Exception {
        return mc.getClass().getMethod("player").invoke(mc);
    }

    private static Object getLevel(Object mc) throws Exception {
        try {
            return mc.getClass().getMethod("level").invoke(mc);
        } catch (NoSuchMethodException e) {
            return mc.getClass().getMethod("world").invoke(mc);
        }
    }

    private static String invokeString(Object obj, String methodName) {
        try {
            Object result = obj.getClass().getMethod(methodName).invoke(obj);
            return result != null ? result.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static Object invokeOrNull(Object obj, String methodName) {
        try {
            return obj.getClass().getMethod(methodName).invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private static double getDouble(Object obj, String... fieldNames) throws Exception {
        for (String name : fieldNames) {
            try {
                Field f = obj.getClass().getDeclaredField(name);
                f.setAccessible(true);
                return f.getDouble(obj);
            } catch (NoSuchFieldException ignored) {}
        }
        return 0.0;
    }

    public static boolean isLwjgl3() {
        return LWJGL3;
    }
}

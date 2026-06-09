package xyz.langyo.minecraft.mcp.common;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

public final class ScreenshotHelper {

    private static volatile boolean screenshotInProgress = false;

    public static boolean isScreenshotInProgress() { return screenshotInProgress; }

    private static volatile byte[] cachedScreenshot = null;
    private static volatile long cachedScreenshotTime = 0;
    private static long lastCacheFrameLog = 0;

    private static volatile boolean videoCaptureActive = false;
    private static int videoFrameCounter = 0;
    private static final int VIDEO_FRAME_SKIP = 6;

    private static int cachedSkyColor = 0x7FB5E3;
    private static boolean skyColorPathChecked = false;
    private static int skyColorPath = 0;

    private ScreenshotHelper() {}

    public static byte[] takeScreenshot(Object mc, int width, int height) {
        screenshotInProgress = true;
        try {
            return takeScreenshot0(mc, width, height);
        } finally {
            screenshotInProgress = false;
        }
    }

    private static void forceRenderOneFrame(Object mc) {
    }

    private static byte[] takeScreenshot0(Object mc, int width, int height) {
        byte[] cached = cachedScreenshot;
        if (cached != null && System.currentTimeMillis() - cachedScreenshotTime < 2000) {
            ReflectionHelper.dbg("takeScreenshot: returning cached screenshot " + cached.length + " bytes (age " + (System.currentTimeMillis() - cachedScreenshotTime) + "ms)");
            return cached;
        }
        ReflectionHelper.dbg("takeScreenshot: no recent cached screenshot, forcing render + reading on render thread");
        final int w = width, h = height;
        final byte[][] resultHolder = new byte[1][];
        final CountDownLatch latch = new CountDownLatch(1);
        Runnable task = new Runnable() {
            public void run() {
                try {
                    ReflectionHelper.dbg("takeScreenshot0: on thread " + Thread.currentThread().getName());
                    forceRenderOneFrame(mc);
                    try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                    suppressGlDebug(true);
                    byte[] r;
                    try { r = takeGlScreenshot0(mc, w, h); } finally { suppressGlDebug(false); }
                    resultHolder[0] = r;
                    if (r != null) {
                        cachedScreenshot = r;
                        cachedScreenshotTime = System.currentTimeMillis();
                        ReflectionHelper.dbg("takeScreenshot0: captured " + r.length + " bytes via forceRender");
                    }
                } catch (Exception e) { ReflectionHelper.dbg("takeScreenshot0: " + e.getMessage()); }
                latch.countDown();
            }
        };
        boolean isSameThread = false;
        for (Method m : ReflectionCache.getAllMethods(mc.getClass())) {
            if (m.getName().equals("isSameThread") && m.getParameterCount() == 0) {
                try { m.setAccessible(true); isSameThread = (boolean) m.invoke(mc); } catch (Exception ignored) {}
                break;
            }
        }
        if (!isSameThread) {
            isSameThread = Thread.currentThread().getName().contains("Render") || Thread.currentThread().getName().contains("Client");
        }
        if (isSameThread) {
            task.run();
        } else {
            boolean scheduled = false;
            for (Method m : mc.getClass().getMethods()) {
                if ((m.getName().equals("execute") || m.getName().equals("addScheduledTask")) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == Runnable.class) {
                    try { m.setAccessible(true); m.invoke(mc, task); scheduled = true; } catch (Exception e) { ReflectionHelper.dbg("takeScreenshot0: schedule failed: " + e.getMessage()); }
                    break;
                }
            }
            if (!scheduled) task.run();
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
                ReflectionHelper.dbg("takeScreenshotOnMainThread: capturer running on thread: " + Thread.currentThread().getName());
                try {
                    Object rt = getMainRenderTarget(mc);
                    int texId = 0, fboId = 0;
                    if (rt != null) {
                        for (Field f : ReflectionCache.getAllFields(rt.getClass())) {
                            f.setAccessible(true);
                            Object val = f.get(rt);
                            if (val != null && val.getClass().getName().contains("GlTexture")) {
                                for (Field idf : ReflectionCache.getAllFields(val.getClass())) {
                                    if (idf.getName().equals("id") && idf.getType() == int.class) { idf.setAccessible(true); int tid = idf.getInt(val); if (tid > 0) texId = tid; }
                                }
                                for (Method gm : val.getClass().getMethods()) {
                                    if (gm.getName().equals("getFbo") && gm.getParameterCount() == 2) {
                                        try { Object dsa = resolveDSA(); Field df = null;
                                            for (Field df2 : ReflectionCache.getAllFields(rt.getClass())) { if (df2.getName().contains("depth") && df2.getName().contains("Texture")) { df2.setAccessible(true); df = df2; break; } }
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
                    ReflectionHelper.dbg("mainThread: texId=" + texId + " fboId=" + fboId);
                    byte[] texResult = null;
                    if (texId > 0) {
                        try { texResult = readTextureViaGetTexImage(texId, w, h); } catch (Exception e) { ReflectionHelper.dbg("mainThread: glGetTexImage failed: " + e.getMessage()); }
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
        ReflectionHelper.dbg("takeScreenshotOnMainThread: current thread=" + threadName);
        boolean isSameThread = false;
        for (Method m : ReflectionCache.getAllMethods(mc.getClass())) {
            if (m.getName().equals("isSameThread") && m.getParameterCount() == 0) {
                try { m.setAccessible(true); isSameThread = (boolean) m.invoke(mc); } catch (Exception ignored) {}
                break;
            }
        }
        ReflectionHelper.dbg("takeScreenshotOnMainThread: isSameThread=" + isSameThread);
        if (!isSameThread) {
            isSameThread = Thread.currentThread().getName().contains("Render") || Thread.currentThread().getName().contains("Client");
        }
        if (isSameThread) {
            ReflectionHelper.dbg("takeScreenshotOnMainThread: already on main thread, running directly");
            capturer.run();
        } else {
            for (Method m : mc.getClass().getMethods()) { if ((m.getName().equals("execute") || m.getName().equals("addScheduledTask")) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == Runnable.class) { execMethod = m; break; } }
            if (execMethod == null) {
                for (Method m : ReflectionCache.getAllMethods(mc.getClass())) {
                    if ((m.getName().equals("execute") || m.getName().equals("addScheduledTask")) && m.getParameterCount() == 1) {
                        Class<?> pt = m.getParameterTypes()[0];
                        if (pt == Runnable.class || pt.getName().equals("java.lang.Runnable")) { execMethod = m; break; }
                    }
                }
            }
            if (execMethod == null) throw new RuntimeException("no execute(Runnable) method found");
            ReflectionHelper.dbg("takeScreenshotOnMainThread: found execute method: " + execMethod);
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

            Object renderTarget = getMainRenderTarget(mc);
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
                ReflectionHelper.dbg("takeScreenshot: MC native CONSUMER ERROR - " + errorHolder[0].getMessage());
                return null;
            }
            return (byte[]) imageHolder[0];
        } catch (Exception e) {
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            System.err.println("[MCP-Native] failed: " + e.getMessage() + " root=" + cause.getClass().getName() + ":" + cause.getMessage());
            ReflectionHelper.dbg("takeScreenshot: MC native EXCEPTION - " + e.getMessage());
            return null;
        }
    }

    private static byte[] takeGlScreenshot(Object mc, int width, int height) throws Exception {
        return takeGlScreenshot0(mc, width, height);
    }

    private static byte[] takeGlScreenshot0(Object mc, int width, int height) throws Exception {
        try {
            return _takeGlScreenshot0Inner(mc, width, height);
        } catch (UnsatisfiedLinkError e) {
            ReflectionHelper.dbg("takeGlScreenshot0: GL libraries unavailable (UnsatisfiedLinkError): " + e.getMessage());
            return null;
        } catch (NoClassDefFoundError e) {
            ReflectionHelper.dbg("takeGlScreenshot0: GL class not found (headless): " + e.getMessage());
            return null;
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("glReadPixels") || msg.contains("GL") || msg.contains("render"))) {
                ReflectionHelper.dbg("takeGlScreenshot0: GL error (headless): " + msg);
                return null;
            }
            throw e;
        }
    }

    private static byte[] _takeGlScreenshot0Inner(Object mc, int width, int height) throws Exception {
        updateSkyColor(mc);
        if (width <= 0 || height <= 0) throw new RuntimeException("bad dims " + width + "x" + height);
        Object fb = getMainRenderTarget(mc);
        int colorTexId = 0;
        int fboId = 0;
        if (fb != null) {
            try {
                for (Field f : ReflectionCache.getAllFields(fb.getClass())) {
                    f.setAccessible(true);
                    if (f.getType() == int.class) {
                        try { int fv = f.getInt(fb); if (fv > 0 && fboId == 0) fboId = fv; } catch (Exception ignored) {}
                    }
                }
                if (fboId == 0) {
                    for (Field cf : ReflectionCache.getAllFields(fb.getClass())) {
                        if (java.lang.reflect.Modifier.isStatic(cf.getModifiers())) continue;
                        cf.setAccessible(true); Object colorTex = cf.get(fb);
                        if (colorTex == null) continue;
                        Class<?> texClass = colorTex.getClass();
                        if (texClass.getName().contains("GpuTexture") || texClass.getName().contains("GlTexture")) {
                            for (Field idf : ReflectionCache.getAllFields(texClass)) {
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
                                        for (Field df2 : ReflectionCache.getAllFields(fb.getClass())) {
                                            if (df2.getName().contains("depth") && df2.getName().contains("Texture")) { df2.setAccessible(true); depthField = df2; break; }
                                        }
                                        Object depthTex = depthField != null ? depthField.get(fb) : null;
                                        if (dsa != null) { int fbo = (Integer) gm.invoke(colorTex, dsa, depthTex); if (fbo > 0) fboId = fbo; }
                                    } catch (Exception ex) { ReflectionHelper.dbg("takeGl: getFbo failed: " + ex.getMessage()); }
                                    break;
                                }
                            }
                            break;
                        }
                    }
                }
                ReflectionHelper.dbgR("takeGl: fboId=" + fboId + " texId=" + colorTexId);
            } catch (Exception e) { ReflectionHelper.dbg("takeGl: failed to get fboId: " + e.getMessage()); }
        }
        if (fb != null) {
            try {
                int fbw = 0, fbh = 0;
                for (Field f : ReflectionCache.getAllFields(fb.getClass())) {
                    if (f.getType() == int.class) {
                        f.setAccessible(true);
                        try { int v = f.getInt(fb); if (v > 0) { if (fbw == 0) fbw = v; else if (fbh == 0) fbh = v; } } catch (Exception ignored) {}
                    }
                }
                if (fbw > 0 && fbh > 0) { width = fbw; height = fbh; }
            } catch (Exception ignored) {}
        }
        int w = width, h = height;
        if (fboId > 0) {
            try { byte[] r = readFboDirect(fboId, w, h, fb); if (r != null) return r; }
            catch (Exception ex) { ReflectionHelper.dbg("takeGl: FBO direct read failed: " + ex.getMessage()); }
        }
        if (colorTexId > 0) {
            try { byte[] r = readTextureViaGetTexImage(colorTexId, w, h); if (r != null) return r; }
            catch (Exception ex) { ReflectionHelper.dbg("takeGl: glGetTexImage failed: " + ex.getMessage()); }
        }
        ByteBuffer bb = ByteBuffer.allocateDirect(w * h * 4);
        if (fb != null) {
            try { for (Method bm : fb.getClass().getMethods()) { if (bm.getName().equals("blitToScreen") && bm.getParameterCount() == 0) { bm.setAccessible(true); bm.invoke(fb); break; } } }
            catch (Exception ignored) {}
        }
        doGlReadPixels(0, 0, w, h, bb, fboId);
        bb.rewind(); int[] raw = new int[w * h];
        int sc = cachedSkyColor;
        int skyR = (sc >> 16) & 0xFF, skyG = (sc >> 8) & 0xFF, skyB = sc & 0xFF;
        for (int i = 0; i < raw.length; i++) {
            int r = bb.get() & 0xFF, g = bb.get() & 0xFF, b = bb.get() & 0xFF, a = bb.get() & 0xFF;
            if (a < 16) { r = skyR; g = skyG; b = skyB; a = 255; }
            raw[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
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
        if (!ReflectionCache.LWJGL3) { try { Class.forName("org.lwjgl.opengl.Display").getMethod("makeCurrent").invoke(null); } catch (Exception ignored) {} }
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
        if (!ReflectionCache.LWJGL3) {
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
                            ReflectionHelper.dbgR("readTexImg: glGetTexImage sig mismatch: " + java.util.Arrays.toString(pts));
                        }
                    }
                } catch (Exception e) {
                    ReflectionHelper.dbgR("readTexImg: glGetTexImage failed: " + e.getMessage());
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
        if (!ReflectionCache.LWJGL3) {
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

    public static void cacheFrameFromRenderThread(Object mc) {
        if (System.currentTimeMillis() - cachedScreenshotTime < 1000) return;
        try {
            Object rt = getMainRenderTarget(mc);
            if (rt == null) return;
            int w = 0, h = 0;
            for (Field f : ReflectionCache.getAllFields(rt.getClass())) {
                f.setAccessible(true);
                if (f.getType() == int.class) {
                    try { int v = f.getInt(rt); if (v > 0) { if (w == 0) w = v; else if (h == 0) h = v; } } catch (Exception ignored) {}
                }
                if (f.getName().equals("height") && f.getType() == int.class) { try { h = f.getInt(rt); } catch (Exception ignored) {} }
            }
            if (w <= 0 || h <= 0) return;
            suppressGlDebug(true);
            byte[] result = null;
            try {
                result = takeGlScreenshot0(mc, w, h);
            } catch (Exception ignored) {} finally { suppressGlDebug(false); }
            if (result != null && result.length > 0) {
                cachedScreenshot = result;
                cachedScreenshotTime = System.currentTimeMillis();
                if (System.currentTimeMillis() - lastCacheFrameLog > 30000) { ReflectionHelper.dbg("cacheFrame: cached " + result.length + " bytes via takeGl"); lastCacheFrameLog = System.currentTimeMillis(); }
            }
        } catch (Exception e) { if (System.currentTimeMillis() - lastCacheFrameLog > 5000) { ReflectionHelper.dbg("cacheFrame: " + e.getMessage()); lastCacheFrameLog = System.currentTimeMillis(); } }
    }

    public static void setVideoCaptureActive(boolean v) { videoCaptureActive = v; }
    public static boolean isVideoCaptureActive() { return videoCaptureActive; }

    public static byte[] captureFrameJpeg(Object mc) {
        try {
            updateSkyColor(mc);
            int w = ReflectionHelper.getGlfwWindowSize(mc, true);
            int h = ReflectionHelper.getGlfwWindowSize(mc, false);
            if (w <= 0 || h <= 0) return null;
            ByteBuffer bb = ByteBuffer.allocateDirect(w * h * 4);
            doGlReadPixels(0, 0, w, h, bb, 0);
            bb.rewind();
            int sc = cachedSkyColor;
            int skyR = (sc >> 16) & 0xFF, skyG = (sc >> 8) & 0xFF, skyB = sc & 0xFF;
            int replaced = 0;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int r = bb.get() & 0xFF;
                    int g = bb.get() & 0xFF;
                    int b = bb.get() & 0xFF;
                    int a = bb.get() & 0xFF;
                    if (a < 16) {
                        r = skyR; g = skyG; b = skyB;
                        replaced++;
                    }
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
                ReflectionHelper.dbg("video: captured frame " + jpeg.length + " bytes (no WS client to send)");
            }
        } catch (Exception e) {
            ReflectionHelper.dbg("video: " + e.getMessage());
        }
    }

    private static void updateSkyColor(Object mc) {
        try {
            if (!skyColorPathChecked) {
                skyColorPathChecked = true;
                if (trySkyViaRenderState(mc)) skyColorPath = 1;
                else if (trySkyViaGetSkyColorInt(mc)) skyColorPath = 2;
                else if (trySkyViaGetSkyColorVec(mc)) skyColorPath = 3;
                else skyColorPath = -1;
            }
            if (skyColorPath == 1) trySkyViaRenderState(mc);
            else if (skyColorPath == 2) trySkyViaGetSkyColorInt(mc);
            else if (skyColorPath == 3) trySkyViaGetSkyColorVec(mc);
        } catch (Exception ignored) {}
    }

    private static boolean trySkyViaRenderState(Object mc) {
        try {
            Object lr = null;
            for (Field f : mc.getClass().getDeclaredFields()) {
                if (f.getType().getSimpleName().equals("LevelRenderer") || f.getType().getSimpleName().equals("WorldRenderer")) {
                    f.setAccessible(true); lr = f.get(mc); break;
                }
            }
            if (lr == null) return false;
            Object lrs = null;
            for (Field f : lr.getClass().getDeclaredFields()) {
                if (f.getType().getSimpleName().equals("LevelRenderState")) { f.setAccessible(true); lrs = f.get(lr); break; }
            }
            if (lrs == null) return false;
            Object srs = null;
            for (Field f : lrs.getClass().getDeclaredFields()) {
                if (f.getType().getSimpleName().equals("SkyRenderState")) { f.setAccessible(true); srs = f.get(lrs); break; }
            }
            if (srs == null) return false;
            for (Field f : srs.getClass().getDeclaredFields()) {
                if (f.getName().equals("skyColor") && f.getType() == int.class) {
                    f.setAccessible(true);
                    int c = f.getInt(srs);
                    if (c != 0) cachedSkyColor = c;
                    return true;
                }
            }
            return false;
        } catch (Exception e) { return false; }
    }

    private static boolean trySkyViaGetSkyColorInt(Object mc) {
        try {
            Object level = getLevelFromMc(mc);
            if (level == null) return false;
            for (Method m : ReflectionCache.getAllMethods(level.getClass())) {
                if (m.getName().equals("getSkyColor") && m.getParameterCount() == 2 && m.getReturnType() == int.class) {
                    m.setAccessible(true);
                    Object pos = ReflectionCache.getPlayer(mc);
                    Object partialTick = getPartialTick(mc);
                    if (pos == null) return true;
                    int c = (int) m.invoke(level, pos, partialTick instanceof Float ? (Float) partialTick : 0f);
                    if (c != 0) cachedSkyColor = c;
                    return true;
                }
            }
            return false;
        } catch (Exception e) { return false; }
    }

    private static boolean trySkyViaGetSkyColorVec(Object mc) {
        try {
            Object level = getLevelFromMc(mc);
            if (level == null) return false;
            for (Method m : ReflectionCache.getAllMethods(level.getClass())) {
                String n = m.getName();
                if ((n.equals("getSkyColor") || n.equals("func_228318_a_") || n.equals("m_171660_"))
                        && m.getParameterCount() == 2 && m.getReturnType().getSimpleName().matches("Vec3|Vector3d|Vector3f")) {
                    m.setAccessible(true);
                    Object player = ReflectionCache.getPlayer(mc);
                    if (player == null) return true;
                    float tick = 0f;
                    try {
                        for (Method pt : mc.getClass().getMethods()) {
                            if (pt.getName().equals("getDeltaTracker") || pt.getName().equals("getTickDelta")) {
                                Object dt = pt.invoke(mc);
                                if (dt != null) { for (Method gf : dt.getClass().getMethods()) { if (gf.getName().equals("getGameTimeDeltaPartialTick") || gf.getName().equals("getTickDelta")) { Object r = gf.invoke(dt); if (r instanceof Float) tick = (Float) r; break; } } break; }
                            }
                        }
                    } catch (Exception ignored) {}
                    Class<?>[] pts = m.getParameterTypes();
                    Object arg0 = player;
                    if (pts[0].getSimpleName().equals("Vec3") || pts[0].getSimpleName().equals("Vector3d")) {
                        double px = ReflectionCache.getDouble(player, "getX", "posX"), py = ReflectionCache.getDouble(player, "getEyeY", "posY") + 1, pz = ReflectionCache.getDouble(player, "getZ", "posZ");
                        arg0 = pts[0].getConstructor(double.class, double.class, double.class).newInstance(px, py, pz);
                    } else if (pts[0].getSimpleName().equals("BlockPos")) {
                        int px = (int) ReflectionCache.getDouble(player, "getX", "posX"), py = (int)(ReflectionCache.getDouble(player, "getEyeY", "posY") + 1), pz = (int) ReflectionCache.getDouble(player, "getZ", "posZ");
                        arg0 = pts[0].getConstructor(int.class, int.class, int.class).newInstance(px, py, pz);
                    }
                    Object result = m.invoke(level, arg0, tick);
                    if (result != null) {
                        double x = ReflectionCache.getDouble(result, "x", "field_72450_a"), y = ReflectionCache.getDouble(result, "y", "field_72448_b"), z = ReflectionCache.getDouble(result, "z", "field_72449_c");
                        int r = (int)(x * 255), g = (int)(y * 255), b = (int)(z * 255);
                        cachedSkyColor = (r << 16) | (g << 8) | b;
                    }
                    return true;
                }
            }
            return false;
        } catch (Exception e) { return false; }
    }

    private static Object getLevelFromMc(Object mc) {
        try { return ReflectionCache.getLevel(mc); } catch (Exception ignored) {}
        return null;
    }

    private static Object getPartialTick(Object mc) {
        try {
            for (Method m : mc.getClass().getMethods()) {
                if (m.getName().equals("getDeltaTracker") && m.getParameterCount() == 0) {
                    Object dt = m.invoke(mc);
                    if (dt != null) { for (Method gf : dt.getClass().getMethods()) { if (gf.getName().equals("getGameTimeDeltaPartialTick") && m.getParameterCount() == 0) return gf.invoke(dt); } }
                }
            }
            for (Method m : mc.getClass().getMethods()) {
                if ((m.getName().equals("getTickDelta") || m.getName().equals("getPartialTick")) && m.getParameterCount() == 0) return m.invoke(mc);
            }
        } catch (Exception ignored) {}
        return 0f;
    }

    private static Object getMainRenderTarget(Object mc) {
        try { return mc.getClass().getMethod("getMainRenderTarget").invoke(mc); } catch (Exception ignored) {}
        Method discovered = ReflectionCache.getDiscoveredMethod("getMainRenderTarget");
        if (discovered != null) { try { discovered.setAccessible(true); return discovered.invoke(mc); } catch (Exception ignored) {} }
        for (Method m : ReflectionCache.getAllMethods(mc.getClass())) {
            if (m.getParameterCount() == 0 && m.getReturnType() != void.class && !m.getReturnType().isPrimitive()) {
                String rtName = m.getReturnType().getName();
                if (rtName.contains("RenderTarget") || rtName.contains("Framebuffer") || rtName.contains("FrameBuffer")) {
                    try { m.setAccessible(true); return m.invoke(mc); } catch (Exception ignored) {}
                }
            }
        }
        return null;
    }
}

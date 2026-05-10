package xyz.langyo.minecraftmcp;

import com.mcbbs.mcp.common.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class NeoForgeInputHandler extends McpMessageHandler implements MinecraftInput {
    private static Minecraft mc() { return Minecraft.getInstance(); }

    public NeoForgeInputHandler() {}

    private static void sendKey(long h, int key, int action) {
        GLFW.glfwSetKeyCallback(h, (w, k, sc, a, m) -> {}).invoke(h, key, 0, action, 0);
    }

    private static void sendMouseButton(long h, int button, int action) {
        GLFW.glfwSetMouseButtonCallback(h, (w, b, a, m) -> {}).invoke(h, button, action, 0);
    }

    @Override
    public void click(int x, int y, String button) {
        mc().execute(() -> {
            try {
                long h = mc().getWindow().getHandle();
                int b = "right".equals(button) ? GLFW.GLFW_MOUSE_BUTTON_RIGHT
                    : "middle".equals(button) ? GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                    : GLFW.GLFW_MOUSE_BUTTON_1;
                GLFW.glfwSetCursorPos(h, x, y);
                Thread.sleep(10);
                sendMouseButton(h, b, GLFW.GLFW_PRESS);
                Thread.sleep(30);
                sendMouseButton(h, b, GLFW.GLFW_RELEASE);
            } catch (Exception e) { System.err.println("[Input] Click: " + e.getMessage()); }
        });
    }

    @Override
    public void pressKey(String key, float hold) {
        mc().execute(() -> {
            try {
                long h = mc().getWindow().getHandle();
                int c = keyCode(key); if (c < 0) return;
                sendKey(h, c, GLFW.GLFW_PRESS);
                Thread.sleep(hold > 0 ? (long)(hold * 1000) : 30);
                sendKey(h, c, GLFW.GLFW_RELEASE);
            } catch (Exception e) { System.err.println("[Input] Key: " + e.getMessage()); }
        });
    }

    @Override
    public void typeText(String text) {
        mc().execute(() -> {
            try {
                long h = mc().getWindow().getHandle();
                for (char ch : text.toCharArray()) {
                    int code = charCode(ch);
                    if (code > 0) {
                        boolean shift = Character.isUpperCase(ch) || "\"!@#$%^&*()_+{}|:<>?\".indexOf(ch) >= 0;
                        if (shift) { sendKey(h, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_PRESS); Thread.sleep(5); }
                        sendKey(h, code, GLFW.GLFW_PRESS); Thread.sleep(25);
                        sendKey(h, code, GLFW.GLFW_RELEASE);
                        if (shift) { Thread.sleep(5); sendKey(h, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_RELEASE); }
                    } else { typeUnicode(h, ch); }
                    Thread.sleep(20);
                }
            } catch (Exception e) { System.err.println("[Input] Type: " + e.getMessage()); }
        });
    }

    @Override
    public void scroll(int clicks) {
        mc().execute(() -> {
            try {
                long h = mc().getWindow().getHandle();
                GLFW.glfwSetScrollCallback(h, (_h, ox, oy) -> {}).invoke(h, 0.0, clicks * 1.0);
            } catch (Exception ignored) {}
        });
    }

    @Override
    public void hotkey(String[] keys) {
        mc().execute(() -> {
            try {
                long h = mc().getWindow().getHandle();
                int[] codes = new int[keys.length];
                for (int i = 0; i < keys.length; i++) codes[i] = keyCode(keys[i]);
                for (int c : codes) { sendKey(h, c, GLFW.GLFW_PRESS); Thread.sleep(5); }
                Thread.sleep(80);
                for (int i = codes.length - 1; i >= 0; i--) sendKey(h, codes[i], GLFW.GLFW_RELEASE);
            } catch (Exception e) { System.err.println("[Input] Hotkey: " + e.getMessage()); }
        });
    }

    @Override
    public byte[] screenshot() {
        try {
            var fb = mc().getMainRenderTarget();
            int w = fb.width, h = fb.height;
            var pixels = new java.util.concurrent.CompletableFuture<int[]>();
            mc().execute(() -> {
                try {
                    int[] buf = new int[w * h];
                    ByteBuffer bb = java.nio.ByteBuffer.allocateDirect(w * h * 4);
                    org.lwjgl.opengl.GL11.glReadPixels(0, 0, w, h, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, bb);
                    bb.rewind();
                    for (int i = 0; i < buf.length; i++) {
                        int r2 = bb.get() & 0xFF;
                        int g2 = bb.get() & 0xFF;
                        int b = bb.get() & 0xFF;
                        int a = bb.get() & 0xFF;
                        buf[i] = (a << 24) | (r2 << 16) | (g2 << 8) | b;
                    }
                    pixels.complete(buf);
                } catch (Exception e) { pixels.completeExceptionally(e); }
            });
            int[] raw = pixels.get(5, java.util.concurrent.TimeUnit.SECONDS);
            int[] flipped = new int[w * h];
            for (int y2 = 0; y2 < h; y2++) {
                for (int x2 = 0; x2 < w; x2++) {
                    flipped[y2 * w + x2] = raw[(h - 1 - y2) * w + x2];
                }
            }
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, w, h, flipped, 0, w);
            var baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) { e.printStackTrace(); return null; }
    }

    @Override
    public String executeCommand(String cmd) {
        mc().execute(() -> {
            if (mc().player != null) mc().player.connection.sendCommand(cmd);
        });
        return "sent: " + cmd;
    }

    @Override
    public String getPlayerInfo() {
        var p = mc().player;
        if (p == null) return "{\"name\":null}";
        return String.format("{\"name\":\"%s\",\"health\":%.1f,\"pos\":\"%.1f %.1f %.1f\",\"dimension\":\"%s\"}",
            p.getName().getString(), p.getHealth(), p.getX(), p.getY(), p.getZ(),
            p.level().dimension().location().toString());
    }

    @Override
    public String getWorldInfo() {
        var l = mc().level;
        if (l == null) return "{\"world_name\":null}";
        return String.format("{\"world_name\":\"%s\",\"difficulty\":\"%s\",\"gametype\":\"%s\"}",
            l.getServer() != null ? l.getServer().getWorldData().getLevelName() : "unknown",
            l.getDifficulty().getKey(),
            mc().gameMode.getPlayerMode().getName());
    }

    private void typeUnicode(long handle, char ch) {
        try {
            int cp = (int) ch;
            sendKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_PRESS);
            sendKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_PRESS);
            sendKey(handle, GLFW.GLFW_KEY_U, GLFW.GLFW_PRESS);
            sendKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_RELEASE);
            sendKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_RELEASE);
            String hex = Integer.toHexString(cp).toLowerCase();
            for (char hc : hex.toCharArray()) {
                int kc = charCode(hc);
                if (kc > 0) { sendKey(handle, kc, GLFW.GLFW_PRESS); Thread.sleep(20); sendKey(handle, kc, GLFW.GLFW_RELEASE); Thread.sleep(15); }
            }
            sendKey(handle, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_PRESS); Thread.sleep(30);
            sendKey(handle, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_RELEASE);
        } catch (Exception e) { System.err.println("[Input] Unicode: " + e.getMessage()); }
    }


    private static int keyCode(String name) {
        String n = name.toLowerCase();
        switch (n) {
            case "enter": case "return": return GLFW.GLFW_KEY_ENTER;
            case "escape": case "esc": return GLFW.GLFW_KEY_ESCAPE;
            case "tab": return GLFW.GLFW_KEY_TAB;
            case "space": return GLFW.GLFW_KEY_SPACE;
            case "backspace": return GLFW.GLFW_KEY_BACKSPACE;
            case "delete": return GLFW.GLFW_KEY_DELETE;
            case "up": return GLFW.GLFW_KEY_UP; case "down": return GLFW.GLFW_KEY_DOWN;
            case "left": return GLFW.GLFW_KEY_LEFT; case "right": return GLFW.GLFW_KEY_RIGHT;
            case "f1": return GLFW.GLFW_KEY_F1; case "f2": return GLFW.GLFW_KEY_F2; case "f3": return GLFW.GLFW_KEY_F3; case "f4": return GLFW.GLFW_KEY_F4;
            case "f5": return GLFW.GLFW_KEY_F5; case "f6": return GLFW.GLFW_KEY_F6; case "f7": return GLFW.GLFW_KEY_F7; case "f8": return GLFW.GLFW_KEY_F8;
            case "f9": return GLFW.GLFW_KEY_F9; case "f10": return GLFW.GLFW_KEY_F10; case "f11": return GLFW.GLFW_KEY_F11; case "f12": return GLFW.GLFW_KEY_F12;
            case "a": return GLFW.GLFW_KEY_A; case "b": return GLFW.GLFW_KEY_B; case "c": return GLFW.GLFW_KEY_C; case "d": return GLFW.GLFW_KEY_D;
            case "e": return GLFW.GLFW_KEY_E; case "f": return GLFW.GLFW_KEY_F; case "g": return GLFW.GLFW_KEY_G; case "h": return GLFW.GLFW_KEY_H;
            case "i": return GLFW.GLFW_KEY_I; case "j": return GLFW.GLFW_KEY_J; case "k": return GLFW.GLFW_KEY_K; case "l": return GLFW.GLFW_KEY_L;
            case "m": return GLFW.GLFW_KEY_M; case "n": return GLFW.GLFW_KEY_N; case "o": return GLFW.GLFW_KEY_O; case "p": return GLFW.GLFW_KEY_P;
            case "q": return GLFW.GLFW_KEY_Q; case "r": return GLFW.GLFW_KEY_R; case "s": return GLFW.GLFW_KEY_S; case "t": return GLFW.GLFW_KEY_T;
            case "u": return GLFW.GLFW_KEY_U; case "v": return GLFW.GLFW_KEY_V; case "w": return GLFW.GLFW_KEY_W; case "x": return GLFW.GLFW_KEY_X;
            case "y": return GLFW.GLFW_KEY_Y; case "z": return GLFW.GLFW_KEY_Z;
            case "0": return GLFW.GLFW_KEY_0; case "1": return GLFW.GLFW_KEY_1; case "2": return GLFW.GLFW_KEY_2; case "3": return GLFW.GLFW_KEY_3;
            case "4": return GLFW.GLFW_KEY_4; case "5": return GLFW.GLFW_KEY_5; case "6": return GLFW.GLFW_KEY_6; case "7": return GLFW.GLFW_KEY_7;
            case "8": return GLFW.GLFW_KEY_8; case "9": return GLFW.GLFW_KEY_9;
            case "shift": return GLFW.GLFW_KEY_LEFT_SHIFT;
            case "ctrl": case "control": return GLFW.GLFW_KEY_LEFT_CONTROL;
            case "alt": return GLFW.GLFW_KEY_LEFT_ALT;
            default: return -1;
        }
    }

    private static int charCode(char ch) {
        if (ch >= 'a' && ch <= 'z') return GLFW.GLFW_KEY_A + (ch - 'a');
        if (ch >= 'A' && ch <= 'Z') return GLFW.GLFW_KEY_A + (ch - 'A');
        if (ch >= '0' && ch <= '9') return GLFW.GLFW_KEY_0 + (ch - '0');
        switch (ch) {
            case ' ': return GLFW.GLFW_KEY_SPACE;
            case '.': return GLFW.GLFW_KEY_PERIOD;
            case ',': return GLFW.GLFW_KEY_COMMA;
            case '!': return GLFW.GLFW_KEY_1;
            case '@': return GLFW.GLFW_KEY_2;
            case '#': return GLFW.GLFW_KEY_3;
            case '$': return GLFW.GLFW_KEY_4;
            case '%': return GLFW.GLFW_KEY_5;
            case '^': return GLFW.GLFW_KEY_6;
            case '&': return GLFW.GLFW_KEY_7;
            case '*': return GLFW.GLFW_KEY_8;
            case '(': return GLFW.GLFW_KEY_9;
            case ')': return GLFW.GLFW_KEY_0;
            case '-': return GLFW.GLFW_KEY_MINUS;
            case '=': return GLFW.GLFW_KEY_EQUAL;
            case '[': return GLFW.GLFW_KEY_LEFT_BRACKET;
            case ']': return GLFW.GLFW_KEY_RIGHT_BRACKET;
            case ';': return GLFW.GLFW_KEY_SEMICOLON;
            case '\'': return GLFW.GLFW_KEY_BACKSLASH;
            case '`': return GLFW.GLFW_KEY_GRAVE_ACCENT;
            default: return -1;
        }
    }

}

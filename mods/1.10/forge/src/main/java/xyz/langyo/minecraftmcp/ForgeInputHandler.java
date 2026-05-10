package xyz.langyo.minecraftmcp;

import com.mcbbs.mcp.common.*;
import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

public class ForgeInputHandler extends McpMessageHandler implements MinecraftInput {
    private static Minecraft mc() { return Minecraft.getMinecraft(); }

    public ForgeInputHandler() {}

    @Override
    public void click(int x, int y, String button) {
        mc().execute(() -> {
            try {
                int b = "right".equals(button) ? 1 : "middle".equals(button) ? 2 : 0;
                org.lwjgl.input.Mouse.setCursorPosition(x, y);
                Thread.sleep(10);
                org.lwjgl.input.Mouse.setGrabbed(false);
                if (b == 0) org.lwjgl.input.Mouse.press();
                org.lwjgl.input.Mouse.next();
                Thread.sleep(30);
            } catch (Exception e) { System.err.println("[Input] Click: " + e.getMessage()); }
        });
    }

    @Override
    public void pressKey(String key, float hold) {
        mc().execute(() -> {
            try {
                int c = keyCode(key); if (c < 0) return;
                org.lwjgl.input.Keyboard.pressKey(c, false);
                Thread.sleep(hold > 0 ? (long)(hold * 1000) : 30);
                org.lwjgl.input.Keyboard.releaseKey(c);
            } catch (Exception e) { System.err.println("[Input] Key: " + e.getMessage()); }
        });
    }

    @Override
    public void typeText(String text) {
        mc().execute(() -> {
            try {
                for (char ch : text.toCharArray()) {
                    if (ch >= 32 && ch < 127) {
                        org.lwjgl.input.Keyboard.typeKey((int) ch);
                    }
                    Thread.sleep(20);
                }
            } catch (Exception e) { System.err.println("[Input] Type: " + e.getMessage()); }
        });
    }

    @Override
    public void scroll(int clicks) {
        mc().execute(() -> {
            try {
                org.lwjgl.input.Mouse.setEventDX(0);
                org.lwjgl.input.Mouse.setEventDWheel(clicks * 120);
            } catch (Exception ignored) {}
        });
    }

    @Override
    public void hotkey(String[] keys) {
        mc().execute(() -> {
            try {
                int[] codes = new int[keys.length];
                for (int i = 0; i < keys.length; i++) codes[i] = keyCode(keys[i]);
                for (int c : codes) { org.lwjgl.input.Keyboard.pressKey(c, false); Thread.sleep(5); }
                Thread.sleep(80);
                for (int i = codes.length - 1; i >= 0; i--) org.lwjgl.input.Keyboard.releaseKey(codes[i]);
            } catch (Exception e) { System.err.println("[Input] Hotkey: " + e.getMessage()); }
        });
    }

    @Override
    public byte[] screenshot() {
        try {
            int w = mc().displayWidth, h = mc().displayHeight;
            int[] raw = new int[w * h];
            ByteBuffer bb = java.nio.ByteBuffer.allocateDirect(w * h * 4);
            org.lwjgl.opengl.GL11.glReadPixels(0, 0, w, h, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, bb);
            bb.rewind();
            for (int i = 0; i < raw.length; i++) {
                int r2 = bb.get() & 0xFF;
                int g2 = bb.get() & 0xFF;
                int b = bb.get() & 0xFF;
                int a = bb.get() & 0xFF;
                raw[i] = (a << 24) | (r2 << 16) | (g2 << 8) | b;
            }
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
            if (mc().player != null) mc().player.sendChatMessage("/" + cmd);
        });
        return "sent: " + cmd;
    }

    @Override
    public String getPlayerInfo() {
        var p = mc().player;
        if (p == null) return "{\"name\":null}";
        return String.format("{\"name\":\"%s\",\"health\":%.1f,\"pos\":\"%.1f %.1f %.1f\"}",
            p.getName(), p.getHealth(), p.posX, p.posY, p.posZ);
    }

    @Override
    public String getWorldInfo() {
        var l = mc().world;
        if (l == null) return "{\"world_name\":null}";
        return String.format("{\"world_name\":\"%s\",\"difficulty\":\"%s\"}",
            l.getWorldInfo().getWorldName(), l.getDifficulty().getDifficultyKey());
    }

    private static int keyCode(String name) {
        String n = name.toLowerCase();
        switch (n) {
            case "enter": case "return": return org.lwjgl.input.Keyboard.KEY_RETURN;
            case "escape": case "esc": return org.lwjgl.input.Keyboard.KEY_ESCAPE;
            case "tab": return org.lwjgl.input.Keyboard.KEY_TAB;
            case "space": return org.lwjgl.input.Keyboard.KEY_SPACE;
            case "backspace": return org.lwjgl.input.Keyboard.KEY_BACK;
            case "delete": return org.lwjgl.input.Keyboard.KEY_DELETE;
            case "up": return org.lwjgl.input.Keyboard.KEY_UP; case "down": return org.lwjgl.input.Keyboard.KEY_DOWN;
            case "left": return org.lwjgl.input.Keyboard.KEY_LEFT; case "right": return org.lwjgl.input.Keyboard.KEY_RIGHT;
            default: return -1;
        }
    }
}

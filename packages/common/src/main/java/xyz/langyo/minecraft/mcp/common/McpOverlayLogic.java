package xyz.langyo.minecraft.mcp.common;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;

public class McpOverlayLogic {

    private static final int MARGIN = 10;
    private static final int BTN_SIZE = 32;
    private static final int ICON_SIZE = 32;

    private static int[] resumePixels;
    private static int[] transferPixels;

    private static int[] loadIcon(String resourcePath) {
        try {
            InputStream is = McpOverlayLogic.class.getClassLoader().getResourceAsStream(resourcePath);
            if (is == null) {
                System.err.println("[MCP] Icon resource not found: " + resourcePath);
                return new int[ICON_SIZE * ICON_SIZE];
            }
            BufferedImage img = ImageIO.read(is);
            is.close();
            int w = img.getWidth();
            int h = img.getHeight();
            int[] raw = new int[w * h];
            img.getRGB(0, 0, w, h, raw, 0, w);
            if (w == ICON_SIZE && h == ICON_SIZE) return raw;
            int[] out = new int[ICON_SIZE * ICON_SIZE];
            for (int y = 0; y < ICON_SIZE && y < h; y++) {
                for (int x = 0; x < ICON_SIZE && x < w; x++) {
                    out[y * ICON_SIZE + x] = raw[y * w + x];
                }
            }
            return out;
        } catch (Exception e) {
            System.err.println("[MCP] Failed to load icon: " + resourcePath + " - " + e.getMessage());
            return new int[ICON_SIZE * ICON_SIZE];
        }
    }

    private static int[] getResumePixels() {
        if (resumePixels == null) resumePixels = loadIcon("assets/mcpmod/icons/resume.png");
        return resumePixels;
    }

    private static int[] getTransferPixels() {
        if (transferPixels == null) transferPixels = loadIcon("assets/mcpmod/icons/transfer.png");
        return transferPixels;
    }

    public static class ButtonBounds {
        public int x, y, w, h;
        public ButtonBounds(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
        public boolean hit(int mx, int my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private static ButtonBounds calcIconBounds(int screenW) {
        return new ButtonBounds(screenW - BTN_SIZE - MARGIN, MARGIN, BTN_SIZE, BTN_SIZE);
    }

    private static void drawPixelIcon(McpRenderer r, int bx, int by, int[] pixels) {
        for (int py = 0; py < ICON_SIZE; py++) {
            for (int px = 0; px < ICON_SIZE; px++) {
                int color = pixels[py * ICON_SIZE + px];
                if ((color & 0xFF000000) != 0) {
                    r.fill(bx + px, by + py, bx + px + 1, by + py + 1, color);
                }
            }
        }
    }

    public static void renderResumeButton(McpRenderer r, Object font, String label, int screenW, int screenH, int mouseX, int mouseY) {
        ButtonBounds b = calcIconBounds(screenW);
        drawPixelIcon(r, b.x, b.y, getResumePixels());
        ReflectionHelper.setOverlayButtonBounds(b.x, b.y, b.w, b.h, 0, 0, 0, 0);
    }

    public static void renderTransferButton(McpRenderer r, Object font, String label, int screenW, int screenH, int mouseX, int mouseY) {
        ButtonBounds b = calcIconBounds(screenW);
        drawPixelIcon(r, b.x, b.y, getTransferPixels());
        ReflectionHelper.setTransferButtonBounds(b.x, b.y, b.w, b.h);
    }
}

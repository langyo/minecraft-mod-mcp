package xyz.langyo.minecraft.mcp.common;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;

public class McpOverlayLogic {

    private static final int MARGIN = 8;
    private static final int BTN_SIZE = 32;
    private static final int PAD = 4;
    private static final int DISPLAY_SIZE = BTN_SIZE - PAD * 2;

    private static final int BG_NORMAL = 0xA0A0A0;
    private static final int BG_HOVER  = 0xBCBCBC;
    private static final int BORDER_LIGHT = 0xFFFFF0;
    private static final int BORDER_DARK  = 0x505050;
    private static final int BG_ALPHA = 0xCC000000;

    private static int[] resumePixels;
    private static int[] transferPixels;

    private static int[] loadIcon(String resourcePath) {
        try {
            InputStream is = McpOverlayLogic.class.getClassLoader().getResourceAsStream(resourcePath);
            if (is == null) {
                System.err.println("[MCP] Icon resource not found: " + resourcePath);
                return new int[DISPLAY_SIZE * DISPLAY_SIZE];
            }
            BufferedImage img = ImageIO.read(is);
            is.close();

            int w = img.getWidth(), h = img.getHeight();
            int[] raw = new int[w * h];
            img.getRGB(0, 0, w, h, raw, 0, w);

            int minX = w, minY = h, maxX = 0, maxY = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if ((raw[y * w + x] & 0xFF000000) != 0) {
                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                    }
                }
            }

            if (maxX < minX || maxY < minY) {
                return new int[DISPLAY_SIZE * DISPLAY_SIZE];
            }

            int cw = maxX - minX + 1;
            int ch = maxY - minY + 1;
            int side = Math.max(cw, ch);
            BufferedImage square = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
            int ox = (side - cw) / 2;
            int oy = (side - ch) / 2;
            for (int y = 0; y < ch; y++) {
                for (int x = 0; x < cw; x++) {
                    square.setRGB(ox + x, oy + y, raw[(minY + y) * w + (minX + x)]);
                }
            }

            Image scaled = square.getScaledInstance(DISPLAY_SIZE, DISPLAY_SIZE, Image.SCALE_AREA_AVERAGING);
            BufferedImage out = new BufferedImage(DISPLAY_SIZE, DISPLAY_SIZE, BufferedImage.TYPE_INT_ARGB);
            out.getGraphics().drawImage(scaled, 0, 0, null);
            out.getGraphics().dispose();
            int[] pixels = new int[DISPLAY_SIZE * DISPLAY_SIZE];
            out.getRGB(0, 0, DISPLAY_SIZE, DISPLAY_SIZE, pixels, 0, DISPLAY_SIZE);
            return pixels;
        } catch (Exception e) {
            System.err.println("[MCP] Failed to load icon: " + resourcePath + " - " + e.getMessage());
            return new int[DISPLAY_SIZE * DISPLAY_SIZE];
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

    private static int rgba(int rgb) {
        return (0xFF000000) | (rgb & 0xFFFFFF);
    }

    private static void drawMcButton(McpRenderer r, ButtonBounds b, boolean hovered) {
        int x = b.x, y = b.y, w = b.w, h = b.h;
        int bg = hovered ? BG_HOVER : BG_NORMAL;

        r.fill(x + 1, y + 1, x + w - 1, y + h - 1, rgba(bg));

        r.fill(x + 1, y, x + w - 1, y + 1, rgba(BORDER_LIGHT));
        r.fill(x, y + 1, x + 1, y + h - 1, rgba(BORDER_LIGHT));

        r.fill(x + 1, y + h - 1, x + w - 1, y + h, rgba(BORDER_DARK));
        r.fill(x + w - 1, y + 1, x + w, y + h - 1, rgba(BORDER_DARK));

        r.fill(x, y, x + 1, y + 1, rgba(BORDER_LIGHT));
        r.fill(x, y + h - 1, x + 1, y + h, rgba(BORDER_DARK));
        r.fill(x + w - 1, y, x + w, y + 1, rgba(BORDER_DARK));
        r.fill(x + w - 1, y + h - 1, x + w, y + h, rgba(BORDER_DARK));
    }

    private static void drawIcon(McpRenderer r, int bx, int by, int[] pixels) {
        for (int py = 0; py < DISPLAY_SIZE; py++) {
            for (int px = 0; px < DISPLAY_SIZE; px++) {
                int color = pixels[py * DISPLAY_SIZE + px];
                if ((color & 0xFF000000) != 0) {
                    r.fill(bx + px, by + py, bx + px + 1, by + py + 1, color);
                }
            }
        }
    }

    public static void renderResumeButton(McpRenderer r, Object font, String label, int screenW, int screenH, int mouseX, int mouseY) {
        ButtonBounds b = calcIconBounds(screenW);
        boolean hovered = b.hit(mouseX, mouseY);
        drawMcButton(r, b, hovered);
        drawIcon(r, b.x + PAD, b.y + PAD, getResumePixels());
        ReflectionHelper.setOverlayButtonBounds(b.x, b.y, b.w, b.h, 0, 0, 0, 0);
    }

    public static void renderTransferButton(McpRenderer r, Object font, String label, int screenW, int screenH, int mouseX, int mouseY) {
        ButtonBounds b = calcIconBounds(screenW);
        boolean hovered = b.hit(mouseX, mouseY);
        drawMcButton(r, b, hovered);
        drawIcon(r, b.x + PAD, b.y + PAD, getTransferPixels());
        ReflectionHelper.setTransferButtonBounds(b.x, b.y, b.w, b.h);
    }

    public static void renderPortInfo(McpRenderer r, Object font, int screenW, int screenH, McpHttpServer httpServer) {
        if (httpServer == null) return;
        int port = httpServer.getPort();
        if (port <= 0) return;
        String text = "MCP \u00B7 :" + port;
        int tw = r.getStringWidth(font, text);
        r.drawString(font, text, screenW - tw - MARGIN, screenH - 12, 0xFFCCCCCC, true);
    }
}

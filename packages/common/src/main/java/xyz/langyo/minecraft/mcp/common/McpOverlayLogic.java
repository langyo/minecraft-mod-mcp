package xyz.langyo.minecraft.mcp.common;

public class McpOverlayLogic {

    private static final int PAD = 5;
    private static final int BTN_H = 13;
    private static final int MARGIN = 10;

    public static class ButtonBounds {
        public int x, y, w, h;
        public ButtonBounds(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
        public boolean hit(int mx, int my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    public static ButtonBounds calcResumeButtonBounds(int fontWidth, int screenW) {
        int btnW = fontWidth + PAD * 2;
        return new ButtonBounds(screenW - btnW - MARGIN, MARGIN, btnW, BTN_H);
    }

    public static ButtonBounds calcTransferButtonBounds(int fontWidth, int screenW) {
        int btnW = fontWidth + PAD * 2;
        return new ButtonBounds(screenW - btnW - MARGIN, MARGIN, btnW, BTN_H);
    }

    public static void renderResumeButton(McpRenderer r, Object font, String label, int screenW, int screenH, int mouseX, int mouseY) {
        int fontW = r.getStringWidth(font, label);
        ButtonBounds b = calcResumeButtonBounds(fontW, screenW);

        boolean hover = b.hit(mouseX, mouseY);
        int bg = hover ? 0xDD666666 : 0xBB444444;
        r.fill(b.x, b.y, b.x + b.w, b.y + b.h, bg);
        r.drawString(font, label, b.x + PAD, b.y + (b.h - 8) / 2, 0xFFFFFFFF, false);

        ReflectionHelper.setOverlayButtonBounds(b.x, b.y, b.w, b.h, 0, 0, 0, 0);
    }

    public static void renderTransferButton(McpRenderer r, Object font, String label, int screenW, int screenH, int mouseX, int mouseY) {
        int fontW = r.getStringWidth(font, label);
        ButtonBounds b = calcTransferButtonBounds(fontW, screenW);

        boolean hover = b.hit(mouseX, mouseY);
        int bg = hover ? 0xDD446644 : 0xBB335533;
        r.fill(b.x, b.y, b.x + b.w, b.y + b.h, bg);
        r.drawString(font, label, b.x + PAD, b.y + (b.h - 8) / 2, 0xFFFFFFFF, false);

        ReflectionHelper.setTransferButtonBounds(b.x, b.y, b.w, b.h);
    }
}

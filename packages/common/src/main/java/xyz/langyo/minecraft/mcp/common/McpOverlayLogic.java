package xyz.langyo.minecraft.mcp.common;

public class McpOverlayLogic {

    private static final int MARGIN = 10;
    private static final int BTN_SIZE = 16;
    private static final int ICON_PAD = 4;

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

    public static void renderResumeButton(McpRenderer r, Object font, String label, int screenW, int screenH, int mouseX, int mouseY) {
        ButtonBounds b = calcIconBounds(screenW);
        boolean hover = b.hit(mouseX, mouseY);
        int bg = hover ? 0xEE666666 : 0xCC444444;
        int fg = hover ? 0xFFCCCCCC : 0xFFAAAAAA;
        r.fill(b.x, b.y, b.x + b.w, b.y + b.h, bg);
        drawResumeIcon(r, b.x, b.y, b.w, b.h, fg);
        ReflectionHelper.setOverlayButtonBounds(b.x, b.y, b.w, b.h, 0, 0, 0, 0);
    }

    public static void renderTransferButton(McpRenderer r, Object font, String label, int screenW, int screenH, int mouseX, int mouseY) {
        ButtonBounds b = calcIconBounds(screenW);
        boolean hover = b.hit(mouseX, mouseY);
        int bg = hover ? 0xEE446644 : 0xCC335533;
        int fg = hover ? 0xFFCCCCCC : 0xFFAAAAAA;
        r.fill(b.x, b.y, b.x + b.w, b.y + b.h, bg);
        drawTransferIcon(r, b.x, b.y, b.w, b.h, fg);
        ReflectionHelper.setTransferButtonBounds(b.x, b.y, b.w, b.h);
    }

    private static void drawResumeIcon(McpRenderer r, int bx, int by, int bw, int bh, int color) {
        int p = ICON_PAD;
        int cx = bx + bw / 2;
        int cy = by + bh / 2;
        int hw = (bw - p * 2) / 2;
        int hh = (bh - p * 2) / 2;
        int steps = hh;
        for (int i = 0; i < steps; i++) {
            int rowY = cy - hh + i;
            int halfW = (i * hw) / steps;
            r.fill(cx - halfW, rowY, cx + halfW + 1, rowY + 1, color);
        }
    }

    private static void drawTransferIcon(McpRenderer r, int bx, int by, int bw, int bh, int color) {
        int p = ICON_PAD;
        int x1 = bx + p;
        int y1 = by + p;
        int x2 = bx + bw - p;
        int y2 = by + bh - p;
        int midX = (x1 + x2) / 2;
        int midY = (y1 + y2) / 2;
        r.fill(x1, midY - 1, x2, midY + 1, color);
        r.fill(midX - 1, y1, midX + 1, y2, color);
        int aw = 3;
        r.fill(x1, y1, x1 + aw, y1 + aw, color);
        r.fill(x2 - aw, y1, x2, y1 + aw, color);
        r.fill(x1, y2 - aw, x1 + aw, y2, color);
        r.fill(x2 - aw, y2 - aw, x2, y2, color);
    }
}

package xyz.langyo.minecraft.mcp.common;

public interface McpRenderer {
    void fill(int x1, int y1, int x2, int y2, int color);
    int drawString(Object font, String text, int x, int y, int color, boolean shadow);
    int getStringWidth(Object font, String text);
}

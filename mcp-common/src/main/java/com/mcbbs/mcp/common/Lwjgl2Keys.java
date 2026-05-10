package com.mcbbs.mcp.common;

public final class Lwjgl2Keys {

    public static final int KEY_RETURN = 0x1C;
    public static final int KEY_ESCAPE = 0x01;
    public static final int KEY_TAB = 0x0F;
    public static final int KEY_SPACE = 0x39;
    public static final int KEY_BACK = 0x0E;
    public static final int KEY_DELETE = 0xD7;
    public static final int KEY_UP = 0xC8;
    public static final int KEY_DOWN = 0xD0;
    public static final int KEY_LEFT = 0xCB;
    public static final int KEY_RIGHT = 0xCD;
    public static final int KEY_LSHIFT = 0x2A;
    public static final int KEY_LCONTROL = 0x1D;
    public static final int KEY_LMENU = 0x38;
    public static final int KEY_F1 = 0x3B;
    public static final int KEY_A = 0x1E;
    public static final int KEY_0 = 0x0B;

    public static int keyCode(String name) {
        String n = name.toLowerCase();
        if (n.equals("enter") || n.equals("return")) return KEY_RETURN;
        if (n.equals("escape") || n.equals("esc")) return KEY_ESCAPE;
        if (n.equals("tab")) return KEY_TAB;
        if (n.equals("space")) return KEY_SPACE;
        if (n.equals("backspace")) return KEY_BACK;
        if (n.equals("delete")) return KEY_DELETE;
        if (n.equals("up")) return KEY_UP;
        if (n.equals("down")) return KEY_DOWN;
        if (n.equals("left")) return KEY_LEFT;
        if (n.equals("right")) return KEY_RIGHT;
        if (n.equals("shift")) return KEY_LSHIFT;
        if (n.equals("ctrl") || n.equals("control")) return KEY_LCONTROL;
        if (n.equals("alt")) return KEY_LMENU;
        if (n.startsWith("f") && n.length() >= 2) {
            try {
                int fn = Integer.parseInt(n.substring(1));
                if (fn >= 1 && fn <= 12) return KEY_F1 + (fn - 1);
            } catch (NumberFormatException ignored) {}
        }
        if (n.length() == 1) {
            char c = n.charAt(0);
            if (c >= 'a' && c <= 'z') return KEY_A + (c - 'a');
        }
        return -1;
    }
}

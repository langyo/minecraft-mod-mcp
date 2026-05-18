package xyz.langyo.minecraft.mcp.common;

public final class GlfwKeys {

    public static final int KEY_ENTER = 0xFF0D;
    public static final int KEY_ESCAPE = 0xFF1B;
    public static final int KEY_TAB = 0xFF09;
    public static final int KEY_SPACE = 0x0020;
    public static final int KEY_BACKSPACE = 0xFF08;
    public static final int KEY_DELETE = 0xFFFF;
    public static final int KEY_UP = 0xFF52;
    public static final int KEY_DOWN = 0xFF54;
    public static final int KEY_LEFT = 0xFF51;
    public static final int KEY_RIGHT = 0xFF53;
    public static final int KEY_LEFT_SHIFT = 0xFFE1;
    public static final int KEY_LEFT_CONTROL = 0xFFE3;
    public static final int KEY_LEFT_ALT = 0xFFE9;
    public static final int KEY_F1 = 0xFFBE;
    public static final int KEY_F2 = 0xFFBF;
    public static final int KEY_F3 = 0xFFC0;
    public static final int KEY_F4 = 0xFFC1;
    public static final int KEY_F5 = 0xFFC2;
    public static final int KEY_F6 = 0xFFC3;
    public static final int KEY_F7 = 0xFFC4;
    public static final int KEY_F8 = 0xFFC5;
    public static final int KEY_F9 = 0xFFC6;
    public static final int KEY_F10 = 0xFFC7;
    public static final int KEY_F11 = 0xFFC8;
    public static final int KEY_F12 = 0xFFC9;
    public static final int KEY_A = 0x0041;
    public static final int KEY_B = 0x0042;
    public static final int KEY_C = 0x0043;
    public static final int KEY_D = 0x0044;
    public static final int KEY_E = 0x0045;
    public static final int KEY_F = 0x0046;
    public static final int KEY_G = 0x0047;
    public static final int KEY_H = 0x0048;
    public static final int KEY_I = 0x0049;
    public static final int KEY_J = 0x004A;
    public static final int KEY_K = 0x004B;
    public static final int KEY_L = 0x004C;
    public static final int KEY_M = 0x004D;
    public static final int KEY_N = 0x004E;
    public static final int KEY_O = 0x004F;
    public static final int KEY_P = 0x0050;
    public static final int KEY_Q = 0x0051;
    public static final int KEY_R = 0x0052;
    public static final int KEY_S = 0x0053;
    public static final int KEY_T = 0x0054;
    public static final int KEY_U = 0x0055;
    public static final int KEY_V = 0x0056;
    public static final int KEY_W = 0x0057;
    public static final int KEY_X = 0x0058;
    public static final int KEY_Y = 0x0059;
    public static final int KEY_Z = 0x005A;
    public static final int KEY_0 = 0x0030;
    public static final int KEY_1 = 0x0031;
    public static final int KEY_2 = 0x0032;
    public static final int KEY_3 = 0x0033;
    public static final int KEY_4 = 0x0034;
    public static final int KEY_5 = 0x0035;
    public static final int KEY_6 = 0x0036;
    public static final int KEY_7 = 0x0037;
    public static final int KEY_8 = 0x0038;
    public static final int KEY_9 = 0x0039;
    public static final int KEY_PERIOD = 0x002E;
    public static final int KEY_COMMA = 0x002C;
    public static final int KEY_MINUS = 0x002D;
    public static final int KEY_EQUAL = 0x003D;
    public static final int KEY_LEFT_BRACKET = 0x005B;
    public static final int KEY_RIGHT_BRACKET = 0x005D;
    public static final int KEY_SEMICOLON = 0x003B;
    public static final int KEY_GRAVE_ACCENT = 0x0060;

    private static boolean resolved;
    private static int GLFW_KEY_ENTER_R;
    private static int GLFW_KEY_ESCAPE_R;
    private static int GLFW_KEY_TAB_R;
    private static int GLFW_KEY_SPACE_R;
    private static int GLFW_KEY_BACKSPACE_R;
    private static int GLFW_KEY_DELETE_R;
    private static int GLFW_KEY_UP_R;
    private static int GLFW_KEY_DOWN_R;
    private static int GLFW_KEY_LEFT_R;
    private static int GLFW_KEY_RIGHT_R;
    private static int GLFW_KEY_LEFT_SHIFT_R;
    private static int GLFW_KEY_LEFT_CONTROL_R;
    private static int GLFW_KEY_LEFT_ALT_R;
    private static int GLFW_KEY_F1_R;
    private static int GLFW_KEY_A_R;
    private static int GLFW_KEY_0_R;
    private static int GLFW_KEY_PERIOD_R;
    private static int GLFW_KEY_COMMA_R;
    private static int GLFW_KEY_MINUS_R;
    private static int GLFW_KEY_EQUAL_R;
    private static int GLFW_KEY_LEFT_BRACKET_R;
    private static int GLFW_KEY_RIGHT_BRACKET_R;
    private static int GLFW_KEY_SEMICOLON_R;
    private static int GLFW_KEY_GRAVE_ACCENT_R;

    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;
        if (!ReflectionHelper.isLwjgl3()) return;
        try {
            Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
            GLFW_KEY_ENTER_R = (int) glfw.getField("GLFW_KEY_ENTER").get(null);
            GLFW_KEY_ESCAPE_R = (int) glfw.getField("GLFW_KEY_ESCAPE").get(null);
            GLFW_KEY_TAB_R = (int) glfw.getField("GLFW_KEY_TAB").get(null);
            GLFW_KEY_SPACE_R = (int) glfw.getField("GLFW_KEY_SPACE").get(null);
            GLFW_KEY_BACKSPACE_R = (int) glfw.getField("GLFW_KEY_BACKSPACE").get(null);
            GLFW_KEY_DELETE_R = (int) glfw.getField("GLFW_KEY_DELETE").get(null);
            GLFW_KEY_UP_R = (int) glfw.getField("GLFW_KEY_UP").get(null);
            GLFW_KEY_DOWN_R = (int) glfw.getField("GLFW_KEY_DOWN").get(null);
            GLFW_KEY_LEFT_R = (int) glfw.getField("GLFW_KEY_LEFT").get(null);
            GLFW_KEY_RIGHT_R = (int) glfw.getField("GLFW_KEY_RIGHT").get(null);
            GLFW_KEY_LEFT_SHIFT_R = (int) glfw.getField("GLFW_KEY_LEFT_SHIFT").get(null);
            GLFW_KEY_LEFT_CONTROL_R = (int) glfw.getField("GLFW_KEY_LEFT_CONTROL").get(null);
            GLFW_KEY_LEFT_ALT_R = (int) glfw.getField("GLFW_KEY_LEFT_ALT").get(null);
            GLFW_KEY_F1_R = (int) glfw.getField("GLFW_KEY_F1").get(null);
            GLFW_KEY_A_R = (int) glfw.getField("GLFW_KEY_A").get(null);
            GLFW_KEY_0_R = (int) glfw.getField("GLFW_KEY_0").get(null);
            GLFW_KEY_PERIOD_R = (int) glfw.getField("GLFW_KEY_PERIOD").get(null);
            GLFW_KEY_COMMA_R = (int) glfw.getField("GLFW_KEY_COMMA").get(null);
            GLFW_KEY_MINUS_R = (int) glfw.getField("GLFW_KEY_MINUS").get(null);
            GLFW_KEY_EQUAL_R = (int) glfw.getField("GLFW_KEY_EQUAL").get(null);
            GLFW_KEY_LEFT_BRACKET_R = (int) glfw.getField("GLFW_KEY_LEFT_BRACKET").get(null);
            GLFW_KEY_RIGHT_BRACKET_R = (int) glfw.getField("GLFW_KEY_RIGHT_BRACKET").get(null);
            GLFW_KEY_SEMICOLON_R = (int) glfw.getField("GLFW_KEY_SEMICOLON").get(null);
            GLFW_KEY_GRAVE_ACCENT_R = (int) glfw.getField("GLFW_KEY_GRAVE_ACCENT").get(null);
        } catch (Exception e) {
            System.err.println("[GlfwKeys] Failed to resolve GLFW constants: " + e.getMessage());
        }
    }

    public static int keyCode(String name) {
        String n = name.toLowerCase();
        resolve();
        if (ReflectionHelper.isLwjgl3()) {
            return resolveGlfw(n);
        }
        return resolveLwjgl2(n);
    }

    public static int charCode(char ch) {
        resolve();
        if (ReflectionHelper.isLwjgl3()) {
            return charCodeGlfw(ch);
        }
        return charCodeLwjgl2(ch);
    }

    private static int resolveGlfw(String n) {
        if (n.equals("enter") || n.equals("return")) return GLFW_KEY_ENTER_R;
        if (n.equals("escape") || n.equals("esc")) return GLFW_KEY_ESCAPE_R;
        if (n.equals("tab")) return GLFW_KEY_TAB_R;
        if (n.equals("space")) return GLFW_KEY_SPACE_R;
        if (n.equals("backspace")) return GLFW_KEY_BACKSPACE_R;
        if (n.equals("delete")) return GLFW_KEY_DELETE_R;
        if (n.equals("up")) return GLFW_KEY_UP_R;
        if (n.equals("down")) return GLFW_KEY_DOWN_R;
        if (n.equals("left")) return GLFW_KEY_LEFT_R;
        if (n.equals("right")) return GLFW_KEY_RIGHT_R;
        if (n.equals("shift")) return GLFW_KEY_LEFT_SHIFT_R;
        if (n.equals("ctrl") || n.equals("control")) return GLFW_KEY_LEFT_CONTROL_R;
        if (n.equals("alt")) return GLFW_KEY_LEFT_ALT_R;
        if (n.startsWith("f") && n.length() >= 2) {
            try {
                int fn = Integer.parseInt(n.substring(1));
                if (fn >= 1 && fn <= 12) return GLFW_KEY_F1_R + (fn - 1);
            } catch (NumberFormatException ignored) {}
        }
        if (n.length() == 1) {
            char c = n.charAt(0);
            if (c >= 'a' && c <= 'z') return GLFW_KEY_A_R + (c - 'a');
            if (c >= '0' && c <= '9') return GLFW_KEY_0_R + (c - '0');
        }
        return -1;
    }

    private static int resolveLwjgl2(String n) {
        if (n.equals("enter") || n.equals("return")) return KEY_ENTER;
        if (n.equals("escape") || n.equals("esc")) return KEY_ESCAPE;
        if (n.equals("tab")) return KEY_TAB;
        if (n.equals("space")) return KEY_SPACE;
        if (n.equals("backspace")) return KEY_BACKSPACE;
        if (n.equals("delete")) return KEY_DELETE;
        if (n.equals("up")) return KEY_UP;
        if (n.equals("down")) return KEY_DOWN;
        if (n.equals("left")) return KEY_LEFT;
        if (n.equals("right")) return KEY_RIGHT;
        if (n.equals("shift")) return KEY_LEFT_SHIFT;
        if (n.equals("ctrl") || n.equals("control")) return KEY_LEFT_CONTROL;
        if (n.equals("alt")) return KEY_LEFT_ALT;
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

    private static int charCodeGlfw(char ch) {
        if (ch >= 'a' && ch <= 'z') return GLFW_KEY_A_R + (ch - 'a');
        if (ch >= 'A' && ch <= 'Z') return GLFW_KEY_A_R + (ch - 'A');
        if (ch >= '0' && ch <= '9') return GLFW_KEY_0_R + (ch - '0');
        switch (ch) {
            case ' ': return GLFW_KEY_SPACE_R;
            case '.': return GLFW_KEY_PERIOD_R;
            case ',': return GLFW_KEY_COMMA_R;
            case '-': return GLFW_KEY_MINUS_R;
            case '=': return GLFW_KEY_EQUAL_R;
            case '[': return GLFW_KEY_LEFT_BRACKET_R;
            case ']': return GLFW_KEY_RIGHT_BRACKET_R;
            case ';': return GLFW_KEY_SEMICOLON_R;
            case '`': return GLFW_KEY_GRAVE_ACCENT_R;
            default: return -1;
        }
    }

    private static int charCodeLwjgl2(char ch) {
        if (ch >= 32 && ch < 127) return (int) ch;
        return -1;
    }
}

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
    public static final int KEY_A = 0x0041;
    public static final int KEY_0 = 0x0030;

    // org.lwjgl.input.Keyboard key codes (DirectInput-style scancodes) used
    // by the 1.7.x-1.12.x LWJGL2 eras. The GLFW/X11 constants above do NOT
    // apply there (e.g. GLFW Enter 0xFF0D is "D" in LWJGL2, whose real Enter
    // is 28) — issue #45.
    private static final int LWJGL2_KEY_ESCAPE = 0x01;   // 1
    private static final int LWJGL2_KEY_0 = 0x0B;        // 11
    private static final int LWJGL2_KEY_TAB = 0x0F;      // 15
    private static final int LWJGL2_KEY_BACKSPACE = 0x0E;// 14
    private static final int LWJGL2_KEY_RETURN = 0x1C;   // 28
    private static final int LWJGL2_KEY_LCONTROL = 0x1D; // 29
    private static final int LWJGL2_KEY_A = 0x1E;        // 30
    private static final int LWJGL2_KEY_LSHIFT = 0x2A;   // 42
    private static final int LWJGL2_KEY_LMENU = 0x38;    // 56 (left alt)
    private static final int LWJGL2_KEY_SPACE = 0x39;    // 57
    private static final int LWJGL2_KEY_F1 = 0x3B;       // 59 (F1-F10 contiguous)
    private static final int LWJGL2_KEY_F11 = 0x57;      // 87 (after the keypad block)
    private static final int LWJGL2_KEY_F12 = 0x58;      // 88
    private static final int LWJGL2_KEY_DELETE = 0xD3;   // 211
    private static final int LWJGL2_KEY_UP = 0xC8;       // 200
    private static final int LWJGL2_KEY_DOWN = 0xD0;     // 208
    private static final int LWJGL2_KEY_LEFT = 0xCB;     // 203
    private static final int LWJGL2_KEY_RIGHT = 0xCD;    // 205

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
        if (n.startsWith("key.keyboard.")) {
            String suffix = n.substring("key.keyboard.".length());
            if (suffix.length() == 1) {
                char c = suffix.charAt(0);
                if (c >= '0' && c <= '9') return GLFW_KEY_0_R + (c - '0');
                if (c >= 'a' && c <= 'z') return GLFW_KEY_A_R + (c - 'a');
                if (c >= 'A' && c <= 'Z') return GLFW_KEY_A_R + (c - 'A');
            }
        }
        return -1;
    }

    // DirectInput scancodes follow the physical QWERTY layout, so letters and
    // digits are not contiguous by character value.
    private static int lwjgl2LetterCode(char c) {
        int idx = "qwertyuiop".indexOf(c);
        if (idx >= 0) return 0x10 + idx;
        idx = "asdfghjkl".indexOf(c);
        if (idx >= 0) return 0x1E + idx;
        idx = "zxcvbnm".indexOf(c);
        if (idx >= 0) return 0x2C + idx;
        return -1;
    }

    private static int lwjgl2DigitCode(char c) {
        if (c == '0') return LWJGL2_KEY_0;
        if (c >= '1' && c <= '9') return 0x02 + (c - '1');
        return -1;
    }

    private static int resolveLwjgl2(String n) {
        if (n.equals("enter") || n.equals("return")) return LWJGL2_KEY_RETURN;
        if (n.equals("escape") || n.equals("esc")) return LWJGL2_KEY_ESCAPE;
        if (n.equals("tab")) return LWJGL2_KEY_TAB;
        if (n.equals("space")) return LWJGL2_KEY_SPACE;
        if (n.equals("backspace")) return LWJGL2_KEY_BACKSPACE;
        if (n.equals("delete")) return LWJGL2_KEY_DELETE;
        if (n.equals("up")) return LWJGL2_KEY_UP;
        if (n.equals("down")) return LWJGL2_KEY_DOWN;
        if (n.equals("left")) return LWJGL2_KEY_LEFT;
        if (n.equals("right")) return LWJGL2_KEY_RIGHT;
        if (n.equals("shift")) return LWJGL2_KEY_LSHIFT;
        if (n.equals("ctrl") || n.equals("control")) return LWJGL2_KEY_LCONTROL;
        if (n.equals("alt")) return LWJGL2_KEY_LMENU;
        if (n.startsWith("f") && n.length() >= 2) {
            try {
                int fn = Integer.parseInt(n.substring(1));
                if (fn >= 1 && fn <= 10) return LWJGL2_KEY_F1 + (fn - 1);
                if (fn == 11) return LWJGL2_KEY_F11;
                if (fn == 12) return LWJGL2_KEY_F12;
            } catch (NumberFormatException ignored) {}
        }
        if (n.length() == 1) {
            int digit = lwjgl2DigitCode(n.charAt(0));
            if (digit > 0) return digit;
            return lwjgl2LetterCode(n.charAt(0));
        }
        if (n.startsWith("key.keyboard.")) {
            String suffix = n.substring("key.keyboard.".length());
            if (suffix.length() == 1) {
                char c = suffix.charAt(0);
                int code = lwjgl2DigitCode(c);
                if (code > 0) return code;
                if (c >= 'A' && c <= 'Z') c = Character.toLowerCase(c);
                return lwjgl2LetterCode(c);
            }
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

package xyz.langyo.minecraft.mcp.common;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public final class InputInjectionHelper {

    private static Robot awtRobot;

    static {
        try { awtRobot = new Robot(); awtRobot.setAutoDelay(10); } catch (Exception e) {}
    }

    private InputInjectionHelper() {}

    public static void sendKey(long handle, int key, int action) {
        if (ReflectionCache.LWJGL3) {
            try {
                Object mc = ReflectionCache.getMinecraftInstance();
                Object kbHandler = getKeyboardHandler(mc);
                if (kbHandler != null) {
                    for (java.lang.reflect.Method m : kbHandler.getClass().getDeclaredMethods()) {
                        Class<?>[] pts = m.getParameterTypes();
                        if (pts.length == 5 && pts[0] == long.class && pts[1] == int.class
                                && pts[2] == int.class && pts[3] == int.class && pts[4] == int.class
                                && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                            m.setAccessible(true);
                            m.invoke(kbHandler, handle, key, 0, action, 0);
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[Input] sendKey GLFW: " + e.getMessage());
            }
        }
        if (McpConfig.isBackgroundInputMode()) return;
        try {
            Robot r = awtRobot;
            if (r == null) return;
            int vk = glfwToAwt(key);
            if (vk < 0) return;
            if (action == 1) r.keyPress(vk);
            else r.keyRelease(vk);
        } catch (Exception e) { System.err.println("[Input] sendKey: " + e.getMessage()); }
    }

    private static Object getKeyboardHandler(Object mc) {
        try { return mc.getClass().getField("keyboardHandler").get(mc); } catch (Exception ignored) {}
        try { return mc.getClass().getDeclaredField("keyboardHandler").get(mc); } catch (Exception ignored) {}
        java.lang.reflect.Field discovered = ReflectionCache.getDiscoveredField("keyboardHandler");
        if (discovered != null) { try { discovered.setAccessible(true); return discovered.get(mc); } catch (Exception ignored) {} }
        for (java.lang.reflect.Field f : ReflectionCache.getAllFields(mc.getClass())) {
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                try { f.setAccessible(true); Object v = f.get(mc); if (v != null && ReflectionCache.isKeyboardHandlerType(v.getClass())) return v; } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static Object getMouseHandlerField(Object mc) {
        return ReflectionCache.getMouseHandler(mc);
    }

    public static void sendMouseButton(long handle, int button, int action) {
        if (ReflectionCache.LWJGL3 && handle != 0) {
            try {
                Object mc = ReflectionCache.getMinecraftInstance();
                Object mouseHandler = ReflectionCache.getMouseHandler(mc);
                if (mouseHandler != null) {
                    java.lang.reflect.Method target = ReflectionCache.findMouseButtonMethod(mouseHandler.getClass());
                    if (target != null) {
                        target.setAccessible(true);
                        Class<?>[] pt = target.getParameterTypes();
                        if (pt.length == 3 && pt[1] != int.class) {
                            Object mouseInput = createMouseInput(pt[1], button, 0);
                            if (mouseInput != null) {
                                target.invoke(mouseHandler, handle, mouseInput, action);
                                return;
                            }
                        } else {
                            target.invoke(mouseHandler, handle, button, action, 0);
                            return;
                        }
                    }
                }
                ReflectionHelper.dbg("sendMouseButton: NO matching method found!");
            } catch (Exception e) {
                ReflectionHelper.dbg("sendMouseButton GLFW: " + e.getMessage());
            }
        }
        if (McpConfig.isBackgroundInputMode()) return;
        try {
            Robot r = awtRobot;
            if (r == null) return;
            int mask = button == 1 ? InputEvent.BUTTON2_DOWN_MASK
                     : button == 2 ? InputEvent.BUTTON3_DOWN_MASK
                     : InputEvent.BUTTON1_DOWN_MASK;
            if (action == 1) r.mousePress(mask);
            else r.mouseRelease(mask);
        } catch (Exception e) { System.err.println("[Input] sendMouseButton: " + e.getMessage()); }
    }

    public static Object createMouseInputPublic(Class<?> mouseInputClass, int button, int modifiers) {
        return createMouseInput(mouseInputClass, button, modifiers);
    }

    private static Object createMouseInput(Class<?> mouseInputClass, int button, int modifiers) {
        try {
            for (java.lang.reflect.Constructor<?> c : mouseInputClass.getDeclaredConstructors()) {
                c.setAccessible(true);
                Class<?>[] pt = c.getParameterTypes();
                if (pt.length == 2 && pt[0] == int.class && pt[1] == int.class) {
                    return c.newInstance(button, modifiers);
                }
                if (pt.length == 1 && pt[0] == int.class) {
                    return c.newInstance(button);
                }
            }
            for (java.lang.reflect.Method m : mouseInputClass.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    Class<?>[] pt = m.getParameterTypes();
                    if (m.getReturnType() == mouseInputClass && pt.length == 2 && pt[0] == int.class && pt[1] == int.class) {
                        return m.invoke(null, button, modifiers);
                    }
                    if (m.getReturnType() == mouseInputClass && pt.length == 1 && pt[0] == int.class) {
                        return m.invoke(null, button);
                    }
                }
            }
            Object[] enums = mouseInputClass.getEnumConstants();
            if (enums != null && enums.length > 0) {
                for (Object e : enums) {
                    String name = ((Enum<?>) e).name();
                    if (button == 0 && name.contains("LEFT")) return e;
                    if (button == 1 && name.contains("RIGHT")) return e;
                    if (button == 2 && name.contains("MIDDLE")) return e;
                }
                return enums[0];
            }
        } catch (Exception e) {
            ReflectionHelper.dbg("createMouseInput failed: " + e.getMessage());
        }
        return null;
    }

    public static void sendScroll(long handle, double scrollY) {
        if (ReflectionCache.LWJGL3) {
            try {
                Object mc = ReflectionCache.getMinecraftInstance();
                Object mouseHandler = ReflectionCache.getMouseHandler(mc);
                if (mouseHandler != null) {
                    for (java.lang.reflect.Method m : mouseHandler.getClass().getDeclaredMethods()) {
                        String name = m.getName();
                        if ((name.contains("scroll") || name.startsWith("lambda$setup$"))
                            && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                            Class<?>[] pt = m.getParameterTypes();
                            if (pt.length == 3 && pt[0] == long.class && pt[1] == double.class && pt[2] == double.class) {
                                m.setAccessible(true);
                                m.invoke(mouseHandler, handle, 0.0, scrollY);
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[Input] sendScroll GLFW: " + e.getMessage());
            }
        }
        if (McpConfig.isBackgroundInputMode()) return;
        try {
            Robot r = awtRobot;
            if (r == null) return;
            r.mouseWheel((int) scrollY);
        } catch (Exception e) { System.err.println("[Input] sendScroll: " + e.getMessage()); }
    }

    public static void sendMouseMoveInternal(long handle, double x, double y) {
        if (ReflectionCache.LWJGL3) {
            try {
                Object mc = ReflectionCache.getMinecraftInstance();
                Object mouseHandler = ReflectionCache.getMouseHandler(mc);
                if (mouseHandler != null) {
                    for (java.lang.reflect.Method m : mouseHandler.getClass().getDeclaredMethods()) {
                        String name = m.getName();
                        if ((name.contains("cursor") || name.contains("pos") || name.startsWith("lambda$setup$"))
                            && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                            Class<?>[] pt = m.getParameterTypes();
                            if (pt.length == 3 && pt[0] == long.class && pt[1] == double.class && pt[2] == double.class) {
                                m.setAccessible(true);
                                m.invoke(mouseHandler, handle, x, y);
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[Input] sendMouseMove GLFW: " + e.getMessage());
            }
        }
    }

    public static void sendMouseDrag(long handle, int x1, int y1, int x2, int y2, int button, int steps) {
        setCursorPos(handle, x1, y1);
        try { Thread.sleep(20); } catch (Exception ignored) {}
        sendMouseButton(handle, button, 1);
        try { Thread.sleep(20); } catch (Exception ignored) {}
        if (steps < 1) steps = 1;
        for (int i = 1; i <= steps; i++) {
            int cx = x1 + (x2 - x1) * i / steps;
            int cy = y1 + (y2 - y1) * i / steps;
            setCursorPos(handle, cx, cy);
            try { Thread.sleep(10); } catch (Exception ignored) {}
        }
        sendMouseButton(handle, button, 0);
    }

    public static void setCursorPos(long handle, double x, double y) {
        ReflectionCache.initClassCache();
        boolean background = McpConfig.isBackgroundInputMode();
        if (ReflectionCache.LWJGL3) {
            try {
                if (!background) {
                    java.lang.reflect.Method glfwSetCursorPosMethod = ReflectionCache.getGlfwSetCursorPosMethod();
                    if (glfwSetCursorPosMethod != null) {
                        glfwSetCursorPosMethod.invoke(null, handle, x, y);
                    } else {
                        java.lang.reflect.Method setPos = ReflectionCache.getGlfwClass().getMethod("glfwSetCursorPos", long.class, double.class, double.class);
                        setPos.invoke(null, handle, x, y);
                    }
                }

                Object mc = ReflectionCache.getMinecraftInstance();
                Object mouseHandler = ReflectionCache.getMouseHandler(mc);
                if (mouseHandler != null) {
                    int doubleFieldsSet = 0;
                    for (java.lang.reflect.Field f : mouseHandler.getClass().getDeclaredFields()) {
                        if (f.getType() == double.class) {
                            String name = f.getName().toLowerCase();
                            if ((name.contains("xpos") || name.equals("x") || name.contains("cursorx") || doubleFieldsSet == 0) && doubleFieldsSet < 2) {
                                f.setAccessible(true);
                                f.setDouble(mouseHandler, doubleFieldsSet == 0 ? x : y);
                                doubleFieldsSet++;
                            } else if ((name.contains("ypos") || name.equals("y") || name.contains("cursory")) && doubleFieldsSet < 2) {
                                f.setAccessible(true);
                                f.setDouble(mouseHandler, y);
                                doubleFieldsSet++;
                            }
                        }
                    }
                }
                return;
            } catch (Exception e) {
                System.err.println("[Input] setCursorPos GLFW: " + e.getMessage());
            }
        }
        if (background) return;
        try {
            Robot r = awtRobot;
            if (r == null) return;
            int wx = 0, wy = 0;
            if (ReflectionCache.LWJGL3) {
                try {
                    Class<?> glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
                    java.lang.reflect.Method getX = glfwClass.getMethod("glfwGetWindowPosX", long.class);
                    java.lang.reflect.Method getY = glfwClass.getMethod("glfwGetWindowPosY", long.class);
                    wx = ((Number) getX.invoke(null, handle)).intValue();
                    wy = ((Number) getY.invoke(null, handle)).intValue();
                } catch (Exception ignored) {}
            } else {
                try {
                    Class<?> displayClass = Class.forName("org.lwjgl.opengl.Display");
                    wx = ((Number) displayClass.getMethod("getX").invoke(null)).intValue();
                    wy = ((Number) displayClass.getMethod("getY").invoke(null)).intValue();
                } catch (Exception ignored) {}
            }
            r.mouseMove(wx + (int) x, wy + (int) y);
        } catch (Exception e) { System.err.println("[Input] setCursorPos: " + e.getMessage()); }
    }

    /**
     * Maps a key code from GlfwKeys.keyCode to an AWT VK code for the Robot
     * fallback. The incoming code is a GLFW key code on LWJGL3 runtimes and
     * an LWJGL2 DirectInput scancode otherwise (issue #76 B4: previously only
     * X11 keysym values were mapped, so every special key resolved to -1 and
     * the Robot press was dropped).
     */
    private static int glfwToAwt(int glfwKey) {
        if (glfwKey >= 'A' && glfwKey <= 'Z') return KeyEvent.VK_A + (glfwKey - 'A');
        if (glfwKey >= 'a' && glfwKey <= 'z') return KeyEvent.VK_A + (glfwKey - 'a');
        if (glfwKey >= '0' && glfwKey <= '9') return KeyEvent.VK_0 + (glfwKey - '0');
        switch (glfwKey) {
            case 0xFF0D: return KeyEvent.VK_ENTER;
            case 0xFF1B: return KeyEvent.VK_ESCAPE;
            case 0xFF09: return KeyEvent.VK_TAB;
            case 0x0020: return KeyEvent.VK_SPACE;
            case 0xFF08: return KeyEvent.VK_BACK_SPACE;
            case 0xFFFF: return KeyEvent.VK_DELETE;
            case 0xFF52: return KeyEvent.VK_UP;
            case 0xFF54: return KeyEvent.VK_DOWN;
            case 0xFF51: return KeyEvent.VK_LEFT;
            case 0xFF53: return KeyEvent.VK_RIGHT;
            case 0xFFE1: return KeyEvent.VK_SHIFT;
            case 0xFFE3: return KeyEvent.VK_CONTROL;
            case 0xFFE9: return KeyEvent.VK_ALT;
            case 0xFFBE: return KeyEvent.VK_F1;
            case 0xFFBF: return KeyEvent.VK_F2;
            case 0xFFC0: return KeyEvent.VK_F3;
            case 0xFFC1: return KeyEvent.VK_F4;
            case 0xFFC2: return KeyEvent.VK_F5;
            case 0xFFC3: return KeyEvent.VK_F6;
            case 0xFFC4: return KeyEvent.VK_F7;
            case 0xFFC5: return KeyEvent.VK_F8;
            case 0xFFC6: return KeyEvent.VK_F9;
            case 0xFFC7: return KeyEvent.VK_F10;
            case 0xFFC8: return KeyEvent.VK_F11;
            case 0xFFC9: return KeyEvent.VK_F12;
            case 0x002E: return KeyEvent.VK_PERIOD;
            case 0x002C: return KeyEvent.VK_COMMA;
            case 0x002D: return KeyEvent.VK_MINUS;
            case 0x003D: return KeyEvent.VK_EQUALS;
            case 0x005B: return KeyEvent.VK_OPEN_BRACKET;
            case 0x005D: return KeyEvent.VK_CLOSE_BRACKET;
            case 0x003B: return KeyEvent.VK_SEMICOLON;
            case 0x0060: return KeyEvent.VK_BACK_QUOTE;
            default: return ReflectionCache.LWJGL3 ? glfwSpecialToAwt(glfwKey) : lwjgl2ScancodeToAwt(glfwKey);
        }
    }

    /** GLFW special keys (256+). Printable GLFW codes equal ASCII and are
     *  already handled by the ranges above (GLFW 'A'-'Z' are 65-90). */
    private static int glfwSpecialToAwt(int key) {
        switch (key) {
            case 256: return KeyEvent.VK_ESCAPE;
            case 257: return KeyEvent.VK_ENTER;
            case 258: return KeyEvent.VK_TAB;
            case 259: return KeyEvent.VK_BACK_SPACE;
            case 260: return KeyEvent.VK_INSERT;
            case 261: return KeyEvent.VK_DELETE;
            case 262: return KeyEvent.VK_RIGHT;
            case 263: return KeyEvent.VK_LEFT;
            case 264: return KeyEvent.VK_DOWN;
            case 265: return KeyEvent.VK_UP;
            case 266: return KeyEvent.VK_PAGE_UP;
            case 267: return KeyEvent.VK_PAGE_DOWN;
            case 268: return KeyEvent.VK_HOME;
            case 269: return KeyEvent.VK_END;
            case 280: return KeyEvent.VK_CAPS_LOCK;
            case 340: return KeyEvent.VK_SHIFT;
            case 341: return KeyEvent.VK_CONTROL;
            case 342: return KeyEvent.VK_ALT;
            default:
                if (key >= 290 && key <= 301) return KeyEvent.VK_F1 + (key - 290);
                return -1;
        }
    }

    /** LWJGL2 DirectInput scancodes (org.lwjgl.input.Keyboard codes). */
    private static int lwjgl2ScancodeToAwt(int key) {
        switch (key) {
            case 0x01: return KeyEvent.VK_ESCAPE;   // 1
            case 0x0C: return KeyEvent.VK_MINUS;    // 12
            case 0x0D: return KeyEvent.VK_EQUALS;   // 13
            case 0x0E: return KeyEvent.VK_BACK_SPACE; // 14
            case 0x0F: return KeyEvent.VK_TAB;      // 15
            case 0x1A: return KeyEvent.VK_OPEN_BRACKET;  // 26
            case 0x1B: return KeyEvent.VK_CLOSE_BRACKET; // 27
            case 0x1C: return KeyEvent.VK_ENTER;    // 28
            case 0x1D: return KeyEvent.VK_CONTROL;  // 29
            case 0x27: return KeyEvent.VK_SEMICOLON;    // 39
            case 0x28: return KeyEvent.VK_QUOTE;        // 40 apostrophe
            case 0x29: return KeyEvent.VK_BACK_QUOTE;   // 41 grave
            case 0x2A: return KeyEvent.VK_SHIFT;        // 42
            case 0x2B: return KeyEvent.VK_BACK_SLASH;   // 43
            case 0x33: return KeyEvent.VK_COMMA;        // 51
            case 0x34: return KeyEvent.VK_PERIOD;       // 52
            case 0x35: return KeyEvent.VK_SLASH;        // 53
            case 0x37: return KeyEvent.VK_MULTIPLY;     // 55 keypad *
            case 0x38: return KeyEvent.VK_ALT;          // 56
            case 0x39: return KeyEvent.VK_SPACE;        // 57
            case 0xC8: return KeyEvent.VK_UP;           // 200
            case 0xCB: return KeyEvent.VK_LEFT;         // 203
            case 0xCD: return KeyEvent.VK_RIGHT;        // 205
            case 0xD0: return KeyEvent.VK_DOWN;         // 208
            case 0xD3: return KeyEvent.VK_DELETE;       // 211
            default:
                if (key >= 0x02 && key <= 0x0A) return KeyEvent.VK_1 + (key - 0x02); // 1-9
                if (key == 0x0B) return KeyEvent.VK_0;                              // 11
                if (key >= 0x3B && key <= 0x44) return KeyEvent.VK_F1 + (key - 0x3B); // F1-F10 (59-68)
                if (key == 0x57) return KeyEvent.VK_F11;  // 87
                if (key == 0x58) return KeyEvent.VK_F12;  // 88
                // QWERTY letter blocks (DirectInput layout, see GlfwKeys)
                if (key >= 0x10 && key <= 0x19) return KeyEvent.VK_Q + ("qwertyuiop".charAt(key - 0x10) - 'q');
                if (key >= 0x1E && key <= 0x26) return KeyEvent.VK_A + ("asdfghjkl".charAt(key - 0x1E) - 'a');
                if (key >= 0x2C && key <= 0x32) return KeyEvent.VK_Z + ("zxcvbnm".charAt(key - 0x2C) - 'z');
                return -1;
        }
    }
}

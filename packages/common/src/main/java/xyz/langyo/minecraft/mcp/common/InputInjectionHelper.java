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
                Object kbHandler = mc.getClass().getField("keyboardHandler").get(mc);
                kbHandler.getClass().getMethod("keyPress", long.class, int.class, int.class, int.class, int.class)
                    .invoke(kbHandler, handle, key, 0, action, 0);
                return;
            } catch (NoSuchFieldException nsfe) {
                try {
                    Object mc = ReflectionCache.getMinecraftInstance();
                    Object kbHandler = mc.getClass().getMethod("keyboardHandler").invoke(mc);
                    kbHandler.getClass().getMethod("keyPress", long.class, int.class, int.class, int.class, int.class)
                        .invoke(kbHandler, handle, key, 0, action, 0);
                    return;
                } catch (Exception e2) {
                    System.err.println("[Input] sendKey GLFW: " + e2.getMessage());
                }
            } catch (Exception e) {
                System.err.println("[Input] sendKey GLFW: " + e.getMessage());
            }
        }
        try {
            Robot r = awtRobot;
            if (r == null) return;
            int vk = glfwToAwt(key);
            if (vk < 0) return;
            if (action == 1) r.keyPress(vk);
            else r.keyRelease(vk);
        } catch (Exception e) { System.err.println("[Input] sendKey: " + e.getMessage()); }
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
                        target.invoke(mouseHandler, handle, button, action, 0);
                        return;
                    }
                }
                try {
                    Class<?> glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
                    java.lang.reflect.Method glfwSetInputMode = glfwClass.getMethod("glfwSetInputMode", long.class, int.class, int.class);
                    int GLFW_STICKY_MOUSE_BUTTONS = glfwClass.getDeclaredField("GLFW_STICKY_MOUSE_BUTTONS").getInt(null);
                    glfwSetInputMode.invoke(null, handle, GLFW_STICKY_MOUSE_BUTTONS, 1);
                    Class<?>MouseButtonCallback = null;
                    for (java.lang.reflect.Method cbm : glfwClass.getDeclaredMethods()) {
                        if (cbm.getName().equals("glfwSetMouseButtonCallback") && cbm.getParameterCount() == 2) {
                            cbm.invoke(null, handle, (java.lang.reflect.Proxy.newProxyInstance(
                                Thread.currentThread().getContextClassLoader(),
                                new Class<?>[]{ Class.forName("org.lwjgl.glfw.GLFWMouseButtonCallbackI") },
                                (proxy, method, args) -> null
                            )));
                            break;
                        }
                    }
                } catch (Exception ignored) {}
                ReflectionHelper.dbg("sendMouseButton: NO matching method found!");
            } catch (Exception e) {
                ReflectionHelper.dbg("sendMouseButton GLFW: " + e.getMessage());
            }
        }
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

    public static void sendScroll(long handle, double scrollY) {
        if (ReflectionCache.LWJGL3) {
            try {
                Object mc = ReflectionCache.getMinecraftInstance();
                Object mouseHandler = mc.getClass().getField("mouseHandler").get(mc);
                for (java.lang.reflect.Method m : mouseHandler.getClass().getDeclaredMethods()) {
                    String name = m.getName();
                    if ((name.equals("lambda$setup$2") || name.equals("lambda$setup$3") || name.contains("scroll"))
                        && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                        Class<?>[] pt = m.getParameterTypes();
                        if (pt.length == 3 && pt[0] == long.class && pt[1] == double.class && pt[2] == double.class) {
                            m.setAccessible(true);
                            m.invoke(mouseHandler, handle, 0.0, scrollY);
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[Input] sendScroll GLFW: " + e.getMessage());
            }
        }
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
                Object mouseHandler = mc.getClass().getField("mouseHandler").get(mc);
                for (java.lang.reflect.Method m : mouseHandler.getClass().getDeclaredMethods()) {
                    String name = m.getName();
                    if ((name.contains("cursor") || name.equals("lambda$setup$0") || name.equals("lambda$setup$1"))
                        && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                        Class<?>[] pt = m.getParameterTypes();
                        if (pt.length == 3 && pt[0] == long.class && pt[1] == double.class && pt[2] == double.class) {
                            m.setAccessible(true);
                            m.invoke(mouseHandler, handle, x, y);
                            return;
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
        if (ReflectionCache.LWJGL3) {
            try {
                java.lang.reflect.Method glfwSetCursorPosMethod = ReflectionCache.getGlfwSetCursorPosMethod();
                if (glfwSetCursorPosMethod != null) {
                    glfwSetCursorPosMethod.invoke(null, handle, x, y);
                } else {
                    java.lang.reflect.Method setPos = ReflectionCache.getGlfwClass().getMethod("glfwSetCursorPos", long.class, double.class, double.class);
                    setPos.invoke(null, handle, x, y);
                }

                Object mc = ReflectionCache.getMinecraftInstance();
                Object mouseHandler = mc.getClass().getField("mouseHandler").get(mc);
                for (java.lang.reflect.Field f : mouseHandler.getClass().getDeclaredFields()) {
                    if (f.getType() == double.class) {
                        String name = f.getName().toLowerCase();
                        if (name.contains("xpos") || name.equals("x")) {
                            f.setAccessible(true);
                            f.setDouble(mouseHandler, x);
                        } else if (name.contains("ypos") || name.equals("y")) {
                            f.setAccessible(true);
                            f.setDouble(mouseHandler, y);
                        }
                    }
                }
                return;
            } catch (Exception e) {
                System.err.println("[Input] setCursorPos GLFW: " + e.getMessage());
            }
        }
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

    private static int glfwToAwt(int glfwKey) {
        if (glfwKey >= 'A' && glfwKey <= 'Z') return KeyEvent.VK_A + (glfwKey - 'A');
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
            default: return -1;
        }
    }
}

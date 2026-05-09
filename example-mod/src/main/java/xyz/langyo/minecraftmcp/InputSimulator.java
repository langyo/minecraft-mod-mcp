package xyz.langyo.minecraftmcp;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = MinecraftMcpMod.MOD_ID, value = Dist.CLIENT)
public class InputSimulator {
    private static Robot robot;

    static {
        try {
            robot = new Robot();
            robot.setAutoDelay(10);
            robot.setAutoWaitForIdle(true);
        } catch (AWTException e) {
            MinecraftMcpMod.LOGGER.error("[MCP Input] Failed to create Robot: {}", e.getMessage());
        }
    }

    private static final Map<String, Integer> KEY_MAP = new HashMap<>();

    static {
        KEY_MAP.put("escape", 256);
        KEY_MAP.put("esc", 256);
        KEY_MAP.put("enter", 257);
        KEY_MAP.put("return", 257);
        KEY_MAP.put("tab", 258);
        KEY_MAP.put("space", 32);
        KEY_MAP.put("shift", 340);
        KEY_MAP.put("ctrl", 341);
        KEY_MAP.put("control", 341);
        KEY_MAP.put("alt", 342);
        KEY_MAP.put("backspace", 259);
        KEY_MAP.put("bs", 259);
        KEY_MAP.put("delete", 261);
        KEY_MAP.put("del", 261);
        KEY_MAP.put("up", 265);
        KEY_MAP.put("down", 264);
        KEY_MAP.put("left", 263);
        KEY_MAP.put("right", 262);
        KEY_MAP.put("f1", 290);
        KEY_MAP.put("f2", 291);
        KEY_MAP.put("f3", 292);
        KEY_MAP.put("f4", 293);
        KEY_MAP.put("f5", 294);
        KEY_MAP.put("f6", 295);
        KEY_MAP.put("f7", 296);
        KEY_MAP.put("f8", 297);
        KEY_MAP.put("f9", 298);
        KEY_MAP.put("f10", 299);
        KEY_MAP.put("f11", 300);
        KEY_MAP.put("f12", 301);
        KEY_MAP.put("a", 65);
        KEY_MAP.put("b", 66);
        KEY_MAP.put("c", 67);
        KEY_MAP.put("d", 68);
        KEY_MAP.put("e", 69);
        KEY_MAP.put("f", 70);
        KEY_MAP.put("g", 71);
        KEY_MAP.put("h", 72);
        KEY_MAP.put("i", 73);
        KEY_MAP.put("j", 74);
        KEY_MAP.put("k", 75);
        KEY_MAP.put("l", 76);
        KEY_MAP.put("m", 77);
        KEY_MAP.put("n", 78);
        KEY_MAP.put("o", 79);
        KEY_MAP.put("p", 80);
        KEY_MAP.put("q", 81);
        KEY_MAP.put("r", 82);
        KEY_MAP.put("s", 83);
        KEY_MAP.put("t", 84);
        KEY_MAP.put("u", 85);
        KEY_MAP.put("v", 86);
        KEY_MAP.put("w", 87);
        KEY_MAP.put("x", 88);
        KEY_MAP.put("y", 89);
        KEY_MAP.put("z", 90);
        KEY_MAP.put("0", 48);
        KEY_MAP.put("1", 49);
        KEY_MAP.put("2", 50);
        KEY_MAP.put("3", 51);
        KEY_MAP.put("4", 52);
        KEY_MAP.put("5", 53);
        KEY_MAP.put("6", 54);
        KEY_MAP.put("7", 55);
        KEY_MAP.put("8", 56);
        KEY_MAP.put("9", 57);
    }

    public static void simulateClick(int x, int y) {
        if (robot == null) return;
        try {
            Point windowPos = getMcWindowPos();
            robot.mouseMove(windowPos.x + x, windowPos.y + y);
            Thread.sleep(30);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            Thread.sleep(50);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            MinecraftMcpMod.LOGGER.debug("[MCP Input] Click at ({}, {})", x, y);
        } catch (Exception e) {
            MinecraftMcpMod.LOGGER.error("[MCP Input] Click error: {}", e.getMessage());
        }
    }

    public static void simulateRightClick(int x, int y) {
        if (robot == null) return;
        try {
            Point windowPos = getMcWindowPos();
            robot.mouseMove(windowPos.x + x, windowPos.y + y);
            Thread.sleep(30);
            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
            Thread.sleep(50);
            robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
        } catch (Exception e) {
            MinecraftMcpMod.LOGGER.error("[MCP Input] Right-click error: {}", e.getMessage());
        }
    }

    public static void simulateKeyPress(String keyName, long holdMs) {
        if (robot == null) return;
        try {
            Integer keyCode = resolveKeyCode(keyName);
            if (keyCode == null) {
                MinecraftMcpMod.LOGGER.warn("[MCP Input] Unknown key: {}", keyName);
                return;
            }
            robot.keyPress(keyCode);
            Thread.sleep(holdMs);
            robot.keyRelease(keyCode);
            MinecraftMcpMod.LOGGER.debug("[MCP Input] Key press: {}", keyName);
        } catch (Exception e) {
            MinecraftMcpMod.LOGGER.error("[MCP Input] Key press error: {}", e.getMessage());
        }
    }

    public static void simulateTypeText(String text, boolean pressEnter) {
        if (robot == null) return;
        try {
            for (char c : text.toCharArray()) {
                int code = Character.toUpperCase(c);
                boolean needsShift = Character.isUpperCase(c) || "!@#$%^&*()_+{}|:\"<>?~".indexOf(c) >= 0;
                if (needsShift) robot.keyPress(340);
                robot.keyPress(code);
                robot.keyRelease(code);
                if (needsShift) robot.keyRelease(340);
                Thread.sleep(30 + (long)(Math.random() * 20));
            }
            if (pressEnter) {
                robot.keyPress(257);
                robot.keyRelease(257);
            }
            MinecraftMcpMod.LOGGER.debug("[MCP Input] Typed: {}", text);
        } catch (Exception e) {
            MinecraftMcpMod.LOGGER.error("[MCP Input] Type error: {}", e.getMessage());
        }
    }

    public static void simulateScroll(int clicks) {
        if (robot == null) return;
        try {
            robot.mouseWheel(clicks);
            MinecraftMcpMod.LOGGER.debug("[MCP Input] Scroll: {}", clicks);
        } catch (Exception e) {
            MinecraftMcpMod.LOGGER.error("[MCP Input] Scroll error: {}", e.getMessage());
        }
    }

    public static void simulateHotkey(String[] keys) {
        if (robot == null) return;
        try {
            int[] codes = new int[keys.length];
            for (int i = 0; i < keys.length; i++) {
                Integer kc = resolveKeyCode(keys[i]);
                if (kc == null) {
                    MinecraftMcpMod.LOGGER.warn("[MCP Input] Unknown hotkey component: {}", keys[i]);
                    return;
                }
                codes[i] = kc;
            }

            for (int c : codes) robot.keyPress(c);
            Thread.sleep(80);
            for (int i = codes.length - 1; i >= 0; i--) robot.keyRelease(codes[i]);

            MinecraftMcpMod.LOGGER.debug("[MCP Input] Hotkey: {}", String.join("+", keys));
        } catch (Exception e) {
            MinecraftMcpMod.LOGGER.error("[MCP Input] Hotkey error: {}", e.getMessage());
        }
    }

    private static Integer resolveKeyCode(String name) {
        String lower = name.toLowerCase();
        if (KEY_MAP.containsKey(lower)) return KEY_MAP.get(lower);
        if (name.length() == 1) return (int) Character.toUpperCase(name.charAt(0));
        return null;
    }

    private static Point getMcWindowPos() {
        Minecraft mc = Minecraft.getInstance();
        var window = mc.getWindow();
        if (window != null && window.getWindow() != null) {
            return window.getWindow().getLocation();
        }
        return new Point(0, 0);
    }

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        MinecraftMcpMod.LOGGER.trace("[MCP Input] Mouse button {} pressed at ({}, {})",
                event.getButton(), event.getMouseX(), event.getMouseY());
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        MinecraftMcpMod.LOGGER.trace("[MCP Input] Key {} pressed", event.getKeyCode());
    }
}

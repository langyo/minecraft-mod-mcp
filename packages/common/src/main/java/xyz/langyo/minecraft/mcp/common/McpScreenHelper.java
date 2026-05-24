package xyz.langyo.minecraft.mcp.common;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class McpScreenHelper {

    public interface ButtonFactory {
        Object createButton(String translationKey, Runnable onClick, int x, int y, int w, int h);
    }

    public static void patchPauseScreen(Object screen, ButtonFactory factory) {
        try {
            System.out.println("[MCP] patchPauseScreen: screen=" + screen.getClass().getName());
            dumpAllFields(screen);
            Object originalQuit = findBottomWideButton(screen);
            System.out.println("[MCP] patchPauseScreen: originalQuit=" + (originalQuit != null ? originalQuit.getClass().getName() : "null"));
            if (originalQuit == null) return;

            int x = getIntField(originalQuit, "x", "xPosition", "field_146128_h", "f_146128_h_");
            int y = getIntField(originalQuit, "y", "yPosition", "field_146129_i", "f_146129_i_");
            int w = getIntField(originalQuit, "width", "f_96515_", "field_146120_f");
            int h = getIntField(originalQuit, "height", "f_96518_", "field_146121_g");
            if (w == 0) w = getIntField(originalQuit, "f_96515_");
            if (h == 0) h = getIntField(originalQuit, "f_96518_");
            System.out.println("[MCP] patchPauseScreen: quitButton bounds: x=" + x + " y=" + y + " w=" + w + " h=" + h);

            int gap = 8;
            int leftW = (w - gap) / 2;
            int rightW = w - gap - leftW;

            setIntField(originalQuit, x + leftW + gap, "x", "xPosition", "field_146128_h");
            setIntField(originalQuit, rightW, "width", "field_146120_f");

            String transferKey = ReflectionHelper.getMcpControlPauseTransferTranslationKey();
            Object transfer = factory.createButton(transferKey, () -> {
                try {
                    Object mc = ReflectionHelper.getMinecraftInstance();
                    ReflectionHelper.enterMcpControlMode(mc);
                    ReflectionHelper.closeScreen(mc);
                } catch (Exception ignored) {}
            }, x, y, leftW, h);
            System.out.println("[MCP] patchPauseScreen: transfer button created: " + transfer.getClass().getName());

            addRenderableWidget(screen, transfer);
            System.out.println("[MCP] patchPauseScreen: button added successfully");
        } catch (Exception e) {
            System.err.println("[MCP] patchPauseScreen ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void dumpAllFields(Object screen) {
        try {
            for (Field f : getAllFields(screen.getClass())) {
                f.setAccessible(true);
                try {
                    Object val = f.get(screen);
                    String valStr;
                    if (val instanceof List) {
                        List<?> list = (List<?>) val;
                        StringBuilder sb = new StringBuilder("[");
                        for (int i = 0; i < Math.min(list.size(), 8); i++) {
                            if (i > 0) sb.append(", ");
                            Object e = list.get(i);
                            sb.append(e != null ? e.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(e)) : "null");
                        }
                        if (list.size() > 8) sb.append(" ... (").append(list.size()).append(" total)");
                        sb.append("]");
                        valStr = sb.toString();
                    } else {
                        valStr = val != null ? val.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(val)) : "null";
                    }
                    System.out.println("[MCP]   field " + f.getName() + " (" + f.getType().getSimpleName() + ") = " + valStr);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private static Object findBottomWideButton(Object screen) throws Exception {
        Object best = null;
        int bestY = -1;
        for (Field f : getAllFields(screen.getClass())) {
            f.setAccessible(true);
            Object val = f.get(screen);
            Object found = pickBottomWide(val, best, bestY);
            if (found != null && found != best) {
                best = found;
                bestY = getIntField(best, "y", "yPosition", "field_146129_i", "f_146129_i_");
                System.out.println("[MCP] findBottomWide: new best from field '" + f.getName() + "' -> y=" + bestY + " class=" + best.getClass().getName());
            }
        }
        for (Field f : getAllFields(screen.getClass())) {
            f.setAccessible(true);
            Object val = f.get(screen);
            if (val instanceof List<?>) {
                List<?> list = (List<?>) val;
                for (int i = 0; i < list.size(); i++) {
                    Object entry = list.get(i);
                    if (i < 3) {
                        Class<?> ec = entry.getClass();
                        StringBuilder hierarchy = new StringBuilder(ec.getName());
                        Class<?> sc = ec.getSuperclass();
                        while (sc != null && sc != Object.class) {
                            hierarchy.append(" extends ").append(sc.getName());
                            sc = sc.getSuperclass();
                        }
                        System.out.println("[MCP] list '" + f.getName() + "'[" + i + "] hierarchy: " + hierarchy);
                    }
                    Object found = pickBottomWide(entry, best, bestY);
                    if (found != null && found != best) {
                        best = found;
                        bestY = getIntField(best, "y", "yPosition", "field_146129_i", "f_146129_i_");
                        System.out.println("[MCP] findBottomWide: new best from list '" + f.getName() + "'[" + i + "] -> y=" + bestY + " class=" + best.getClass().getName());
                    }
                }
            }
        }
        System.out.println("[MCP] findBottomWide: final best=" + (best != null ? best.getClass().getName() : "null"));
        return best;
    }

    private static Object pickBottomWide(Object obj, Object currentBest, int currentBestY) {
        try {
            if (obj == null) return currentBest;
            if (!isButtonLike(obj)) return currentBest;
            int w = getIntField(obj, "width", "f_96515_", "field_146120_f");
            int y = getIntField(obj, "y", "yPosition", "field_146129_i", "f_146129_i_");
            System.out.println("[MCP] pickBottomWide: candidate " + obj.getClass().getName() + " w=" + w + " y=" + y + " currentBestY=" + currentBestY);
            if (w < 150) return currentBest;
            if (y > currentBestY) return obj;
            return currentBest;
        } catch (Exception e) { return currentBest; }
    }

    private static boolean isButtonLike(Object obj) {
        Class<?> c = obj.getClass();
        while (c != null && c != Object.class) {
            String name = c.getName();
            String simple = c.getSimpleName();
            if (name.contains("Button") || simple.contains("Button") ||
                name.contains("AbstractWidget") || simple.contains("Widget") ||
                name.contains("AbstractButton") || simple.contains("AbstractButton")) {
                return true;
            }
            for (Class<?> iface : c.getInterfaces()) {
                String iname = iface.getName();
                if (iname.contains("Button") || iname.contains("Widget") || iname.contains("Clickable")) {
                    return true;
                }
            }
            c = c.getSuperclass();
        }
        return false;
    }

    private static void addRenderableWidget(Object screen, Object widget) throws Exception {
        for (Class<?> c = screen.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                String name = m.getName();
                if (!name.equals("addRenderableWidget") && !name.equals("addButton") && !name.equals("func_212284_a")) continue;
                if (m.getParameterCount() != 1) continue;
                System.out.println("[MCP] addRenderableWidget: found method " + name + " param=" + m.getParameterTypes()[0].getName() + " widget=" + widget.getClass().getName() + " assignable=" + m.getParameterTypes()[0].isAssignableFrom(widget.getClass()));
                if (!m.getParameterTypes()[0].isAssignableFrom(widget.getClass())) continue;
                m.setAccessible(true);
                m.invoke(screen, widget);
                System.out.println("[MCP] addRenderableWidget: invoked " + name + " successfully");
                return;
            }
        }
        System.out.println("[MCP] addRenderableWidget: no method found, trying named lists");
        addToNamedList(screen, "buttons", widget);
        addToNamedList(screen, "buttonList", widget);
        addToNamedList(screen, "field_146292_n", widget);
        addToNamedList(screen, "field_195124_j", widget);
        addToNamedList(screen, "renderables", widget);
        addToNamedList(screen, "children", widget);
        addToNamedList(screen, "narratables", widget);
    }

    private static void addToNamedList(Object screen, String fieldName, Object widget) throws Exception {
        for (Field f : getAllFields(screen.getClass())) {
            if (!f.getName().equals(fieldName)) continue;
            f.setAccessible(true);
            Object val = f.get(screen);
            if (val instanceof List<?>) {
                @SuppressWarnings("unchecked")
                List<Object> mutable = (List<Object>) val;
                if (!mutable.contains(widget)) mutable.add(widget);
                return;
            }
        }
    }

    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) fields.add(f);
            c = c.getSuperclass();
        }
        return fields;
    }

    private static int getIntField(Object obj, String... names) throws Exception {
        for (String name : names) {
            try {
                for (Field f : getAllFields(obj.getClass())) {
                    if (f.getName().equals(name)) {
                        f.setAccessible(true);
                        return f.getInt(obj);
                    }
                }
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private static void setIntField(Object obj, int value, String... names) throws Exception {
        for (String name : names) {
            for (Field f : getAllFields(obj.getClass())) {
                if (f.getName().equals(name)) {
                    f.setAccessible(true);
                    f.setInt(obj, value);
                    return;
                }
            }
        }
    }
}

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
            Object originalQuit = findBottomWideButton(screen);
            if (originalQuit == null) return;

            int x = getIntField(originalQuit, "x", "xPosition", "field_146128_h", "f_146128_h_");
            int y = getIntField(originalQuit, "y", "yPosition", "field_146129_i", "f_146129_i_");
            int w = getIntField(originalQuit, "width", "f_96515_", "field_146120_f");
            int h = getIntField(originalQuit, "height", "f_96518_", "field_146121_g");
            if (w == 0) w = getIntField(originalQuit, "f_96515_");
            if (h == 0) h = getIntField(originalQuit, "f_96518_");

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

            addRenderableWidget(screen, transfer);
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
            }
        }
        for (Field f : getAllFields(screen.getClass())) {
            f.setAccessible(true);
            Object val = f.get(screen);
            if (val instanceof List<?>) {
                List<?> list = (List<?>) val;
                for (Object entry : list) {
                    Object found = pickBottomWide(entry, best, bestY);
                    if (found != null && found != best) {
                        best = found;
                        bestY = getIntField(best, "y", "yPosition", "field_146129_i", "f_146129_i_");
                    }
                }
            }
        }
        return best;
    }

    private static Object pickBottomWide(Object obj, Object currentBest, int currentBestY) {
        try {
            if (obj == null) return currentBest;
            String cn = obj.getClass().getName();
            if (!cn.contains("Button") && !cn.contains("AbstractButton")) return currentBest;
            int w = getIntField(obj, "width", "f_96515_", "field_146120_f");
            if (w < 150) return currentBest;
            int y = getIntField(obj, "y", "yPosition", "field_146129_i", "f_146129_i_");
            if (y > currentBestY) return obj;
            return currentBest;
        } catch (Exception e) { return currentBest; }
    }

    private static void addRenderableWidget(Object screen, Object widget) throws Exception {
        for (Class<?> c = screen.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals("addRenderableWidget") || m.getParameterCount() != 1) continue;
                if (!m.getParameterTypes()[0].isAssignableFrom(widget.getClass())) continue;
                m.setAccessible(true);
                m.invoke(screen, widget);
                return;
            }
        }
        addToNamedList(screen, "buttonList", widget);
        addToNamedList(screen, "field_146292_n", widget);
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

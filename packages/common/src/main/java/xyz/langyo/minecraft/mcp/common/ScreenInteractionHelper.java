package xyz.langyo.minecraft.mcp.common;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ScreenInteractionHelper {

    private ScreenInteractionHelper() {}

    private static final Map<Class<?>, List<Method>> methodCache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, List<Field>> fieldCache = new ConcurrentHashMap<>();

    private static String invokeWidgetCallback(Object w, double gx, double gy, int button, int[] bounds) {
        String wName = w.getClass().getSimpleName();
        try {
            java.util.List<Field> allFields = ReflectionCache.getAllFields(w.getClass());
            int fieldCount = 0;
            for (Field bf : allFields) {
                bf.setAccessible(true);
                Object cb;
                try { cb = bf.get(w); } catch (Exception e) { continue; }
                if (cb == null) continue;
                fieldCount++;
                Class<?> cbClass = cb.getClass();
                Class<?> declaredType = bf.getType();
                if (fieldCount <= 10) ReflectionHelper.dbg("guiClick: widget field #" + fieldCount + " name=" + bf.getName() + " declared=" + declaredType.getSimpleName() + " actual=" + cbClass.getSimpleName());
                java.util.List<Method> abstractMethods = new java.util.ArrayList<>();
                for (Method m : declaredType.getMethods()) {
                    if (m.getDeclaringClass() == Object.class) continue;
                    if (!java.lang.reflect.Modifier.isAbstract(m.getModifiers())) continue;
                    abstractMethods.add(m);
                }
                if (abstractMethods.isEmpty()) {
                    for (Method m : cbClass.getMethods()) {
                        if (m.getDeclaringClass() == Object.class) continue;
                        if (!java.lang.reflect.Modifier.isAbstract(m.getModifiers())) continue;
                        abstractMethods.add(m);
                    }
                }
                if (fieldCount <= 10) ReflectionHelper.dbg("guiClick: field " + bf.getName() + " has " + abstractMethods.size() + " abstract methods (declaredType)");
                if (abstractMethods.size() != 1) continue;
                Method sam = abstractMethods.get(0);
                ReflectionHelper.dbg("guiClick: SAM field=" + bf.getName() + " type=" + cbClass.getSimpleName() + " method=" + sam.getName() + "(" + java.util.Arrays.toString(sam.getParameterTypes()) + ")");
                try {
                    sam.setAccessible(true);
                    Class<?>[] spt = sam.getParameterTypes();
                    if (spt.length == 0) {
                        sam.invoke(cb);
                        ReflectionHelper.dbg("guiClick: callback " + bf.getName() + "." + sam.getName() + "() on " + wName);
                        return ",\"widget\":\"" + wName + "\",\"callback\":\"" + bf.getName() + "." + sam.getName() + "()\"";
                    } else if (spt.length == 1) {
                        Object arg = spt[0].isInstance(w) ? w : null;
                        sam.invoke(cb, arg);
                        ReflectionHelper.dbg("guiClick: callback " + bf.getName() + "." + sam.getName() + "(widget) on " + wName);
                        return ",\"widget\":\"" + wName + "\",\"callback\":\"" + bf.getName() + "." + sam.getName() + "(widget)\"";
                    } else {
                        ReflectionHelper.dbg("guiClick: SAM has " + spt.length + " params, skipping");
                    }
                } catch (Exception e) {
                    ReflectionHelper.dbg("guiClick: callback " + bf.getName() + " failed: " + e.getMessage());
                }
            }
            ReflectionHelper.dbg("guiClick: no callback found on " + wName);
            return ",\"widget\":\"" + wName + "\",\"error\":\"no callback\"";
        } catch (Exception e) {
            return ",\"widget\":\"" + wName + "\",\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String guiClick(Object mc, int x, int y, int button) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen == null) return "{\"error\":\"no screen\"}";
            String screenName = screen.getClass().getSimpleName();
            ReflectionHelper.dbg("guiClick: gui(" + x + "," + y + ") screen=" + screenName);
            double gx = (double) x;
            double gy = (double) y;
            int winW = WindowHelper.getDisplayWidth(mc);
            int winH = WindowHelper.getDisplayHeight(mc);
            if (winW <= 0) { try { winW = (int) Class.forName("org.lwjgl.glfw.GLFW").getMethod("glfwGetWindowSize", long.class, java.nio.IntBuffer.class, java.nio.IntBuffer.class).invoke(null, 0L, java.nio.IntBuffer.allocate(1), java.nio.IntBuffer.allocate(1)); } catch (Exception ignored) {} }
            if (winW <= 0) winW = 854;
            if (winH <= 0) winH = 480;
            int maxField1 = 0, maxField2 = 0;
            for (Field sf : ReflectionCache.getAllFields(screen.getClass())) {
                if (sf.getType() != int.class) continue;
                try { sf.setAccessible(true); int sv = sf.getInt(screen);
                    if (sv > 0 && sv < 10000) {
                        if (sv > maxField1) { maxField2 = maxField1; maxField1 = sv; }
                        else if (sv > maxField2) { maxField2 = sv; }
                    }
                } catch (Exception ignored) {}
            }
            ReflectionHelper.dbg("guiClick: win=" + winW + "x" + winH + " screenMax=" + maxField1 + "," + maxField2);
            if (maxField1 > 0 && maxField2 > 0 && maxField1 != winW && maxField2 != winH) {
                int guiW = Math.max(maxField1, maxField2);
                int guiH = Math.min(maxField1, maxField2);
                double scaleX = (double) guiW / winW;
                double scaleY = (double) guiH / winH;
                gx = x * scaleX;
                gy = y * scaleY;
                ReflectionHelper.dbg("guiClick: scaled click(" + x + "," + y + ")→gui(" + (int)gx + "," + (int)gy + ") scale=" + scaleX);
            }
            if (screenName.equals("class_526")) {
                try {
                    Object worldListWidget = null;
                    for (Field sf : ReflectionCache.getAllFields(screen.getClass())) {
                        if (sf.getName().equals("field_3218")) {
                            sf.setAccessible(true);
                            try { worldListWidget = sf.get(screen); } catch (Exception ignored) {}
                            break;
                        }
                    }
                    if (worldListWidget == null) {
                        for (Field sf : ReflectionCache.getAllFields(screen.getClass())) {
                            sf.setAccessible(true);
                            try { Object v = sf.get(screen); if (v != null && v.getClass().getName().equals("net.minecraft.class_528")) { worldListWidget = v; break; } } catch (Exception ignored) {}
                        }
                    }
                    if (worldListWidget != null) {
                        ReflectionHelper.dbg("guiClick: found levelList=" + worldListWidget.getClass().getName());
                        java.util.List<?> entries = null;
                        for (Field ef : ReflectionCache.getAllFields(worldListWidget.getClass())) {
                            ef.setAccessible(true);
                            try { Object v = ef.get(worldListWidget); if (v instanceof java.util.List && !((java.util.List<?>)v).isEmpty()) { entries = (java.util.List<?>)v; ReflectionHelper.dbg("guiClick: entries field=" + ef.getName() + " count=" + entries.size()); break; } } catch (Exception ignored) {}
                        }
                        if (entries != null && !entries.isEmpty()) {
                            Object firstEntry = entries.get(0);
                            ReflectionHelper.dbg("guiClick: firstEntry type=" + firstEntry.getClass().getName());
                            Object levelSummary = null;
                            if (firstEntry.getClass().getName().equals("net.minecraft.class_34")) {
                                levelSummary = firstEntry;
                                ReflectionHelper.dbg("guiClick: entry IS LevelSummary");
                            } else {
                                for (Method gm : firstEntry.getClass().getMethods()) {
                                    if (gm.getParameterCount() == 0 && !java.lang.reflect.Modifier.isStatic(gm.getModifiers())) {
                                        try { gm.setAccessible(true); Object r = gm.invoke(firstEntry); if (r != null && r.getClass().getName().equals("net.minecraft.class_34")) { levelSummary = r; ReflectionHelper.dbg("guiClick: got levelSummary via " + gm.getName()); break; } } catch (Exception ignored) {}
                                    }
                                }
                                if (levelSummary == null) {
                                    for (Field ef2 : ReflectionCache.getAllFields(firstEntry.getClass())) {
                                        ef2.setAccessible(true);
                                        try { Object v = ef2.get(firstEntry); if (v != null && v.getClass().getName().equals("net.minecraft.class_34")) { levelSummary = v; ReflectionHelper.dbg("guiClick: got levelSummary from field " + ef2.getName()); break; } } catch (Exception ignored) {}
                                    }
                                }
                            }
                            if (levelSummary != null) {
                                for (Method wm : screen.getClass().getDeclaredMethods()) {
                                    if (wm.getName().equals("method_19940") || (wm.getParameterCount() == 1 && wm.getParameterTypes()[0].getName().equals("net.minecraft.class_34") && wm.getReturnType() == void.class)) {
                                        try { wm.setAccessible(true); wm.invoke(screen, levelSummary); ReflectionHelper.dbg("guiClick: called worldSelected via " + wm.getName()); } catch (Exception e) { ReflectionHelper.dbg("guiClick: worldSelected failed: " + e.getMessage()); }
                                        break;
                                    }
                                }
                                try {
                                    Class<?> loaderClass = Class.forName("net.minecraft.class_7196");
                                    Object levelStorage = null;
                                    for (Method lsm : mc.getClass().getMethods()) {
                                        if (lsm.getParameterCount() == 0 && lsm.getReturnType().getName().equals("net.minecraft.class_32") && !java.lang.reflect.Modifier.isStatic(lsm.getModifiers())) {
                                            try { lsm.setAccessible(true); levelStorage = lsm.invoke(mc); break; } catch (Exception ignored) {}
                                        }
                                    }
                                    if (levelStorage != null) {
                                        Object loader = loaderClass.getConstructor(Class.forName("net.minecraft.class_310"), Class.forName("net.minecraft.class_32")).newInstance(mc, levelStorage);
                                        String levelName = null;
                                        for (Method gm : levelSummary.getClass().getMethods()) {
                                            if (gm.getParameterCount() == 0 && gm.getReturnType() == String.class) {
                                                try { gm.setAccessible(true); String n = (String) gm.invoke(levelSummary); if (n != null && !n.isEmpty()) { levelName = n; break; } } catch (Exception ignored) {}
                                            }
                                        }
                                        if (levelName == null) {
                                            for (Field nf : ReflectionCache.getAllFields(levelSummary.getClass())) {
                                                if (nf.getType() == String.class && !java.lang.reflect.Modifier.isStatic(nf.getModifiers())) {
                                                    try { nf.setAccessible(true); String v = (String) nf.get(levelSummary); if (v != null && !v.isEmpty()) { levelName = v; break; } } catch (Exception ignored) {}
                                                }
                                            }
                                        }
                                        if (levelName != null) {
                                            ReflectionHelper.dbg("guiClick: loading world '" + levelName + "' via IntegratedServerLoader");
                                            for (Method sm : loaderClass.getDeclaredMethods()) {
                                                if (sm.getParameterCount() == 2 && sm.getParameterTypes()[0] == String.class && java.lang.Runnable.class.isAssignableFrom(sm.getParameterTypes()[1]) && !java.lang.reflect.Modifier.isStatic(sm.getModifiers())) {
                                                    try { sm.setAccessible(true); sm.invoke(loader, levelName, (Runnable) () -> {}); ReflectionHelper.dbg("guiClick: world load started"); return "{\"clicked\":true,\"screen\":\"class_526\",\"gui\":[" + (int)gx + "," + (int)gy + "],\"worldLoaded\":true}"; } catch (Exception e) { ReflectionHelper.dbg("guiClick: load failed: " + e.getMessage()); }
                                                    break;
                                                }
                                            }
                                        } else { ReflectionHelper.dbg("guiClick: could not determine world name"); }
                                    }
                                } catch (Exception e) { ReflectionHelper.dbg("guiClick: loader creation failed: " + e.getMessage()); }
                            } else {
                                ReflectionHelper.dbg("guiClick: no levelSummary from entry, trying worldSelected with null");
                                for (Method wm : screen.getClass().getDeclaredMethods()) {
                                    if (wm.getName().equals("method_19940")) { try { wm.setAccessible(true); wm.invoke(screen, (Object)null); } catch (Exception ignored) {} break; }
                                }
                            }
                        }
                    } else {
                        ReflectionHelper.dbg("guiClick: levelList field not found on " + screen.getClass().getName());
                    }
                } catch (Exception e) { ReflectionHelper.dbg("guiClick: world select error: " + e.getMessage()); }
            }
            StringBuilder results = new StringBuilder();
            java.util.List<Method> clickedMethods = new java.util.ArrayList<>();
            Method isMouseOverMethod = null;
            for (Method m : screen.getClass().getMethods()) {
                Class<?>[] pt = m.getParameterTypes();
                if (!(m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) continue;
                if (m.getParameterCount() == 2 && pt[0] == double.class && pt[1] == double.class) {
                    if (isMouseOverMethod == null) isMouseOverMethod = m;
                    continue;
                }
                if (m.getParameterCount() == 3 && pt[0] == double.class && pt[1] == double.class && pt[2] == int.class) {
                    clickedMethods.add(m);
                } else if (m.getParameterCount() == 3 && pt[0] == int.class && pt[1] == int.class && pt[2] == int.class) {
                    clickedMethods.add(m);
                } else if (m.getParameterCount() == 3 && !pt[0].isPrimitive() && pt[1] == double.class && pt[2] == double.class) {
                    clickedMethods.add(m);
                } else if (m.getParameterCount() == 4 && !pt[0].isPrimitive() && pt[1] == double.class && pt[2] == double.class && pt[3] == int.class) {
                    clickedMethods.add(m);
                }
            }
            for (Method m : clickedMethods) {
                Class<?>[] pt = m.getParameterTypes();
                try {
                    m.setAccessible(true);
                    Object result = null;
                    if (m.getParameterCount() == 3 && pt[0] == double.class && pt[1] == double.class && pt[2] == int.class) {
                        result = m.invoke(screen, gx, gy, button);
                    } else if (m.getParameterCount() == 3 && pt[0] == int.class && pt[1] == int.class && pt[2] == int.class) {
                        result = m.invoke(screen, (int)gx, (int)gy, button);
                    } else if (m.getParameterCount() == 3 && !pt[0].isPrimitive() && pt[1] == double.class && pt[2] == double.class) {
                        Object clickType = createClickType(pt[0], button);
                        result = m.invoke(screen, clickType, gx, gy);
                    } else if (m.getParameterCount() == 4 && !pt[0].isPrimitive() && pt[1] == double.class && pt[2] == double.class && pt[3] == int.class) {
                        Object clickType = createClickType(pt[0], button);
                        result = m.invoke(screen, clickType, gx, gy, button);
                    } else { continue; }
                    ReflectionHelper.dbg("guiClick: " + m.getName() + java.util.Arrays.toString(pt) + "=" + result + " from " + m.getDeclaringClass().getSimpleName());
                    if (results.length() > 0) results.append(",");
                    results.append("\"").append(m.getName()).append("\":").append(result);
                    if (Boolean.TRUE.equals(result)) break;
                } catch (Exception e) {
                    ReflectionHelper.dbg("guiClick: " + m.getName() + " failed: " + e.getMessage());
                }
            }
            if (isMouseOverMethod != null) {
                try {
                    isMouseOverMethod.setAccessible(true);
                    Object result = isMouseOverMethod.invoke(screen, gx, gy);
                    ReflectionHelper.dbg("guiClick: isMouseOver=" + result);
                } catch (Exception ignored) {}
            }
            String widgetResult = "";
            int[] widgetBounds = null;
            Object hitWidget = null;
            ReflectionHelper.dbg("guiClick: starting children scan (primary)");
            try {
                for (Method cm : screen.getClass().getMethods()) {
                    if (cm.getParameterCount() != 0) continue;
                    if (!java.util.List.class.isAssignableFrom(cm.getReturnType())) continue;
                    if (java.lang.reflect.Modifier.isStatic(cm.getModifiers())) continue;
                    cm.setAccessible(true);
                    Object childrenObj;
                    try { childrenObj = cm.invoke(screen); } catch (Exception e) { continue; }
                    if (!(childrenObj instanceof java.util.List)) continue;
                    java.util.List<?> children = (java.util.List<?>) childrenObj;
                    ReflectionHelper.dbg("guiClick: children method=" + cm.getName() + " size=" + children.size());
                    for (Object child : children) {
                        if (child == null) continue;
                        Class<?> cc = child.getClass();
                        int cx = -1, cy = -1, cw = -1, ch = -1;
                        int cright = -1, cbottom = -1;
                        for (Method gm : cc.getMethods()) {
                            if (gm.getParameterCount() != 0 || gm.getReturnType() != int.class) continue;
                            String gmn = gm.getName();
                            try {
                                gm.setAccessible(true);
                                int val = ((Number) gm.invoke(child)).intValue();
                                if (gmn.contains("46426")) cx = val;
                                else if (gmn.contains("46427")) cy = val;
                                else if (gmn.contains("25368")) { if (cright < 0) cright = val; }
                                else if (gmn.contains("25364")) { if (cbottom < 0) cbottom = val; }
                                else if (gmn.contains("55442")) { if (cright < 0) cright = val; }
                                else if (gmn.contains("55443")) { if (cbottom < 0) cbottom = val; }
                                else if (gmn.contains("25402")) { if (cx < 0) cx = val; }
                                else if (gmn.contains("25403")) { if (cy < 0) cy = val; }
                                else if (gmn.contains("25404")) { if (cw < 0) cw = val; }
                                else if (gmn.contains("25405")) { if (ch < 0) ch = val; }
                                else if (gmn.contains("48590")) { if (ch < 0) ch = val; }
                            } catch (Exception ignored) {}
                        }
                        if (cright > 0 && cbottom > 0) {
                            if (cright > cx && cright < 1000 && cbottom < cy) {
                                cw = cright;
                                ch = cbottom;
                            } else if (cright > cx) {
                                cw = cright - cx;
                            }
                            if (cbottom > cy) {
                                ch = cbottom - cy;
                            } else if (cbottom > 0 && cbottom < 100) {
                                ch = cbottom;
                            }
                        }
                        if (cx >= 0 && cy >= 0 && cw > 0 && ch > 0) {
                            ReflectionHelper.dbg("guiClick: child " + cc.getSimpleName() + " bounds=(" + cx + "," + cy + ")+" + cw + "x" + ch + " r=" + cright + " b=" + cbottom);
                        } else {
                            ReflectionHelper.dbg("guiClick: child " + cc.getSimpleName() + " cx=" + cx + " cy=" + cy + " cw=" + cw + " ch=" + ch + " right=" + cright + " bottom=" + cbottom);
                        }
                        if (cx >= 0 && cy >= 0 && cw > 0 && ch > 0 && ch <= 40) {
                            if (gx >= cx && gx < cx + cw && gy >= cy && gy < cy + ch) {
                                hitWidget = child;
                                widgetBounds = new int[]{cx, cy, cw, ch};
                                ReflectionHelper.dbg("guiClick: hit child widget " + cc.getSimpleName());
                                break;
                            }
                        }
                    }
                    if (hitWidget != null) break;
                }
                if (hitWidget != null) widgetResult = invokeWidgetCallback(hitWidget, gx, gy, button, widgetBounds);
            } catch (Exception e) {
                ReflectionHelper.dbg("guiClick: children scan failed: " + e.getMessage());
            }
            if (hitWidget == null) {
                ReflectionHelper.dbg("guiClick: children scan missed, trying field scan");
                for (Field sf : ReflectionCache.getAllFields(screen.getClass())) {
                    if (java.lang.reflect.Modifier.isStatic(sf.getModifiers())) continue;
                    try {
                        sf.setAccessible(true);
                        Object widget = sf.get(screen);
                        if (widget == null || widget == screen) continue;
                        Class<?> wc = widget.getClass();
                        boolean isWidget = false;
                        for (Class<?> sup = wc; sup != null && sup != Object.class; sup = sup.getSuperclass()) {
                            if (sup.getSimpleName().contains("ClickableWidget") || sup.getSimpleName().contains("AbstractButton")
                                || sup.getName().contains("class_4185") || sup.getName().contains("class_5676")) {
                                isWidget = true; break;
                            }
                        }
                        if (!isWidget && !(widget instanceof java.util.List)) {
                            boolean hasXY = false;
                            for (Method wm : wc.getMethods()) {
                                if ((wm.getName().equals("getX") || wm.getName().equals("getY") || wm.getName().equals("method_25402"))
                                    && wm.getParameterCount() == 0 && wm.getReturnType() == int.class) { hasXY = true; break; }
                            }
                            if (!hasXY) continue;
                            isWidget = true;
                        }
                        if (!isWidget) continue;
                        int wx = -1, wy = -1, ww = -1, wh = -1;
                        int wright = -1, wbottom = -1;
                        for (Method wm : wc.getMethods()) {
                            if (wm.getParameterCount() != 0 || wm.getReturnType() != int.class) continue;
                            String mn = wm.getName();
                            try {
                                wm.setAccessible(true);
                                int val = ((Number) wm.invoke(widget)).intValue();
                                if (mn.equals("getX") || mn.contains("25402") || mn.contains("46426")) wx = val;
                                else if (mn.equals("getY") || mn.contains("25403") || mn.contains("46427")) wy = val;
                                else if (mn.equals("getWidth") || mn.contains("25404") || mn.contains("55442")) ww = val;
                                else if (mn.equals("getHeight") || mn.contains("25405") || mn.contains("48590")) wh = val;
                                else if (mn.contains("25368")) wright = val;
                                else if (mn.contains("25364")) wbottom = val;
                            } catch (Exception ignored) {}
                        }
                        if (wx >= 0 && wright > wx) ww = wright - wx;
                        if (wy >= 0 && wbottom > wy) wh = wbottom - wy;
                        if (wx < 0 || wy < 0 || ww <= 0 || wh <= 0) {
                            java.util.List<Field> wIntFields = new java.util.ArrayList<>();
                            for (Field wf : ReflectionCache.getAllFields(wc)) {
                                if (wf.getType() == int.class && !java.lang.reflect.Modifier.isStatic(wf.getModifiers())) wIntFields.add(wf);
                            }
                            int[] ivals = new int[wIntFields.size()];
                            for (int i = 0; i < wIntFields.size(); i++) {
                                try { wIntFields.get(i).setAccessible(true); ivals[i] = wIntFields.get(i).getInt(widget); } catch (Exception e) { ivals[i] = -1; }
                            }
                            if (ivals.length >= 4) { wx = ivals[0]; wy = ivals[1]; ww = ivals[2]; wh = ivals[3]; }
                        }
                        if (wx >= 0 && wy >= 0 && ww > 0 && wh > 0 && wh <= 40) {
                            ReflectionHelper.dbg("guiClick: field " + sf.getName() + "=" + wc.getSimpleName() + " bounds=(" + wx + "," + wy + ")+" + ww + "x" + wh);
                            if (gx >= wx && gx < wx + ww && gy >= wy && gy < wy + wh) {
                                hitWidget = widget;
                                widgetBounds = new int[]{wx, wy, ww, wh};
                                ReflectionHelper.dbg("guiClick: hit field widget " + sf.getName());
                                break;
                            }
                        }
                    } catch (Exception ignored) {}
                }
                if (hitWidget != null) {
                    widgetResult = invokeWidgetCallback(hitWidget, gx, gy, button, widgetBounds);
                    if (!widgetResult.contains("\"callback\"")) {
                        hitWidget = null;
                        widgetResult = "";
                    }
                }
            }
            if (hitWidget == null) {
                ReflectionHelper.dbg("guiClick: children+field scan missed, trying list scan");
                int listFieldCount = 0;
                for (Field f : ReflectionCache.getAllFields(screen.getClass())) {
                    f.setAccessible(true);
                    Object list;
                    try { list = f.get(screen); } catch (Exception e) { continue; }
                    if (!(list instanceof java.util.List)) continue;
                    java.util.List<?> wList = (java.util.List<?>) list;
                    listFieldCount++;
                    if (wList.isEmpty()) continue;
                    boolean hasIntFields = false;
                    for (Field wf : wList.get(0).getClass().getFields()) {
                        if (wf.getType() == int.class) { hasIntFields = true; break; }
                    }
                    if (!hasIntFields) {
                        for (Field wf : ReflectionCache.getAllFields(wList.get(0).getClass())) {
                            if (wf.getType() == int.class) { hasIntFields = true; break; }
                        }
                    }
                    if (!hasIntFields) continue;
                    java.util.List<Field> wFields = ReflectionCache.getAllFields(wList.get(0).getClass());
                    java.util.List<Integer> intFieldIndices = new java.util.ArrayList<>();
                    for (int i = 0; i < wFields.size(); i++) {
                        if (wFields.get(i).getType() == int.class) intFieldIndices.add(i);
                    }
                    if (intFieldIndices.size() < 4) continue;
                    int nWidgets = wList.size();
                    int[] allVals = new int[intFieldIndices.size()];
                    try { for (int i = 0; i < intFieldIndices.size(); i++) { wFields.get(intFieldIndices.get(i)).setAccessible(true); allVals[i] = wFields.get(intFieldIndices.get(i)).getInt(wList.get(0)); } } catch (Exception ignored) { continue; }
                    java.util.Set<Integer> constantFieldSet = new java.util.LinkedHashSet<>();
                    java.util.Set<Integer> varyingFieldSet = new java.util.LinkedHashSet<>();
                    for (int fi = 0; fi < intFieldIndices.size(); fi++) {
                        boolean constant = true;
                        int val0 = allVals[fi];
                        for (int wi = 1; wi < nWidgets; wi++) {
                            try {
                                int v = wFields.get(intFieldIndices.get(fi)).getInt(wList.get(wi));
                                if (v != val0) { constant = false; break; }
                            } catch (Exception e) { constant = false; break; }
                        }
                        if (constant) constantFieldSet.add(fi); else varyingFieldSet.add(fi);
                    }
                    int heightIdx = -1, widthIdx = -1;
                    for (int fi : constantFieldSet) {
                        if (allVals[fi] == 20 && heightIdx < 0) heightIdx = fi;
                    }
                    for (int fi : constantFieldSet) {
                        if (fi == heightIdx) continue;
                        if (allVals[fi] == 200 && widthIdx < 0) widthIdx = fi;
                    }
                    if (widthIdx < 0) { for (int fi : constantFieldSet) { if (fi != heightIdx && allVals[fi] == 98 && widthIdx < 0) widthIdx = fi; } }
                    if (widthIdx < 0) { for (int fi : constantFieldSet) { if (fi != heightIdx && allVals[fi] > 50 && allVals[fi] < 500 && widthIdx < 0) widthIdx = fi; } }
                    java.util.List<Integer> xyIndices = new java.util.ArrayList<>();
                    int perWidgetWidthIdx = -1;
                    for (int fi : varyingFieldSet) {
                        boolean allStandardWidths = true;
                        for (int wi = 0; wi < nWidgets; wi++) {
                            try {
                                int v = wFields.get(intFieldIndices.get(fi)).getInt(wList.get(wi));
                                if (v != 200 && v != 150 && v != 98 && v != 50) { allStandardWidths = false; break; }
                            } catch (Exception e) { allStandardWidths = false; break; }
                        }
                    if (allStandardWidths && perWidgetWidthIdx < 0) { perWidgetWidthIdx = fi; ReflectionHelper.dbg("guiClick: perWidgetWidth fi=" + fi + " vals=" + allVals[fi]); }
                    else { xyIndices.add(fi); }
                    }
                    if (heightIdx < 0 || xyIndices.size() < 2) {
                        xyIndices.clear();
                        for (int fi = 0; fi < intFieldIndices.size(); fi++) { if (fi != heightIdx && fi != widthIdx && fi != perWidgetWidthIdx) xyIndices.add(fi); }
                    }
                    ReflectionHelper.dbg("guiClick: list #" + listFieldCount + " size=" + nWidgets + " elem=" + wList.get(0).getClass().getSimpleName() + " const=" + constantFieldSet + " var=" + varyingFieldSet + " hIdx=" + heightIdx + " wIdx=" + widthIdx + " pww=" + perWidgetWidthIdx + " xy=" + xyIndices);
                    for (Object w : wList) {
                        try {
                            int[] fVals = new int[intFieldIndices.size()];
                            for (int i = 0; i < intFieldIndices.size(); i++) { try { wFields.get(intFieldIndices.get(i)).setAccessible(true); fVals[i] = wFields.get(intFieldIndices.get(i)).getInt(w); } catch (Exception e) { fVals[i] = -1; } }
                            if (heightIdx < 0) continue;
                            int h = fVals[heightIdx];
                            if (hitWidget != null) continue;
                            int pww = perWidgetWidthIdx >= 0 ? fVals[perWidgetWidthIdx] : -1;
                            for (int xi = 0; xi < xyIndices.size() - 1; xi++) {
                                for (int yi = xi + 1; yi < xyIndices.size(); yi++) {
                                    int[] wOptions;
                                    if (widthIdx >= 0 && pww > 0) wOptions = new int[]{pww};
                                    else if (widthIdx >= 0) wOptions = new int[]{fVals[widthIdx]};
                                    else if (pww > 0) wOptions = new int[]{pww};
                                    else wOptions = new int[]{200, 150, 98};
                                    for (int wVal : wOptions) {
                                        if (wVal <= 0) continue;
                                        int vx = fVals[xyIndices.get(xi)], vy = fVals[xyIndices.get(yi)];
                                        if (vx >= 0 && vy >= 0 && h > 0 && gx >= vx && gx < vx + wVal && gy >= vy && gy < vy + h) {
                                            widgetBounds = new int[]{vx, vy, wVal, h};
                                            hitWidget = w;
                                            ReflectionHelper.dbg("guiClick: hit list " + w.getClass().getSimpleName() + " at (" + vx + "," + vy + ")+" + wVal + "x" + h);
                                            break;
                                        }
                                        vx = fVals[xyIndices.get(yi)]; vy = fVals[xyIndices.get(xi)];
                                        if (vx >= 0 && vy >= 0 && h > 0 && gx >= vx && gx < vx + wVal && gy >= vy && gy < vy + h) {
                                            widgetBounds = new int[]{vx, vy, wVal, h};
                                            hitWidget = w;
                                            ReflectionHelper.dbg("guiClick: hit list " + w.getClass().getSimpleName() + " at (" + vx + "," + vy + ")+" + wVal + "x" + h);
                                            break;
                                        }
                                    }
                                    if (hitWidget != null) break;
                                }
                                if (hitWidget != null) break;
                            }
                        } catch (Exception ignored) {}
                    }
                    if (hitWidget != null) break;
                }
                ReflectionHelper.dbg("guiClick: list scan done, hit=" + (hitWidget != null));
                if (hitWidget != null) {
                    widgetResult = invokeWidgetCallback(hitWidget, gx, gy, button, widgetBounds);
                }
            }
            if (widgetResult.contains("\"callback\""))
                return "{\"clicked\":true,\"screen\":\"" + screenName + "\",\"gui\":[" + (int)gx + "," + (int)gy + "],\"results\":{" + results.toString() + "}" + widgetResult + "}";
            StringBuilder fieldDump = new StringBuilder("guiClick: " + screenName + " fields:");
            for (Field sf : ReflectionCache.getAllFields(screen.getClass())) {
                if (java.lang.reflect.Modifier.isStatic(sf.getModifiers())) continue;
                try { sf.setAccessible(true); Object sv = sf.get(screen);
                    if (sv != null) fieldDump.append(" ").append(sf.getName()).append("=").append(sv.getClass().getSimpleName());
                } catch (Exception ignored) {}
            }
            ReflectionHelper.dbg(fieldDump.toString());
            for (Field sf : ReflectionCache.getAllFields(screen.getClass())) {
                if (java.lang.reflect.Modifier.isStatic(sf.getModifiers())) continue;
                try {
                    sf.setAccessible(true);
                    Object sub = sf.get(screen);
                    if (sub == null || sub == screen) continue;
                    if (!ReflectionCache.isScreenInstancePublic(sub)) continue;
                    if (sub.getClass() == screen.getClass()) continue;
                    String subName = sub.getClass().getSimpleName();
                    ReflectionHelper.dbg("guiClick: trying sub-screen " + sf.getName() + "=" + subName);
                    StringBuilder subResults = new StringBuilder();
                    boolean subHandled = false;
                    for (Method m : sub.getClass().getMethods()) {
                        Class<?>[] pt = m.getParameterTypes();
                        if (!(m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) continue;
                        try {
                            m.setAccessible(true);
                            Object result = null;
                            if (m.getParameterCount() == 3 && pt[0] == double.class && pt[1] == double.class && pt[2] == int.class) {
                                result = m.invoke(sub, gx, gy, button);
                            } else if (m.getParameterCount() == 3 && !pt[0].isPrimitive() && pt[1] == double.class && pt[2] == double.class) {
                                Object clickType = createClickType(pt[0], button);
                                result = m.invoke(sub, clickType, gx, gy);
                            } else { continue; }
                            ReflectionHelper.dbg("guiClick: sub " + m.getName() + "=" + result);
                            if (subResults.length() > 0) subResults.append(",");
                            subResults.append("\"").append(m.getName()).append("\":").append(result);
                            if (Boolean.TRUE.equals(result)) subHandled = true;
                        } catch (Exception e) {
                            ReflectionHelper.dbg("guiClick: sub " + m.getName() + " failed: " + e.getMessage());
                        }
                    }
                    if (subHandled) {
                        String subWidgetResult = findAndClickWidget(sub, gx, gy, button);
                        return "{\"clicked\":true,\"screen\":\"" + screenName + "." + subName + "\",\"gui\":[" + (int)gx + "," + (int)gy + "],\"results\":{" + subResults.toString() + "}" + subWidgetResult + "}";
                    }
                } catch (Exception ignored) {}
            }
            ReflectionHelper.dbg("guiClick: trying children mouseClicked at gui(" + (int)gx + "," + (int)gy + ")");
            boolean childClicked = false;
            try {
                for (Method cm : screen.getClass().getMethods()) {
                    if (cm.getParameterCount() != 0 || !java.util.List.class.isAssignableFrom(cm.getReturnType())) continue;
                    if (java.lang.reflect.Modifier.isStatic(cm.getModifiers())) continue;
                    cm.setAccessible(true);
                    Object childrenObj = cm.invoke(screen);
                    if (!(childrenObj instanceof java.util.List)) continue;
                    java.util.List<?> children = (java.util.List<?>) childrenObj;
                    for (Object child : children) {
                        if (child == null || child == screen) continue;
                        String childName = child.getClass().getSimpleName();
                        for (Method mci : child.getClass().getMethods()) {
                            if (mci.getReturnType() != boolean.class || mci.getParameterCount() < 2) continue;
                            Class<?>[] mpt = mci.getParameterTypes();
                            try {
                                mci.setAccessible(true);
                                Object r = null;
                                if (mpt.length == 3 && mpt[0] == double.class && mpt[1] == double.class && mpt[2] == int.class) {
                                    r = mci.invoke(child, gx, gy, button);
                                } else if (mpt.length == 3 && !mpt[0].isPrimitive() && mpt[1] == double.class && mpt[2] == double.class) {
                                    Object clickType = createClickType(mpt[0], button);
                                    if (clickType != null) r = mci.invoke(child, clickType, gx, gy);
                                } else if (mpt.length == 4 && !mpt[0].isPrimitive() && mpt[1] == double.class && mpt[2] == double.class && mpt[3] == int.class) {
                                    Object clickType = createClickType(mpt[0], button);
                                    if (clickType != null) r = mci.invoke(child, clickType, gx, gy, button);
                                } else { continue; }
                                if (Boolean.TRUE.equals(r)) {
                                    ReflectionHelper.dbg("guiClick: child " + childName + "." + mci.getName() + " hit at (" + (int)gx + "," + (int)gy + ")");
                                    childClicked = true;
                                    break;
                                }
                            } catch (Exception e) { ReflectionHelper.dbg("guiClick: child " + childName + "." + mci.getName() + " err: " + e.getMessage()); }
                        }
                        if (childClicked) break;
                    }
                    if (childClicked) break;
                }
            } catch (Exception ignored) {}
            if (childClicked) {
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                Object newScreen = getCurrentScreen(mc);
                if (newScreen == null || newScreen.getClass() != screen.getClass()) {
                    String newScreenName = newScreen != null ? newScreen.getClass().getSimpleName() : "null";
                    return "{\"clicked\":true,\"screen\":\"" + screenName + "->" + newScreenName + "\",\"gui\":[" + (int)gx + "," + (int)gy + "],\"method\":\"child.mouseClicked\"}";
                }
            }
            String glfwResult = tryGlfwClick(mc, x, y, button);
            if (glfwResult != null && glfwResult.contains("\"clicked\":true")) return glfwResult;
            StringBuilder methodInfo = new StringBuilder();
            for (Method m : screen.getClass().getMethods()) {
                if (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class) {
                    Class<?>[] pt = m.getParameterTypes();
                    if (methodInfo.length() < 500) {
                        methodInfo.append(m.getName()).append("(");
                        for (int i = 0; i < pt.length; i++) { if (i > 0) methodInfo.append(","); methodInfo.append(pt[i].getSimpleName()); }
                        methodInfo.append(") ");
                    }
                }
            }
            return "{\"error\":\"no click on " + screen.getClass().getName() + "\",\"methods\":\"" + methodInfo + "\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private static String findAndClickWidget(Object screen, double gx, double gy, int button) {
        for (Field f : ReflectionCache.getAllFields(screen.getClass())) {
            f.setAccessible(true);
            Object list;
            try { list = f.get(screen); } catch (Exception e) { continue; }
            if (!(list instanceof java.util.List)) continue;
            java.util.List<?> wList = (java.util.List<?>) list;
            if (wList.isEmpty()) continue;
            for (Object w : wList) {
                int[] bounds = null;
                java.util.List<Field> wFields = ReflectionCache.getAllFields(w.getClass());
                java.util.List<Field> intFields = new java.util.ArrayList<>();
                for (Field wf : wFields) { if (wf.getType() == int.class) intFields.add(wf); }
                if (intFields.size() < 4) continue;
                int x=-1,y=-1,width=-1,height=-1;
                for (Field wf : intFields) {
                    String wn = wf.getName();
                    wf.setAccessible(true);
                    try {
                        if (wn.equals("x")) x = wf.getInt(w);
                        else if (wn.equals("y")) y = wf.getInt(w);
                        else if (wn.equals("width")) width = wf.getInt(w);
                        else if (wn.equals("height")) height = wf.getInt(w);
                    } catch (Exception ignored) {}
                }
                if (x >= 0 && y >= 0 && width > 0 && height > 0 && gx >= x && gx < x + width && gy >= y && gy < y + height) {
                    return invokeWidgetCallback(w, gx, gy, button, new int[]{x, y, width, height});
                }
            }
        }
        return "";
    }

    private static Object createClickType(Class<?> type, int button) {
        try {
            if (type.isEnum()) {
                Object[] values = type.getEnumConstants();
                if (values != null && values.length > 0) {
                    for (Object v : values) {
                        String name = ((Enum<?>) v).name().toUpperCase();
                        if (name.contains("LEFT") && button == 0) return v;
                        if (name.contains("RIGHT") && button == 1) return v;
                        if (name.contains("MIDDLE") && button == 2) return v;
                    }
                    return values[0];
                }
            }
            for (Method m : type.getMethods()) {
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1
                        && m.getReturnType() == type && (m.getParameterTypes()[0] == int.class || m.getParameterTypes()[0] == Integer.class)) {
                    return m.invoke(null, button);
                }
            }
            for (Method m : type.getMethods()) {
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0 && m.getReturnType() == type) {
                    return m.invoke(null);
                }
            }
            for (java.lang.reflect.Constructor<?> c : type.getDeclaredConstructors()) {
                c.setAccessible(true);
                Class<?>[] cpt = c.getParameterTypes();
                if (cpt.length == 1 && (cpt[0] == int.class || cpt[0] == Integer.class)) {
                    return c.newInstance(button);
                } else if (cpt.length == 2 && cpt[0] == int.class && cpt[1] == int.class) {
                    return c.newInstance(button, 0);
                }
            }
            for (java.lang.reflect.Constructor<?> c : type.getDeclaredConstructors()) {
                c.setAccessible(true);
                Class<?>[] cpt = c.getParameterTypes();
                Object[] args = new Object[cpt.length];
                boolean ok = true;
                for (int i = 0; i < cpt.length; i++) {
                    if (cpt[i] == int.class || cpt[i] == Integer.class) args[i] = 0;
                    else if (cpt[i] == long.class) args[i] = 0L;
                    else if (cpt[i] == double.class) args[i] = 0.0;
                    else if (cpt[i] == float.class) args[i] = 0.0f;
                    else if (cpt[i] == boolean.class) args[i] = false;
                    else { ok = false; break; }
                }
                if (ok && args.length > 0) try { return c.newInstance(args); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            ReflectionHelper.dbg("createClickType failed for " + type.getName() + ": " + e.getMessage());
        }
        return null;
    }

    private static String tryGlfwClick(Object mc, int x, int y, int button) {
        try {
            Object mouseHandler = ReflectionCache.getMouseHandler(mc);
            if (mouseHandler == null) return "{\"error\":\"no mouseHandler\"}";
            long handle = WindowHelper.getWindowHandle(mc);
            if (handle == 0) return "{\"error\":\"no windowHandle\"}";
            double winX = (double) x;
            double winY = (double) y;
            ReflectionHelper.dbg("glfwClick: direct call winX=" + winX + " winY=" + winY);
            for (java.lang.reflect.Method m : mouseHandler.getClass().getDeclaredMethods()) {
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length == 3 && pt[0] == long.class && pt[1] == double.class && pt[2] == double.class
                        && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    try { m.setAccessible(true); m.invoke(mouseHandler, handle, winX, winY); ReflectionHelper.dbg("glfwClick: cursor pos set"); break; } catch (Exception e) { ReflectionHelper.dbg("glfwClick: cursor pos failed: " + e.getMessage()); }
                }
            }
            java.lang.reflect.Method btnMethod = ReflectionCache.findMouseButtonMethod(mouseHandler.getClass());
            if (btnMethod != null) {
                btnMethod.setAccessible(true);
                Class<?>[] bpt = btnMethod.getParameterTypes();
                if (bpt.length == 3 && bpt[1] != int.class) {
                    Object mouseInputPress = InputInjectionHelper.createMouseInputPublic(bpt[1], button, 0);
                    Object mouseInputRelease = InputInjectionHelper.createMouseInputPublic(bpt[1], button, 0);
                    if (mouseInputPress != null && mouseInputRelease != null) {
                        btnMethod.invoke(mouseHandler, handle, mouseInputPress, 1);
                        Thread.sleep(50);
                        btnMethod.invoke(mouseHandler, handle, mouseInputRelease, 0);
                    }
                } else {
                    btnMethod.invoke(mouseHandler, handle, button, 1, 0);
                    Thread.sleep(50);
                    btnMethod.invoke(mouseHandler, handle, button, 0, 0);
                }
                ReflectionHelper.dbg("glfwClick: success");
                return "{\"clicked\":true,\"method\":\"glfw\",\"gui\":[" + x + "," + y + "]}";
            }
            return "{\"error\":\"glfw: no btn method\"}";
        } catch (Exception e) {
            return "{\"error\":\"glfw: " + e.getMessage() + "\"}";
        }
    }

    public static String getScreenButtons(Object mc) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen == null) return "{\"error\":\"no screen\"}";
            StringBuilder sb = new StringBuilder("{\"screen\":\"" + screen.getClass().getSimpleName() + "\",\"buttons\":[");
            boolean first = true;
            for (Field f : ReflectionCache.getAllFields(screen.getClass())) {
                String fn = f.getName();
                if (fn.equals("buttonList") || fn.equals("buttons") || fn.equals("field_146292_n") || fn.equals("children") || fn.equals("renderables") || fn.contains("button")) {
                    try {
                        f.setAccessible(true);
                        Object list = f.get(screen);
                        if (list instanceof java.util.List) {
                            for (Object btn : (java.util.List<?>) list) {
                                if (first) first = false; else sb.append(",");
                                int id = 0, x = 0, y = 0, w = 0, h = 0;
                                String label = "";
                                Class<?> bc = btn.getClass();
                                java.util.List<Field> intFields = new java.util.ArrayList<>();
                                java.util.List<Field> strFields = new java.util.ArrayList<>();
                                for (Field bf : ReflectionCache.getAllFields(bc)) {
                                    if (bf.getType() == int.class) intFields.add(bf);
                                    else if (bf.getType() == String.class) strFields.add(bf);
                                }
                                for (Field bf : intFields) {
                                    String bfn = bf.getName();
                                    bf.setAccessible(true);
                                    try {
                                        if (bfn.equals("id") || bfn.contains("146127") || bfn.endsWith("_k")) id = bf.getInt(btn);
                                        else if (bfn.equals("x") || bfn.contains("146120") || bfn.endsWith("_f")) x = bf.getInt(btn);
                                        else if (bfn.equals("y") || (bfn.contains("121") && !bfn.contains("146")) || bfn.endsWith("_g")) y = bf.getInt(btn);
                                        else if (bfn.equals("width") || bfn.contains("146118") || bfn.endsWith("_e")) w = bf.getInt(btn);
                                        else if (bfn.equals("height") || bfn.contains("119") && !bfn.contains("146") || bfn.endsWith("_h")) h = bf.getInt(btn);
                                    } catch (Exception ignored) {}
                                }
                                if (x == 0 && y == 0 && w == 0 && intFields.size() >= 5) {
                                    x = readIntField(btn, intFields, 1);
                                    y = readIntField(btn, intFields, 2);
                                    w = readIntField(btn, intFields, 3);
                                    h = readIntField(btn, intFields, 4);
                                }
                                for (Field bf : strFields) {
                                    String bfn = bf.getName();
                                    bf.setAccessible(true);
                                    try {
                                        if (bfn.contains("displayString") || bfn.contains("146126") || bfn.endsWith("_o"))
                                            label = String.valueOf(bf.get(btn));
                                    } catch (Exception ignored) {}
                                }
                                if (label.isEmpty() && !strFields.isEmpty()) {
                                    try { strFields.get(0).setAccessible(true); label = String.valueOf(strFields.get(0).get(btn)); } catch (Exception ignored) {}
                                }
                                sb.append(String.format("{\"id\":%d,\"x\":%d,\"y\":%d,\"w\":%d,\"h\":%d,\"label\":\"%s\"}",
                                        id, x, y, w, h, label.replace("\"", "'")));
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
            sb.append("]}");
            return sb.toString();
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private static int readIntField(Object obj, java.util.List<Field> fields, int index) {
        if (index < fields.size()) try { fields.get(index).setAccessible(true); return fields.get(index).getInt(obj); } catch (Exception e) {}
        return 0;
    }

    public static String clickButtonById(Object mc, int buttonId) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen == null) return "{\"error\":\"no screen\"}";
            for (Field f : ReflectionCache.getAllFields(screen.getClass())) {
                String fn = f.getName();
                if (fn.equals("buttonList") || fn.equals("buttons") || fn.equals("field_146292_n") || fn.equals("children") || fn.equals("renderables") || fn.contains("button")) {
                    f.setAccessible(true);
                    Object list = f.get(screen);
                    if (list instanceof java.util.List) {
                        for (Object btn : (java.util.List<?>) list) {
                            int id = 0;
                            for (Field bf : ReflectionCache.getAllFields(btn.getClass())) {
                                String bfn = bf.getName();
                                if (bfn.equals("id") || bfn.contains("146127") || bfn.endsWith("_k")) {
                                    try { bf.setAccessible(true); id = bf.getInt(btn); } catch (Exception ignored) {}
                                }
                            }
                            if (id == buttonId) {
                                ReflectionHelper.dbg("clickButtonById: found button id=" + buttonId + " class=" + btn.getClass().getSimpleName());
                                for (Method bm : ReflectionCache.getAllMethods(btn.getClass())) {
                                    String bn = bm.getName();
                                    if ((bn.equals("onPress") || bn.equals("onClick") || bn.equals("pressAction")
                                            || bn.contains("Press") || bn.contains("Click"))
                                            && bm.getParameterCount() <= 2) {
                                        try {
                                            bm.setAccessible(true);
                                            Class<?>[] bpt = bm.getParameterTypes();
                                            Object[] bargs = new Object[bpt.length];
                                            for (int bi = 0; bi < bpt.length; bi++) {
                                                if (bpt[bi] == double.class) bargs[bi] = 0.0;
                                                else if (bpt[bi] == float.class) bargs[bi] = 0.0f;
                                                else bargs[bi] = 0;
                                            }
                                            bm.invoke(btn, bargs);
                                            return "{\"clicked\":true,\"button_id\":" + buttonId + ",\"btn_method\":\"" + bn + "\"}";
                                        } catch (Exception e) { ReflectionHelper.dbg("btn invoke fail " + bn + ": " + e.getMessage()); }
                                    }
                                }
                                for (Field bf : ReflectionCache.getAllFields(btn.getClass())) {
                                    String bfn = bf.getName();
                                    if (bfn.equals("onPress") || bfn.contains("onPress")) {
                                        try {
                                            bf.setAccessible(true);
                                            Object pressHandler = bf.get(btn);
                                            if (pressHandler != null) {
                                                ReflectionHelper.dbg("clickButtonById: invoking onPress handler " + pressHandler.getClass().getName());
                                                for (Method hm : ReflectionCache.getAllMethods(pressHandler.getClass())) {
                                                    if (hm.getName().equals("accept") && hm.getParameterCount() == 1) {
                                                        hm.setAccessible(true);
                                                        hm.invoke(pressHandler, btn);
                                                        return "{\"clicked\":true,\"button_id\":" + buttonId + ",\"via\":\"onPress.accept\"}";
                                                    }
                                                }
                                                for (Method hm : ReflectionCache.getAllMethods(pressHandler.getClass())) {
                                                    if ((hm.getName().equals("accept") || hm.getName().equals("run") || hm.getName().equals("get"))
                                                            && hm.getParameterCount() == 0) {
                                                        hm.setAccessible(true);
                                                        hm.invoke(pressHandler);
                                                        return "{\"clicked\":true,\"button_id\":" + buttonId + ",\"via\":\"" + hm.getName() + "()\"}";
                                                    }
                                                }
                                            }
                                        } catch (Exception e) { ReflectionHelper.dbg("onPress field fail: " + e.getMessage()); }
                                    }
                                }
                                for (Method m : ReflectionCache.getAllMethods(screen.getClass())) {
                                    if ((m.getName().contains("actionPerformed") || m.getName().contains("func_146284"))
                                            && m.getParameterCount() == 1) {
                                        try {
                                            m.setAccessible(true);
                                            m.invoke(screen, btn);
                                            return "{\"clicked\":true,\"button_id\":" + buttonId + ",\"method\":\"" + m.getName() + "\"}";
                                        } catch (Exception e) { ReflectionHelper.dbg("clickButtonById invoke fail " + m.getName() + ": " + e.getMessage()); }
                                    }
                                }
                                for (Method m : ReflectionCache.getAllMethods(screen.getClass())) {
                                    if (m.getParameterCount() == 1) {
                                        Class<?> pt = m.getParameterTypes()[0];
                                        if (pt.isAssignableFrom(btn.getClass())) {
                                            try {
                                                m.setAccessible(true);
                                                m.invoke(screen, btn);
                                                return "{\"clicked\":true,\"button_id\":" + buttonId + ",\"method\":\"" + m.getName() + "\"}";
                                            } catch (Exception e) { ReflectionHelper.dbg("clickButtonById typeMatch fail " + m.getName() + ": " + e.getMessage()); }
                                        }
                                    }
                                }
                                return "{\"clicked\":true,\"button_id\":" + buttonId + ",\"method\":\"direct\"}";
                            }
                        }
                    }
                }
            }
            return "{\"error\":\"button " + buttonId + " not found\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String enumerateWidgets(Object mc) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen == null) return "{\"error\":\"no screen\"}";
            StringBuilder sb = new StringBuilder("{\"screen\":\"" + screen.getClass().getSimpleName() + "\",\"widgets\":[");
            int idx = 0;
            boolean first = true;
            boolean found = false;
            for (String fn : new String[]{"renderables", "children", "widgets"}) {
                for (Field f : ReflectionCache.getAllFields(screen.getClass())) {
                    if (f.getName().equals(fn)) {
                        try {
                            f.setAccessible(true);
                            Object list = f.get(screen);
                            if (list instanceof java.util.List) {
                                for (Object w : (java.util.List<?>) list) {
                                    if (first) first = false; else sb.append(",");
                                    String cls = w.getClass().getSimpleName();
                                    int x=0,y=0,w2=0,h2=0;
                                    boolean hasOnPress = false;
                                    for (Field wf : ReflectionCache.getAllFields(w.getClass())) {
                                        try { wf.setAccessible(true);
                                            String wfn = wf.getName();
                                            if (wfn.equals("x")) x = wf.getInt(w);
                                            else if (wfn.equals("y")) y = wf.getInt(w);
                                            else if (wfn.equals("width")) w2 = wf.getInt(w);
                                            else if (wfn.equals("height")) h2 = wf.getInt(w);
                                            else if (wfn.equals("onPress") && wf.get(w) != null) hasOnPress = true;
                                        } catch(Exception ignored){}
                                    }
                                    for (Method wm : ReflectionCache.getAllMethods(w.getClass())) {
                                        if (wm.getName().equals("onPress")) hasOnPress = true;
                                    }
                                    sb.append(String.format("{\"i\":%d,\"c\":\"%s\",\"x\":%d,\"y\":%d,\"w\":%d,\"h\":%d,\"press\":%b}",
                                            idx, cls, x, y, w2, h2, hasOnPress));
                                    idx++;
                                }
                                found = true;
                            }
                        } catch (Exception ignored) {}
                        break;
                    }
                }
                if (found) break;
            }
            sb.append("],\"total\":" + idx + "}");
            return sb.toString();
        } catch (Exception e) { return "{\"error\":\"" + e.getMessage() + "\"}"; }
    }

    public static String clickButtonByIndex(Object mc, int index) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen == null) return "{\"error\":\"no screen\"}";
            String[] fieldNames = new String[]{"renderables", "children", "widgets"};
            Object targetWidget = null;
            for (String fn : fieldNames) {
                try {
                    for (Field f : ReflectionCache.getAllFields(screen.getClass())) {
                        if (f.getName().equals(fn)) {
                            f.setAccessible(true);
                            Object list = f.get(screen);
                            if (list instanceof java.util.List) {
                                java.util.List<?> wl = (java.util.List<?>) list;
                                if (index < wl.size()) {
                                    targetWidget = wl.get(index);
                                    break;
                                }
                            }
                        }
                    }
                    if (targetWidget != null) break;
                } catch (Exception ignored) {}
            }
            if (targetWidget == null) {
                for (Field f : ReflectionCache.getAllFields(screen.getClass())) {
                    String fn = f.getName();
                    if (fn.contains("button")) {
                        f.setAccessible(true);
                        Object list = f.get(screen);
                        if (list instanceof java.util.List) {
                            java.util.List<?> wl = (java.util.List<?>) list;
                            if (index < wl.size()) {
                                targetWidget = wl.get(index);
                                break;
                            }
                        }
                    }
                }
            }
            if (targetWidget == null) return "{\"error\":\"index " + index + " out of range\"}";
            Object widget = targetWidget;
            ReflectionHelper.dbg("clickButtonByIndex: index=" + index + " class=" + widget.getClass().getSimpleName());
            String widgetClassName = widget.getClass().getSimpleName();
            ReflectionHelper.dbg("clickButtonByIndex: checking CycleButton: className=" + widgetClassName + " match=" + widgetClassName.equals("CycleButton"));
            if (widgetClassName.equals("CycleButton")) {
                ReflectionHelper.dbg("clickButtonByIndex: ENTERED CycleButton handler");
                try {
                    for (Method cm : ReflectionCache.getAllMethods(widget.getClass())) {
                        if (cm.getName().equals("cycleValue") && cm.getParameterCount() == 0) {
                            cm.setAccessible(true);
                            cm.invoke(widget);
                            return "{\"clicked\":true,\"index\":" + index + ",\"via\":\"cycleValue()\",\"class\":\"CycleButton\"}";
                        }
                    }
                    Field idxField = null, valField = null, valsField = null, onChangeField = null;
                    for (Field df : ReflectionCache.getAllFields(widget.getClass())) {
                        String dn = df.getName();
                        if (dn.equals("index")) idxField = df;
                        else if (dn.equals("value")) valField = df;
                        else if (dn.equals("values")) valsField = df;
                        else if (dn.equals("onValueChange")) onChangeField = df;
                    }
                    ReflectionHelper.dbg("clickBtn CB: idxField=" + (idxField != null) + " valsField=" + (valsField != null) + " valField=" + (valField != null) + " onChangeField=" + (onChangeField != null));
                    if (idxField != null && valsField != null) {
                        idxField.setAccessible(true);
                        valsField.setAccessible(true);
                        int curIdx = idxField.getInt(widget);
                        Object vals = valsField.get(widget);
                        ReflectionHelper.dbg("clickBtn CB: curIdx=" + curIdx + " vals type=" + (vals != null ? vals.getClass().getName() : "null"));
                        java.util.List<?> valueList = null;
                        if (vals instanceof java.util.List) {
                            valueList = (java.util.List<?>) vals;
                        } else {
                            for (Method m : ReflectionCache.getAllMethods(vals.getClass())) {
                                String mn = m.getName();
                                ReflectionHelper.dbg("clickBtn CB: vals method " + mn + "(" + m.getParameterCount() + ") ret=" + m.getReturnType().getSimpleName());
                                if ((mn.equals("values") || mn.equals("get") || mn.equals("apply") || mn.equals("getAll"))
                                        && m.getParameterCount() == 0 && java.util.List.class.isAssignableFrom(m.getReturnType())) {
                                    m.setAccessible(true);
                                    valueList = (java.util.List<?>) m.invoke(vals);
                                    break;
                                }
                            }
                            if (valueList == null) {
                                for (Method m : ReflectionCache.getAllMethods(vals.getClass())) {
                                    if (m.getParameterCount() == 0 && java.util.List.class.isAssignableFrom(m.getReturnType())) {
                                        m.setAccessible(true);
                                        valueList = (java.util.List<?>) m.invoke(vals);
                                        break;
                                    }
                                }
                            }
                        }
                        int size = valueList != null ? valueList.size() : 0;
                        ReflectionHelper.dbg("clickBtn CB: valueList size=" + size);
                        if (size > 0) {
                            int newIdx = (curIdx + 1) % size;
                            ReflectionHelper.dbg("clickBtn CB: cycling from " + curIdx + " to " + newIdx);
                            idxField.setInt(widget, newIdx);
                            Object newVal = null;
                            if (valueList != null && newIdx < valueList.size()) {
                                newVal = valueList.get(newIdx);
                            }
                            ReflectionHelper.dbg("clickBtn CB: newVal=" + (newVal != null ? newVal.toString() : "null"));
                            if (valField != null) { valField.setAccessible(true); valField.set(widget, newVal); }
                            if (onChangeField != null) {
                                onChangeField.setAccessible(true);
                                Object onChange = onChangeField.get(widget);
                                ReflectionHelper.dbg("clickBtn CB: onChange=" + (onChange != null ? onChange.getClass().getName() : "null"));
                                if (onChange != null) {
                                    for (Method om : ReflectionCache.getAllMethods(onChange.getClass())) {
                                        ReflectionHelper.dbg("clickBtn CB: onChange method " + om.getName() + "(" + om.getParameterCount() + ")");
                                    }
                                    for (Method om : ReflectionCache.getAllMethods(onChange.getClass())) {
                                        if (om.getName().equals("onValueChange") && om.getParameterCount() == 3) {
                                            om.setAccessible(true); om.invoke(onChange, widget, newVal, null); break;
                                        } else if (om.getName().equals("onValueChange") && om.getParameterCount() == 2) {
                                            om.setAccessible(true); om.invoke(onChange, widget, newVal); break;
                                        } else if (om.getName().equals("accept") && om.getParameterCount() >= 1) {
                                            om.setAccessible(true);
                                            if (om.getParameterCount() == 1) om.invoke(onChange, widget);
                                            else if (om.getParameterCount() == 3) om.invoke(onChange, widget, newVal, null);
                                            break;
                                        }
                                    }
                                }
                            }
                            return "{\"clicked\":true,\"index\":" + index + ",\"via\":\"manual_cycle\",\"newIdx\":" + newIdx + ",\"class\":\"CycleButton\"}";
                        }
                    }
                } catch (Exception e) { ReflectionHelper.dbg("CycleButton cycle fail: " + e.getMessage()); }
            }
            for (Field bf : ReflectionCache.getAllFields(widget.getClass())) {
                String bfn = bf.getName();
                if (bfn.equals("onPress")) {
                    try {
                        bf.setAccessible(true);
                        Object pressHandler = bf.get(widget);
                        if (pressHandler != null) {
                            for (Method hm : ReflectionCache.getAllMethods(pressHandler.getClass())) {
                                if (hm.getName().equals("accept") && hm.getParameterCount() == 1) {
                                    hm.setAccessible(true); hm.invoke(pressHandler, widget);
                                    return "{\"clicked\":true,\"index\":" + index + ",\"via\":\"onPress.accept\",\"class\":\"" + widget.getClass().getSimpleName() + "\"}";
                                }
                            }
                            for (Method hm : ReflectionCache.getAllMethods(pressHandler.getClass())) {
                                if (hm.getName().equals("onPress") && hm.getParameterCount() == 1) {
                                    hm.setAccessible(true); hm.invoke(pressHandler, widget);
                                    return "{\"clicked\":true,\"index\":" + index + ",\"via\":\"onPress.onPress\",\"class\":\"" + widget.getClass().getSimpleName() + "\"}";
                                }
                            }
                            for (Method hm : ReflectionCache.getAllMethods(pressHandler.getClass())) {
                                if (hm.getParameterCount() == 0 && (hm.getName().equals("run") || hm.getName().equals("accept"))) {
                                    hm.setAccessible(true); hm.invoke(pressHandler);
                                    return "{\"clicked\":true,\"index\":" + index + ",\"via\":\"" + hm.getName() + "()\",\"class\":\"" + widget.getClass().getSimpleName() + "\"}";
                                }
                            }
                        }
                    } catch (Exception e) { ReflectionHelper.dbg("onPress field fail: " + e.getMessage()); }
                }
            }
            for (Method bm : ReflectionCache.getAllMethods(widget.getClass())) {
                String bn = bm.getName();
                if ((bn.equals("onPress") || bn.equals("onClick") || bn.equals("pressAction"))
                        && bm.getParameterCount() <= 2) {
                    try {
                        bm.setAccessible(true);
                        Class<?>[] bpt = bm.getParameterTypes();
                        Object[] bargs = new Object[bpt.length];
                        for (int bi = 0; bi < bpt.length; bi++) {
                            if (bpt[bi] == double.class) bargs[bi] = 0.0;
                            else if (bpt[bi] == float.class) bargs[bi] = 0.0f;
                            else if (bpt[bi] == int.class) bargs[bi] = 0;
                            else if (bpt[bi] == long.class) bargs[bi] = 0L;
                            else if (bpt[bi] == boolean.class) bargs[bi] = false;
                            else if (bpt[bi].isInstance(widget)) bargs[bi] = widget;
                            else bargs[bi] = null;
                        }
                        bm.invoke(widget, bargs);
                        return "{\"clicked\":true,\"index\":" + index + ",\"method\":\"" + bn + "\",\"class\":\"" + widget.getClass().getSimpleName() + "\"}";
                    } catch (Exception e) { ReflectionHelper.dbg("btn invoke fail " + bn + ": " + e.getMessage()); }
                }
            }
            return "{\"error\":\"no press method on index " + index + " (" + widget.getClass().getSimpleName() + ")\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String callScreenMethod(Object mc, String methodName) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen == null) return "{\"error\":\"no screen\"}";
            String sn = screen.getClass().getSimpleName();
            if (methodName.equals("*") || methodName.equals("__list__")) {
                StringBuilder sb = new StringBuilder("{\"screen\":\"" + sn + "\",\"methods\":[");
                boolean first = true;
                for (Method m : ReflectionCache.getAllMethods(screen.getClass())) {
                    if (java.lang.reflect.Modifier.isPublic(m.getModifiers()) && m.getParameterCount() <= 1) {
                        if (first) first = false; else sb.append(",");
                        sb.append("\"").append(m.getName()).append("(").append(m.getParameterCount()).append(")\"");
                    }
                }
                sb.append("]}");
                return sb.toString();
            }
            for (Method m : ReflectionCache.getAllMethods(screen.getClass())) {
                if (m.getName().equals(methodName) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    Object result = m.invoke(screen);
                    return "{\"called\":true,\"screen\":\"" + sn + "\",\"method\":\"" + methodName + "\",\"result\":" + (result == null ? "null" : "\"" + result + "\"") + "}";
                }
            }
            StringBuilder available = new StringBuilder();
            for (Method m : ReflectionCache.getAllMethods(screen.getClass())) {
                if (m.getName().toLowerCase().contains(methodName.toLowerCase())
                        && m.getParameterCount() <= 1
                        && java.lang.reflect.Modifier.isPublic(m.getModifiers())) {
                    if (available.length() > 0) available.append(",");
                    available.append(m.getName()).append("(").append(m.getParameterCount()).append(")");
                }
            }
            return "{\"error\":\"method '" + methodName + "' not found on " + sn + "\",\"candidates\":[" + available.toString() + "]}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String guiKeyPress(Object mc, int keyCode, int scanCode, int action, int modifiers) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen != null) {
                for (Method m : ReflectionCache.getAllMethods(screen.getClass())) {
                    String n = m.getName();
                    if ((n.equals("keyPressed") || n.equals("func_73864_a") || n.contains("keyPressed"))
                            && m.getParameterCount() >= 3) {
                        try {
                            m.setAccessible(true);
                            Object[] args = new Object[m.getParameterCount()];
                            args[0] = keyCode;
                            args[1] = scanCode;
                            args[2] = modifiers;
                            for (int i = 3; i < args.length; i++) args[i] = 0;
                            Object result = m.invoke(screen, args);
                            return "{\"keyPressed\":true,\"result\":" + result + "}";
                        } catch (Exception ignored) {}
                    }
                    if ((n.equals("keyTyped") || n.equals("func_73869_a")) && m.getParameterCount() == 2
                            && m.getParameterTypes()[0] == char.class && m.getParameterTypes()[1] == int.class) {
                        try {
                            m.setAccessible(true);
                            Object result = m.invoke(screen, (char) keyCode, keyCode);
                            return "{\"keyPressed\":true,\"result\":" + result + ",\"method\":\"keyTyped\"}";
                        } catch (Exception ignored) {}
                    }
                }
            }
            Object kbHandler = null;
            try { kbHandler = mc.getClass().getField("keyboardHandler").get(mc); } catch (Exception ignored) {}
            if (kbHandler == null) {
                try { kbHandler = mc.getClass().getMethod("keyboardHandler").invoke(mc); } catch (Exception ignored) {}
            }
            if (kbHandler != null) {
                for (Method m : kbHandler.getClass().getDeclaredMethods()) {
                    if (m.getName().equals("keyPress") && m.getParameterCount() == 5) {
                        long handle = 0;
                        try { handle = WindowHelper.getWindowHandle(mc); } catch (Exception ignored) {}
                        ReflectionHelper.dbg("guiKeyPress: keyPress(" + handle + "," + keyCode + "," + scanCode + "," + action + "," + modifiers + ") kb=" + kbHandler.getClass().getSimpleName());
                        m.setAccessible(true);
                        m.invoke(kbHandler, handle, keyCode, scanCode, action, modifiers);
                        return "{\"keyPressed\":true,\"via\":\"keyboardHandler\"}";
                    }
                }
            }
            return "{\"error\":\"no key input method found\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String guiCharType(Object mc, char ch, int modifiers) {
        try {
            Object screen = getCurrentScreen(mc);
            ReflectionHelper.dbg("guiCharType: ch=" + ch + " screen=" + (screen != null ? screen.getClass().getSimpleName() : "null"));
            if (screen != null) {
                for (Method m : ReflectionCache.getAllMethods(screen.getClass())) {
                    String n = m.getName();
                    Class<?>[] pt = m.getParameterTypes();
                    if (m.getParameterCount() == 2 && pt[0] == char.class && pt[1] == int.class) {
                        try {
                            m.setAccessible(true);
                            m.invoke(screen, ch, modifiers);
                            return "{\"charTyped\":true,\"method\":\"" + n + "\"}";
                        } catch (Exception e) {
                            ReflectionHelper.dbg("guiCharType: " + n + " failed: " + e.getMessage());
                        }
                    }
                }
                String charStr = String.valueOf(ch);
                for (Method m : ReflectionCache.getAllMethods(screen.getClass())) {
                    String n = m.getName();
                    Class<?>[] pt = m.getParameterTypes();
                    if ((n.equals("insertText") || n.equals("charTyped")) && m.getParameterCount() == 2
                            && pt[0] == String.class && pt[1] == boolean.class) {
                        try {
                            m.setAccessible(true);
                            m.invoke(screen, charStr, false);
                            return "{\"charTyped\":true,\"method\":\"" + n + "(String)\"}";
                        } catch (Exception e) {
                            ReflectionHelper.dbg("guiCharType: " + n + "(String) failed: " + e.getMessage());
                        }
                    }
                }
            }
            Object kbHandler = null;
            try { kbHandler = mc.getClass().getField("keyboardHandler").get(mc); } catch (Exception ignored) {}
            if (kbHandler == null) {
                try { kbHandler = mc.getClass().getMethod("keyboardHandler").invoke(mc); } catch (Exception ignored) {}
            }
            if (kbHandler != null) {
                long handle = WindowHelper.getWindowHandle(mc);
                for (Method m : kbHandler.getClass().getDeclaredMethods()) {
                    String n = m.getName();
                    if (m.getParameterCount() >= 2 && m.getParameterTypes()[0] == long.class
                            && m.getParameterTypes()[1] == char.class) {
                        try {
                            m.setAccessible(true);
                            if (m.getParameterCount() == 3) m.invoke(kbHandler, handle, ch, modifiers);
                            else m.invoke(kbHandler, handle, ch);
                            return "{\"charTyped\":true,\"via\":\"kb." + n + "\"}";
                        } catch (Exception ignored) {}
                    }
                }
                for (Method m : kbHandler.getClass().getDeclaredMethods()) {
                    String n = m.getName().toLowerCase();
                    if (m.getParameterCount() == 2 && m.getParameterTypes()[0] == char.class
                            && (n.contains("char") || n.contains("type"))) {
                        try {
                            m.setAccessible(true);
                            m.invoke(kbHandler, ch, modifiers);
                            return "{\"charTyped\":true,\"via\":\"kb2." + m.getName() + "\"}";
                        } catch (Exception ignored) {}
                    }
                }
            }
            return "{\"error\":\"no charTyped method\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    static Object getCurrentScreen(Object mc) throws Exception {
        Object screen = ReflectionCache.getCurrentScreen(mc);
        if (screen != null) return screen;
        return null;
    }

    public static String directScroll(Object mc, double mouseX, double mouseY, double delta) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen == null) return "{\"error\":\"no screen\"}";
            String sn = screen.getClass().getSimpleName();
            double mx = mouseX, my = mouseY;
            if (mx < 0 || my < 0) {
                try {
                    Object mh = mc.getClass().getField("mouseHandler").get(mc);
                    for (Field f : ReflectionCache.getAllFields(mh.getClass())) {
                        if (f.getName().equals("xpos") || f.getName().equals("field_192635_i")) {
                            try { f.setAccessible(true); mx = f.getDouble(mh); } catch (Exception ignored) {}
                        }
                    }
                    for (Field f : ReflectionCache.getAllFields(mh.getClass())) {
                        if (f.getName().equals("ypos") || f.getName().equals("field_192636_j")) {
                            try { f.setAccessible(true); my = f.getDouble(mh); } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
            }
            double guiScale = 1.0;
            try {
                Object window = null;
                for (Method m : mc.getClass().getMethods()) {
                    if (m.getName().equals("getWindow") && m.getParameterCount() == 0) {
                        window = m.invoke(mc); break;
                    }
                }
                if (window != null) {
                    for (Method m : window.getClass().getMethods()) {
                        if (m.getName().equals("getGuiScale") && m.getParameterCount() == 0) {
                            guiScale = ((Number) m.invoke(window)).doubleValue();
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}
            double scaledX = mx / guiScale;
            double scaledY = my / guiScale;
            Method target = null;
            for (Method m : ReflectionCache.getAllMethods(screen.getClass())) {
                if (m.getName().equals("mouseScrolled")) {
                    Class<?>[] pt = m.getParameterTypes();
                    if (pt.length == 4 && pt[0] == double.class && pt[1] == double.class
                        && pt[2] == double.class && pt[3] == double.class) {
                        target = m; break;
                    }
                    if (pt.length == 3 && pt[0] == double.class && pt[1] == double.class && pt[2] == double.class) {
                        target = m; break;
                    }
                }
            }
            if (target == null) return "{\"error\":\"mouseScrolled not found on " + sn + "\"}";
            target.setAccessible(true);
            if (target.getParameterCount() == 4) {
                target.invoke(screen, scaledX, scaledY, 0.0, delta);
            } else {
                target.invoke(screen, scaledX, scaledY, delta);
            }
            return "{\"direct_scroll\":true,\"screen\":\"" + sn + "\",\"mouseX\":" + scaledX + ",\"mouseY\":" + scaledY + ",\"delta\":" + delta + ",\"guiScale\":" + guiScale + "}";
        } catch (Exception e) {
            return "{\"error\":\"" + (e.getMessage() != null ? e.getMessage().replace("\\","\\\\").replace("\"","\\\"") : "null") + "\"}";
        }
    }

    public static String selectListItem(Object mc, int targetIndex) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen == null) return "{\"error\":\"no screen\"}";
            String sn = screen.getClass().getSimpleName();

            Method childrenMethod = null;
            for (Method m : ReflectionCache.getAllMethods(screen.getClass())) {
                if (m.getName().equals("children") && m.getParameterCount() == 0) {
                    childrenMethod = m; break;
                }
            }
            if (childrenMethod == null) return "{\"error\":\"no children() on " + sn + "\"}";
            childrenMethod.setAccessible(true);
            Object children = childrenMethod.invoke(screen);
            if (!(children instanceof java.util.List)) return "{\"error\":\"children not a List\"}";
            java.util.List<?> childList = (java.util.List<?>) children;

            Object listWidget = null;
            for (Object child : childList) {
                String cn = child.getClass().getSimpleName();
                if (cn.contains("List") || cn.contains("SelectionList")) {
                    listWidget = child; break;
                }
            }
            if (listWidget == null) return "{\"error\":\"no list widget in children\"}";

            String lcn = listWidget.getClass().getName();
            ReflectionHelper.dbg("selectListItem: found " + lcn);

            int setSize = 0;
            for (Method m : ReflectionCache.getAllMethods(listWidget.getClass())) {
                if (m.getName().equals("getRowCount") && m.getParameterCount() == 0) {
                    try { m.setAccessible(true); setSize = ((Number) m.invoke(listWidget)).intValue(); } catch (Exception ignored) {}
                    break;
                }
            }
            if (setSize == 0) {
                try {
                    for (Method m : ReflectionCache.getAllMethods(listWidget.getClass())) {
                        if (m.getName().equals("size") && m.getParameterCount() == 0) {
                            m.setAccessible(true); setSize = ((Number) m.invoke(listWidget)).intValue(); break;
                        }
                    }
                } catch (Exception ignored) {}
            }
            if (setSize > 0 && targetIndex >= setSize) return "{\"error\":\"index " + targetIndex + " >= size " + setSize + "\"}";

            boolean selected = false;
            boolean setSelectedCalled = false;
            for (Method m : ReflectionCache.getAllMethods(listWidget.getClass())) {
                String mn = m.getName();
                if ((mn.equals("setSelected") || mn.equals("selectItemIndex") || mn.equals("setSelectedIndex"))
                    && m.getParameterCount() == 1) {
                    Class<?> pt = m.getParameterTypes()[0];
                    try {
                        m.setAccessible(true);
                        if (pt == int.class) m.invoke(listWidget, targetIndex);
                        else if (pt == Integer.class) m.invoke(listWidget, Integer.valueOf(targetIndex));
                        setSelectedCalled = true;
                        ReflectionHelper.dbg("selectListItem: called " + mn + "(" + targetIndex + ")");
                        break;
                    } catch (Exception e) { ReflectionHelper.dbg("selectListItem: " + mn + " failed: " + e.getMessage()); }
                }
            }

            if (!setSelectedCalled) {
                for (Field f : ReflectionCache.getAllFields(listWidget.getClass())) {
                    String fn = f.getName();
                    if ((fn.equals("selected") || fn.equals("selectedIndex") || fn.equals("selectedItem") || fn.contains("selectedRow"))
                        && (f.getType() == int.class || f.getType() == Integer.class)) {
                        try {
                            f.setAccessible(true);
                            f.setInt(listWidget, targetIndex);
                            selected = true;
                            ReflectionHelper.dbg("selectListItem: set field " + fn + "=" + targetIndex);
                            break;
                        } catch (Exception e) { ReflectionHelper.dbg("selectListItem: field " + fn + " failed: " + e.getMessage()); }
                    }
                }
            }

            if (!selected) {
                try {
                    for (Method m : ReflectionCache.getAllMethods(listWidget.getClass())) {
                        if (m.getName().equals("ensureVisible") && m.getParameterCount() == 1) {
                            m.setAccessible(true);
                            m.invoke(listWidget, targetIndex);
                            ReflectionHelper.dbg("selectListItem: ensureVisible(" + targetIndex + ")");
                        }
                    }
                } catch (Exception ignored) {}
            }

            {
                try {
                    java.util.List<?> entries = null;
                    for (Method m : ReflectionCache.getAllMethods(listWidget.getClass())) {
                        if (m.getName().equals("children") && m.getParameterCount() == 0) {
                            m.setAccessible(true);
                            Object result = m.invoke(listWidget);
                            if (result instanceof java.util.List) { entries = (java.util.List<?>) result; break; }
                        }
                    }
                    if (entries == null) {
                        for (Field f : ReflectionCache.getAllFields(listWidget.getClass())) {
                            if (java.util.List.class.isAssignableFrom(f.getType())) {
                                try { f.setAccessible(true); entries = (java.util.List<?>) f.get(listWidget); } catch (Exception ignored) {}
                                if (entries != null) break;
                            }
                        }
                    }
                    if (entries != null && targetIndex < entries.size()) {
                        Object entry = entries.get(targetIndex);
                        ReflectionHelper.dbg("selectListItem: got entry " + targetIndex + " of " + entries.size() + ": " + entry.getClass().getName());
                        for (Method m : ReflectionCache.getAllMethods(entry.getClass())) {
                            String mn = m.getName();
                            if ((mn.equals("select") || mn.equals("onClick") || mn.equals("onSelect") || mn.equals("setSelected"))
                                && m.getParameterCount() == 0) {
                                try {
                                    m.setAccessible(true);
                                    m.invoke(entry);
                                    selected = true;
                                    ReflectionHelper.dbg("selectListItem: called " + mn + "() on entry " + targetIndex);
                                    break;
                                } catch (Exception e) { ReflectionHelper.dbg("selectListItem: " + mn + "() failed: " + e.getMessage()); }
                            }
                        }
                    } else if (entries != null) {
                        ReflectionHelper.dbg("selectListItem: entries.size=" + entries.size() + " targetIndex=" + targetIndex);
                    } else {
                        ReflectionHelper.dbg("selectListItem: no entries found on " + lcn);
                    }
                } catch (Exception e) { ReflectionHelper.dbg("selectListItem: entry fallback: " + e.getMessage()); }
            }

            if (!selected) {
                StringBuilder methods = new StringBuilder();
                for (Method m : ReflectionCache.getAllMethods(listWidget.getClass())) {
                    if (m.getName().toLowerCase().contains("select") || m.getName().toLowerCase().contains("row") || m.getName().toLowerCase().contains("index")) {
                        methods.append(m.getName()).append("(").append(m.getParameterCount()).append(") ");
                    }
                }
                StringBuilder fields = new StringBuilder();
                for (Field f : ReflectionCache.getAllFields(listWidget.getClass())) {
                    fields.append(f.getName()).append(":").append(f.getType().getSimpleName()).append(" ");
                }
                return "{\"error\":\"could not select on " + lcn + "\",\"methods\":\"" + methods + "\",\"fields\":\"" + fields + "\"}";
            }

            for (Method m : ReflectionCache.getAllMethods(screen.getClass())) {
                if (m.getName().equals("updateButtonValidity") && m.getParameterCount() == 1) {
                    try { m.setAccessible(true); m.invoke(screen, true); } catch (Exception ignored) {}
                    break;
                }
            }

            return "{\"selectListItem\":true,\"screen\":\"" + sn + "\",\"listWidget\":\"" + listWidget.getClass().getSimpleName() + "\",\"index\":" + targetIndex + ",\"setSize\":" + setSize + "}";
        } catch (Exception e) {
            return "{\"error\":\"" + (e.getMessage() != null ? e.getMessage().replace("\\","\\\\").replace("\"","\\\"") : "null") + "\"}";
        }
    }

    public static String pasteText(Object mc, String text) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen != null) {
                try {
                    Object clipboardManager = ReflectionCache.getClipboardManager(mc);
                    if (clipboardManager != null) {
                        for (Method m : ReflectionCache.getAllMethods(clipboardManager.getClass())) {
                            if ((m.getName().equals("setClipboard") || m.getName().contains("setClipboard"))
                                    && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) {
                                m.setAccessible(true);
                                m.invoke(clipboardManager, text);
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {}
                for (Method m : ReflectionCache.getAllMethods(screen.getClass())) {
                    if ((m.equals("paste") || m.getName().contains("paste"))
                            && m.getParameterCount() == 1 && m.getParameterTypes()[0] == CharSequence.class) {
                        try {
                            m.setAccessible(true);
                            m.invoke(screen, text);
                            return "{\"pasted\":true,\"method\":\"paste(CharSequence)\"}";
                        } catch (Exception ignored) {}
                    }
                    if ((m.getName().equals("paste") || m.getName().contains("paste"))
                            && m.getParameterCount() == 0) {
                        try {
                            m.setAccessible(true);
                            m.invoke(screen);
                            return "{\"pasted\":true,\"method\":\"paste()\"}";
                        } catch (Exception ignored) {}
                    }
                }
            }
            java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new java.awt.datatransfer.StringSelection(text), null);
            Thread.sleep(50);
            if (screen != null) {
                for (Method m : ReflectionCache.getAllMethods(screen.getClass())) {
                    if ((m.getName().equals("paste") || m.getName().contains("paste")) && m.getParameterCount() == 0) {
                        try {
                            m.setAccessible(true);
                            m.invoke(screen);
                            return "{\"pasted\":true,\"method\":\"awt_paste\"}";
                        } catch (Exception ignored) {}
                    }
                }
            }
            hotkey(mc, new String[]{"ctrl", "v"});
            return "{\"pasted\":true,\"method\":\"hotkey_ctrl_v\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private static void hotkey(Object mc, String[] keys) {
        try {
            long h = WindowHelper.getWindowHandle(mc);
            if (h == 0) return;
            int[] codes = new int[keys.length];
            for (int i = 0; i < keys.length; i++) codes[i] = GlfwKeys.keyCode(keys[i]);
            for (int c : codes) { InputInjectionHelper.sendKey(h, c, 1); Thread.sleep(5); }
            Thread.sleep(80);
            for (int i = codes.length - 1; i >= 0; i--) InputInjectionHelper.sendKey(h, codes[i], 0);
        } catch (Exception e) { System.err.println("[ScreenInteractionHelper] hotkey: " + e.getMessage()); }
    }
}

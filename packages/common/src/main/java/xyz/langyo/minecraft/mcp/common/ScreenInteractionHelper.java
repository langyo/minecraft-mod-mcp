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

    public static String guiClick(Object mc, int x, int y, int button) {
        try {
            Object screen = getCurrentScreen(mc);
            if (screen == null) return "{\"error\":\"no screen\"}";
            String screenName = screen.getClass().getSimpleName();
            ReflectionHelper.dbg("guiClick: gui(" + x + "," + y + ") screen=" + screenName);
            String glfwResult = tryGlfwClick(mc, x, y, button);
            if (glfwResult != null && glfwResult.contains("\"clicked\":true")) return glfwResult;            double gx = (double) x;
            double gy = (double) y;
            StringBuilder results = new StringBuilder();
            for (Method m : screen.getClass().getMethods()) {
                Class<?>[] pt = m.getParameterTypes();
                if (!(m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) continue;
                try {
                    m.setAccessible(true);
                    Object result = null;
                    if (m.getParameterCount() == 3 && pt[0] == double.class && pt[1] == double.class && pt[2] == int.class) {
                        result = m.invoke(screen, gx, gy, button);
                    } else if (m.getParameterCount() == 3 && pt[0] == int.class && pt[1] == int.class && pt[2] == int.class) {
                        result = m.invoke(screen, (int)gx, (int)gy, button);
                    } else if (m.getParameterCount() == 3 && !pt[0].isPrimitive() && pt[1] == double.class && pt[2] == double.class) {
                        result = m.invoke(screen, null, gx, gy);
                    } else if (m.getParameterCount() == 2 && pt[0] == double.class && pt[1] == double.class) {
                        result = m.invoke(screen, gx, gy);
                    } else if (m.getParameterCount() == 4 && !pt[0].isPrimitive() && pt[1] == double.class && pt[2] == double.class && pt[3] == int.class) {
                        result = m.invoke(screen, null, gx, gy, button);
                    } else { continue; }
                    ReflectionHelper.dbg("guiClick: " + m.getName() + java.util.Arrays.toString(pt) + "=" + result + " from " + m.getDeclaringClass().getSimpleName());
                    if (results.length() > 0) results.append(",");
                    results.append("\"").append(m.getName()).append("\":").append(result);
                    if (Boolean.TRUE.equals(result)) break;
                } catch (Exception e) {
                    ReflectionHelper.dbg("guiClick: " + m.getName() + " failed: " + e.getMessage());
                }
            }
            String widgetResult = "";
            for (Field f : ReflectionCache.getAllFields(screen.getClass())) {
                f.setAccessible(true);
                Object list;
                try { list = f.get(screen); } catch (Exception e) { continue; }
                if (!(list instanceof java.util.List)) continue;
                java.util.List<?> wList = (java.util.List<?>) list;
                if (wList.isEmpty()) continue;
                boolean hasWidgetCoords = false;
                for (Field wf : wList.get(0).getClass().getFields()) {
                    String wfn = wf.getName();
                    if ((wfn.equals("x") || wfn.equals("y") || wfn.equals("width") || wfn.equals("height")) && wf.getType() == int.class) {
                        hasWidgetCoords = true; break;
                    }
                }
                if (!hasWidgetCoords) continue;
                for (Object w : wList) {
                            try {
                                int wx=0, wy=0, ww=0, wh=0;
                                for (Field wf : ReflectionCache.getAllFields(w.getClass())) {
                                    try { wf.setAccessible(true);
                                        String wfn = wf.getName();
                                        if (wfn.equals("x")) wx = wf.getInt(w);
                                        else if (wfn.equals("y")) wy = wf.getInt(w);
                                        else if (wfn.equals("width")) ww = wf.getInt(w);
                                        else if (wfn.equals("height")) wh = wf.getInt(w);
                                    } catch(Exception ignored){}
                                }
                                if (wx <= gx && gx < wx + ww && wy <= gy && gy < wy + wh) {
                                    ReflectionHelper.dbg("guiClick: hit widget " + w.getClass().getSimpleName() + " at (" + wx + "," + wy + ")+" + ww + "x" + wh);
                                    double relX = gx - wx;
                                    double relY = gy - wy;
                                    boolean widgetClicked = false;
                                    for (Method bm : w.getClass().getMethods()) {
                                        if (!bm.getDeclaringClass().getName().startsWith("net.minecraft.") && !bm.getDeclaringClass().getName().startsWith("java.")) continue;
                                        Class<?>[] bpt = bm.getParameterTypes();
                                        if (bm.getReturnType() != boolean.class && bm.getReturnType() != Boolean.class) continue;
                                        try {
                                            bm.setAccessible(true);
                                            Object r = null;
                                            if (bm.getParameterCount() == 3 && bpt[0] == double.class && bpt[1] == double.class && bpt[2] == int.class) {
                                                r = bm.invoke(w, relX, relY, button);
                                            } else if (bm.getParameterCount() == 2 && bpt[0] == double.class && bpt[1] == double.class) {
                                                r = bm.invoke(w, relX, relY);
                                            } else if (bm.getParameterCount() == 3 && !bpt[0].isPrimitive() && bpt[1] == double.class && bpt[2] == double.class) {
                                                r = bm.invoke(w, null, relX, relY);
                                            } else { continue; }
                                            ReflectionHelper.dbg("guiClick: widget." + bm.getName() + "=" + r);
                                            if (Boolean.TRUE.equals(r)) { widgetClicked = true; break; }
                                        } catch (Exception e) {
                                            ReflectionHelper.dbg("guiClick: widget." + bm.getName() + " failed: " + e.getMessage());
                                        }
                                    }
                                    if (!widgetClicked) {
                                        for (Method bm : ReflectionCache.getAllMethods(w.getClass())) {
                                            Class<?>[] bpt = bm.getParameterTypes();
                                            if (bm.getReturnType() != boolean.class && bm.getReturnType() != Boolean.class) continue;
                                            try {
                                                bm.setAccessible(true);
                                                Object r = null;
                                                if (bm.getParameterCount() == 3 && bpt[0] == double.class && bpt[1] == double.class && bpt[2] == int.class) {
                                                    r = bm.invoke(w, relX, relY, button);
                                                } else if (bm.getParameterCount() == 2 && bpt[0] == double.class && bpt[1] == double.class) {
                                                    r = bm.invoke(w, relX, relY);
                                                } else if (bm.getParameterCount() == 3 && !bpt[0].isPrimitive() && bpt[1] == double.class && bpt[2] == double.class) {
                                                    r = bm.invoke(w, null, relX, relY);
                                                } else { continue; }
                                                ReflectionHelper.dbg("guiClick: widget2." + bm.getName() + "=" + r);
                                                if (Boolean.TRUE.equals(r)) { widgetClicked = true; break; }
                                            } catch (Exception e) {
                                                ReflectionHelper.dbg("guiClick: widget2." + bm.getName() + " failed: " + e.getMessage());
                                        }
                                    }
                                    }
                                    if (!widgetClicked) {
                                        for (Method bm : ReflectionCache.getAllMethods(w.getClass())) {
                                            if ((bm.getName().equals("onPress") || bm.getName().equals("onClick"))
                                                    && bm.getParameterCount() <= 2) {
                                            bm.setAccessible(true);
                                            Class<?>[] bpt = bm.getParameterTypes();
                                            Object[] bargs = new Object[bpt.length];
                                            for (int bi = 0; bi < bpt.length; bi++) {
                                                if (bpt[bi] == double.class) bargs[bi] = 0.0;
                                                else if (bpt[bi] == float.class) bargs[bi] = 0.0f;
                                                else if (bpt[bi] == int.class) bargs[bi] = 0;
                                                else if (bpt[bi] == long.class) bargs[bi] = 0L;
                                                else if (bpt[bi] == boolean.class) bargs[bi] = false;
                                                else if (bpt[bi].isInstance(w)) bargs[bi] = w;
                                                else bargs[bi] = null;
                                            }
                                            bm.invoke(w, bargs);
                                            widgetResult = ",\"widget\":\"" + w.getClass().getSimpleName() + "\",\"widget_method\":\"" + bm.getName() + "\"";
                                            }
                                        }
                                    }
                                    if (!widgetClicked) {
                                        for (Field bf : ReflectionCache.getAllFields(w.getClass())) {
                                        if (bf.getName().equals("onPress")) {
                                            bf.setAccessible(true);
                                            Object ph = bf.get(w);
                                            if (ph != null) {
                                                for (Method hm : ReflectionCache.getAllMethods(ph.getClass())) {
                                                    if (hm.getName().equals("accept") && hm.getParameterCount() == 1) {
                                                        hm.setAccessible(true); hm.invoke(ph, w);
                                                        widgetResult = ",\"widget\":\"" + w.getClass().getSimpleName() + "\",\"via\":\"onPress.accept\"";
                                                    } else if (hm.getParameterCount() == 0 && (hm.getName().equals("run") || hm.getName().equals("accept"))) {
                                                        hm.setAccessible(true); hm.invoke(ph);
                                                        widgetResult = ",\"widget\":\"" + w.getClass().getSimpleName() + "\",\"via\":\"" + hm.getName() + "()\"";
                                                    }
                                                }
                                            }
                                            }
                                        }
                                    }
                                }
                            } catch(Exception ignored){}
                        }
                    }
            if (results.length() > 0 || !widgetResult.isEmpty())
                return "{\"clicked\":true,\"screen\":\"" + screenName + "\",\"gui\":[" + (int)gx + "," + (int)gy + "],\"results\":{" + results.toString() + "}" + widgetResult + (glfwResult != null ? ",\"glfw\":\"" + glfwResult.replace("\"", "'") + "\"" : "") + "}";
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

    private static volatile String glfwClickResult = null;

    private static String tryGlfwClick(Object mc, int x, int y, int button) {
        try {
            Object mouseHandler = ReflectionCache.getMouseHandler(mc);
            if (mouseHandler == null) return "{\"error\":\"no mouseHandler\"}";
            long handle = WindowHelper.getWindowHandle(mc);
            if (handle == 0) return "{\"error\":\"no windowHandle\"}";
            double guiScale = 1.0;
            try {
                Object window = WindowHelper.findWindowObject(mc);
                if (window != null) {
                    for (java.lang.reflect.Method sm : window.getClass().getMethods()) {
                        if (sm.getParameterCount() == 0 && (sm.getReturnType() == double.class || sm.getReturnType() == float.class)) {
                            try { guiScale = ((Number) sm.invoke(window)).doubleValue(); if (guiScale > 0) break; } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}
            if (guiScale <= 0) guiScale = 1.0;
            double winX = x / guiScale;
            double winY = y / guiScale;
            final double fWinX = winX;
            final double fWinY = winY;
            final long fHandle = handle;
            final int fButton = button;
            final Object fMH = mouseHandler;
            glfwClickResult = null;
            ReflectedInputHandler.executeOnRenderThread(() -> {
                try {
                    ReflectionHelper.dbg("glfwClick: running on render thread");
                    for (java.lang.reflect.Method m : fMH.getClass().getDeclaredMethods()) {
                        Class<?>[] pt = m.getParameterTypes();
                        if (pt.length == 3 && pt[0] == long.class && pt[1] == double.class && pt[2] == double.class
                                && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                            try { m.setAccessible(true); m.invoke(fMH, fHandle, fWinX, fWinY); ReflectionHelper.dbg("glfwClick: cursor pos set"); break; } catch (Exception e) { ReflectionHelper.dbg("glfwClick: cursor pos failed: " + e.getMessage()); }
                        }
                    }
                    java.lang.reflect.Method btnMethod = ReflectionCache.findMouseButtonMethod(fMH.getClass());
                    if (btnMethod != null) {
                        btnMethod.setAccessible(true);
                        btnMethod.invoke(fMH, fHandle, fButton, 1, 0);
                        Thread.sleep(50);
                        btnMethod.invoke(fMH, fHandle, fButton, 0, 0);
                        glfwClickResult = "{\"clicked\":true,\"method\":\"glfw\",\"gui\":[" + x + "," + y + "]";
                        ReflectionHelper.dbg("glfwClick: success");
                    } else {
                        glfwClickResult = "{\"error\":\"glfw: no btn method\"}";
                    }
                } catch (Exception e) {
                    glfwClickResult = "{\"error\":\"glfw exec: " + e.getMessage() + "\"}";
                    ReflectionHelper.dbg("glfwClick: failed: " + e.getMessage());
                }
            });
            long deadline = System.currentTimeMillis() + 2000;
            while (glfwClickResult == null && System.currentTimeMillis() < deadline) Thread.sleep(50);
            return glfwClickResult;
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

package xyz.langyo.minecraft.mcp.common;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public final class PlayerWorldHelper {

    private PlayerWorldHelper() {}

    public static String getPlayerInfo(Object mc) {
        try {
            Object player = getPlayer(mc);
            if (player == null) return "{\"name\":null}";
            String name = invokeString(player, "getName");
            double health = getDouble(player, "getHealth");
            double x = getDouble(player, "getX", "posX");
            double y = getDouble(player, "getY", "posY");
            double z = getDouble(player, "getZ", "posZ");
            String dim = getDimensionId(player);
            return String.format("{\"name\":\"%s\",\"health\":%.1f,\"pos\":\"%.1f %.1f %.1f\",\"dimension\":\"%s\"}",
                    name, health, x, y, z, dim);
        } catch (Exception e) {
            return "{\"name\":null,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String getWorldInfo(Object mc) {
        try {
            Object level = getLevel(mc);
            if (level == null) return "{\"world_name\":null}";
            String worldName = "unknown";
            String difficulty = "normal";
            String gameType = "survival";

            Object server = invokeOrNull(level, "getServer");
            if (server != null) {
                Object wd = invokeOrNull(server, "getWorldData");
                if (wd != null) worldName = invokeString(wd, "getLevelName");
            }
            difficulty = getDifficultyKey(level);
            gameType = getGameType(mc);

            return String.format("{\"world_name\":\"%s\",\"difficulty\":\"%s\",\"gametype\":\"%s\"}",
                    worldName, difficulty, gameType);
        } catch (Exception e) {
            return "{\"world_name\":null,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String getDimensionId(Object player) throws Exception {
        Object level = getPlayerLevel(player);
        if (level == null) return "overworld";
        Object provider = fieldOrNull(level, "provider");
        if (provider != null) {
            Object dimId = fieldOrNull(provider, "dimensionId");
            if (dimId != null) return String.valueOf(dimId);
        }
        try {
            Object dim = level.getClass().getMethod("dimension").invoke(level);
            try { return dim.getClass().getMethod("identifier").invoke(dim).toString(); } catch (NoSuchMethodException ignored) {}
            try { return dim.getClass().getMethod("location").invoke(dim).toString(); } catch (NoSuchMethodException ignored) {}
            try { return dim.getClass().getMethod("getRegistryName").invoke(dim).toString(); } catch (NoSuchMethodException ignored) {}
        } catch (NoSuchMethodException ignored) {}
        return "overworld";
    }

    public static String getDifficultyKey(Object level) throws Exception {
        Object diff = level.getClass().getMethod("getDifficulty").invoke(level);
        try { return (String) diff.getClass().getMethod("getSerializedName").invoke(diff); } catch (NoSuchMethodException ignored) {}
        try { return (String) diff.getClass().getMethod("getName").invoke(diff); } catch (NoSuchMethodException ignored) {}
        try { return ((Enum<?>) diff).name().toLowerCase(); } catch (ClassCastException ignored) {}
        return "normal";
    }

    public static String getGameType(Object mc) throws Exception {
        try {
            Object gm = null;
            try { gm = mc.getClass().getMethod("gameMode").invoke(mc); } catch (NoSuchMethodException ignored) {}
            if (gm == null) {
                java.lang.reflect.Field discovered = ReflectionCache.getDiscoveredField("gameMode");
                if (discovered != null) { try { discovered.setAccessible(true); gm = discovered.get(mc); } catch (Exception ignored) {} }
            }
            if (gm == null) {
                for (java.lang.reflect.Field f : ReflectionCache.getAllFields(mc.getClass())) {
                    String fn = f.getName().toLowerCase();
                    if (fn.contains("gamemode") || fn.contains("interaction") || fn.contains("multiplayer") || fn.contains("playercontroller") || fn.contains("field_71442_b")) {
                        try { f.setAccessible(true); gm = f.get(mc); break; } catch (Exception ignored) {}
                    }
                }
            }
            if (gm != null) {
                for (Method m : ReflectionCache.getAllMethods(gm.getClass())) {
                    if (m.getReturnType().isEnum() && m.getParameterCount() == 0 && m.getReturnType().getSimpleName().contains("Game")) {
                        try { Object pt = m.invoke(gm); return (String) pt.getClass().getMethod("getName").invoke(pt); } catch (Exception ignored) {}
                    }
                }
                for (Method m : ReflectionCache.getAllMethods(gm.getClass())) {
                    if (m.getName().equals("getPlayerMode") && m.getParameterCount() == 0) {
                        try { Object pt = m.invoke(gm); return (String) pt.getClass().getMethod("getName").invoke(pt); } catch (Exception ignored) {}
                    }
                }
            }
            return "survival";
        } catch (Exception e) { return "survival"; }
    }

    public static String sendCommand(Object mc, String cmd) {
        try {
            Object player = getPlayer(mc);
            if (player == null) return "{\"error\":\"no player\"}";
            String stripped = cmd.startsWith("/") ? cmd.substring(1) : cmd;

            try {
                Object server = null;
                try { server = mc.getClass().getMethod("getSingleplayerServer").invoke(mc); } catch (Exception ignored) {}
                if (server == null) {
                    try { server = mc.getClass().getMethod("getServer").invoke(mc); } catch (Exception ignored) {}
                }
                if (server == null) {
                    try { server = mc.getClass().getMethod("getIntegratedServer").invoke(mc); } catch (Exception ignored) {}
                }
                if (server == null) {
                    Object f = fieldOrNull(mc, "singleplayerServer");
                    if (f != null) server = f;
                }
                if (server == null) {
                    Object level = getLevel(mc);
                    if (level != null) server = invokeOrNull(level, "getServer");
                }
                if (server != null) {
                    ReflectionHelper.dbg("sendCommand: got server " + server.getClass().getName());
                    Object commands = invokeOrNull(server, "getCommands");
                    ReflectionHelper.dbg("sendCommand: commands=" + (commands != null ? commands.getClass().getName() : "null"));
                    if (commands != null) {
                        Object dispatcher = invokeOrNull(commands, "getDispatcher");
                        ReflectionHelper.dbg("sendCommand: dispatcher=" + (dispatcher != null ? dispatcher.getClass().getName() : "null"));
                        if (dispatcher != null) {
                            Object source = null;
                            for (String mname : new String[]{"createCommandSourceStack", "createCommandSource", "getSource"}) {
                                try {
                                    for (Method sm : ReflectionCache.getAllMethods(server.getClass())) {
                                        if (sm.getName().equals(mname) && sm.getParameterCount() == 0) {
                                            sm.setAccessible(true);
                                            source = sm.invoke(server);
                                            break;
                                        }
                                    }
                                } catch (Exception ignored) {}
                                if (source != null) break;
                            }
                            if (source == null) {
                                try {
                                    for (Method sm : ReflectionCache.getAllMethods(commands.getClass())) {
                                        if ((sm.getName().contains("Source") || sm.getName().contains("source")) && sm.getParameterCount() == 0) {
                                            sm.setAccessible(true);
                                            source = sm.invoke(commands);
                                            break;
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                            if (source != null) {
                                Object parseResult = dispatcher.getClass().getMethod("parse", String.class, Object.class)
                                        .invoke(dispatcher, stripped, source);
                                for (Method m : ReflectionCache.getAllMethods(parseResult.getClass())) {
                                    if (m.getName().equals("execute") && m.getParameterCount() == 0) {
                                        m.setAccessible(true);
                                        try { m.invoke(parseResult); return "{\"sent\":true,\"method\":\"dispatcher.execute\"}"; }
                                        catch (Exception e) { return "{\"sent\":false,\"method\":\"dispatcher\",\"error\":\"" + e.getMessage() + "\"}"; }
                                    }
                                }
                            }
                            for (Method m : ReflectionCache.getAllMethods(commands.getClass())) {
                                String mn = m.getName();
                                if ((mn.equals("performCommand") || mn.equals("execute")) && m.getParameterCount() == 2) {
                                    try {
                                        if (source == null) source = invokeOrNull(server, "createCommandSourceStack");
                                        if (source != null) {
                                            Class<?>[] ptypes = m.getParameterTypes();
                                            if (ptypes[0].isAssignableFrom(source.getClass()) && ptypes[1] == String.class) {
                                                m.setAccessible(true);
                                                m.invoke(commands, source, stripped);
                                                return "{\"sent\":true,\"method\":\"commands." + mn + "\"}";
                                            }
                                        }
                                    } catch (Exception e) { ReflectionHelper.dbg("sendCommand: " + mn + " failed: " + e.getMessage()); }
                                }
                            }
                            for (Method m : ReflectionCache.getAllMethods(server.getClass())) {
                                String mn = m.getName();
                                if ((mn.contains("executeCommand") || mn.contains("runCommand") || mn.equals("execute"))
                                        && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) {
                                    try {
                                        m.setAccessible(true);
                                        m.invoke(server, stripped);
                                        return "{\"sent\":true,\"method\":\"server." + mn + "\"}";
                                    } catch (Exception e) { ReflectionHelper.dbg("sendCommand: server." + mn + " failed: " + e.getMessage()); }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) { ReflectionHelper.dbg("sendCommand: dispatcher path failed: " + e.getMessage()); }

            String msg = "/" + stripped;
            for (Method m : ReflectionCache.getAllMethods(player.getClass())) {
                if ((m.getName().equals("sendChatMessage") || m.getName().contains("sendChat") || m.getName().contains("func_146158_b"))
                        && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) {
                    try { m.setAccessible(true); m.invoke(player, msg); return "{\"sent\":true,\"method\":\"" + m.getName() + "\"}"; }
                    catch (Exception ignored) {}
                }
            }
            Object conn = null;
            try { conn = player.getClass().getMethod("connection").invoke(player); }
            catch (NoSuchMethodException ignored) {
                conn = fieldOrNull(player, "Connection");
                if (conn == null) conn = fieldOrNull(player, "field_71174_a");
            }
            if (conn != null) {
                ReflectionHelper.dbg("sendCommand: found connection " + conn.getClass().getName());
                for (Method m : ReflectionCache.getAllMethods(conn.getClass())) {
                    if ((m.getName().equals("sendPacket") || m.getName().contains("sendPacket") || m.getName().contains("func_147297_a"))
                            && m.getParameterCount() == 1) {
                        try {
                            for (String pktName : new String[]{
                                "net.minecraft.network.protocol.game.ServerboundChatCommandPacket"
                            }) {
                                try {
                                    Class<?> pktClass = Class.forName(pktName);
                                    Object packet = pktClass.getConstructor(String.class).newInstance(stripped);
                                    m.setAccessible(true);
                                    m.invoke(conn, packet);
                                    return "{\"sent\":true,\"method\":\"packet:" + pktName + "\"}";
                                } catch (ClassNotFoundException ignored2) {}
                            }
                        } catch (Exception ignored) {}
                    }
                }
                for (Method m : ReflectionCache.getAllMethods(conn.getClass())) {
                    String mn = m.getName();
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class
                            && (mn.equals("sendCommand") || mn.equals("sendUnsignedCommand"))
                            && !mn.contains("verify") && !mn.contains("Valid") && !mn.contains("check")) {
                        try { m.setAccessible(true); m.invoke(conn, stripped); return "{\"sent\":true,\"method\":\"conn." + mn + "\"}"; }
                        catch (Exception ignored) {}
                    }
                }
                for (Method m : ReflectionCache.getAllMethods(conn.getClass())) {
                    String mn = m.getName();
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class
                            && (mn.equals("send") || mn.equals("sendChat") || mn.equals("sendChatMessage") || mn.equals("chat"))
                            && !mn.contains("verify") && !mn.contains("Valid") && !mn.contains("check") && !mn.contains("Command")) {
                        try { m.setAccessible(true); m.invoke(conn, msg); return "{\"sent\":true,\"method\":\"conn." + mn + "\"}"; }
                        catch (Exception ignored) {}
                    }
                }
                for (Method m : ReflectionCache.getAllMethods(conn.getClass())) {
                    if ((m.getName().equals("sendPacket") || m.getName().contains("sendPacket") || m.getName().contains("func_147297_a"))
                            && m.getParameterCount() == 1) {
                        try {
                            for (String pktName : new String[]{
                                "net.minecraft.network.play.client.CPacketChatMessage",
                                "net.minecraft.network.protocol.game.ServerboundChatPacket",
                                "net.minecraft.network.play.client.C01PacketChatMessage",
                            }) {
                                try {
                                    Class<?> pktClass = Class.forName(pktName);
                                    Object packet = pktClass.getConstructor(String.class).newInstance(msg);
                                    m.setAccessible(true);
                                    m.invoke(conn, packet);
                                    return "{\"sent\":true,\"method\":\"packet:" + pktName + "\"}";
                                } catch (ClassNotFoundException ignored2) {}
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            for (Method m : ReflectionCache.getAllMethods(player.getClass())) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class
                        && (m.getName().toLowerCase().contains("chat") || m.getName().toLowerCase().contains("command")
                            || m.getName().toLowerCase().contains("message") || m.getName().toLowerCase().contains("send"))) {
                    try { m.setAccessible(true); m.invoke(player, msg); return "{\"sent\":true,\"method\":\"" + m.getName() + "\"}"; }
                    catch (Exception ignored) {}
                }
            }
            return "{\"error\":\"no command method found\",\"player_class\":\"" + player.getClass().getName() + "\"}";
        } catch (Exception e) { return "{\"error\":\"" + e.getMessage() + "\"}"; }
    }

    public static String debugFields(Object mc) {
        StringBuilder sb = new StringBuilder("{\"class\":\"").append(mc.getClass().getName()).append("\",");
        List<Field> all = ReflectionCache.getAllFields(mc.getClass());
        sb.append("\"fieldCount\":").append(all.size()).append(",");
        sb.append("\"fields\":[");
        boolean first = true;
        for (Field f : all) {
            String n = f.getName();
            if (n.contains("Player") || n.contains("player") || n.contains("field_71439") ||
                n.contains("World") || n.contains("world") || n.contains("field_71441") || n.contains("field_71435") ||
                n.contains("Mouse") || n.contains("mouse") || n.contains("Keyboard") || n.contains("keyboard") ||
                n.contains("Screen") || n.contains("screen") || n.contains("Window") || n.contains("window") ||
                n.contains("Game") || n.contains("game")) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(mc);
                    if (!first) sb.append(",");
                    sb.append("{\"name\":\"").append(n).append("\",\"value\":\"").append(v != null ? v.getClass().getSimpleName() : "null").append("\"}");
                    first = false;
                } catch (Exception e) {
                    if (!first) sb.append(",");
                    sb.append("{\"name\":\"").append(n).append("\",\"error\":\"").append(e.getMessage()).append("\"}");
                    first = false;
                }
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    public static String setPlayerRotation(Object mc, float yaw, float pitch) {
        try {
            Object player = getPlayer(mc);
            if (player == null) return "{\"error\":\"no player\"}";
            setRotField(player, "yRot", yaw);
            setRotField(player, "xRot", pitch);
            setRotField(player, "yawRot", yaw);
            setRotField(player, "xRotO", pitch);
            setRotField(player, "yRotO", yaw);
            setRotField(player, "oYRot", yaw);
            setRotField(player, "oXRot", pitch);
            setRotField(player, "rotationYaw", yaw);
            setRotField(player, "rotationPitch", pitch);
            setRotField(player, "prevRotationYaw", yaw);
            setRotField(player, "prevRotationPitch", pitch);
            return "{\"rot_set\":true,\"yaw\":" + yaw + ",\"pitch\":" + pitch + "}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String deltaPlayerRotation(Object mc, float deltaYaw, float deltaPitch) {
        try {
            Object player = getPlayer(mc);
            if (player == null) return "{\"error\":\"no player\"}";
            float currentYaw = getRotField(player, "yRot", "rotationYaw");
            float currentPitch = getRotField(player, "xRot", "rotationPitch");
            float newYaw = currentYaw + deltaYaw;
            float newPitch = Math.max(-90f, Math.min(90f, currentPitch + deltaPitch));
            setPlayerRotation(mc, newYaw, newPitch);
            return "{\"rot_delta\":true,\"from\":[" + currentYaw + "," + currentPitch + "],\"to\":[" + newYaw + "," + newPitch + "]}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String doRightClick(Object mc) {
        try {
            Object screen = null;
            for (Method m : ReflectionCache.getAllMethods(mc.getClass())) {
                String mn = m.getName();
                if ((mn.equals("screen") || mn.equals("currentScreen")) && m.getParameterCount() == 0) {
                    try { m.setAccessible(true); screen = m.invoke(mc); break; } catch (Exception ignored) {}
                }
            }
            if (screen == null) {
                for (Field f : ReflectionCache.getAllFields(mc.getClass())) {
                    if (f.getType().getSimpleName().contains("Screen") || f.getType().getSimpleName().contains("GuiScreen")) {
                        try { f.setAccessible(true); screen = f.get(mc); break; } catch (Exception ignored) {}
                    }
                }
            }
            if (screen != null) {
                long handle = WindowHelper.getWindowHandle(mc);
                Object mouseHandler = ReflectionCache.getMouseHandler(mc);
                if (mouseHandler != null && handle != 0) {
                    Method target = ReflectionCache.findMouseButtonMethod(mouseHandler.getClass());
                    if (target != null) {
                        target.setAccessible(true);
                        target.invoke(mouseHandler, handle, 1, 1, 0);
                        Thread.sleep(50);
                        target.invoke(mouseHandler, handle, 1, 0, 0);
                        return "{\"right_click\":true,\"via\":\"screen_mouseHandler\"}";
                    }
                }
                InputInjectionHelper.sendMouseButton(handle, 1, 1);
                Thread.sleep(100);
                InputInjectionHelper.sendMouseButton(handle, 1, 0);
                return "{\"right_click\":true,\"via\":\"screen_sendMouseButton\"}";
            }
            for (Method m : ReflectionCache.getAllMethods(mc.getClass())) {
                String mn = m.getName();
                if ((mn.equals("startUseItem") || mn.equals("rightClickMouse") || mn.equals("func_147121_ag") || mn.equals("func_147118_ci"))
                        && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                        m.invoke(mc);
                        return "{\"right_click\":true,\"via\":\"startUseItem\",\"method\":\"" + mn + "\"}";
                    } catch (Exception ignored) {}
                }
            }
            for (Method m : ReflectionCache.getAllMethods(mc.getClass())) {
                String mn = m.getName().toLowerCase();
                if (m.getParameterCount() == 0 && mn.contains("useitem") && !mn.contains("tick") && !mn.contains("render")) {
                    try {
                        m.setAccessible(true);
                        m.invoke(mc);
                        return "{\"right_click\":true,\"via\":\"useItem_generic\",\"method\":\"" + m.getName() + "\"}";
                    } catch (Exception ignored) {}
                }
            }
            return "{\"error\":\"no useItem method found for 3D rightClick\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String doUseItem(Object mc) {
        try {
            for (Method m : ReflectionCache.getAllMethods(mc.getClass())) {
                String mn = m.getName();
                if ((mn.equals("startUseItem") || mn.equals("rightClickMouse") || mn.equals("func_147121_ag") || mn.equals("func_147118_ci"))
                        && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                        m.invoke(mc);
                        return "{\"use_item\":true,\"method\":\"" + mn + "\"}";
                    } catch (Exception ignored) {}
                }
            }
            for (Method m : ReflectionCache.getAllMethods(mc.getClass())) {
                String mn = m.getName().toLowerCase();
                if (m.getParameterCount() == 0 && mn.contains("useitem") && !mn.contains("tick") && !mn.contains("render")) {
                    try {
                        m.setAccessible(true);
                        m.invoke(mc);
                        return "{\"use_item\":true,\"method\":\"" + m.getName() + "\"}";
                    } catch (Exception ignored) {}
                }
            }
            return "{\"error\":\"no useItem method found\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String doPlaceBlock(Object mc) {
        try {
            Object player = getPlayer(mc);
            if (player == null) return "{\"error\":\"no player\"}";
            Object gameMode = null;
            try { gameMode = mc.getClass().getMethod("gameMode").invoke(mc); } catch (Exception ignored) {}
            if (gameMode == null) {
                for (Field f : ReflectionCache.getAllFields(mc.getClass())) {
                    String fn = f.getName().toLowerCase();
                    if (fn.contains("gamemode") || fn.contains("interaction") || fn.contains("multiplayer") || fn.contains("playercontroller") || fn.contains("field_71442_b")) {
                        try { f.setAccessible(true); gameMode = f.get(mc); ReflectionHelper.dbg("doPlaceBlock: found gameMode via field " + f.getName()); break; } catch (Exception ignored) {}
                    }
                }
            }
            if (gameMode == null) return "{\"error\":\"no gameMode\"}";
            double px = 0, py = 0, pz = 0;
            for (Method m : ReflectionCache.getAllMethods(player.getClass())) {
                if ((m.getName().equals("getX") || m.getName().equals("func_223148_aQ")) && m.getParameterCount() == 0) { m.setAccessible(true); px = ((Number)m.invoke(player)).doubleValue(); }
                if ((m.getName().equals("getY") || m.getName().equals("func_223149_e")) && m.getParameterCount() == 0) { m.setAccessible(true); py = ((Number)m.invoke(player)).doubleValue(); }
                if ((m.getName().equals("getZ") || m.getName().equals("func_223143_g")) && m.getParameterCount() == 0) { m.setAccessible(true); pz = ((Number)m.invoke(player)).doubleValue(); }
            }
            int bx = (int)Math.floor(px), by = (int)Math.floor(py) - 1, bz = (int)Math.floor(pz);
            ReflectionHelper.dbg("doPlaceBlock: player at " + px + "," + py + "," + pz + " placing at " + bx + "," + by + "," + bz);
            ReflectionHelper.dbg("doPlaceBlock: gameMode class=" + gameMode.getClass().getName());
            Class<?> vec3Class = null;
            try { vec3Class = Class.forName("net.minecraft.world.phys.Vec3"); } catch (ClassNotFoundException e) {
                vec3Class = Class.forName("net.minecraft.util.math.Vec3d");
            }
            Object hitVec = vec3Class.getConstructor(double.class, double.class, double.class).newInstance(px, by + 1.0, pz);
            Class<?> dirClass = null;
            Object dirUp = null;
            try { dirClass = Class.forName("net.minecraft.core.Direction"); } catch (ClassNotFoundException e) {
                dirClass = Class.forName("net.minecraft.util.math.Direction");
            }
            for (Object d : (Enum[])dirClass.getMethod("values").invoke(null)) { if (((Enum)d).name().equals("UP")) { dirUp = d; break; } }
            Class<?> bpClass = null;
            try { bpClass = Class.forName("net.minecraft.core.BlockPos"); } catch (ClassNotFoundException e) {
                bpClass = Class.forName("net.minecraft.util.math.BlockPos");
            }
            Object blockPos = bpClass.getConstructor(int.class, int.class, int.class).newInstance(bx, by, bz);
            Class<?> bhrClass = null;
            try { bhrClass = Class.forName("net.minecraft.world.phys.BlockHitResult"); } catch (ClassNotFoundException e) {
                bhrClass = Class.forName("net.minecraft.util.hit.BlockHitResult");
            }
            Object hitResult = bhrClass.getConstructor(vec3Class, dirClass, bpClass, boolean.class).newInstance(hitVec, dirUp, blockPos, false);
            Class<?> handClass = null;
            try { handClass = Class.forName("net.minecraft.world.InteractionHand"); } catch (ClassNotFoundException e) {
                handClass = Class.forName("net.minecraft.util.Hand");
            }
            Object mainHand = null;
            for (Object h : (Enum[])handClass.getMethod("values").invoke(null)) { if (((Enum)h).name().equals("MAIN_HAND")) { mainHand = h; break; } }
            boolean placed = false;
            StringBuilder methodsInfo = new StringBuilder();
            for (Method m : ReflectionCache.getAllMethods(gameMode.getClass())) {
                String mn = m.getName();
                Class<?>[] pts = m.getParameterTypes();
                methodsInfo.append(mn).append("(").append(pts.length).append(") ");
                if (mn.equals("useItemOn") || mn.equals("func_180517_b") || mn.contains("useItemOn")
                        || mn.startsWith("m_") || mn.startsWith("func_")) {
                    if (pts.length >= 3) {
                        try {
                            m.setAccessible(true);
                            if (pts.length == 4) {
                                m.invoke(gameMode, player, mainHand, hitResult);
                            } else if (pts.length == 3) {
                                m.invoke(gameMode, player, mainHand, hitResult);
                            }
                            placed = true;
                            ReflectionHelper.dbg("doPlaceBlock: OK via " + mn + "(" + pts.length + ")");
                            break;
                        } catch (Exception ex) {
                            ReflectionHelper.dbg("doPlaceBlock: " + mn + " failed: " + ex.getMessage());
                        }
                    }
                }
            }
            if (!placed) {
                for (Method m : ReflectionCache.getAllMethods(gameMode.getClass())) {
                    String mn = m.getName().toLowerCase();
                    if (mn.contains("useitem") && !mn.contains("continue") && m.getParameterCount() >= 2) {
                        try { m.setAccessible(true); m.invoke(gameMode, player, mainHand, hitResult); placed = true; ReflectionHelper.dbg("doPlaceBlock: OK via " + m.getName()); break; }
                        catch (Exception ex) { ReflectionHelper.dbg("doPlaceBlock: " + m.getName() + " failed: " + ex.getMessage()); }
                    }
                }
            }
            if (!placed) {
                for (Method m : ReflectionCache.getAllMethods(gameMode.getClass())) {
                    Class<?>[] pts = m.getParameterTypes();
                    if (m.getParameterCount() >= 3 && pts.length >= 3) {
                        boolean hasPlayer = false, hasHand = false, hasHit = false;
                        for (Class<?> pt : pts) {
                            if (pt.getSimpleName().contains("Player")) hasPlayer = true;
                            if (pt.getSimpleName().contains("Hand") || pt.getSimpleName().contains("InteractionHand")) hasHand = true;
                            if (pt.getSimpleName().contains("Hit") || pt.getSimpleName().contains("BlockHit")) hasHit = true;
                        }
                        if (hasPlayer && hasHand && hasHit) {
                            try { m.setAccessible(true); m.invoke(gameMode, player, mainHand, hitResult); placed = true; ReflectionHelper.dbg("doPlaceBlock: OK via sig-match " + m.getName()); break; }
                            catch (Exception ex) { ReflectionHelper.dbg("doPlaceBlock: sig-match " + m.getName() + " failed: " + ex.getMessage()); }
                        }
                    }
                }
            }
            if (!placed) return "{\"error\":\"no useItemOn method found on gameMode\",\"methods\":\"" + methodsInfo + "\"}";
            return "{\"placed\":true,\"at\":[" + bx + "," + by + "," + bz + "]}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String openChatScreen(Object mc) {
        try {
            Class<?> chatScreenClass = null;
            for (String cn : new String[]{
                "net.minecraft.client.gui.screens.ChatScreen",
                "net.minecraft.client.gui.screen.ChatScreen",
                "net.minecraft.client.gui.screen.inventory.ChatScreen",
                "net.minecraft.client.gui.GuiChat"
            }) {
                try { chatScreenClass = Class.forName(cn); break; } catch (ClassNotFoundException ignored) {}
            }
            if (chatScreenClass == null) return "{\"error\":\"ChatScreen class not found\"}";
            Object chatScreen = null;
            try { chatScreen = chatScreenClass.getConstructor(String.class).newInstance(""); }
            catch (Exception ignored) {
                try { chatScreen = chatScreenClass.getConstructor().newInstance(); }
                catch (Exception ignored2) {}
            }
            if (chatScreen == null) return "{\"error\":\"ChatScreen instantiation failed\"}";
            for (Method m : ReflectionCache.getAllMethods(mc.getClass())) {
                String mn = m.getName();
                if ((mn.equals("setScreen") || mn.equals("displayGuiScreen") || mn.equals("func_147108_a")) && m.getParameterCount() == 1) {
                    m.setAccessible(true);
                    m.invoke(mc, chatScreen);
                    return "{\"chat_opened\":true,\"screen\":\"" + chatScreenClass.getSimpleName() + "\"}";
                }
            }
            return "{\"error\":\"setScreen method not found\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String closeScreen(Object mc) {
        try {
            Object screen = ReflectionCache.getCurrentScreen(mc);
            if (screen == null) return "{\"error\":\"no screen to close\"}";
            String sn = screen.getClass().getSimpleName();
            boolean keyPressedWorked = false;
            int[] escapeKeys = {256, 1};
            for (int escapeKey : escapeKeys) {
                for (Method m : ReflectionCache.getAllMethods(screen.getClass())) {
                    String n = m.getName();
                    if ((n.equals("keyPressed") || n.contains("keyPressed"))
                            && m.getParameterCount() >= 3) {
                        try {
                            m.setAccessible(true);
                            Object[] args = new Object[m.getParameterCount()];
                            args[0] = escapeKey;
                            args[1] = 0;
                            args[2] = 0;
                            for (int i = 3; i < args.length; i++) args[i] = 0;
                            Object result = m.invoke(screen, args);
                            if (Boolean.TRUE.equals(result)) keyPressedWorked = true;
                        } catch (Exception ignored) {}
                    }
                }
                if (keyPressedWorked) break;
            }
            if (!keyPressedWorked) {
                String[] methodNames = {"setScreen", "displayGuiScreen", "setGuiScreen", "openScreen"};
                for (String methodName : methodNames) {
                    for (Method m : ReflectionCache.getAllMethods(mc.getClass())) {
                        if (m.getName().equals(methodName) && m.getParameterCount() == 1) {
                            m.setAccessible(true);
                            m.invoke(mc, (Object) null);
                            return "{\"screen_closed\":true,\"method\":\"" + methodName + "(null)\",\"was\":\"" + sn + "\"}";
                        }
                    }
                }
            }
            return "{\"screen_closed\":true,\"method\":\"keyPressed(ESCAPE)\",\"was\":\"" + sn + "\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String switchTab(Object mc, int tabIndex) {
        try {
            Object screen = ReflectionCache.getCurrentScreen(mc);
            if (screen == null) return "{\"error\":\"no screen\"}";
            Object tabBar = null;
            for (Field f : ReflectionCache.getAllFields(screen.getClass())) {
                String fn = f.getName();
                if (fn.equals("tabNavigationBar") || fn.contains("tabNavigation") || fn.contains("tabBar")) {
                    try {
                        f.setAccessible(true);
                        tabBar = f.get(screen);
                        if (tabBar != null) break;
                    } catch (Exception ignored) {}
                }
            }
            if (tabBar == null) {
                for (Field f : ReflectionCache.getAllFields(screen.getClass())) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(screen);
                        if (val != null && val.getClass().getSimpleName().equals("TabNavigationBar")) {
                            tabBar = val;
                            break;
                        }
                    } catch (Exception ignored) {}
                }
            }
            if (tabBar == null) return "{\"error\":\"no TabNavigationBar found on " + screen.getClass().getSimpleName() + "\"}";
            for (Method m : ReflectionCache.getAllMethods(tabBar.getClass())) {
                if (m.getName().equals("selectTab") && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                        m.invoke(tabBar, tabIndex, true);
                        return "{\"switched\":true,\"tab\":" + tabIndex + ",\"screen\":\"" + screen.getClass().getSimpleName() + "\"}";
                    } catch (Exception e) {
                        return "{\"error\":\"selectTab failed: " + e.getMessage() + "\"}";
                    }
                }
            }
            for (Method m : ReflectionCache.getAllMethods(tabBar.getClass())) {
                String mn = m.getName();
                if (mn.contains("select") && m.getParameterCount() >= 1) {
                    try {
                        m.setAccessible(true);
                        if (m.getParameterCount() == 1) {
                            m.invoke(tabBar, tabIndex);
                        } else if (m.getParameterCount() == 2) {
                            m.invoke(tabBar, tabIndex, true);
                        }
                        return "{\"switched\":true,\"tab\":" + tabIndex + ",\"via\":\"" + mn + "\"}";
                    } catch (Exception e) {
                        return "{\"error\":\"" + mn + " failed: " + e.getMessage() + "\"}";
                    }
                }
            }
            return "{\"error\":\"no selectTab method on TabNavigationBar\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String setGameMode(Object mc, String gameMode) {
        try {
            Object server = null;
            for (Method m : ReflectionCache.getAllMethods(mc.getClass())) {
                String mn = m.getName();
                if ((mn.equals("getSingleplayerServer") || mn.equals("getServer") || mn.equals("getIntegratedServer")) && m.getParameterCount() == 0) {
                    try { server = m.invoke(mc); if (server != null) break; } catch (Exception ignored) {}
                }
            }
            if (server == null) {
                for (Field f : ReflectionCache.getAllFields(mc.getClass())) {
                    String n = f.getName();
                    if (n.equals("singleplayerServer") || n.contains("integratedServer")) {
                        try { f.setAccessible(true); server = f.get(mc); if (server != null) break; } catch (Exception ignored) {}
                    }
                }
            }
            if (server == null) return "{\"error\":\"no singleplayer server\"}";
            for (Method m : ReflectionCache.getAllMethods(server.getClass())) {
                if (m.getName().equals("getPlayerList") && m.getParameterCount() == 0) {
                    Object playerList = m.invoke(server);
                    for (Method pm : ReflectionCache.getAllMethods(playerList.getClass())) {
                        if ((pm.getName().equals("getPlayers") || pm.getName().equals("getPlayerList"))
                                && pm.getParameterCount() == 0 && java.util.List.class.isAssignableFrom(pm.getReturnType())) {
                            java.util.List<?> players = (java.util.List<?>) pm.invoke(playerList);
                            if (!players.isEmpty()) {
                                Object serverPlayer = players.get(0);
                                for (Method sm : ReflectionCache.getAllMethods(serverPlayer.getClass())) {
                                    if (sm.getName().equals("setGameMode") || sm.getName().equals("setGameType") || sm.getName().equals("setPlayerMode")) {
                                        if (sm.getParameterCount() == 1) {
                                            Class<?> pt = sm.getParameterTypes()[0];
                                            for (String gtClass : new String[]{
                                                "net.minecraft.world.level.GameType",
                                                "net.minecraft.world.GameType",
                                                "net.minecraft.server.level.GameType",
                                                "net.minecraft.world.WorldSettings$GameType"
                                            }) {
                                                try {
                                                    Class<?> gc = Class.forName(gtClass);
                                                    for (Object e : gc.getEnumConstants()) {
                                                        String en = ((Enum<?>) e).name().toUpperCase();
                                                        if (en.equals(gameMode.toUpperCase()) || en.startsWith(gameMode.toUpperCase().substring(0, 3))) {
                                                            sm.setAccessible(true);
                                                            sm.invoke(serverPlayer, e);
                                                            return "{\"gamemode_set\":true,\"mode\":\"" + en + "\"}";
                                                        }
                                                    }
                                                } catch (ClassNotFoundException ignored) {}
                                            }
                                            int modeId = gameMode.toLowerCase().equals("creative") ? 1 :
                                                         gameMode.toLowerCase().equals("adventure") ? 2 :
                                                         gameMode.toLowerCase().equals("spectator") ? 3 : 0;
                                            if (pt == int.class) {
                                                sm.setAccessible(true);
                                                sm.invoke(serverPlayer, modeId);
                                                return "{\"gamemode_set\":true,\"mode_id\":" + modeId + "}";
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            for (Method m : ReflectionCache.getAllMethods(server.getClass())) {
                if ((m.getName().equals("execute") || m.getName().equals("runCommand"))
                        && m.getParameterCount() >= 1 && m.getParameterTypes()[0] == String.class) {
                    try {
                        m.setAccessible(true);
                        Object result = m.invoke(server, "gamemode " + gameMode + " Player");
                        return "{\"gamemode_set\":true,\"via\":\"server.execute\"}";
                    } catch (Exception ignored) {}
                }
            }
            for (Method m : ReflectionCache.getAllMethods(server.getClass())) {
                if (m.getName().equals("getCommands") && m.getParameterCount() == 0) {
                    Object commands = m.invoke(server);
                    for (Method cm : ReflectionCache.getAllMethods(commands.getClass())) {
                        if ((cm.getName().equals("performPrefixedCommand") || cm.getName().equals("execute"))
                                && cm.getParameterCount() >= 2) {
                            try {
                                Class<?>[] pts = cm.getParameterTypes();
                                Object source = null;
                                for (Method sm : ReflectionCache.getAllMethods(server.getClass())) {
                                    if (sm.getName().equals("createCommandSourceStack") && sm.getParameterCount() == 0) {
                                        source = sm.invoke(server); break;
                                    }
                                }
                                if (source != null) {
                                    cm.setAccessible(true);
                                    cm.invoke(commands, source, "gamemode " + gameMode);
                                    return "{\"gamemode_set\":true,\"via\":\"commands.performPrefixed\"}";
                                }
                            } catch (Exception e) { ReflectionHelper.dbg("setGameMode cmd fail: " + e.getMessage()); }
                        }
                    }
                }
            }
            return "{\"error\":\"could not set gamemode\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String openPauseMenu(Object mc) {
        try {
            for (Method m : ReflectionCache.getAllMethods(mc.getClass())) {
                String mn = m.getName();
                if (m.getParameterCount() == 0 && (mn.equals("pauseGame") || mn.equals("func_147108_a"))) {
                    try {
                        m.setAccessible(true);
                        m.invoke(mc);
                        return "{\"paused\":true,\"method\":\"" + mn + "\"}";
                    } catch (Exception ignored) {}
                }
            }
            for (Method m : ReflectionCache.getAllMethods(mc.getClass())) {
                String mn = m.getName();
                if (m.getParameterCount() == 1 && mn.equals("pause")) {
                    try {
                        m.setAccessible(true);
                        m.invoke(mc, false);
                        return "{\"paused\":true,\"method\":\"" + mn + "\"}";
                    } catch (Exception ignored) {}
                }
            }
            Class<?> screenClass = null;
            for (String cn : new String[]{
                "net.minecraft.client.gui.screens.PauseScreen",
                "net.minecraft.client.gui.screens.GameMenuScreen",
                "net.minecraft.client.gui.screen.IngameMenuScreen",
                "net.minecraft.client.gui.GuiIngameMenu"
            }) {
                try { screenClass = Class.forName(cn); break; } catch (ClassNotFoundException ignored) {}
            }
            if (screenClass != null) {
                Object screen = null;
                try { screen = screenClass.getConstructor(boolean.class).newInstance(true); }
                catch (Exception ignored) {
                    try { screen = screenClass.getConstructor().newInstance(); }
                    catch (Exception ignored2) {}
                }
            if (screen != null) {
                for (Method m : ReflectionCache.getAllMethods(mc.getClass())) {
                        String mn = m.getName();
                        if ((mn.equals("setScreen") || mn.equals("displayGuiScreen") || mn.equals("func_147108_a")) && m.getParameterCount() == 1) {
                            try {
                                m.setAccessible(true);
                                m.invoke(mc, screen);
                                return "{\"paused\":true,\"method\":\"setScreen(" + screenClass.getSimpleName() + ")\"}";
                            } catch (Exception ignored3) {}
                        }
                    }
                }
            }
            return "{\"error\":\"no pause method found\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private static Object getPlayer(Object mc) throws Exception {
        return ReflectionCache.getPlayer(mc);
    }

    private static Object getLevel(Object mc) throws Exception {
        return ReflectionCache.getLevel(mc);
    }

    private static Object getPlayerLevel(Object player) throws Exception {
        return ReflectionCache.getPlayerLevel(player);
    }

    private static Object fieldOrNull(Object obj, String fieldName) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) { return null; }
    }

    private static String invokeString(Object obj, String methodName) {
        try {
            Object r = obj.getClass().getMethod(methodName).invoke(obj);
            return r != null ? r.toString() : "";
        } catch (Exception e) { return ""; }
    }

    private static Object invokeOrNull(Object obj, String methodName) {
        try { return obj.getClass().getMethod(methodName).invoke(obj); }
        catch (Exception e) { return null; }
    }

    private static double getDouble(Object obj, String... names) throws Exception {
        for (String name : names) {
            try {
                for (Method m : ReflectionCache.getAllMethods(obj.getClass())) {
                    if (m.getName().equals(name) && m.getParameterCount() == 0 && (m.getReturnType() == double.class || m.getReturnType() == float.class)) {
                        m.setAccessible(true);
                        Object v = m.invoke(obj);
                        return ((Number) v).doubleValue();
                    }
                }
            } catch (Exception ignored) {}
            try {
                for (Field f : ReflectionCache.getAllFields(obj.getClass())) {
                    if (f.getName().equals(name) && (f.getType() == double.class || f.getType() == float.class)) {
                        f.setAccessible(true);
                        return f.getDouble(obj);
                    }
                }
            } catch (Exception ignored) {}
        }
        return 0.0;
    }

    private static void setRotField(Object player, String fieldName, float value) throws Exception {
        for (Field f : ReflectionCache.getAllFields(player.getClass())) {
            if (f.getName().equals(fieldName) || f.getName().contains(srgSafe(fieldName))) {
                if (f.getType() == float.class || f.getType() == double.class) {
                    f.setAccessible(true);
                    if (f.getType() == double.class) f.setDouble(player, (double)value);
                    else f.setFloat(player, value);
                    return;
                }
            }
        }
    }

    private static float getRotField(Object player, String... fieldNames) throws Exception {
        for (String fieldName : fieldNames) {
            for (Field f : ReflectionCache.getAllFields(player.getClass())) {
                if (f.getName().equals(fieldName) || f.getName().contains(srgSafe(fieldName))) {
                    if (f.getType() == float.class || f.getType() == double.class) {
                        f.setAccessible(true);
                        if (f.getType() == double.class) return (float)f.getDouble(player);
                        return f.getFloat(player);
                    }
                }
            }
        }
        return 0f;
    }

    private static String srgSafe(String name) {
        switch (name) {
            case "yRot": return "146127";
            case "xRot": return "146118";
            case "yRotO": return "146128";
            case "xRotO": return "146119";
            case "yawRot": return "36076";
            case "oYRot": return "36080";
            case "oXRot": return "36081";
            case "rotationYaw": return "779";
            case "rotationPitch": return "781";
            case "prevRotationYaw": return "780";
            case "prevRotationPitch": return "782";
            default: return name;
        }
    }
}

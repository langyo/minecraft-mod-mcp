package xyz.langyo.minecraft.mcp.common;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class McpMessageHandler {

    private static final Gson GSON = McpProtocol.GSON;
    protected McpProtocol.MinecraftInput minecraftInput;
    private int reqId = 0;

    public McpMessageHandler() {}


    public void sendInit(Object wsClient) {
    }

    public void handleMessage(String raw, Object wsClient) {
        try {
            JsonObject msg = GSON.fromJson(raw, JsonObject.class);
            JsonElement methodEl = msg.get("method");
            if (methodEl == null) {
                return;
            }
            String method = methodEl.getAsString();
            java.util.Map<String, String> params = new java.util.LinkedHashMap<>();
            JsonObject paramsObj = msg.getAsJsonObject("params");
            if (paramsObj != null) {
                for (java.util.Map.Entry<String, JsonElement> e : paramsObj.entrySet()) {
                    params.put(e.getKey(), e.getValue().isJsonNull() ? "" : e.getValue().getAsString());
                }
            }
            Object result = dispatch(method, params, wsClient);
            sendResponse(method, result, wsClient, params.get("requestId"));
        } catch (Exception e) {
            System.err.println("[MCP-Handler] Error: " + e.getMessage());
        }
    }

    private static final java.util.Set<String> ALWAYS_ALLOWED;
    static {
        java.util.Set<String> s = new java.util.HashSet<>();
        s.add("ping"); s.add("screenshot"); s.add("get_player_info"); s.add("get_world_info"); s.add("debug_fields");
        s.add("get_screen_buttons"); s.add("enumerate_widgets"); s.add("overlay_click");
        s.add("enter_control_mode"); s.add("exit_control_mode");
        s.add("set_gamemode"); s.add("release_mouse"); s.add("pause_game"); s.add("close_screen"); s.add("open_chat");
        ALWAYS_ALLOWED = java.util.Collections.unmodifiableSet(s);
    }

    protected Object dispatch(String method, java.util.Map<String, String> params, Object wsClient) {
        McpProtocol.MinecraftInput inp = minecraftInput;
        if (inp == null) {
            return "error: no input handler bound";
        }
        if (method.equals("ping")) return "pong";
        if (method.equals("screenshot")) return handleScreenshot();
        if (method.equals("overlay_click")) return handleOverlayClick(params);
        if (method.equals("enter_control_mode") || method.equals("exit_control_mode")) return handleControlMode(params, method);

        boolean inControl = ReflectionHelper.isMcpControlMode();
        if (!inControl && !ALWAYS_ALLOWED.contains(method)) {
            return "{\"error\":\"not in control mode\",\"hint\":\"Enter control mode via ESC > MCP Take Over\"}";
        }
        if (method.equals("click")) return handleClick(params);
        if (method.equals("press_key")) return handlePressKey(params);
        if (method.equals("type_text")) return handleTypeText(params);
        if (method.equals("scroll")) return handleScroll(params);
        if (method.equals("scroll_at")) return handleScrollAt(params);
        if (method.equals("direct_scroll")) return handleDirectScroll(params);
        if (method.equals("select_list_item")) return handleSelectListItem(params);
        if (method.equals("mouse_drag") || method.equals("drag")) return handleMouseDrag(params);
        if (method.equals("hotkey")) return handleHotkey(params);
        if (method.equals("execute_command")) return handleExecuteCommand(params);
        if (method.equals("get_player_info")) return handleGetPlayerInfo();
        if (method.equals("get_world_info")) return handleGetWorldInfo();
        if (method.equals("debug_fields")) return handleDebugFields();
        if (method.equals("get_screen_buttons")) return handleGetScreenButtons();
        if (method.equals("click_button_id")) return handleClickButtonId(params);
        if (method.equals("click_button_index")) return handleClickButtonIndex(params);
        if (method.equals("enumerate_widgets")) return handleEnumerateWidgets(params);
        if (method.equals("call_screen_method")) return handleCallScreenMethod(params);
        if (method.equals("paste_text")) return handlePasteText(params);
        if (method.equals("set_view_angle")) return handleSetViewAngle(params);
        if (method.equals("look_delta")) return handleLookDelta(params);
        if (method.equals("right_click")) return handleRightClick();
        if (method.equals("use_item")) return handleUseItem();
        if (method.equals("place_block")) return handlePlaceBlock();
        if (method.equals("pause_game")) return handlePauseGame();
        if (method.equals("open_chat")) return handleOpenChat();
        if (method.equals("close_screen")) return handleCloseScreen();
        if (method.equals("release_mouse")) return handleReleaseMouse();
        if (method.equals("set_gamemode")) return handleSetGameMode(params);
        if (method.equals("switch_tab")) return handleSwitchTab(params);

        return "unknown: " + method;
    }

    private void sendResponse(String method, Object result, Object wsClient, String requestId) {
    }

    protected Object handleOverlayClick(java.util.Map<String, String> params) {
        int x = 0, y = 0;
        try { x = Integer.parseInt(params.getOrDefault("x", "0")); } catch (Exception ignored) {}
        try { y = Integer.parseInt(params.getOrDefault("y", "0")); } catch (Exception ignored) {}
        final int fx = x, fy = y;
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] result = {""};
        ReflectedInputHandler.executeOnRenderThread(() -> {
            try {
                Object mc = ReflectionHelper.getMinecraftInstance();
                result[0] = ReflectionHelper.handleOverlayClick(fx, fy, mc);
            } catch (Exception e) { result[0] = "{\"error\":\"" + e.getMessage() + "\"}"; }
            latch.countDown();
        });
        try { latch.await(5, TimeUnit.SECONDS); } catch (Exception ignored) {}
        return result[0];
    }

    protected Object handleControlMode(java.util.Map<String, String> params, String method) {
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] result = {""};
        ReflectedInputHandler.executeOnRenderThread(() -> {
            try {
                Object mc = ReflectionHelper.getMinecraftInstance();
                if (method.equals("enter_control_mode")) {
                    result[0] = ReflectionHelper.enterMcpControlMode(mc);
                } else {
                    result[0] = ReflectionHelper.exitMcpControlMode(mc);
                }
            } catch (Exception e) { result[0] = "{\"error\":\"" + e.getMessage() + "\"}"; }
            latch.countDown();
        });
        try { latch.await(5, TimeUnit.SECONDS); } catch (Exception ignored) {}
        return result[0];
    }

    protected Object handleScreenshot() {
        try {
            byte[] bytes = minecraftInput != null ? minecraftInput.screenshot() : null;
            if (bytes != null) {
                return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(bytes);
            }
            return "error: screenshot returned null";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    protected Object handleClick(java.util.Map<String, String> p) {
        String xStr = p.get("x");
        String yStr = p.get("y");
        if (xStr == null || yStr == null) return "missing x or y";
        int x = Integer.parseInt(xStr);
        int y = Integer.parseInt(yStr);
        String button = p.getOrDefault("button", "left");
        if (minecraftInput != null) {
            minecraftInput.click(x, y, button);
            return ReflectedInputHandler.getLastClickResult();
        }
        return "{\"error\":\"no handler\"}";
    }

    protected Object handlePressKey(java.util.Map<String, String> p) {
        String key = p.get("key");
        if (key == null) return "{\"error\":\"missing key\"}";
        float hold = 0f;
        String holdStr = p.get("hold_seconds");
        if (holdStr != null) {
            try { hold = Float.parseFloat(holdStr); } catch (NumberFormatException ignored) {}
        }
        if (minecraftInput != null) minecraftInput.pressKey(key, hold);
        return "{\"pressed\":\"" + key + "\"}";
    }

    protected Object handleTypeText(java.util.Map<String, String> p) {
        String text = p.get("text");
        if (text == null) return "missing text";
        if (minecraftInput != null) {
            minecraftInput.typeText(text);
            if ("true".equals(p.get("press_enter"))) {
                minecraftInput.pressKey("Enter", 0f);
            }
        }
        return text;
    }

    protected Object handleScroll(java.util.Map<String, String> p) {
        int clicks = 1;
        String clicksStr = p.get("clicks");
        if (clicksStr != null) {
            try { clicks = Integer.parseInt(clicksStr); } catch (NumberFormatException ignored) {}
        }
        if (minecraftInput != null) minecraftInput.scroll(clicks);
        return "{\"scrolls\":" + clicks + "}";
    }

    protected Object handleScrollAt(java.util.Map<String, String> p) {
        int x = 0, y = 0;
        try { x = Integer.parseInt(p.getOrDefault("x", "0")); } catch (Exception ignored) {}
        try { y = Integer.parseInt(p.getOrDefault("y", "0")); } catch (Exception ignored) {}
        int clicks = 1;
        String clicksStr = p.get("clicks");
        if (clicksStr != null) {
            try { clicks = Integer.parseInt(clicksStr); } catch (NumberFormatException ignored) {}
        }
        if (minecraftInput != null) minecraftInput.scrollAt(x, y, clicks);
        return "{\"scroll_at\":true,\"x\":"+x+",\"y\":"+y+",\"clicks\":"+clicks+"}";
    }

    protected Object handleSelectListItem(java.util.Map<String, String> p) {
        int index = 0;
        try { index = Integer.parseInt(p.getOrDefault("index", "0")); } catch (Exception ignored) {}
        return ReflectionHelper.selectListItem(ReflectionHelper.getMinecraftInstance(), index);
    }

    protected Object handleDirectScroll(java.util.Map<String, String> p) {
        double mouseX = -1, mouseY = -1, delta = 1.0;
        try { mouseX = Double.parseDouble(p.getOrDefault("mouseX", "-1")); } catch (Exception ignored) {}
        try { mouseY = Double.parseDouble(p.getOrDefault("mouseY", "-1")); } catch (Exception ignored) {}
        try { delta = Double.parseDouble(p.getOrDefault("delta", "1")); } catch (Exception ignored) {}
        return ReflectionHelper.directScroll(ReflectionHelper.getMinecraftInstance(), mouseX, mouseY, delta);
    }

    protected Object handleMouseDrag(java.util.Map<String, String> p) {
        int x1 = 0, y1 = 0, x2 = 0, y2 = 0;
        try { x1 = Integer.parseInt(p.getOrDefault("x1", p.getOrDefault("x_start", "0"))); } catch (Exception ignored) {}
        try { y1 = Integer.parseInt(p.getOrDefault("y1", p.getOrDefault("y_start", "0"))); } catch (Exception ignored) {}
        try { x2 = Integer.parseInt(p.getOrDefault("x2", p.getOrDefault("x_end", "0"))); } catch (Exception ignored) {}
        try { y2 = Integer.parseInt(p.getOrDefault("y2", p.getOrDefault("y_end", "0"))); } catch (Exception ignored) {}
        String button = p.getOrDefault("button", "left");
        if (minecraftInput != null) minecraftInput.mouseDrag(x1, y1, x2, y2, button);
        return "{\"drag\":true,\"from\":[" + x1 + "," + y1 + "],\"to\":[" + x2 + "," + y2 + "]}";
    }

    protected Object handleHotkey(java.util.Map<String, String> p) {
        String keysStr = p.get("keys");
        if (keysStr == null) return "missing keys";
        String[] keys = keysStr.split(",");
        for (int i = 0; i < keys.length; i++) keys[i] = keys[i].trim();
        if (minecraftInput != null) minecraftInput.hotkey(keys);
        return keysStr;
    }

    protected Object handleExecuteCommand(java.util.Map<String, String> p) {
        String cmd = p.get("command");
        if (cmd == null) return "missing command";
        if (minecraftInput != null) return minecraftInput.executeCommand(cmd);
        return "error: no input handler";
    }

    protected Object handleGetPlayerInfo() {
        if (minecraftInput != null) return minecraftInput.getPlayerInfo();
        return "no player";
    }

    protected Object handleGetWorldInfo() {
        if (minecraftInput != null) return minecraftInput.getWorldInfo();
        return "no world";
    }

    protected Object handleDebugFields() {
        if (minecraftInput != null) return minecraftInput.debugFields();
        return "no input handler";
    }

    protected Object handleGetScreenButtons() {
        if (minecraftInput != null) return ReflectionHelper.getScreenButtons(ReflectionHelper.getMinecraftInstance());
        return "no input handler";
    }

    protected Object handleClickButtonId(java.util.Map<String, String> params) {
        if (minecraftInput != null) {
            int id = 0;
            try { id = Integer.parseInt(params.getOrDefault("id", params.get("button_id"))); } catch (Exception ignored) {}
            final int fid = id;
            final CountDownLatch latch = new CountDownLatch(1);
            final String[] result = {""};
            ReflectedInputHandler.executeOnRenderThread(() -> {
                try { result[0] = ReflectionHelper.clickButtonById(ReflectionHelper.getMinecraftInstance(), fid); }
                catch (Exception e) { result[0] = "{\"error\":\"" + e.getMessage() + "\"}"; }
                latch.countDown();
            });
            try { latch.await(5, TimeUnit.SECONDS); } catch (Exception ignored) {}
            return result[0];
        }
        return "no input handler";
    }

    protected Object handleClickButtonIndex(java.util.Map<String, String> params) {
        if (minecraftInput != null) {
            int idx = 0;
            try { idx = Integer.parseInt(params.getOrDefault("index", params.get("button_index"))); } catch (Exception ignored) {}
            final int fidx = idx;
            final CountDownLatch latch = new CountDownLatch(1);
            final String[] result = {""};
            ReflectedInputHandler.executeOnRenderThread(() -> {
                try { result[0] = ReflectionHelper.clickButtonByIndex(ReflectionHelper.getMinecraftInstance(), fidx); }
                catch (Exception e) { result[0] = "{\"error\":\"" + e.getMessage() + "\"}"; }
                latch.countDown();
            });
            try { latch.await(5, TimeUnit.SECONDS); } catch (Exception ignored) {}
            return result[0];
        }
        return "no input handler";
    }

    protected Object handleEnumerateWidgets(java.util.Map<String, String> params) {
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] result = {""};
        ReflectedInputHandler.executeOnRenderThread(() -> {
            try { result[0] = ReflectionHelper.enumerateWidgets(ReflectionHelper.getMinecraftInstance()); }
            catch (Exception e) { result[0] = "{\"error\":\"" + e.getMessage() + "\"}"; }
            latch.countDown();
        });
        try { latch.await(5, TimeUnit.SECONDS); } catch (Exception ignored) {}
        return result[0];
    }

    protected Object handleCallScreenMethod(java.util.Map<String, String> params) {
        String methodName = params.get("method");
        if (methodName == null || methodName.isEmpty()) methodName = params.get("methodName");
        if (methodName == null) return "missing method";
        final String fm = methodName;
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] result = {""};
        ReflectedInputHandler.executeOnRenderThread(() -> {
            try { result[0] = ReflectionHelper.callScreenMethod(ReflectionHelper.getMinecraftInstance(), fm); }
            catch (Exception e) { result[0] = "{\"error\":\"" + e.getMessage() + "\"}"; }
            latch.countDown();
        });
        try { latch.await(5, TimeUnit.SECONDS); } catch (Exception ignored) {}
        return result[0];
    }

    protected Object handlePasteText(java.util.Map<String, String> p) {
        String text = p.get("text");
        if (text == null) return "missing text";
        if (minecraftInput != null) {
            minecraftInput.pasteText(text);
            if ("true".equals(p.get("press_enter"))) {
                minecraftInput.pressKey("Enter", 0f);
            }
        }
        return text;
    }

    protected Object handleSetViewAngle(java.util.Map<String, String> p) {
        float yaw = 0f, pitch = 0f;
        try { yaw = Float.parseFloat(p.get("yaw")); } catch (Exception ignored) {}
        try { pitch = Float.parseFloat(p.get("pitch")); } catch (Exception ignored) {}
        if (minecraftInput != null) minecraftInput.setViewAngle(yaw, pitch);
        return "yaw=" + yaw + ",pitch=" + pitch;
    }

    protected Object handleLookDelta(java.util.Map<String, String> p) {
        float deltaYaw = 0f, deltaPitch = 0f;
        try { deltaYaw = Float.parseFloat(p.get("delta_yaw")); } catch (Exception ignored) {}
        try { deltaPitch = Float.parseFloat(p.get("delta_pitch")); } catch (Exception ignored) {}
        if (minecraftInput != null) minecraftInput.lookDelta(deltaYaw, deltaPitch);
        return "deltaYaw=" + deltaYaw + ",deltaPitch=" + deltaPitch;
    }

    protected Object handleRightClick() {
        if (minecraftInput != null) minecraftInput.rightClick();
        return "right_clicked";
    }

    protected Object handleUseItem() {
        if (minecraftInput != null) return ReflectionHelper.doUseItem(ReflectionHelper.getMinecraftInstance());
        return "no input handler";
    }

    protected Object handlePlaceBlock() {
        if (minecraftInput != null) return ReflectionHelper.doPlaceBlock(ReflectionHelper.getMinecraftInstance());
        return "no input handler";
    }

    protected Object handlePauseGame() {
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] result = {""};
        ReflectedInputHandler.executeOnRenderThread(() -> {
            try { result[0] = ReflectionHelper.openPauseMenu(ReflectionHelper.getMinecraftInstance()); }
            catch (Exception e) { result[0] = "{\"error\":\"" + e.getMessage() + "\"}"; }
            latch.countDown();
        });
        try { latch.await(5, TimeUnit.SECONDS); } catch (Exception ignored) {}
        return result[0];
    }

    protected Object handleOpenChat() {
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] result = {""};
        ReflectedInputHandler.executeOnRenderThread(() -> {
            try { result[0] = ReflectionHelper.openChatScreen(ReflectionHelper.getMinecraftInstance()); }
            catch (Exception e) { result[0] = "{\"error\":\"" + e.getMessage() + "\"}"; }
            latch.countDown();
        });
        try { latch.await(5, TimeUnit.SECONDS); } catch (Exception ignored) {}
        return result[0];
    }

    protected Object handleCloseScreen() {
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] result = {""};
        ReflectedInputHandler.executeOnRenderThread(() -> {
            try { result[0] = ReflectionHelper.closeScreen(ReflectionHelper.getMinecraftInstance()); }
            catch (Exception e) { result[0] = "{\"error\":\"" + e.getMessage() + "\"}"; }
            latch.countDown();
        });
        try { latch.await(5, TimeUnit.SECONDS); } catch (Exception ignored) {}
        return result[0];
    }

    protected Object handleReleaseMouse() {
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] result = {""};
        ReflectedInputHandler.executeOnRenderThread(() -> {
            try { result[0] = ReflectionHelper.releaseMouse(ReflectionHelper.getMinecraftInstance()); }
            catch (Exception e) { result[0] = "{\"error\":\"" + e.getMessage() + "\"}"; }
            latch.countDown();
        });
        try { latch.await(5, TimeUnit.SECONDS); } catch (Exception ignored) {}
        return result[0];
    }

    protected Object handleSetGameMode(java.util.Map<String, String> params) {
        String mode = params.getOrDefault("mode", "creative");
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] result = {""};
        ReflectedInputHandler.executeOnRenderThread(() -> {
            try { result[0] = ReflectionHelper.setGameMode(ReflectionHelper.getMinecraftInstance(), mode); }
            catch (Exception e) { result[0] = "{\"error\":\"" + e.getMessage() + "\"}"; }
            latch.countDown();
        });
        try { latch.await(5, TimeUnit.SECONDS); } catch (Exception ignored) {}
        return result[0];
    }

    protected Object handleSwitchTab(java.util.Map<String, String> params) {
        int tabIndex = 0;
        try { tabIndex = Integer.parseInt(params.getOrDefault("index", params.get("tab_index"))); } catch (Exception ignored) {}
        final int fidx = tabIndex;
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] result = {""};
        ReflectedInputHandler.executeOnRenderThread(() -> {
            try { result[0] = ReflectionHelper.switchTab(ReflectionHelper.getMinecraftInstance(), fidx); }
            catch (Exception e) { result[0] = "{\"error\":\"" + e.getMessage() + "\"}"; }
            latch.countDown();
        });
        try { latch.await(5, TimeUnit.SECONDS); } catch (Exception ignored) {}
        return result[0];
    }



}

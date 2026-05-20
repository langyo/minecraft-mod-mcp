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
        McpWebSocketClient ws = castClient(wsClient);
        if (ws != null && ws.isOpen()) {
            JsonObject init = new JsonObject();
            init.addProperty("jsonrpc", "2.0");
            init.addProperty("method", "initialize");
            init.add("params", new JsonObject());
            init.addProperty("id", 1);
            ws.send(GSON.toJson(init));
        }
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

    protected Object dispatch(String method, java.util.Map<String, String> params, Object wsClient) {
        McpProtocol.MinecraftInput inp = minecraftInput;
        if (inp == null) {
            return "error: no input handler bound";
        }
        if (method.equals("screenshot")) return handleScreenshot();
        if (method.equals("click")) return handleClick(params);
        if (method.equals("press_key")) return handlePressKey(params);
        if (method.equals("type_text")) return handleTypeText(params);
        if (method.equals("scroll")) return handleScroll(params);
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
        if (method.equals("enter_control_mode") || method.equals("exit_control_mode")) return handleControlMode(params, method);
        if (method.equals("paste_text")) return handlePasteText(params);
        if (method.equals("set_view_angle")) return handleSetViewAngle(params);
        if (method.equals("look_delta")) return handleLookDelta(params);
        if (method.equals("right_click")) return handleRightClick();
        if (method.equals("use_item")) return handleUseItem();
        if (method.equals("pause_game")) return handlePauseGame();
        if (method.equals("open_chat")) return handleOpenChat();
        if (method.equals("close_screen")) return handleCloseScreen();
        if (method.equals("release_mouse")) return handleReleaseMouse();
        if (method.equals("set_gamemode")) return handleSetGameMode(params);
        if (method.equals("switch_tab")) return handleSwitchTab(params);
        if (method.equals("ping")) return "pong";
        if (method.equals("win32_borderless")) return handleWin32Borderless();
        if (method.equals("win32_container")) return handleWin32Container();
        if (method.equals("win32_status")) return handleWin32Status();
        if (method.equals("overlay_show")) return handleOverlayShow(params);
        if (method.equals("overlay_hide")) return handleOverlayHide();
        if (method.equals("overlay_text")) return handleOverlayText(params);
        if (method.equals("mouse_hook_status")) return handleMouseHookStatus();
        if (method.equals("inject_click")) return handleInjectClick(params);
        if (method.equals("inject_key")) return handleInjectKey(params);
        if (method.equals("platform_status")) return handlePlatformStatus();
        if (method.equals("video_start")) return handleVideoStart();
        if (method.equals("video_stop")) return handleVideoStop();
        return "unknown: " + method;
    }

    private void sendResponse(String method, Object result, Object wsClient, String requestId) {
        McpWebSocketClient ws = castClient(wsClient);
        if (ws == null || !ws.isOpen()) return;
        JsonObject resp = new JsonObject();
        resp.addProperty("jsonrpc", "2.0");
        resp.add("result", GSON.toJsonTree(result));
        resp.addProperty("id", requestId != null ? requestId : String.valueOf(reqId++));
        ws.send(GSON.toJson(resp));
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
        if (key == null) return "missing key";
        float hold = 0f;
        String holdStr = p.get("hold_seconds");
        if (holdStr != null) {
            try { hold = Float.parseFloat(holdStr); } catch (NumberFormatException ignored) {}
        }
        if (minecraftInput != null) minecraftInput.pressKey(key, hold);
        return key;
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
        return clicks + " scrolls";
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

    protected Object handleControlMode(java.util.Map<String, String> params, String method) {
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] result = {""};
        ReflectedInputHandler.executeOnRenderThread(() -> {
            try {
                if ("enter_control_mode".equals(method))
                    result[0] = ReflectionHelper.enterMcpControlMode(ReflectionHelper.getMinecraftInstance());
                else
                    result[0] = ReflectionHelper.exitMcpControlMode(ReflectionHelper.getMinecraftInstance());
            } catch (Exception e) { result[0] = "{\"error\":\"" + e.getMessage() + "\"}"; }
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

    protected Object handleWin32Status() {
        return ReflectionHelper.getHookStatus();
    }

    protected Object handleWin32Container() {
        try {
            McpPlatformControl ctrl = McpControlFactory.get();
            Object mc = ReflectionHelper.getMinecraftInstance();
            Object window = mc.getClass().getMethod("getWindow").invoke(mc);
            final long glfwWin = (long) window.getClass().getMethod("getWindow").invoke(window);
            final java.util.concurrent.CountDownLatch hwndLatch = new java.util.concurrent.CountDownLatch(1);
            final Object[] hwndResult = {null};
            java.lang.reflect.Method exec = mc.getClass().getMethod("execute", Runnable.class);
            exec.invoke(mc, (Runnable) () -> {
                try {
                    hwndResult[0] = ctrl.resolveNativeWindowHandle(glfwWin);
                } catch (Exception e) { hwndResult[0] = null; }
                hwndLatch.countDown();
            });
            hwndLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
            if (hwndResult[0] == null) return "error: native handle not found";
            final long nativeHandle = (Long) hwndResult[0];
            if (nativeHandle == 0) return "error: native handle is 0";
            return ctrl.createContainer(nativeHandle);
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    protected Object handleWin32Borderless() {
        try {
            McpPlatformControl ctrl = McpControlFactory.get();
            Object mc = ReflectionHelper.getMinecraftInstance();
            Object window = mc.getClass().getMethod("getWindow").invoke(mc);
            final long glfwWin = (long) window.getClass().getMethod("getWindow").invoke(window);
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final Object[] result = {null};
            java.lang.reflect.Method exec = mc.getClass().getMethod("execute", Runnable.class);
            exec.invoke(mc, (Runnable) () -> {
                try {
                    long nativeHandle = ctrl.resolveNativeWindowHandle(glfwWin);
                    if (nativeHandle == 0) {
                        result[0] = "error: native handle is 0 (glfw=" + glfwWin + ")";
                    } else {
                        long oldStyle = ctrl.makeBorderless(nativeHandle);
                        result[0] = "borderless: hwnd=" + Long.toHexString(nativeHandle) + " oldStyle=" + Long.toHexString(oldStyle);
                    }
                } catch (Exception e) { result[0] = "error: " + e.getMessage(); }
                latch.countDown();
            });
            latch.await(8, java.util.concurrent.TimeUnit.SECONDS);
            return result[0] != null ? result[0] : "timeout on render thread";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    protected Object handleOverlayShow(java.util.Map<String, String> p) {
        String text = p.getOrDefault("text", "");
        int port = 9876;
        try { port = Integer.parseInt(p.getOrDefault("port", "9876")); } catch (Exception ignored) {}
        return ReflectionHelper.showMcpOverlay(text, port);
    }

    protected Object handleOverlayHide() {
        return ReflectionHelper.hideMcpOverlay();
    }

    protected Object handleOverlayText(java.util.Map<String, String> p) {
        String text = p.getOrDefault("text", "");
        return ReflectionHelper.updateMcpOverlayText(text);
    }

    protected Object handleMouseHookStatus() {
        return ReflectionHelper.getHookStatus();
    }

    protected Object handleInjectClick(java.util.Map<String, String> p) {
        int x = 0, y = 0;
        try { x = Integer.parseInt(p.getOrDefault("x", "0")); } catch (Exception ignored) {}
        try { y = Integer.parseInt(p.getOrDefault("y", "0")); } catch (Exception ignored) {}
        return ReflectionHelper.platformInjectClick(x, y);
    }

    protected Object handleInjectKey(java.util.Map<String, String> p) {
        int vk = 0;
        try { vk = Integer.parseInt(p.getOrDefault("vk", p.getOrDefault("key", "0"))); } catch (Exception ignored) {}
        return ReflectionHelper.platformInjectKey(vk);
    }

    protected Object handlePlatformStatus() {
        return ReflectionHelper.getHookStatus();
    }

    protected Object handleVideoStart() {
        ReflectionHelper.setVideoCaptureActive(true);
        return "{\"video_capture\":true}";
    }

    protected Object handleVideoStop() {
        ReflectionHelper.setVideoCaptureActive(false);
        return "{\"video_capture\":false}";
    }

    private static McpWebSocketClient castClient(Object wsClient) {
        if (wsClient instanceof McpWebSocketClient) return (McpWebSocketClient) wsClient;
        return null;
    }
}

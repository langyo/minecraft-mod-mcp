package com.mcbbs.mcp.common;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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
        if (method.equals("ping")) return "pong";
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
        if (minecraftInput != null) minecraftInput.click(x, y, button);
        return "clicked(" + x + "," + y + ")";
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

    private static McpWebSocketClient castClient(Object wsClient) {
        if (wsClient instanceof McpWebSocketClient) return (McpWebSocketClient) wsClient;
        return null;
    }
}

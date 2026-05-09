package xyz.langyo.minecraftmcp;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

public class McpWebSocketClient extends WebSocketClient {
    private static final Gson GSON = new Gson();
    private volatile String pendingResponse = null;
    private final Object responseLock = new Object();

    public McpWebSocketClient(String serverUrl) {
        super(URI.create(serverUrl));
        setReuseAddr(true);
        setConnectionLostTimeout(30);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        MinecraftMcpMod.LOGGER.info("[MCP WS] Connected to MCP server");
        sendStatus("connected", "WebSocket connection established");
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonObject cmd = GSON.fromJson(message, JsonObject.class);
            String action = cmd.has("action") ? cmd.get("action").getAsString() : "";
            JsonObject params = cmd.has("params") ? cmd.getAsJsonObject("params") : new JsonObject();
            String requestId = params.has("requestId") ? params.get("requestId").getAsString() : null;

            MinecraftMcpMod.LOGGER.debug("[MCP WS] Received action: {}", action);

            JsonObject result = McpMessageHandler.handle(action, params);

            if (requestId != null) {
                result.addProperty("requestId", requestId);
            }
            result.addProperty("success", true);

            synchronized (responseLock) {
                pendingResponse = GSON.toJson(result);
            }
        } catch (Exception e) {
            MinecraftMcpMod.LOGGER.error("[MCP WS] Message parse error: {}", e.getMessage());
            sendError(e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        MinecraftMcpMod.LOGGER.info("[MCP WS] Connection closed: {} - {}", code, reason);
    }

    @Override
    public void onError(Exception ex) {
        MinecraftMcpMod.LOGGER.error("[MCP WS] Error: {}", ex.getMessage());
    }

    public void processQueue() {
        synchronized (responseLock) {
            if (pendingResponse != null && isOpen()) {
                send(pendingResponse);
                pendingResponse = null;
            }
        }
    }

    private void sendStatus(String status, String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("status", status);
        obj.addProperty("message", message);
        obj.addProperty("success", true);
        if (isOpen()) send(GSON.toJson(obj));
    }

    private void sendError(String error) {
        JsonObject obj = new JsonObject();
        obj.addProperty("success", false);
        obj.addProperty("error", error != null ? error : "unknown error");
        if (isOpen()) send(GSON.toJson(obj));
    }
}

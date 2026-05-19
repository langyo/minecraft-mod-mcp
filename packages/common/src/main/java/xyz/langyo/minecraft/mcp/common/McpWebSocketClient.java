package xyz.langyo.minecraft.mcp.common;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;
import java.util.concurrent.ConcurrentLinkedQueue;

public class McpWebSocketClient extends WebSocketClient {

    private final ConcurrentLinkedQueue<String> messageQueue = new ConcurrentLinkedQueue<>();
    protected final McpMessageHandler handler;

    public McpWebSocketClient(String serverUrl, McpMessageHandler handler) {
        super(URI.create(serverUrl));
        this.handler = handler;
        this.setConnectionLostTimeout(0);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        try {
            java.lang.reflect.Method setMax = this.getClass().getMethod("setMaxTextMessageSize", int.class);
            setMax.invoke(this, 10 * 1024 * 1024);
        } catch (Exception ignored) {}
        System.out.println("[MCP-WS] Connected to MCP server");
        ReflectionHelper.setForceCursorNormal(true);
        handler.sendInit(this);
    }

    public void connectAsync() {
        new Thread(() -> {
            try { connect(); } catch (Exception e) { }
        }, "MCP-WS-Connect").start();
    }

    @Override
    public void onMessage(String message) {
        if (message != null) messageQueue.add(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("[MCP-WS] Disconnected: " + reason + " (code=" + code + ")");
        ReflectionHelper.setForceCursorNormal(false);
        new Thread(() -> {
            try { Thread.sleep(3000); reconnect(); }
            catch (Exception e) { System.err.println("[MCP-WS] Reconnect failed: " + e.getMessage()); }
        }, "MCP-WS-Reconnect").start();
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("[MCP-WS] Error: " + (ex != null ? ex.getMessage() : "null"));
    }

    @Override
    public void send(String msg) {
        if (isOpen()) super.send(msg);
    }

    public void handleMessages() {
        String msg;
        while ((msg = messageQueue.poll()) != null) {
            try { handler.handleMessage(msg, this); }
            catch (Exception e) { e.printStackTrace(); }
        }
    }

    private static volatile McpWebSocketClient instance;

    public static void setInstance(McpWebSocketClient c) { instance = c; }

    public static void sendVideoFrame(byte[] jpegData) {
        McpWebSocketClient ws = instance;
        if (ws != null && ws.isOpen()) {
            try {
                String b64 = java.util.Base64.getEncoder().encodeToString(jpegData);
                String msg = "{\"jsonrpc\":\"2.0\",\"method\":\"video_frame\",\"params\":{\"data\":\"" + b64 + "\",\"size\":" + jpegData.length + "}}";
                ws.send(msg);
            } catch (Exception e) {
                ReflectionHelper.dbg("video: send failed: " + e.getMessage());
            }
        }
    }
}

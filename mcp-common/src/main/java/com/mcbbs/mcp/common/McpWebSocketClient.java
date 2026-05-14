package com.mcbbs.mcp.common;

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

    public void connectAsync() {
        new Thread(() -> {
            try {
                connect();
            } catch (Exception e) {
                System.err.println("[MCP-WS] Connect failed: " + e.getMessage());
            }
        }, "MCP-WS-Connect").start();
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        System.out.println("[MCP-WS] Connected to MCP server");
        handler.sendInit(this);
    }

    @Override
    public void onMessage(String message) {
        if (message != null) {
            messageQueue.add(message);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("[MCP-WS] Disconnected: " + reason + " (code=" + code + ")");
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("[MCP-WS] Error: " + (ex != null ? ex.getMessage() : "null"));
    }

    @Override
    public void send(String msg) {
        if (isOpen()) {
            super.send(msg);
        }
    }

    public void handleMessages() {
        String msg;
        while ((msg = messageQueue.poll()) != null) {
            try {
                handler.handleMessage(msg, this);
            } catch (Exception e) {
                System.err.println("[MCP-WS] Handle error: " + e.getMessage());
            }
        }
    }
}

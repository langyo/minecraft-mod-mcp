package com.mcbbs.mcp.common

import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

open class McpWebSocketClient(
    serverUrl: String,
    protected val handler: McpMessageHandler
) : WebSocketClient(URI.create(serverUrl)) {
    protected var connected = false
        private set

    init { connectionLostTimeout = 0 }

    fun connectAsync() {
        Thread({
            try { connect() } catch (e: Exception) {
                System.err.println("[MCP-WS] Connect failed: ${e.message}")
            }
        }, "MCP-WS-Connect").start()
    }

    override fun onOpen(handshake: ServerHandshake?) {
        connected = true
        System.out.println("[MCP-WS] Connected to MCP server")
        handler.sendInit(this)
    }

    override fun onMessage(message: String?) {
        if (message != null) handler.handleMessage(message, this)
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        connected = false
        System.out.println("[MCP-WS] Disconnected: $reason (code=$code)")
    }

    override fun onError(ex: Exception?) {
        System.err.println("[MCP-WS] Error: ${ex?.message}")
    }

    override fun send(msg: String) {
        if (connected && isOpen) super.send(msg)
    }

    open fun handleMessages() {}
}

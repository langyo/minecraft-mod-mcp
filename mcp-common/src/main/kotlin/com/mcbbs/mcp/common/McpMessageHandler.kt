package com.mcbbs.mcp.common

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.java_websocket.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

open class McpMessageHandler @JvmOverloads constructor(
    private val gson: Gson = COMMON_GSON
) {
    @JvmField
    protected var minecraftInput: MinecraftInput? = null
    private val pending = ConcurrentHashMap<String, CompletableFuture<String>>()
    private var reqId = 0

    open fun setWebSocket(ws: WebSocket?) {}
    open fun sendInit(wsClient: Any? = null) {
        val ws = (wsClient as? McpWebSocketClient)
        if (ws != null && ws.isOpen) {
            val init = JsonObject()
            init.addProperty("jsonrpc", "2.0")
            init.addProperty("method", "initialize")
            init.add("params", JsonObject())
            init.addProperty("id", 1)
            ws.send(gson.toJson(init))
        }
    }

    open fun handleMessage(raw: String, wsClient: Any? = null) {
        try {
            val msg = gson.fromJson(raw, JsonObject::class.java)
            val method = msg.get("method")?.asString
            if (method == null) {
                val id = msg.get("id")?.asString
                if (id != null) pending.remove(id)?.complete(raw)
                return
            }
            val params = mutableMapOf<String, String>()
            msg.getAsJsonObject("params")?.entrySet()?.forEach { e ->
                params[e.key] = e.value?.asString ?: ""
            }
            val requestId = params.remove("requestId")
            val result = dispatch(method, params, wsClient)
            sendResponse(method, result, wsClient, requestId)
        } catch (e: Exception) {
            System.err.println("[MCP-Handler] Error: ${e.message}")
        }
    }

    protected open fun dispatch(method: String, params: Map<String, String>, wsClient: Any?): Any? {
        val inp = minecraftInput ?: return "error: no input handler bound"
        return when (method) {
            "screenshot" -> handleScreenshot()
            "click" -> handleClick(params)
            "press_key" -> handlePressKey(params)
            "type_text" -> handleTypeText(params)
            "scroll" -> handleScroll(params)
            "hotkey" -> handleHotkey(params)
            "execute_command" -> handleExecuteCommand(params)
            "get_player_info" -> handleGetPlayerInfo()
            "get_world_info" -> handleGetWorldInfo()
            "ping" -> "pong"
            else -> "unknown: $method"
        }
    }

    private fun sendResponse(method: String, result: Any?, wsClient: Any?, requestId: String? = null) {
        val ws = (wsClient as? McpWebSocketClient) ?: return
        if (!ws.isOpen) return
        val resp = JsonObject()
        resp.addProperty("jsonrpc", "2.0")
        resp.add("result", gson.toJsonTree(result))
        resp.addProperty("id", requestId ?: reqId++.toString())
        ws.send(gson.toJson(resp))
    }

    protected open fun handleScreenshot(): Any? = try {
        val bytes = minecraftInput?.screenshot()
        if (bytes != null) "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(bytes)
        else "error: screenshot returned null"
    } catch (e: Exception) { "error: ${e.message}" }

    protected open fun handleClick(p: Map<String, String>): Any? {
        val x = p["x"]?.toIntOrNull() ?: return "missing x"
        val y = p["y"]?.toIntOrNull() ?: return "missing y"
        minecraftInput!!.click(x, y, p["button"] ?: "left"); return "clicked($x,$y)"
    }

    protected open fun handlePressKey(p: Map<String, String>): Any? {
        val key = p["key"] ?: return "missing key"
        val hold = p["hold_seconds"]?.toFloatOrNull() ?: 0f
        minecraftInput!!.pressKey(key, hold); return key
    }

    protected open fun handleTypeText(p: Map<String, String>): Any? {
        val text = p["text"] ?: return "missing text"
        minecraftInput!!.typeText(text)
        if (p["press_enter"]?.toBoolean() == true) minecraftInput!!.pressKey("Enter", 0f)
        return text
    }

    protected open fun handleScroll(p: Map<String, String>): Any? {
        val clicks = p["clicks"]?.toIntOrNull() ?: 1
        minecraftInput!!.scroll(clicks); return "$clicks scrolls"
    }

    protected open fun handleHotkey(p: Map<String, String>): Any? {
        val keysStr = p["keys"] ?: return "missing keys"
        minecraftInput!!.hotkey(keysStr.split(",").map { it.trim() }.toTypedArray())
        return keysStr
    }

    protected open fun handleExecuteCommand(p: Map<String, String>): Any? {
        val cmd = p["command"] ?: return "missing command"
        return minecraftInput!!.executeCommand(cmd)
    }

    protected open fun handleGetPlayerInfo(): Any? = minecraftInput?.getPlayerInfo() ?: "no player"

    protected open fun handleGetWorldInfo(): Any? = minecraftInput?.getWorldInfo() ?: "no world"
}

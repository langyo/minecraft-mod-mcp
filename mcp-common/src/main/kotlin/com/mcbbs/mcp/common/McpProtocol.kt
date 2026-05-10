package com.mcbbs.mcp.common

import com.google.gson.Gson
import com.google.gson.JsonObject

val COMMON_GSON = Gson()

data class McpCommand(val action: String, val params: Map<String, Any?> = emptyMap())
data class McpResult(val success: Boolean = true, val data: Any? = null, val error: String? = null)

interface MinecraftInput {
    fun click(x: Int, y: Int, button: String = "left")
    fun pressKey(key: String, holdSeconds: Float = 0f)
    fun typeText(text: String)
    fun scroll(clicks: Int)
    fun hotkey(keys: Array<String>)
    fun screenshot(): ByteArray?
    fun executeCommand(command: String): String
    fun getPlayerInfo(): String
    fun getWorldInfo(): String
}

interface ModLifecycle {
    fun onInit(input: MinecraftInput, wsClient: Any)
    fun onTick()
    fun onShutdown()
}

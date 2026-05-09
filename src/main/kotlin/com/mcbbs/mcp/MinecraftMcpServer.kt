package com.mcbbs.mcp

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

val gson = Gson()
data class McCommand(val action: String, val params: Map<String, Any?> = emptyMap())
data class McResult(val success: Boolean, val data: Any? = null, val error: String? = null)

class McWsServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {
    private var client: WebSocket? = null
    private val pending = ConcurrentHashMap<String, (McResult) -> Unit>()
    private var reqId = 0
    override fun onOpen(conn: WebSocket?, h: ClientHandshake?) { client = conn; println("[WS] MC connected") }
    override fun onClose(conn: WebSocket?, c: Int, r: String?, b: Boolean) { if (conn == client) client = null }
    override fun onMessage(conn: WebSocket?, m: String?) { if (m != null) try { val r = gson.fromJson(m, McResult::class.java); pending[r.data?.toString() ?: ""]?.invoke(r) } catch (_: Exception) {} }
    override fun onError(conn: WebSocket?, e: Exception?) {}
    override fun onStart() { println("[WS] Listening on $port") }

    fun send(cmd: McCommand, cb: (McResult) -> Unit) {
        if (!connected()) { cb(McResult(false, error="MC not connected via WebSocket")); return }
        val id = "r_${reqId++}"; pending[id] = cb
        val p = HashMap(cmd.params); p["requestId"] = id
        client?.send(gson.toJson(McCommand(cmd.action, p)))
        Thread.sleep(200)
        if (pending.remove(id) != null) cb(McResult(false, error="timeout"))
    }
    fun connected() = client?.isOpen == true
}

fun main() {
    val port = System.getenv("MC_MCP_WS_PORT")?.toIntOrNull() ?: 9876
    val ws = McWsServer(port); ws.start()
    println("Minecraft MCP Server ready WS:$port")
    val reader = BufferedReader(InputStreamReader(System.`in`))
    while (true) {
        val line = reader.readLine() ?: break
        if (line.isBlank()) continue
        try {
            val req = gson.fromJson(line, JsonObject::class.java)
            val method = req.get("method")?.asString ?: ""
            val id = if (req.has("id")) req.get("id") else null
            val resp: JsonObject = when (method) {
                "initialize" -> jobj("result" to jobj(
                    "protocolVersion" to "2025-03-26",
                    "capabilities" to jobj(),
                    "serverInfo" to jobj("name" to "minecraft-neoforge-mcp", "version" to "0.1.0")
                ))
                "tools/list" -> jobj("result" to jobj("tools" to jarr(
                    toolObj("launch_game", "Launch Minecraft game with example mod (detached)", """{"type":"object","properties":{"mod_jar_path":{"type":"string","description":"Path to mod project dir"},"mc_dir":{"type":"string","description":"Minecraft run directory"},"max_memory_gb":{"type":"number","description":"Max heap in GB, default 4"}}}"""),
                    toolObj("screenshot", "Take screenshot via in-game mod pipeline", """{"type":"object","properties":{"save_path":{"type":"string"}}}"""),
                    toolObj("click", "Click at position inside MC window (via mod input system)", """{"type":"object","properties":{"x":{"type":"integer"},"y":{"type":"integer"},"button":{"type":"string","enum":["left","right","middle"]}},"required":["x","y"]}"""),
                    toolObj("press_key", "Press keyboard key (via mod input system)", """{"type":"object","properties":{"key":{"type":"string"},"hold_seconds":{"type":"number"}},"required":["key"]}"""),
                    toolObj("type_text", "Type text into MC (via mod input system)", """{"type":"object","properties":{"text":{"type":"string"},"press_enter":{"type":"boolean"}},"required":["text"]}"""),
                    toolObj("scroll", "Scroll mouse wheel (via mod input system)", """{"type":"object","properties":{"clicks":{"type":"integer"}},"required":["clicks"]}"""),
                    toolObj("get_window_info", "Get connection status and mod info", """{"type":"object","properties":{}}"""),
                    toolObj("wait_for_screen", "Wait for MC mod WebSocket connection", """{"type":"object","properties":{"timeout_seconds":{"type":"number"}}}"""),
                    toolObj("hotkey", "Press key combo (via mod input system)", """{"type":"object","properties":{"keys":{"type":"array","items":{"type":"string"}},"required":["keys"]}""")
                )))
                "tools/call" -> { val r = handleToolCall(req.getAsJsonObject("params"), ws); jobj("result" to r) }
                else -> jobj("error" to jobj("code" to -32601, "message" to "unknown method: $method"))
            }
            if (id != null) resp.add("id", id)
            resp.add("jsonrpc", com.google.gson.JsonPrimitive("2.0"))
            println(gson.toJson(resp)); System.out.flush()
        } catch (e: Exception) {
            println(gson.toJson(jobj("jsonrpc" to "2.0", "error" to jobj("code" to -32700, "message" to (e.message ?: "parse error")), "id" to null))); System.out.flush()
        }
    }
}

fun toolObj(name: String, desc: String, schema: String): JsonObject =
    jobj("name" to name, "description" to desc, "inputSchema" to gson.fromJson(schema, JsonObject::class.java))

private fun requireWs(ws: McWsServer): JsonObject? {
    return if (ws.connected()) null else txtObj("Error: MC not connected. Launch the game with minecraft-mcp.langyo.xyz mod first.")
}

fun handleToolCall(params: JsonObject?, ws: McWsServer): JsonObject {
    val name = params?.get("name")?.asString ?: return txtObj("Error: missing name")
    val args = params?.getAsJsonObject("arguments") ?: JsonObject()
    return try { when (name) {
        "launch_game" -> doLaunchGame(args)
        "screenshot" -> { val e = requireWs(ws); if (e != null) e else doScreenshot(args, ws) }
        "click" -> { val e = requireWs(ws); if (e != null) e else doClick(args, ws) }
        "press_key" -> { val e = requireWs(ws); if (e != null) e else doPressKey(args, ws) }
        "type_text" -> { val e = requireWs(ws); if (e != null) e else doTypeText(args, ws) }
        "scroll" -> { val e = requireWs(ws); if (e != null) e else doScroll(args, ws) }
        "get_window_info" -> doWinInfo(ws)
        "wait_for_screen" -> doWaitScreen(args, ws)
        "hotkey" -> { val e = requireWs(ws); if (e != null) e else doHotkey(args, ws) }
        else -> txtObj("Error: unknown tool $name")
    }} catch (e: Exception) { txtObj("Error: ${e.message}") }
}

fun doLaunchGame(a: JsonObject): JsonObject {
    val modPath = a.get("mod_jar_path")?.asString ?: System.getProperty("user.dir")
    val mcDir = a.get("mc_dir")?.asString ?: System.getenv("MC_RUN_DIR") ?: System.getProperty("user.home") + "/.mcbbs-memorial"
    val maxMem = (a.get("max_memory_gb")?.asDouble ?: 4.0).toInt()
    try {
        val pb = ProcessBuilder(
            "gradlew.bat", "runClient",
            "-Porg.gradle.jvmargs=-Xmx${maxMem}G",
            "-PmcDir=$mcDir"
        )
        pb.directory(java.io.File(modPath))
        pb.redirectErrorStream(true)
        pb.environment()["MC_MCP_SERVER"] = "ws://127.0.0.1:${System.getenv("MC_MCP_WS_PORT") ?: "9876"}"
        val proc = pb.start()
        println("[LAUNCH] MC process started pid=${proc.pid()} dir=$modPath")
        return txtObj("launched pid=${proc.pid()}, waiting for mod WS connection...")
    } catch (e: Exception) {
        return txtObj("launch failed: ${e.message}")
    }
}

fun doScreenshot(a: JsonObject, w: McWsServer): JsonObject {
    var out: JsonObject? = null
    w.send(McCommand("screenshot", hashMapOf("save_path" to a.get("save_path")?.asString))) { res ->
        out = if (res.success && res.data != null) imgObj(res.data as String) else txtObj("mod error: ${res.error}")
    }
    return out ?: txtObj("timeout")
}

fun doClick(a: JsonObject, w: McWsServer): JsonObject {
    val x = a.get("x")?.asInt ?: return txtObj("missing x"); val y = a.get("y")?.asInt ?: return txtObj("missing y")
    var out: JsonObject? = null
    w.send(McCommand("click", hashMapOf("x" to x, "y" to y))) { o ->
        out = if (o.success) txtObj("clicked ($x,$y)") else txtObj(o.error ?: "fail")
    }
    return out ?: txtObj("timeout")
}

fun doPressKey(a: JsonObject, w: McWsServer): JsonObject {
    val k = a.get("key")?.asString ?: return txtObj("missing key")
    var out: JsonObject? = null
    w.send(McCommand("press_key", hashMapOf("key" to k))) { o ->
        out = if (o.success) txtObj(k) else txtObj(o.error ?: "fail")
    }
    return out ?: txtObj("timeout")
}

fun doTypeText(a: JsonObject, w: McWsServer): JsonObject {
    val t = a.get("text")?.asString ?: return txtObj("missing text")
    var out: JsonObject? = null
    w.send(McCommand("type_text", hashMapOf("text" to t))) { o ->
        out = if (o.success) txtObj(t) else txtObj(o.error ?: "fail")
    }
    return out ?: txtObj("timeout")
}

fun doScroll(a: JsonObject, w: McWsServer): JsonObject {
    val c = a.get("clicks")?.asInt ?: return txtObj("missing clicks")
    var out: JsonObject? = null
    w.send(McCommand("scroll", hashMapOf("clicks" to c))) { o ->
        out = if (o.success) txtObj("$c clicks") else txtObj(o.error ?: "fail")
    }
    return out ?: txtObj("timeout")
}

fun doWinInfo(w: McWsServer): JsonObject {
    var out: JsonObject? = null
    w.send(McCommand("ping", emptyMap())) { o ->
        out = if (o.success) txtObj(gson.toJson(jobj(
            "mcConnected" to true,
            "message" to "mod online"
        ))) else txtObj(gson.toJson(jobj("mcConnected" to false)))
    }
    return out ?: txtObj(gson.toJson(jobj("mcConnected" to false)))
}

fun doWaitScreen(a: JsonObject, w: McWsServer): JsonObject {
    val to = ((a.get("timeout_seconds")?.asDouble ?: 30.0)*1000).toLong(); val s = System.currentTimeMillis()
    while (System.currentTimeMillis()-s < to) { if (w.connected()) break; Thread.sleep(500) }
    return txtObj("connected=${w.connected()} elapsed=${(System.currentTimeMillis()-s)/1000.0}s")
}

fun doHotkey(a: JsonObject, w: McWsServer): JsonObject {
    val keys = a.get("keys")?.asJsonArray?.map { it.asString } ?: return txtObj("missing keys")
    var out: JsonObject? = null
    w.send(McCommand("hotkey", hashMapOf("keys" to keys))) { o ->
        out = if (o.success) txtObj(keys.joinToString("+")) else txtObj(o.error ?: "fail")
    }
    return out ?: txtObj("timeout")
}
fun jobj(vararg pairs: Pair<String, Any?>): JsonObject {
    val o = JsonObject()
    for ((k,v) in pairs) { if (v == null) o.add(k, null) else when (v) {
        is String -> o.addProperty(k, v)
        is Number -> o.addProperty(k, v)
        is Boolean -> o.addProperty(k, v)
        is Char -> o.addProperty(k, v)
        is JsonObject -> o.add(k, v)
        is JsonArray -> o.add(k, v)
        is com.google.gson.JsonPrimitive -> o.add(k, v)
        else -> o.addProperty(k, v.toString())
    } }
    return o
}
fun jarr(vararg elems: JsonObject): JsonArray { val a = JsonArray(); for (e in elems) a.add(e); return a }
fun txtObj(s: String): JsonObject { val arr = JsonArray(); arr.add(jobj("type" to "text", "text" to s)); return jobj("content" to arr) }
fun imgObj(b64: String): JsonObject { val arr = JsonArray(); arr.add(jobj("type" to "image", "data" to b64, "mimeType" to "image/png")); return jobj("content" to arr) }

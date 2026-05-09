package com.mcbbs.mcp

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.awt.Robot
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
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
        val id = "r_${reqId++}"; pending[id] = cb
        val p = HashMap(cmd.params); p["requestId"] = id
        if (client?.isOpen == true) client?.send(gson.toJson(McCommand(cmd.action, p))) else cb(McResult(false, error="no connection"))
        Thread.sleep(200)
        if (pending.remove(id) != null) cb(McResult(false, error="timeout"))
    }
    fun connected() = client?.isOpen == true
}

fun main() {
    val port = System.getenv("MC_MCP_WS_PORT")?.toIntOrNull() ?: 9876
    val ws = McWsServer(port); ws.start()
    println("Minecraft MCP Server ready WS:$port")
    val robot = Robot()
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
                    toolObj("launch_game", "Launch Minecraft game (detached process)", """{"type":"object","properties":{"mod_jar_path":{"type":"string","description":"Path to mod jar or project dir"},"mc_dir":{"type":"string","description":"Minecraft run directory"},"max_memory_gb":{"type":"number","description":"Max heap in GB, default 4"}}}"""),
                    toolObj("screenshot", "Take screenshot via WS/game-internal pipeline", """{"type":"object","properties":{"save_path":{"type":"string"}}}"""),
                    toolObj("click", "Click at position in MC window", """{"type":"object","properties":{"x":{"type":"integer"},"y":{"type":"integer"},"button":{"type":"string","enum":["left","right","middle"]}},"required":["x","y"]}"""),
                    toolObj("press_key", "Press keyboard key", """{"type":"object","properties":{"key":{"type":"string"},"hold_seconds":{"type":"number"}},"required":["key"]}"""),
                    toolObj("type_text", "Type text into MC", """{"type":"object","properties":{"text":{"type":"string"},"press_enter":{"type":"boolean"}},"required":["text"]}"""),
                    toolObj("scroll", "Scroll mouse wheel", """{"type":"object","properties":{"clicks":{"type":"integer"}},"required":["clicks"]}"""),
                    toolObj("get_window_info", "Get window info", """{"type":"object","properties":{}}"""),
                    toolObj("wait_for_screen", "Wait for MC client WS connection", """{"type":"object","properties":{"timeout_seconds":{"type":"number"}}}"""),
                    toolObj("hotkey", "Press key combo", """{"type":"object","properties":{"keys":{"type":"array","items":{"type":"string"}},"required":["keys"]}""")
                )))
                "tools/call" -> { val r = handleToolCall(req.getAsJsonObject("params"), robot, ws); jobj("result" to r) }
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

fun handleToolCall(params: JsonObject?, robot: Robot, ws: McWsServer): JsonObject {
    val name = params?.get("name")?.asString ?: return txtObj("Error: missing name")
    val args = params?.getAsJsonObject("arguments") ?: JsonObject()
    return try { when (name) {
        "launch_game" -> doLaunchGame(args)
        "screenshot" -> doScreenshot(args, robot, ws)
        "click" -> doClick(args, robot, ws)
        "press_key" -> doPressKey(args, robot, ws)
        "type_text" -> doTypeText(args, robot, ws)
        "scroll" -> doScroll(args, robot, ws)
        "get_window_info" -> doWinInfo(ws)
        "wait_for_screen" -> doWaitScreen(args, ws)
        "hotkey" -> doHotkey(args, robot, ws)
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
        return txtObj("launched pid=${proc.pid()}, waiting for WS connection...")
    } catch (e: Exception) {
        return txtObj("launch failed: ${e.message}")
    }
}

fun doScreenshot(a: JsonObject, r: Robot, w: McWsServer): JsonObject {
    if (w.connected()) { var out: JsonObject? = null
        w.send(McCommand("screenshot", hashMapOf("save_path" to a.get("save_path")?.asString))) { res ->
            out = if (res.success && res.data != null) imgObj(res.data as String) else txtObj("WS error: ${res.error}")
        }; return out ?: txtObj("timeout")
    }
    val screen = r.createScreenCapture(java.awt.Rectangle(java.awt.Toolkit.getDefaultToolkit().screenSize))
    val baos = java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(screen, "png", baos)
    return imgObj(Base64.getEncoder().encodeToString(baos.toByteArray()))
}
fun doClick(a: JsonObject, r: Robot, w: McWsServer): JsonObject {
    val x = a.get("x")?.asInt ?: return txtObj("missing x"); val y = a.get("y")?.asInt ?: return txtObj("missing y")
    val btn = when (a.get("button")?.asString) {"right"->InputEvent.BUTTON3_DOWN_MASK;"middle"->InputEvent.BUTTON2_DOWN_MASK;else->InputEvent.BUTTON1_DOWN_MASK}
    if (w.connected()) { var o: JsonObject? = null; w.send(McCommand("click", hashMapOf("x" to x, "y" to y))) { o = if (it.success) txtObj("clicked ($x,$y)") else txtObj(it.error ?: "fail") }; return o ?: txtObj("timeout") }
    r.mouseMove(x,y); Thread.sleep(50); r.mousePress(btn); r.mouseRelease(btn); return txtObj("clicked ($x,$y)")
}
fun doPressKey(a: JsonObject, r: Robot, w: McWsServer): JsonObject {
    val k = a.get("key")?.asString ?: return txtObj("missing key"); val hold = ((a.get("hold_seconds")?.asDouble ?: 0.05)*1000).toLong()
    if (w.connected()) { var o: JsonObject? = null; w.send(McCommand("press_key", hashMapOf("key" to k))) { o = if (it.success) txtObj(k) else txtObj(it.error ?: "fail") }; return o ?: txtObj("timeout") }
    val kc = keyCode(k) ?: return txtObj("bad key: $k"); r.keyPress(kc); Thread.sleep(hold); r.keyRelease(kc); return txtObj("pressed $k")
}
fun doTypeText(a: JsonObject, r: Robot, w: McWsServer): JsonObject {
    val t = a.get("text")?.asString ?: return txtObj("missing text"); val iv = ((a.get("interval")?.asDouble ?: 0.03)*1000).toLong(); val ent = a.get("press_enter")?.asBoolean ?: false
    if (w.connected()) { var o: JsonObject? = null; w.send(McCommand("type_text", hashMapOf("text" to t))) { o = if (it.success) txtObj(t) else txtObj(it.error ?: "fail") }; return o ?: txtObj("timeout") }
    for (ch in t) { val kc = charKeyCode(ch) ?: continue; r.keyPress(kc); r.keyRelease(kc); Thread.sleep(iv) }
    if (ent) { r.keyPress(KeyEvent.VK_ENTER); r.keyRelease(KeyEvent.VK_ENTER) }; return txtObj("typed '$t'")
}
fun doScroll(a: JsonObject, r: Robot, w: McWsServer): JsonObject {
    val c = a.get("clicks")?.asInt ?: return txtObj("missing clicks")
    if (w.connected()) { var o: JsonObject? = null; w.send(McCommand("scroll", hashMapOf("clicks" to c))) { o = if (it.success) txtObj("$c clicks") else txtObj(it.error ?: "fail") }; return o ?: txtObj("timeout") }
    r.mouseWheel(c); return txtObj("scrolled $c")
}
fun doWinInfo(w: McWsServer): JsonObject { val i = jobj("mcConnected" to w.connected()); return txtObj(gson.toJson(i)) }
fun doWaitScreen(a: JsonObject, w: McWsServer): JsonObject {
    val to = ((a.get("timeout_seconds")?.asDouble ?: 30.0)*1000).toLong(); val s = System.currentTimeMillis()
    while (System.currentTimeMillis()-s < to) { if (w.connected()) break; Thread.sleep(500) }
    return txtObj("connected=${w.connected()} elapsed=${(System.currentTimeMillis()-s)/1000.0}s")
}
fun doHotkey(a: JsonObject, r: Robot, w: McWsServer): JsonObject {
    val keys = a.get("keys")?.asJsonArray?.map { it.asString } ?: return txtObj("missing keys")
    if (w.connected()) { var o: JsonObject? = null; w.send(McCommand("hotkey", hashMapOf("keys" to keys))) { o = if (it.success) txtObj(keys.joinToString("+")) else txtObj(it.error ?: "fail") }; return o ?: txtObj("timeout") }
    val codes = keys.mapNotNull { keyCode(it) }.toIntArray(); for (c in codes) r.keyPress(c); Thread.sleep(50); for (c in codes.reversed()) r.keyRelease(c); return txtObj(keys.joinToString("+"))
}
fun keyCode(k: String): Int? = when (k.lowercase()) {
    "escape","esc"->256;"enter","return"->257;"tab"->258;"space"->32
    "shift"->340;"control","ctrl"->341;"alt"->342
    "backspace","bs"->259;"delete","del"->261
    "up"->265;"down"->264;"left"->263;"right"->262
    "f1"->290;"f2"->291;"f3"->292;"f4"->293;"f5"->294;"f6"->295;"f7"->296;"f8"->297;"f9"->298;"f10"->299;"f11"->300;"f12"->301
    else -> if (k.length==1) charKeyCode(k[0]) else null
}

fun charKeyCode(ch: Char): Int? = when (ch) {
    in 'a'..'z' -> ch.uppercaseChar().code
    in 'A'..'Z' -> ch.code
    in '0'..'9' -> ch.code
    ' ' -> 32; '!' -> 33; '@' -> 64; '#' -> 35; '$' -> 36; '%' -> 37; '^' -> 94; '&' -> 38
    '*' -> 42; '(' -> 40; ')' -> 41; '-' -> 45; '_' -> 95; '=' -> 61; '+' -> 43
    '[' -> 91; '{' -> 123; ']' -> 93; '}' -> 125; '\\' -> 92; '|' -> 124
    ';' -> 59; ':' -> 58; '\'' -> 39; '"' -> 34; ',' -> 44; '<' -> 60
    '.' -> 46; '>' -> 62; '/' -> 47; '?' -> 63; '`' -> 96; '~' -> 126
    else -> ch.code.takeIf { it < 128 }
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

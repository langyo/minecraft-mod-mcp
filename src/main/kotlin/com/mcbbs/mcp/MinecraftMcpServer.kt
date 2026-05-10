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
    override fun onMessage(conn: WebSocket?, m: String?) {
        if (m != null) try {
            val obj = gson.fromJson(m, JsonObject::class.java)
            val respId = obj.get("id")?.asString
            if (respId != null) {
                val data = obj.get("result")?.asString
                val cb = pending.remove(respId)
                if (cb != null) {
                    val success = data != null && !data.startsWith("error:")
                    cb(McResult(success, data, if (!success) data else null))
                }
            }
        } catch (_: Exception) {}
    }
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
                    toolObj("launch_game", "Launch Minecraft via official launcher installation", """{"type":"object","properties":{"loader_type":{"type":"string","enum":["auto","forge","neoforge","fabric"],"description":"Mod loader to use (auto-detect by default)"},"mc_dir":{"type":"string","description":"Path to .minecraft directory"},"mod_jar_path":{"type":"string","description":"Path to mod JAR to copy into mods/"},"max_memory_gb":{"type":"number","description":"Max heap in GB, default 4"}}}"""),
                    toolObj("screenshot", "Take screenshot via in-game mod pipeline", """{"type":"object","properties":{"save_path":{"type":"string"}}}"""),
                    toolObj("click", "Click at position inside MC window (via mod input system)", """{"type":"object","properties":{"x":{"type":"integer"},"y":{"type":"integer"},"button":{"type":"string","enum":["left","right","middle"]}},"required":["x","y"]}"""),
                    toolObj("press_key", "Press keyboard key (via mod input system)", """{"type":"object","properties":{"key":{"type":"string"},"hold_seconds":{"type":"number"}},"required":["key"]}"""),
                    toolObj("type_text", "Type text into MC (via mod input system)", """{"type":"object","properties":{"text":{"type":"string"},"press_enter":{"type":"boolean"}},"required":["text"]}"""),
                    toolObj("scroll", "Scroll mouse wheel (via mod input system)", """{"type":"object","properties":{"clicks":{"type":"integer"}},"required":["clicks"]}"""),
                    toolObj("get_window_info", "Get connection status and mod info", """{"type":"object","properties":{}}"""),
                    toolObj("wait_for_screen", "Wait for MC mod WebSocket connection", """{"type":"object","properties":{"timeout_seconds":{"type":"number"}}}"""),
                    toolObj("hotkey", "Press key combo (via mod input system)", """{"type":"object","properties":{"keys":{"type":"array","items":{"type":"string"}},"required":["keys"]}}"""),
                    toolObj("execute_command", "Execute in-game command via mod", """{"type":"object","properties":{"command":{"type":"string"}},"required":["command"]}"""),
                    toolObj("get_player_info", "Query player state from mod", """{"type":"object","properties":{}}"""),
                    toolObj("get_world_info", "Query world state from mod", """{"type":"object","properties":{}}"""),
                    toolObj("ping", "Ping mod for connectivity test", """{"type":"object","properties":{}}""")
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
    ws.stop(1000)
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
        "execute_command" -> { val e = requireWs(ws); if (e != null) e else doExecuteCommand(args, ws) }
        "get_player_info" -> { val e = requireWs(ws); if (e != null) e else doPlayerInfo(ws) }
        "get_world_info" -> { val e = requireWs(ws); if (e != null) e else doWorldInfo(ws) }
        "ping" -> { val e = requireWs(ws); if (e != null) e else doPing(ws) }
        else -> txtObj("Error: unknown tool $name")
    }} catch (e: Exception) { txtObj("Error: ${e.message}") }
}

fun doLaunchGame(a: JsonObject): JsonObject {
    val loaderType = a.get("loader_type")?.asString ?: "auto"
    val mcDir = a.get("mc_dir")?.asString
        ?: System.getenv("MC_RUN_DIR")
        ?: run {
            val os = System.getProperty("os.name").lowercase()
            if (os.contains("win")) System.getenv("APPDATA") + "\\.minecraft"
            else System.getProperty("user.home") + "/.minecraft"
        }
    val maxMem = (a.get("max_memory_gb")?.asDouble ?: 4.0).toInt()
    val modJarPath = a.get("mod_jar_path")?.asString
    try {
        val mcDirFile = java.io.File(mcDir)
        if (!mcDirFile.isDirectory) return txtObj("Error: Minecraft directory not found: $mcDir")
        val versionsDir = java.io.File(mcDirFile, "versions")
        if (!versionsDir.isDirectory) return txtObj("Error: No versions folder in $mcDir - is this a valid .minecraft directory?")

        val installed = detectLoaders(versionsDir)
        if (installed.isEmpty()) return txtObj("""Error: No compatible mod loader found in $mcDir.
Please install one via the official Minecraft launcher:
  - NeoForge: https://neoforged.net/
  - Forge:   https://files.minecraftforge.net/
  - Fabric:  https://fabricmc.net/

Found versions: ${versionsDir.listFiles()?.map { it.name }?.joinToString(", ") ?: "none"}""")

        val target = when {
            loaderType != "auto" -> installed.find { it.loader == loaderType }
                ?: return txtObj("Error: '$loaderType' not installed. Available: ${installed.map { "${it.loader}@${it.version}" }.joinToString(", ")}")
            else -> installed.firstOrNull()
                ?: return txtObj("Error: No loader detected from installed versions")
        }

        val modsDir = java.io.File(mcDirFile, "mods"); modsDir.mkdirs()

        if (modJarPath != null) {
            val srcJar = java.io.File(modJarPath)
            if (srcJar.exists()) {
                val dstJar = java.io.File(modsDir, srcJar.name)
                java.nio.file.Files.copy(srcJar.toPath(), dstJar.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                println("[LAUNCH] Copied mod: ${srcJar.name} -> mods/")
            } else { return txtObj("Error: Mod JAR not found: $modJarPath") }
        }

        val versionJson = java.io.File(target.versionDir, "${target.version}.json")
        if (!versionJson.exists()) return txtObj("Error: Version JSON missing: ${versionJson.path}")

        val proc = launchViaVersionJson(mcDirFile, target, versionJson, maxMem)
        println("[LAUNCH] MC launched pid=${proc?.pid()} loader=${target.loader} version=${target.version}")
        return txtObj("launched ${target.loader}@${target.version} pid=${proc?.pid()}, waiting for mod WS connection...")
    } catch (e: Exception) {
        return txtObj("launch failed: ${e.message}")
    }
}

data class DetectedLoader(val loader: String, val version: String, val versionDir: java.io.File)

private fun detectLoaders(versionsDir: java.io.File): List<DetectedLoader> {
    val result = mutableListOf<DetectedLoader>()
    val dirs = versionsDir.listFiles()?.filter { it.isDirectory } ?: return emptyList()
    for (dir in dirs) {
        val name = dir.name.lowercase()
        val loader = when {
            name.contains("neoforge") || name.contains("neo") && !name.contains("vanilla") -> "neoforge"
            name.contains("forge") -> "forge"
            name.contains("fabric") -> "fabric"
            else -> null
        }
        if (loader != null) {
            val jsonFile = java.io.File(dir, "${dir.name}.json")
            if (jsonFile.exists()) result.add(DetectedLoader(loader, dir.name, dir))
        }
    }
    return result.sortedByDescending { it.version }
}

private fun launchViaVersionJson(
    mcDir: java.io.File,
    target: DetectedLoader,
    versionJson: java.io.File,
    maxMem: Int
): Process? {
    val json = mergeVersionJson(versionJson, mcDir)

    val mainClass = json.get("mainClass")?.asString
        ?: return null.also { println("[LAUNCH] Error: no mainClass in JSON") }

    val cp = buildFullClasspath(mcDir, json)
    if (cp.isEmpty()) return null.also { println("[LAUNCH] Error: empty classpath") }

    val jvmArgs = extractJvmArgs(json, mcDir)
    val gameArgs = extractGameArgs(json)

    val nativesDir = extractNatives(mcDir, json)

    val javaHome = findJavaForMc() ?: System.getProperty("java.home")
    val sep = java.io.File.separator
    val javaExe = java.io.File(javaHome, "bin${sep}java.exe").takeIf { it.exists() }
        ?: java.io.File(javaHome, "bin${sep}java")

    val cmd = mutableListOf<String>().apply {
        add(javaExe.absolutePath)
        add("-Xmx${maxMem}G")
        add("-Xms${maxMem/2}G")
        add("-Djava.library.path=$nativesDir")
        add("-Dminecraft.client.jar=${java.io.File(mcDir, "versions/${target.version}/${target.version}.jar").absolutePath}")
        add("-Dminecraft.classpath=$cp")
        addAll(jvmArgs)
        add("-cp"); add(cp)
        add(mainClass)
        addAll(gameArgs)
        add("--version"); add(target.version)
        add("--gameDir"); add(mcDir.absolutePath)
        add("--assetsDir"); add(java.io.File(mcDir, "assets").absolutePath)
        add("--assetIndex"); add(json.get("assetIndex")?.asJsonObject?.get("id")?.asString ?: "1.21")
    }

    val isWin = System.getProperty("os.name").lowercase().contains("win")
    println("[LAUNCH] Command: ${cmd.take(6).joinToString(" ")}... (${cmd.size} total args)")
    println("[LAUNCH] Classpath: ${cp.split(java.io.File.pathSeparatorChar).size} entries")

    val pb = if (isWin) {
        ProcessBuilder(mutableListOf("cmd", "/c", "start", "Minecraft", "/B").also { it.addAll(cmd) })
    } else {
        ProcessBuilder(cmd)
    }
    pb.environment()["MC_MCP_SERVER"] = "ws://127.0.0.1:${System.getenv("MC_MCP_WS_PORT") ?: "9876"}"
    pb.directory(mcDir)

    val proc = try { pb.start() } catch (e: Exception) {
        println("[LAUNCH] Start error: ${e.message}")
        return null
    }

    Thread {
        val err = proc.errorStream.bufferedReader().readText()
        if (err.isNotEmpty()) println("[LAUNCH] stderr:\n$err")
    }.start()

    Thread {
        Thread.sleep(8000)
        if (!proc.isAlive) {
            val out = proc.inputStream.bufferedReader().readText()
            if (out.isNotEmpty()) println("[LAUNCH] stdout:\n$out")
        }
    }.start()

    return proc
}

private fun mergeVersionJson(versionJson: java.io.File, mcDir: java.io.File): JsonObject {
    var json = gson.fromJson(versionJson.readText(), JsonObject::class.java)
    val inheritsFrom = json.get("inheritsFrom")?.asString
    if (inheritsFrom != null) {
        val parentFile = java.io.File(java.io.File(mcDir, "versions"), "$inheritsFrom/$inheritsFrom.json")
        if (parentFile.exists()) {
            val parent = gson.fromJson(parentFile.readText(), JsonObject::class.java)
            json = mergeJsonObjects(parent, json)
        }
    }
    return json
}

private fun mergeJsonObjects(base: JsonObject, override: JsonObject): JsonObject {
    val result = JsonObject()
    base.entrySet().forEach { result.add(it.key, it.value) }
    override.entrySet().forEach { entry ->
        val key = entry.key
        val value = entry.value
        val existing = result.get(key)
        if (existing != null && existing.isJsonArray && value.isJsonArray) {
            val combined = JsonArray()
            existing.asJsonArray.forEach { combined.add(it) }
            value.asJsonArray.forEach { combined.add(it) }
            result.add(key, combined)
        } else {
            result.add(key, value)
        }
    }
    return result
}

private fun buildFullClasspath(mcDir: java.io.File, json: JsonObject): String {
    val libs = json.getAsJsonArray("libraries") ?: return ""
    val cpParts = mutableListOf<String>()
    for (lib in libs) {
        val libObj = lib.asJsonObject
        val downloads = libObj.get("downloads")?.asJsonObject ?: continue
        val artifact = downloads.get("artifact")?.asJsonObject ?: continue
        val path = artifact.get("path")?.asString ?: continue
        val libFile = java.io.File(mcDir, "libraries${java.io.File.separator}$path")
        if (libFile.exists()) cpParts.add(libFile.absolutePath)
    }
    return cpParts.joinToString(java.io.File.pathSeparator)
}

private fun extractNatives(mcDir: java.io.File, json: JsonObject): String {
    val nativesDir = java.io.File(mcDir, "versions-natives-${System.currentTimeMillis()}")
    nativesDir.mkdirs()
    val libs = json.getAsJsonArray("libraries") ?: return nativesDir.absolutePath
    for (lib in libs) {
        val libObj = lib.asJsonObject
        val downloads = libObj.get("downloads")?.asJsonObject ?: continue
        val classifiers = downloads.get("classifiers")?.asJsonObject ?: continue
        val nativeArtifact = classifiers.asMap().entries.firstOrNull {
            it.key.contains("natives") && allowRulesForOS(libObj)
        }?.value?.asJsonObject ?: continue
        val path = nativeArtifact.get("path")?.asString ?: continue
        val srcFile = java.io.File(mcDir, "libraries${java.io.File.separator}$path")
        if (srcFile.exists()) {
            try {
                java.nio.file.Files.copy(srcFile.toPath(),
                    java.io.File(nativesDir, srcFile.name).toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } catch (_: Exception) {}
        }
    }
    return nativesDir.absolutePath
}

private fun allowRulesForOS(libObj: JsonObject): Boolean {
    val rules = libObj.get("rules")?.asJsonArray ?: return true
    return allowRules(rules)
}

private fun findJavaForMc(): String? {
    val candidates = listOf(
        System.getenv("JAVA_HOME"),
        System.getProperty("java.home"),
        "C:\\Program Files\\Amazon Corretto\\jdk21.0.8_9",
        "C:\\Program Files\\Java\\jdk-21"
    ).mapNotNull { it?.let { p -> java.io.File(p).takeIf { it.isDirectory } } }
    for (c in candidates) {
        val sep = java.io.File.separator
        val exe = java.io.File(c, "bin${sep}${if (System.getProperty("os.name").lowercase().contains("win")) "java.exe" else "java"}")
        if (exe.exists()) return c.absolutePath
    }
    return null
}

private fun extractJvmArgs(json: JsonObject, mcDir: java.io.File): List<String> {
    val args = mutableListOf<String>()
    val jvmArgs = json.get("arguments")?.asJsonObject?.get("jvm")?.asJsonArray ?: return args
    for (arg in jvmArgs) {
        if (arg.isJsonObject) {
            val argObj = arg.asJsonObject
            val rules = argObj.get("rules")?.asJsonArray
            if (rules != null && !allowRules(rules)) continue
            val v = argObj.get("value")
            if (v.isJsonArray) v.asJsonArray.forEach { if (it.isJsonPrimitive) args.add(it.asString) }
            else if (v.isJsonPrimitive) args.add(v.asString)
        } else if (arg.isJsonPrimitive) {
            args.add(arg.asString)
        }
    }
    return args.map { replaceVars(it, mcDir) }
}

private fun extractGameArgs(json: JsonObject): List<String> {
    val gameArgs = json.get("arguments")?.asJsonObject?.get("game")?.asJsonArray
    if (gameArgs != null) {
        val args = mutableListOf<String>()
        for (arg in gameArgs) {
            if (arg.isJsonObject) {
                val argObj = arg.asJsonObject
                val rules = argObj.get("rules")?.asJsonArray
                if (rules != null && !allowRules(rules)) continue
                val v = argObj.get("value")
            if (v.isJsonArray) v.asJsonArray.forEach { if (it.isJsonPrimitive) args.add(it.asString) }
            else if (v.isJsonPrimitive) args.add(v.asString)
            } else if (arg.isJsonPrimitive) args.add(arg.asString)
        }
        return args
    }
    val legacyArgs = json.getAsJsonArray("minecraftArguments")?.asString ?: ""
    return legacyArgs.split(" ").filter { it.isNotEmpty() }
}

private fun allowRules(rules: JsonArray): Boolean {
    for (rule in rules) {
        val r = rule.asJsonObject
        val action = r.get("action")?.asString ?: "allow"
        val os = r.get("os")?.asJsonObject
        if (os != null) {
            val osName = os.get("name")?.asString ?: continue
            val currentOs = System.getProperty("os.name").lowercase()
            if (!currentOs.contains(osName.lowercase())) return action == "disallow"
        }
        if (action == "disallow") return false
    }
    return true
}

private fun replaceVars(s: String, mcDir: java.io.File): String {
    var result = s
        .replace("\${launcher_name}", "minecraft-mcp")
        .replace("\${launcher_version}", "0.1.0")
        .replace("\${classpath}", "")
    if (result.contains("\${natives_directory}")) {
        val nativesPath = java.io.File(mcDir, "natives").absolutePath
        result = result.replace("\${natives_directory}", nativesPath)
    }
    return result
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

fun doExecuteCommand(a: JsonObject, w: McWsServer): JsonObject {
    val cmd = a.get("command")?.asString ?: return txtObj("missing command")
    var out: JsonObject? = null
    w.send(McCommand("execute_command", hashMapOf("command" to cmd))) { o ->
        out = if (o.success) txtObj("command sent: $cmd") else txtObj(o.error ?: "fail")
    }
    return out ?: txtObj("timeout")
}

fun doPlayerInfo(w: McWsServer): JsonObject {
    var out: JsonObject? = null
    w.send(McCommand("get_player_info", emptyMap())) { o ->
        out = if (o.success && o.data != null) txtObj(o.data.toString()) else txtObj(o.error ?: "no data")
    }
    return out ?: txtObj("timeout")
}

fun doWorldInfo(w: McWsServer): JsonObject {
    var out: JsonObject? = null
    w.send(McCommand("get_world_info", emptyMap())) { o ->
        out = if (o.success && o.data != null) txtObj(o.data.toString()) else txtObj(o.error ?: "no data")
    }
    return out ?: txtObj("timeout")
}

fun doPing(w: McWsServer): JsonObject {
    var out: JsonObject? = null
    w.send(McCommand("ping", emptyMap())) { o ->
        out = if (o.success && o.data != null) txtObj(o.data.toString()) else txtObj("pong")
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

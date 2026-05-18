# Game Manipulation Facilities — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix and harden mcp-common's input injection, screenshot, and WS client so they work correctly across all 76 MC versions (1.7.2–26.1.2, Forge/NeoForge/Fabric), with proper message queue, server URL from system property + env var, and compile + smoke-test verification.

**Architecture:** mcp-common is a pure-reflection shared library (no MC imports). Each per-version mod is a thin lifecycle shim that instantiates `ReflectedInputHandler` + `McpWebSocketClient`. WS messages arrive via `onMessage`, queue into `ConcurrentLinkedQueue`, and are drained on the MC render/client tick thread via `handleMessages()`. Server URL resolved as: system property `-Dmcp.server` > env var `MC_MCP_SERVER` > default `ws://127.0.0.1:9876`.

**Tech Stack:** Java 8 bytecode target, Kotlin 2.1 (mcp-common), Java-WebSocket 1.5.4, Gson 2.11.0, LWJGL2/3 via reflection, Python scripts for generation.

---

## Critical Bugs to Fix

| # | Bug | Impact | Files |
|---|-----|--------|-------|
| 1 | `getPlayer()` calls `mc.player()` as method — fails on MC 1.7.x–1.11.x (field is `thePlayer`) | All input fails on legacy versions | ReflectionHelper.java |
| 2 | `getLevel()` calls `mc.level()` — fails on MC 1.7.x–1.11.x (field is `theWorld`) | world info broken on legacy | ReflectionHelper.java |
| 3 | `getDimensionId()` calls `player.level()` — fails on legacy (field, not method) | dimension always "overworld" on legacy | ReflectionHelper.java |
| 4 | `lwjgl2Click()` never sends mouse button events — only sets cursor position | Click does nothing on MC 1.7–1.12 | ReflectedInputHandler.java |
| 5 | `handleMessages()` is empty — no queue draining | Messages processed on WS thread (race conditions) | McpWebSocketClient.kt |
| 6 | Screenshot uses `Thread.sleep(200)` for async sync | Unreliable, may miss frame | ReflectionHelper.java |
| 7 | Server URL only from env var | User wants `-Dmcp.server=` with higher priority | McpWebSocketClient.kt + all templates |
| 8 | Kotlin runtime not in mod jars | Kotlin classes won't load at runtime in mod classloader | mcp-common/build.gradle.kts |

---

### Task 1: Fix ReflectionHelper — player/level/dimension access for all MC versions

**Files:**
- Modify: `mcp-common/src/main/java/com/mcbbs/mcp/common/ReflectionHelper.java`

**Step 1: Fix getPlayer() for legacy MC versions**

The `getPlayer()` method must try multiple paths:
- MC 1.13+: `mc.player()` (method)
- MC 1.7–1.12: `mc.thePlayer` (field) or `mc.field_71439_g` (SRG)

```java
private static Object getPlayer(Object mc) throws Exception {
    try {
        return mc.getClass().getMethod("player").invoke(mc);
    } catch (NoSuchMethodException e) {
        try {
            Field f = mc.getClass().getDeclaredField("thePlayer");
            f.setAccessible(true);
            return f.get(mc);
        } catch (NoSuchFieldException e2) {
            try {
                Field f = mc.getClass().getDeclaredField("field_71439_g");
                f.setAccessible(true);
                return f.get(mc);
            } catch (NoSuchFieldException e3) {
                return null;
            }
        }
    }
}
```

**Step 2: Fix getLevel() for legacy MC versions**

```java
private static Object getLevel(Object mc) throws Exception {
    try {
        return mc.getClass().getMethod("level").invoke(mc);
    } catch (NoSuchMethodException e) {
        try {
            return mc.getClass().getMethod("world").invoke(mc);
        } catch (NoSuchMethodException e2) {
            try {
                Field f = mc.getClass().getDeclaredField("theWorld");
                f.setAccessible(true);
                return f.get(mc);
            } catch (NoSuchFieldException e3) {
                try {
                    Field f = mc.getClass().getDeclaredField("field_71441_f");
                    f.setAccessible(true);
                    return f.get(mc);
                } catch (NoSuchFieldException e4) {
                    return null;
                }
            }
        }
    }
}
```

**Step 3: Fix getDimensionId() — use player's level field on legacy**

```java
public static String getDimensionId(Object player) throws Exception {
    Object level = null;
    try {
        level = player.getClass().getMethod("level").invoke(player);
    } catch (NoSuchMethodException e) {
        try {
            Field f = player.getClass().getDeclaredField("theWorld");
            f.setAccessible(true);
            level = f.get(player);
        } catch (NoSuchFieldException e2) {
            Field f = player.getClass().getDeclaredField("field_70170_p");
            f.setAccessible(true);
            level = f.get(player);
        }
    }
    if (level == null) return "overworld";
    Object provider = null;
    try {
        provider = level.getClass().getField("provider").get(level);
    } catch (Exception ignored) {}
    if (provider != null) {
        try {
            Object id = provider.getClass().getField("dimensionId").get(provider);
            return String.valueOf(id);
        } catch (Exception ignored) {}
        try {
            int id = provider.getClass().getField("field_76574_g").getInt(provider);
            return String.valueOf(id);
        } catch (Exception ignored) {}
    }
    try {
        Object dim = level.getClass().getMethod("dimension").invoke(level);
        try { return (String) dim.getClass().getMethod("identifier").invoke(dim); } catch (NoSuchMethodException ignored) {}
        try { Object loc = dim.getClass().getMethod("location").invoke(dim); return loc.toString(); } catch (NoSuchMethodException ignored) {}
        try { Object key = dim.getClass().getMethod("getRegistryName").invoke(dim); return key.toString(); } catch (NoSuchMethodException ignored) {}
    } catch (NoSuchMethodException ignored) {}
    return "overworld";
}
```

**Step 4: Fix getDifficultyKey() — add fallback for legacy enum names**

```java
public static String getDifficultyKey(Object level) throws Exception {
    Object diff = level.getClass().getMethod("getDifficulty").invoke(level);
    try {
        return (String) diff.getClass().getMethod("getSerializedName").invoke(diff);
    } catch (NoSuchMethodException e) {}
    try {
        return (String) diff.getClass().getMethod("getName").invoke(diff);
    } catch (NoSuchMethodException e) {}
    try {
        return diff.name().toLowerCase();
    } catch (Exception e) {}
    return "normal";
}
```

**Step 5: Fix sendCommand() — legacy uses `thePlayer.sendQueue`**

```java
public static String sendCommand(Object mc, String cmd) {
    try {
        Object player = getPlayer(mc);
        if (player == null) return "{\"error\":\"no player\"}";
        try {
            Object conn = player.getClass().getMethod("connection").invoke(player);
            conn.getClass().getMethod("sendCommand", String.class).invoke(conn, cmd);
            return "sent: " + cmd;
        } catch (NoSuchMethodException e) {
            try {
                Object conn = player.getClass().getDeclaredField("sendQueue").get(player);
                if (conn == null) conn = player.getClass().getDeclaredField("field_71174_a").get(player);
                conn.getClass().getMethod("addToSendQueue", Class.forName("net.minecraft.network.Packet")).invoke(conn, buildChatPacket("/" + cmd));
            } catch (Exception e2) {
                player.getClass().getMethod("sendChatMessage", String.class).invoke(player, "/" + cmd);
            }
            return "sent: " + cmd;
        }
    } catch (Exception e) {
        return "{\"error\":\"" + e.getMessage() + "\"}";
    }
}
```

Note: For legacy versions, sending commands via chat message (`"/" + cmd`) is the safest approach since packet construction varies heavily. Simplify `sendCommand` to just try `connection.sendCommand` then fall back to `sendChatMessage`:

```java
public static String sendCommand(Object mc, String cmd) {
    try {
        Object player = getPlayer(mc);
        if (player == null) return "{\"error\":\"no player\"}";
        try {
            Object conn = null;
            try { conn = player.getClass().getMethod("connection").invoke(player); } catch (NoSuchMethodException e) {
                try {
                    Field f = player.getClass().getDeclaredField("connection");
                    f.setAccessible(true);
                    conn = f.get(player);
                } catch (NoSuchFieldException e2) {
                    try {
                        Field f = player.getClass().getDeclaredField("sendQueue");
                        f.setAccessible(true);
                        conn = f.get(player);
                    } catch (NoSuchFieldException e3) {
                        try {
                            Field f = player.getClass().getDeclaredField("field_71174_a");
                            f.setAccessible(true);
                            conn = f.get(player);
                        } catch (NoSuchFieldException ignored) {}
                    }
                }
            }
            if (conn != null) {
                try {
                    conn.getClass().getMethod("sendCommand", String.class).invoke(conn, cmd);
                    return "sent: " + cmd;
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Exception ignored) {}
        try {
            player.getClass().getMethod("sendChatMessage", String.class).invoke(player, "/" + cmd);
            return "sent: " + cmd;
        } catch (NoSuchMethodException e) {
            Method chatMethod = null;
            for (Method m : player.getClass().getMethods()) {
                if (m.getName().contains("chat") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) {
                    chatMethod = m;
                    break;
                }
            }
            if (chatMethod != null) {
                chatMethod.invoke(player, "/" + cmd);
                return "sent: " + cmd;
            }
        }
        return "{\"error\":\"no command method found\"}";
    } catch (Exception e) {
        return "{\"error\":\"" + e.getMessage() + "\"}";
    }
}
```

**Step 6: Commit**

```bash
git add mcp-common/src/main/java/com/mcbbs/mcp/common/ReflectionHelper.java
git commit -m "fix: multi-version player/level/dimension/command reflection for MC 1.7–26.x"
```

---

### Task 2: Fix LWJGL2 mouse click in ReflectedInputHandler

**Files:**
- Modify: `mcp-common/src/main/java/com/mcbbs/mcp/common/ReflectedInputHandler.java`
- Modify: `mcp-common/src/main/java/com/mcbbs/mcp/common/ReflectionHelper.java`

**Step 1: Add LWJGL2 mouse button helpers to ReflectionHelper**

```java
public static void lwjgl2SetMouseButton(int button, boolean state) {
    if (LWJGL3) return;
    try {
        Class<?> mouse = Class.forName("org.lwjgl.input.Mouse");
        // Set the event state fields
        Field eventButton = mouse.getDeclaredField("eventButton");
        eventButton.setAccessible(true);
        eventButton.setInt(null, button);
        Field eventState = mouse.getDeclaredField("eventButtonState");
        eventState.setAccessible(true);
        eventState.setBoolean(null, state);
        // Create the event
        mouse.getMethod("next").invoke(null);
    } catch (NoSuchFieldException e) {
        try {
            Class<?> mouse = Class.forName("org.lwjgl.input.Mouse");
            // Alternative: use setEventButton + setEventButtonState if available
            try {
                mouse.getMethod("setEventButton", int.class).invoke(null, button);
            } catch (NoSuchMethodException ignored) {}
            try {
                mouse.getMethod("setEventButtonState", boolean.class).invoke(null, state);
            } catch (NoSuchMethodException ignored) {}
            mouse.getMethod("next").invoke(null);
        } catch (Exception ignored) {}
    } catch (Exception e) {
        e.printStackTrace();
    }
}
```

**Step 2: Rewrite lwjgl2Click() in ReflectedInputHandler**

```java
private void lwjgl2Click(int x, int y, String button) {
    try {
        int b = "right".equals(button) ? 1 : "middle".equals(button) ? 2 : 0;
        Class<?> mouse = Class.forName("org.lwjgl.input.Mouse");
        mouse.getMethod("setGrabbed", boolean.class).invoke(null, false);
        mouse.getMethod("setCursorPosition", int.class, int.class).invoke(null, x, y);
        Thread.sleep(10);
        ReflectionHelper.lwjgl2SetMouseButton(b, true);
        Thread.sleep(30);
        ReflectionHelper.lwjgl2SetMouseButton(b, false);
    } catch (Exception e) {
        System.err.println("[Input] LWJGL2 Click: " + e.getMessage());
    }
}
```

**Step 3: Commit**

```bash
git add mcp-common/src/main/java/com/mcbbs/mcp/common/ReflectedInputHandler.java mcp-common/src/main/java/com/mcbbs/mcp/common/ReflectionHelper.java
git commit -m "fix: LWJGL2 mouse button events for MC 1.7–1.12"
```

---

### Task 3: Add message queue to McpWebSocketClient + fix handleMessages()

**Files:**
- Modify: `mcp-common/src/main/kotlin/com/mcbbs/mcp/common/McpWebSocketClient.kt`

**Step 1: Add ConcurrentLinkedQueue and drain in handleMessages()**

```kotlin
package xyz.langyo.minecraft.mcp.common

import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.concurrent.ConcurrentLinkedQueue

open class McpWebSocketClient(
    serverUrl: String,
    protected val handler: McpMessageHandler
) : WebSocketClient(URI.create(serverUrl)) {
    private val messageQueue = ConcurrentLinkedQueue<String>()

    init { connectionLostTimeout = 0 }

    fun connectAsync() {
        Thread({
            try { connect() } catch (e: Exception) {
                System.err.println("[MCP-WS] Connect failed: ${e.message}")
            }
        }, "MCP-WS-Connect").start()
    }

    override fun onOpen(handshake: ServerHandshake?) {
        System.out.println("[MCP-WS] Connected to MCP server")
        handler.sendInit(this)
    }

    override fun onMessage(message: String?) {
        if (message != null) messageQueue.add(message)
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        System.out.println("[MCP-WS] Disconnected: $reason (code=$code)")
    }

    override fun onError(ex: Exception?) {
        System.err.println("[MCP-WS] Error: ${ex?.message}")
    }

    override fun send(msg: String) {
        if (isOpen) super.send(msg)
    }

    open fun handleMessages() {
        var msg = messageQueue.poll()
        while (msg != null) {
            try {
                handler.handleMessage(msg, this)
            } catch (e: Exception) {
                System.err.println("[MCP-WS] Handle error: ${e.message}")
            }
            msg = messageQueue.poll()
        }
    }
}
```

**Step 2: Commit**

```bash
git add mcp-common/src/main/kotlin/com/mcbbs/mcp/common/McpWebSocketClient.kt
git commit -m "fix: add message queue to McpWebSocketClient for thread-safe tick-based processing"
```

---

### Task 4: Add system property server URL resolution + common URL helper

**Files:**
- Create: `mcp-common/src/main/java/com/mcbbs/mcp/common/McpConfig.java`
- Modify: All template functions in `scripts/generate_sources.py`

**Step 1: Create McpConfig.java**

```java
package xyz.langyo.minecraft.mcp.common;

public final class McpConfig {
    private McpConfig() {}

    public static String getServerUrl() {
        String url = System.getProperty("mcp.server");
        if (url != null && !url.isEmpty()) return url;
        url = System.getenv("MC_MCP_SERVER");
        if (url != null && !url.isEmpty()) return url;
        return "ws://127.0.0.1:9876";
    }
}
```

**Step 2: Update all mod templates to use McpConfig.getServerUrl()**

Replace in every template function:
```java
String serverUrl = System.getenv("MC_MCP_SERVER");
if (serverUrl == null || serverUrl.isEmpty()) serverUrl = "ws://127.0.0.1:9876";
```
With:
```java
String serverUrl = McpConfig.getServerUrl();
```

This applies to ALL template functions in `generate_sources.py`:
- `forge_mod_legacy_17()`
- `forge_mod_legacy()`
- `forge_mod_fg3()` (both variants)
- `forge_mod_fg4()` (also fg5, fg6 aliases)
- `forge_mod_fg7()`
- `forge_mod_mc26()`
- `neoforge_mod_1201()`
- `neoforge_mod_1204()`
- `neoforge_mod()`
- `fabric_mod()`

**Step 3: Commit**

```bash
git add mcp-common/src/main/java/com/mcbbs/mcp/common/McpConfig.java scripts/generate_sources.py
git commit -m "feat: add McpConfig for server URL from -Dmcp.server > env var > default"
```

---

### Task 5: Add Kotlin shadow/stdlib to mcp-common jar

**Files:**
- Modify: `mcp-common/build.gradle.kts`

The mod jars depend on mcp-common but mod classloaders may not have kotlin-stdlib. We need to either:
(a) Shadow kotlin stdlib into mcp-common jar, or
(b) Rewrite Kotlin files as pure Java

Option (b) is simpler and avoids classloader issues. Rewrite `McpProtocol.kt` and `McpWebSocketClient.kt` and `McpMessageHandler.kt` as Java.

**Step 1: Convert McpProtocol.kt to McpProtocol.java**

Delete `McpProtocol.kt`, create `McpProtocol.java`:

```java
package xyz.langyo.minecraft.mcp.common;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public final class McpProtocol {
    public static final Gson GSON = new Gson();

    private McpProtocol() {}

    public interface MinecraftInput {
        void click(int x, int y, String button);
        void pressKey(String key, float holdSeconds);
        void typeText(String text);
        void scroll(int clicks);
        void hotkey(String[] keys);
        byte[] screenshot();
        String executeCommand(String command);
        String getPlayerInfo();
        String getWorldInfo();
    }

    public interface ModLifecycle {
        void onInit(MinecraftInput input, Object wsClient);
        void onTick();
        void onShutdown();
    }
}
```

**Step 2: Convert McpWebSocketClient.kt to McpWebSocketClient.java**

**Step 3: Convert McpMessageHandler.kt to McpMessageHandler.java**

**Step 4: Update all imports in Java files from `MinecraftInput` (Kotlin interface) to `McpProtocol.MinecraftInput`**

**Step 5: Remove Kotlin plugin from build.gradle.kts**

**Step 6: Commit**

```bash
git add -A mcp-common/
git commit -m "refactor: convert Kotlin to pure Java, remove kotlin-stdlib dependency"
```

---

### Task 6: Regenerate all 76 mod source files + rebuild mcp-common

**Files:**
- Run: `python scripts/generate_sources.py`
- Run: build mcp-common and publish to .maven-local
- Run: `python scripts/build_all.py --no-cache`

**Step 1: Regenerate sources**

```bash
cd D:\源代码\工程项目\2026TeaCon\minecraft-neoforge-mcp
python scripts/generate_sources.py
```

**Step 2: Rebuild mcp-common**

```bash
cd mcp-common && gradlew clean publish && cd ..
```

**Step 3: Full build**

```bash
python scripts/build_all.py --no-cache
```

**Step 4: Verify 76/76**

**Step 5: Commit**

```bash
git add -A mods/
git commit -m "regenerate: all 76 mod sources with McpConfig + queue-based WS client"
```

---

### Task 7: Improve screenshot reliability

**Files:**
- Modify: `mcp-common/src/main/java/com/mcbbs/mcp/common/ReflectionHelper.java`

Replace `Thread.sleep(200)` with a proper wait/notify or CountDownLatch.

**Step 1: Replace async sleep with CountDownLatch**

In `takeScreenshot()`, replace:
```java
final Object[] result = new Object[1];
// ...
Thread.sleep(200);
if (!"ok".equals(result[0])) return null;
```

With:
```java
final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
final Throwable[] error = new Throwable[1];
// in captureTask: latch.countDown()
// after execute: latch.await(2, TimeUnit.SECONDS)
```

**Step 2: Commit**

```bash
git commit -m "fix: replace Thread.sleep with CountDownLatch for reliable screenshot capture"
```

---

### Task 8: Smoke test — build WS server + launch MC headless

**Files:**
- Create: `scripts/smoke_test.py`

**Step 1: Create a simple Python WebSocket server that accepts connections and sends test commands**

```python
# A minimal WS server that:
# 1. Listens on port 9876
# 2. Waits for mod to connect and send "initialize"
# 3. Sends ping, get_player_info, screenshot commands
# 4. Reports success/failure for each
```

**Step 2: Create smoke_test.py that iterates through all versions, launches MC with the mod, runs test commands**

For each version:
1. Start WS server on port 9876
2. Launch MC client with `-Dmcp.server=ws://127.0.0.1:9876` in headless/offscreen mode
3. Wait for connection + initialization message
4. Send test commands, verify responses
5. Kill MC process, record result

Note: This requires actual MC installations. For now, the script should:
- Download the Forge/Fabric installer for each version
- Run `gradlew runClient` or similar
- Use `-Djava.awt.headless=true` for headless mode

**Step 3: Commit**

```bash
git commit -m "test: add smoke test script for WS connection verification"
```

---

## Execution Order

1. Task 5 (Kotlin → Java) — must be first, affects all other files
2. Task 1 (ReflectionHelper fixes) — core bug fixes
3. Task 2 (LWJGL2 mouse fix) — depends on Task 1
4. Task 3 (Message queue) — independent
5. Task 4 (McpConfig + URL) — independent
6. Task 7 (Screenshot reliability) — independent
7. Task 6 (Regenerate + rebuild) — after all code changes
8. Task 8 (Smoke test) — after everything works

package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.minecraftforge.fml.common.Mod;

@Mod("mcpmod")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private final boolean dependenciesAvailable;

    public ModDevMcpMod() {
        INSTANCE = this;
        boolean depsOk = false;
        try {
            Class.forName("com.sun.jna.Library");
            Class.forName("org.java_websocket.client.WebSocketClient");
            depsOk = true;
        } catch (ClassNotFoundException e) {
            System.err.println("[MCP-MOD] WARNING: Required dependencies not on classpath (JNA or Java-WebSocket).");
            System.err.println("[MCP-MOD] External control facilities unavailable. Add JNA + Java-WebSocket to classpath or use launch_mc.py.");
            System.err.println("[MCP-MOD] Missing: " + e.getMessage());
        } catch (Error e) {
            System.err.println("[MCP-MOD] WARNING: Dependency load error: " + e.getMessage());
            System.err.println("[MCP-MOD] External control facilities unavailable.");
        }
        dependenciesAvailable = depsOk;

        if (depsOk) {
            new Thread(() -> {
                try {
                    String serverUrl = McpConfig.getServerUrl();
                    ReflectedInputHandler handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
                    wsClient = new McpWebSocketClient(serverUrl, handler);
                    McpWebSocketClient.setInstance(wsClient);
                    wsClient.connectBlocking(30, java.util.concurrent.TimeUnit.SECONDS);
                    long start = System.currentTimeMillis();
                    while (true) {
                        try {
                            Thread.sleep(10);
                            if (wsClient != null) wsClient.handleMessages();
                            if (System.currentTimeMillis() - start < 15000) continue;
                            Object mc = ReflectionHelper.getMinecraftInstance();
                            ReflectionHelper.tickForceCursorNormal(mc);
                            ReflectionHelper.tickVideoCapture(mc);
                        } catch (Exception e) { break; }
                    }
                } catch (Exception e) {
                    System.err.println("[MCP-MOD] WebSocket thread failed: " + e.getMessage());
                } catch (Error e) {
                    System.err.println("[MCP-MOD] WebSocket thread error (missing dependency?): " + e.getMessage());
                }
            }, "MCP-WebSocket").start();
        }
    }
}

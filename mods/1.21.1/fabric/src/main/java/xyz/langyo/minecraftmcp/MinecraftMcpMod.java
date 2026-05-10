package xyz.langyo.minecraftmcp;

import com.mcbbs.mcp.common.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class MinecraftMcpMod implements ClientModInitializer {
    private McpWebSocketClient wsClient;

    @Override
    public void onInitializeClient() {
        String serverUrl = System.getenv("MC_MCP_SERVER");
        if (serverUrl == null || serverUrl.isEmpty()) serverUrl = "ws://127.0.0.1:9876";
        ReflectedInputHandler handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        wsClient = new McpWebSocketClient(serverUrl, handler);
        wsClient.connectAsync();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (wsClient != null) wsClient.handleMessages();
        });
    }
}

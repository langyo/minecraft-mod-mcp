package xyz.langyo.minecraftmcp;

import com.mcbbs.mcp.common.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class MinecraftMcpMod implements ClientModInitializer {
    private McpWebSocketClient wsClient;
    private FabricInputHandler handler;

    @Override
    public void onInitializeClient() {
        handler = new FabricInputHandler();
        String serverUrl = System.getenv("MC_MCP_SERVER");
        if (serverUrl == null || serverUrl.isEmpty()) serverUrl = "ws://127.0.0.1:9876";
        wsClient = new McpWebSocketClient(serverUrl, handler);
        wsClient.connectAsync();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (wsClient != null) wsClient.handleMessages();
        });
    }
}

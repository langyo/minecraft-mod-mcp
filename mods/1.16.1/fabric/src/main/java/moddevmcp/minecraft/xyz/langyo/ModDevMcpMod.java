package moddevmcp.minecraft.xyz.langyo;

import com.mcbbs.mcp.common.*;
import net.fabricmc.api.ClientModInitializer;

public class ModDevMcpMod implements ClientModInitializer {
    private McpWebSocketClient wsClient;

    @Override
    public void onInitializeClient() {
        String serverUrl = System.getenv("MC_MCP_SERVER");
        if (serverUrl == null || serverUrl.isEmpty()) serverUrl = "ws://127.0.0.1:9876";
        ReflectedInputHandler handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        wsClient = new McpWebSocketClient(serverUrl, handler);
        wsClient.connectAsync();
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(50);
                    if (wsClient != null) wsClient.handleMessages();
                } catch (Exception e) { break; }
            }
        }).start();
    }
}

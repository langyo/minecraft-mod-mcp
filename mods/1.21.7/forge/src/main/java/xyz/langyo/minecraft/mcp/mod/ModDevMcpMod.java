package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.minecraftforge.fml.common.Mod;

@Mod("mcpmod")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpWebSocketClient wsClient;

    public ModDevMcpMod() {
        INSTANCE = this;
        new Thread(() -> {
            String serverUrl = McpConfig.getServerUrl();
            ReflectedInputHandler handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
            wsClient = new McpWebSocketClient(serverUrl, handler);
            wsClient.connectAsync();
            while (true) {
                try {
                    Thread.sleep(50);
                    if (wsClient != null) wsClient.handleMessages();
                } catch (Exception e) { break; }
            }
        }).start();
    }
}

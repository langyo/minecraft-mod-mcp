package moddevmcp.minecraft.xyz.langyo;

import com.mcbbs.mcp.common.*;
import net.minecraftforge.fml.common.Mod;

@Mod("moddevmcp")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpWebSocketClient wsClient;

    public ModDevMcpMod() {
        INSTANCE = this;
        ReflectedInputHandler inputHandler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        McpMessageHandler msgHandler = new McpMessageHandler() {
            { minecraftInput = inputHandler; }
        };
        new Thread(() -> {
            try {
                String serverUrl = McpConfig.getServerUrl();
                wsClient = new McpWebSocketClient(serverUrl, msgHandler);
                wsClient.connectAsync();
                while (true) {
                    Thread.sleep(50);
                    if (wsClient != null) wsClient.handleMessages();
                }
            } catch (Exception e) { /* thread exit */ }
        }, "MCP-Main").start();
    }
}

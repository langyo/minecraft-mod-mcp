package moddevmcp.minecraft.xyz.langyo;

import com.mcbbs.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = "moddevmcp", name = "ModDev MCP", version = "1.0")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ReflectedInputHandler handler;

    @Mod.Instance("moddevmcp")
    public static ModDevMcpMod instance;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        INSTANCE = this;
        String serverUrl = System.getenv("MC_MCP_SERVER");
        if (serverUrl == null || serverUrl.isEmpty()) serverUrl = "ws://127.0.0.1:9876";
        handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
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

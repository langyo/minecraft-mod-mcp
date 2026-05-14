package xyz.langyo.minecraftmcp;

import com.mcbbs.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.IEventBus;

@Mod("minecraftmcp")
public class MinecraftMcpMod {
    public static MinecraftMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ReflectedInputHandler handler;

    public MinecraftMcpMod(IEventBus modBus) {
        INSTANCE = this;
        modBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        String serverUrl = System.getenv("MC_MCP_SERVER");
        if (serverUrl == null || serverUrl.isEmpty()) serverUrl = "ws://127.0.0.1:9876";
        handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        wsClient = new McpWebSocketClient(serverUrl, handler);
        wsClient.connectAsync();
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && wsClient != null) wsClient.handleMessages();
    }
}

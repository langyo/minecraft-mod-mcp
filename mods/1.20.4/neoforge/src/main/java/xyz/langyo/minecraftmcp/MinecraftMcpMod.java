package xyz.langyo.minecraftmcp;

import com.mcbbs.mcp.common.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod("minecraftmcp")
public class MinecraftMcpMod {
    public static MinecraftMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ReflectedInputHandler handler;

    public MinecraftMcpMod(IEventBus modBus) {
        INSTANCE = this;
        modBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
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

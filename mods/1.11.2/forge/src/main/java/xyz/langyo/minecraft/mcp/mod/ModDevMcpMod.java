package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.MinecraftForge;

@Mod(modid = "mcpmod", name = "ModDev MCP", version = "1.0")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ReflectedInputHandler handler;

    @Mod.Instance("mcpmod")
    public static ModDevMcpMod instance;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        INSTANCE = this;
        String serverUrl = McpConfig.getServerUrl();
        handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        wsClient = new McpWebSocketClient(serverUrl, handler);
        wsClient.connectAsync();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && wsClient != null) wsClient.handleMessages();
    }
}

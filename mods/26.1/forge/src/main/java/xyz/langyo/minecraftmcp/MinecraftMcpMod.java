package xyz.langyo.minecraftmcp;

import com.mcbbs.mcp.common.*;
import net.minecraftforge.event.TickEvent.ClientTickEvent.Post;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("minecraftmcp")
public class MinecraftMcpMod {
    public static MinecraftMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ForgeInputHandler handler;

    public MinecraftMcpMod(FMLJavaModLoadingContext context) {
        INSTANCE = this;
        var modBusGroup = context.getModBusGroup();
        FMLCommonSetupEvent.getBus(modBusGroup).addListener(this::commonSetup);
        Post.BUS.addListener(this::onClientTickPost);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        String serverUrl = System.getenv("MC_MCP_SERVER");
        if (serverUrl == null || serverUrl.isEmpty()) serverUrl = "ws://127.0.0.1:9876";
        handler = new ForgeInputHandler();
        wsClient = new McpWebSocketClient(serverUrl, handler);
        wsClient.connectAsync();
    }

    private void onClientTickPost(Post event) {
        if (wsClient != null) wsClient.handleMessages();
    }
}

package moddevmcp.minecraft.xyz.langyo;

import com.mcbbs.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;

@Mod("moddevmcp")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ReflectedInputHandler handler;

    public ModDevMcpMod(FMLJavaModLoadingContext context) {
        INSTANCE = this;
        context.getModEventBus().addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.addListener(this::onClientTickPost);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        String serverUrl = McpConfig.getServerUrl();
        handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        wsClient = new McpWebSocketClient(serverUrl, handler);
        wsClient.connectAsync();
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onClientTickPost(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && wsClient != null) wsClient.handleMessages();
    }
}

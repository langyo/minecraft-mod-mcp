package xyz.langyo.minecraftmcp;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("minecraft_mcp_example")
public class MinecraftMcpMod {
    public static final String MOD_ID = "minecraft_mcp_example";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static McpWebSocketClient wsClient;

    public MinecraftMcpMod(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            String wsUrl = System.getenv("MC_MCP_SERVER");
            if (wsUrl == null || wsUrl.isEmpty()) {
                wsUrl = "ws://127.0.0.1:9876";
            }
            LOGGER.info("[MCP Example] Connecting to MCP server at {}", wsUrl);
            wsClient = new McpWebSocketClient(wsUrl);
            wsClient.connect();
        });
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("[MCP Example] Client setup complete, registering input handlers");
        NeoForge.EVENT_BUS.register(InputSimulator.class);
    }

    @EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            if (wsClient != null && wsClient.isOpen()) {
                wsClient.processQueue();
            }
        }
    }
}

package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = "mcpmod", name = "ModDev MCP", version = "1.0")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpHttpServer httpServer;

    @Mod.Instance("mcpmod")
    public static ModDevMcpMod instance;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        INSTANCE = this;
        boolean depsOk = false;
        try { Class.forName("com.sun.jna.Library"); depsOk = true; } catch (Exception ignored) {}
        if (!depsOk) {
            System.err.println("[MCP-MOD] JNA not on classpath. External control unavailable.");
            return;
        }
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                ReflectedInputHandler handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
                int port = McpConfig.getServerPort();
                httpServer = new McpHttpServer(handler, port);
                httpServer.start();
                System.out.println("[MCP-MOD] Debug page: http://127.0.0.1:" + port + "/debug");
            } catch (Exception e) { System.err.println("[MCP-MOD] HTTP server failed: " + e.getMessage()); }
        }, "MCP-HTTP").start();
    }
}

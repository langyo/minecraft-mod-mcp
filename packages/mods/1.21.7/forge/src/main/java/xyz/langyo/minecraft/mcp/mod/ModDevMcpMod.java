package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.minecraftforge.fml.common.Mod;

@Mod("mcpmod")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpHttpServer httpServer;
    private final boolean dependenciesAvailable;

    public ModDevMcpMod() {
        INSTANCE = this;
        boolean depsOk = false;
        try {
            Class.forName("com.sun.jna.Library");
            depsOk = true;
        } catch (ClassNotFoundException e) {
            System.err.println("[MCP-MOD] WARNING: JNA not on classpath.");
            System.err.println("[MCP-MOD] External control facilities unavailable. Add JNA to classpath or use launch_mc.py.");
            System.err.println("[MCP-MOD] Missing: " + e.getMessage());
        } catch (Error e) {
            System.err.println("[MCP-MOD] WARNING: Dependency load error: " + e.getMessage());
            System.err.println("[MCP-MOD] External control facilities unavailable.");
        }
        dependenciesAvailable = depsOk;

        if (depsOk) {
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    ReflectedInputHandler handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
                    int port = McpConfig.getServerPort();
                    httpServer = new McpHttpServer(handler, port);
                    httpServer.start();
                } catch (Exception e) {
                    System.err.println("[MCP-MOD] HTTP server failed: " + e.getMessage());
                } catch (Error e) {
                    System.err.println("[MCP-MOD] HTTP server error (missing dependency?): " + e.getMessage());
                }
            }, "MCP-HTTP").start();
        }
    }
}

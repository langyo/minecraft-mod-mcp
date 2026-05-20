package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;

@Mod("mcpmod")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    McpHttpServer httpServer;
    volatile String debugUrl = null;
    volatile boolean chatSent = false;
    private final boolean dependenciesAvailable;

    public ModDevMcpMod() {
        INSTANCE = this;
        boolean depsOk = false;
        try {
            Class.forName("com.sun.jna.Library");
            depsOk = true;
        } catch (ClassNotFoundException e) {
            System.err.println("[MCP-MOD] JNA not on classpath. Use launch_mc.py.");
        } catch (Error e) {
            System.err.println("[MCP-MOD] Dependency error: " + e.getMessage());
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
                    debugUrl = "http://127.0.0.1:" + port + "/debug";
                    System.out.println("[MCP-MOD] Debug page: " + debugUrl);
                } catch (Exception e) {
                    System.err.println("[MCP-MOD] HTTP server failed: " + e.getMessage());
                } catch (Error e) {
                    System.err.println("[MCP-MOD] HTTP server error: " + e.getMessage());
                }
            }, "MCP-HTTP").start();
        }

        CustomizeGuiOverlayEvent.DebugText.BUS.addListener(event -> {
            if (debugUrl != null && event.getSide() == CustomizeGuiOverlayEvent.DebugText.Side.Left) {
                event.getText().add("\u00a7a[MCP] " + debugUrl);
            }
        });

        ScreenEvent.Render.Post.BUS.addListener(event -> {
            if (debugUrl == null) return;
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                net.minecraft.client.gui.GuiGraphics g = event.getGuiGraphics();
                String text = "[MCP] " + debugUrl;
                g.drawString(mc.font, text, 4, 4, 0xFF55FF55, true);
            } catch (Exception ignored) {}
        });

        TickEvent.ClientTickEvent.BUS.addListener(event -> {
            if (event.phase != TickEvent.Phase.END) return;
            if (debugUrl == null) return;
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                ReflectionHelper.tickMouseRelease(mc);
                ReflectionHelper.tickVideoCapture(mc);
            } catch (Exception ignored) {}
            if (chatSent) return;
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.gui == null || mc.gui.getChat() == null) return;
                chatSent = true;
                String url = debugUrl;
                Component msg = Component.empty()
                    .append(Component.literal("[MCP] Debug page: ").withStyle(s -> s.withColor(0x55FF55)))
                    .append(Component.literal(url).withStyle(s -> s
                        .withColor(0x5555FF)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(url)))
                    ));
                mc.gui.getChat().addMessage(msg);
            } catch (Exception ignored) {}
        });
    }
}

"""Generate Java source files for ALL mod projects.

Architecture: Each mod has a thin MinecraftMcpMod.java that handles FML lifecycle
and event registration, then delegates to ReflectedInputHandler from mcp-common
which uses reflection to handle ALL version differences at runtime.

All mod files use ReflectedInputHandler::executeOnRenderThread for MC thread
scheduling, so NO mod file needs to import any net.minecraft.* class directly.
"""
import os

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODS_DIR = os.path.join(BASE, "mods")
PKG = "xyz/langyo/minecraftmcp"


def api_group(mc):
    if mc in ("1.7.2","1.7.10","1.8","1.8.9","1.9","1.9.4","1.10","1.10.2",
              "1.11","1.11.2","1.12","1.12.2"):
        return "legacy"
    if mc == "1.13.2":
        return "fg3"
    if mc in ("1.14.4","1.15","1.15.2","1.16.1","1.16.3","1.16.4","1.16.5"):
        return "fg4"
    if mc in ("1.17.1","1.18","1.18.2","1.19","1.19.2"):
        return "fg5"
    if mc in ("1.19.3","1.19.4","1.20","1.20.1","1.20.2","1.20.3","1.20.4",
              "1.20.5","1.20.6","1.21","1.21.1","1.21.2","1.21.3"):
        return "fg6"
    if mc in ("1.21.4","1.21.5"):
        return "fg7"
    if mc.startswith("26."):
        return "mc26"
    return "unknown"


# ============================================================
# FORGE MOD TEMPLATES (event registration only)
# ============================================================

def forge_mod_legacy(mc):
    return """package xyz.langyo.minecraftmcp;

import com.mcbbs.mcp.common.*;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;

@Mod(modid = "minecraftmcp", name = "Minecraft MCP Bridge", version = "1.0")
public class MinecraftMcpMod {
    public static MinecraftMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ReflectedInputHandler handler;

    @Mod.Instance("minecraftmcp")
    public static MinecraftMcpMod instance;

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
"""

def forge_mod_fg3(mc):
    return """package xyz.langyo.minecraftmcp;

import com.mcbbs.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.event.TickEvent;

@Mod("minecraftmcp")
public class MinecraftMcpMod {
    public static MinecraftMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ReflectedInputHandler handler;

    public MinecraftMcpMod() {
        INSTANCE = this;
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(final FMLCommonSetupEvent event) {
        String serverUrl = System.getenv("MC_MCP_SERVER");
        if (serverUrl == null || serverUrl.isEmpty()) serverUrl = "ws://127.0.0.1:9876";
        handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        wsClient = new McpWebSocketClient(serverUrl, handler);
        wsClient.connectAsync();
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && wsClient != null) wsClient.handleMessages();
    }
}
"""

def forge_mod_fg4(mc):
    return """package xyz.langyo.minecraftmcp;

import com.mcbbs.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;

@Mod("minecraftmcp")
public class MinecraftMcpMod {
    public static MinecraftMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ReflectedInputHandler handler;

    public MinecraftMcpMod() {
        INSTANCE = this;
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        String serverUrl = System.getenv("MC_MCP_SERVER");
        if (serverUrl == null || serverUrl.isEmpty()) serverUrl = "ws://127.0.0.1:9876";
        handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        wsClient = new McpWebSocketClient(serverUrl, handler);
        wsClient.connectAsync();
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && wsClient != null) wsClient.handleMessages();
    }
}
"""

def forge_mod_fg5(mc):
    return forge_mod_fg4(mc)

def forge_mod_fg6(mc):
    return forge_mod_fg4(mc)

def forge_mod_fg7(mc):
    return """package xyz.langyo.minecraftmcp;

import com.mcbbs.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;

@Mod("minecraftmcp")
public class MinecraftMcpMod {
    public static MinecraftMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ReflectedInputHandler handler;

    public MinecraftMcpMod(FMLJavaModLoadingContext context) {
        INSTANCE = this;
        context.getModEventBus().addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.addListener(this::onClientTickPost);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        String serverUrl = System.getenv("MC_MCP_SERVER");
        if (serverUrl == null || serverUrl.isEmpty()) serverUrl = "ws://127.0.0.1:9876";
        handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        wsClient = new McpWebSocketClient(serverUrl, handler);
        wsClient.connectAsync();
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onClientTickPost(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && wsClient != null) wsClient.handleMessages();
    }
}
"""

def forge_mod_mc26(mc):
    return """package xyz.langyo.minecraftmcp;

import com.mcbbs.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.client.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;

@Mod("minecraftmcp")
public class MinecraftMcpMod {
    public static MinecraftMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ReflectedInputHandler handler;

    public MinecraftMcpMod(FMLJavaModLoadingContext context) {
        INSTANCE = this;
        context.getModEventBus().addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.addListener(TickEvent.ClientTick::new, event -> {
            if (wsClient != null) wsClient.handleMessages();
        });
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        String serverUrl = System.getenv("MC_MCP_SERVER");
        if (serverUrl == null || serverUrl.isEmpty()) serverUrl = "ws://127.0.0.1:9876";
        handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        wsClient = new McpWebSocketClient(serverUrl, handler);
        wsClient.connectAsync();
    }
}
"""


# ============================================================
# NEOFORGE MOD TEMPLATE
# ============================================================

def neoforge_mod(mc):
    return """package xyz.langyo.minecraftmcp;

import com.mcbbs.mcp.common.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod("minecraftmcp")
public class MinecraftMcpMod {
    public static MinecraftMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ReflectedInputHandler handler;

    public MinecraftMcpMod(IEventBus modBus) {
        INSTANCE = this;
        modBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(this::onClientTickPost);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        String serverUrl = System.getenv("MC_MCP_SERVER");
        if (serverUrl == null || serverUrl.isEmpty()) serverUrl = "ws://127.0.0.1:9876";
        handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        wsClient = new McpWebSocketClient(serverUrl, handler);
        wsClient.connectAsync();
    }

    private void onClientTickPost(ClientTickEvent.Post event) {
        if (wsClient != null) wsClient.handleMessages();
    }
}
"""


# ============================================================
# FABRIC MOD TEMPLATE
# ============================================================

def fabric_mod(mc):
    return """package xyz.langyo.minecraftmcp;

import com.mcbbs.mcp.common.*;
import net.fabricmc.api.ClientModInitializer;

public class MinecraftMcpMod implements ClientModInitializer {
    private McpWebSocketClient wsClient;

    @Override
    public void onInitializeClient() {
        String serverUrl = System.getenv("MC_MCP_SERVER");
        if (serverUrl == null || serverUrl.isEmpty()) serverUrl = "ws://127.0.0.1:9876";
        ReflectedInputHandler handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
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
"""


# ============================================================
# WRITE TO PROJECTS
# ============================================================

ALL_VERSIONS = {
    "1.7.2": ["forge"], "1.7.10": ["forge"],
    "1.8": ["forge"], "1.8.9": ["forge"],
    "1.9": ["forge"], "1.9.4": ["forge"],
    "1.10": ["forge"], "1.10.2": ["forge"],
    "1.11": ["forge"], "1.11.2": ["forge"],
    "1.12": ["forge"], "1.12.2": ["forge"],
    "1.13.2": ["forge"],
    "1.14.4": ["forge","fabric"], "1.15": ["forge","fabric"], "1.15.2": ["forge","fabric"],
    "1.16.1": ["forge","fabric"], "1.16.3": ["forge","fabric"],
    "1.16.4": ["forge","fabric"], "1.16.5": ["forge","fabric"],
    "1.17.1": ["forge","fabric"],
    "1.18": ["forge","fabric"], "1.18.2": ["forge","fabric"],
    "1.19": ["forge","fabric"], "1.19.2": ["forge","fabric"],
    "1.19.3": ["forge","fabric"], "1.19.4": ["forge","fabric"],
    "1.20": ["forge","fabric"],
    "1.20.1": ["forge","neoforge","fabric"],
    "1.20.2": ["forge","neoforge","fabric"],
    "1.20.3": ["forge","neoforge","fabric"],
    "1.20.4": ["forge","neoforge","fabric"],
    "1.20.5": ["neoforge","fabric"],
    "1.20.6": ["forge","neoforge","fabric"],
    "1.21": ["forge","fabric"],
    "1.21.1": ["forge","neoforge","fabric"],
    "1.21.2": ["neoforge","fabric"],
    "1.21.3": ["forge","neoforge","fabric"],
    "1.21.4": ["forge","neoforge","fabric"],
    "1.21.5": ["forge","neoforge","fabric"],
    "26.1": ["forge"],
    "26.1.1": ["forge","neoforge"],
    "26.1.2": ["forge","neoforge"],
}

MODS_TOML = """modLoader="javafml"
loaderVersion="[4,)"
license="MIT"

[[mods]]
modId="minecraftmcp"
version="${version}"
displayName="Minecraft MCP Bridge"
description="WebSocket bridge for AI agent interaction"
authors="langyo"
"""

NEOFORGE_MODS_TOML = """modLoader = "javafml"
loaderVersion = "[4,)"
license = "MIT"

[[mods]]
modId = "minecraftmcp"
version = "${version}"
displayName = "Minecraft MCP Bridge"
description = "WebSocket bridge for AI agent interaction"
authors = "langyo"
"""

def write_java(path, filename, content):
    pkg_dir = os.path.join(path, "src", "main", "java", PKG)
    os.makedirs(pkg_dir, exist_ok=True)
    with open(os.path.join(pkg_dir, filename), "w", encoding="utf-8") as f:
        f.write(content)

def get_forge_mod_template(mc):
    g = api_group(mc)
    return {
        "legacy": forge_mod_legacy,
        "fg3": forge_mod_fg3,
        "fg4": forge_mod_fg4,
        "fg5": forge_mod_fg5,
        "fg6": forge_mod_fg6,
        "fg7": forge_mod_fg7,
        "mc26": forge_mod_mc26,
    }.get(g, forge_mod_fg6)(mc)

if __name__ == "__main__":
    total = 0
    for mc, loaders in ALL_VERSIONS.items():
        for loader in loaders:
            path = os.path.join(MODS_DIR, mc, loader)
            if not os.path.isdir(path):
                print(f"  SKIP (no project): {mc}/{loader}")
                continue

            if loader == "forge":
                write_java(path, "MinecraftMcpMod.java", get_forge_mod_template(mc))
                g = api_group(mc)
                if g in ("fg6","fg7","mc26"):
                    res_dir = os.path.join(path, "src", "main", "resources", "META-INF")
                    os.makedirs(res_dir, exist_ok=True)
                    with open(os.path.join(res_dir, "mods.toml"), "w") as f:
                        f.write(MODS_TOML)
                total += 1
            elif loader == "neoforge":
                write_java(path, "MinecraftMcpMod.java", neoforge_mod(mc))
                res_dir = os.path.join(path, "src", "main", "resources", "META-INF")
                os.makedirs(res_dir, exist_ok=True)
                with open(os.path.join(res_dir, "neoforge.mods.toml"), "w") as f:
                    f.write(NEOFORGE_MODS_TOML)
                total += 1
            elif loader == "fabric":
                write_java(path, "MinecraftMcpMod.java", fabric_mod(mc))
                total += 1

    print(f"Java source files written to {total} projects")

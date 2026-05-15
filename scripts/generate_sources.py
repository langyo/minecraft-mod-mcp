"""Generate Java source files for ALL mod projects.

Architecture: Each mod has a thin ModDevMcpMod.java that handles FML lifecycle
and event registration, then delegates to ReflectedInputHandler from mcp-common
which uses reflection to handle ALL version differences at runtime.

All mod files use ReflectedInputHandler::executeOnRenderThread for MC thread
scheduling, so NO mod file needs to import any net.minecraft.* class directly.

All version/group data comes from version_config.py — nothing is hardcoded here.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from version_config import ALL_VERSIONS, MODS_DIR, get_api_group, get_loaders

PKG = "moddevmcp/minecraft/xyz/langyo"
MOD_ID = "moddevmcp"
CLASS = "ModDevMcpMod"
PKG_DOTTED = "moddevmcp.minecraft.xyz.langyo"


# ============================================================
# FORGE MOD TEMPLATES (event registration only)
# ============================================================

def forge_mod_legacy_17(mc):
    return """package moddevmcp.minecraft.xyz.langyo;

import com.mcbbs.mcp.common.*;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;

@Mod(modid = "moddevmcp", name = "ModDev MCP", version = "1.0")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ReflectedInputHandler handler;

    @Mod.Instance("moddevmcp")
    public static ModDevMcpMod instance;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        INSTANCE = this;
        String serverUrl = McpConfig.getServerUrl();
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

def forge_mod_legacy(mc):
    return """package moddevmcp.minecraft.xyz.langyo;

import com.mcbbs.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = "moddevmcp", name = "ModDev MCP", version = "1.0")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ReflectedInputHandler handler;

    @Mod.Instance("moddevmcp")
    public static ModDevMcpMod instance;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        INSTANCE = this;
        String serverUrl = McpConfig.getServerUrl();
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
    if mc == "1.13.2":
        return """package moddevmcp.minecraft.xyz.langyo;

import com.mcbbs.mcp.common.*;
import net.minecraftforge.fml.common.Mod;

@Mod("moddevmcp")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ReflectedInputHandler handler;

    public ModDevMcpMod() {
        INSTANCE = this;
        String serverUrl = McpConfig.getServerUrl();
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
    return """package moddevmcp.minecraft.xyz.langyo;

import com.mcbbs.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.event.TickEvent;

@Mod("moddevmcp")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ReflectedInputHandler handler;

    public ModDevMcpMod() {
        INSTANCE = this;
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(final FMLCommonSetupEvent event) {
        String serverUrl = McpConfig.getServerUrl();
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
    return """package moddevmcp.minecraft.xyz.langyo;

import com.mcbbs.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;

@Mod("moddevmcp")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ReflectedInputHandler handler;

    public ModDevMcpMod() {
        INSTANCE = this;
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        String serverUrl = McpConfig.getServerUrl();
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
    return """package moddevmcp.minecraft.xyz.langyo;

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
"""

def forge_mod_mc26(mc):
    return """package moddevmcp.minecraft.xyz.langyo;

import com.mcbbs.mcp.common.*;
import net.minecraftforge.fml.common.Mod;

@Mod("moddevmcp")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpWebSocketClient wsClient;

    public ModDevMcpMod() {
        INSTANCE = this;
        new Thread(() -> {
            String serverUrl = McpConfig.getServerUrl();
            ReflectedInputHandler handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
            wsClient = new McpWebSocketClient(serverUrl, handler);
            wsClient.connectAsync();
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
# NEOFORGE MOD TEMPLATE — MC 1.20.1 (Forge-style API with neoforged namespace)
# ============================================================

def neoforge_mod_1201(mc):
    return """package moddevmcp.minecraft.xyz.langyo;

import com.mcbbs.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.IEventBus;

@Mod("moddevmcp")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ReflectedInputHandler handler;

    public ModDevMcpMod(IEventBus modBus) {
        INSTANCE = this;
        modBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        String serverUrl = McpConfig.getServerUrl();
        handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        wsClient = new McpWebSocketClient(serverUrl, handler);
        wsClient.connectAsync();
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && wsClient != null) wsClient.handleMessages();
    }
}
"""


def neoforge_mod_1204(mc):
    return """package moddevmcp.minecraft.xyz.langyo;

import com.mcbbs.mcp.common.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod("moddevmcp")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ReflectedInputHandler handler;

    public ModDevMcpMod(IEventBus modBus) {
        INSTANCE = this;
        modBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        String serverUrl = McpConfig.getServerUrl();
        handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        wsClient = new McpWebSocketClient(serverUrl, handler);
        wsClient.connectAsync();
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && wsClient != null) wsClient.handleMessages();
    }
}
"""


# NEOFORGE MOD TEMPLATE — MC 1.20.2+ (modern NeoForge API)
# ============================================================

def neoforge_mod(mc):
    return """package moddevmcp.minecraft.xyz.langyo;

import com.mcbbs.mcp.common.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod("moddevmcp")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ReflectedInputHandler handler;

    public ModDevMcpMod(IEventBus modBus) {
        INSTANCE = this;
        modBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(this::onClientTickPost);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        String serverUrl = McpConfig.getServerUrl();
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
    return """package moddevmcp.minecraft.xyz.langyo;

import com.mcbbs.mcp.common.*;
import net.fabricmc.api.ClientModInitializer;

public class ModDevMcpMod implements ClientModInitializer {
    private McpWebSocketClient wsClient;

    @Override
    public void onInitializeClient() {
        String serverUrl = McpConfig.getServerUrl();
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

PACK_MCMETA = """{
  "pack": {
    "description": "ModDev MCP resources",
    "pack_format": 34
  }
}
"""

MODS_TOML = """modLoader="javafml"
loaderVersion="[4,)"
license="MIT"

[[mods]]
modId="moddevmcp"
version="1.0.0"
displayName="ModDev MCP"
description="WebSocket bridge for AI agent interaction"
authors="langyo"
"""

NEOFORGE_MODS_TOML = """modLoader = "javafml"
loaderVersion = "[4,)"
license = "MIT"

[[mods]]
modId = "moddevmcp"
version = "1.0.0"
displayName = "ModDev MCP"
description = "WebSocket bridge for AI agent interaction"
authors = "langyo"
"""

def write_java(path, filename, content):
    pkg_dir = os.path.join(path, "src", "main", "java", PKG)
    os.makedirs(pkg_dir, exist_ok=True)
    with open(os.path.join(pkg_dir, filename), "w", encoding="utf-8") as f:
        f.write(content)

def get_forge_mod_template(mc):
    g = get_api_group(mc)
    return {
        "legacy_17": forge_mod_legacy_17,
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
    for mc, info in ALL_VERSIONS.items():
        for loader in get_loaders(mc):
            path = os.path.join(MODS_DIR, mc, loader)
            if not os.path.isdir(path):
                print(f"  SKIP (no project): {mc}/{loader}")
                continue

            if loader == "forge":
                write_java(path, "ModDevMcpMod.java", get_forge_mod_template(mc))
                g = get_api_group(mc)
                res_dir = os.path.join(path, "src", "main", "resources")
                os.makedirs(res_dir, exist_ok=True)
                if g in ("fg6","fg7","mc26"):
                    meta_dir = os.path.join(res_dir, "META-INF")
                    os.makedirs(meta_dir, exist_ok=True)
                    with open(os.path.join(meta_dir, "mods.toml"), "w") as f:
                        f.write(MODS_TOML)
                with open(os.path.join(res_dir, "pack.mcmeta"), "w") as f:
                    f.write(PACK_MCMETA)
                total += 1
            elif loader == "neoforge":
                nf_style = info.get("neoforge_style", "mdg")
                if nf_style == "fg6":
                    write_java(path, "ModDevMcpMod.java", neoforge_mod_1201(mc))
                elif mc == "1.20.4":
                    write_java(path, "ModDevMcpMod.java", neoforge_mod_1204(mc))
                else:
                    write_java(path, "ModDevMcpMod.java", neoforge_mod(mc))
                res_dir = os.path.join(path, "src", "main", "resources")
                meta_dir = os.path.join(res_dir, "META-INF")
                os.makedirs(meta_dir, exist_ok=True)
                with open(os.path.join(meta_dir, "neoforge.mods.toml"), "w") as f:
                    f.write(NEOFORGE_MODS_TOML)
                with open(os.path.join(res_dir, "pack.mcmeta"), "w") as f:
                    f.write(PACK_MCMETA)
                total += 1
            elif loader == "fabric":
                write_java(path, "ModDevMcpMod.java", fabric_mod(mc))
                total += 1

    print(f"Java source files written to {total} projects")

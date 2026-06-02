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

PKG = "xyz/langyo/minecraft/mcp/mod"
MOD_ID = "mcpmod"
CLASS = "ModDevMcpMod"
PKG_DOTTED = "xyz.langyo.minecraft.mcp.mod"


# ============================================================
# FORGE MOD TEMPLATES (event registration only)
# ============================================================

def forge_mod_legacy(mc):
    return """package xyz.langyo.minecraft.mcp.mod;

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
        ReflectedInputHandler handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        int port = McpConfig.getServerPort();
        httpServer = new McpHttpServer(handler, port);
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                httpServer.start();
            } catch (Exception e) {
                System.err.println("[MCP-MOD] HTTP server failed: " + e.getMessage());
            }
        }, "MCP-HTTP").start();
    }
}
"""

def forge_mod_fg3(mc):
    if mc == "1.13.2":
        return """package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.minecraftforge.fml.common.Mod;

@Mod("mcpmod")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpHttpServer httpServer;

    public ModDevMcpMod() {
        INSTANCE = this;
        ReflectedInputHandler handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        int port = McpConfig.getServerPort();
        httpServer = new McpHttpServer(handler, port);
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                httpServer.start();
            } catch (Exception e) {
                System.err.println("[MCP-MOD] HTTP server failed: " + e.getMessage());
            }
        }, "MCP-HTTP").start();
    }
}
"""
    return """package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod("mcpmod")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpHttpServer httpServer;

    public ModDevMcpMod() {
        INSTANCE = this;
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(final FMLCommonSetupEvent event) {
        ReflectedInputHandler handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        int port = McpConfig.getServerPort();
        httpServer = new McpHttpServer(handler, port);
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                httpServer.start();
            } catch (Exception e) {
                System.err.println("[MCP-MOD] HTTP server failed: " + e.getMessage());
            }
        }, "MCP-HTTP").start();
    }
}
"""

def forge_mod_fg4(mc):
    return """package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod("mcpmod")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpHttpServer httpServer;

    public ModDevMcpMod() {
        INSTANCE = this;
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(final FMLCommonSetupEvent event) {
        ReflectedInputHandler handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        int port = McpConfig.getServerPort();
        httpServer = new McpHttpServer(handler, port);
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                httpServer.start();
            } catch (Exception e) {
                System.err.println("[MCP-MOD] HTTP server failed: " + e.getMessage());
            }
        }, "MCP-HTTP").start();
    }
}
"""

def forge_mod_fg5(mc):
    return forge_mod_fg4(mc)

def forge_mod_fg6(mc):
    return forge_mod_fg4(mc)

def forge_mod_fg7(mc):
    return """package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("mcpmod")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpHttpServer httpServer;

    public ModDevMcpMod(FMLJavaModLoadingContext context) {
        INSTANCE = this;
        context.getModEventBus().addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        ReflectedInputHandler handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        int port = McpConfig.getServerPort();
        httpServer = new McpHttpServer(handler, port);
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                httpServer.start();
            } catch (Exception e) {
                System.err.println("[MCP-MOD] HTTP server failed: " + e.getMessage());
            }
        }, "MCP-HTTP").start();
    }
}
"""

def forge_mod_mc26(mc):
    return """package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.minecraftforge.fml.common.Mod;

@Mod("mcpmod")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpHttpServer httpServer;

    public ModDevMcpMod() {
        INSTANCE = this;
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                ReflectedInputHandler handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
                int port = McpConfig.getServerPort();
                httpServer = new McpHttpServer(handler, port);
                httpServer.start();
            } catch (Exception e) {
                System.err.println("[MCP-MOD] HTTP server failed: " + e.getMessage());
            }
        }, "MCP-HTTP").start();
    }
}
"""


# ============================================================
# NEOFORGE MOD TEMPLATE — MC 1.20.1 (Forge-style API with neoforged namespace)
# ============================================================

def neoforge_mod_1201(mc):
    return """package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod("mcpmod")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpHttpServer httpServer;

    public ModDevMcpMod(IEventBus modBus) {
        INSTANCE = this;
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        ReflectedInputHandler handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        int port = McpConfig.getServerPort();
        httpServer = new McpHttpServer(handler, port);
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                httpServer.start();
            } catch (Exception e) {
                System.err.println("[MCP-MOD] HTTP server failed: " + e.getMessage());
            }
        }, "MCP-HTTP").start();
    }
}
"""


def neoforge_mod_1204(mc):
    return """package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod("mcpmod")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpHttpServer httpServer;

    public ModDevMcpMod(IEventBus modBus) {
        INSTANCE = this;
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        ReflectedInputHandler handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        int port = McpConfig.getServerPort();
        httpServer = new McpHttpServer(handler, port);
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                httpServer.start();
            } catch (Exception e) {
                System.err.println("[MCP-MOD] HTTP server failed: " + e.getMessage());
            }
        }, "MCP-HTTP").start();
    }
}
"""


# NEOFORGE MOD TEMPLATE — MC 1.20.2+ (modern NeoForge API)
# ============================================================

def neoforge_mod(mc):
    return """package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod("mcpmod")
public class ModDevMcpMod {
    public static ModDevMcpMod INSTANCE;
    private McpHttpServer httpServer;

    public ModDevMcpMod(IEventBus modBus) {
        INSTANCE = this;
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        ReflectedInputHandler handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        int port = McpConfig.getServerPort();
        httpServer = new McpHttpServer(handler, port);
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                httpServer.start();
            } catch (Exception e) {
                System.err.println("[MCP-MOD] HTTP server failed: " + e.getMessage());
            }
        }, "MCP-HTTP").start();
    }
}
"""


# ============================================================
# FABRIC MOD TEMPLATE
# ============================================================

def fabric_mod(mc):
    return """package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.fabricmc.api.ClientModInitializer;

public class ModDevMcpMod implements ClientModInitializer {
    private McpHttpServer httpServer;

    @Override
    public void onInitializeClient() {
        ReflectedInputHandler handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        int port = McpConfig.getServerPort();
        httpServer = new McpHttpServer(handler, port);
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                httpServer.start();
            } catch (Exception e) {
                System.err.println("[MCP-MOD] HTTP server failed: " + e.getMessage());
            }
        }, "MCP-HTTP").start();
    }
}
"""


# ============================================================
# WRITE TO PROJECTS
# ============================================================

PACK_MCMETA = """{
  "pack": {
    "description": {
      "en_us": "ModDev MCP resources",
      "zh_cn": "ModDev MCP \\u8d44\\u6e90\\u5305",
      "zh_tw": "ModDev MCP \\u8cc7\\u6e90\\u5305",
      "ja_jp": "ModDev MCP \\u30ea\\u30bd\\u30fc\\u30b9\\u30d1\\u30c3\\u30af",
      "ko_kr": "ModDev MCP \\ub9ac\\uc18c\\uc2a4 \\ud329",
      "fr_fr": "Pack de ressources ModDev MCP",
      "es_es": "Paquete de recursos ModDev MCP",
      "ru_ru": "\\u041f\\u0430\\u043a\\u0435\\u0442 \\u0440\\u0435\\u0441\\u0443\\u0440\\u0441\\u043e\\u0432 ModDev MCP"
    },
    "pack_format": 34
  }
}
"""

MODS_TOML = """modLoader="javafml"
loaderVersion="[4,)"
license="MIT"

[[mods]]
modId="mcpmod"
version="0.1.1"
displayName="ModDev MCP"
description="WebSocket bridge for AI agent interaction"
authors="langyo"

[mods.description_localized]
en_us = "WebSocket bridge for AI agent interaction"
zh_cn = "\\u7528\\u4e8e AI \\u4ee3\\u7406\\u4ea4\\u4e92\\u7684 Minecraft WebSocket \\u6865\\u63a5\\u6a21\\u7ec4"
zh_tw = "\\u7528\\u65bc AI \\u4ee3\\u7406\\u4ea4\\u4e92\\u7684 Minecraft WebSocket \\u6a4b\\u63a5\\u6a21\\u7d44"
ja_jp = "AI\\u30a8\\u30fc\\u30b8\\u30a7\\u30f3\\u30c8\\u9023\\u643a\\u306e\\u305f\\u3081\\u306eMinecraft WebSocket\\u30d6\\u30ea\\u30c3\\u30b8MOD"
ko_kr = "AI \\uc5d0\\uc774\\uc804\\ud2b8 \\uc0c1\\ud638\\uc791\\uc6a9\\uc744 \\uc704\\ud55c Minecraft WebSocket \\ube0c\\ub9ac\\uc9c0 \\ubaa8\\ub4dc"
fr_fr = "Pont WebSocket pour l'interaction d'agents IA avec Minecraft"
es_es = "Puente WebSocket para la interacci\\u00f3n de agentes IA en Minecraft"
ru_ru = "WebSocket-\\u043c\\u043e\\u0441\\u0442 \\u0434\\u043b\\u044f \\u0432\\u0437\\u0430\\u0438\\u043c\\u043e\\u0434\\u0435\\u0439\\u0441\\u0442\\u0432\\u0438\\u044f AI-\\u0430\\u0433\\u0435\\u043d\\u0442\\u043e\\u0432 \\u0441 Minecraft"
"""

MCMOD_INFO = """[
  {
    "modid": "mcpmod",
    "name": "ModDev MCP",
    "description": "WebSocket bridge for AI agent interaction",
    "description_localized": {
      "en_us": "WebSocket bridge for AI agent interaction",
      "zh_cn": "\\u7528\\u4e8e AI \\u4ee3\\u7406\\u4ea4\\u4e92\\u7684 Minecraft WebSocket \\u6865\\u63a5\\u6a21\\u7ec4",
      "zh_tw": "\\u7528\\u65bc AI \\u4ee3\\u7406\\u4ea4\\u4e92\\u7684 Minecraft WebSocket \\u6a4b\\u63a5\\u6a21\\u7d44",
      "ja_jp": "AI\\u30a8\\u30fc\\u30b8\\u30a7\\u30f3\\u30c8\\u9023\\u643a\\u306e\\u305f\\u3081\\u306eMinecraft WebSocket\\u30d6\\u30ea\\u30c3\\u30b8MOD",
      "ko_kr": "AI \\uc5d0\\uc774\\uc804\\ud2b8 \\uc0c1\\ud638\\uc791\\uc6a9\\uc744 \\uc704\\ud55c Minecraft WebSocket \\ube0c\\ub9ac\\uc9c0 \\ubaa8\\ub4dc",
      "fr_fr": "Pont WebSocket pour l'interaction d'agents IA avec Minecraft",
      "es_es": "Puente WebSocket para la interacci\\u00f3n de agentes IA en Minecraft",
      "ru_ru": "WebSocket-\\u043c\\u043e\\u0441\\u0442 \\u0434\\u043b\\u044f \\u0432\\u0437\\u0430\\u0438\\u043c\\u043e\\u0434\\u0435\\u0439\\u0441\\u0442\\u0432\\u0438\\u044f AI-\\u0430\\u0433\\u0435\\u043d\\u0442\\u043e\\u0432 \\u0441 Minecraft"
    },
    "version": "0.1.1",
    "authorList": ["langyo"],
    "credits": ""
  }
]
"""

NEOFORGE_MODS_TOML = """modLoader = "javafml"
loaderVersion = "[4,)"
license = "MIT"

[[mods]]
modId = "mcpmod"
version="0.1.1"
displayName = "ModDev MCP"
description = "WebSocket bridge for AI agent interaction"
authors = "langyo"

[mods.description_localized]
en_us = "WebSocket bridge for AI agent interaction"
zh_cn = "\\u7528\\u4e8e AI \\u4ee3\\u7406\\u4ea4\\u4e92\\u7684 Minecraft WebSocket \\u6865\\u63a5\\u6a21\\u7ec4"
zh_tw = "\\u7528\\u65bc AI \\u4ee3\\u7406\\u4ea4\\u4e92\\u7684 Minecraft WebSocket \\u6a4b\\u63a5\\u6a21\\u7d44"
ja_jp = "AI\\u30a8\\u30fc\\u30b8\\u30a7\\u30f3\\u30c8\\u9023\\u643a\\u306e\\u305f\\u3081\\u306eMinecraft WebSocket\\u30d6\\u30ea\\u30c3\\u30b8MOD"
ko_kr = "AI \\uc5d0\\uc774\\uc804\\ud2b8 \\uc0c1\\ud638\\uc791\\uc6a9\\uc744 \\uc704\\ud55c Minecraft WebSocket \\ube0c\\ub9ac\\uc9c0 \\ubaa8\\ub4dc"
fr_fr = "Pont WebSocket pour l'interaction d'agents IA avec Minecraft"
es_es = "Puente WebSocket para la interacci\\u00f3n de agentes IA en Minecraft"
ru_ru = "WebSocket-\\u043c\\u043e\\u0441\\u0442 \\u0434\\u043b\\u044f \\u0432\\u0437\\u0430\\u0438\\u043c\\u043e\\u0434\\u0435\\u0439\\u0441\\u0442\\u0432\\u0438\\u044f AI-\\u0430\\u0433\\u0435\\u043d\\u0442\\u043e\\u0432 \\u0441 Minecraft"
"""

def write_java(path, filename, content):
    pkg_dir = os.path.join(path, "src", "main", "java", PKG)
    os.makedirs(pkg_dir, exist_ok=True)
    with open(os.path.join(pkg_dir, filename), "w", encoding="utf-8") as f:
        f.write(content)

def _read_ref_source(mc):
    ref = os.path.join(MODS_DIR, mc, "forge", "src", "main", "java", PKG, "ModDevMcpMod.java")
    if os.path.isfile(ref):
        with open(ref, encoding="utf-8") as f:
            return f.read()
    return None


def get_forge_mod_template(mc):
    g = get_api_group(mc)
    if g == "legacy17":
        src = _read_ref_source(mc)
        if src:
            return src
    return {
        "legacy": forge_mod_legacy,
        "legacy17": forge_mod_legacy,
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
                if g in ("fg3","fg4","fg5","fg6","fg7","mc26"):
                    meta_dir = os.path.join(res_dir, "META-INF")
                    os.makedirs(meta_dir, exist_ok=True)
                    with open(os.path.join(meta_dir, "mods.toml"), "w") as f:
                        f.write(MODS_TOML)
                elif g in ("legacy", "legacy17"):
                    with open(os.path.join(res_dir, "mcmod.info"), "w") as f:
                        f.write(MCMOD_INFO)
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

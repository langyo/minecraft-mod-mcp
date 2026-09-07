"""Generate Java source files for ALL mod projects.

Architecture: Each mod has a thin ModDevMcpMod.java that handles FML lifecycle
and event registration, then delegates to ReflectedInputHandler from mcp-common
which uses reflection to handle ALL version differences at runtime.

All mod files use ReflectedInputHandler::executeOnRenderThread for MC thread
scheduling, so NO mod file needs to import any net.minecraft.* class directly.

Exception: the top-right overlay (resume / transfer buttons) needs real
render + input hooks. Those hooks are version-specific — every generated mod
carries a small overlay block that adapts its era's render API (GL11 /
MatrixStack / GuiGraphics / DrawContext / 26.x GuiGraphicsExtractor) to
mcp-common's McpOverlayLogic + ControlModeHelper. The 1.7.x reference
implementations live in packages/mods/1.7.x (see _read_ref_source).

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


def mc_key(mc):
    """Numeric sort/compare key for MC version strings ("1.21.2" -> (1,21,2))."""
    parts = []
    for chunk in mc.split("."):
        num = ""
        for ch in chunk:
            if ch.isdigit():
                num += ch
            else:
                break
        parts.append(int(num) if num else 0)
    while len(parts) < 3:
        parts.append(0)
    return tuple(parts)


# ============================================================
# OVERLAY BLOCK — forge family (forge + neoforge, 1.13+)
# ============================================================
# Knobs pick the era's event/draw API; the logic mirrors the 1.7.2 reference:
#  - HUD (no screen open): in control mode draw the resume button and route
#    left-clicks to ControlModeHelper.handleOverlayClick.
#  - Screens: in control mode draw resume; otherwise (world loaded) draw the
#    transfer button and route clicks to handleTransferOverlayClick.
#  - PauseScreen gets an "MCP Take Over" button (where Button.builder exists).

def _overlay_renderer_java(draw):
    """McpRenderer adapter source for the era's draw flavor."""
    if draw == "gl":
        return """    private static McpRenderer makeRenderer() {
        return new McpRenderer() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {
                net.minecraft.client.gui.Gui.drawRect(x1, y1, x2, y2, color);
            }
            @Override public int drawString(Object font, String text, int x, int y, int color, boolean shadow) { return 0; }
            @Override public int getStringWidth(Object font, String text) { return 0; }
        };
    }"""
    if draw == "ms":
        # 1.17-1.19.2 official mappings: GuiComponent.fill(PoseStack, ...)
        return """    private static McpRenderer makeRenderer(final Object stack) {
        return new McpRenderer() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {
                net.minecraft.client.gui.GuiComponent.fill(
                        (com.mojang.blaze3d.vertex.PoseStack) stack, x1, y1, x2, y2, color);
            }
            @Override public int drawString(Object font, String text, int x, int y, int color, boolean shadow) { return 0; }
            @Override public int getStringWidth(Object font, String text) { return 0; }
        };
    }"""
    # "gg": GuiGraphics carries fill(int x1,y1,x2,y2,color)
    return """    private static McpRenderer makeRenderer(final Object graphics) {
        return new McpRenderer() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {
                ((net.minecraft.client.gui.GuiGraphics) graphics).fill(x1, y1, x2, y2, color);
            }
            @Override public int drawString(Object font, String text, int x, int y, int color, boolean shadow) { return 0; }
            @Override public int getStringWidth(Object font, String text) { return 0; }
        };
    }"""


def overlay_block_forge(ns, hud, screen, tick, draw, take_over, stack_acc="getMatrixStack"):
    """Build the overlay block for forge/neoforge 1.13+.

    ns:        "mf" (net.minecraftforge) or "neo" (net.neoforged.neoforge)
    hud:       "rgo" RenderGameOverlayEvent.Post | "gui" RenderGuiEvent.Post
               | "gui_overlay" RenderGuiOverlayEvent.Post
    screen:    "screen" ScreenEvent.Render.Post/MouseButtonPressed/Init.Post
               | "guiscreen" legacy GuiScreenEvent names
    tick:      "forge_tick" TickEvent.ClientTickEvent | "neo_post" ClientTickEvent.Post
    draw:      "gl" | "ms" (GuiComponent.fill + PoseStack) | "gg" (GuiGraphics.fill)
    take_over: add the PauseScreen button via ScreenEvent.Init.Post
    """
    subscribe = ("net.minecraftforge.eventbus.api.SubscribeEvent" if ns == "mf"
                 else "net.neoforged.bus.api.SubscribeEvent")

    if draw == "gg":
        draw_pre = ("            net.minecraft.client.gui.GuiGraphics g = "
                    "new net.minecraft.client.gui.GuiGraphics(\n"
                    "                    mc, mc.renderBuffers().bufferSource());\n")
        draw_ctx = "g"
        draw_post = "            g.flush();\n"
    else:
        draw_pre = ("            com.mojang.blaze3d.vertex.PoseStack g = "
                    "new com.mojang.blaze3d.vertex.PoseStack();\n")
        draw_ctx = "g"
        draw_post = ""

    if draw == "gl":
        renderer = _overlay_renderer_java("gl")
        render_ctx = "makeRenderer()"
        hud_arg = "Object stack"
        screen_arg = "makeRenderer()"
    elif draw == "ms":
        renderer = _overlay_renderer_java("ms")
        render_ctx = "makeRenderer(stack)"
        hud_arg = "Object stack"
        screen_arg = "makeRenderer(event.%s())" % ("getMatrixStack" if hud == "rgo" else "getPoseStack")
    else:
        renderer = _overlay_renderer_java("gg")
        render_ctx = "makeRenderer(graphics)"
        hud_arg = "Object graphics"
        screen_arg = "makeRenderer(event.getGuiGraphics())"

    if tick == "neo_tick":
        tick_code = """    @SUBSCRIBE
    public void onClientTick(net.neoforged.neoforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase == net.neoforged.neoforge.event.TickEvent.Phase.END) tickOverlay();
    }""".replace("SUBSCRIBE", subscribe)
    if tick == "neo_post":

        tick_code = """    @SUBSCRIBE
    public void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        tickOverlay();
    }""".replace("SUBSCRIBE", subscribe)
    else:
        tick_code = """    @SUBSCRIBE
    public void onClientTick(%PKG%.event.TickEvent.ClientTickEvent event) {
        if (event.phase == %PKG%.event.TickEvent.Phase.END) tickOverlay();
    }""".replace("SUBSCRIBE", subscribe).replace("%PKG%", "net.minecraftforge" if ns == "mf" else "net.neoforged.neoforge")

    take_over_code = ""
    if take_over:
        take_over_code = """
    @SUBSCRIBE
    public void onScreenInit(ScreenEventCls.Init.Post event) {
        if (xyz.langyo.minecraft.mcp.common.ReflectionHelper.isMcpControlMode()) return;
        net.minecraft.client.gui.screens.Screen s = event.getScreen();
        if (!(s instanceof net.minecraft.client.gui.screens.PauseScreen)) return;
        try {
            net.minecraft.client.gui.components.Button button = net.minecraft.client.gui.components.Button.builder(
                    net.minecraft.network.chat.Component.literal("MCP Take Over"),
                    btn -> xyz.langyo.minecraft.mcp.common.ControlModeHelper.enterMcpControlMode(
                            net.minecraft.client.Minecraft.getInstance()))
                    .bounds(s.width - 104, 44, 100, 20).build();
            for (Class<?> c = s.getClass(); c != null; c = c.getSuperclass()) {
                for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals("addRenderableWidget") && m.getParameterCount() == 1) {
                        m.setAccessible(true);
                        m.invoke(s, button);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}
    }""".replace("SUBSCRIBE", subscribe).replace("ScreenEventCls", "net.minecraftforge.client.event.ScreenEvent" if ns == "mf" else "net.neoforged.neoforge.client.event.ScreenEvent")

    screen_block = ""
    if screen == "none":
        screen_block = ""
    else:
        screen_block = """
    @SUBSCRIBE
    public void onScreenRender(ScreenEventCls.Render.Pre event) {
        renderScreenOverlay(SCREEN_ARG, event.getScreen(), (int) event.getMouseX(), (int) event.getMouseY());
    }

    @SUBSCRIBE
    public void onScreenClick(ScreenEventCls.MouseButtonPressed.Pre event) {
        if (event.getButton() == 0 && clickOverlay((int) event.getMouseX(), (int) event.getMouseY())) {
            event.setCanceled(true);
        }
    }TAKE_OVER""".replace("SUBSCRIBE", subscribe) \
        .replace("ScreenEventCls", "net.minecraftforge.client.event.ScreenEvent" if ns == "mf" else "net.neoforged.neoforge.client.event.ScreenEvent") \
        .replace("SCREEN_ARG", screen_arg) \
        .replace("TAKE_OVER", take_over_code)

    core = """
    // ===== top-right overlay (resume / transfer buttons) =====
    private static boolean prevLeftDown = false;

    RENDERER

    private static int[] scaledMouse(net.minecraft.client.Minecraft mc) {
        long win = mc.getWindow().getWindow();
        double[] px = new double[1], py = new double[1];
        org.lwjgl.glfw.GLFW.glfwGetCursorPos(win, px, py);
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        int mx = (int) (px[0] * w / (double) mc.getWindow().getWidth());
        int my = (int) (py[0] * h / (double) mc.getWindow().getHeight());
        return new int[]{mx, my};
    }

    private void tickOverlay() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            xyz.langyo.minecraft.mcp.common.ReflectionHelper.tickMouseRelease(mc);
            xyz.langyo.minecraft.mcp.common.ReflectionHelper.tickMcpControlMode(mc);
        } catch (Exception ignored) {}
    }

    private void renderHudOverlay(HUD_ARG) {
        try {
            if (xyz.langyo.minecraft.mcp.common.ReflectionHelper.isScreenshotInProgress()) return;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.screen != null || !xyz.langyo.minecraft.mcp.common.ReflectionHelper.isMcpControlMode()) return;
            int[] m = scaledMouse(mc);
            long win = mc.getWindow().getWindow();
            boolean left = org.lwjgl.glfw.GLFW.glfwGetMouseButton(win, 0) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            if (left && !prevLeftDown) {
                xyz.langyo.minecraft.mcp.common.ReflectionHelper.handleOverlayClick(m[0], m[1], mc);
            }
            prevLeftDown = left;
            if (!xyz.langyo.minecraft.mcp.common.ReflectionHelper.isScreenshotInProgress()) {
                xyz.langyo.minecraft.mcp.common.McpOverlayLogic.renderResumeButton(
                        RENDER_ARG, null, "",
                        mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), m[0], m[1]);
            }
        } catch (Exception ignored) {}
    }

    private void renderScreenOverlay(Object graphics, net.minecraft.client.gui.screens.Screen screen, int mx, int my) {
        try {
            if (screen == null || xyz.langyo.minecraft.mcp.common.ReflectionHelper.isScreenshotInProgress()) return;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            // The GuiGraphics handed to ScreenEvent.Render may not be flushed
            // into the final frame on some NeoForge builds — gg eras draw with
            // their own graphics over the shared buffer source and flush
            // explicitly; ms eras draw immediately via a fresh PoseStack.
            DRAW_PRE
            if (xyz.langyo.minecraft.mcp.common.ReflectionHelper.isMcpControlMode()) {
                xyz.langyo.minecraft.mcp.common.McpOverlayLogic.renderResumeButton(
                        makeRenderer(DRAW_CTX), null, "", screen.width, screen.height, mx, my);
            } else if (mc.level != null) {
                xyz.langyo.minecraft.mcp.common.McpOverlayLogic.renderTransferButton(
                        makeRenderer(DRAW_CTX), null, "", screen.width, screen.height, mx, my);
            }
            DRAW_POST
        } catch (Exception ignored) {}
    }

    private boolean clickOverlay(int mx, int my) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (xyz.langyo.minecraft.mcp.common.ReflectionHelper.isMcpControlMode()) {
                String r = xyz.langyo.minecraft.mcp.common.ReflectionHelper.handleOverlayClick(mx, my, mc);
                return !"blocked".equals(r) && !"cooldown".equals(r) && !"not_in_control_mode".equals(r);
            }
            if (mc.level != null && mc.screen != null) {
                String r = xyz.langyo.minecraft.mcp.common.ReflectionHelper.handleTransferOverlayClick(mx, my, mc);
                return "transfer_to_mcp".equals(r);
            }
        } catch (Exception ignored) {}
        return false;
    }
""".replace("RENDERER", renderer).replace("HUD_ARG", hud_arg).replace("RENDER_ARG", render_ctx).replace("DRAW_PRE", draw_pre).replace("DRAW_CTX", draw_ctx).replace("DRAW_POST", draw_post)

    hud_cls = ("net.minecraftforge" if ns == "mf" else "net.neoforged.neoforge") + ".client.event."
    if hud == "gui_overlay":
        ev_cls = hud_cls + "RenderGuiOverlayEvent"
        hud_sub = """    @SUBSCRIBE
    public void onHudOverlay(EV_CLS.Post event) {
        renderHudOverlay(event.STACK_ACC());
    }""".replace("SUBSCRIBE", subscribe).replace("EV_CLS", ev_cls).replace("STACK_ACC", stack_acc)
    elif hud == "rgo":
        ev_cls = hud_cls + "RenderGameOverlayEvent"
        hud_sub = """    @SUBSCRIBE
    public void onHudOverlay(EV_CLS.Post event) {
        if (event.getType() != EV_CLS.ElementType.CHAT) return;
        renderHudOverlay(event.STACK_ACC());
    }""".replace("SUBSCRIBE", subscribe).replace("EV_CLS", ev_cls).replace("STACK_ACC", stack_acc)
    else:
        ev_cls = hud_cls + "RenderGuiEvent"
        hud_sub = """    @SUBSCRIBE
    public void onHudOverlay(EV_CLS.Post event) {
        renderHudOverlay(event.getGuiGraphics());
    }""".replace("SUBSCRIBE", subscribe).replace("EV_CLS", ev_cls)

    subscribers = hud_sub + "\n" + screen_block + "\n" + tick_code

    game_bus = ("net.minecraftforge.common.MinecraftForge.EVENT_BUS"
                if ns == "mf" else "net.neoforged.neoforge.common.NeoForge.EVENT_BUS")
    register_code = "        %s.register(this);" % game_bus

    return core + "\n" + subscribers + "\n", register_code


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



def _forge_modern_body(mc, loader):
    """Overlay block + register statement for forge/neoforge 1.17+ templates.

    1.13.2-1.16.5 keep the plain lifecycle templates for now: their Forge
    event surface (GuiScreenEvent/AbstractGui mappings) needs its own verified
    pass — emitting official-mappings code there would not compile.
    """
    k = mc_key(mc)
    nf = loader == "neoforge"
    if nf:
        ns = "mf" if mc == "1.20.1" else "neo"
    else:
        ns = "mf"

    # Era selection for the HUD event + draw flavor. GuiGraphics only exists
    # from forge 1.20.1 / neoforge 1.20.4 — older eras draw with
    # GuiComponent.fill over a PoseStack taken from the event.
    if k <= (1, 16, 99):
        return "", ""   # legacy mappings eras: overlay block not emitted yet
    if k >= (26, 1, 0):
        return "", ""   # MC 26.x GUI extraction API: overlay hook is a follow-up
    elif k <= (1, 19, 99):
        # 1.17.1-1.19.4: 1.17.x is HUD-only (no ScreenEvent.Render yet);
        # 1.19.x carries the full screen hooks over a PoseStack.
        hud, draw, take_over = "gui_overlay" if k >= (1, 19, 0) else "rgo", "ms", False
        screen = "none" if k <= (1, 18, 99) else "screen"
        stack_acc = "getPoseStack" if k >= (1, 19, 0) else "getMatrixStack"
    else:
        hud, draw, take_over = "gui", "gg", True
        screen = "screen"
        stack_acc = "getGuiGraphics"
    if k <= (1, 16, 99):
        stack_acc = "getMatrixStack"

    if nf and mc == "1.20.1":
        tick = "forge_tick"
    elif nf and mc == "1.20.4":
        tick = "neo_tick"
    elif nf:
        tick = "neo_post"
    else:
        tick = "forge_tick"

    block, register = overlay_block_forge(ns, hud, screen, tick, draw, take_over, stack_acc)
    return block, register


def _forge_lifecycle_body(mc, setup_method_name, event_type, event_bus_line):
    return ""


def forge_mod_fg3(mc, loader="forge"):
    if mc == "1.13.2":
        block, register = _forge_modern_body(mc, loader)
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
        %s
    }
%s}
""" % (register, block)
    block, register = _forge_modern_body(mc, loader)
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
%s
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
%s}
""" % (("        " + register), block)


def forge_mod_fg4(mc, loader="forge"):
    block, register = _forge_modern_body(mc, loader)
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
%s
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
%s}
""" % (("        " + register), block)


def forge_mod_fg5(mc, loader="forge"):
    return forge_mod_fg4(mc, loader)


def forge_mod_fg6(mc, loader="forge"):
    return forge_mod_fg4(mc, loader)


def forge_mod_fg7(mc, loader="forge"):
    block, register = _forge_modern_body(mc, loader)
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
%s
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
%s}
""" % (("        " + register), block)


def forge_mod_mc26(mc, loader="forge"):
    block, register = _forge_modern_body(mc, loader)
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
                try { Object mc = ReflectionHelper.getMinecraftInstance(); if (mc != null) ReflectionHelper.setMinecraftInstance(mc); } catch (Exception ignored) {}
                ReflectedInputHandler handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
                int port = McpConfig.getServerPort();
                httpServer = new McpHttpServer(handler, port);
                httpServer.start();
            } catch (Exception e) {
                System.err.println("[MCP-MOD] HTTP server failed: " + e.getMessage());
            }
        }, "MCP-HTTP").start();
%s
    }
%s}
""" % (("        " + register), block)


# ============================================================
# NEOFORGE MOD TEMPLATES
# ============================================================

def neoforge_mod_1201(mc):
    block, register = _forge_modern_body(mc, "neoforge")
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
%s
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
%s}
""" % (("        " + register), block)


def neoforge_mod_1204(mc):
    block, register = _forge_modern_body(mc, "neoforge")
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
%s
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
%s}
""" % (("        " + register), block)


def neoforge_mod(mc):
    block, register = _forge_modern_body(mc, "neoforge")
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
%s
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        ReflectedInputHandler handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        int port = McpConfig.getServerPort();
        httpServer = new McpHttpServer(handler, port);
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                try { Object mc = ReflectionHelper.getMinecraftInstance(); if (mc != null) ReflectionHelper.setMinecraftInstance(mc); } catch (Exception ignored) {}
                httpServer.start();
            } catch (Exception e) {
                System.err.println("[MCP-MOD] HTTP server failed: " + e.getMessage());
            }
        }, "MCP-HTTP").start();
    }
%s}
""" % (("        " + register), block)


# ============================================================
# FABRIC MOD TEMPLATE
# ============================================================
# Every era declares the full callback surface (all historical mixin shapes
# coexist via overloads), while only the era's drawing core differs:
#   gl14  — 1.14.x fixed-function GL11 quads
#   ms    — 1.15-1.19.4 DrawableHelper.fill(MatrixStack, ...) (static)
#   dc    — 1.20-1.21.x + 26.1 DrawContext.fill(...)
#   ggx   — 26.2 GuiGraphicsExtractor (official names, unobfuscated era)

def _fabric_draw_core(mc):
    k = mc_key(mc)
    if k <= (1, 14, 99):
        return "gl14"
    if k <= (1, 15, 99):
        return "ms"
    if k < (1, 20, 0):
        return "ms"
    if k < (26, 2, 0):
        return "dc"
    return "ggx"


def _fabric_renderer_java(mc, core):
    if core == "gl14":
        return """    private McpRenderer makeRenderer(Object ctx) {
        return new McpRenderer() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {
                float a = ((color >> 24) & 0xFF) / 255f, r = ((color >> 16) & 0xFF) / 255f,
                      g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;
                org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
                org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_QUADS);
                org.lwjgl.opengl.GL11.glColor4f(r, g, b, a);
                org.lwjgl.opengl.GL11.glVertex2f(x1, y1);
                org.lwjgl.opengl.GL11.glVertex2f(x1, y2);
                org.lwjgl.opengl.GL11.glVertex2f(x2, y2);
                org.lwjgl.opengl.GL11.glVertex2f(x2, y1);
                org.lwjgl.opengl.GL11.glEnd();
                org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            }
            @Override public int drawString(Object font, String text, int x, int y, int color, boolean shadow) { return 0; }
            @Override public int getStringWidth(Object font, String text) { return 0; }
        };
    }"""
    if core == "ms":
        return """    private McpRenderer makeRenderer(Object ctx) {
        final net.minecraft.client.util.math.MatrixStack stack =
                (ctx instanceof net.minecraft.client.util.math.MatrixStack)
                        ? (net.minecraft.client.util.math.MatrixStack) ctx
                        : new net.minecraft.client.util.math.MatrixStack();
        return new McpRenderer() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {
                net.minecraft.client.gui.DrawableHelper.fill(stack, x1, y1, x2, y2, color);
            }
            @Override public int drawString(Object font, String text, int x, int y, int color, boolean shadow) { return 0; }
            @Override public int getStringWidth(Object font, String text) { return 0; }
        };
    }"""
    # dc / ggx: the context carries fill(int,int,int,int,int)
    ctx_type = ("net.minecraft.client.gui.GuiGraphicsExtractor" if core == "ggx"
                else "net.minecraft.client.gui.DrawContext")
    return """    private McpRenderer makeRenderer(Object ctx) {
        return new McpRenderer() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {
                if (ctx instanceof CTX_TYPE) {
                    ((CTX_TYPE) ctx).fill(x1, y1, x2, y2, color);
                }
            }
            @Override public int drawString(Object font, String text, int x, int y, int color, boolean shadow) { return 0; }
            @Override public int getStringWidth(Object font, String text) { return 0; }
        };
    }""".replace("CTX_TYPE", ctx_type)


def fabric_mod(mc):
    core = _fabric_draw_core(mc)
    k = mc_key(mc)
    # MC 26.x ships unobfuscated: official names at runtime for every loader.
    official = k >= (26, 2, 0)
    renderer = _fabric_renderer_java(mc, core)

    # 1.14.x yarn predates MinecraftClient.getWindow(); the overlay core has
    # no compile-clean form there yet, so keep the hooks but no overlay body.
    overlay_enabled = k > (1, 14, 99)

    mc_inst = "net.minecraft.client.Minecraft.getInstance()" if official \
        else "net.minecraft.client.MinecraftClient.getInstance()"
    # MC 26.2 renamed the accessors this block needs (official mappings):
    # world->level, currentScreen->screen, mouse->mouseHandler (scaled accessors),
    # Window.getScaledWidth->getGuiScaledWidth.
    if official:
        mouse_x = "mc.mouseHandler.getScaledXPos(mc.getWindow())"
        mouse_y = "mc.mouseHandler.getScaledYPos(mc.getWindow())"
        scaled_w = "mc.getWindow().getGuiScaledWidth()"
        scaled_h = "mc.getWindow().getGuiScaledHeight()"
        world_field = "mc.level"
        screen_field = "mc.gui.screen()"
    else:
        mouse_x = "mc.mouse.getX()"
        mouse_y = "mc.mouse.getY()"
        scaled_w = "mc.getWindow().getScaledWidth()"
        scaled_h = "mc.getWindow().getScaledHeight()"
        world_field = "mc.world"
        screen_field = "mc.currentScreen"

    overlay = """
    // ===== top-right overlay (resume / transfer buttons) =====
    RENDERER

    private static double[] scaledMouse(Object mcObj) {
        MC_TYPE mc = MC_INST;
        double mx = MOUSE_X;
        double my = MOUSE_Y;
        int w = SCALED_W;
        int h = SCALED_H;
        return new double[]{mx, my, w, h};
    }

    private void renderHudOverlay(Object ctx) {
        try {
            if (xyz.langyo.minecraft.mcp.common.ReflectionHelper.isScreenshotInProgress()) return;
            MC_TYPE mc = MC_INST;
            if (SCREEN_FIELD != null || !xyz.langyo.minecraft.mcp.common.ReflectionHelper.isMcpControlMode()) return;
            double[] m = scaledMouse(mc);
            xyz.langyo.minecraft.mcp.common.McpOverlayLogic.renderResumeButton(
                    makeRenderer(ctx), null, "", (int) m[2], (int) m[3], (int) m[0], (int) m[1]);
        } catch (Exception ignored) {}
    }

    private void renderScreenOverlay(Object ctx, Object screen, int mx, int my) {
        try {
            if (ctx == null || screen == null || xyz.langyo.minecraft.mcp.common.ReflectionHelper.isScreenshotInProgress()) return;
            MC_TYPE mc = MC_INST;
            int sw = ((SCREEN_FQCN) screen).width;
            int sh = ((SCREEN_FQCN) screen).height;
            if (xyz.langyo.minecraft.mcp.common.ReflectionHelper.isMcpControlMode()) {
                xyz.langyo.minecraft.mcp.common.McpOverlayLogic.renderResumeButton(
                        makeRenderer(ctx), null, "", sw, sh, mx, my);
            } else if (WORLD_FIELD != null) {
                xyz.langyo.minecraft.mcp.common.McpOverlayLogic.renderTransferButton(
                        makeRenderer(ctx), null, "", sw, sh, mx, my);
            }
        } catch (Exception ignored) {}
    }

    private boolean clickOverlay(Object mcObj, double mx, double my, int button) {
        try {
            if (button != 0) return false;
            MC_TYPE mc = MC_INST;
            if (xyz.langyo.minecraft.mcp.common.ReflectionHelper.isMcpControlMode()) {
                String r = xyz.langyo.minecraft.mcp.common.ReflectionHelper.handleOverlayClick((int) mx, (int) my, mc);
                return !"blocked".equals(r) && !"cooldown".equals(r) && !"not_in_control_mode".equals(r);
            }
            if (WORLD_FIELD != null && SCREEN_FIELD != null) {
                String r = xyz.langyo.minecraft.mcp.common.ReflectionHelper.handleTransferOverlayClick((int) mx, (int) my, mc);
                return "transfer_to_mcp".equals(r);
            }
        } catch (Exception ignored) {}
        return false;
    }
""".replace("MC_TYPE", "net.minecraft.client.Minecraft" if official else "net.minecraft.client.MinecraftClient") \
   .replace("SCREEN_FQCN", "net.minecraft.client.gui.screens.Screen" if official else "net.minecraft.client.gui.screen.Screen") \
   .replace("MC_INST", mc_inst).replace("RENDERER", renderer) \
   .replace("MOUSE_X", mouse_x).replace("MOUSE_Y", mouse_y) \
   .replace("SCALED_W", scaled_w).replace("SCALED_H", scaled_h) \
   .replace("WORLD_FIELD", world_field).replace("SCREEN_FIELD", screen_field)

    if not overlay_enabled:
        # 1.14.x era: no overlay core yet — hooks stay as compilable no-ops.
        overlay = ""
        hooks = """
    // ---- render hooks called by the version mixins / events (no-ops on this era) ----
    public void onInGameHudRender(Object ctx, float tickDelta) {}

    public void onScreenRender(Object ctx, Object screen, int mouseX, int mouseY, float tickDelta) {}

    public void onScreenRender(Object screen, int mouseX, int mouseY, float tickDelta) {}

    public boolean onMouseButtonEvent(Object mc, double mx, double my, int button) {
        return false;
    }

    public boolean onMouseClicked(double mx, double my, int button) {
        return false;
    }
"""
    else:
        hooks = """
    // ---- render hooks called by the version mixins / events ----
    public void onInGameHudRender(Object ctx, float tickDelta) {
        renderHudOverlay(ctx);
    }

    public void onScreenRender(Object ctx, Object screen, int mouseX, int mouseY, float tickDelta) {
        renderScreenOverlay(ctx, screen, mouseX, mouseY);
    }

    public void onScreenRender(Object screen, int mouseX, int mouseY, float tickDelta) {
        renderScreenOverlay(null, screen, mouseX, mouseY);
    }

    public boolean onMouseButtonEvent(Object mc, double mx, double my, int button) {
        return clickOverlay(mc, mx, my, button);
    }

    public boolean onMouseClicked(double mx, double my, int button) {
        return clickOverlay(null, mx, my, button);
    }
"""

    return """package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.*;
import net.fabricmc.api.ClientModInitializer;

public class ModDevMcpMod implements ClientModInitializer {
    public static ModDevMcpMod INSTANCE;
    private McpHttpServer httpServer;
    private ReflectedInputHandler handler;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        handler = new ReflectedInputHandler(ReflectedInputHandler::executeOnRenderThread);
        int port = McpConfig.getServerPort();
        httpServer = new McpHttpServer(handler, port);
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                try { Object mc = ReflectionHelper.getMinecraftInstance(); if (mc != null) ReflectionHelper.setMinecraftInstance(mc); } catch (Exception ignored) {}
                httpServer.start();
            } catch (Exception e) {
                System.err.println("[MCP-MOD] HTTP server failed: " + e.getMessage());
            }
        }, "MCP-HTTP").start();
    }

    public void onClientTick() {
        try {
            Object mc = ReflectionHelper.getMinecraftInstance();
            if (mc != null) ReflectionHelper.setMinecraftInstance(mc);
            ReflectionHelper.tickMouseRelease(mc);
            ReflectionHelper.tickMcpControlMode(mc);
        } catch (Exception ignored) {}
    }
""" + overlay + hooks + "}\n"


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
      "ko_kr": "ModDev MCP \\ub9ac\\uc18c\\uc2a4\\ud329",
      "fr_fr": "Pack de ressources ModDev MCP",
      "es_es": "Paquete de recursos ModDev MCP",
      "ru_ru": "WebSocket-\\u043c\\u043e\\u0441\\u0442 ModDev MCP"
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
zh_tw = "\\u7528\\u65bc AI \\u4ee3\\u7406\\u4ea4\\u4e92\\u7684 Minecraft WebSocket \\u6865\\u63a5\\u6a21\\u7d44"
ja_jp = "AI\\u30a8\\u30fc\\u30b8\\u30a7\\u30f3\\u30c8\\u9023\\u643a\\u306e\\u305f\\u3081\\u306eMinecraft WebSocket\\u30d6\\u30ea\\u30c3\\u30b8MOD"
ko_kr = "AI \\uc5d0\\uc774\\uc804\\ud2b8 \\uc0c1\\ud638\\uc791\\uc6a9\\uc744 \\uc704\\ud55c Minecraft WebSocket \\ube0c\\ub9ac\\uc9c0 \\ubaa8\\ub4dc"
fr_fr = "Pont WebSocket pour l'interaction d'agents IA avec Minecraft"
es_es = "Puente WebSocket para la interacci\\u00f3n de agentes IA con Minecraft"
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
      "zh_tw": "\\u7528\\u65bc AI \\u4ee3\\u7406\\u4ea4\\u4e92\\u7684 Minecraft WebSocket \\u6865\\u63a5\\u6a21\\u7d44",
      "ja_jp": "AI\\u30a8\\u30fc\\u30b8\\u30a7\\u30f3\\u30c8\\u9023\\u643a\\u306e\\u305f\\u3081\\u306eMinecraft WebSocket\\u30d6\\u30ea\\u30c3\\u30b8MOD",
      "ko_kr": "AI \\uc5d0\\uc774\\uc804\\ud2b8 \\uc0c1\\ud638\\uc791\\uc6a9\\uc744 \\uc704\\ud55c Minecraft WebSocket \\ube0c\\ub9ac\\uc9c0 \\ubaa8\\ub4dc",
      "fr_fr": "Pont WebSocket pour l'interaction d'agents IA avec Minecraft",
      "es_es": "Puente WebSocket para la interacci\\u00f3n de agentes IA con Minecraft",
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
zh_tw = "\\u7528\\u65bc AI \\u4ee3\\u7406\\u4ea4\\u4e92\\u7684 Minecraft WebSocket \\u6865\\u63a5\\u6a21\\u7d44"
ja_jp = "AI\\u30a8\\u30fc\\u30b8\\u30a7\\u30f3\\u30c8\\u9023\\u643a\\u306e\\u305f\\u3081\\u306eMinecraft WebSocket\\u30d6\\u30ea\\u30c3\\u30b8MOD"
ko_kr = "AI \\uc5d0\\uc774\\uc804\\ud2b8 \\uc0c1\\ud638\\uc791\\uc6a9\\uc744 \\uc704\\ud55c Minecraft WebSocket \\ube0c\\ub9ac\\uc9c0 \\ubaa8\\ub4dc"
fr_fr = "Pont WebSocket pour l'interaction d'agents IA avec Minecraft"
es_es = "Puente WebSocket para la interacci\\u00f3n de agentes IA con Minecraft"
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

"""Generate Java source files for ALL 81 mod projects.

API groups:
- legacy (1.7.2-1.12.2): LWJGL 2, old Forge event system, Minecraft.getMinecraft()
- fg3 (1.13.2): LWJGL 3, ForgeGradle 3, Minecraft.getInstance()
- fg4 (1.14.4-1.16.5): LWJGL 3, GLFW, getWindow(), getWindow().getHandle()
- fg5 (1.17.1-1.19.2): LWJGL 3, GLFW, Minecraft.getInstance().getWindow()
- fg6 (1.19.3-1.21.2): LWJGL 3, GLFW, official mappings, @SubscribeEvent
- fg7 (1.21.4-1.21.5): LWJGL 3, EventBus 7, FMLJavaModLoadingContext, getWindow().handle()
- mc26 (26.x): Java 25, no obfuscation, .handle(), .identifier(), .getSerializedName()
"""
import os

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODS_DIR = os.path.join(BASE, "mods")
PKG = "xyz/langyo/minecraftmcp"


def api_group(mc):
    if mc in ("1.7.2","1.7.10","1.8","1.8.9","1.9","1.9.4","1.10","1.10.2","1.11","1.11.2","1.12","1.12.2"):
        return "legacy"
    if mc == "1.13.2":
        return "fg3"
    if mc in ("1.14.4","1.15","1.15.2","1.16.1","1.16.3","1.16.4","1.16.5"):
        return "fg4"
    if mc in ("1.17.1","1.18","1.18.2","1.19","1.19.2"):
        return "fg5"
    if mc in ("1.19.3","1.19.4","1.20","1.20.1","1.20.2","1.20.3","1.20.4","1.20.5","1.20.6","1.21","1.21.1","1.21.2","1.21.3"):
        return "fg6"
    if mc in ("1.21.4","1.21.5"):
        return "fg7"
    if mc.startswith("26."):
        return "mc26"
    return "unknown"


# ============================================================
# FORGE MOD TEMPLATES
# ============================================================

def forge_mod_legacy(mc):
    """1.7.2-1.12.2: LWJGL 2, old @Mod, FMLCommonSetupEvent may not exist"""
    return f"""package xyz.langyo.minecraftmcp;

import com.mcbbs.mcp.common.*;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

@Mod(modid = "minecraftmcp", name = "Minecraft MCP Bridge", version = "1.0")
public class MinecraftMcpMod {{
    public static MinecraftMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ForgeInputHandler handler;

    @Mod.Instance("minecraftmcp")
    public static MinecraftMcpMod instance;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {{
        INSTANCE = this;
        String serverUrl = System.getenv("MC_MCP_SERVER");
        if (serverUrl == null || serverUrl.isEmpty()) serverUrl = "ws://127.0.0.1:9876";
        handler = new ForgeInputHandler();
        wsClient = new McpWebSocketClient(serverUrl, handler);
        wsClient.connectAsync();
        new Thread(() -> {{
            while (true) {{
                try {{
                    Thread.sleep(50);
                    if (wsClient != null) wsClient.handleMessages();
                }} catch (Exception e) {{ break; }}
            }}
        }}).start();
    }}
}}
"""

def forge_mod_fg3(mc):
    """1.13.2: @Mod annotation, SubscribeEvent"""
    return f"""package xyz.langyo.minecraftmcp;

import com.mcbbs.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.event.TickEvent;

@Mod("minecraftmcp")
public class MinecraftMcpMod {{
    public static MinecraftMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ForgeInputHandler handler;

    public MinecraftMcpMod() {{
        INSTANCE = this;
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }}

    private void setup(final FMLCommonSetupEvent event) {{
        String serverUrl = System.getenv("MC_MCP_SERVER");
        if (serverUrl == null || serverUrl.isEmpty()) serverUrl = "ws://127.0.0.1:9876";
        handler = new ForgeInputHandler();
        wsClient = new McpWebSocketClient(serverUrl, handler);
        wsClient.connectAsync();
    }}

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {{
        if (event.phase == TickEvent.Phase.END && wsClient != null) wsClient.handleMessages();
    }}
}}
"""

def forge_mod_fg4(mc):
    """1.14.4-1.16.5: Same as fg3 but Minecraft.getInstance().getWindow()"""
    return f"""package xyz.langyo.minecraftmcp;

import com.mcbbs.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;

@Mod("minecraftmcp")
public class MinecraftMcpMod {{
    public static MinecraftMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ForgeInputHandler handler;

    public MinecraftMcpMod() {{
        INSTANCE = this;
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);
    }}

    private void setup(final FMLCommonSetupEvent event) {{
        String serverUrl = System.getenv("MC_MCP_SERVER");
        if (serverUrl == null || serverUrl.isEmpty()) serverUrl = "ws://127.0.0.1:9876";
        handler = new ForgeInputHandler();
        wsClient = new McpWebSocketClient(serverUrl, handler);
        wsClient.connectAsync();
    }}

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {{
        if (event.phase == TickEvent.Phase.END && wsClient != null) wsClient.handleMessages();
    }}
}}
"""

def forge_mod_fg5(mc):
    """1.17.1-1.19.2: Same structure"""
    return forge_mod_fg4(mc)

def forge_mod_fg6(mc):
    """1.19.3-1.21.3: FG6, official mappings, same event system"""
    return f"""package xyz.langyo.minecraftmcp;

import com.mcbbs.mcp.common.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;

@Mod("minecraftmcp")
public class MinecraftMcpMod {{
    public static MinecraftMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ForgeInputHandler handler;

    public MinecraftMcpMod() {{
        INSTANCE = this;
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);
    }}

    private void setup(final FMLCommonSetupEvent event) {{
        String serverUrl = System.getenv("MC_MCP_SERVER");
        if (serverUrl == null || serverUrl.isEmpty()) serverUrl = "ws://127.0.0.1:9876";
        handler = new ForgeInputHandler();
        wsClient = new McpWebSocketClient(serverUrl, handler);
        wsClient.connectAsync();
    }}

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {{
        if (event.phase == TickEvent.Phase.END && wsClient != null) wsClient.handleMessages();
    }}
}}
"""

def forge_mod_fg7(mc):
    """1.21.4+: EventBus 7, FMLJavaModLoadingContext param"""
    return f"""package xyz.langyo.minecraftmcp;

import com.mcbbs.mcp.common.*;
import net.minecraftforge.event.TickEvent.ClientTickEvent.Post;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("minecraftmcp")
public class MinecraftMcpMod {{
    public static MinecraftMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private ForgeInputHandler handler;

    public MinecraftMcpMod(FMLJavaModLoadingContext context) {{
        INSTANCE = this;
        var modBusGroup = context.getModBusGroup();
        FMLCommonSetupEvent.getBus(modBusGroup).addListener(this::commonSetup);
        Post.BUS.addListener(this::onClientTickPost);
    }}

    private void commonSetup(final FMLCommonSetupEvent event) {{
        String serverUrl = System.getenv("MC_MCP_SERVER");
        if (serverUrl == null || serverUrl.isEmpty()) serverUrl = "ws://127.0.0.1:9876";
        handler = new ForgeInputHandler();
        wsClient = new McpWebSocketClient(serverUrl, handler);
        wsClient.connectAsync();
    }}

    private void onClientTickPost(Post event) {{
        if (wsClient != null) wsClient.handleMessages();
    }}
}}
"""

def forge_mod_mc26(mc):
    """26.x: Same as fg7 but API differences"""
    return forge_mod_fg7(mc)


# ============================================================
# NEOFORGE MOD TEMPLATES
# ============================================================

def neoforge_mod(mc):
    return f"""package xyz.langyo.minecraftmcp;

import com.mcbbs.mcp.common.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod("minecraftmcp")
public class MinecraftMcpMod {{
    public static MinecraftMcpMod INSTANCE;
    private McpWebSocketClient wsClient;
    private NeoForgeInputHandler handler;

    public MinecraftMcpMod(IEventBus modBus) {{
        INSTANCE = this;
        modBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(this::onClientTickPost);
    }}

    private void commonSetup(final FMLCommonSetupEvent event) {{
        String serverUrl = System.getenv("MC_MCP_SERVER");
        if (serverUrl == null || serverUrl.isEmpty()) serverUrl = "ws://127.0.0.1:9876";
        handler = new NeoForgeInputHandler();
        wsClient = new McpWebSocketClient(serverUrl, handler);
        wsClient.connectAsync();
    }}

    private void onClientTickPost(ClientTickEvent.Post event) {{
        if (wsClient != null) wsClient.handleMessages();
    }}
}}
"""


# ============================================================
# FABRIC MOD TEMPLATE
# ============================================================

def fabric_mod(mc):
    return f"""package xyz.langyo.minecraftmcp;

import com.mcbbs.mcp.common.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class MinecraftMcpMod implements ClientModInitializer {{
    private McpWebSocketClient wsClient;

    @Override
    public void onInitializeClient() {{
        String serverUrl = System.getenv("MC_MCP_SERVER");
        if (serverUrl == null || serverUrl.isEmpty()) serverUrl = "ws://127.0.0.1:9876";
        FabricInputHandler handler = new FabricInputHandler();
        wsClient = new McpWebSocketClient(serverUrl, handler);
        wsClient.connectAsync();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {{
            if (wsClient != null) wsClient.handleMessages();
        }});
    }}
}}
"""


# ============================================================
# INPUT HANDLER TEMPLATES
# ============================================================

INPUT_HANDLER_COMMON = """
    private static void sendKey(long h, int key, int action) {
        GLFW.glfwSetKeyCallback(h, (w, k, sc, a, m) -> {}).invoke(h, key, 0, action, 0);
    }

    private static void sendMouseButton(long h, int button, int action) {
        GLFW.glfwSetMouseButtonCallback(h, (w, b, a, m) -> {}).invoke(h, button, action, 0);
    }
"""

KEYCODE_SWITCH = """
    private static int keyCode(String name) {
        String n = name.toLowerCase();
        switch (n) {
            case "enter": case "return": return GLFW.GLFW_KEY_ENTER;
            case "escape": case "esc": return GLFW.GLFW_KEY_ESCAPE;
            case "tab": return GLFW.GLFW_KEY_TAB;
            case "space": return GLFW.GLFW_KEY_SPACE;
            case "backspace": return GLFW.GLFW_KEY_BACKSPACE;
            case "delete": return GLFW.GLFW_KEY_DELETE;
            case "up": return GLFW.GLFW_KEY_UP; case "down": return GLFW.GLFW_KEY_DOWN;
            case "left": return GLFW.GLFW_KEY_LEFT; case "right": return GLFW.GLFW_KEY_RIGHT;
            case "f1": return GLFW.GLFW_KEY_F1; case "f2": return GLFW.GLFW_KEY_F2; case "f3": return GLFW.GLFW_KEY_F3; case "f4": return GLFW.GLFW_KEY_F4;
            case "f5": return GLFW.GLFW_KEY_F5; case "f6": return GLFW.GLFW_KEY_F6; case "f7": return GLFW.GLFW_KEY_F7; case "f8": return GLFW.GLFW_KEY_F8;
            case "f9": return GLFW.GLFW_KEY_F9; case "f10": return GLFW.GLFW_KEY_F10; case "f11": return GLFW.GLFW_KEY_F11; case "f12": return GLFW.GLFW_KEY_F12;
            case "a": return GLFW.GLFW_KEY_A; case "b": return GLFW.GLFW_KEY_B; case "c": return GLFW.GLFW_KEY_C; case "d": return GLFW.GLFW_KEY_D;
            case "e": return GLFW.GLFW_KEY_E; case "f": return GLFW.GLFW_KEY_F; case "g": return GLFW.GLFW_KEY_G; case "h": return GLFW.GLFW_KEY_H;
            case "i": return GLFW.GLFW_KEY_I; case "j": return GLFW.GLFW_KEY_J; case "k": return GLFW.GLFW_KEY_K; case "l": return GLFW.GLFW_KEY_L;
            case "m": return GLFW.GLFW_KEY_M; case "n": return GLFW.GLFW_KEY_N; case "o": return GLFW.GLFW_KEY_O; case "p": return GLFW.GLFW_KEY_P;
            case "q": return GLFW.GLFW_KEY_Q; case "r": return GLFW.GLFW_KEY_R; case "s": return GLFW.GLFW_KEY_S; case "t": return GLFW.GLFW_KEY_T;
            case "u": return GLFW.GLFW_KEY_U; case "v": return GLFW.GLFW_KEY_V; case "w": return GLFW.GLFW_KEY_W; case "x": return GLFW.GLFW_KEY_X;
            case "y": return GLFW.GLFW_KEY_Y; case "z": return GLFW.GLFW_KEY_Z;
            case "0": return GLFW.GLFW_KEY_0; case "1": return GLFW.GLFW_KEY_1; case "2": return GLFW.GLFW_KEY_2; case "3": return GLFW.GLFW_KEY_3;
            case "4": return GLFW.GLFW_KEY_4; case "5": return GLFW.GLFW_KEY_5; case "6": return GLFW.GLFW_KEY_6; case "7": return GLFW.GLFW_KEY_7;
            case "8": return GLFW.GLFW_KEY_8; case "9": return GLFW.GLFW_KEY_9;
            case "shift": return GLFW.GLFW_KEY_LEFT_SHIFT;
            case "ctrl": case "control": return GLFW.GLFW_KEY_LEFT_CONTROL;
            case "alt": return GLFW.GLFW_KEY_LEFT_ALT;
            default: return -1;
        }
    }

    private static int charCode(char ch) {
        if (ch >= 'a' && ch <= 'z') return GLFW.GLFW_KEY_A + (ch - 'a');
        if (ch >= 'A' && ch <= 'Z') return GLFW.GLFW_KEY_A + (ch - 'A');
        if (ch >= '0' && ch <= '9') return GLFW.GLFW_KEY_0 + (ch - '0');
        switch (ch) {
            case ' ': return GLFW.GLFW_KEY_SPACE;
            case '.': return GLFW.GLFW_KEY_PERIOD;
            case ',': return GLFW.GLFW_KEY_COMMA;
            case '!': return GLFW.GLFW_KEY_1;
            case '@': return GLFW.GLFW_KEY_2;
            case '#': return GLFW.GLFW_KEY_3;
            case '$': return GLFW.GLFW_KEY_4;
            case '%': return GLFW.GLFW_KEY_5;
            case '^': return GLFW.GLFW_KEY_6;
            case '&': return GLFW.GLFW_KEY_7;
            case '*': return GLFW.GLFW_KEY_8;
            case '(': return GLFW.GLFW_KEY_9;
            case ')': return GLFW.GLFW_KEY_0;
            case '-': return GLFW.GLFW_KEY_MINUS;
            case '=': return GLFW.GLFW_KEY_EQUAL;
            case '[': return GLFW.GLFW_KEY_LEFT_BRACKET;
            case ']': return GLFW.GLFW_KEY_RIGHT_BRACKET;
            case ';': return GLFW.GLFW_KEY_SEMICOLON;
            case '\\'': return GLFW.GLFW_KEY_BACKSLASH;
            case '`': return GLFW.GLFW_KEY_GRAVE_ACCENT;
            default: return -1;
        }
    }
"""

# Window handle accessor per API group
# fg4/f5/f6: mc().getWindow().getHandle()
# fg7/mc26: mc().getWindow().handle()
# mc26: dimension().identifier(), difficulty().getSerializedName()

def handler_fg6(classname, mc):
    """1.19.3-1.21.3: getWindow().getHandle(), dimension().location(), difficulty().getKey()"""
    return f"""package xyz.langyo.minecraftmcp;

import com.mcbbs.mcp.common.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class {classname} extends McpMessageHandler implements MinecraftInput {{
    private static Minecraft mc() {{ return Minecraft.getInstance(); }}

    public {classname}() {{}}

    private static void sendKey(long h, int key, int action) {{
        GLFW.glfwSetKeyCallback(h, (w, k, sc, a, m) -> {{}}).invoke(h, key, 0, action, 0);
    }}

    private static void sendMouseButton(long h, int button, int action) {{
        GLFW.glfwSetMouseButtonCallback(h, (w, b, a, m) -> {{}}).invoke(h, button, action, 0);
    }}

    @Override
    public void click(int x, int y, String button) {{
        mc().execute(() -> {{
            try {{
                long h = mc().getWindow().getHandle();
                int b = "right".equals(button) ? GLFW.GLFW_MOUSE_BUTTON_RIGHT
                    : "middle".equals(button) ? GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                    : GLFW.GLFW_MOUSE_BUTTON_1;
                GLFW.glfwSetCursorPos(h, x, y);
                Thread.sleep(10);
                sendMouseButton(h, b, GLFW.GLFW_PRESS);
                Thread.sleep(30);
                sendMouseButton(h, b, GLFW.GLFW_RELEASE);
            }} catch (Exception e) {{ System.err.println("[Input] Click: " + e.getMessage()); }}
        }});
    }}

    @Override
    public void pressKey(String key, float hold) {{
        mc().execute(() -> {{
            try {{
                long h = mc().getWindow().getHandle();
                int c = keyCode(key); if (c < 0) return;
                sendKey(h, c, GLFW.GLFW_PRESS);
                Thread.sleep(hold > 0 ? (long)(hold * 1000) : 30);
                sendKey(h, c, GLFW.GLFW_RELEASE);
            }} catch (Exception e) {{ System.err.println("[Input] Key: " + e.getMessage()); }}
        }});
    }}

    @Override
    public void typeText(String text) {{
        mc().execute(() -> {{
            try {{
                long h = mc().getWindow().getHandle();
                for (char ch : text.toCharArray()) {{
                    int code = charCode(ch);
                    if (code > 0) {{
                        boolean shift = Character.isUpperCase(ch) || "\\"!@#$%^&*()_+{{}}|:<>?\\".indexOf(ch) >= 0;
                        if (shift) {{ sendKey(h, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_PRESS); Thread.sleep(5); }}
                        sendKey(h, code, GLFW.GLFW_PRESS); Thread.sleep(25);
                        sendKey(h, code, GLFW.GLFW_RELEASE);
                        if (shift) {{ Thread.sleep(5); sendKey(h, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_RELEASE); }}
                    }} else {{ typeUnicode(h, ch); }}
                    Thread.sleep(20);
                }}
            }} catch (Exception e) {{ System.err.println("[Input] Type: " + e.getMessage()); }}
        }});
    }}

    @Override
    public void scroll(int clicks) {{
        mc().execute(() -> {{
            try {{
                long h = mc().getWindow().getHandle();
                GLFW.glfwSetScrollCallback(h, (_h, ox, oy) -> {{}}).invoke(h, 0.0, clicks * 1.0);
            }} catch (Exception ignored) {{}}
        }});
    }}

    @Override
    public void hotkey(String[] keys) {{
        mc().execute(() -> {{
            try {{
                long h = mc().getWindow().getHandle();
                int[] codes = new int[keys.length];
                for (int i = 0; i < keys.length; i++) codes[i] = keyCode(keys[i]);
                for (int c : codes) {{ sendKey(h, c, GLFW.GLFW_PRESS); Thread.sleep(5); }}
                Thread.sleep(80);
                for (int i = codes.length - 1; i >= 0; i--) sendKey(h, codes[i], GLFW.GLFW_RELEASE);
            }} catch (Exception e) {{ System.err.println("[Input] Hotkey: " + e.getMessage()); }}
        }});
    }}

    @Override
    public byte[] screenshot() {{
        try {{
            var fb = mc().getMainRenderTarget();
            int w = fb.width, h = fb.height;
            var pixels = new java.util.concurrent.CompletableFuture<int[]>();
            mc().execute(() -> {{
                try {{
                    int[] buf = new int[w * h];
                    ByteBuffer bb = java.nio.ByteBuffer.allocateDirect(w * h * 4);
                    org.lwjgl.opengl.GL11.glReadPixels(0, 0, w, h, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, bb);
                    bb.rewind();
                    for (int i = 0; i < buf.length; i++) {{
                        int r2 = bb.get() & 0xFF;
                        int g2 = bb.get() & 0xFF;
                        int b = bb.get() & 0xFF;
                        int a = bb.get() & 0xFF;
                        buf[i] = (a << 24) | (r2 << 16) | (g2 << 8) | b;
                    }}
                    pixels.complete(buf);
                }} catch (Exception e) {{ pixels.completeExceptionally(e); }}
            }});
            int[] raw = pixels.get(5, java.util.concurrent.TimeUnit.SECONDS);
            int[] flipped = new int[w * h];
            for (int y2 = 0; y2 < h; y2++) {{
                for (int x2 = 0; x2 < w; x2++) {{
                    flipped[y2 * w + x2] = raw[(h - 1 - y2) * w + x2];
                }}
            }}
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, w, h, flipped, 0, w);
            var baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        }} catch (Exception e) {{ e.printStackTrace(); return null; }}
    }}

    @Override
    public String executeCommand(String cmd) {{
        mc().execute(() -> {{
            if (mc().player != null) mc().player.connection.sendCommand(cmd);
        }});
        return "sent: " + cmd;
    }}

    @Override
    public String getPlayerInfo() {{
        var p = mc().player;
        if (p == null) return "{{\\"name\\":null}}";
        return String.format("{{\\"name\\":\\"%s\\",\\"health\\":%.1f,\\"pos\\":\\"%.1f %.1f %.1f\\",\\"dimension\\":\\"%s\\"}}",
            p.getName().getString(), p.getHealth(), p.getX(), p.getY(), p.getZ(),
            p.level().dimension().location().toString());
    }}

    @Override
    public String getWorldInfo() {{
        var l = mc().level;
        if (l == null) return "{{\\"world_name\\":null}}";
        return String.format("{{\\"world_name\\":\\"%s\\",\\"difficulty\\":\\"%s\\",\\"gametype\\":\\"%s\\"}}",
            l.getServer() != null ? l.getServer().getWorldData().getLevelName() : "unknown",
            l.getDifficulty().getKey(),
            mc().gameMode.getPlayerMode().getName());
    }}

    private void typeUnicode(long handle, char ch) {{
        try {{
            int cp = (int) ch;
            sendKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_PRESS);
            sendKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_PRESS);
            sendKey(handle, GLFW.GLFW_KEY_U, GLFW.GLFW_PRESS);
            sendKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_RELEASE);
            sendKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_RELEASE);
            String hex = Integer.toHexString(cp).toLowerCase();
            for (char hc : hex.toCharArray()) {{
                int kc = charCode(hc);
                if (kc > 0) {{ sendKey(handle, kc, GLFW.GLFW_PRESS); Thread.sleep(20); sendKey(handle, kc, GLFW.GLFW_RELEASE); Thread.sleep(15); }}
            }}
            sendKey(handle, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_PRESS); Thread.sleep(30);
            sendKey(handle, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_RELEASE);
        }} catch (Exception e) {{ System.err.println("[Input] Unicode: " + e.getMessage()); }}
    }}

{KEYCODE_SWITCH}
}}
"""

def handler_mc26(classname):
    """26.x: .handle(), .identifier(), .getSerializedName()"""
    return handler_fg6(classname, "26.1.2").replace(".getHandle()", ".handle()").replace(".location()", ".identifier()").replace(".getKey()", ".getSerializedName()")

def handler_fg7(classname):
    """1.21.4+: .handle(), but old API names"""
    return handler_fg6(classname, "1.21.4").replace(".getHandle()", ".handle()")

def handler_legacy(classname):
    """1.7.2-1.12.2: LWJGL 2, completely different"""
    return f"""package xyz.langyo.minecraftmcp;

import com.mcbbs.mcp.common.*;
import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

public class {classname} extends McpMessageHandler implements MinecraftInput {{
    private static Minecraft mc() {{ return Minecraft.getMinecraft(); }}

    public {classname}() {{}}

    @Override
    public void click(int x, int y, String button) {{
        mc().execute(() -> {{
            try {{
                int b = "right".equals(button) ? 1 : "middle".equals(button) ? 2 : 0;
                org.lwjgl.input.Mouse.setCursorPosition(x, y);
                Thread.sleep(10);
                org.lwjgl.input.Mouse.setGrabbed(false);
                if (b == 0) org.lwjgl.input.Mouse.press();
                org.lwjgl.input.Mouse.next();
                Thread.sleep(30);
            }} catch (Exception e) {{ System.err.println("[Input] Click: " + e.getMessage()); }}
        }});
    }}

    @Override
    public void pressKey(String key, float hold) {{
        mc().execute(() -> {{
            try {{
                int c = keyCode(key); if (c < 0) return;
                org.lwjgl.input.Keyboard.pressKey(c, false);
                Thread.sleep(hold > 0 ? (long)(hold * 1000) : 30);
                org.lwjgl.input.Keyboard.releaseKey(c);
            }} catch (Exception e) {{ System.err.println("[Input] Key: " + e.getMessage()); }}
        }});
    }}

    @Override
    public void typeText(String text) {{
        mc().execute(() -> {{
            try {{
                for (char ch : text.toCharArray()) {{
                    if (ch >= 32 && ch < 127) {{
                        org.lwjgl.input.Keyboard.typeKey((int) ch);
                    }}
                    Thread.sleep(20);
                }}
            }} catch (Exception e) {{ System.err.println("[Input] Type: " + e.getMessage()); }}
        }});
    }}

    @Override
    public void scroll(int clicks) {{
        mc().execute(() -> {{
            try {{
                org.lwjgl.input.Mouse.setEventDX(0);
                org.lwjgl.input.Mouse.setEventDWheel(clicks * 120);
            }} catch (Exception ignored) {{}}
        }});
    }}

    @Override
    public void hotkey(String[] keys) {{
        mc().execute(() -> {{
            try {{
                int[] codes = new int[keys.length];
                for (int i = 0; i < keys.length; i++) codes[i] = keyCode(keys[i]);
                for (int c : codes) {{ org.lwjgl.input.Keyboard.pressKey(c, false); Thread.sleep(5); }}
                Thread.sleep(80);
                for (int i = codes.length - 1; i >= 0; i--) org.lwjgl.input.Keyboard.releaseKey(codes[i]);
            }} catch (Exception e) {{ System.err.println("[Input] Hotkey: " + e.getMessage()); }}
        }});
    }}

    @Override
    public byte[] screenshot() {{
        try {{
            int w = mc().displayWidth, h = mc().displayHeight;
            int[] raw = new int[w * h];
            ByteBuffer bb = java.nio.ByteBuffer.allocateDirect(w * h * 4);
            org.lwjgl.opengl.GL11.glReadPixels(0, 0, w, h, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, bb);
            bb.rewind();
            for (int i = 0; i < raw.length; i++) {{
                int r2 = bb.get() & 0xFF;
                int g2 = bb.get() & 0xFF;
                int b = bb.get() & 0xFF;
                int a = bb.get() & 0xFF;
                raw[i] = (a << 24) | (r2 << 16) | (g2 << 8) | b;
            }}
            int[] flipped = new int[w * h];
            for (int y2 = 0; y2 < h; y2++) {{
                for (int x2 = 0; x2 < w; x2++) {{
                    flipped[y2 * w + x2] = raw[(h - 1 - y2) * w + x2];
                }}
            }}
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, w, h, flipped, 0, w);
            var baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        }} catch (Exception e) {{ e.printStackTrace(); return null; }}
    }}

    @Override
    public String executeCommand(String cmd) {{
        mc().execute(() -> {{
            if (mc().player != null) mc().player.sendChatMessage("/" + cmd);
        }});
        return "sent: " + cmd;
    }}

    @Override
    public String getPlayerInfo() {{
        var p = mc().player;
        if (p == null) return "{{\\"name\\":null}}";
        return String.format("{{\\"name\\":\\"%s\\",\\"health\\":%.1f,\\"pos\\":\\"%.1f %.1f %.1f\\"}}",
            p.getName(), p.getHealth(), p.posX, p.posY, p.posZ);
    }}

    @Override
    public String getWorldInfo() {{
        var l = mc().world;
        if (l == null) return "{{\\"world_name\\":null}}";
        return String.format("{{\\"world_name\\":\\"%s\\",\\"difficulty\\":\\"%s\\"}}",
            l.getWorldInfo().getWorldName(), l.getDifficulty().getDifficultyKey());
    }}

    private static int keyCode(String name) {{
        String n = name.toLowerCase();
        switch (n) {{
            case "enter": case "return": return org.lwjgl.input.Keyboard.KEY_RETURN;
            case "escape": case "esc": return org.lwjgl.input.Keyboard.KEY_ESCAPE;
            case "tab": return org.lwjgl.input.Keyboard.KEY_TAB;
            case "space": return org.lwjgl.input.Keyboard.KEY_SPACE;
            case "backspace": return org.lwjgl.input.Keyboard.KEY_BACK;
            case "delete": return org.lwjgl.input.Keyboard.KEY_DELETE;
            case "up": return org.lwjgl.input.Keyboard.KEY_UP; case "down": return org.lwjgl.input.Keyboard.KEY_DOWN;
            case "left": return org.lwjgl.input.Keyboard.KEY_LEFT; case "right": return org.lwjgl.input.Keyboard.KEY_RIGHT;
            default: return -1;
        }}
    }}
}}
"""

def handler_fg4(classname):
    """1.14.4-1.16.5: getWindow().getHandle()"""
    return handler_fg6(classname, "1.14.4")


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

def get_forge_handler_template(mc):
    g = api_group(mc)
    return {
        "legacy": lambda: handler_legacy("ForgeInputHandler"),
        "fg3": lambda: handler_fg4("ForgeInputHandler"),
        "fg4": lambda: handler_fg4("ForgeInputHandler"),
        "fg5": lambda: handler_fg4("ForgeInputHandler"),
        "fg6": lambda: handler_fg6("ForgeInputHandler", mc),
        "fg7": lambda: handler_fg7("ForgeInputHandler"),
        "mc26": lambda: handler_mc26("ForgeInputHandler"),
    }.get(g, lambda: handler_fg6("ForgeInputHandler", mc))()

def get_neoforge_handler_template(mc):
    g = api_group(mc)
    return {
        "fg6": lambda: handler_fg6("NeoForgeInputHandler", mc),
        "mc26": lambda: handler_mc26("NeoForgeInputHandler"),
    }.get(g, lambda: handler_fg6("NeoForgeInputHandler", mc))()

def get_fabric_handler_template(mc):
    g = api_group(mc)
    return {
        "legacy": lambda: handler_legacy("FabricInputHandler"),
        "fg3": lambda: handler_fg4("FabricInputHandler"),
        "fg4": lambda: handler_fg4("FabricInputHandler"),
        "fg5": lambda: handler_fg4("FabricInputHandler"),
        "fg6": lambda: handler_fg6("FabricInputHandler", mc),
        "fg7": lambda: handler_fg7("FabricInputHandler"),
        "mc26": lambda: handler_mc26("FabricInputHandler"),
    }.get(g, lambda: handler_fg6("FabricInputHandler", mc))()

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
                write_java(path, "ForgeInputHandler.java", get_forge_handler_template(mc))
                # Write mods.toml for FG6+
                g = api_group(mc)
                if g in ("fg6","fg7","mc26"):
                    res_dir = os.path.join(path, "src", "main", "resources", "META-INF")
                    os.makedirs(res_dir, exist_ok=True)
                    with open(os.path.join(res_dir, "mods.toml"), "w") as f:
                        f.write("""modLoader="javafml"
loaderVersion="[4,)"
license="MIT"

[[mods]]
modId="minecraftmcp"
version="${version}"
displayName="Minecraft MCP Bridge"
description="WebSocket bridge for AI agent interaction"
authors="langyo"
""")
                total += 1
            elif loader == "neoforge":
                write_java(path, "MinecraftMcpMod.java", neoforge_mod(mc))
                write_java(path, "NeoForgeInputHandler.java", get_neoforge_handler_template(mc))
                res_dir = os.path.join(path, "src", "main", "resources", "META-INF")
                os.makedirs(res_dir, exist_ok=True)
                with open(os.path.join(res_dir, "neoforge.mods.toml"), "w") as f:
                    f.write("""modLoader = "javafml"
loaderVersion = "[4,)"
license = "MIT"

[[mods]]
modId = "minecraftmcp"
version = "${version}"
displayName = "Minecraft MCP Bridge"
description = "WebSocket bridge for AI agent interaction"
authors = "langyo"
""")
                total += 1
            elif loader == "fabric":
                write_java(path, "MinecraftMcpMod.java", fabric_mod(mc))
                write_java(path, "FabricInputHandler.java", get_fabric_handler_template(mc))
                total += 1

    print(f"Java source files written to {total} projects")

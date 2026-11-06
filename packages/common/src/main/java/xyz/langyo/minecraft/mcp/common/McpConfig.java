package xyz.langyo.minecraft.mcp.common;

public final class McpConfig {
    private McpConfig() {}

    public static final int PORT_START = 9876;
    public static final int PORT_END = 9000;

    public static int getServerPort() {
        return getConfiguredPort();
    }

    public static int getConfiguredPort() {
        String p = System.getProperty("mcp.port");
        if (p != null && !p.isEmpty()) {
            try { return Integer.parseInt(p); } catch (NumberFormatException ignored) {}
        }
        p = System.getenv("MC_MCP_PORT");
        if (p != null && !p.isEmpty()) {
            try { return Integer.parseInt(p); } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    public static String getModVersion() {
        String v = System.getProperty("mcp.mod.version");
        if (v != null && !v.isEmpty()) return v;
        // Launchers that don't pass -Dmcp.mod.version (e.g. a user dropping
        // the jar into a vanilla-launched mods folder): fall back to the
        // Implementation-Version stamped into the mod jar's manifest.
        try {
            String mv = McpConfig.class.getPackage().getImplementationVersion();
            if (mv != null && !mv.isEmpty()) return mv;
        } catch (Exception ignored) {}
        return "unknown";
    }

    public static String getModLoader() {
        String l = System.getProperty("mcp.mod.loader");
        if (l != null && !l.isEmpty()) return l;
        // Fallback for launchers that don't pass -Dmcp.mod.loader: detect
        // the loader from its classes (issue #45). Loaded without running
        // static initializers so a broken class can't take the status down.
        ClassLoader cl = McpConfig.class.getClassLoader();
        try { Class.forName("net.neoforged.fml.common.Mod", false, cl); return "neoforge"; } catch (Throwable ignored) {}
        try { Class.forName("net.minecraftforge.fml.common.Mod", false, cl); return "forge"; } catch (Throwable ignored) {}
        try { Class.forName("cpw.mods.fml.common.Mod", false, cl); return "forge"; } catch (Throwable ignored) {}
        try { Class.forName("net.fabricmc.api.ModInitializer", false, cl); return "fabric"; } catch (Throwable ignored) {}
        return "unknown";
    }

    public static String getForgeVersion() {
        String f = System.getProperty("mcp.mod.forge.version");
        if (f != null && !f.isEmpty()) return f;
        return null;
    }

    /**
     * Shared bearer token for the HTTP API (issue #81). When configured via
     * {@code -Dmcp.token} or {@code MC_MCP_TOKEN}, every mutating endpoint
     * ({@code /api/cmd}, {@code /api/screenshot}, {@code /api/events},
     * {@code /api/calls}) requires {@code Authorization: Bearer <token>}.
     * {@code /api/status} stays open so port discovery keeps working.
     * When no token is configured the API stays open (legacy behavior for
     * manually launched games) but the server still binds loopback-only.
     */
    public static String getAuthToken() {
        String t = System.getProperty("mcp.token");
        if (t != null && !t.isEmpty()) return t;
        t = System.getenv("MC_MCP_TOKEN");
        if (t != null && !t.isEmpty()) return t;
        return null;
    }

    /**
     * Bind address for the HTTP server (issue #81). Defaults to loopback so
     * the game control surface is not reachable from the network; users who
     * deliberately want LAN exposure can opt in with {@code -Dmcp.bind=0.0.0.0}
     * or {@code MC_MCP_BIND}. The server ignores a non-loopback address unless
     * a token is configured too.
     */
    public static String getBindAddress() {
        String a = System.getProperty("mcp.bind");
        if (a != null && !a.isEmpty()) return a;
        a = System.getenv("MC_MCP_BIND");
        if (a != null && !a.isEmpty()) return a;
        return "127.0.0.1";
    }

    private static Boolean remoteControlCache;

    /**
     * External confirmation gate for MCP control mode (issue #81). Control
     * mode used to be a soft gate: any local caller could invoke
     * {@code enter_control_mode} directly and self-authorize full input
     * control. Now remote entry additionally requires the operator's
     * launch-time consent ({@code -Dmcp.remote_control=true} or
     * {@code MC_MCP_REMOTE_CONTROL}): launching through the bundled
     * launcher sets it automatically, while a mod jar dropped into a
     * manually launched game defaults to the in-game consent path
     * (ESC &gt; MCP Take Over).
     */
    public static boolean isRemoteControlAllowed() {
        Boolean cached = remoteControlCache;
        if (cached != null) return cached;
        String v = System.getProperty("mcp.remote_control");
        if (v == null || v.isEmpty()) v = System.getenv("MC_MCP_REMOTE_CONTROL");
        boolean enabled = v != null && (v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes"));
        remoteControlCache = enabled;
        return enabled;
    }

    private static Boolean backgroundInputCache;

    /**
     * Opt-in "background / no OS input" mode for clients driven by an agent
     * while the user keeps working in other windows (issue #54). GLFW on
     * Windows reports a background-process window as focused, so
     * {@code glfwSetCursorPos} and {@code GLFW_CURSOR_DISABLED} would move and
     * confine the user's real desktop cursor. In this mode the mod only edits
     * Minecraft-internal mouse state and never touches the OS cursor.
     *
     * Enabled with {@code -Dmcp.background_input=true} or the
     * {@code MC_MCP_BACKGROUND_INPUT} environment variable.
     */
    public static boolean isBackgroundInputMode() {
        Boolean cached = backgroundInputCache;
        if (cached != null) return cached;
        String v = System.getProperty("mcp.background_input");
        if (v == null || v.isEmpty()) v = System.getenv("MC_MCP_BACKGROUND_INPUT");
        boolean enabled = v != null && (v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes"));
        backgroundInputCache = enabled;
        return enabled;
    }

    private static Boolean pauseMenuSuppressionCache;

    /**
     * While MCP control mode is active, legacy-era modules cancel the pause
     * menu (GuiIngameMenu) so opening ESC cannot pause the game out from
     * under the agent; the menu is restored when control mode exits. Users
     * who prefer the vanilla pause behavior can disable the cancellation
     * with {@code -Dmcp.suppress_pause_menu=false} or the
     * {@code MC_MCP_SUPPRESS_PAUSE_MENU} environment variable
     * (issue #76 B8). Default: enabled.
     */
    public static boolean isPauseMenuSuppressionEnabled() {
        Boolean cached = pauseMenuSuppressionCache;
        if (cached != null) return cached;
        String v = System.getProperty("mcp.suppress_pause_menu");
        if (v == null || v.isEmpty()) v = System.getenv("MC_MCP_SUPPRESS_PAUSE_MENU");
        boolean enabled = v == null || v.isEmpty()
                || v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes");
        pauseMenuSuppressionCache = enabled;
        return enabled;
    }
}

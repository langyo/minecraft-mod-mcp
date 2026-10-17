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
        return "unknown";
    }

    public static String getModLoader() {
        String l = System.getProperty("mcp.mod.loader");
        if (l != null && !l.isEmpty()) return l;
        return "unknown";
    }

    public static String getForgeVersion() {
        String f = System.getProperty("mcp.mod.forge.version");
        if (f != null && !f.isEmpty()) return f;
        return null;
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
}

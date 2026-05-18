package xyz.langyo.minecraft.mcp.common;

public final class McpConfig {
    private McpConfig() {}

    public static String getServerUrl() {
        String url = System.getProperty("mcp.server");
        if (url != null && !url.isEmpty()) return url;
        url = System.getenv("MC_MCP_SERVER");
        if (url != null && !url.isEmpty()) return url;
        return "ws://127.0.0.1:9876";
    }
}

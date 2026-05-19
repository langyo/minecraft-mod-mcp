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

    public static int getServerPort() {
        String p = System.getProperty("mcp.port");
        if (p != null && !p.isEmpty()) {
            try { return Integer.parseInt(p); } catch (NumberFormatException ignored) {}
        }
        p = System.getenv("MC_MCP_PORT");
        if (p != null && !p.isEmpty()) {
            try { return Integer.parseInt(p); } catch (NumberFormatException ignored) {}
        }
        String url = getServerUrl();
        int idx = url.lastIndexOf(':');
        if (idx > 0) {
            try { return Integer.parseInt(url.substring(idx + 1).replaceAll("[^0-9]", "")); } catch (NumberFormatException ignored) {}
        }
        return 9876;
    }
}

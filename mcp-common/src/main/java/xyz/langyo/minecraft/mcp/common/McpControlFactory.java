package xyz.langyo.minecraft.mcp.common;

public final class McpControlFactory {

    private McpControlFactory() {}

    private static McpPlatformControl instance;

    public static synchronized McpPlatformControl get() {
        if (instance != null) return instance;
        instance = detect();
        ReflectionHelper.dbg("McpControlFactory: detected platform=" + instance.getPlatformName());
        return instance;
    }

    private static McpPlatformControl detect() {
        String os = System.getProperty("os.name", "").toLowerCase();
        ReflectionHelper.dbg("McpControlFactory: os.name=" + os);

        if (os.contains("win")) {
            return tryCreate("xyz.langyo.minecraft.mcp.common.McpWin32Control");
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return tryCreate("xyz.langyo.minecraft.mcp.common.McpMacControl");
        }
        if (os.contains("linux") || os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            String termux = System.getenv("TERMUX_VERSION");
            if (termux != null && !termux.isEmpty()) {
                return new McpNoopControl();
            }
            return tryCreate("xyz.langyo.minecraft.mcp.common.McpX11Control");
        }
        return new McpNoopControl();
    }

    private static McpPlatformControl tryCreate(String className) {
        try {
            Class<?> cl = Class.forName(className);
            return (McpPlatformControl) cl.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            ReflectionHelper.dbg("McpControlFactory: " + className + " not found, falling back to Noop");
        } catch (Exception e) {
            ReflectionHelper.dbg("McpControlFactory: " + className + " failed: " + e.getMessage());
        }
        return new McpNoopControl();
    }
}

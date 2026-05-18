package xyz.langyo.minecraft.mcp.common;

public interface RenderThreadExecutor {
    void executeOnRenderThread(Runnable task);
}

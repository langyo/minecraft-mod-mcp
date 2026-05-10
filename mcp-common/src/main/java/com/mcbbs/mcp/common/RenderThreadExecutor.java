package com.mcbbs.mcp.common;

public interface RenderThreadExecutor {
    void executeOnRenderThread(Runnable task);
}

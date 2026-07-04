package xyz.langyo.minecraft.mcp.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import xyz.langyo.minecraft.mcp.mod.ModDevMcpMod;

@Mixin(Mouse.class)
public abstract class MouseClickMixin {

    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void onMouseButton(long window, MouseInput input, int action, CallbackInfo ci) {
        try {
            if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_RELEASE) return;
            if (input.button() != 0) return;
            ModDevMcpMod mod = ModDevMcpMod.INSTANCE;
            if (mod == null) return;
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            boolean inControlMode = xyz.langyo.minecraft.mcp.common.ReflectionHelper.isMcpControlMode();
            if (mc.currentScreen == null && !inControlMode) return;
            double mx = mc.mouse.getX() * mc.getWindow().getScaledWidth() / (double) mc.getWindow().getWidth();
            double my = mc.mouse.getY() * mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight();
            if (action == GLFW.GLFW_PRESS) {
                if (mod.onMouseClicked(mx, my, input.button())) {
                    mc.setScreen(mc.currentScreen);
                }
            }
        } catch (Exception e) {
            System.err.println("[MCP-MOD] MouseClickMixin error: " + e.getMessage());
        }
    }

    private static class GLFW {
        static final int GLFW_PRESS = 1;
        static final int GLFW_RELEASE = 0;
    }
}

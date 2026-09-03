package xyz.langyo.minecraft.mcp.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import xyz.langyo.minecraft.mcp.mod.ModDevMcpMod;

@Mixin(MouseHandler.class)
public abstract class MouseClickMixin {

    private static final int GLFW_RELEASE = 0;
    private static final int GLFW_PRESS = 1;

    @Inject(method = "onButton", at = @At("HEAD"))
    private void onButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        try {
            if (action != GLFW_PRESS && action != GLFW_RELEASE) return;
            if (info.button() != 0) return;
            ModDevMcpMod mod = ModDevMcpMod.INSTANCE;
            if (mod == null) return;
            Minecraft mc = Minecraft.getInstance();
            boolean inControlMode = xyz.langyo.minecraft.mcp.common.ReflectionHelper.isMcpControlMode();
            if (mc.gui.screen() == null && !inControlMode) return;
            MouseHandler self = (MouseHandler) (Object) this;
            double mx = self.getScaledXPos(mc.getWindow());
            double my = self.getScaledYPos(mc.getWindow());
            if (action == GLFW_PRESS) {
                if (mod.onMouseClicked(mx, my, info.button())) {
                    mc.gui.setScreen(mc.gui.screen());
                }
            }
        } catch (Exception e) {
            System.err.println("[MCP-MOD] MouseClickMixin error: " + e.getMessage());
        }
    }
}

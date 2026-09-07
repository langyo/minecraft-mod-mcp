package xyz.langyo.minecraft.mcp.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import xyz.langyo.minecraft.mcp.common.ReflectionHelper;
import xyz.langyo.minecraft.mcp.mod.ModDevMcpMod;

@Mixin(Mouse.class)
public abstract class MouseMixin {

    @Shadow private double x;
    @Shadow private double y;
    @Shadow private double cursorDeltaX;
    @Shadow private double cursorDeltaY;
    @Shadow private boolean cursorLocked;

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        ModDevMcpMod mod = ModDevMcpMod.INSTANCE;
        if (mod == null || action != 1) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        double mx = this.x * mc.getWindow().getScaledWidth() / (double) mc.getWindow().getWidth();
        double my = this.y * mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight();

        if (mod.onMouseButtonEvent(mc, mx, my, button)) {
            ci.cancel();
        }
    }

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void onUpdateMouse(double timeDelta, CallbackInfo ci) {
        if (ReflectionHelper.isMcpControlMode()) {
            this.cursorDeltaX = 0.0;
            this.cursorDeltaY = 0.0;
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (ReflectionHelper.isMcpControlMode()) {
            this.cursorDeltaX = 0.0;
            this.cursorDeltaY = 0.0;
            this.cursorLocked = false;
        }
    }
}

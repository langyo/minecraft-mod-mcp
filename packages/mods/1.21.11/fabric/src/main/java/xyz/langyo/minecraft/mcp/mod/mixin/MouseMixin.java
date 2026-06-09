package xyz.langyo.minecraft.mcp.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Mouse;

@Mixin(Mouse.class)
public abstract class MouseMixin {

    @Shadow private double cursorDeltaX;
    @Shadow private double cursorDeltaY;
    @Shadow private boolean cursorLocked;

    @Inject(method = "tick", at = @At("HEAD"))
    private void suppressInControlMode(CallbackInfo ci) {
        if (xyz.langyo.minecraft.mcp.common.ReflectionHelper.isMcpControlMode()) {
            this.cursorDeltaX = 0.0;
            this.cursorDeltaY = 0.0;
            this.cursorLocked = false;
        }
    }
}

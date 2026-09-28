package xyz.langyo.minecraft.mcp.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.MouseHandler;
import xyz.langyo.minecraft.mcp.common.ReflectionHelper;

// MC 26.x renamed Mouse to MouseHandler; accumulated cursor movement now
// drains through handleAccumulatedMovement().
@Mixin(MouseHandler.class)
public abstract class MouseMixin {

    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @Shadow private boolean mouseGrabbed;

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void suppressInControlMode(CallbackInfo ci) {
        if (ReflectionHelper.isMcpControlMode()) {
            this.accumulatedDX = 0.0;
            this.accumulatedDY = 0.0;
            this.mouseGrabbed = false;
        }
    }
}

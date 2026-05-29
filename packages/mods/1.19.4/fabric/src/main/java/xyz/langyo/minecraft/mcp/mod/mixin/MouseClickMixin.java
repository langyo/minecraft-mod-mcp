package xyz.langyo.minecraft.mcp.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.gui.ParentElement;
import xyz.langyo.minecraft.mcp.mod.ModDevMcpMod;

@Mixin(ParentElement.class)
public interface MouseClickMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    default void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (self instanceof net.minecraft.client.gui.screen.Screen) {
            ModDevMcpMod mod = ModDevMcpMod.INSTANCE;
            if (mod != null && mod.onMouseClicked(mouseX, mouseY, button)) {
                cir.setReturnValue(true);
            }
        }
    }
}

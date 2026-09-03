package xyz.langyo.minecraft.mcp.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import xyz.langyo.minecraft.mcp.mod.ModDevMcpMod;

// MC 26.x replaced Screen#render with extractRenderState(GuiGraphicsExtractor, ...).
@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ModDevMcpMod mod = ModDevMcpMod.INSTANCE;
        if (mod != null) {
            mod.onScreenRender(ctx, (Screen) (Object) this, mouseX, mouseY, partialTick);
        }
    }
}

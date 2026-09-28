package xyz.langyo.minecraft.mcp.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import xyz.langyo.minecraft.mcp.mod.ModDevMcpMod;

// MC 26.x replaced InGameHud#render with Hud#extractRenderState(GuiGraphicsExtractor, DeltaTracker).
@Mixin(Hud.class)
public abstract class InGameHudMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(GuiGraphicsExtractor ctx, DeltaTracker deltaTracker, CallbackInfo ci) {
        ModDevMcpMod mod = ModDevMcpMod.INSTANCE;
        if (mod != null) {
            mod.onInGameHudRender(ctx, deltaTracker.getGameTimeDeltaPartialTick(false));
        }
    }
}

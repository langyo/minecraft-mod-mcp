package xyz.langyo.minecraft.mcp.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.MinecraftClient;
import xyz.langyo.minecraft.mcp.mod.ModDevMcpMod;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        ModDevMcpMod mod = ModDevMcpMod.INSTANCE;
        if (mod != null) {
            mod.onClientTick();
        }
    }
}

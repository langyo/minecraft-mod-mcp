package xyz.langyo.minecraftmcp.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMcpMixin {

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        if (xyz.langyo.minecraftmcp.MinecraftMcpMod.LOGGER != null) {
            xyz.langyo.minecraftmcp.MinecraftMcpMod.LOGGER.debug("[MCP Mixin] Screen set to: {}",
                    screen != null ? screen.getClass().getSimpleName() : "null");
        }
    }
}

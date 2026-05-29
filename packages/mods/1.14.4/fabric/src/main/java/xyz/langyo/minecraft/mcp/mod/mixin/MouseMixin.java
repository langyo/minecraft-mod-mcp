package xyz.langyo.minecraft.mcp.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import xyz.langyo.minecraft.mcp.mod.ModDevMcpMod;

@Mixin(Mouse.class)
public abstract class MouseMixin {

    @Shadow private double x;
    @Shadow private double y;

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        ModDevMcpMod mod = ModDevMcpMod.INSTANCE;
        if (mod == null || action != 1) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.window == null) return;

        double scaledW = mc.window.getScaledWidth();
        double scaledH = mc.window.getScaledHeight();
        double actualW = mc.window.getWidth();
        double actualH = mc.window.getHeight();
        if (actualW <= 0 || actualH <= 0) return;

        double mx = this.x * scaledW / actualW;
        double my = this.y * scaledH / actualH;

        if (mod.onMouseButtonEvent(mc, mx, my, button)) {
            ci.cancel();
        }
    }
}

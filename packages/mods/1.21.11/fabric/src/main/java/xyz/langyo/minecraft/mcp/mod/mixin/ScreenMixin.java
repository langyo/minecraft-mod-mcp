package xyz.langyo.minecraft.mcp.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import xyz.langyo.minecraft.mcp.mod.ModDevMcpMod;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.File;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    private static void logToFile(String msg) {
        try {
            String home = System.getProperty("user.home");
            File f = new File(home + File.separator + "mcp_screen.log");
            BufferedWriter w = new BufferedWriter(new FileWriter(f, true));
            w.write(System.currentTimeMillis() + " " + msg + "\n");
            w.close();
        } catch (Exception ignored) {}
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext ctx, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        logToFile("[ScreenMixin] render TAIL called on " + ((Object)this).getClass().getSimpleName());
        ModDevMcpMod mod = ModDevMcpMod.INSTANCE;
        if (mod != null) {
            mod.onScreenRender(ctx, (Screen) (Object) this, mouseX, mouseY, tickDelta);
        }
    }
}

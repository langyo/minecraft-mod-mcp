package xyz.langyo.minecraft.mcp.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import xyz.langyo.minecraft.mcp.common.ReflectionHelper;
import xyz.langyo.minecraft.mcp.mod.ModDevMcpMod;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.File;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    private static void logToFile(String msg) {
        try {
            String home = System.getProperty("user.home");
            File f = new File(home + File.separator + "mcp_screen.log");
            BufferedWriter w = new BufferedWriter(new FileWriter(f, true));
            w.write(System.currentTimeMillis() + " " + msg + "\n");
            w.close();
        } catch (Exception ignored) {}
    }

    private static long lastTickLog = 0;

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        ModDevMcpMod mod = ModDevMcpMod.INSTANCE;
        if (mod != null) {
            mod.onClientTick();
        }
        long now = System.currentTimeMillis();
        if (now - lastTickLog > 10000) {
            lastTickLog = now;
            logToFile("[MinecraftClientMixin] tick() TAIL called - mixin is active, INSTANCE=" + (mod != null));
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onOpenScreen(Screen screen, CallbackInfo ci) {
        if (ReflectionHelper.isMcpControlMode() && screen instanceof GameMenuScreen) {
            ci.cancel();
            logToFile("[MinecraftClientMixin] blocked GameMenuScreen in control mode");
        }
    }
}

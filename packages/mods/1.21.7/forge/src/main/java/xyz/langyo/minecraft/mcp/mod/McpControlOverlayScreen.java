package xyz.langyo.minecraft.mcp.mod;

import xyz.langyo.minecraft.mcp.common.ReflectionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import java.net.URI;

public class McpControlOverlayScreen extends Screen {

    private static McpControlOverlayScreen currentInstance = null;
    private final String debugUrl;
    private int urlX, urlY, urlWidth, urlHeight;

    public McpControlOverlayScreen(String debugUrl) {
        super(Component.translatable("mcpmod.control.overlay"));
        this.debugUrl = debugUrl;
    }

    public static McpControlOverlayScreen getCurrentInstance() {
        return currentInstance;
    }

    public static void clearInstance() {
        currentInstance = null;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        currentInstance = this;

        int btnW = 150;
        int btnH = 20;
        int gap = 10;
        int totalW = btnW * 2 + gap;
        int startX = (this.width - totalW) / 2;
        int btnY = this.height - 40;

        this.addRenderableWidget(Button.builder(
            Component.translatable("mcpmod.control.resume"),
            btn -> {
                ReflectionHelper.exitMcpControlMode(Minecraft.getInstance());
                currentInstance = null;
                Minecraft.getInstance().setScreen(null);
            }
        ).bounds(startX, btnY, btnW, btnH).build());

        this.addRenderableWidget(Button.builder(
            Component.translatable("mcpmod.control.menu"),
            btn -> {
                ReflectionHelper.exitMcpControlMode(Minecraft.getInstance());
                currentInstance = null;
                Minecraft.getInstance().setScreen(null);
                ReflectionHelper.openPauseMenu(Minecraft.getInstance());
            }
        ).bounds(startX + btnW + gap, btnY, btnW, btnH).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!ReflectionHelper.isScreenshotInProgress()) {
            g.fill(0, 0, this.width, this.height, 0x88404040);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ReflectionHelper.cacheFrameFromRenderThread(Minecraft.getInstance());

        boolean capturing = ReflectionHelper.isScreenshotInProgress();

        if (!capturing) {
            this.renderBackground(g, mouseX, mouseY, partialTick);

            Component title = Component.translatable("mcpmod.control.overlay");
            int textW = this.font.width(title);
            g.drawString(this.font, title, (this.width - textW) / 2, Math.max(20, this.height / 5), 0xFFFFFFFF, true);

            if (debugUrl != null) {
                Component urlLabel = Component.literal(debugUrl).withStyle(s -> s
                    .withColor(0x55FF55)
                    .withUnderlined(true)
                );
                int urlTextW = this.font.width(urlLabel);
                int cx = (this.width - urlTextW) / 2;
                int cy = Math.max(20, this.height / 5) + 14;
                urlX = cx;
                urlY = cy;
                urlWidth = urlTextW;
                urlHeight = 9;
                g.drawString(this.font, urlLabel, cx, cy, 0xFF55FF55, false);
            }

            for (var renderable : this.renderables) {
                renderable.render(g, mouseX, mouseY, partialTick);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && debugUrl != null && !ReflectionHelper.isScreenshotInProgress()) {
            if (mouseX >= urlX && mouseX <= urlX + urlWidth && mouseY >= urlY && mouseY <= urlY + urlHeight) {
                Util.getPlatform().openUri(URI.create(debugUrl));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        currentInstance = null;
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        if (!ReflectionHelper.isMcpControlMode()) {
            Minecraft.getInstance().setScreen(null);
        }
    }
}

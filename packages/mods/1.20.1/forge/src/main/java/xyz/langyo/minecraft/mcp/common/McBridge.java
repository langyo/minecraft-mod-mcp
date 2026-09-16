package xyz.langyo.minecraft.mcp.common;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

/**
 * 编译期直连桥接（Forge 1.20.1，official mappings）。
 *
 * <p>本模组的公共代码大量依赖运行期反射按字符串名查找成员。Forge 1.20.1 的生产环境
 * 会把成员名重混淆为 SRG 形式（例如 {@code Minecraft.player} {@code f_91074_}），
 * 因此按 official 名（{@code "player"}、{@code "level"}、{@code "execute"} 等）反射查找
 * 在生产环境会全部落空：玩家/世界被判定为 null、主线程调度找不到 {@code execute(Runnable)}、
 * 窗口尺寸取到 0。</p>
 *
 * <p>这里把这些成员改为编译期直接引用；构建时的 reobf 会把引用自动改写成 SRG，
 * 所以开发环境与生产环境都可用。反射路径仍作为兜底保留。</p>
 */
public final class McBridge {

    private McBridge() {}

    public static boolean isMinecraft(Object o) { return o instanceof Minecraft; }

    public static boolean isPlayer(Object o) { return o instanceof Player; }

    public static boolean isLevel(Object o) { return o instanceof Level; }

    public static Minecraft asMc(Object o) { return o instanceof Minecraft m ? m : null; }

    // ===== Minecraft 顶层字段 =====

    public static Object getPlayer(Object mc) {
        Minecraft m = asMc(mc);
        return m != null ? m.player : null;
    }

    public static Object getLevel(Object mc) {
        Minecraft m = asMc(mc);
        return m != null ? m.level : null;
    }

    public static Object getScreen(Object mc) {
        Minecraft m = asMc(mc);
        return m != null ? m.screen : null;
    }

    public static Object getMouseHandler(Object mc) {
        Minecraft m = asMc(mc);
        return m != null ? m.mouseHandler : null;
    }

    public static Object getKeyboardHandler(Object mc) {
        Minecraft m = asMc(mc);
        return m != null ? m.keyboardHandler : null;
    }

    public static Object getGameMode(Object mc) {
        Minecraft m = asMc(mc);
        return m != null ? m.gameMode : null;
    }

    public static Object getWindow(Object mc) {
        Minecraft m = asMc(mc);
        return m != null ? m.getWindow() : null;
    }

    public static long getWindowHandle(Object mc) {
        Minecraft m = asMc(mc);
        return m != null ? m.getWindow().getWindow() : 0L;
    }

    public static int getDisplayWidth(Object mc) {
        Minecraft m = asMc(mc);
        return m != null ? m.getWindow().getWidth() : 0;
    }

    public static int getDisplayHeight(Object mc) {
        Minecraft m = asMc(mc);
        return m != null ? m.getWindow().getHeight() : 0;
    }

    public static int getGuiScaledWidth(Object mc) {
        Minecraft m = asMc(mc);
        return m != null ? m.getWindow().getGuiScaledWidth() : 0;
    }

    public static int getGuiScaledHeight(Object mc) {
        Minecraft m = asMc(mc);
        return m != null ? m.getWindow().getGuiScaledHeight() : 0;
    }

    public static double getGuiScale(Object mc) {
        Minecraft m = asMc(mc);
        return m != null ? m.getWindow().getGuiScale() : 1.0;
    }

    public static boolean isSameThread(Object mc) {
        Minecraft m = asMc(mc);
        return m != null && m.isSameThread();
    }

    public static void execute(Object mc, Runnable task) {
        Minecraft m = asMc(mc);
        if (m != null) m.execute(task);
    }

    public static Object getMainRenderTarget(Object mc) {
        Minecraft m = asMc(mc);
        return m != null ? m.getMainRenderTarget() : null;
    }

    public static Object getSingleplayerServer(Object mc) {
        Minecraft m = asMc(mc);
        return m != null ? m.getSingleplayerServer() : null;
    }

    public static Object getPlayerLevel(Object player) {
        return player instanceof Entity e ? e.level() : null;
    }

    public static void setScreen(Object mc, Object screen) {
        Minecraft m = asMc(mc);
        if (m != null) m.setScreen(screen instanceof Screen s ? s : null);
    }

    public static boolean openChatScreen(Object mc) {
        Minecraft m = asMc(mc);
        if (m == null) return false;
        m.setScreen(new ChatScreen(""));
        return true;
    }

    public static boolean closeCurrentScreen(Object mc) {
        Minecraft m = asMc(mc);
        if (m == null || m.screen == null) return false;
        m.screen.onClose();
        return true;
    }

    public static boolean openPauseMenu(Object mc) {
        Minecraft m = asMc(mc);
        if (m == null || m.level == null) return false;
        m.pauseGame(false);
        return true;
    }

    // ===== 玩家数据 =====

    public static String getPlayerName(Object player) {
        if (player instanceof Player p) {
            try { return p.getGameProfile().getName(); } catch (Throwable ignored) { return null; }
        }
        return null;
    }

    public static double getPlayerHealth(Object player) {
        return player instanceof LivingEntity le ? le.getHealth() : 0.0;
    }

    public static double[] getPlayerPosition(Object player) {
        if (player instanceof Entity e) return new double[]{e.getX(), e.getY(), e.getZ()};
        return null;
    }

    public static float[] getPlayerRotation(Object player) {
        if (player instanceof Entity e) return new float[]{e.getYRot(), e.getXRot()};
        return null;
    }

    public static void setPlayerRotation(Object player, float yaw, float pitch) {
        if (player instanceof Entity e) {
            e.setYRot(yaw);
            e.setXRot(pitch);
            if (e instanceof LivingEntity le) {
                le.setYHeadRot(yaw);
                le.setYBodyRot(yaw);
            }
        }
    }

    public static int getPlayerFoodLevel(Object player) {
        if (player instanceof Player p) {
            FoodData fd = p.getFoodData();
            return fd != null ? fd.getFoodLevel() : 0;
        }
        return -1;
    }

    public static String getPlayerDimension(Object player) {
        if (player instanceof Entity e) {
            Level l = e.level();
            if (l != null) return l.dimension().location().toString();
        }
        return null;
    }

    public static String getGameTypeName(Object mc) {
        Minecraft m = asMc(mc);
        if (m == null || m.gameMode == null) return null;
        GameType gt = m.gameMode.getPlayerMode();
        return gt != null ? gt.getName() : null;
    }

    public static String setGameMode(Object mc, String mode) {
        Minecraft m = asMc(mc);
        if (m == null || m.player == null || mode == null) return null;
        MinecraftServer server = m.getSingleplayerServer();
        if (server == null || server.getPlayerList() == null) return null;
        GameType gt = GameType.byName(mode.toLowerCase(java.util.Locale.ROOT), null);
        if (gt == null) return null;
        java.util.UUID playerId = m.player.getUUID();
        try {
            return server.submit(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayer(playerId);
                if (sp == null) return null;
                if (sp.gameMode.getGameModeForPlayer() == gt || sp.setGameMode(gt)) return gt.getName();
                return null;
            }).get(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {}
        return null;
    }

    public static double getMouseX(Object mc) {
        Minecraft m = asMc(mc);
        return (m != null && m.mouseHandler != null) ? m.mouseHandler.xpos() : -1.0;
    }

    public static double getMouseY(Object mc) {
        Minecraft m = asMc(mc);
        return (m != null && m.mouseHandler != null) ? m.mouseHandler.ypos() : -1.0;
    }

    public static boolean isMouseGrabbed(Object mc) {
        Minecraft m = asMc(mc);
        return m != null && m.mouseHandler != null && m.mouseHandler.isMouseGrabbed();
    }

    public static void setMouseGrabbed(Object mc, boolean grabbed) {
        Minecraft m = asMc(mc);
        if (m == null || m.mouseHandler == null) return;
        try {
            if (grabbed) m.mouseHandler.grabMouse();
            else m.mouseHandler.releaseMouse();
        } catch (Throwable ignored) {}
    }

    // ===== 单机暂停 / 局域网 =====

    public static boolean hasSingleplayerServer(Object mc) {
        Minecraft m = asMc(mc);
        return m != null && m.hasSingleplayerServer();
    }

    public static boolean isGamePaused(Object mc) {
        Minecraft m = asMc(mc);
        return m != null && m.isPaused();
    }

    public static boolean isLanPublished(Object mc) {
        Minecraft m = asMc(mc);
        if (m == null) return false;
        IntegratedServer server = m.getSingleplayerServer();
        return server != null && server.isPublished();
    }

    public static int getLanPort(Object mc) {
        Minecraft m = asMc(mc);
        if (m == null) return -1;
        IntegratedServer server = m.getSingleplayerServer();
        return server != null ? server.getPort() : -1;
    }

    public static void resumeSound(Object mc) {
        Minecraft m = asMc(mc);
        if (m == null) return;
        try {
            net.minecraft.client.sounds.SoundManager sm = m.getSoundManager();
            if (sm != null) sm.resume();
        } catch (Throwable ignored) {}
    }

    /**
     * 把单机集成本地服务器发布到局域网。
     *
     * <p>成功发布后 {@code IntegratedServer.isPublished()} 为 true，{@code Minecraft.runTick}
     * 推导出的暂停标志恒为 false：ESC 菜单不再冻结世界，局域网内其它客户端也能直接加入
     * 并获得独立视角（即「让 Codex 作为第二名玩家进游戏」这条路）。</p>
     *
     * @return true 表示发布成功或此前已发布；false 表示当前不是单机本地世界或端口被占用
     */
    public static boolean openToLan(Object mc, String gameTypeName, boolean allowCheats, int port) {
        Minecraft m = asMc(mc);
        if (m == null || !m.hasSingleplayerServer() || m.player == null || m.level == null) return false;
        IntegratedServer server = m.getSingleplayerServer();
        if (server == null) return false;
        if (server.isPublished()) return true;
        GameType gt = GameType.byName(
                gameTypeName == null ? "survival" : gameTypeName.toLowerCase(java.util.Locale.ROOT),
                GameType.SURVIVAL);
        try {
            return server.publishServer(gt, allowCheats, port);
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ===== 世界数据 =====

    public static String getDifficultyKey(Object level) {
        if (level instanceof Level l) {
            Difficulty d = l.getDifficulty();
            if (d != null) return d.getKey();
        }
        return null;
    }

    public static long getDayTime(Object level) {
        return level instanceof Level l ? l.getDayTime() : 0L;
    }

    public static boolean isRaining(Object level) {
        return level instanceof Level l && l.isRaining();
    }

    public static boolean isThundering(Object level) {
        return level instanceof Level l && l.isThundering();
    }

    public static String getWorldName(Object mc) {
        Minecraft m = asMc(mc);
        if (m == null) return null;
        IntegratedServer server = m.getSingleplayerServer();
        if (server == null) return null;
        try { return server.getWorldData().getLevelName(); } catch (Throwable ignored) { return null; }
    }

    // ===== 指令 / 聊天发送 =====

    public static boolean sendCommandPacket(Object mc, String command) {
        Minecraft m = asMc(mc);
        if (m == null || m.player == null || m.player.connection == null) return false;
        try {
            m.player.connection.sendCommand(command);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean sendChatPacket(Object mc, String message) {
        Minecraft m = asMc(mc);
        if (m == null || m.player == null || m.player.connection == null) return false;
        try {
            m.player.connection.sendChat(message);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ===== 界面控件 =====

    /** 当前界面的子控件列表；没有界面时返回 null（与“空列表”区分）。 */
    public static java.util.List<?> screenChildren(Object mc) {
        Minecraft m = asMc(mc);
        if (m == null || m.screen == null) return null;
        return m.screen.children();
    }

    public static boolean isWidget(Object o) { return o instanceof AbstractWidget; }

    public static boolean isButtonWidget(Object o) { return o instanceof AbstractButton; }

    public static int widgetX(Object o) { return o instanceof AbstractWidget w ? w.getX() : Integer.MIN_VALUE; }

    public static int widgetY(Object o) { return o instanceof AbstractWidget w ? w.getY() : Integer.MIN_VALUE; }

    public static int widgetWidth(Object o) { return o instanceof AbstractWidget w ? w.getWidth() : 0; }

    public static int widgetHeight(Object o) { return o instanceof AbstractWidget w ? w.getHeight() : 0; }

    public static boolean widgetActive(Object o) { return o instanceof AbstractWidget w && w.active; }

    public static String widgetLabel(Object o) {
        if (o instanceof AbstractWidget w) {
            net.minecraft.network.chat.Component c = w.getMessage();
            return c != null ? c.getString() : "";
        }
        return "";
    }

    public static boolean pressWidget(Object o) {
        if (o instanceof AbstractWidget w && w.active && w.visible) {
            return w.mouseClicked(w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0, 0);
        }
        return false;
    }

    public static boolean screenMouseClicked(Object mc, double x, double y, int button) {
        Minecraft m = asMc(mc);
        if (m == null || m.screen == null) return false;
        try { return m.screen.mouseClicked(x, y, button); } catch (Throwable ignored) { return false; }
    }

    public static boolean screenMouseReleased(Object mc, double x, double y, int button) {
        Minecraft m = asMc(mc);
        if (m == null || m.screen == null) return false;
        try { return m.screen.mouseReleased(x, y, button); } catch (Throwable ignored) { return false; }
    }

    public static boolean screenMouseScrolled(Object mc, double x, double y, double delta) {
        Minecraft m = asMc(mc);
        if (m == null || m.screen == null) return false;
        try { return m.screen.mouseScrolled(x, y, delta); } catch (Throwable ignored) { return false; }
    }

    public static boolean screenKeyPressed(Object mc, int keyCode, int scanCode, int modifiers) {
        Minecraft m = asMc(mc);
        if (m == null || m.screen == null) return false;
        try { return m.screen.keyPressed(keyCode, scanCode, modifiers); } catch (Throwable ignored) { return false; }
    }

    public static boolean screenCharTyped(Object mc, char c, int modifiers) {
        Minecraft m = asMc(mc);
        if (m == null || m.screen == null) return false;
        try { return m.screen.charTyped(c, modifiers); } catch (Throwable ignored) { return false; }
    }

    public static void keyboardKeyPress(Object mc, long handle, int keyCode, int scanCode, int action, int modifiers) {
        Minecraft m = asMc(mc);
        if (m != null && m.keyboardHandler != null) {
            try { m.keyboardHandler.keyPress(handle, keyCode, scanCode, action, modifiers); } catch (Throwable ignored) {}
        }
    }

    // ===== 截图 =====

    /**
     * 用原版 {@code Screenshot.takeScreenshot(RenderTarget)} 抓取当前主渲染目标并编码成 PNG。
     * 必须在渲染线程调用；失败返回 null。
     */
    public static byte[] nativeScreenshotPng(Object mc) {
        Minecraft m = asMc(mc);
        if (m == null) return null;
        RenderTarget rt = m.getMainRenderTarget();
        if (rt == null) return null;
        NativeImage img = null;
        java.io.File tmp = null;
        try {
            img = net.minecraft.client.Screenshot.takeScreenshot(rt);
            if (img == null) return null;
            tmp = java.io.File.createTempFile("mcp_screenshot_", ".png");
            img.writeToFile(tmp);
            byte[] data = java.nio.file.Files.readAllBytes(tmp.toPath());
            return (data != null && data.length > 0) ? data : null;
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (tmp != null) { try { tmp.delete(); } catch (Throwable ignored) {} }
            if (img != null) { try { img.close(); } catch (Throwable ignored) {} }
        }
    }
}

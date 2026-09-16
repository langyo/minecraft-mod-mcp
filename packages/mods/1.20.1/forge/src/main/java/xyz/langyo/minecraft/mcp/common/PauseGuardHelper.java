package xyz.langyo.minecraft.mcp.common;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * 单机模式下「按 ESC 不再冻结世界」。
 *
 * <p>Minecraft 1.20.1 的 {@code Minecraft.runTick} 每帧都会这样推导暂停标志：</p>
 *
 * <pre>
 * pause = hasSingleplayerServer()
 *      &amp;&amp; (screen != null &amp;&amp; screen.isPauseScreen() || overlay != null &amp;&amp; overlay.isPauseScreen())
 *      &amp;&amp; !singleplayerServer.isPublished();
 * </pre>
 *
 * <p>{@code isPaused()} 只是返回这个字段，而 {@code IntegratedServer.tickServer} 又会把
 * {@code Minecraft.isPaused()} 抄过去决定服务端要不要空转。也就是说：只要 ESC 菜单的
 * {@code isPauseScreen()} 返回 true，而且本地世界还没开局域网，世界与集成本地服务器都会停摆。</p>
 *
 * <p>这里不去改那个每帧重算的字段（改了下帧就被覆盖），而是走 Forge 的
 * {@code ScreenEvent.Opening}：把原版 {@link PauseScreen} 换成一个行为完全一样的匿名子类，
 * 只把它继承自 {@link Screen} 的 {@code isPauseScreen()} 覆写成 false。菜单、按钮、音效路径
 * 全部照旧，只有「暂停」这一件事不再发生。</p>
 *
 * <p>开关由 {@link ControlModeHelper#isNoPause()} 控制，默认开启；
 * 关闭后完全恢复原版暂停行为。</p>
 */
public final class PauseGuardHelper {

    private PauseGuardHelper() {}

    /**
     * 本次演示当前是否真的把菜单换成了不暂停版本。
     * 仅用于状态展示，不代表「本帧世界一定在跑」。
     */
    private static volatile boolean lastWrapped = false;

    public static boolean isLastWrapped() { return lastWrapped; }

    /**
     * 判断当前是否应该绕过暂停：
     * 功能开启 + 确实在单机集成本地世界 + 该世界还没被发布到局域网。
     * 已经开局域网的世界本来就不会暂停，无需再包一层。
     */
    public static boolean shouldBypassPause(Object mcObj) {
        if (!ControlModeHelper.isNoPause()) {
            lastWrapped = false;
            return false;
        }
        Minecraft mc = McBridge.asMc(mcObj);
        if (mc == null || mc.level == null) return false;
        if (!mc.hasSingleplayerServer()) return false;
        try {
            net.minecraft.client.server.IntegratedServer server = mc.getSingleplayerServer();
            if (server == null || server.isPublished()) return false;
        } catch (Throwable t) {
            return false;
        }
        return true;
    }

    /**
     * 如果传入的正是原版 {@link PauseScreen}，返回一个「不暂停」的等价菜单；否则原样返回。
     * 只处理 {@code getClass() == PauseScreen.class}，不动其它模组或子类的暂停界面。
     */
    public static Screen wrap(Screen screen) {
        if (!(screen instanceof PauseScreen)) return screen;
        if (screen.getClass() != PauseScreen.class) return screen;
        boolean showMenu = readShowPauseMenu(screen);
        lastWrapped = true;
        return new PauseScreen(showMenu) {
            @Override
            public boolean isPauseScreen() {
                return !ControlModeHelper.isNoPause();
            }
        };
    }

    /**
     * 读取 {@code PauseScreen.showPauseMenu}。生产环境字段名会被重混淆成 SRG，
     * 所以按「类里唯一的 boolean 字段」来认，读不到时退回 true（标准暂停菜单）。
     */
    private static boolean readShowPauseMenu(Screen screen) {
        Boolean found = null;
        for (Class<?> c = screen.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType() != boolean.class) continue;
                if (f.isSynthetic() || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    boolean v = f.getBoolean(screen);
                    if (found == null) {
                        found = v;
                    } else {
                        // 出现第二个 boolean 字段说明认错了，保守起见用 true
                        return true;
                    }
                } catch (Throwable ignored) {
                    return true;
                }
            }
            if (found != null) break;
        }
        return found == null ? true : found;
    }

    /** 关闭「不暂停」时，把是否已经生效的标记一并复位。 */
    public static void reset() { lastWrapped = false; }
}

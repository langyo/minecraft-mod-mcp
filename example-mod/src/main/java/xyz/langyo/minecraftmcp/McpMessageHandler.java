package xyz.langyo.minecraftmcp;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class McpMessageHandler {
    private static final Gson GSON = new Gson();
    private static final ConcurrentHashMap<String, CompletableFuture<JsonObject>> pendingCommands = new ConcurrentHashMap<>();

    public static JsonObject handle(String action, JsonObject params) {
        return switch (action) {
            case "screenshot" -> handleScreenshot(params);
            case "click" -> handleClick(params);
            case "press_key" -> handlePressKey(params);
            case "type_text" -> handleTypeText(params);
            case "scroll" -> handleScroll(params);
            case "hotkey" -> handleHotkey(params);
            case "execute_command" -> handleExecuteCommand(params);
            case "get_player_info" -> handleGetPlayerInfo(params);
            case "get_world_info" -> handleGetWorldInfo(params);
            case "set_gui_screen" -> handleSetGuiScreen(params);
            case "test_echo" -> handleTestEcho(params);
            case "ping" -> handlePing(params);
            default -> createError("unknown action: " + action);
        };
    }

    private static JsonObject handleScreenshot(JsonObject params) {
        try {
            Minecraft mc = Minecraft.getInstance();
            int width = mc.getWindow().getWidth();
            int height = mc.getWindow().getHeight();

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();

            Robot robot = new Robot();
            Point windowPos = mc.getWindow().getWindow().getLocation();
            Rectangle screenRect = new Rectangle(windowPos.x, windowPos.y, width, height);
            BufferedImage screenCapture = robot.createScreenCapture(screenRect);

            g2d.drawImage(screenCapture, 0, 0, null);
            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

            String savePath = params.has("save_path") ? params.get("save_path").getAsString() : null;
            if (savePath != null) {
                java.io.File outFile = new java.io.File(savePath);
                javax.imageio.ImageIO.write(image, "png", outFile);
            }

            JsonObject result = new JsonObject();
            result.addProperty("data", base64);
            result.addProperty("width", width);
            result.addProperty("height", height);
            return result;
        } catch (Exception e) {
            return createError("screenshot failed: " + e.getMessage());
        }
    }

    private static JsonObject handleClick(JsonObject params) {
        try {
            int x = params.has("x") ? params.get("x").getAsInt() : -1;
            int y = params.has("y") ? params.get("y").getAsInt() : -1;

            InputSimulator.simulateClick(x, y);

            JsonObject result = new JsonObject();
            result.addProperty("message", "clicked at (" + x + ", " + y + ")");
            return result;
        } catch (Exception e) {
            return createError("click failed: " + e.getMessage());
        }
    }

    private static JsonObject handlePressKey(JsonObject params) {
        try {
            String key = params.has("key") ? params.get("key").getAsString() : "";
            double holdSeconds = params.has("hold_seconds") ? params.get("hold_seconds").getAsDouble() : 0.05;

            InputSimulator.simulateKeyPress(key, (long)(holdSeconds * 1000));

            JsonObject result = new JsonObject();
            result.addProperty("message", "pressed key: " + key);
            return result;
        } catch (Exception e) {
            return createError("press_key failed: " + e.getMessage());
        }
    }

    private static JsonObject handleTypeText(JsonObject params) {
        try {
            String text = params.has("text") ? params.get("text").getAsString() : "";
            boolean pressEnter = params.has("press_enter") && params.get("press_enter").getAsBoolean();

            InputSimulator.simulateTypeText(text, pressEnter);

            JsonObject result = new JsonObject();
            result.addProperty("message", "typed: " + text + (pressEnter ? " [ENTER]" : ""));
            return result;
        } catch (Exception e) {
            return createError("type_text failed: " + e.getMessage());
        }
    }

    private static JsonObject handleScroll(JsonObject params) {
        try {
            int clicks = params.has("clicks") ? params.get("clicks").getAsInt() : 1;

            InputSimulator.simulateScroll(clicks);

            JsonObject result = new JsonObject();
            result.addProperty("message", "scrolled " + clicks + " clicks");
            return result;
        } catch (Exception e) {
            return createError("scroll failed: " + e.getMessage());
        }
    }

    private static JsonObject handleHotkey(JsonObject params) {
        try {
            var keysArr = params.getAsJsonArray("keys");
            if (keysArr == null) return createError("missing keys array");

            String[] keys = new String[keysArr.size()];
            for (int i = 0; i < keysArr.size(); i++) keys[i] = keysArr.get(i).getAsString();

            InputSimulator.simulateHotkey(keys);

            JsonObject result = new JsonObject();
            result.addProperty("message", "hotkey: " + String.join("+", keys));
            return result;
        } catch (Exception e) {
            return createError("hotkey failed: " + e.getMessage());
        }
    }

    private static JsonObject handleExecuteCommand(JsonObject params) {
        try {
            String command = params.has("command") ? params.get("command").getAsString() : "";
            if (command.isEmpty()) return createError("missing command");

            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) return createError("no player in game");

            AtomicReference<JsonObject> response = new AtomicReference<>();
            mc.execute(() -> {
                player.connection.sendCommand(command);
                response.set(createResult("command sent: " + command));
            });

            Thread.sleep(100);
            return response.get() != null ? response.get() : createResult("command queued: " + command);
        } catch (Exception e) {
            return createError("execute_command failed: " + e.getMessage());
        }
    }

    private static JsonObject handleGetPlayerInfo(JsonObject params) {
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;

            JsonObject info = new JsonObject();
            if (player != null) {
                info.addProperty("name", player.getName().getString());
                info.addProperty("health", player.getHealth());
                info.addProperty("foodLevel", player.getFoodData().getFoodLevel());
                info.addProperty("positionX", player.getX());
                info.addProperty("positionY", player.getY());
                info.addProperty("positionZ", player.getZ());
                info.addProperty("dimension", player.level().dimension().location().toString());
                info.addProperty("gamemode", player.gameMode.getGameModeForPlayer().getName());
                info.addProperty("inGame", true);
            } else {
                info.addProperty("inGame", false);
            }
            return info;
        } catch (Exception e) {
            return createError("get_player_info failed: " + e.getMessage());
        }
    }

    private static JsonObject handleGetWorldInfo(JsonObject params) {
        try {
            Minecraft mc = Minecraft.getInstance();
            var level = mc.level;

            JsonObject info = new JsonObject();
            if (level != null) {
                info.addProperty("worldName", level.getServer().getWorldData().getLevelName());
                info.addProperty("difficulty(level.getDifficulty().getKey()));
                info.addProperty("dayTime", level.getDayTime());
                info.addProperty("gameType(mc.isSingleplayer() ? "singleplayer" : "multiplayer"));
                info.addProperty("seed", level.getSeed());
            } else {
                info.addProperty("loaded", false);
            }
            return info;
        } catch (Exception e) {
            return createError("get_world_info failed: " + e.getMessage());
        }
    }

    private static JsonObject handleSetGuiScreen(JsonObject params) {
        try {
            String screenType = params.has("screen") ? params.get("screen").getAsString() : "";
            Minecraft mc = Minecraft.getInstance();

            return switch (screenType.toLowerCase()) {
                case "title" -> { mc.setScreen(net.minecraft.client.gui.screens.TitleScreen()); yield createResult("opened title screen"); }
                case "options" -> { mc.setScreen(new net.minecraft.client.gui.screens.OptionsScreen(null, mc.options)); yield createResult("opened options"); }
                case "inventory" -> {
                    if (mc.player != null) mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                    yield createResult("opened inventory");
                }
                case "close" -> { mc.setScreen(null); yield createResult("closed GUI"); }
                default -> createError("unknown screen: " + screenType);
            };
        } catch (Exception e) {
            return createError("set_gui_screen failed: " + e.getMessage());
        }
    }

    private static JsonObject handleTestEcho(JsonObject params) {
        JsonObject result = new JsonObject();
        result.addProperty("echo", GSON.toJson(params));
        result.addProperty("timestamp", System.currentTimeMillis());
        result.addProperty("modVersion", "1.0.0");
        result.addProperty("modId", "minecraft_mcp_example");
        result.addProperty("package", "minecraft-mcp.langyo.xyz");
        return result;
    }

    private static JsonObject handlePing(JsonObject params) {
        JsonObject result = new JsonObject();
        result.addProperty("pong", true);
        result.addProperty("timestamp", System.currentTimeMillis());
        result.addProperty("serverTime", System.currentTimeMillis());
        return result;
    }

    private static JsonObject createResult(String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("data", message);
        return obj;
    }

    private static JsonObject createError(String error) {
        JsonObject obj = new JsonObject();
        obj.addProperty("error", error);
        return obj;
    }
}

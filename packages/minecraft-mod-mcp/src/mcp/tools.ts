import { z } from "zod";

export interface ToolDef {
  name: string;
  description: string;
  inputSchema: z.ZodTypeAny;
  requiresControl?: boolean;
}

function zOpt<T extends z.ZodTypeAny>(schema: T) {
  return schema.optional();
}

const MouseButton = z.enum(["left", "right", "middle"]);

export const TOOLS: ToolDef[] = [
  {
    name: "ping",
    description: "Ping the Minecraft mod. Always available. Returns \"pong\".",
    inputSchema: z.object({}),
  },
  {
    name: "screenshot",
    description: "Capture a screenshot of the current Minecraft game window. Returns base64 PNG with coordinate grid overlay.",
    inputSchema: z.object({}),
  },
  {
    name: "screenshot_to_file",
    description: "Capture a screenshot and save to disk. Returns file path and size.",
    inputSchema: z.object({
      path: z.string().optional().describe("File path to save to. Defaults to screenshots/vtty/ directory."),
    }),
  },
  {
    name: "get_player_info",
    description: "Get player state: position, rotation, health, hunger, game mode, dimension, etc.",
    inputSchema: z.object({}),
  },
  {
    name: "get_world_info",
    description: "Get world state: seed, time, weather, difficulty, loaded chunks, entities, etc.",
    inputSchema: z.object({}),
  },
  {
    name: "debug_fields",
    description: "Get the debug overlay fields (F3 screen data): FPS, coordinates, biome, etc.",
    inputSchema: z.object({}),
  },
  {
    name: "get_screen_buttons",
    description: "List all interactive buttons on the current GUI screen with IDs, labels, bounds.",
    inputSchema: z.object({}),
  },
  {
    name: "enumerate_widgets",
    description: "Enumerate the full widget tree of the current GUI screen.",
    inputSchema: z.object({}),
  },
  {
    name: "enter_control_mode",
    description: "Enter MCP control mode — releases mouse cursor, enables click/type/keyboard input. User must have activated via PauseScreen button or overlay click first.",
    inputSchema: z.object({}),
  },
  {
    name: "exit_control_mode",
    description: "Exit MCP control mode — returns to normal Minecraft input handling.",
    inputSchema: z.object({}),
  },
  {
    name: "release_mouse",
    description: "Release the mouse cursor from Minecraft's grab.",
    inputSchema: z.object({}),
  },
  {
    name: "pause_game",
    description: "Pause the game (open pause screen).",
    inputSchema: z.object({}),
  },
  {
    name: "close_screen",
    description: "Close the current GUI screen.",
    inputSchema: z.object({}),
    requiresControl: true,
  },
  {
    name: "open_chat",
    description: "Open the chat input box.",
    inputSchema: z.object({}),
    requiresControl: true,
  },
  {
    name: "set_gamemode",
    description: "Change the player's game mode.",
    inputSchema: z.object({
      mode: z.enum(["survival", "creative", "adventure", "spectator"]).describe("Game mode to set."),
    }),
  },
  {
    name: "click",
    description: "Click at the specified screen coordinates. Requires control mode.",
    inputSchema: z.object({
      x: z.number().int().describe("X coordinate in screen pixels."),
      y: z.number().int().describe("Y coordinate in screen pixels."),
      button: zOpt(MouseButton.describe("Mouse button. Defaults to \"left\".")).default("left"),
    }),
    requiresControl: true,
  },
  {
    name: "right_click",
    description: "Perform a right-click (use item / place block). Requires control mode.",
    inputSchema: z.object({}),
    requiresControl: true,
  },
  {
    name: "mouse_drag",
    description: "Drag mouse from one point to another. Requires control mode.",
    inputSchema: z.object({
      x_start: z.number().int().describe("Start X."),
      y_start: z.number().int().describe("Start Y."),
      x_end: z.number().int().describe("End X."),
      y_end: z.number().int().describe("End Y."),
      button: zOpt(MouseButton).default("left"),
    }),
    requiresControl: true,
  },
  {
    name: "scroll",
    description: "Scroll the mouse wheel. Requires control mode.",
    inputSchema: z.object({
      clicks: z.number().int().describe("Number of scroll clicks. Positive = up, negative = down."),
    }),
    requiresControl: true,
  },
  {
    name: "scroll_at",
    description: "Scroll at specific coordinates. Requires control mode.",
    inputSchema: z.object({
      x: z.number().int(),
      y: z.number().int(),
      clicks: z.number().int(),
    }),
    requiresControl: true,
  },
  {
    name: "press_key",
    description: "Press a keyboard key. Requires control mode.",
    inputSchema: z.object({
      key: z.string().describe("Key name (e.g. \"key.keyboard.w\", \"key.keyboard.space\", \"A\")."),
      hold_seconds: zOpt(z.number().describe("Duration to hold the key in seconds.")).default(0),
    }),
    requiresControl: true,
  },
  {
    name: "type_text",
    description: "Type text into the focused text field. Requires control mode.",
    inputSchema: z.object({
      text: z.string().describe("Text to type."),
      press_enter: zOpt(z.boolean().describe("Whether to press Enter after typing.")).default(false),
    }),
    requiresControl: true,
  },
  {
    name: "paste_text",
    description: "Paste text (uses clipboard injection). Requires control mode.",
    inputSchema: z.object({
      text: z.string().describe("Text to paste."),
      press_enter: zOpt(z.boolean()).default(false),
    }),
    requiresControl: true,
  },
  {
    name: "hotkey",
    description: "Press a key combination (e.g. Ctrl+S). Requires control mode.",
    inputSchema: z.object({
      keys: z.string().describe("Comma-separated key names, e.g. \"key.keyboard.left control,key.keyboard.s\"."),
    }),
    requiresControl: true,
  },
  {
    name: "click_button_id",
    description: "Click a GUI button by its numeric ID. Requires control mode.",
    inputSchema: z.object({
      id: z.number().int().describe("Button ID from get_screen_buttons."),
    }),
    requiresControl: true,
  },
  {
    name: "click_button_index",
    description: "Click a GUI button by its index (0-based). Requires control mode.",
    inputSchema: z.object({
      index: z.number().int().describe("Button index."),
    }),
    requiresControl: true,
  },
  {
    name: "switch_tab",
    description: "Switch to a tab by index in a tabbed GUI. Requires control mode.",
    inputSchema: z.object({
      index: z.number().int().describe("Tab index."),
    }),
    requiresControl: true,
  },
  {
    name: "execute_command",
    description: "Execute a Minecraft slash command (e.g. \"/gamemode creative\"). Requires control mode.",
    inputSchema: z.object({
      command: z.string().describe("The command string, with or without leading /."),
    }),
    requiresControl: true,
  },
  {
    name: "set_view_angle",
    description: "Set the player's view angles. Requires control mode.",
    inputSchema: z.object({
      yaw: z.number().describe("Yaw angle in degrees (-180 to 180)."),
      pitch: z.number().describe("Pitch angle in degrees (-90 to 90)."),
    }),
    requiresControl: true,
  },
  {
    name: "look_delta",
    description: "Rotate view by a delta. Requires control mode.",
    inputSchema: z.object({
      delta_yaw: z.number().describe("Yaw change in degrees."),
      delta_pitch: z.number().describe("Pitch change in degrees."),
    }),
    requiresControl: true,
  },
  {
    name: "use_item",
    description: "Use the currently held item. Requires control mode.",
    inputSchema: z.object({}),
    requiresControl: true,
  },
  {
    name: "place_block",
    description: "Place a block at the current target position. Requires control mode.",
    inputSchema: z.object({}),
    requiresControl: true,
  },
  {
    name: "overlay_click",
    description: "Click on the MCP overlay (e.g. the resume/transfer button).",
    inputSchema: z.object({
      x: z.number().int(),
      y: z.number().int(),
    }),
  },
  {
    name: "wait",
    description: "Wait for a specified duration. Useful for sequencing actions.",
    inputSchema: z.object({
      seconds: z.number().positive().describe("Seconds to wait."),
    }),
  },
  {
    name: "launch_minecraft",
    description: "Launch a Minecraft instance with the specified version and mod loader. Automatically downloads and installs the version if not already present.",
    inputSchema: z.object({
      version: z.string().describe("Minecraft version, e.g. \"1.21.7\", \"26.1.2\"."),
      loader: zOpt(z.enum(["forge", "fabric", "neoforge"])).default("forge"),
      memory: zOpt(z.number()).describe("Max memory pool in MB (default: 2048)"),
      min_memory: zOpt(z.number()).describe("Min memory pool in MB"),
      jvm_args: zOpt(z.string()).describe("Extra JVM arguments (space-separated)"),
      game_args: zOpt(z.string()).describe("Extra game arguments (space-separated)"),
      fullscreen: zOpt(z.boolean()).describe("Launch in fullscreen mode"),
      width: zOpt(z.number()).describe("Window width in pixels"),
      height: zOpt(z.number()).describe("Window height in pixels"),
      server: zOpt(z.string()).describe("Auto-connect to this server host on launch"),
      server_port: zOpt(z.number()).describe("Server port (default: 25565)"),
    }),
  },
  {
    name: "kill_minecraft",
    description: "Kill the running Minecraft process.",
    inputSchema: z.object({}),
  },
  {
    name: "get_minecraft_status",
    description: "Get Minecraft process and mod connection status.",
    inputSchema: z.object({}),
  },
  {
    name: "install_version",
    description: "Download and install a Minecraft version with the specified mod loader. Downloads base MC + loader (Forge/Fabric/NeoForge).",
    inputSchema: z.object({
      version: z.string().describe("Minecraft version, e.g. \"1.21.7\", \"26.1.2\". Use list_supported_versions to see available versions."),
      loader: zOpt(z.enum(["forge", "fabric", "neoforge"])).default("forge"),
    }),
  },
  {
    name: "list_supported_versions",
    description: "List all supported Minecraft versions with their Java requirements, available loaders, and version IDs.",
    inputSchema: z.object({}),
  },
  {
    name: "list_installed_versions",
    description: "List all locally installed Minecraft versions.",
    inputSchema: z.object({}),
  },
  {
    name: "detect_java",
    description: "Detect all Java installations on the system. Returns version, vendor, and path for each.",
    inputSchema: z.object({}),
  },
  {
    name: "create_offline_account",
    description: "Create an offline Minecraft account for launching without authentication.",
    inputSchema: z.object({
      username: z.string().describe("Player username for the offline account."),
    }),
  },
  {
    name: "list_accounts",
    description: "List all configured Minecraft accounts (offline and Microsoft).",
    inputSchema: z.object({}),
  },
  {
    name: "install_server",
    description: "Download and set up a Minecraft server. Supports vanilla, Paper, and Spigot frameworks. Generates server.properties with custom settings.",
    inputSchema: z.object({
      version: z.string().describe("Minecraft version, e.g. \"1.21.11\", \"26.1.2\"."),
      loader: zOpt(z.enum(["forge", "fabric", "neoforge"])).default("forge"),
      server_type: zOpt(z.enum(["vanilla", "spigot", "craftbukkit", "paper", "forge", "fabric", "neoforge"])).describe("Server framework (default: vanilla)"),
      port: zOpt(z.number()).describe("Server port (default: 25565)"),
      motd: zOpt(z.string()).describe("Server MOTD message"),
      max_players: zOpt(z.number()).describe("Max players (default: 20)"),
      gamemode: zOpt(z.enum(["survival", "creative", "adventure", "spectator"])).describe("Default gamemode"),
      difficulty: zOpt(z.enum(["peaceful", "easy", "normal", "hard"])).describe("Difficulty"),
      online_mode: zOpt(z.boolean()).describe("Enable online authentication (default: false)"),
      level_seed: zOpt(z.string()).describe("World seed"),
      level_name: zOpt(z.string()).describe("World save name (default: world)"),
      level_type: zOpt(z.string()).describe("World type (default: flat)"),
      white_list: zOpt(z.boolean()).describe("Enable whitelist"),
    }),
  },
  {
    name: "launch_server",
    description: "Launch a Minecraft server. Supports vanilla, Paper, and Spigot frameworks. Auto-installs if not present.",
    inputSchema: z.object({
      version: z.string().describe("Minecraft version."),
      loader: zOpt(z.enum(["forge", "fabric", "neoforge"])).default("forge"),
      server_type: zOpt(z.enum(["vanilla", "spigot", "craftbukkit", "paper", "forge", "fabric", "neoforge"])).describe("Server framework (default: vanilla)"),
      memory: zOpt(z.number()).describe("Server max memory in MB (default: 1024)"),
      min_memory: zOpt(z.number()).describe("Server min memory in MB"),
      jvm_args: zOpt(z.string()).describe("Extra JVM arguments (space-separated)"),
      game_args: zOpt(z.string()).describe("Extra server arguments (space-separated)"),
      port: zOpt(z.number()).describe("Server port (default: 25565)"),
      motd: zOpt(z.string()).describe("Server MOTD message"),
      max_players: zOpt(z.number()).describe("Max players (default: 20)"),
      gamemode: zOpt(z.enum(["survival", "creative", "adventure", "spectator"])).describe("Default gamemode"),
      difficulty: zOpt(z.enum(["peaceful", "easy", "normal", "hard"])).describe("Difficulty"),
    }),
  },
  {
    name: "serve",
    description: "One-command: install server + launch server + launch client auto-connected. Supports vanilla, Paper, and Spigot server frameworks.",
    inputSchema: z.object({
      version: z.string().describe("Minecraft version."),
      loader: zOpt(z.enum(["forge", "fabric", "neoforge"])).default("forge"),
      server_type: zOpt(z.enum(["vanilla", "spigot", "craftbukkit", "paper", "forge", "fabric", "neoforge"])).describe("Server framework (default: vanilla)"),
      memory: zOpt(z.number()).describe("Client max memory in MB (default: 2048)"),
      server_memory: zOpt(z.number()).describe("Server max memory in MB (default: 1024)"),
      fullscreen: zOpt(z.boolean()).describe("Launch client in fullscreen"),
      width: zOpt(z.number()).describe("Client window width in pixels"),
      height: zOpt(z.number()).describe("Client window height in pixels"),
      port: zOpt(z.number()).describe("Server port (default: 25565)"),
      motd: zOpt(z.string()).describe("Server MOTD message"),
      max_players: zOpt(z.number()).describe("Max players (default: 20)"),
      gamemode: zOpt(z.enum(["survival", "creative", "adventure", "spectator"])).describe("Default gamemode"),
      difficulty: zOpt(z.enum(["peaceful", "easy", "normal", "hard"])).describe("Difficulty"),
    }),
  },
];

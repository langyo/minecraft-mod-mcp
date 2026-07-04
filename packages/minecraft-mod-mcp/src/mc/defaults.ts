export const LAUNCHER = {
  name: "MMML",
  nameGame: "MCP-Launcher",
  version: "0.2.1",
  versionType: "release",
  mcpServerName: "minecraft-mod-mcp",
} as const;

export const PLAYER = {
  defaultName: "Player",
  defaultUuid: "00000000-0000-0000-0000-000000000000",
  defaultAccessToken: "0",
  defaultUserType: "legacy",
} as const;

export const GAME = {
  defaultWidth: 854,
  defaultHeight: 480,
  defaultMaxMemoryMb: 2048,
  defaultMinMemoryMb: 512,
  defaultServerMemoryMb: 1024,
  defaultServerPort: 25565,
  serverStartupWaitMs: 15000,
  defaultVersion: "1.21.7",
  defaultLoader: "forge",
  defaultLanguage: "en-US",
  defaultDownloadSource: "mojang" as const,
  javaVersionFallback: 17,
  javaVersionThresholds: { mc117: 17, mc116: 16, mc113: 13 } as Record<string, number>,
  defaultOptionsTxt: [
    "version:29",
    "autoJump:false",
    "chatOpacity:1.0",
    "enableVsync:false",
    "forceUnicodeFont:false",
    "fov:70.0",
    "gamma:1.0",
    "guiScale:2",
    "lang:en_US",
    "maxFps:260",
    "musicVolume:0.0",
    "renderDistance:8",
    "soundCategory_master:0.0",
    "soundCategory_music:0.0",
    "soundCategory_record:0.0",
    "soundCategory_weather:0.0",
    "soundCategory_block:0.0",
    "soundCategory_hostile:0.0",
    "soundCategory_neutral:0.0",
    "soundCategory_player:0.0",
    "soundCategory_ambient:0.0",
    "soundCategory_voice:0.0",
    "showAccessibilityOnboardingScreen:false",
  ].join("\n"),
} as const;

export const MCP = {
  portStart: 9876,
  portEnd: 9000,
  heartbeatTimeoutMs: 2000,
  discoverTimeoutMs: 300000,
  bindAddress: "127.0.0.1",
  waitTimeoutMs: 120_000,
  pollIntervalMs: 3_000,
} as const;

export const PATHS = {
  mcDirName: ".minecraft",
  launcherDirName: "mcp_launcher",
  gradleJdksSubdir: ".gradle/jdks",
  versionsDirName: "versions",
  librariesDirName: "libraries",
  assetsDirName: "assets",
  nativesDirName: "natives",
  assetIndexesDirName: "indexes",
  assetObjectsDirName: "objects",
  tmpSuffix: ".tmp",
  javaDirName: "java",
  gameDirName: "game",
  serverDirName: "server",
} as const;

export const JAVA = {
  jdkDirPrefixes: {
    8: "eclipse_adoptium-8",
    16: "eclipse_foundation-16",
    17: "eclipse_adoptium-17",
    21: "eclipse_adoptium-21",
    25: "eclipse_adoptium-25",
  } as Record<number, string>,
  adoptiumApiUrl: "https://api.adoptium.net/v3/assets/latest",
  extractTimeoutMs: 120_000,
} as const;

export const DOWNLOAD = {
  versionManifestUrl: "https://piston-meta.mojang.com/mc/game/version_manifest.json",
  versionManifestV2Url: "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json",
  assetBaseUrl: "https://resources.download.minecraft.net/",
  assetBatchSize: 50,
  mavenLibrariesUrl: "https://libraries.minecraft.net/",
  forgeMavenUrl: "https://maven.minecraftforge.net/",
  neoforgeMavenUrl: "https://maven.neoforged.net/releases/",
  fabricMetaUrl: "https://meta.fabricmc.net/v2/versions/loader",
  jitpackUrl: "https://jitpack.io",
  fallbackRepoUrls: [
    "https://libraries.minecraft.net/",
    "https://maven.minecraftforge.net/",
    "https://maven.neoforged.net/releases/",
  ],
} as const;

export const AUTH = {
  microsoftClientId: "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb",
  deviceCodeUrl: "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode",
  tokenUrl: "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
  xblAuthUrl: "https://user.auth.xboxlive.com/user/authenticate",
  xstsAuthUrl: "https://xsts.auth.xboxlive.com/xsts/authorize",
  xblRelyingParty: "http://auth.xboxlive.com",
  xblSiteName: "user.auth.xboxlive.com",
  xstsSandboxId: "RETAIL",
  xstsRelyingParty: "rp://api.minecraftservices.com/",
  mcLoginUrl: "https://api.minecraftservices.com/authentication/login_with_xbox",
  mcProfileUrl: "https://api.minecraftservices.com/minecraft/profile",
  oauthScope: "XboxLive.signin offline_access",
  defaultExpiresIn: 3600,
  pollIntervalMs: 5000,
  slowDownIntervalMs: 10000,
} as const;

export type ServerType = "vanilla" | "spigot" | "craftbukkit" | "paper" | "forge" | "fabric" | "neoforge";

export const SERVER_TYPES: readonly ServerType[] = ["vanilla", "spigot", "craftbukkit", "paper", "forge", "fabric", "neoforge"] as const;

export const SERVER = {
  rconPort: 25575,
  networkCompressionThreshold: 256,
  maxTickTime: 60000,
  maxPlayers: 20,
  viewDistance: 10,
  simulationDistance: 10,
  maxWorldSize: 29999984,
  connectHost: "localhost",
  defaultJavaVersion: 21,
  userAgent: "minecraft-mcp",
  bindAddress: "0.0.0.0",
  eulaFileName: "eula.txt",
  propertiesFileName: "server.properties",
  modsDirName: "mods",
  defaultType: "vanilla" as ServerType,
  buildToolsUrl: "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar",
  paperApiUrl: "https://api.papermc.io/v2/projects/paper",
} as const;

export const MOD = {
  cmdEndpoint: "/api/cmd",
  screenshotEndpoint: "/api/screenshot",
  statusEndpoint: "/api/status",
  statusType: "minecraft-mod",
  httpServerName: "minecraft-mod-mcp-server",
} as const;

export const BUILD = {
  portableGitDirName: "PortableGit-2.45.2-64-bit",
} as const;

export const FABRIC = {
  defaultLoaderVersion: "0.16.14",
  defaultInstallerVersion: "0.11.2",
  mavenBaseUrl: "https://maven.fabricmc.net",
} as const;

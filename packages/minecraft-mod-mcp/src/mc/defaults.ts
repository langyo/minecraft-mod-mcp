export const LAUNCHER = {
  name: "MMML",
  nameGame: "MCP-Launcher",
  version: "0.1.0",
  versionType: "release",
  mcpServerName: "minecraft-mod-mcp",
} as const;

export const PLAYER = {
  defaultName: "Player",
  defaultUuid: "0",
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
  defaultLanguage: "zh-CN",
  defaultDownloadSource: "bmclapi",
  javaVersionFallback: 17,
  javaVersionThresholds: { mc117: 17, mc116: 16, mc113: 13 } as Record<string, number>,
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
    17: "eclipse_adoptium-17",
    21: "eclipse_adoptium-21",
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

export const SERVER = {
  rconPort: 25575,
  networkCompressionThreshold: 256,
  maxTickTime: 60000,
  maxPlayers: 20,
  viewDistance: 10,
  simulationDistance: 10,
  maxWorldSize: 29999984,
  connectHost: "localhost",
  defaultJavaVersion: 17,
  userAgent: "minecraft-mcp",
  bindAddress: "0.0.0.0",
  eulaFileName: "eula.txt",
  propertiesFileName: "server.properties",
  modsDirName: "mods",
} as const;

export const MOD = {
  cmdEndpoint: "/api/cmd",
  screenshotEndpoint: "/api/screenshot",
  statusEndpoint: "/api/status",
  statusType: "minecraft-mod",
  httpServerName: "minecraft-mod-mcp-server",
} as const;

export const FABRIC = {
  defaultLoaderVersion: "0.16.14",
} as const;

export const CACHE = {
  repo: "anomalyco/minecraft-mcp",
  githubProxyUrls: [
    "",
    "https://gh-proxy.com/",
    "https://ghfast.top/",
  ],
  assets: {
    "1.7.x": {
      tag: "cache-1.7x-gradle",
      file: "mc-17x-gradle-cache.zip",
      versionPrefixes: ["1.7.2", "1.7.10"],
    },
  },
} as const;

export type CacheEntry = (typeof CACHE)["assets"][keyof (typeof CACHE)["assets"]];

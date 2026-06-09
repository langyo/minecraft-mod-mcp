import { parseArgs } from "node:util";
import { startServer } from "./server.js";
import { isWindows } from "./runtime/detector.js";
import { loadVersionsData } from "./mc/versionsData.js";
import { getVersions, getVersion, getVersionForLoader, loaders, getFgEra, type Loader, DEFAULT_FABRIC_LOADER_VERSION } from "./mc/versions.js";
import { loadVersionMerged } from "./mc/versionJson.js";
import { buildLaunchCommand, ensureJavaForLaunch, type LaunchConfig } from "./mc/launch.js";
import { loadConfig, saveConfig, addAccount, selectedAccount, gameDirPath, javaExecPath, accountUuid, accountUsername, accountAccessToken, accountUserType, type Account } from "./mc/settings.js";
import { detectJavas } from "./mc/javaDetect.js";
import { ensureJavaInstalled } from "./mc/javaDownload.js";
import { startDeviceAuth, pollDeviceAuth, createOfflineUuid } from "./mc/auth.js";
import { fetchVersionManifest, fetchVersionJson, downloadVersion, listInstalledVersions, downloadLoaderVersion, ensureVersionInstalled } from "./mc/download.js";
import { versionsDir, classpathSeparator, modJarPath } from "./mc/platform.js";
import { findFreePort } from "./discovery/scanner.js";
import { spawn } from "node:child_process";
import { existsSync, mkdirSync, readdirSync, statSync, copyFileSync } from "node:fs";
import { gradleProxyEnv, setupGlobalProxy } from "./mc/proxy.js";
import { join, resolve } from "node:path";
import { GAME, MCP, PLAYER, SERVER, SERVER_TYPES, type ServerType } from "./mc/defaults.js";

const HELP = `minecraft-mod-mcp — Minecraft MCP Bridge + Launcher CLI

Usage:
  minecraft-mod-mcp                          Start MCP stdio server (for AI tools)
  minecraft-mod-mcp mcp [options]            Start MCP stdio server
  minecraft-mod-mcp launch <version> [opts]  Launch Minecraft client
  minecraft-mod-mcp server <version> [opts]  Launch standalone server
  minecraft-mod-mcp serve <version> [opts]   Install + launch server + client
  minecraft-mod-mcp list                     List supported MC versions
  minecraft-mod-mcp installed                List installed versions
  minecraft-mod-mcp install <version>        Download a MC version
  minecraft-mod-mcp auth login               Microsoft OAuth login
  minecraft-mod-mcp auth offline <name>      Create offline account
  minecraft-mod-mcp auth list                List accounts
  minecraft-mod-mcp auth select <uuid>       Select active account
  minecraft-mod-mcp auth remove <uuid>       Remove an account
  minecraft-mod-mcp java                     Detect installed Java versions
  minecraft-mod-mcp status                   Show connection status
  minecraft-mod-mcp test-matrix <version>    Test all valid server/client combos
  minecraft-mod-mcp tui                      Interactive TUI launcher
  minecraft-mod-mcp sdk <version> [opts]     Build mod SDK for a version

Common Launch Options (for launch/serve):
  --loader <forge|fabric|neoforge>  Mod loader (default: ${GAME.defaultLoader})
  --java <path>                     Java executable path
  --memory <mb>                     Max memory pool in MB
  --min-memory <mb>                 Min memory pool in MB
  --jvm-args <args>                 Extra JVM arguments (space-separated)
  --game-args <args>                Extra game arguments (space-separated)
  --fullscreen                      Launch in fullscreen
  --width <px> / --height <px>      Window dimensions
  --server <host>                   Auto-connect to server on launch
  --server-port <port>              Server port (default: ${GAME.defaultServerPort})
  --dry-run                         Print command without executing
  --mod-jar <path>                  Mod JAR to inject

Server Options (for server/serve):
  --type <vanilla|spigot|paper|...>  Server framework (default: vanilla)
  --server-memory <mb>              Server max memory (default: ${GAME.defaultServerMemoryMb})
  --server-min-memory <mb>          Server min memory in MB
  --server-game-args <args>         Extra server-side arguments
  --port <port>                     Server port (default: ${GAME.defaultServerPort})
  --motd <text>                     Server MOTD message
  --max-players <n>                 Max players (default: ${SERVER.maxPlayers})
  --gamemode <mode>                 Default gamemode (default: survival)
  --difficulty <diff>               Difficulty (default: easy)
  --online-mode                     Enable online authentication
  --pvp                             Enable PVP (default: true)
  --level-seed <seed>               World seed
  --level-name <name>               World name (default: world)
  --level-type <type>               World type (default: flat)
  --white-list                      Enable whitelist
  --view-distance <n>               View distance (default: ${SERVER.viewDistance})

SDK Options:
  --loader <forge|fabric|neoforge>  Mod loader (default: ${GAME.defaultLoader})
  --java <path>                     Java executable override
  --no-build                        Only ensure Java/dependencies, don't build

MCP Server Options:
  --no-discover              Don't scan for running Minecraft mod
  --discover-timeout <ms>    Timeout for mod discovery (default: ${MCP.discoverTimeoutMs})
  -h, --help                 Show this help
`;

async function main() {
  await setupGlobalProxy();

  const args = process.argv.slice(2);

  if (args.length === 0 || args[0] === "mcp") {
    await runMcp(args.slice(1));
    return;
  }

  const cmd = args[0];
  const rest = args.slice(1);

  switch (cmd) {
    case "launch": await runLaunch(rest); break;
    case "serve": await runServe(rest); break;
    case "server": await runServer(rest); break;
    case "test-matrix": await runTestMatrix(rest); break;
    case "list": await runList(); break;
    case "installed": await runInstalled(); break;
    case "install": await runInstall(rest); break;
    case "auth": await runAuth(rest); break;
    case "java": await runJava(); break;
    case "status": await runStatus(); break;
    case "tui": await runTui(); break;
    case "sdk": await runSdk(rest); break;
    case "-h":
    case "--help":
      console.log(HELP);
      break;
    default:
      console.error(`Unknown command: ${cmd}\n`);
      console.log(HELP);
      process.exit(1);
  }
}

async function runMcp(args: string[]) {
  const { values } = parseArgs({
    args,
    options: {
      "no-discover": { type: "boolean", default: false },
      "discover-timeout": { type: "string" },
      help: { type: "boolean", short: "h" },
    },
    strict: true,
  });

  if (values.help) {
    console.error(HELP);
    process.exit(0);
  }

  await startServer({
    autoDiscover: !values["no-discover"],
    discoverTimeout: values["discover-timeout"] ? parseInt(values["discover-timeout"], 10) : undefined,
  });
}

async function runLaunch(args: string[]) {
  if (args.length === 0 || args[0] === "-h" || args[0] === "--help") {
    console.log(`Usage: minecraft-mod-mcp launch <version> [options]

Options:
  --loader <forge|fabric|neoforge>  Mod loader (default: ${GAME.defaultLoader})
  --mc-dir <path>                   Game directory (default: isolated MCP dir)
  --java <path>                     Java executable path
  --memory <mb>                     Max memory in MB (default: ${GAME.defaultMaxMemoryMb})
  --min-memory <mb>                 Min memory in MB (default: ${GAME.defaultMinMemoryMb})
  --jvm-args <args>                 Extra JVM arguments (space-separated)
  --game-args <args>                Extra game arguments (space-separated)
  --fullscreen                      Launch in fullscreen mode
  --width <px>                      Window width (default: ${GAME.defaultWidth})
  --height <px>                     Window height (default: ${GAME.defaultHeight})
  --port <port>                     MCP port
  --server <host>                   Auto-connect to server on launch
  --server-port <port>              Server port (default: ${GAME.defaultServerPort})
  --dry-run                         Print command without executing
  --mod-jar <path>                  Path to mod JAR to inject`);
    return;
  }

  const { values, positionals } = parseArgs({
    args,
    options: {
      loader: { type: "string", default: GAME.defaultLoader },
      "mc-dir": { type: "string" },
      java: { type: "string" },
      memory: { type: "string", default: String(GAME.defaultMaxMemoryMb) },
      "min-memory": { type: "string" },
      "jvm-args": { type: "string" },
      "game-args": { type: "string" },
      fullscreen: { type: "boolean", default: false },
      width: { type: "string" },
      height: { type: "string" },
      port: { type: "string" },
      server: { type: "string" },
      "server-port": { type: "string", default: String(GAME.defaultServerPort) },
      "dry-run": { type: "boolean", default: false },
      "mod-jar": { type: "string" },
    },
    strict: false,
  });

  const versionArg = positionals[0];
  const loader = (values.loader ?? GAME.defaultLoader) as Loader;

  console.log(`Resolving version ${versionArg} (${loader})...`);
  const versionId = await ensureVersionInstalled(versionArg, loader, (msg) => {
    console.error(`  ${msg}`);
  });

  const vj = loadVersionMerged(versionId);
  const data = loadVersionsData();
  const config = loadConfig();
  const account = selectedAccount(config);

  const extraJvmParts: string[] = [];
  if (config.java_args) extraJvmParts.push(config.java_args);
  if (typeof values["jvm-args"] === "string") extraJvmParts.push(values["jvm-args"]);

  const launchConfig: LaunchConfig = {
    versionId,
    mcDir: typeof values["mc-dir"] === "string" ? values["mc-dir"] : gameDirPath(config),
    loader,
    modJar: typeof values["mod-jar"] === "string" ? values["mod-jar"] : modJarPath(versionArg, loader) ?? undefined,
    mcpPort: typeof values.port === "string" ? parseInt(values.port, 10) : config.mcp_port ?? await findFreePort(),
    dryRun: values["dry-run"] === true,
    maxMemoryMb: parseInt(typeof values.memory === "string" ? values.memory : String(config.max_memory_mb), 10),
    minMemoryMb: typeof values["min-memory"] === "string"
      ? parseInt(values["min-memory"] as string, 10)
      : config.min_memory_mb,
    extraJvmArgs: extraJvmParts.length > 0 ? extraJvmParts.join(" ") : undefined,
    extraGameArgs: buildExtraGameArgs(
      config.game_args,
      typeof values["game-args"] === "string" ? (values["game-args"] as string) : undefined,
      values.server,
      values["server-port"],
      versionArg,
    ),
    javaPath: typeof values.java === "string" ? values.java : javaExecPath(config) ?? undefined,
    playerName: account ? accountUsername(account) : PLAYER.defaultName,
    uuid: account ? accountUuid(account) : PLAYER.defaultUuid,
    accessToken: account ? accountAccessToken(account) : PLAYER.defaultAccessToken,
    userType: account ? accountUserType(account) : PLAYER.defaultUserType,
    width: typeof values.width === "string" ? parseInt(values.width, 10) : config.width,
    height: typeof values.height === "string" ? parseInt(values.height, 10) : config.height,
    fullscreen: values.fullscreen === true || config.fullscreen,
  };

  if (!launchConfig.javaPath) {
    launchConfig.javaPath = await ensureJavaForLaunch(launchConfig, vj, data, (msg) => {
      console.error(`  ${msg}`);
    });
  }

  const cmd = buildLaunchCommand(launchConfig, vj, data);

  if (launchConfig.dryRun) {
    console.log(`Java: ${cmd.java}`);
    console.log(`MainClass: ${cmd.mainClass}`);
    console.log(`Classpath (${cmd.classpath.split(classpathSeparator()).length} entries):`);
    for (const cp of cmd.classpath.split(classpathSeparator())) console.log(`  ${cp}`);
    console.log(`Args:`);
    for (const arg of cmd.args) console.log(`  ${arg}`);
    return;
  }

  const mcDir_ = launchConfig.mcDir!;
  if (!existsSync(mcDir_)) mkdirSync(mcDir_, { recursive: true });
  console.error(`Launching ${versionId} (${loader})...`);
  console.error(`  Java: ${cmd.java}`);
  console.error(`  MCP Port: ${launchConfig.mcpPort}`);
  console.error(`  Game Dir: ${mcDir_}`);
  console.error(`  Memory: ${launchConfig.maxMemoryMb}MB / ${launchConfig.minMemoryMb}MB`);
  if (launchConfig.fullscreen) console.error(`  Fullscreen: true`);
  else console.error(`  Resolution: ${launchConfig.width}x${launchConfig.height}`);

  const child = spawn(cmd.java, cmd.args, {
    cwd: mcDir_,
    stdio: "ignore",
    detached: true,
  });
  child.unref();

  child.on("error", (err) => {
    console.error(`Launch failed: ${err.message}`);
    process.exit(1);
  });

  console.error(`  PID: ${child.pid}`);
  console.error(`Launched successfully.`);
}

async function runList() {
  const data = loadVersionsData();
  const versions = getVersions(data);

  console.log("Supported Minecraft Versions:\n");
  console.log("  MC Version  | Java | Loaders                    | Version ID");
  console.log("  ------------|------|----------------------------|------------------------------------------");

  for (const v of versions) {
    const loaderList = loaders(v).join(", ");
    console.log(`  ${v.mc_version.padEnd(11)} | ${String(v.java).padEnd(4)} | ${loaderList.padEnd(26)} | ${v.version_id}`);
  }
}

async function runInstalled() {
  const installed = listInstalledVersions();
  if (installed.length === 0) {
    console.log("No Minecraft versions installed.");
    return;
  }
  console.log("Installed versions:\n");
  for (const v of installed) {
    console.log(`  ${v}`);
  }
}

async function runInstall(args: string[]) {
  if (args.length === 0) {
    console.error("Usage: minecraft-mod-mcp install <version> [options]");
    console.error("\nOptions:");
    console.error("  --loader <forge|fabric|neoforge>  Mod loader (default: ${GAME.defaultLoader})");
    console.error("\nUse 'minecraft-mod-mcp list' to see supported versions.");
    process.exit(1);
  }

  const { values, positionals } = parseArgs({
    args,
    options: {
      loader: { type: "string", default: GAME.defaultLoader },
    },
    strict: false,
  });

  const versionArg = positionals[0];
  const loader = (values.loader ?? GAME.defaultLoader) as Loader;
  const data = loadVersionsData();

  const vi = getVersion(data, versionArg);
  if (vi) {
    const mcVersion = vi.mc_version;
    console.log(`Resolved ${versionArg} (${loader})`);

    const baseVersionDir = join(versionsDir(), mcVersion);
    const baseJsonPath = join(baseVersionDir, `${mcVersion}.json`);
    if (!existsSync(baseJsonPath)) {
      console.log(`Fetching version manifest...`);
      const manifest = await fetchVersionManifest();

      const mv = manifest.versions.find((v) => v.id === mcVersion);
      if (!mv) {
        console.error(`Base MC version ${mcVersion} not found in manifest.`);
        process.exit(1);
      }

      console.log(`Downloading base version JSON for ${mcVersion}...`);
      const baseVj = await fetchVersionJson(mv.url);

      await downloadVersion(baseVj, (msg) => {
        console.error(`  ${msg}`);
      });
    } else {
      console.log(`Base MC ${mcVersion} already installed.`);
    }

    let loaderVersion: string | undefined;
    if (loader === "forge" && vi.forge) {
      loaderVersion = vi.forge;
    } else if (loader === "neoforge" && vi.neoforge) {
      loaderVersion = vi.neoforge;
    } else if (loader === "fabric" && vi.fabric_yarn) {
      loaderVersion = DEFAULT_FABRIC_LOADER_VERSION;
    }

    if (loaderVersion) {
      console.log(`\nInstalling ${loader} ${loaderVersion}...`);
      await downloadLoaderVersion(mcVersion, loader, loaderVersion, (msg) => {
        console.error(`  ${msg}`);
      });
    } else {
      console.log(`\nNote: ${loader} is not available for ${versionArg}.`);
      console.log(`Only base MC ${mcVersion} was installed.`);
    }

    const versionId = getVersionForLoader(data, versionArg, loader) ?? vi.version_id;
    console.log(`\nDone. Installed version: ${versionId}`);
  } else {
    console.log(`Fetching version manifest...`);
    const manifest = await fetchVersionManifest();

    const mv = manifest.versions.find((v) => v.id === versionArg);
    if (!mv) {
      console.error(`Version ${versionArg} not found in manifest.`);
      console.error(`Use 'minecraft-mod-mcp list' to see supported versions.`);
      process.exit(1);
    }

    console.log(`Downloading version JSON for ${mv.id}...`);
    const vj = await fetchVersionJson(mv.url);

    await downloadVersion(vj, (msg) => {
      console.error(`  ${msg}`);
    });

    console.log(`Done. Version ${mv.id} installed.`);
  }
}

async function runAuth(args: string[]) {
  if (args.length === 0) {
    console.log(`Usage: minecraft-mod-mcp auth <login|offline|list|select|remove>`);
    return;
  }

  const sub = args[0];
  const rest = args.slice(1);
  const config = loadConfig();

  switch (sub) {
    case "login": {
      console.log("Starting Microsoft authentication...");
      const info = await startDeviceAuth();
      console.log(`\n  To sign in, visit: ${info.verification_uri}`);
      console.log(`  Enter code: ${info.user_code}\n`);
      console.log("Waiting for authentication...");

      const profile = await pollDeviceAuth(info.device_code);
      const account: Account = {
        type: "microsoft",
        uuid: profile.uuid,
        username: profile.username,
        access_token: profile.access_token,
        refresh_token: profile.refresh_token,
        not_after: profile.expires_at,
      };
      addAccount(config, account);
      if (!config.selected_account) config.selected_account = profile.uuid;
      saveConfig(config);

      console.log(`Authenticated as ${profile.username} (${profile.uuid})`);
      break;
    }
    case "offline": {
      const username = rest[0];
      if (!username) {
        console.error("Usage: minecraft-mod-mcp auth offline <username>");
        process.exit(1);
      }
      const uuid = createOfflineUuid(username);
      const account: Account = { type: "offline", uuid, username };
      addAccount(config, account);
      if (!config.selected_account) config.selected_account = uuid;
      saveConfig(config);
      console.log(`Created offline account: ${username} (${uuid})`);
      break;
    }
    case "list": {
      if (config.accounts.length === 0) {
        console.log("No accounts configured.");
        return;
      }
      for (const a of config.accounts) {
        const sel = accountUuid(a) === config.selected_account ? " (selected)" : "";
        const type = a.type === "microsoft" ? "Microsoft" : "Offline";
        console.log(`  ${accountUsername(a)} (${accountUuid(a)}) [${type}]${sel}`);
      }
      break;
    }
    case "select": {
      const uuid = rest[0];
      if (!uuid) {
        console.error("Usage: minecraft-mod-mcp auth select <uuid>");
        process.exit(1);
      }
      const found = config.accounts.find((a) => accountUuid(a) === uuid);
      if (!found) {
        console.error(`Account not found: ${uuid}`);
        process.exit(1);
      }
      config.selected_account = uuid;
      saveConfig(config);
      console.log(`Selected account: ${accountUsername(found)}`);
      break;
    }
    case "remove": {
      const uuid = rest[0];
      if (!uuid) {
        console.error("Usage: minecraft-mod-mcp auth remove <uuid>");
        process.exit(1);
      }
      const oldLen = config.accounts.length;
      config.accounts = config.accounts.filter((a) => accountUuid(a) !== uuid);
      if (config.accounts.length === oldLen) {
        console.error(`Account not found: ${uuid}`);
        process.exit(1);
      }
      if (config.selected_account === uuid) {
        config.selected_account = config.accounts[0] ? accountUuid(config.accounts[0]) : undefined;
      }
      saveConfig(config);
      console.log(`Account removed.`);
      break;
    }
    default:
      console.error(`Unknown auth subcommand: ${sub}`);
  }
}

async function runJava() {
  const javas = detectJavas();
  if (javas.length === 0) {
    console.log("No Java installations found.");
    return;
  }
  console.log("Detected Java installations:\n");
  for (const j of javas) {
    console.log(`  Java ${j.version} (${j.vendor}) ${j.isJdk ? "[JDK]" : "[JRE]"}`);
    console.log(`    ${j.path}`);
  }
}

async function runStatus() {
  const { findMod } = await import("./discovery/scanner.js");
  const { PORT_START, PORT_END } = await import("./consts.js");

  const status = await findMod(PORT_START, PORT_END);
  if (status) {
    console.log(`Minecraft mod connected:`);
    console.log(`  Version: ${status.version}`);
    console.log(`  Loader:  ${status.loader}`);
    console.log(`  PID:     ${status.pid}`);
    console.log(`  Port:    ${status.port}`);
    console.log(`  Uptime:  ${Math.floor(status.uptime)}s`);
  } else {
    console.log("No Minecraft mod detected.");
  }
}

async function runSdk(args: string[]) {
  if (args.length === 0 || args[0] === "-h" || args[0] === "--help") {
    console.log(`Usage: minecraft-mod-mcp sdk <version> [options]

Prepare mod build environment and build.

Options:
  --loader <forge|fabric|neoforge>  Mod loader (default: ${GAME.defaultLoader})
  --java <path>                     Java executable override
  --no-build                        Only ensure Java, don't run gradle build
`);
    return;
  }

  const { values, positionals } = parseArgs({
    args,
    allowPositionals: true,
    options: {
      loader: { type: "string", default: GAME.defaultLoader },
      java: { type: "string" },
      "no-build": { type: "boolean", default: false },
    },
    strict: true,
  });

  const versionArg = positionals[0] ?? "";
  if (!versionArg) {
    console.error("Version is required.");
    process.exit(1);
  }
  const loader = (values.loader ?? GAME.defaultLoader) as Loader;

  const data = loadVersionsData();
  const info = getVersion(data, versionArg);
  if (!info) {
    console.error(`Unknown version: ${versionArg}`);
    console.error(`Use 'minecraft-mod-mcp list' to see supported versions.`);
    process.exit(1);
  }

  const availableLoaders = loaders(info);
  if (!availableLoaders.includes(loader)) {
    console.error(`Loader '${loader}' not available for ${versionArg}. Available: ${availableLoaders.join(", ")}`);
    process.exit(1);
  }

  const modProjectDir = resolve("packages", "mods", versionArg, loader);
  if (!existsSync(modProjectDir)) {
    console.error(`Mod project directory not found: ${modProjectDir}`);
    console.error(`Make sure you are running from the repository root.`);
    process.exit(1);
  }

  const gradlew = isWindows() ? "gradlew.bat" : "gradlew";
  const gradlewPath = join(modProjectDir, gradlew);
  if (!existsSync(gradlewPath)) {
    console.error(`Gradle wrapper not found: ${gradlewPath}`);
    process.exit(1);
  }

  console.log(`=== SDK: ${versionArg} (${loader}) ===\n`);
  console.log(`Project dir: ${modProjectDir}`);

  let javaVersion = info.java;
  const era = getFgEra(data, info.fg_era);
  if (era && (loader === "forge" || loader === "neoforge")) {
    javaVersion = era.java;
  }
  if (loader === "fabric") {
    javaVersion = Math.max(javaVersion, 17);
  }
  let javaExe: string;

  if (typeof values.java === "string") {
    javaExe = values.java;
    console.log(`Using specified Java: ${javaExe}`);
  } else {
    console.log(`\n[1/2] Ensuring Java ${javaVersion}...`);
    const home = await ensureJavaInstalled(javaVersion, (msg) => console.error(`  ${msg}`));
    javaExe = isWindows() ? join(home, "bin", "java.exe") : join(home, "bin", "java");
    console.log(`  Java ${javaVersion}: ${home}`);
  }

  if (values["no-build"]) {
    console.log(`\n--no-build specified, skipping build.`);
    console.log(`\nReady. Run: cd ${modProjectDir} && ${gradlew} build`);
    return;
  }

  console.log(`\n[2/2] Building mod...`);
  console.log(`  Running: ${gradlew} build`);

  const buildResult = await runGradle(modProjectDir, gradlew, ["build"], javaExe);

  if (buildResult.code !== 0) {
    console.error(`\nBuild FAILED (exit code ${buildResult.code}).`);
    console.error(`\nstdout:\n${buildResult.stdout}`);
    console.error(`\nstderr:\n${buildResult.stderr}`);
    process.exit(1);
  }

  const jars = findJars(modProjectDir);
  if (jars.length > 0) {
    console.log(`\nBuild succeeded! Output:`);
    for (const j of jars) {
      const sizeKB = Math.round(j.size / 1024);
      console.log(`  ${j.path} (${sizeKB} KB)`);
    }
  } else {
    console.log(`\nBuild succeeded (no JAR found in build/libs/).`);
  }

  console.log(`\nProject ready: cd ${modProjectDir} && ${gradlew} build`);
}

async function runTui() {
  console.error("TUI mode is not yet implemented. Use CLI subcommands instead.");
  console.error("Run `minecraft-mod-mcp --help` for available commands.");
  process.exit(1);
}

function mcVersionGte(version: string, target: string): boolean {
  const parse = (v: string) => v.split(".").map(Number);
  const a = parse(version), b = parse(target);
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    const x = a[i] ?? 0, y = b[i] ?? 0;
    if (x > y) return true;
    if (x < y) return false;
  }
  return true;
}

function serverConnectArgs(host: string, port: number | string, mcVersion: string): string {
  const portStr = typeof port === "string" ? port : String(port);
  if (mcVersionGte(mcVersion, "1.20.5")) {
    return `--quickPlayMultiplayer ${host}:${portStr}`;
  }
  return `--server ${host} --port ${portStr}`;
}

function buildExtraGameArgs(
  base: string | undefined,
  extraGameArgs?: string,
  server?: string | boolean,
  serverPort?: string | boolean,
  mcVersion?: string,
): string | undefined {
  const parts: string[] = [];
  if (base) parts.push(base);
  if (extraGameArgs) parts.push(extraGameArgs);
  if (typeof server === "string") {
    const port = typeof serverPort === "string" ? serverPort : String(GAME.defaultServerPort);
    parts.push(serverConnectArgs(server, port, mcVersion ?? "1.21.11"));
  }
  return parts.length > 0 ? parts.join(" ") : undefined;
}

async function runServer(args: string[]) {
  if (args.length === 0 || args[0] === "-h" || args[0] === "--help") {
    console.log(`Usage: minecraft-mod-mcp server <version> [options]

Launch a standalone Minecraft server.

Server Framework:
  --type <vanilla|spigot|craftbukkit|paper|forge|fabric|neoforge>  Server framework (default: ${SERVER.defaultType})
  --loader <forge|fabric|neoforge>  Mod loader (for modded servers)

Server Configuration:
  --port <port>                     Server port (default: ${GAME.defaultServerPort})
  --motd <text>                     Server MOTD message
  --max-players <n>                 Max players (default: ${SERVER.maxPlayers})
  --gamemode <mode>                 Default gamemode (default: survival)
  --difficulty <diff>               Difficulty (default: easy)
  --online-mode                     Enable online authentication
  --pvp                             Enable PVP (default: true)
  --level-seed <seed>               World seed
  --level-name <name>               World name (default: world)
  --level-type <type>               World type (default: flat)
  --white-list                      Enable whitelist
  --view-distance <n>               View distance (default: ${SERVER.viewDistance})

Resources:
  --java <path>                     Java executable path
  --memory <mb>                     Max memory in MB (default: ${GAME.defaultServerMemoryMb})
  --min-memory <mb>                 Min memory in MB
  --jvm-args <args>                 Extra JVM arguments (space-separated)
  --game-args <args>                Extra server arguments (space-separated)
  --dry-run                         Print command without executing
  --mod-jar <path>                  Mod JAR to copy into server mods/`);
    return;
  }

  const { values, positionals } = parseArgs({
    args,
    options: {
      type: { type: "string", default: SERVER.defaultType },
      loader: { type: "string", default: GAME.defaultLoader },
      java: { type: "string" },
      memory: { type: "string", default: String(GAME.defaultServerMemoryMb) },
      "min-memory": { type: "string" },
      "jvm-args": { type: "string" },
      "game-args": { type: "string" },
      port: { type: "string", default: String(GAME.defaultServerPort) },
      motd: { type: "string" },
      "max-players": { type: "string" },
      gamemode: { type: "string" },
      difficulty: { type: "string" },
      "online-mode": { type: "boolean", default: false },
      pvp: { type: "boolean", default: true },
      "level-seed": { type: "string" },
      "level-name": { type: "string" },
      "level-type": { type: "string" },
      "white-list": { type: "boolean", default: false },
      "view-distance": { type: "string" },
      "dry-run": { type: "boolean", default: false },
      "mod-jar": { type: "string" },
    },
    strict: false,
  });

  const versionArg = positionals[0];
  const loader = (values.loader ?? GAME.defaultLoader) as Loader;
  const serverType = (values.type ?? SERVER.defaultType) as ServerType;
  if (!SERVER_TYPES.includes(serverType)) {
    console.error(`Unknown server type: ${serverType}. Available: ${SERVER_TYPES.join(", ")}`);
    process.exit(1);
  }

  const { installServer: installServerFn, launchServer: launchServerFn } = await import("./mc/server.js");
  const { copyFileSync } = await import("node:fs");

  console.log(`=== Setting up server ${versionArg} (${serverType}) ===\n`);

  const serverProps = buildServerProperties(values);
  const serverPort = parseInt(values.port as string, 10);

  console.log(`[1/2] Installing server...`);
  const setup = await installServerFn(versionArg, loader, (msg) => console.error(`  ${msg}`), serverType, serverProps);
  console.log(`  Server dir: ${setup.serverDir}`);
  console.log(`  Server JAR: ${setup.jarPath}`);
  console.log(`  Server type: ${setup.serverType}`);

  if (typeof values["mod-jar"] === "string") {
    const modJarSrc = values["mod-jar"] as string;
    if (existsSync(modJarSrc)) {
      const modsDir = join(setup.serverDir, "mods");
      if (!existsSync(modsDir)) mkdirSync(modsDir, { recursive: true });
      const dest = join(modsDir, modJarSrc.split(/[/\\]/).pop()!);
      copyFileSync(modJarSrc, dest);
      console.log(`  Mod JAR copied to: ${dest}`);
    }
  }

  const maxMem = parseInt(values.memory as string, 10);
  const minMem = typeof values["min-memory"] === "string" ? parseInt(values["min-memory"] as string, 10) : undefined;

  if (values["dry-run"]) {
    const parts = [`java`, `-Xmx${maxMem}m`];
    if (minMem) parts.push(`-Xms${minMem}m`);
    parts.push(`-Dmcp.port=0`);
    if (typeof values["jvm-args"] === "string") parts.push(...(values["jvm-args"] as string).split(/\s+/).filter(Boolean));
    parts.push(`-jar`, setup.jarPath, `--nogui`);
    if (typeof values["game-args"] === "string") parts.push(...(values["game-args"] as string).split(/\s+/).filter(Boolean));
    console.log(`\n[dry-run] Command:`);
    console.log(`  ${parts.join(" ")}`);
    console.log(`\n[dry-run] Server properties:`);
    console.log(`  server-port=${serverPort}`);
    if (values.motd) console.log(`  motd=${values.motd}`);
    if (values["max-players"]) console.log(`  max-players=${values["max-players"]}`);
    if (values.gamemode) console.log(`  gamemode=${values.gamemode}`);
    if (values.difficulty) console.log(`  difficulty=${values.difficulty}`);
    console.log(`  online-mode=${values["online-mode"]}`);
    return;
  }

  console.log(`\n[2/2] Starting server...`);
  const srv = launchServerFn(setup, {
    javaPath: typeof values.java === "string" ? values.java : undefined,
    javaVersion: setup.javaVersion,
    maxMemoryMb: maxMem,
    minMemoryMb: minMem,
    extraJvmArgs: typeof values["jvm-args"] === "string" ? (values["jvm-args"] as string) : undefined,
    extraGameArgs: typeof values["game-args"] === "string" ? (values["game-args"] as string) : undefined,
    port: serverPort,
  });

  srv.process.on("error", (err) => {
    console.error(`Server launch failed: ${err.message}`);
    process.exit(1);
  });

  console.error(`  Server PID: ${srv.process.pid}`);
  console.error(`  Port: ${srv.port}`);
  console.error(`  Type: ${setup.serverType}`);
  console.error(`  Dir: ${srv.dir}`);
  console.error(`Server launched successfully.`);
}

function buildServerProperties(values: Record<string, unknown>): import("./mc/server.js").ServerProperties {
  const props: Record<string, unknown> = {};
  if (values.port) props.serverPort = parseInt(values.port as string, 10);
  if (values.motd) props.motd = values.motd as string;
  if (values["max-players"]) props.maxPlayers = parseInt(values["max-players"] as string, 10);
  if (values.gamemode) props.gamemode = values.gamemode as string;
  if (values.difficulty) props.difficulty = values.difficulty as string;
  if (values["online-mode"] !== undefined) props.onlineMode = values["online-mode"] === true;
  if (values.pvp !== undefined) props.pvp = values.pvp !== false;
  if (values["level-seed"]) props.levelSeed = values["level-seed"] as string;
  if (values["level-name"]) props.levelName = values["level-name"] as string;
  if (values["level-type"]) props.levelType = values["level-type"] as string;
  if (values["white-list"] !== undefined) props.whiteList = values["white-list"] === true;
  if (values["view-distance"]) props.viewDistance = parseInt(values["view-distance"] as string, 10);
  return props as import("./mc/server.js").ServerProperties;
}

async function runTestMatrix(args: string[]) {
  if (args.length === 0 || args[0] === "-h" || args[0] === "--help") {
    console.log(`Usage: minecraft-mod-mcp test-matrix <version> [options]

Test all valid client/server combinations for a Minecraft version.

Options:
  --server-types <types>   Comma-separated server types to test (default: all valid)
  --client-loaders <types> Comma-separated client loaders (default: all available)
  --timeout <seconds>      Timeout per server startup (default: 120)
  --memory <mb>            Server memory in MB (default: ${GAME.defaultServerMemoryMb})
  --json                   Output results as JSON (for CI)
  --list                   Only list valid pairs, don't run tests
  --skip-install           Skip server install, assume already installed`);
    return;
  }

  const { values, positionals } = parseArgs({
    args,
    options: {
      "server-types": { type: "string" },
      "client-loaders": { type: "string" },
      timeout: { type: "string", default: "120" },
      memory: { type: "string", default: String(GAME.defaultServerMemoryMb) },
      json: { type: "boolean", default: false },
      list: { type: "boolean", default: false },
      "skip-install": { type: "boolean", default: false },
    },
    strict: false,
  });

  const versionArg = positionals[0];
  const { getCompatPairs, installServer: installServerFn, launchServer: launchServerFn, waitForServer } = await import("./mc/server.js");

  const allPairs = getCompatPairs(versionArg);
  if (allPairs.length === 0) {
    console.error(`No valid pairs for ${versionArg}. Check 'minecraft-mod-mcp list' for available versions.`);
    process.exit(1);
  }

  const filterST = values["server-types"] ? (values["server-types"] as string).split(",") : null;
  const filterCL = values["client-loaders"] ? (values["client-loaders"] as string).split(",") : null;
  const pairs = allPairs.filter(p => p.valid && (!filterST || filterST.includes(p.serverType)) && (!filterCL || filterCL.includes(p.clientLoader)));

  if (values.list) {
    console.log(`\nValid combinations for ${versionArg} (${pairs.length} pairs):\n`);
    console.log("  Client      | Server       | Reason");
    console.log("  -------------|------------- |-------------------------------------------");
    for (const p of pairs) {
      console.log(`  ${p.clientLoader.padEnd(12)} | ${p.serverType.padEnd(12)} | ${p.reason}`);
    }
    console.log(`\n  Invalid pairs (${allPairs.filter(p => !p.valid).length}):`);
    for (const p of allPairs.filter(p => !p.valid)) {
      console.log(`  ${p.clientLoader} + ${p.serverType}: ${p.reason}`);
    }
    return;
  }

  const timeoutMs = parseInt(values.timeout as string, 10) * 1000;
  const mem = parseInt(values.memory as string, 10);
  const jsonOutput = values.json === true;
  const results: Array<{
    clientLoader: string;
    serverType: string;
    status: "pass" | "fail" | "skip";
    duration_ms: number;
    error?: string;
    serverPid?: number;
    port?: number;
  }> = [];

  if (!jsonOutput) {
    console.log(`\n=== Test Matrix: ${versionArg} (${pairs.length} valid pairs) ===\n`);
  }

  let passCount = 0;
  let failCount = 0;

  for (let i = 0; i < pairs.length; i++) {
    const pair = pairs[i];
    const tag = `[${i + 1}/${pairs.length}] ${pair.clientLoader} → ${pair.serverType}`;
    if (!jsonOutput) console.log(`\n${tag}`);

    const start = Date.now();
    try {
      const loader = pair.clientLoader;
      let setup: import("./mc/server.js").ServerSetup;

      if (!values["skip-install"]) {
        if (!jsonOutput) console.log(`  Installing ${pair.serverType} server...`);
        setup = await installServerFn(versionArg, loader, (msg) => { if (!jsonOutput) console.error(`    ${msg}`); }, pair.serverType);
      } else {
        const data = loadVersionsData();
        const vi = getVersion(data, versionArg);
        if (!vi) throw new Error(`Version ${versionArg} not found`);
        const versionId = getVersionForLoader(data, versionArg, loader) ?? vi.version_id;
        const { serverDir: sDirFn } = await import("./mc/settings.js");
        const sDir = sDirFn(`${versionId}-${pair.serverType}`);
        setup = {
          serverDir: sDir,
          jarPath: join(sDir, `server-${vi.mc_version}.jar`),
          versionId,
          mcVersion: vi.mc_version,
          serverType: pair.serverType,
          javaVersion: vi.java,
        };
      }

      if (!jsonOutput) console.log(`  Starting server...`);
      const srv = launchServerFn(setup, { maxMemoryMb: mem, port: GAME.defaultServerPort + i });

      if (!jsonOutput) console.log(`  Waiting for server (timeout ${timeoutMs / 1000}s)...`);
      const ok = await waitForServer(setup.serverDir, timeoutMs);
      const duration = Date.now() - start;

      if (ok) {
        passCount++;
        if (!jsonOutput) console.log(`  PASS (${(duration / 1000).toFixed(1)}s)`);
        results.push({ clientLoader: pair.clientLoader, serverType: pair.serverType, status: "pass", duration_ms: duration, serverPid: srv.process.pid, port: srv.port });
      } else {
        failCount++;
        if (!jsonOutput) console.log(`  FAIL: server did not start within timeout`);
        results.push({ clientLoader: pair.clientLoader, serverType: pair.serverType, status: "fail", duration_ms: duration, error: "Server did not start within timeout" });
      }

      try { srv.process.kill(); } catch {}
      await new Promise(r => setTimeout(r, 3000));
    } catch (err: any) {
      failCount++;
      const duration = Date.now() - start;
      if (!jsonOutput) console.log(`  FAIL: ${err.message}`);
      results.push({ clientLoader: pair.clientLoader, serverType: pair.serverType, status: "fail", duration_ms: duration, error: err.message });
    }
  }

  if (jsonOutput) {
    console.log(JSON.stringify({ version: versionArg, total: pairs.length, passed: passCount, failed: failCount, results }, null, 2));
  } else {
    console.log(`\n=== Results: ${passCount} passed, ${failCount} failed, ${pairs.length} total ===`);
  }

  if (failCount > 0) process.exit(1);
}

async function runServe(args: string[]) {
  if (args.length === 0 || args[0] === "-h" || args[0] === "--help") {
    console.log(`Usage: minecraft-mod-mcp serve <version> [options]

One-command: install server + install client + launch both.

Server Framework:
  --type <vanilla|spigot|craftbukkit|paper|forge|fabric|neoforge>  Server framework (default: ${SERVER.defaultType})

Server Configuration:
  --port <port>                     Server port (default: ${GAME.defaultServerPort})
  --motd <text>                     Server MOTD message
  --max-players <n>                 Max players (default: ${SERVER.maxPlayers})
  --gamemode <mode>                 Default gamemode (default: survival)
  --difficulty <diff>               Difficulty (default: easy)
  --online-mode                     Enable online authentication
  --level-seed <seed>               World seed
  --level-name <name>               World name (default: world)
  --level-type <type>               World type (default: flat)
  --view-distance <n>               View distance (default: ${SERVER.viewDistance})

Client Options:
  --loader <forge|fabric|neoforge>  Mod loader (default: ${GAME.defaultLoader})
  --java <path>                     Java executable path
  --memory <mb>                     Client max memory (default: ${GAME.defaultMaxMemoryMb})
  --min-memory <mb>                 Client min memory in MB
  --server-memory <mb>              Server max memory (default: ${GAME.defaultServerMemoryMb})
  --server-min-memory <mb>          Server min memory in MB
  --jvm-args <args>                 Extra JVM args for both (space-separated)
  --game-args <args>                Extra game args for client (space-separated)
  --server-game-args <args>         Extra args for server (space-separated)
  --fullscreen                      Launch client in fullscreen
  --width <px>                      Client window width
  --height <px>                     Client window height
  --dry-run                         Show plan without executing
  --mod-jar <path>                  Mod JAR to inject into both sides`);
    return;
  }

  const { values, positionals } = parseArgs({
    args,
    options: {
      type: { type: "string", default: SERVER.defaultType },
      loader: { type: "string", default: GAME.defaultLoader },
      java: { type: "string" },
      memory: { type: "string", default: String(GAME.defaultMaxMemoryMb) },
      "min-memory": { type: "string" },
      "server-memory": { type: "string", default: String(GAME.defaultServerMemoryMb) },
      "server-min-memory": { type: "string" },
      "jvm-args": { type: "string" },
      "game-args": { type: "string" },
      "server-game-args": { type: "string" },
      fullscreen: { type: "boolean", default: false },
      width: { type: "string" },
      height: { type: "string" },
      port: { type: "string", default: String(GAME.defaultServerPort) },
      motd: { type: "string" },
      "max-players": { type: "string" },
      gamemode: { type: "string" },
      difficulty: { type: "string" },
      "online-mode": { type: "boolean", default: false },
      "level-seed": { type: "string" },
      "level-name": { type: "string" },
      "level-type": { type: "string" },
      "view-distance": { type: "string" },
      "dry-run": { type: "boolean", default: false },
      "mod-jar": { type: "string" },
    },
    strict: false,
  });

  const versionArg = positionals[0];
  const loader = (values.loader ?? GAME.defaultLoader) as Loader;
  const serverType = (values.type ?? SERVER.defaultType) as ServerType;
  const { installServer, launchServer } = await import("./mc/server.js");
  await import("./mc/settings.js");

  console.log(`=== Setting up ${versionArg} (${serverType}) ===\n`);

  const serverProps = buildServerProperties(values);
  const serverPort = parseInt(values.port as string, 10);

  console.log(`[1/3] Installing server (${serverType})...`);
  const setup = await installServer(versionArg, loader, (msg) => console.error(`  ${msg}`), serverType, serverProps);
  console.log(`  Server dir: ${setup.serverDir}`);
  console.log(`  Server type: ${setup.serverType}`);

  if (values["dry-run"]) {
    const serverMem = values["server-memory"] as string;
    const serverMinMem = values["server-min-memory"] as string;
    const dryCmd = [`java`, `-Xmx${serverMem}m`];
    if (serverMinMem) dryCmd.push(`-Xms${serverMinMem}m`);
    dryCmd.push(`-Dmcp.port=0`, `-jar`, setup.jarPath, `--nogui`);
    if (typeof values["server-game-args"] === "string") dryCmd.push(...(values["server-game-args"] as string).split(/\s+/).filter(Boolean));
    console.log(`\n[dry-run] Server command:`);
    console.log(`  ${dryCmd.join(" ")}`);
    console.log(`\n[dry-run] Server properties:`);
    console.log(`  server-port=${serverPort}`);
    if (values.motd) console.log(`  motd=${values.motd}`);
    if (values.gamemode) console.log(`  gamemode=${values.gamemode}`);
    console.log(`[dry-run] Would launch client connecting to ${SERVER.connectHost}:${serverPort}`);
    return;
  }

  const autoModJar = typeof values["mod-jar"] === "string" ? (values["mod-jar"] as string) : modJarPath(versionArg, loader);
  if (autoModJar && existsSync(autoModJar)) {
    const serverModsDir = join(setup.serverDir, SERVER.modsDirName);
    if (!existsSync(serverModsDir)) mkdirSync(serverModsDir, { recursive: true });
    const dest = join(serverModsDir, autoModJar.split(/[/\\]/).pop()!);
    copyFileSync(autoModJar, dest);
    console.log(`  Deployed mod JAR to server: ${dest}`);
  }

  console.log(`\n[2/3] Starting server...`);
  const srv = launchServer(setup, {
    javaPath: typeof values.java === "string" ? values.java : undefined,
    javaVersion: setup.javaVersion,
    maxMemoryMb: parseInt(values["server-memory"] as string, 10),
    minMemoryMb: typeof values["server-min-memory"] === "string" ? parseInt(values["server-min-memory"] as string, 10) : undefined,
    extraJvmArgs: typeof values["jvm-args"] === "string" ? (values["jvm-args"] as string) : undefined,
    extraGameArgs: typeof values["server-game-args"] === "string" ? (values["server-game-args"] as string) : undefined,
    port: serverPort,
  });
  console.log(`  Server PID: ${srv.process.pid}, port: ${srv.port}`);
  console.log(`  Waiting for server to start...`);
  const { waitForServer } = await import("./mc/server.js");
  await waitForServer(setup.serverDir);

  console.log(`\n[3/3] Launching client (auto-connect to localhost:${srv.port})...`);
  const launchArgs = [
    versionArg,
    "--loader", loader,
    "--server", "localhost",
    "--server-port", String(srv.port),
    "--memory", String(values.memory),
  ];
  if (typeof values.java === "string") launchArgs.push("--java", values.java);
  if (autoModJar) launchArgs.push("--mod-jar", autoModJar);
  if (values.fullscreen) launchArgs.push("--fullscreen");
  if (typeof values.width === "string") launchArgs.push("--width", values.width);
  if (typeof values.height === "string") launchArgs.push("--height", values.height);
  if (typeof values["min-memory"] === "string") launchArgs.push("--min-memory", values["min-memory"]);
  if (typeof values["jvm-args"] === "string") launchArgs.push("--jvm-args", values["jvm-args"]);
  if (typeof values["game-args"] === "string") launchArgs.push("--game-args", values["game-args"]);

  await runLaunch(launchArgs);
}

main().catch((err) => {
  console.error("Fatal:", err);
  process.exit(1);
});

function runGradle(
  cwd: string,
  gradlew: string,
  args: string[],
  javaExe: string,
): Promise<{ code: number; stdout: string; stderr: string }> {
  return new Promise((resolve_) => {
    const proxyEnv = gradleProxyEnv();
    const env = { ...process.env, ...proxyEnv, JAVA_HOME: resolve(javaExe, "..", "..") };
    const proc = spawn(join(cwd, gradlew), args, {
      cwd,
      env,
      stdio: ["ignore", "pipe", "pipe"],
      shell: isWindows(),
    });

    const stdoutChunks: Buffer[] = [];
    const stderrChunks: Buffer[] = [];
    proc.stdout?.on("data", (c: Buffer) => stdoutChunks.push(c));
    proc.stderr?.on("data", (c: Buffer) => {
      stderrChunks.push(c);
      process.stderr.write(c);
    });

    proc.on("close", (code) => {
      resolve_({
        code: code ?? 1,
        stdout: Buffer.concat(stdoutChunks).toString("utf-8"),
        stderr: Buffer.concat(stderrChunks).toString("utf-8"),
      });
    });
  });
}

function findJars(projectDir: string): { path: string; size: number }[] {
  const libsDir = join(projectDir, "build", "libs");
  if (!existsSync(libsDir)) return [];
  const results: { path: string; size: number }[] = [];
  try {
    for (const f of readdirSync(libsDir)) {
      if (f.endsWith(".jar")) {
        const full = join(libsDir, f);
        results.push({ path: full, size: statSync(full).size });
      }
    }
  } catch {}
  return results;
}

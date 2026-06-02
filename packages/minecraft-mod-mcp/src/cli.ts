import { parseArgs } from "node:util";
import { startServer } from "./server.js";
import { loadVersionsData } from "./mc/versions-data.js";
import { getVersions, getVersion, getVersionById, getVersionForLoader, loaders, type Loader, DEFAULT_FABRIC_LOADER_VERSION } from "./mc/versions.js";
import { loadVersion } from "./mc/version-json.js";
import { buildLaunchCommand, type LaunchConfig } from "./mc/launch.js";
import { loadConfig, saveConfig, addAccount, removeAccount, selectedAccount, gameDirPath, javaExecPath, accountUuid, accountUsername, accountAccessToken, accountUserType, defaultConfig, type Account } from "./mc/settings.js";
import { detectJavas } from "./mc/java-detect.js";
import { startDeviceAuth, pollDeviceAuth, createOfflineUuid } from "./mc/auth.js";
import { fetchVersionManifest, fetchVersionJson, downloadVersion, listInstalledVersions, downloadLoaderVersion } from "./mc/download.js";
import { mcDir, versionsDir, classpathSeparator, findJavaForVersion } from "./mc/platform.js";
import { findFreePort } from "./discovery/scanner.js";
import { PORT_START, PORT_END } from "./consts.js";
import { spawn } from "node:child_process";
import { existsSync } from "node:fs";
import { join } from "node:path";

const HELP = `minecraft-mod-mcp — Minecraft MCP Bridge + Launcher CLI

Usage:
  minecraft-mod-mcp                          Start MCP stdio server (for AI tools)
  minecraft-mod-mcp mcp [options]            Start MCP stdio server
  minecraft-mod-mcp launch <version> [opts]  Launch Minecraft
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
  minecraft-mod-mcp tui                      Interactive TUI launcher

MCP Server Options:
  --no-discover              Don't scan for running Minecraft mod
  --discover-timeout <ms>    Timeout for mod discovery (default: 300000)
  -h, --help                 Show this help
`;

async function main() {
  const args = process.argv.slice(2);

  if (args.length === 0 || args[0] === "mcp") {
    await runMcp(args.slice(1));
    return;
  }

  const cmd = args[0];
  const rest = args.slice(1);

  switch (cmd) {
    case "launch": await runLaunch(rest); break;
    case "list": await runList(); break;
    case "installed": await runInstalled(); break;
    case "install": await runInstall(rest); break;
    case "auth": await runAuth(rest); break;
    case "java": await runJava(); break;
    case "status": await runStatus(); break;
    case "tui": await runTui(); break;
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
  --loader <forge|fabric|neoforge>  Mod loader (default: forge)
  --mc-dir <path>                   Minecraft directory
  --java <path>                     Java executable path
  --memory <mb>                     Max memory in MB (default: 2048)
  --port <port>                     MCP port
  --dry-run                         Print command without executing
  --mod-jar <path>                  Path to mod JAR to inject`);
    return;
  }

  const { values, positionals } = parseArgs({
    args,
    options: {
      loader: { type: "string", default: "forge" },
      "mc-dir": { type: "string" },
      java: { type: "string" },
      memory: { type: "string", default: "2048" },
      port: { type: "string" },
      "dry-run": { type: "boolean", default: false },
      "mod-jar": { type: "string" },
    },
    strict: false,
  });

  const versionArg = positionals[0];
  const loader = (values.loader ?? "forge") as Loader;
  const data = loadVersionsData();

  let versionId: string | undefined;
  const vi = getVersion(data, versionArg);
  if (vi) {
    versionId = getVersionForLoader(data, versionArg, loader) ?? undefined;
    if (!versionId) {
      console.error(`${loader} is not available for ${versionArg}`);
      process.exit(1);
    }
  } else {
    versionId = versionArg;
  }

  if (!versionId) {
    console.error(`Unknown version: ${versionArg}`);
    process.exit(1);
  }

  const vj = loadVersion(versionId);
  const config = loadConfig();
  const account = selectedAccount(config);

  const launchConfig: LaunchConfig = {
    versionId,
    mcDir: typeof values["mc-dir"] === "string" ? values["mc-dir"] : gameDirPath(config),
    loader,
    modJar: typeof values["mod-jar"] === "string" ? values["mod-jar"] : undefined,
    mcpPort: typeof values.port === "string" ? parseInt(values.port, 10) : config.mcp_port ?? await findFreePort(),
    dryRun: values["dry-run"] === true,
    maxMemoryMb: parseInt(typeof values.memory === "string" ? values.memory : String(config.max_memory_mb), 10),
    minMemoryMb: config.min_memory_mb,
    extraJvmArgs: config.java_args,
    extraGameArgs: config.game_args,
    javaPath: typeof values.java === "string" ? values.java : javaExecPath(config) ?? undefined,
    playerName: account ? accountUsername(account) : "Player",
    uuid: account ? accountUuid(account) : "0",
    accessToken: account ? accountAccessToken(account) : "0",
    userType: account ? accountUserType(account) : "legacy",
  };

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
  console.error(`Launching ${versionId} (${loader})...`);
  console.error(`  Java: ${cmd.java}`);
  console.error(`  MCP Port: ${launchConfig.mcpPort}`);
  console.error(`  Game Dir: ${mcDir_}`);

  const child = spawn(cmd.java, cmd.args, {
    cwd: mcDir_,
    stdio: "ignore",
    detached: process.platform !== "win32",
  });

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
    console.error("  --loader <forge|fabric|neoforge>  Mod loader (default: forge)");
    console.error("\nUse 'minecraft-mod-mcp list' to see supported versions.");
    process.exit(1);
  }

  const { values, positionals } = parseArgs({
    args,
    options: {
      loader: { type: "string", default: "forge" },
    },
    strict: false,
  });

  const versionArg = positionals[0];
  const loader = (values.loader ?? "forge") as Loader;
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

async function runTui() {
  console.error("TUI mode is not yet implemented. Use CLI subcommands instead.");
  console.error("Run `minecraft-mod-mcp --help` for available commands.");
  process.exit(1);
}

main().catch((err) => {
  console.error("Fatal:", err);
  process.exit(1);
});

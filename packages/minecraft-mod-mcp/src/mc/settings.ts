import { existsSync, readFileSync, writeFileSync, mkdirSync, renameSync, chmodSync, unlinkSync } from "node:fs";
import { join, dirname } from "node:path";
import { randomBytes } from "node:crypto";
import { isWindows } from "../runtime/detector.js";
import { launcherDir } from "./platform.js";
import { GAME, PATHS } from "./defaults.js";

export interface MicrosoftAccount {
  type: "microsoft";
  uuid: string;
  username: string;
  access_token: string;
  refresh_token: string;
  not_after: number;
}

export interface OfflineAccount {
  type: "offline";
  uuid: string;
  username: string;
}

export type Account = MicrosoftAccount | OfflineAccount;

export type DownloadSource = "mojang" | "bmclapi";

export interface LauncherConfig {
  java_dir?: string;
  java_version?: number;
  max_memory_mb: number;
  min_memory_mb: number;
  game_dir?: string;
  java_args?: string;
  game_args?: string;
  width: number;
  height: number;
  fullscreen: boolean;
  accounts: Account[];
  selected_account?: string;
  download_source: DownloadSource;
  mcp_port?: number;
  language: string;
}

export function defaultConfig(): LauncherConfig {
  return {
    max_memory_mb: GAME.defaultMaxMemoryMb,
    min_memory_mb: GAME.defaultMinMemoryMb,
    width: GAME.defaultWidth,
    height: GAME.defaultHeight,
    fullscreen: false,
    accounts: [],
    download_source: GAME.defaultDownloadSource,
    language: GAME.defaultLanguage,
  };
}

export function configPath(): string {
  return join(launcherDir(), "config.json");
}

/**
 * Microsoft account tokens live here (0600) instead of config.json, so the
 * OAuth credentials are not world-readable and sharing a config file cannot
 * leak them (issue #81). Shape: { secrets: { [account uuid]: tokens } }.
 */
export function accountSecretsPath(): string {
  return join(launcherDir(), "account-secrets.json");
}

interface AccountTokens {
  access_token?: string;
  refresh_token?: string;
}

type SecretStore = { secrets: Record<string, AccountTokens> };

function readSecretStore(): Record<string, AccountTokens> {
  try {
    const store = JSON.parse(readFileSync(accountSecretsPath(), "utf-8")) as SecretStore;
    return store?.secrets && typeof store.secrets === "object" ? store.secrets : {};
  } catch {
    return {};
  }
}

function writeSecretStore(secrets: Record<string, AccountTokens>): void {
  const path = accountSecretsPath();
  const dir = dirname(path);
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true });
  // Write-then-rename so a crash mid-write cannot lose every token.
  const tmp = `${path}.tmp`;
  writeFileSync(tmp, JSON.stringify({ secrets } satisfies SecretStore, null, 2), { mode: 0o600, encoding: "utf-8" });
  try {
    // Rename does not carry the mode on every platform; re-apply owner-only.
    chmodSync(tmp, 0o600);
    renameSync(tmp, path);
  } catch (err) {
    try { unlinkSync(tmp); } catch {}
    throw err;
  }
}

/** A config with the token fields removed — the only shape persisted to config.json. */
type PersistedConfig = LauncherConfig;

function stripTokens(config: LauncherConfig): PersistedConfig {
  return {
    ...config,
    accounts: config.accounts.map((a) =>
      a.type === "microsoft" ? { ...a, access_token: "", refresh_token: "" } : a,
    ),
  };
}

export function loadConfig(): LauncherConfig {
  const path = configPath();
  if (!existsSync(path)) {
    const cfg = defaultConfig();
    saveConfig(cfg);
    return cfg;
  }
  let config: LauncherConfig;
  try {
    const content = readFileSync(path, "utf-8");
    config = { ...defaultConfig(), ...JSON.parse(content) } as LauncherConfig;
  } catch {
    return defaultConfig();
  }
  const secrets = readSecretStore();
  let migrated = false;
  config.accounts = config.accounts.map((a) => {
    if (a.type !== "microsoft") return a;
    // Tokens found in config.json are authoritative: they can only come from
    // the write-failure fallback of this version or an older build, both of
    // which wrote them later than the secrets file. Copy them over, then
    // strip config.json — even when they match, so plaintext never lingers.
    if (a.access_token || a.refresh_token) migrated = true;
    if (a.access_token && secrets[a.uuid]?.access_token !== a.access_token) {
      secrets[a.uuid] = { ...secrets[a.uuid], access_token: a.access_token };
    }
    if (a.refresh_token && secrets[a.uuid]?.refresh_token !== a.refresh_token) {
      secrets[a.uuid] = { ...secrets[a.uuid], refresh_token: a.refresh_token };
    }
    const stored = secrets[a.uuid];
    return { ...a, access_token: stored?.access_token || "", refresh_token: stored?.refresh_token || "" };
  });
  if (migrated) {
    try {
      writeSecretStore(secrets);
      writeFileSync(path, JSON.stringify(stripTokens(config), null, 2), "utf-8");
    } catch {
      // Read-only media: keep the merged in-memory view.
    }
  }
  return config;
}

export function saveConfig(config: LauncherConfig): void {
  const path = configPath();
  const dir = dirname(path);
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true });

  const secrets = readSecretStore();
  const live = new Set<string>();
  for (const a of config.accounts) {
    if (a.type !== "microsoft") continue;
    live.add(a.uuid);
    // Saved values always overwrite the stored ones; an explicit clear is an
    // empty string, and reload then serves the same empty token.
    const stored: AccountTokens = {
      access_token: a.access_token || "",
      refresh_token: a.refresh_token || "",
    };
    if (stored.access_token || stored.refresh_token) secrets[a.uuid] = stored;
    else delete secrets[a.uuid];
  }
  for (const uuid of Object.keys(secrets)) {
    if (!live.has(uuid)) delete secrets[uuid];
  }
  try {
    writeSecretStore(secrets);
  } catch {
    // Without the secrets file the accounts would lose their tokens on
    // reload; refuse to strip them from config.json in that case.
    writeFileSync(path, JSON.stringify(config, null, 2), "utf-8");
    return;
  }
  writeFileSync(path, JSON.stringify(stripTokens(config), null, 2), "utf-8");
}

export function mcpTokenPath(): string {
  return join(launcherDir(), "mcp-token");
}

/** The shared mod-API token if one exists: MC_MCP_TOKEN, then the token file. */
export function readMcpToken(): string | null {
  if (process.env.MC_MCP_TOKEN) return process.env.MC_MCP_TOKEN;
  try {
    return readFileSync(mcpTokenPath(), "utf-8").trim() || null;
  } catch {
    return null;
  }
}

/**
 * Resolve the shared token for the mod's HTTP API (issue #81), generating one
 * on first launch. MC_MCP_TOKEN lets external tooling and test harnesses pin a
 * known token for a whole process tree. The generated token lives in its own
 * owner-only file rather than config.json, so launching never rewrites the
 * launcher config.
 */
export function ensureMcpToken(): string {
  const existing = readMcpToken();
  if (existing) return existing;
  const path = mcpTokenPath();
  const dir = dirname(path);
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true });
  const token = randomBytes(32).toString("hex");
  try {
    writeFileSync(path, token, { mode: 0o600, flag: "wx" });
    return token;
  } catch {
    // The file exists (lost a first-launch race, or it is empty). Prefer
    // whatever is in it; only overwrite while it is still empty.
    const winner = readMcpToken();
    if (winner) return winner;
    const retry = randomBytes(32).toString("hex");
    try {
      writeFileSync(path, retry, { mode: 0o600, flag: "wx" });
    } catch {
      const after = readMcpToken();
      if (after) return after;
      writeFileSync(path, retry, { mode: 0o600 });
    }
    return retry;
  }
}

export function selectedAccount(config: LauncherConfig): Account | undefined {
  if (!config.selected_account) return undefined;
  return config.accounts.find((a) => accountUuid(a) === config.selected_account);
}

export function addAccount(config: LauncherConfig, account: Account): void {
  const uuid = accountUuid(account);
  const idx = config.accounts.findIndex((a) => accountUuid(a) === uuid);
  if (idx >= 0) config.accounts[idx] = account;
  else config.accounts.push(account);
}

export function removeAccount(config: LauncherConfig, uuid: string): void {
  config.accounts = config.accounts.filter((a) => accountUuid(a) !== uuid);
  if (config.selected_account === uuid) {
    config.selected_account = config.accounts[0]
      ? accountUuid(config.accounts[0])
      : undefined;
  }
}

export function accountUuid(a: Account): string {
  return a.uuid;
}

export function accountUsername(a: Account): string {
  return a.username;
}

export function accountAccessToken(a: Account): string {
  return a.type === "microsoft" ? a.access_token : "";
}

export function accountUserType(a: Account): string {
  return a.type === "microsoft" ? "msa" : "legacy";
}

export function gameDirPath(config: LauncherConfig): string {
  if (config.game_dir) return config.game_dir;
  return join(launcherDir(), PATHS.gameDirName);
}

export function serverDir(versionId?: string): string {
  const base = join(launcherDir(), PATHS.serverDirName);
  return versionId ? join(base, versionId) : base;
}

export function javaExecPath(config: LauncherConfig): string | undefined {
  if (!config.java_dir) return undefined;
  const base = config.java_dir;
  return isWindows()
    ? join(base, "bin", "java.exe")
    : join(base, "bin", "java");
}

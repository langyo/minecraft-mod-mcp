import { existsSync, readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";
import { crossHomedir, isWindows } from "../runtime/detector.js";
import { launcherDir, mcDir } from "./platform.js";

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
    max_memory_mb: 2048,
    min_memory_mb: 512,
    width: 854,
    height: 480,
    fullscreen: false,
    accounts: [],
    download_source: "bmclapi",
    language: "zh-CN",
  };
}

export function configPath(): string {
  return join(launcherDir(), "config.json");
}

export function loadConfig(): LauncherConfig {
  const path = configPath();
  if (!existsSync(path)) {
    const cfg = defaultConfig();
    saveConfig(cfg);
    return cfg;
  }
  try {
    const content = readFileSync(path, "utf-8");
    return { ...defaultConfig(), ...JSON.parse(content) } as LauncherConfig;
  } catch {
    return defaultConfig();
  }
}

export function saveConfig(config: LauncherConfig): void {
  const path = configPath();
  const dir = join(path, "..");
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true });
  writeFileSync(path, JSON.stringify(config, null, 2), "utf-8");
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
  return join(launcherDir(), "game");
}

export function serverDir(versionId?: string): string {
  const base = join(launcherDir(), "server");
  return versionId ? join(base, versionId) : base;
}

export function javaExecPath(config: LauncherConfig): string | undefined {
  if (!config.java_dir) return undefined;
  const base = config.java_dir;
  return isWindows()
    ? join(base, "bin", "java.exe")
    : join(base, "bin", "java");
}

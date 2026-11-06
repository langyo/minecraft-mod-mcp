import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync, mkdirSync, statSync, chmodSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const SAVED_ENV = ["APPDATA", "HOME", "USERPROFILE"].map((k) => [k, process.env[k]] as const);

let root: string;

beforeEach(() => {
  root = mkdtempSync(join(tmpdir(), "mcp-p1p2-"));
  process.env.APPDATA = root;
  process.env.HOME = root;
  process.env.USERPROFILE = root;
  vi.resetModules();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  for (const [k, v] of SAVED_ENV) {
    if (v === undefined) delete process.env[k];
    else process.env[k] = v;
  }
  rmSync(root, { recursive: true, force: true });
});

describe("account secrets store (issue #81 P1)", () => {
  it("migrates tokens out of an old config.json on load", async () => {
    const { loadConfig, configPath, accountSecretsPath } = await import("../src/mc/settings.js");
    const dir = join(root, ".minecraft", "mcp_launcher");
    mkdirSync(dir, { recursive: true });
    writeFileSync(configPath(), JSON.stringify({
      max_memory_mb: 2048,
      accounts: [
        { type: "microsoft", uuid: "u1", username: "a", access_token: "SECRET-ACCESS", refresh_token: "SECRET-REFRESH", not_after: 1 },
        { type: "offline", uuid: "u2", username: "b" },
      ],
      width: 854, height: 480, fullscreen: false, download_source: "mojang", language: "en-US",
    }), "utf-8");

    const config = loadConfig();

    // Runtime view still carries the tokens for the launch flow.
    const msa = config.accounts.find((a) => a.uuid === "u1")!;
    expect(msa.type === "microsoft" && msa.access_token).toBe("SECRET-ACCESS");

    // config.json no longer stores them.
    const persisted = JSON.parse(readFileSync(configPath(), "utf-8"));
    expect(JSON.stringify(persisted)).not.toContain("SECRET-ACCESS");
    expect(JSON.stringify(persisted)).not.toContain("SECRET-REFRESH");

    // The secrets file does, owner-only on POSIX.
    const secrets = JSON.parse(readFileSync(accountSecretsPath(), "utf-8"));
    expect(secrets.secrets.u1).toEqual({ access_token: "SECRET-ACCESS", refresh_token: "SECRET-REFRESH" });
    if (process.platform !== "win32") {
      expect(statSync(accountSecretsPath()).mode & 0o777).toBe(0o600);
    }
  });

  it("routes tokens of saved accounts to the secrets file", async () => {
    const { loadConfig, saveConfig, addAccount, accountSecretsPath, configPath } = await import("../src/mc/settings.js");
    const config = loadConfig();
    addAccount(config, { type: "microsoft", uuid: "u9", username: "c", access_token: "T-A", refresh_token: "T-R", not_after: 2 });
    saveConfig(config);

    expect(JSON.stringify(JSON.parse(readFileSync(configPath(), "utf-8")))).not.toContain("T-A");
    expect(JSON.parse(readFileSync(accountSecretsPath(), "utf-8")).secrets.u9).toEqual({ access_token: "T-A", refresh_token: "T-R" });

    // And loads them back merged.
    const re = await import("../src/mc/settings.js");
    const again = re.loadConfig();
    const msa = again.accounts.find((a) => a.uuid === "u9")!;
    expect(msa.type === "microsoft" && msa.access_token).toBe("T-A");
  });

  it("drops secrets of removed accounts", async () => {
    const { loadConfig, saveConfig, addAccount, removeAccount, accountSecretsPath } = await import("../src/mc/settings.js");
    const config = loadConfig();
    addAccount(config, { type: "microsoft", uuid: "gone", username: "g", access_token: "X", refresh_token: "Y", not_after: 3 });
    saveConfig(config);
    removeAccount(config, "gone");
    saveConfig(config);
    expect(JSON.parse(readFileSync(accountSecretsPath(), "utf-8")).secrets.gone).toBeUndefined();
  });

  it("keeps tokens in config.json when the secrets file cannot be written", async () => {
    const { loadConfig, saveConfig, addAccount, configPath, accountSecretsPath } = await import("../src/mc/settings.js");
    // A directory in place of the secrets file makes writing it fail.
    mkdirSync(accountSecretsPath(), { recursive: true });
    const config = loadConfig();
    addAccount(config, { type: "microsoft", uuid: "u7", username: "h", access_token: "KEEP-ME", refresh_token: "R", not_after: 4 });
    saveConfig(config);
    expect(JSON.stringify(JSON.parse(readFileSync(configPath(), "utf-8")))).toContain("KEEP-ME");
  });

  it("newer tokens in config.json beat stale ones in the secrets file", async () => {
    const settings = await import("../src/mc/settings.js");
    const { loadConfig, saveConfig, configPath, accountSecretsPath, addAccount } = settings;
    const config = loadConfig();
    addAccount(config, { type: "microsoft", uuid: "u8", username: "i", access_token: "FRESH", refresh_token: "FR", not_after: 5 });
    saveConfig(config);
    // Simulate the fallback: secrets file becomes read-only, a new login
    // lands in config.json instead.
    const secretsPath = accountSecretsPath();
    if (process.platform !== "win32") chmodSync(secretsPath, 0o444);
    const again = loadConfig();
    addAccount(again, { type: "microsoft", uuid: "u8", username: "i", access_token: "NEWER", refresh_token: "NR", not_after: 6 });
    saveConfig(again);
    if (process.platform !== "win32") chmodSync(secretsPath, 0o600);

    const reloaded = await import("../src/mc/settings.js");
    const cfg = reloaded.loadConfig();
    const msa = cfg.accounts.find((a) => a.uuid === "u8")!;
    expect(msa.type === "microsoft" && msa.access_token).toBe("NEWER");
    expect(JSON.stringify(JSON.parse(readFileSync(configPath(), "utf-8")))).not.toContain("NEWER");
  });
});

describe("multi-instance discovery (issue #81 P1)", () => {
  const status = (port: number, pid: number, instanceId: string) => ({
    ok: true, type: "minecraft-mod", version: "0.4.28", loader: "forge",
    pid, port, instanceId, uptime: 1,
  });

  async function withStubbedProbes(instances: ReturnType<typeof status>[], fn: () => Promise<void>) {
    vi.stubGlobal("fetch", vi.fn(async (url: string) => {
      const m = /:(\d+)\/api\/status$/.exec(String(url));
      const found = m ? instances.find((i) => i.port === Number(m[1])) : undefined;
      return found
        ? new Response(JSON.stringify(found), { status: 200 })
        : new Response("nope", { status: 404 });
    }));
    await fn();
  }

  it("findAllMods reports every instance, not just the first port", async () => {
    await withStubbedProbes([status(9876, 11, "aaa"), status(9874, 22, "bbb")], async () => {
      const { findAllMods } = await import("../src/discovery/scanner.js");
      const all = await findAllMods(9876, 9870);
      expect(all.map((s) => s.port)).toEqual([9876, 9874]);
    });
  });

  it("discover prefers the game this client spawned", async () => {
    await withStubbedProbes([status(9876, 111, "first"), status(9874, 222, "mine")], async () => {
      const { ModClient } = await import("../src/api/modClient.js");
      const mod = new ModClient();
      (mod as any).mcProcess = { pid: 222, exitCode: null };
      const found = await mod.discover(9876, 9870);
      expect(found?.port).toBe(9874);
      expect((mod as any).instanceId).toBe("mine");
    });
  });

  it("discover falls back to the remembered instanceId", async () => {
    await withStubbedProbes([status(9876, 111, "first"), status(9874, 222, "mine")], async () => {
      const { ModClient } = await import("../src/api/modClient.js");
      const mod = new ModClient();
      (mod as any).instanceId = "mine";
      expect((await mod.discover(9876, 9870))?.port).toBe(9874);
    });
  });

  it("a fresh launch owns the client: no falling back to an older game", async () => {
    await withStubbedProbes([status(9876, 111, "first")], async () => {
      const { ModClient } = await import("../src/api/modClient.js");
      const mod = new ModClient();
      expect((await mod.discover(9876, 9870))?.port).toBe(9876); // connected to A
      // Launch game B (pid 222) — not answering yet.
      (mod as any).mcProcess = { pid: 222, exitCode: null };
      (mod as any).spawnedAtMs = Date.now();
      (mod as any).disconnect();
      (mod as any).instanceId = null;
      expect(mod.connected).toBe(false);
      expect(await mod.discover(9876, 9870)).toBeNull(); // refuses A during grace
      // B comes up: the client binds to it even though A answers first.
      await withStubbedProbes([status(9876, 111, "first"), status(9874, 222, "mine")], async () => {
        expect((await mod.discover(9876, 9870))?.port).toBe(9874);
      });
    });
  });

  it("falls back to any game after the spawn grace window", async () => {
    await withStubbedProbes([status(9876, 111, "first")], async () => {
      const { ModClient } = await import("../src/api/modClient.js");
      const mod = new ModClient();
      (mod as any).mcProcess = { pid: 999, exitCode: null }; // pid never matches
      (mod as any).spawnedAtMs = Date.now() - 60_000;         // grace expired
      expect((await mod.discover(9876, 9870))?.port).toBe(9876);
    });
  });
});

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { PACKAGE_VERSION } from "../src/mc/defaults.js";

const MC = "1.99.0";
const LOADER = "forge";
const TAG_URL = `https://api.github.com/repos/langyo/minecraft-mod-mcp/releases/tags/v${PACKAGE_VERSION}`;
const LATEST_URL = "https://api.github.com/repos/langyo/minecraft-mod-mcp/releases/latest";
const JAR_URL = "https://github.com/langyo/minecraft-mod-mcp/releases/download/x/mod.jar";
const JAR_BYTES = Buffer.from("verified jar bytes");
const JAR_DIGEST = `sha256:${createHash("sha256").update(JAR_BYTES).digest("hex")}`;

const SAVED_ENV = ["APPDATA", "HOME", "USERPROFILE", "MC_MCP_TOKEN"].map((k) => [k, process.env[k]] as const);

let root: string;
let cwd: string;
let launcherDir: string;
let cacheDir: string;

// platform.ts memoizes the .minecraft location, so each test imports fresh
// modules against its own temp home.
beforeEach(() => {
  cwd = process.cwd();
  root = mkdtempSync(join(tmpdir(), "mcp-hardening-"));
  process.chdir(root);
  process.env.APPDATA = root;
  process.env.HOME = root;
  process.env.USERPROFILE = root;
  delete process.env.MC_MCP_TOKEN;
  launcherDir = join(root, ".minecraft", "mcp_launcher");
  cacheDir = join(launcherDir, "modjars");
  vi.resetModules();
  vi.spyOn(console, "error").mockImplementation(() => {});
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  process.chdir(cwd);
  for (const [k, v] of SAVED_ENV) {
    if (v === undefined) delete process.env[k];
    else process.env[k] = v;
  }
  rmSync(root, { recursive: true, force: true });
});

function release(tag: string, digest: string | null = JAR_DIGEST) {
  return { tag_name: tag, assets: [{ name: `minecraft-mcp-${MC}-${LOADER}.jar`, browser_download_url: JAR_URL, digest }] };
}

/** Stub fetch from a URL -> Response map; unknown URLs fail like a network error. */
function stubFetch(routes: Record<string, () => Response>) {
  const calls: string[] = [];
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    calls.push(url);
    const route = routes[url];
    if (!route) throw new TypeError("fetch failed");
    return route();
  }));
  return calls;
}

const json = (body: unknown, status = 200) => () => new Response(JSON.stringify(body), { status });
const jarResponse = () => new Response(JAR_BYTES);

function cacheJar(name: string) {
  mkdirSync(cacheDir, { recursive: true });
  writeFileSync(join(cacheDir, name), "cached");
  return join(cacheDir, name);
}

async function ensureModJar() {
  const platform = await import("../src/mc/platform.js");
  return platform.ensureModJar(MC, LOADER);
}

describe("ensureModJar", () => {
  it("downloads the pinned release and caches it under a version-keyed name", async () => {
    const calls = stubFetch({ [TAG_URL]: json(release(`v${PACKAGE_VERSION}`)), [JAR_URL]: jarResponse });
    const jar = await ensureModJar();
    expect(jar).toBe(join(cacheDir, `minecraft-mcp-${MC}-${LOADER}-v${PACKAGE_VERSION}.jar`));
    expect(readFileSync(jar!)).toEqual(JAR_BYTES);
    expect(calls).not.toContain(LATEST_URL);
  });

  it("refuses a jar that does not match the published digest", async () => {
    stubFetch({ [TAG_URL]: json(release(`v${PACKAGE_VERSION}`, `sha256:${"0".repeat(64)}`)), [JAR_URL]: jarResponse });
    expect(await ensureModJar()).toBeNull();
    expect(existsSync(cacheDir) ? readdirSync(cacheDir) : []).toEqual([]);
  });

  it("refuses a jar with no published digest", async () => {
    stubFetch({ [TAG_URL]: json(release(`v${PACKAGE_VERSION}`, null)), [JAR_URL]: jarResponse });
    expect(await ensureModJar()).toBeNull();
    expect(existsSync(cacheDir) ? readdirSync(cacheDir) : []).toEqual([]);
  });

  it("does not swap in the latest release on a rate limit", async () => {
    const calls = stubFetch({ [TAG_URL]: json({ message: "rate limited" }, 403), [LATEST_URL]: json(release("v0.1.0")) });
    expect(await ensureModJar()).toBeNull();
    expect(calls).toEqual([TAG_URL]);
  });

  it("falls back to the latest release only when the tag was never published", async () => {
    stubFetch({ [TAG_URL]: json({ message: "Not Found" }, 404), [LATEST_URL]: json(release("v0.1.0")), [JAR_URL]: jarResponse });
    expect(await ensureModJar()).toBe(join(cacheDir, `minecraft-mcp-${MC}-${LOADER}-v0.1.0.jar`));
  });

  it("prefers a version-keyed cached jar over the latest release", async () => {
    const cached = cacheJar(`minecraft-mcp-${MC}-${LOADER}-v0.2.0.jar`);
    const calls = stubFetch({ [TAG_URL]: json({ message: "Not Found" }, 404) });
    expect(await ensureModJar()).toBe(cached);
    expect(calls).toEqual([TAG_URL]);
  });

  it("reuses a version-keyed cached jar when offline", async () => {
    const cached = cacheJar(`minecraft-mcp-${MC}-${LOADER}-v0.2.0.jar`);
    stubFetch({});
    expect(await ensureModJar()).toBe(cached);
  });

  it("never reuses a legacy unversioned cached jar", async () => {
    cacheJar(`minecraft-mcp-${MC}-${LOADER}.jar`);
    stubFetch({});
    expect(await ensureModJar()).toBeNull();
  });
});

describe("mod API token", () => {
  async function settings() {
    return import("../src/mc/settings.js");
  }

  it("generates one token, reuses it and leaves config.json alone", async () => {
    const { ensureMcpToken, readMcpToken } = await settings();
    expect(readMcpToken()).toBeNull();
    const token = ensureMcpToken();
    expect(token).toMatch(/^[0-9a-f]{64}$/);
    expect(ensureMcpToken()).toBe(token);
    expect(readMcpToken()).toBe(token);
    expect(existsSync(join(launcherDir, "config.json"))).toBe(false);
  });

  it("prefers MC_MCP_TOKEN and then writes nothing", async () => {
    process.env.MC_MCP_TOKEN = "from-env";
    const { ensureMcpToken, mcpTokenPath } = await settings();
    expect(ensureMcpToken()).toBe("from-env");
    expect(existsSync(mcpTokenPath())).toBe(false);
  });

  it("treats an empty MC_MCP_TOKEN as unset", async () => {
    process.env.MC_MCP_TOKEN = "";
    const { ensureMcpToken } = await settings();
    expect(ensureMcpToken()).toMatch(/^[0-9a-f]{64}$/);
  });

  it("recovers from an empty token file", async () => {
    const { ensureMcpToken, mcpTokenPath } = await settings();
    mkdirSync(launcherDir, { recursive: true });
    writeFileSync(mcpTokenPath(), "");
    const token = ensureMcpToken();
    expect(token).toMatch(/^[0-9a-f]{64}$/);
    expect(readFileSync(mcpTokenPath(), "utf-8")).toBe(token);
  });

  it.skipIf(process.platform === "win32")("keeps the token file owner-only", async () => {
    const { ensureMcpToken, mcpTokenPath } = await settings();
    ensureMcpToken();
    expect(statSync(mcpTokenPath()).mode & 0o777).toBe(0o600);
  });
});

describe("buildLaunchCommand", () => {
  const vj = { id: MC, mainClass: "example.Main", libraries: [], arguments: { jvm: [], game: [] } };

  async function build(modJar: string, extra: Record<string, unknown> = {}) {
    const { buildLaunchCommand } = await import("../src/mc/launch.js");
    return buildLaunchCommand(
      { versionId: MC, loader: LOADER, mcDir: join(root, "game"), javaPath: "java", modJar, ...extra } as never,
      vj as never,
    );
  }

  function modJar(name: string) {
    const path = join(root, name);
    writeFileSync(path, "jar");
    return path;
  }

  it("reports the jar's version, consents to remote control and keeps the token out of argv", async () => {
    const cmd = await build(modJar(`minecraft-mcp-${MC}-${LOADER}-v1.2.3.jar`), { mcpToken: "t0ken" });
    expect(cmd.args).toContain("-Dmcp.mod.version=1.2.3");
    expect(cmd.args).toContain("-Dmcp.remote_control=true");
    expect(cmd.env.MC_MCP_TOKEN).toBe("t0ken");
    expect(cmd.args.join(" ")).not.toContain("t0ken");
  });

  it("does not guess a version for an unversioned jar", async () => {
    const cmd = await build(modJar(`minecraft-mcp-${MC}-${LOADER}.jar`));
    expect(cmd.args.some((a) => a.startsWith("-Dmcp.mod.version="))).toBe(false);
  });

  it("leaves mods/ untouched on a dry run", async () => {
    const mods = join(root, "game", "mods");
    mkdirSync(mods, { recursive: true });
    writeFileSync(join(mods, `minecraft-mcp-${MC}-${LOADER}.jar`), "old");
    await build(modJar(`minecraft-mcp-${MC}-${LOADER}-v1.2.3.jar`), { dryRun: true });
    expect(readdirSync(mods)).toEqual([`minecraft-mcp-${MC}-${LOADER}.jar`]);
  });
});

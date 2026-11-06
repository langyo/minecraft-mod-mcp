import { join, resolve, dirname, basename } from "node:path";
import { existsSync, readdirSync, mkdirSync, writeFileSync, copyFileSync, unlinkSync, statSync } from "node:fs";
import { createHash } from "node:crypto";
import { crossHomedir, isWindows, isMacos, classpathSeparator as cpSep } from "../runtime/detector.js";
import { detectJavas } from "./javaDetect.js";
import { PATHS, JAVA, PACKAGE_VERSION } from "./defaults.js";
import { assertSafeUrl } from "./urlGuard.js";

function homedir(): string {
  return crossHomedir();
}

let _mcDir: string | null = null;

export function mcDir(): string {
  if (_mcDir) return _mcDir;

  const candidates: string[] = [];
  if (isWindows() && process.env.APPDATA) {
    candidates.push(join(process.env.APPDATA, PATHS.mcDirName));
  }
  candidates.push(join(homedir(), PATHS.mcDirName));

  for (const dir of candidates) {
    if (existsSync(dir) && existsSync(join(dir, PATHS.versionsDirName))) {
      _mcDir = dir;
      return dir;
    }
  }

  const fallback = candidates[0] || join(homedir(), PATHS.mcDirName);
  _mcDir = fallback;
  return fallback;
}

export function versionsDir(): string {
  return join(mcDir(), PATHS.versionsDirName);
}

export function librariesDir(): string {
  return join(mcDir(), PATHS.librariesDirName);
}

export function assetsDir(): string {
  return join(mcDir(), PATHS.assetsDirName);
}

export function nativesDir(versionId: string): string {
  return join(mcDir(), PATHS.versionsDirName, versionId, PATHS.nativesDirName);
}

export function launcherDir(): string {
  return join(mcDir(), PATHS.launcherDirName);
}

// Version suffix of an MCP mod jar name: "-1.2.3.jar" (local builds),
// "-v1.2.3.jar" (release / cache) or "-1.2.3-SNAPSHOT.jar". Unversioned
// release names ("minecraft-mcp-1.12.2-forge.jar") end in the loader and do
// not match, so an MC version is never mistaken for the mod version.
const JAR_VERSION_RE = /-v?(\d+\.\d+\.\d+(?:-SNAPSHOT)?)\.jar$/i;

/** Mod version embedded in a jar file name, or null when the name carries none. */
export function modVersionFromJar(jarPath: string): string | null {
  const m = basename(jarPath).match(JAR_VERSION_RE);
  return m ? m[1] : null;
}

function compareVersions(a: string, b: string): number {
  const parse = (v: string) => {
    const dash = v.indexOf("-");
    const core = dash >= 0 ? v.slice(0, dash) : v;
    return { nums: core.split(".").map((n) => parseInt(n, 10) || 0), pre: dash >= 0 ? v.slice(dash + 1) : "" };
  };
  const pa = parse(a), pb = parse(b);
  for (let i = 0; i < 3; i++) {
    const d = (pa.nums[i] ?? 0) - (pb.nums[i] ?? 0);
    if (d !== 0) return d;
  }
  if (pa.pre === pb.pre) return 0;
  if (pa.pre === "") return 1;
  if (pb.pre === "") return -1;
  return pa.pre.localeCompare(pb.pre);
}

/** Pick the jar matching this bridge's version, else the highest-versioned one. */
function pickModJar(jars: string[]): string | null {
  if (jars.length === 0) return null;
  const exact = jars.find((f) => modVersionFromJar(f) === PACKAGE_VERSION);
  if (exact) return exact;
  const versioned = jars.filter((f) => modVersionFromJar(f) !== null);
  if (versioned.length > 0) {
    versioned.sort((a, b) => compareVersions(modVersionFromJar(b)!, modVersionFromJar(a)!));
    return versioned[0];
  }
  return [...jars].sort((a, b) => b.length - a.length)[0];
}

export function modJarPath(mcVersion: string, loader: string): string | null {
  const projectDir = resolve("packages", "mods", mcVersion, loader);
  const libsDir = join(projectDir, "build", "libs");
  if (!existsSync(libsDir)) return null;

  try {
    // build/libs keeps one jar per version ever built; the old "longest
    // name wins" pick could launch 0.4.17 over 0.4.21 (issue #81).
    const jars = readdirSync(libsDir).filter(f => f.endsWith(".jar") && !f.endsWith("-sources.jar") && !f.endsWith("-javadoc.jar"));
    const pick = pickModJar(jars);
    if (pick) return join(libsDir, pick);
  } catch {}

  return null;
}

// Names this project gives its own mod jars: release/cache names and the
// local Gradle output. Anything else in mods/ belongs to the user.
const MCP_JAR_RE = /^(minecraft-mcp-\d[\w.]*-(forge|fabric|neoforge)(-v[\d.]+)?|minecraft-moddev-mcp-.+)\.jar$/;

/**
 * Copy a mod jar into a mods/ directory, first removing stale copies of the
 * MCP mod. Version-keyed jar names would otherwise leave two copies side by
 * side and the loader refuses to start with a duplicate mod (issue #81).
 */
export function deployModJar(modsDir: string, modJar: string): string {
  if (!existsSync(modsDir)) mkdirSync(modsDir, { recursive: true });
  const name = basename(modJar);
  for (const f of readdirSync(modsDir)) {
    if (f !== name && MCP_JAR_RE.test(f)) {
      try { unlinkSync(join(modsDir, f)); } catch {}
    }
  }
  const dest = join(modsDir, name);
  // A rebuilt jar keeps its name, so existence alone is not enough.
  if (!existsSync(dest) || statSync(modJar).mtimeMs > statSync(dest).mtimeMs) {
    try {
      copyFileSync(modJar, dest);
    } catch (err) {
      // Windows locks the jar of a running game; keep that copy.
      if (!existsSync(dest)) throw err;
    }
  }
  return dest;
}

const RELEASES_API = "https://api.github.com/repos/langyo/minecraft-mod-mcp/releases";

interface ReleaseAsset {
  name: string;
  browser_download_url: string;
  digest?: string | null;
}

interface Release {
  tag_name?: string;
  assets?: ReleaseAsset[];
}

/** The release, "missing" for a 404, or null for any other failure. */
async function fetchRelease(url: string): Promise<Release | "missing" | null> {
  const resp = await fetch(url, {
    headers: { "user-agent": "minecraft-mod-mcp", accept: "application/json" },
  });
  if (resp.status === 404) return "missing";
  return resp.ok ? ((await resp.json()) as Release) : null;
}

/**
 * Newest version-keyed cached jar. Unversioned cache entries are skipped on
 * purpose: older bridges wrote them without pinning or verification, so they
 * may hold a mod that predates the loopback binding and token auth.
 */
function newestCachedJar(cacheDir: string, mcVersion: string, loader: string): string | null {
  if (!existsSync(cacheDir)) return null;
  const prefix = `minecraft-mcp-${mcVersion}-${loader}-v`;
  try {
    const pick = pickModJar(readdirSync(cacheDir).filter((f) => f.startsWith(prefix) && f.endsWith(".jar")));
    return pick ? join(cacheDir, pick) : null;
  } catch {
    return null;
  }
}

/**
 * Resolve a mod JAR for a version/loader, downloading it from GitHub Releases
 * when no locally-built one exists. End users running via `npx` have no source
 * checkout, so without this the game launches vanilla (no MCP HTTP server).
 *
 * Hardened in issue #81: the download is pinned to the release matching this
 * bridge's version and verified against the sha256 digest GitHub publishes for
 * each release asset. When that tag was never published the newest cached jar
 * is used, else the latest stable release. Any other failure (offline, rate
 * limit, missing asset) can only reuse a cached jar. Every fallback warns on
 * stderr. Returns null if no JAR can be obtained (launch then proceeds
 * without the mod).
 */
export async function ensureModJar(mcVersion: string, loader: string): Promise<string | null> {
  const local = modJarPath(mcVersion, loader);
  if (local) return local;

  const cacheDir = join(launcherDir(), "modjars");
  const pinnedCache = join(cacheDir, `minecraft-mcp-${mcVersion}-${loader}-v${PACKAGE_VERSION}.jar`);
  if (existsSync(pinnedCache)) return pinnedCache;

  const cachedFallback = (): string | null => {
    const jar = newestCachedJar(cacheDir, mcVersion, loader);
    if (jar) console.error(`[minecraft-mod-mcp] Using cached ${basename(jar)}: the v${PACKAGE_VERSION} mod is not available.`);
    return jar;
  };

  let rel: Release | "missing" | null;
  try {
    rel = await fetchRelease(`${RELEASES_API}/tags/v${PACKAGE_VERSION}`);
    if (rel === "missing") {
      const jar = cachedFallback();
      if (jar) return jar;
      rel = await fetchRelease(`${RELEASES_API}/latest`);
    }
  } catch {
    rel = null;
  }
  if (!rel || rel === "missing") return cachedFallback();

  const esc = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const pattern = new RegExp(`^minecraft-mcp-${esc(mcVersion)}-${esc(loader)}(-v[\\d.]+)?\\.jar$`);
  const asset = rel.assets?.find((a) => pattern.test(a.name));
  if (!asset) return cachedFallback();

  const relVersion = (rel.tag_name ?? "").replace(/^v/, "");
  if (relVersion !== PACKAGE_VERSION) {
    console.error(`[minecraft-mod-mcp] No v${PACKAGE_VERSION} release yet; using the mod from ${rel.tag_name}.`);
  }
  const cachePath = join(cacheDir, `minecraft-mcp-${mcVersion}-${loader}-v${relVersion || "0.0.0"}.jar`);
  if (existsSync(cachePath)) return cachePath;

  let bytes: Buffer;
  try {
    const dlResp = await fetch(assertSafeUrl(asset.browser_download_url));
    if (!dlResp.ok) return cachedFallback();
    bytes = Buffer.from(await dlResp.arrayBuffer());
  } catch {
    return cachedFallback();
  }

  // Integrity: refuse a jar unless its bytes match the published digest. Every
  // release this bridge can reach publishes one, so a missing digest is as
  // suspect as a wrong one.
  const expected = asset.digest?.toLowerCase().match(/^sha256:([0-9a-f]{64})$/)?.[1];
  const actual = createHash("sha256").update(bytes).digest("hex");
  if (actual !== expected) {
    console.error(`[minecraft-mod-mcp] ${asset.name} failed sha256 verification (published ${asset.digest ?? "no digest"}, got ${actual}); refusing to use it.`);
    return null;
  }

  try {
    if (!existsSync(cacheDir)) mkdirSync(cacheDir, { recursive: true });
    writeFileSync(cachePath, bytes);
    return cachePath;
  } catch {
    return null;
  }
}

export function jdkHome(javaVersion: number): string | null {
  const envVar = `JDK_${javaVersion}_HOME`;
  const envVal = process.env[envVar];
  if (envVal && existsSync(envVal)) return envVal;

  const mcJavaDir = join(launcherDir(), PATHS.javaDirName, `jdk-${javaVersion}`);
  if (existsSync(mcJavaDir)) {
    try {
      const jdk = readdirSync(mcJavaDir, { withFileTypes: true })
        .find(e => e.isDirectory() && !e.name.startsWith("."));
      if (jdk) {
        const home = join(mcJavaDir, jdk.name);
        const exe = isWindows() ? join(home, "bin", "java.exe") : join(home, "bin", "java");
        if (existsSync(exe)) return home;
      }
    } catch {}
  }

  const jdksDir = join(homedir(), PATHS.gradleJdksSubdir);
  if (!existsSync(jdksDir)) return null;

  const prefixes = JAVA.jdkDirPrefixes;

  const prefix = prefixes[javaVersion];
  if (!prefix) return null;

  try {
    for (const entry of readdirSync(jdksDir, { withFileTypes: true })) {
      if (
        entry.isDirectory() &&
        entry.name.startsWith(prefix) &&
        !entry.name.includes("lock")
      ) {
        return join(jdksDir, entry.name);
      }
    }
  } catch {}

  return null;
}

export function javaExec(javaVersion: number): string {
  const home = jdkHome(javaVersion);
  if (!home) throw new Error(`Java not found: version ${javaVersion}`);

  const exe = isWindows()
    ? join(home, "bin", "java.exe")
    : join(home, "bin", "java");

  if (existsSync(exe)) return exe;
  throw new Error(`Java not found: version ${javaVersion}`);
}

export function findJavaOnPath(): string | null {
  const exe = isWindows() ? "java.exe" : "java";
  return exe;
}

export function findJavaForVersion(targetVersion: number): string {
  const home = jdkHome(targetVersion);
  if (home) {
    const exe = isWindows() ? join(home, "bin", "java.exe") : join(home, "bin", "java");
    if (existsSync(exe)) return exe;
  }

  const all = detectJavas();
  const sorted = [...all].sort((a, b) => a.version - b.version);

  const pick =
    sorted.find((j) => j.version === targetVersion) ??
    sorted.find((j) => j.version >= targetVersion) ??
    (sorted.length > 0 ? sorted[sorted.length - 1] : null);

  if (pick) {
    const exe = isWindows()
      ? join(pick.path, "bin", "java.exe")
      : join(pick.path, "bin", "java");
    if (existsSync(exe)) return exe;
  }

  return isWindows() ? "java.exe" : "java";
}

export function classpathSeparator(): string {
  return cpSep();
}

export function getNativeClassifier(): string {
  if (isWindows()) {
    return process.arch === "arm64" ? "natives-windows-arm64" : "natives-windows";
  }
  if (isMacos()) {
    return process.arch === "arm64" ? "natives-macos-arm64" : "natives-macos";
  }
  return "natives-linux";
}

import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { inflateRawSync } from "node:zlib";
import { spawnSync } from "node:child_process";
import { librariesDir } from "./platform.js";
import { DOWNLOAD } from "./defaults.js";

/**
 * Generic runner for the Forge/NeoForge installer `processors` pipeline.
 *
 * Modern Forge (1.13+, "fg3" through "mc26") does not ship a launchable
 * client jar. Instead the installer ships an `install_profile.json` whose
 * `processors` array describes a sequence of Java tools (jarsplitter,
 * ForgeAutoRenamingTool, binarypatcher, …) that take the vanilla client jar
 * and produce the remapped/patched artifacts (`*-srg.jar`, `*-extra.jar`,
 * `forge-<ver>-client.jar`) the version JSON references at launch.
 *
 * Without running these, launching Forge fails with
 * `Invalid paths argument, contained no existing paths: […-srg.jar, …-extra.jar,
 * forge-…-client.jar]` (or a JPMS `module not found` on the bootstrap era).
 *
 * This module replays those processors headlessly, the same way the official
 * installer does, so the bridge's install actually produces a runnable client.
 */

export interface ProcessorProfile {
  spec?: number;
  processors?: Processor[];
  data?: Record<string, { client?: string; server?: string }>;
  libraries?: Array<{ name?: string; downloads?: { artifact?: { path?: string; url?: string; sha1?: string } } }>;
}

interface Processor {
  sides?: string[];
  jar: string;
  classpath?: string[];
  args?: string[];
}

export interface ProcessorContext {
  /** Path to the Forge/NeoForge installer jar (kept in .tmp during install). */
  installerJar: string;
  /** Side to install for — "client" or "server". */
  side: "client" | "server";
  /** Vanilla jar for this side: versionsDir/<mc>/<mc>.jar (client) or server jar. */
  minecraftJar: string;
  /** Java executable used to run the processor tools (any JDK 8+). */
  javaExecutable: string;
}

const DEFAULT_EXT = "jar";

/** Read a jar's manifest and return the `Main-Class` value, if any. */
function readManifestMainClass(jarPath: string): string | null {
  // Prefer the system unzip for robustness (some jars trip the hand-rolled parser).
  const sys = spawnSync("unzip", ["-p", jarPath, "META-INF/MANIFEST.MF"], { maxBuffer: 4 * 1024 * 1024 });
  if (sys.status === 0 && sys.stdout && sys.stdout.length > 0) {
    const main = parseMainClass(sys.stdout.toString("utf-8"));
    if (main) return main;
  }
  const entry = readJarEntry(readFileSync(jarPath), "META-INF/MANIFEST.MF");
  return entry ? parseMainClass(entry.toString("utf-8")) : null;
}

function parseMainClass(manifest: string): string | null {
  const text = manifest.replace(/\r/g, "");
  for (const line of text.split("\n")) {
    const m = line.match(/^Main-Class:\s*(.+)$/);
    if (m) return m[1].trim();
  }
  return null;
}

/** Resolve `group:artifact:version[:classifier]` + ext to a libraries file path. */
function artifactFilePath(coords: string, ext: string): string {
  const parts = coords.split(":");
  const group = parts[0].replace(/\./g, "/");
  const artifact = parts[1];
  const version = parts[2];
  const classifier = parts[3];
  const file = classifier
    ? `${artifact}-${version}-${classifier}.${ext}`
    : `${artifact}-${version}.${ext}`;
  return join(librariesDir(), group, artifact, version, file);
}

/** Match `[coords@ext]` / `[coords:classifier@ext]` / `[coords]` artifact references. */
function parseArtifactRef(token: string): { coords: string; ext: string } | null {
  const m = token.match(/^\[([^\]]+)\]$/);
  if (!m) return null;
  const inner = m[1];
  const at = inner.lastIndexOf("@");
  if (at >= 0) return { coords: inner.slice(0, at), ext: inner.slice(at + 1) };
  return { coords: inner, ext: DEFAULT_EXT };
}

/**
 * Best-effort download of an artifact referenced by the processor pipeline.
 * Inputs (mcp_config, mojmaps, processor tools) download fine; outputs
 * (e.g. `net.minecraft:client:…:srg`) are produced by the processors and
 * will 404 — that is expected and silently ignored.
 */
async function ensureArtifact(coords: string, ext: string): Promise<string> {
  const filePath = artifactFilePath(coords, ext);
  const dir = dirname(filePath);
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true });
  if (existsSync(filePath)) return filePath;

  const mvnRel = artifactFilePath(coords, ext).slice(librariesDir().length + 1);
  const candidates = [
    `${DOWNLOAD.forgeMavenUrl}${mvnRel}`,
    `${DOWNLOAD.mavenLibrariesUrl}${mvnRel}`,
    `${DOWNLOAD.neoforgeMavenUrl}${mvnRel}`,
    ...DOWNLOAD.fallbackRepoUrls.map((u) => `${u}${mvnRel}`),
  ];
  for (const url of candidates) {
    try {
      const resp = await fetch(url);
      if (!resp.ok) continue;
      const ab = Buffer.from(await resp.arrayBuffer());
      writeFileSync(filePath, ab);
      return filePath;
    } catch {
      /* try next mirror */
    }
  }
  return filePath; // may not exist yet — likely a processor output
}

/** Extract a `/data/…` entry from the installer jar to a stable cache path. */
function extractInstallerResource(installerJar: string, entryPath: string): string {
  const entry = entryPath.replace(/^\//, "");
  const outDir = join(dirname(installerJar), "processor-data");
  if (!existsSync(outDir)) mkdirSync(outDir, { recursive: true });
  const outPath = join(outDir, entry.replace(/\//g, "__"));
  if (existsSync(outPath)) return outPath;

  // Prefer the system unzip — it correctly handles large/streaming/ZIP64 entries
  // that the hand-rolled parser below can choke on (e.g. multi-MB data/client.lzma).
  const sys = spawnSync("unzip", ["-p", installerJar, entry], { maxBuffer: 64 * 1024 * 1024 });
  if (sys.status === 0 && sys.stdout && sys.stdout.length > 0) {
    writeFileSync(outPath, sys.stdout);
    return outPath;
  }

  const buf = readFileSync(installerJar);
  const data = readJarEntry(buf, entry);
  if (!data) throw new Error(`Installer missing entry: ${entry}`);
  writeFileSync(outPath, data);
  return outPath;
}

/** Resolve a single (already side-selected) data/arg token to a concrete string. */
async function resolveToken(
  token: string,
  ctx: ProcessorContext,
  data: Record<string, string>,
): Promise<string> {
  // {SYMBOL} — looked up from data map or well-known runtime symbols.
  const sym = token.match(/^\{([^}]+)\}$/);
  if (sym) {
    const key = sym[1];
    if (key in data) return data[key];
    switch (key) {
      case "SIDE": return ctx.side;
      case "MINECRAFT_JAR": return ctx.minecraftJar;
      case "INSTALLER": return ctx.installerJar;
      case "LIBRARY_DIR": return librariesDir();
      default: return token; // unknown symbol — leave as-is
    }
  }
  // 'literal' — strip surrounding single quotes.
  if (token.startsWith("'") && token.endsWith("'")) return token.slice(1, -1);
  // [artifact@ext] — resolve to a libraries file path (downloading inputs).
  const ref = parseArtifactRef(token);
  if (ref) return await ensureArtifact(ref.coords, ref.ext);
  // /path — resource bundled inside the installer jar.
  if (token.startsWith("/")) return extractInstallerResource(ctx.installerJar, token);
  return token;
}

export async function runForgeProcessors(
  profile: ProcessorProfile,
  ctx: ProcessorContext,
  onProgress?: (msg: string) => void,
): Promise<void> {
  const processors = profile.processors ?? [];
  if (processors.length === 0) return;

  // 1. Resolve the `data` table for the chosen side up-front (each value may
  //    itself be an artifact ref / installer resource / literal).
  const data: Record<string, string> = {};
  for (const [key, sides] of Object.entries(profile.data ?? {})) {
    const raw = sides[ctx.side] ?? sides.client ?? sides.server;
    if (raw != null) data[key] = await resolveToken(raw, ctx, data);
  }

  // 2. Replay each processor (filtered by side) the way the official installer does.
  for (let i = 0; i < processors.length; i++) {
    const proc = processors[i];
    if (proc.sides && !proc.sides.includes(ctx.side)) continue;

    const jarPath = await ensureArtifact(proc.jar, DEFAULT_EXT);
    const cpEntries = [jarPath];
    for (const cp of proc.classpath ?? []) cpEntries.push(await ensureArtifact(cp, DEFAULT_EXT));

    const mainClass = readManifestMainClass(jarPath);
    if (!mainClass) {
      onProgress?.(`  Warning: processor ${proc.jar} has no Main-Class, skipping`);
      continue;
    }

    const resolvedArgs: string[] = [];
    for (const arg of proc.args ?? []) resolvedArgs.push(await resolveToken(arg, ctx, data));

    // Windows uses ';' as the classpath separator; ':' is POSIX-only. Using the
    // wrong one makes every multi-jar processor classpath resolve to a single
    // bogus path → ClassNotFoundException on the processor's main class.
    const classpath = cpEntries.join(process.platform === "win32" ? ";" : ":");
    onProgress?.(`  [processor ${i + 1}/${processors.length}] ${mainClass}`);

    const result = spawnSync(ctx.javaExecutable, ["-cp", classpath, mainClass, ...resolvedArgs], {
      stdio: ["ignore", "pipe", "pipe"],
      maxBuffer: 16 * 1024 * 1024,
    });

    if (result.status !== 0) {
      const stderr = result.stderr?.toString("utf-8").trim().slice(-500);
      // Output artifacts legitimately don't pre-exist; only hard-fail on real errors.
      throw new Error(`Forge processor ${proc.jar} failed (exit ${result.status}): ${stderr}`);
    }
  }

  onProgress?.(`  Processors complete (${processors.length} steps)`);
}

/* ----------------------------- zip helpers ------------------------------ */

function readJarEntry(buf: Buffer, entryName: string): Buffer | null {
  let eocd = -1;
  for (let i = buf.length - 22; i >= 0; i--) {
    if (buf[i] === 0x50 && buf[i + 1] === 0x4b && buf[i + 2] === 0x05 && buf[i + 3] === 0x06) {
      eocd = i;
      break;
    }
  }
  if (eocd < 0) return null;

  const cdOffset = buf.readUInt32LE(eocd + 16);
  const cdSize = buf.readUInt32LE(eocd + 12);
  const cdEnd = cdOffset + cdSize;

  let pos = cdOffset;
  while (pos + 46 <= cdEnd) {
    if (buf.readUInt32LE(pos) !== 0x02014b50) break;
    const nameLen = buf.readUInt16LE(pos + 28);
    const extraLen = buf.readUInt16LE(pos + 30);
    const commentLen = buf.readUInt16LE(pos + 32);
    const localOffset = buf.readUInt32LE(pos + 42);
    const name = buf.subarray(pos + 46, pos + 46 + nameLen).toString("utf-8");
    pos += 46 + nameLen + extraLen + commentLen;

    if (name !== entryName) continue;

    // Use the CENTRAL DIRECTORY sizes (offset+20/24), not the local header's —
    // jars written in streaming mode put 0 in the local header and a trailing
    // data descriptor, which would yield Z_BUF_ERROR on inflate.
    const cSize = buf.readUInt32LE(pos + 20);
    const lNameLen = buf.readUInt16LE(localOffset + 26);
    const lExtraLen = buf.readUInt16LE(localOffset + 28);
    const lMethod = buf.readUInt16LE(localOffset + 8);
    const dataStart = localOffset + 30 + lNameLen + lExtraLen;
    const compressed = buf.subarray(dataStart, dataStart + cSize);
    return lMethod === 0 ? compressed : inflateRawSync(compressed);
  }
  return null;
}

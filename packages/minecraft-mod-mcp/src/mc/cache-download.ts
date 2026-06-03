import { existsSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { CACHE } from "./defaults.js";
import { mcDir } from "./platform.js";
import { isWindows } from "../runtime/detector.js";

const CACHE_DIR_NAME = ".cache";

function cacheDir(): string {
  return join(mcDir(), CACHE_DIR_NAME);
}

function cacheMarkerPath(tag: string): string {
  return join(cacheDir(), `${tag}.ok`);
}

export function isCacheExtracted(tag: string): boolean {
  return existsSync(cacheMarkerPath(tag));
}

function findCacheEntry(mcVersion: string): { tag: string; file: string } | null {
  for (const entry of Object.values(CACHE.assets)) {
    if (entry.versionPrefixes.some((p) => mcVersion.startsWith(p))) {
      return { tag: entry.tag, file: entry.file };
    }
  }
  return null;
}

export function needsCache(mcVersion: string): boolean {
  return findCacheEntry(mcVersion) !== null;
}

function buildDownloadUrl(tag: string, file: string): string[] {
  const base = `https://github.com/${CACHE.repo}/releases/download/${tag}/${file}`;
  const urls: string[] = [];
  for (const proxy of CACHE.githubProxyUrls) {
    urls.push(`${proxy}${base}`);
  }
  return urls;
}

async function tryDownload(urls: string[], dest: string): Promise<void> {
  const dir = dirname(dest);
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true });

  let lastErr: Error | null = null;
  for (const url of urls) {
    try {
      const resp = await fetch(url, { redirect: "follow" });
      if (!resp.ok) continue;
      const buf = Buffer.from(await resp.arrayBuffer());
      if (buf.length < 1000) continue;
      writeFileSync(dest, buf);
      return;
    } catch (e) {
      lastErr = e instanceof Error ? e : new Error(String(e));
    }
  }
  throw lastErr ?? new Error(`All download attempts failed for ${dest}`);
}

async function extractZip(archive: string, outDir: string): Promise<void> {
  mkdirSync(outDir, { recursive: true });
  if (isWindows()) {
    const { execFileSync } = await import("node:child_process");
    const ps = `Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.ZipFile]::ExtractToDirectory('${archive}','${outDir}')`;
    execFileSync("powershell", ["-NoProfile", "-Command", ps], {
      stdio: "pipe",
      timeout: 120_000,
    });
  } else {
    const { execSync } = await import("node:child_process");
    execSync(`unzip -o -q "${archive}" -d "${outDir}"`, { stdio: "pipe", timeout: 120_000 });
  }
}

export async function ensureCacheExtracted(
  mcVersion: string,
  onProgress?: (msg: string) => void,
): Promise<void> {
  const entry = findCacheEntry(mcVersion);
  if (!entry) return;

  if (isCacheExtracted(entry.tag)) return;

  const zipPath = join(cacheDir(), entry.file);

  if (!existsSync(zipPath)) {
    onProgress?.(`Downloading cache for ${mcVersion}...`);
    const urls = buildDownloadUrl(entry.tag, entry.file);
    await tryDownload(urls, zipPath);
    onProgress?.(`Download complete.`);
  }

  onProgress?.(`Extracting cache for ${mcVersion}...`);
  const target = mcDir();
  await extractZip(zipPath, target);
  onProgress?.(`Cache extracted.`);

  writeFileSync(cacheMarkerPath(entry.tag), new Date().toISOString());
}

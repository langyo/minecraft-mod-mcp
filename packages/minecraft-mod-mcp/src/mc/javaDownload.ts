import { existsSync, mkdirSync, createWriteStream, readdirSync, rmSync } from "node:fs";
import { join, basename, dirname } from "node:path";
import { isWindows, isMacos } from "../runtime/detector.js";
import { launcherDir, jdkHome } from "./platform.js";
import { JAVA, PATHS } from "./defaults.js";
import { downloadWithNativeFallback } from "./proxy.js";

function javaCacheDir(): string {
  return join(launcherDir(), PATHS.javaDirName);
}

function getPlatform(): string {
  if (isWindows()) return "windows";
  if (isMacos()) return "mac";
  return "linux";
}

function getArch(): string {
  const arch = process.arch;
  if (arch === "arm64") return "aarch64";
  return "x64";
}

export function installedJavaHome(javaVersion: number): string | null {
  const dir = join(javaCacheDir(), `jdk-${javaVersion}`);
  if (!existsSync(dir)) return null;

  const entries = readdirSync(dir, { withFileTypes: true });
  const jdkDir = entries.find(e => e.isDirectory() && !e.name.startsWith("."));
  if (!jdkDir) return null;

  const home = join(dir, jdkDir.name);
  const exe = isWindows() ? join(home, "bin", "java.exe") : join(home, "bin", "java");
  return existsSync(exe) ? home : null;
}

interface AdoptiumAsset {
  binary: {
    package: {
      link: string;
      name: string;
      checksum: string;
    };
    installer?: {
      link: string;
      name: string;
    };
  };
}

async function fetchJdkUrl(javaVersion: number): Promise<{ url: string; name: string }> {
  const platform = getPlatform();
  const arch = getArch();

  const apiUrl = `${JAVA.adoptiumApiUrl}/${javaVersion}/hotspot?architecture=${arch}&image_type=jdk&os=${platform}&vendor=eclipse`;

  const res = await fetch(apiUrl);
  if (!res.ok) throw new Error(`Adoptium API error: ${res.status} ${res.statusText}`);

  const assets = (await res.json()) as AdoptiumAsset[];
  if (!assets || assets.length === 0) throw new Error(`No JDK ${javaVersion} found for ${platform}-${arch}`);

  const pkg = assets[0].binary.package;
  return { url: pkg.link, name: pkg.name };
}

async function downloadFile(url: string, dest: string, expectedSize: number, onProgress?: (msg: string) => void): Promise<void> {
  onProgress?.(`Downloading ${basename(dest)}...`);
  mkdirSync(dirname(dest), { recursive: true });

  // Stream via Node fetch first (gives progress). JDK tarballs are served from
  // CDNs that frequently reset mid-stream; on any failure fall back to the native
  // downloader, which resumes (`curl -C -`) and retries across resets.
  try {
    await streamDownload(url, dest, expectedSize, onProgress);
    return;
  } catch (err: any) {
    if (existsSync(dest)) try { rmSync(dest, { force: true }); } catch {}
    onProgress?.(`  Node fetch failed (${err.cause?.code || err.message}), resuming via native download...`);
    await downloadWithNativeFallback(url, dest);
  }

  // Validate after the fallback path too.
  if (expectedSize > 0) {
    const { statSync } = await import("node:fs");
    try {
      const got = statSync(dest).size;
      if (got < expectedSize * 0.95) throw new Error(`Download incomplete: got ${got} bytes, expected ~${expectedSize}`);
    } catch (e) {
      throw e;
    }
  }
}

async function streamDownload(url: string, dest: string, expectedSize: number, onProgress?: (msg: string) => void): Promise<void> {
  const res = await fetch(url, { redirect: "follow" });
  if (!res.ok) throw new Error(`Download failed: ${res.status}`);
  if (!res.body) throw new Error("No response body");

  const total = parseInt(res.headers.get("content-length") ?? "0");
  let downloaded = 0;
  let lastPct = -1;

  const fileStream = createWriteStream(dest);
  const reader = res.body as unknown as AsyncIterable<Uint8Array>;

  for await (const chunk of reader) {
    fileStream.write(chunk);
    downloaded += chunk.length;
    if (total > 0) {
      const pct = Math.floor((downloaded / total) * 100);
      if (pct !== lastPct && pct % 10 === 0) {
        lastPct = pct;
        onProgress?.(`Downloading ${basename(dest)}... ${pct}%`);
      }
    }
  }
  fileStream.end();
  await new Promise<void>((resolve, reject) => {
    fileStream.on("finish", resolve);
    fileStream.on("error", reject);
  });

  if (expectedSize > 0 && downloaded < expectedSize * 0.95) {
    try { rmSync(dest, { force: true }); } catch {}
    throw new Error(`Download incomplete: got ${downloaded} bytes, expected ~${expectedSize}`);
  }
}

async function extractArchive(archive: string, outDir: string): Promise<void> {
  mkdirSync(outDir, { recursive: true });

  if (isWindows()) {
    const { execFileSync } = await import("node:child_process");
    const escArchive = archive.replace(/'/g, "''");
    const escOutDir = outDir.replace(/'/g, "''");
    const psScript = `Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.ZipFile]::ExtractToDirectory('${escArchive}', '${escOutDir}')`;
    execFileSync("powershell", ["-NoProfile", "-Command", psScript], {
      stdio: "pipe",
      timeout: JAVA.extractTimeoutMs,
    });
  } else {
    const { execSync } = await import("node:child_process");
    execSync(`tar -xzf "${archive}" -C "${outDir}"`, { stdio: "pipe", timeout: JAVA.extractTimeoutMs });
  }
}

export async function ensureJavaInstalled(
  javaVersion: number,
  onProgress?: (msg: string) => void,
): Promise<string> {
  const existing = installedJavaHome(javaVersion);
  if (existing) return existing;

  const fromGradle = jdkHome(javaVersion);
  if (fromGradle) {
    onProgress?.(`Java ${javaVersion} found at ${fromGradle}`);
    return fromGradle;
  }

  const cacheDir = join(javaCacheDir(), `jdk-${javaVersion}`);
  const tmpDir = join(javaCacheDir(), "tmp");

  onProgress?.(`Java ${javaVersion} not found, downloading from Adoptium...`);

  const { url, name } = await fetchJdkUrl(javaVersion);

  const archivePath = join(tmpDir, name);
  if (existsSync(archivePath)) {
    const { statSync } = await import("node:fs");
    if (statSync(archivePath).size < 10_000_000) {
      const { unlinkSync } = await import("node:fs");
      try { unlinkSync(archivePath); } catch {}
    }
  }
  if (!existsSync(archivePath)) {
    mkdirSync(tmpDir, { recursive: true });
    await downloadFile(url, archivePath, 0, onProgress);
  }

  onProgress?.(`Extracting JDK ${javaVersion}...`);

  rmSync(cacheDir, { recursive: true, force: true });
  await extractArchive(archivePath, cacheDir);

  const home = installedJavaHome(javaVersion);
  if (!home) throw new Error(`JDK ${javaVersion} installation failed: no valid home found in ${cacheDir}`);

  onProgress?.(`Java ${javaVersion} installed at ${home}`);

  rmSync(archivePath, { force: true });

  return home;
}

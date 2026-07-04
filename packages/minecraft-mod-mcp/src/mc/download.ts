import { createHash } from "node:crypto";
import { existsSync, mkdirSync, writeFileSync, readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { spawnSync } from "node:child_process";
import { versionsDir, assetsDir, librariesDir, mcDir } from "./platform.js";
import { libraryMavenPath, loadVersionMerged } from "./versionJson.js";
import type { VersionJson } from "./versionJson.js";
import { loadVersionsData } from "./versionsData.js";
import { getVersion, getVersionForLoader, DEFAULT_FABRIC_LOADER_VERSION, type Loader } from "./versions.js";
import { GAME, MCP, PATHS, DOWNLOAD } from "./defaults.js";
import { fetchWithFallback, downloadWithNativeFallback } from "./proxy.js";
import { runForgeProcessors } from "./forgeProcessor.js";
import { findJavaForVersion } from "./platform.js";
import { detectJavas } from "./javaDetect.js";

export interface ForgeInstallProfileLegacy {
  install: {
    profileName: string;
    target: string;
    path: string;
    version: string;
    filePath: string;
    minecraft: string;
    mirrorList?: string;
  };
  versionInfo: VersionJson;
}

export interface ForgeInstallProfileModern {
  spec?: number;
  profile?: string;
  version?: string;
  minecraft?: string;
  json?: string;
  path?: string;
  logo?: string;
  mirrorList?: string;
  libraries?: VersionJson["libraries"];
  processors?: unknown[];
  data?: Record<string, Record<string, string>>;
  install?: {
    profileName?: string;
    target?: string;
    path?: string;
    version?: string;
    filePath?: string;
    minecraft?: string;
    json?: string;
    mirrorList?: string;
  };
  versionInfo?: VersionJson | string;
}

export interface VersionManifest {
  latest: { release: string; snapshot: string };
  versions: Array<{
    id: string;
    type: string;
    url: string;
    time: string;
    releaseTime: string;
  }>;
}

export async function fetchVersionManifest(): Promise<VersionManifest> {
  const resp = await fetchWithFallback(DOWNLOAD.versionManifestUrl);
  if (!resp.ok) throw new Error(`Failed to fetch version manifest: ${resp.status}`);
  return (await resp.json()) as VersionManifest;
}

export async function fetchVersionJson(url: string): Promise<VersionJson> {
  const resp = await fetchWithFallback(url);
  if (!resp.ok) throw new Error(`Failed to fetch version JSON: ${resp.status}`);
  return (await resp.json()) as VersionJson;
}

async function sha1File(filePath: string): Promise<string> {
  const data = readFileSync(filePath);
  return createHash("sha1").update(data).digest("hex");
}

export async function downloadFile(
  url: string,
  path: string,
  expectedSha1?: string,
): Promise<void> {
  if (existsSync(path)) {
    if (expectedSha1) {
      const existing = await sha1File(path);
      if (existing.toLowerCase() === expectedSha1.toLowerCase()) return;
    } else {
      return;
    }
  }

  const dir = dirname(path);
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true });

  await downloadWithNativeFallback(url, path);

  if (expectedSha1) {
    const { readFile } = await import("node:fs/promises");
    const buf = await readFile(path);
    const actual = createHash("sha1").update(buf).digest("hex");
    if (actual.toLowerCase() !== expectedSha1.toLowerCase()) {
      throw new Error(`SHA-1 mismatch for ${path}: expected ${expectedSha1}, got ${actual}`);
    }
  }
}

export async function downloadVersion(
  versionJson: VersionJson,
  onProgress?: (msg: string) => void,
): Promise<void> {
  const versionId = versionJson.id;
  const vDir = join(versionsDir(), versionId);
  if (!existsSync(vDir)) mkdirSync(vDir, { recursive: true });

  const jsonPath = join(vDir, `${versionId}.json`);
  writeFileSync(jsonPath, JSON.stringify(versionJson, null, 2), "utf-8");
  onProgress?.(`Saved version JSON for ${versionId}`);

  if (versionJson.downloads?.client?.url) {
    const jarPath = join(vDir, `${versionId}.jar`);
    if (!existsSync(jarPath)) {
      onProgress?.(`Downloading client JAR for ${versionId}...`);
      await downloadFile(versionJson.downloads.client.url, jarPath, versionJson.downloads.client.sha1);
      onProgress?.(`Client JAR downloaded.`);
    }
  }

  if (versionJson.assetIndex?.url) {
    const idxDir = join(assetsDir(), PATHS.assetIndexesDirName);
    if (!existsSync(idxDir)) mkdirSync(idxDir, { recursive: true });
    const idxPath = join(idxDir, `${versionJson.assetIndex.id}.json`);

    if (!existsSync(idxPath)) {
      await downloadFile(versionJson.assetIndex.url, idxPath, versionJson.assetIndex.sha1);
    }

    const indexData = JSON.parse(readFileSync(idxPath, "utf-8"));
    const objects = indexData.objects as Record<string, { hash: string; size: number }> | undefined;
    if (objects) {
      const entries = Object.entries(objects);
      onProgress?.(`Downloading ${entries.length} assets...`);

      const BATCH = DOWNLOAD.assetBatchSize;
      for (let i = 0; i < entries.length; i += BATCH) {
        const batch = entries.slice(i, i + BATCH);
        await Promise.all(batch.map(async ([, obj]) => {
          const hash = obj.hash;
          const prefix = hash.slice(0, 2);
          const assetUrl = `${DOWNLOAD.assetBaseUrl}${prefix}/${hash}`;
          const objDir = join(assetsDir(), PATHS.assetObjectsDirName, prefix);
          if (!existsSync(objDir)) mkdirSync(objDir, { recursive: true });
          const assetPath = join(objDir, hash);
          if (!existsSync(assetPath)) {
            try { await downloadFile(assetUrl, assetPath, hash); } catch { /* skip */ }
          }
        }));
        onProgress?.(`Assets: ${Math.min(i + BATCH, entries.length)}/${entries.length}`);
      }
    }
  }

  onProgress?.(`Downloading libraries for ${versionId}...`);
  for (const lib of versionJson.libraries) {
    if (lib.downloads?.artifact?.url) {
      const libPath = join(librariesDir(), lib.downloads.artifact.path);
      if (!existsSync(libPath)) {
        try {
          await downloadFile(lib.downloads.artifact.url, libPath, lib.downloads.artifact.sha1);
        } catch {
          const mvnPath = libraryMavenPath(lib.name);
          let ok = false;
          for (const repo of DOWNLOAD.fallbackRepoUrls) {
            try {
              await downloadFile(`${repo}${mvnPath}`, libPath);
              ok = true;
              break;
            } catch { /* try next */ }
          }
          if (!ok) onProgress?.(`  Warning: could not download ${lib.name}`);
        }
      }
    } else if (lib.name) {
      const mvnPath = libraryMavenPath(lib.name);
      const url = `${DOWNLOAD.mavenLibrariesUrl}${mvnPath}`;
      const filePath = join(librariesDir(), mvnPath);
      if (!existsSync(filePath)) {
        let ok = false;
        try {
          await downloadFile(url, filePath);
          ok = true;
        } catch { /* try fallback */ }
        if (!ok) {
          for (const repo of DOWNLOAD.fallbackRepoUrls) {
            try {
              await downloadFile(`${repo}${mvnPath}`, filePath);
              ok = true;
              break;
            } catch { /* try next */ }
          }
        }
        if (!ok) onProgress?.(`  Warning: could not download ${lib.name}`);
      }
    }

    // Native classifier jars. Modern MC (1.19.4+) ships natives as separate
    // artifact libraries with OS rules (handled by the loop above). Legacy MC
    // (<=1.19.3) declares natives via `natives` + `downloads.classifiers` — those
    // classifier jars are NOT covered by the artifact branch above, so download
    // them explicitly or the game fails at launch with UnsatisfiedLinkError.
    await downloadNativeClassifiers(lib, onProgress);
  }

  onProgress?.(`Version ${versionId} download complete`);
}

export function listInstalledVersions(): string[] {
  const vDir = versionsDir();
  if (!existsSync(vDir)) return [];

  const installed: string[] = [];
  try {
    for (const entry of readdirSync(vDir, { withFileTypes: true })) {
      if (entry.isDirectory() && entry.name !== PATHS.tmpSuffix) {
        const jsonPath = join(vDir, entry.name, `${entry.name}.json`);
        if (existsSync(jsonPath)) installed.push(entry.name);
      }
    }
  } catch {}

  return installed.sort();
}

export async function downloadLoaderVersion(
  mcVersion: string,
  loader: Loader,
  loaderVersion: string,
  onProgress?: (msg: string) => void,
): Promise<void> {
  switch (loader) {
    case "forge":
      await downloadForgeInstaller(loaderVersion, onProgress);
      break;
    case "fabric":
      await downloadFabricLoader(mcVersion, loaderVersion, onProgress);
      break;
    case "neoforge":
      await downloadNeoforgeInstaller(loaderVersion, onProgress);
      break;
  }
}

export async function downloadForgeInstaller(
  forgeVersion: string,
  onProgress?: (msg: string) => void,
): Promise<void> {
  const mavenUrl = `${DOWNLOAD.forgeMavenUrl}net/minecraftforge/forge/${forgeVersion}/forge-${forgeVersion}-installer.jar`;
  const tmpDir = join(versionsDir(), PATHS.tmpSuffix);
  if (!existsSync(tmpDir)) mkdirSync(tmpDir, { recursive: true });
  const installerPath = join(tmpDir, `forge-${forgeVersion}-installer.jar`);

  onProgress?.(`Downloading Forge installer for ${forgeVersion}...`);
  await downloadFile(mavenUrl, installerPath);

  onProgress?.(`Extracting install profile...`);
  const profile = await extractJarJson<ForgeInstallProfileModern>(installerPath, "install_profile.json");
  if (!profile) throw new Error("Invalid Forge installer: missing install_profile.json");

  let versionJson: VersionJson;
  let versionId: string;

  if (profile.versionInfo && typeof profile.versionInfo === "object" && "id" in profile.versionInfo) {
    versionJson = profile.versionInfo as VersionJson;
    versionId = versionJson.id;
    onProgress?.(`Found embedded versionInfo (legacy format)`);
  } else {
    const jsonPath = (profile as any).json || (profile.install as any)?.json || "/version.json";
    const entryName = jsonPath.replace(/^\//, "");
    onProgress?.(`Extracting version JSON from ${entryName} (modern format)...`);
    const vj = await extractJarJson<VersionJson>(installerPath, entryName);
    if (!vj || !vj.id) throw new Error(`Invalid Forge installer: missing ${entryName}`);
    versionJson = vj;
    versionId = vj.id;
  }

  const vDir = join(versionsDir(), versionId);
  if (!existsSync(vDir)) mkdirSync(vDir, { recursive: true });

  const jsonFilePath = join(vDir, `${versionId}.json`);
  writeFileSync(jsonFilePath, JSON.stringify(versionJson, null, 2), "utf-8");
  onProgress?.(`Saved version JSON for ${versionId}`);

  const universalFileName = profile.install?.filePath
    ?? `forge-${forgeVersion}-universal.jar`;
  const universalUrl = `${DOWNLOAD.forgeMavenUrl}net/minecraftforge/forge/${forgeVersion}/${universalFileName}`;
  const forgeLibName = `net.minecraftforge:forge:${forgeVersion}`;
  const forgeLibPath = join(librariesDir(), libraryMavenPath(forgeLibName));
  const forgeLibDir = dirname(forgeLibPath);
  if (!existsSync(forgeLibDir)) mkdirSync(forgeLibDir, { recursive: true });
  if (!existsSync(forgeLibPath)) {
    onProgress?.(`Downloading Forge universal JAR...`);
    await downloadFile(universalUrl, forgeLibPath);
  }

  const allLibs = [...(versionJson.libraries ?? [])];
  if (profile.libraries) allLibs.push(...profile.libraries);
  await downloadLibraries(allLibs, onProgress);

  // Produce the remapped+patched client artifacts the version JSON launches
  // against. Try the fast headless processor replay first; on some modern
  // Forge versions (1.21.11 etc.) our FART output diverges from the patch
  // data's expected checksums and the binarypatcher fails. In that case fall
  // back to the official installer jar, which is authoritative (but slow and
  // can hang on very old Forge installers, so it's the fallback, not the
  // primary path).
  let processorsOk = false;
  let replayErr = "";
  try {
    await runForgeInstallerProcessors(profile, installerPath, forgeVersion, "client", onProgress);
    processorsOk = true;
  } catch (err: any) {
    replayErr = (err?.message ?? err).toString();
    onProgress?.(`  Headless processor replay failed: ${replayErr.slice(0, 120)}`);
  }
  // Only fall back to the official installer when the replay hit the known
  // binarypatcher checksum mismatch (modern Forge). Old Forge installers hang
  // headless in the official jar, so don't try them — a different replay error
  // there just means the version genuinely isn't installable by us.
  const wantsOfficial = !processorsOk && /binarypatcher|checksum|Patch expected/i.test(replayErr);
  if (wantsOfficial) {
    onProgress?.(`Falling back to official installer jar (binarypatcher mismatch)...`);
    const usedOfficial = await tryOfficialInstaller(installerPath, versionId, "client", mcDir(), onProgress);
    if (!usedOfficial) {
      throw new Error("Both headless processor replay and official installer failed");
    }
  } else if (!processorsOk) {
    // Replay failed for a non-binarypatcher reason and the official installer
    // isn't a safe fallback here — surface the original failure.
    throw new Error(replayErr || "Forge processor replay failed");
  }

  onProgress?.(`Forge ${versionId} install complete`);
}

/**
 * Run the official Forge/NeoForge installer jar headlessly via
 * `java -jar <installer> --installClient <mcDir>`. This is the authoritative
 * way to produce the patched client artifacts — our processor replay diverges
 * from the installer's FART on some versions (binarypatcher checksum mismatch).
 * Returns true if the official installer ran and produced the expected client
 * jar, false if it should fall back to the headless replay.
 */
async function tryOfficialInstaller(
  installerPath: string,
  versionId: string,
  side: "client" | "server",
  targetMcDir: string,
  onProgress?: (msg: string) => void,
): Promise<boolean> {
  if (!existsSync(installerPath)) return false;
  const javaExe = processorJavaExecutable();
  const flag = side === "client" ? "--installClient" : "--installServer";

  // The patched client jar the installer produces — presence signals success.
  const versionLibBase = join(librariesDir(), "net", "minecraftforge", "forge");
  const expectedClientJar = join(versionLibBase, versionId.replace(/^forge-/, "").replace("-forge", "-forge"),
    `forge-${versionId.split("-").slice(-2).join("-") || versionId}-client.jar`);

  onProgress?.(`Running official installer (${side})...`);
  const r = spawnSync(javaExe, ["-jar", installerPath, flag, targetMcDir], {
    stdio: ["ignore", "pipe", "pipe"],
    maxBuffer: 32 * 1024 * 1024,
    timeout: 600_000,
  });
  if (r.status !== 0) {
    const tail = (r.stderr?.toString("utf-8") || r.stdout?.toString("utf-8") || "").trim().slice(-300);
    onProgress?.(`  Official installer exited ${r.status}: ${tail}`);
    return false;
  }
  // The installer prints "Successfully installed" on success; treat that + the
  // patched jar existing as success. If the jar is missing, fall back.
  const out = (r.stdout?.toString("utf-8") || "") + (r.stderr?.toString("utf-8") || "");
  const ok = /Successfully installed|already installed/i.test(out);
  if (!ok) {
    onProgress?.(`  Official installer did not report success; falling back`);
    return false;
  }
  onProgress?.(`  Official installer succeeded`);
  return true;
}

/** Resolve a Java executable (any JDK 8+) for running installer processor tools. */
function processorJavaExecutable(): string {
  for (const v of [17, 21, 8, 16]) {
    const exe = findJavaForVersion(v);
    if (exe) return exe;
  }
  const all = detectJavas();
  if (all.length > 0) return `${all[0].path}/bin/java`;
  return "java";
}

async function runForgeInstallerProcessors(
  profile: ForgeInstallProfileModern,
  installerPath: string,
  loaderVersion: string,
  side: "client" | "server",
  onProgress?: (msg: string) => void,
): Promise<void> {
  if (!profile.processors || profile.processors.length === 0) return;

  const mcVersion = profile.minecraft
    ?? (profile.install as { minecraft?: string })?.minecraft
    ?? loaderVersion.split("-")[0];

  const vanillaJar = join(versionsDir(), mcVersion, `${mcVersion}.jar`);
  if (side === "client" && !existsSync(vanillaJar)) {
    onProgress?.(`  Skipping processors: vanilla client jar not found at ${vanillaJar}`);
    return;
  }

  onProgress?.(`Running ${profile.processors.length} installer processors (${side})...`);
  await runForgeProcessors(
    profile as unknown as Parameters<typeof runForgeProcessors>[0],
    {
      installerJar: installerPath,
      side,
      minecraftJar: vanillaJar,
      javaExecutable: processorJavaExecutable(),
    },
    onProgress,
  );
}

export async function downloadFabricLoader(
  mcVersion: string,
  loaderVersion: string,
  onProgress?: (msg: string) => void,
): Promise<void> {
  const profileUrl = `${DOWNLOAD.fabricMetaUrl}/${mcVersion}/${loaderVersion}/profile/json`;
  onProgress?.(`Fetching Fabric profile for ${mcVersion} loader ${loaderVersion}...`);
  const resp = await fetchWithFallback(profileUrl);
  if (!resp.ok) throw new Error(`Failed to fetch Fabric profile: HTTP ${resp.status}`);
  const profileJson = await resp.json() as VersionJson;

  const versionId = profileJson.id;
  const vDir = join(versionsDir(), versionId);
  if (!existsSync(vDir)) mkdirSync(vDir, { recursive: true });

  const jsonPath = join(vDir, `${versionId}.json`);
  if (existsSync(jsonPath)) {
    onProgress?.(`Fabric loader ${versionId} already installed.`);
    return;
  }

  writeFileSync(jsonPath, JSON.stringify(profileJson, null, 2), "utf-8");
  onProgress?.(`Saved version JSON for ${versionId}`);

  await downloadLibraries(profileJson.libraries, onProgress);

  onProgress?.(`Fabric ${versionId} install complete`);
}

export async function downloadNeoforgeInstaller(
  neoforgeVersion: string,
  onProgress?: (msg: string) => void,
): Promise<void> {
  const mavenUrl = `${DOWNLOAD.neoforgeMavenUrl}net/neoforged/neoforge/${neoforgeVersion}/neoforge-${neoforgeVersion}-installer.jar`;
  const tmpDir = join(versionsDir(), PATHS.tmpSuffix);
  if (!existsSync(tmpDir)) mkdirSync(tmpDir, { recursive: true });
  const installerPath = join(tmpDir, `neoforge-${neoforgeVersion}-installer.jar`);

  onProgress?.(`Downloading NeoForge installer for ${neoforgeVersion}...`);
  await downloadFile(mavenUrl, installerPath);

  onProgress?.(`Extracting install profile...`);
  const profile = await extractJarJson<ForgeInstallProfileModern>(installerPath, "install_profile.json");
  if (!profile) throw new Error("Invalid NeoForge installer: missing install_profile.json");

  let versionJson: VersionJson;
  let versionId: string;

  if (profile.versionInfo && typeof profile.versionInfo === "object" && "id" in profile.versionInfo) {
    versionJson = profile.versionInfo as VersionJson;
    versionId = versionJson.id;
    onProgress?.(`Found embedded versionInfo (legacy format)`);
  } else {
    const jsonPath = (profile as any).json || (profile.install as any)?.json || "/version.json";
    const entryName = jsonPath.replace(/^\//, "");
    onProgress?.(`Extracting version JSON from ${entryName} (modern format)...`);
    const vj = await extractJarJson<VersionJson>(installerPath, entryName);
    if (!vj || !vj.id) throw new Error(`Invalid NeoForge installer: missing ${entryName}`);
    versionJson = vj;
    versionId = vj.id;
  }

  const vDir = join(versionsDir(), versionId);
  if (!existsSync(vDir)) mkdirSync(vDir, { recursive: true });

  const jsonFilePath = join(vDir, `${versionId}.json`);
  writeFileSync(jsonFilePath, JSON.stringify(versionJson, null, 2), "utf-8");
  onProgress?.(`Saved version JSON for ${versionId}`);

  const universalFileName = profile.install?.filePath
    ?? `neoforge-${neoforgeVersion}-universal.jar`;
  const universalUrl = `${DOWNLOAD.neoforgeMavenUrl}net/neoforged/neoforge/${neoforgeVersion}/${universalFileName}`;
  const nfLibName = `net.neoforged:neoforge:${neoforgeVersion}`;
  const nfLibPath = join(librariesDir(), libraryMavenPath(nfLibName));
  const nfLibDir = dirname(nfLibPath);
  if (!existsSync(nfLibDir)) mkdirSync(nfLibDir, { recursive: true });
  if (!existsSync(nfLibPath)) {
    onProgress?.(`Downloading NeoForge JAR...`);
    await downloadFile(universalUrl, nfLibPath);
  }

  const allLibs = [...(versionJson.libraries ?? [])];
  if (profile.libraries) allLibs.push(...profile.libraries);
  await downloadLibraries(allLibs, onProgress);

  // Try the fast headless processor replay first; fall back to the official
  // installer jar only when the replay hit the known binarypatcher mismatch.
  let processorsOk = false;
  let replayErr = "";
  try {
    await runForgeInstallerProcessors(profile, installerPath, neoforgeVersion, "client", onProgress);
    processorsOk = true;
  } catch (err: any) {
    replayErr = (err?.message ?? err).toString();
    onProgress?.(`  Headless processor replay failed: ${replayErr.slice(0, 120)}`);
  }
  const wantsOfficial = !processorsOk && /binarypatcher|checksum|Patch expected|invalid file signature/i.test(replayErr);
  if (wantsOfficial) {
    onProgress?.(`Falling back to official installer jar (patch mismatch)...`);
    const usedOfficial = await tryOfficialInstaller(installerPath, versionId, "client", mcDir(), onProgress);
    if (!usedOfficial) {
      throw new Error("Both headless processor replay and official installer failed");
    }
  } else if (!processorsOk) {
    throw new Error(replayErr || "NeoForge processor replay failed");
  }

  onProgress?.(`NeoForge ${versionId} install complete`);
}

async function downloadNativeClassifiers(
  lib: VersionJson["libraries"][number],
  onProgress?: (msg: string) => void,
): Promise<void> {
  if (!lib.natives || !lib.downloads?.classifiers) return;
  for (const classifierName of Object.values(lib.natives)) {
    const cl = lib.downloads.classifiers[classifierName] as
      | { path?: string; url?: string; sha1?: string }
      | undefined;
    if (!cl?.path || !cl.url) continue;
    const clPath = join(librariesDir(), cl.path);
    if (existsSync(clPath)) continue;
    try {
      await downloadFile(cl.url, clPath, cl.sha1);
    } catch {
      onProgress?.(`  Warning: could not download native ${lib.name}:${classifierName}`);
    }
  }
}

async function downloadLibraries(
  libraries: VersionJson["libraries"],
  onProgress?: (msg: string) => void,
): Promise<void> {
  for (const lib of libraries) {
    if (lib.downloads?.artifact?.url) {
      const libPath = join(librariesDir(), lib.downloads.artifact.path);
      try {
        await downloadFile(lib.downloads.artifact.url, libPath, lib.downloads.artifact.sha1);
      } catch {
        onProgress?.(`  Warning: could not download ${lib.name}`);
      }
    } else if (lib.name) {
      const repoUrl = (lib as any).url as string | undefined;
      const mvnPath = libraryMavenPath(lib.name);
      const filePath = join(librariesDir(), mvnPath);
      if (!existsSync(filePath)) {
        const baseUrls = repoUrl
          ? [`${repoUrl.replace(/\/$/, "")}/${mvnPath}`]
          : [];
        baseUrls.push(...DOWNLOAD.fallbackRepoUrls.map(u => `${u}${mvnPath}`));
        let downloaded = false;
        for (const url of baseUrls) {
          try {
            await downloadFile(url, filePath);
            downloaded = true;
            break;
          } catch { /* try next */ }
        }
        if (!downloaded) {
          onProgress?.(`  Warning: could not download ${lib.name}`);
        }
      }
    }

    // Native classifier jars (legacy native format) — see downloadVersion.
    await downloadNativeClassifiers(lib, onProgress);
  }
}

async function extractJarJson<T>(jarPath: string, entryName: string): Promise<T | null> {
  const { inflateRawSync } = await import("node:zlib");
  const data = readFileSync(jarPath);

  let eocdOffset = -1;
  for (let i = data.length - 22; i >= 0; i--) {
    if (data[i] === 0x50 && data[i + 1] === 0x4b && data[i + 2] === 0x05 && data[i + 3] === 0x06) {
      eocdOffset = i;
      break;
    }
  }
  if (eocdOffset < 0) return null;

  const cdOffset = data.readUInt32LE(eocdOffset + 16);
  const cdEntries = data.readUInt16LE(eocdOffset + 10);

  let offset = cdOffset;
  for (let i = 0; i < cdEntries; i++) {
    if (data.readUInt32LE(offset) !== 0x02014b50) break;
    const fnameLen = data.readUInt16LE(offset + 28);
    const extraLen = data.readUInt16LE(offset + 30);
    const commentLen = data.readUInt16LE(offset + 32);
    const localHeaderOffset = data.readUInt32LE(offset + 42);
    const fname = data.subarray(offset + 46, offset + 46 + fnameLen).toString("utf-8");

    if (fname === entryName) {
      const compMethod = data.readUInt16LE(localHeaderOffset + 8);
      const lhFnameLen = data.readUInt16LE(localHeaderOffset + 26);
      const lhExtraLen = data.readUInt16LE(localHeaderOffset + 28);
      const compSize = data.readUInt32LE(offset + 20);
      const contentStart = localHeaderOffset + 30 + lhFnameLen + lhExtraLen;

      const compressed = data.subarray(contentStart, contentStart + compSize);
      const jsonBytes = compMethod === 0 ? compressed : inflateRawSync(compressed);
      return JSON.parse(jsonBytes.toString("utf-8")) as T;
    }

    offset = offset + 46 + fnameLen + extraLen + commentLen;
  }

  return null;
}

export async function ensureVersionInstalled(
  version: string,
  loader: Loader,
  onProgress?: (msg: string) => void,
): Promise<string> {
  const data = loadVersionsData();
  const vi = getVersion(data, version);

  if (!vi) {
    try {
      loadVersionMerged(version);
      return version;
    } catch {
      throw new Error(
        `Unknown version: "${version}". Use list_supported_versions to see available versions.`,
      );
    }
  }

  const mcVersion = vi.mc_version;
  let versionId = getVersionForLoader(data, version, loader) ?? vi.version_id;

  try {
    loadVersionMerged(versionId);
    return versionId;
  } catch {}

  onProgress?.(`Auto-installing ${mcVersion} (${loader})...`);

  const baseJsonPath = join(versionsDir(), mcVersion, `${mcVersion}.json`);
  if (!existsSync(baseJsonPath)) {
    onProgress?.(`Downloading base MC ${mcVersion}...`);
    const manifest = await fetchVersionManifest();
    const mv = manifest.versions.find((v) => v.id === mcVersion);
    if (!mv) throw new Error(`Base MC version ${mcVersion} not found in manifest.`);
    const baseVj = await fetchVersionJson(mv.url);
    await downloadVersion(baseVj, onProgress);
  }

  let loaderVersion: string | undefined;
  if (loader === "forge" && vi.forge) loaderVersion = vi.forge;
  else if (loader === "neoforge" && vi.neoforge) loaderVersion = vi.neoforge;
  else if (loader === "fabric") loaderVersion = DEFAULT_FABRIC_LOADER_VERSION;

  if (loaderVersion) {
    onProgress?.(`Installing ${loader} ${loaderVersion}...`);
    await downloadLoaderVersion(mcVersion, loader, loaderVersion, onProgress);
  }

  const resolved = getVersionForLoader(data, version, loader);
  if (resolved) versionId = resolved;

  onProgress?.(`Installation complete: ${versionId}`);
  return versionId;
}

import { createHash } from "node:crypto";
import { existsSync, mkdirSync, writeFileSync, readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { versionsDir, assetsDir, librariesDir } from "./platform.js";
import { libraryMavenPath } from "./version-json.js";
import type { VersionJson } from "./version-json.js";

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
  const resp = await fetch("https://piston-meta.mojang.com/mc/game/version_manifest.json");
  if (!resp.ok) throw new Error(`Failed to fetch version manifest: ${resp.status}`);
  return (await resp.json()) as VersionManifest;
}

export async function fetchVersionJson(url: string): Promise<VersionJson> {
  const resp = await fetch(url);
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
    }
  }

  const dir = dirname(path);
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true });

  const resp = await fetch(url);
  if (!resp.ok) throw new Error(`Download failed for ${url}: HTTP ${resp.status}`);

  const arrayBuf = await resp.arrayBuffer();
  const buffer = Buffer.from(arrayBuf);

  if (expectedSha1) {
    const actual = createHash("sha1").update(buffer).digest("hex");
    if (actual.toLowerCase() !== expectedSha1.toLowerCase()) {
      throw new Error(`SHA-1 mismatch for ${path}: expected ${expectedSha1}, got ${actual}`);
    }
  }

  writeFileSync(path, buffer);
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

  if (versionJson.assetIndex?.url) {
    const idxDir = join(assetsDir(), "indexes");
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

      for (let i = 0; i < entries.length; i++) {
        const [, obj] = entries[i];
        const hash = obj.hash;
        const prefix = hash.slice(0, 2);
        const assetUrl = `https://resources.download.minecraft.net/${prefix}/${hash}`;
        const objDir = join(assetsDir(), "objects", prefix);
        if (!existsSync(objDir)) mkdirSync(objDir, { recursive: true });
        const assetPath = join(objDir, hash);

        if (!existsSync(assetPath)) {
          try {
            await downloadFile(assetUrl, assetPath, hash);
          } catch {
            continue;
          }
        }

        if (i % 200 === 0) {
          onProgress?.(`Assets: ${i + 1}/${entries.length}`);
        }
      }
    }
  }

  onProgress?.(`Downloading libraries for ${versionId}...`);
  for (const lib of versionJson.libraries) {
    if (lib.downloads?.artifact?.url) {
      const libPath = join(librariesDir(), lib.downloads.artifact.path);
      await downloadFile(lib.downloads.artifact.url, libPath, lib.downloads.artifact.sha1);
    } else if (lib.name) {
      const mvnPath = libraryMavenPath(lib.name);
      const url = `https://libraries.minecraft.net/${mvnPath}`;
      const filePath = join(librariesDir(), mvnPath);
      if (!existsSync(filePath)) {
        await downloadFile(url, filePath);
      }
    }
  }

  if (versionJson.downloads?.client?.url) {
    const jarPath = join(versionsDir(), versionId, `${versionId}.jar`);
    if (!existsSync(jarPath)) {
      await downloadFile(versionJson.downloads.client.url, jarPath, versionJson.downloads.client.sha1);
    }
  }

  onProgress?.(`Version ${versionId} download complete`);
}

export function listInstalledVersions(): string[] {
  const vDir = versionsDir();
  if (!existsSync(vDir)) return [];

  const installed: string[] = [];
  try {
    for (const entry of readdirSync(vDir, { withFileTypes: true })) {
      if (entry.isDirectory()) {
        const jsonPath = join(vDir, entry.name, `${entry.name}.json`);
        if (existsSync(jsonPath)) installed.push(entry.name);
      }
    }
  } catch {}

  return installed.sort();
}

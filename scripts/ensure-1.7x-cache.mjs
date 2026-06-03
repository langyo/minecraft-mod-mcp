#!/usr/bin/env node
// ensure-1.7x-cache.mjs — Download pre-built 1.7.x Gradle cache from GitHub Release
// Usage: node scripts/ensure-1.7x-cache.mjs
// Extracts to ~/.gradle/caches/minecraft/ if not already present

import { existsSync, mkdirSync, createWriteStream, readdirSync } from "node:fs";
import { join, resolve } from "node:path";
import { homedir } from "node:os";
import { pipeline } from "node:stream/promises";
import { Readable } from "node:stream";
import { createUnzip } from "node:zlib";
import { execSync } from "node:child_process";

const CACHE_TAG = "cache-1.7x-gradle";
const REPO = "langyo/minecraft-mcp";
const CACHE_FILE = "mc-17x-gradle-cache.zip";
const MC_CACHE_DIR = join(homedir(), ".gradle", "caches", "minecraft");

const MARKER_DIRS = [
  "net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10",
  "net/minecraftforge/forge/1.7.2-10.12.1.1109",
];

function isCachePresent() {
  return MARKER_DIRS.every(d => {
    const p = join(MC_CACHE_DIR, d);
    return existsSync(p) && readdirSync(p).length > 0;
  });
}

async function downloadFile(url, dest) {
  const res = await fetch(url, { redirect: "follow" });
  if (!res.ok) throw new Error(`Download failed: ${res.status} ${res.statusText}`);
  const file = createWriteStream(dest);
  await pipeline(Readable.fromWeb(res.body), file);
}

async function main() {
  if (isCachePresent()) {
    console.log("[cache-1.7x] Gradle cache already present, skipping download.");
    return;
  }

  console.log("[cache-1.7x] Gradle cache not found. Downloading from GitHub Release...");

  mkdirSync(MC_CACHE_DIR, { recursive: true });

  const tmpZip = join(MC_CACHE_DIR, "__cache_download.zip");

  const releaseUrl = `https://github.com/${REPO}/releases/download/${CACHE_TAG}/${CACHE_FILE}`;
  console.log(`[cache-1.7x] URL: ${releaseUrl}`);

  await downloadFile(releaseUrl, tmpZip);
  console.log(`[cache-1.7x] Downloaded ${(existsSync(tmpZip) ? "ok" : "FAILED")}`);

  console.log("[cache-1.7x] Extracting...");
  execSync(`powershell -Command "Expand-Archive -Path '${tmpZip}' -DestinationPath '${MC_CACHE_DIR}' -Force"`, {
    stdio: "inherit",
    windowsHide: true,
  });

  if (existsSync(tmpZip)) {
    const { unlinkSync } = await import("node:fs");
    unlinkSync(tmpZip);
  }

  if (isCachePresent()) {
    console.log("[cache-1.7x] Cache extracted successfully.");
  } else {
    console.error("[cache-1.7x] WARNING: Cache extraction may have failed. Check manually.");
  }
}

main().catch(e => {
  console.error("[cache-1.7x] Error:", e.message);
  process.exit(1);
});

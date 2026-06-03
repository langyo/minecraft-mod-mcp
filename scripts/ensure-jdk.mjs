#!/usr/bin/env node
// ensure-jdk.mjs — Auto-download JDK to .jdks/ if not found locally
// Usage: node scripts/ensure-jdk.mjs <major-version> [--print-home|--print-exec|--json]

import { existsSync, mkdirSync, readdirSync, writeFileSync, unlinkSync, renameSync, readFileSync as fsReadFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { execSync } from "node:child_process";
import { createWriteStream } from "node:fs";
import { platform } from "node:os";
import _https from "node:https";
import _http from "node:http";

const PROJECT_ROOT = resolve(import.meta.dirname, "..");
const JDKS_DIR = join(PROJECT_ROOT, ".jdks");

const ADOPTIUM_API = "https://api.adoptium.net/v3/binary/latest";

function getOs() {
  const p = platform();
  if (p === "win32") return "windows";
  if (p === "darwin") return "mac";
  return "linux";
}

function getArch() {
  if (process.arch === "arm64") return "aarch64";
  return "x64";
}

function getExt() {
  return platform() === "win32" ? "zip" : "tar.gz";
}

function javaExeName() {
  return platform() === "win32" ? "java.exe" : "java";
}

function checkJavaVersion(path) {
  try {
    const out = execSync(`"${path}" -version 2>&1`, { encoding: "utf-8", timeout: 5000 });
    const match = out.match(/"(\d+)(?:\.(\d+))?/);
    if (match) return parseInt(match[1]) === 1 ? parseInt(match[2]) : parseInt(match[1]);
  } catch {}
  return null;
}

function findExistingJdk(majorVersion) {
  const searchDirs = [
    JDKS_DIR,
    join(process.env.HOME || process.env.USERPROFILE || "", ".gradle", "jdks"),
    ...(platform() === "win32"
      ? ["C:\\Program Files\\Java", "C:\\Program Files\\Amazon Corretto", "C:\\Program Files\\Eclipse Adoptium", "C:\\Program Files\\Eclipse Temurin"]
      : ["/usr/lib/jvm", "/Library/Java/JavaVirtualMachines"]),
  ];

  for (const base of searchDirs) {
    if (!existsSync(base)) continue;
    for (const entry of readdirSync(base, { withFileTypes: true })) {
      if (!entry.isDirectory()) continue;
      const exe = join(base, entry.name, "bin", javaExeName());
      if (existsSync(exe)) {
        const v = checkJavaVersion(exe);
        if (v === majorVersion) return join(base, entry.name);
      }
    }
  }

  // Check env var
  const envHome = process.env[`JDK_${majorVersion}_HOME`];
  if (envHome && existsSync(join(envHome, "bin", javaExeName()))) return envHome;

  // Check system PATH
  try {
    const javaPath = execSync(platform() === "win32" ? "where java" : "which java", { encoding: "utf-8" }).trim().split("\n")[0].trim();
    const v = checkJavaVersion(javaPath);
    if (v === majorVersion) return "system";
  } catch {}

  return null;
}

function downloadFile(url, dest) {
  return new Promise((resolve, reject) => {
    const follow = (url, tries = 0) => {
      if (tries > 15) return reject(new Error("Too many redirects"));
      const mod = url.startsWith("https") ? _https : _http;
      mod.get(url, { headers: { "User-Agent": "minecraft-mcp" } }, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          res.resume();
          follow(res.headers.location, tries + 1);
          return;
        }
        if (res.statusCode !== 200) {
          res.resume();
          reject(new Error(`HTTP ${res.statusCode} from ${url}`));
          return;
        }
        const total = parseInt(res.headers["content-length"] || "0");
        let downloaded = 0;
        const stream = createWriteStream(dest);
        res.on("data", (chunk) => {
          downloaded += chunk.length;
          if (total > 0) {
            const pct = Math.round(downloaded / total * 100);
            const mb = Math.round(downloaded / 1048576);
            process.stdout.write(`\r[ensure-jdk] ${pct}% (${mb} MB)   `);
          }
        });
        res.pipe(stream);
        stream.on("finish", () => { console.log(""); resolve(); });
        stream.on("error", reject);
      }).on("error", reject);
    };
    follow(url);
  });
}

function extractArchive(archivePath, destDir) {
  if (archivePath.endsWith(".zip")) {
    // Use PowerShell on Windows, unzip on others
    if (platform() === "win32") {
      execSync(`powershell -NoProfile -Command "Expand-Archive -LiteralPath '${archivePath}' -DestinationPath '${destDir}' -Force"`, { stdio: "inherit" });
    } else {
      execSync(`unzip -q -o "${archivePath}" -d "${destDir}"`, { stdio: "inherit" });
    }
  } else {
    execSync(`tar -xzf "${archivePath}" -C "${destDir}"`, { stdio: "inherit" });
  }
}

async function downloadJdk(majorVersion) {
  mkdirSync(JDKS_DIR, { recursive: true });

  const stableName = `jdk-${majorVersion}`;
  const stablePath = join(JDKS_DIR, stableName);
  if (existsSync(join(stablePath, "bin", javaExeName()))) {
    console.log(`[ensure-jdk] JDK ${majorVersion} already in .jdks/: ${stablePath}`);
    return stablePath;
  }

  const os = getOs();
  const arch = getArch();
  const ext = getExt();
  const url = `${ADOPTIUM_API}/${majorVersion}/ga/${os}/${arch}/jdk/hotspot/normal/eclipse`;
  const archiveName = `jdk-${majorVersion}-${os}-${arch}.${ext}`;
  const archivePath = join(JDKS_DIR, archiveName);

  console.log(`[ensure-jdk] Downloading JDK ${majorVersion} (${os}-${arch})...`);
  await downloadFile(url, archivePath);

  console.log("[ensure-jdk] Extracting...");
  extractArchive(archivePath, JDKS_DIR);

  // Find the extracted directory and rename to stable name
  const before = new Set(readdirSync(JDKS_DIR).filter(n => !n.startsWith(".")));
  
  // The extracted dir will be something like jdk8u492-b09 or jdk-21.0.5+11
  const candidates = readdirSync(JDKS_DIR, { withFileTypes: true })
    .filter(e => e.isDirectory() && e.name !== stableName && e.name !== "jdk.properties")
    .filter(e => {
      const exe = join(JDKS_DIR, e.name, "bin", javaExeName());
      if (!existsSync(exe)) return false;
      const v = checkJavaVersion(exe);
      return v === majorVersion;
    });

  // Remove old stableName if exists but doesn't have java
  if (existsSync(stablePath)) {
    if (!existsSync(join(stablePath, "bin", javaExeName()))) {
      execSync(platform() === "win32" ? `rmdir /s /q "${stablePath}"` : `rm -rf "${stablePath}"`);
    }
  }

  if (candidates.length > 0 && !existsSync(stablePath)) {
    renameSync(join(JDKS_DIR, candidates[0].name), stablePath);
    console.log(`[ensure-jdk] Renamed ${candidates[0].name} -> ${stableName}`);
  }

  // Clean up archive
  try { if (existsSync(archivePath)) unlinkSync(archivePath); } catch {}

  if (!existsSync(join(stablePath, "bin", javaExeName()))) {
    const dirs = readdirSync(JDKS_DIR, { withFileTypes: true }).filter(e => e.isDirectory()).map(e => e.name);
    console.error(`[ensure-jdk] ERROR: JDK ${majorVersion} not found after extraction. Dirs: ${dirs.join(", ")}`);
    process.exit(1);
  }

  console.log(`[ensure-jdk] JDK ${majorVersion} installed: ${stablePath}`);
  return stablePath;
}

function writeJdkProps(version, home) {
  const propsPath = join(JDKS_DIR, "jdk.properties");
  const lines = [];
  if (existsSync(propsPath)) {
    const content = fsReadFileSync(propsPath, "utf-8");
    for (const line of content.split("\n")) {
      if (!line.startsWith(`jdk.${version}.home=`)) lines.push(line);
    }
  }
  lines.push(`jdk.${version}.home=${home.replace(/\\/g, "/")}`);
  writeFileSync(propsPath, lines.join("\n") + "\n");
}

async function main() {
  const args = process.argv.slice(2);
  if (args.length < 1 || args[0] === "--help") {
    console.error("Usage: node scripts/ensure-jdk.mjs <major-version> [--print-home|--print-exec|--json]");
    console.error("  --print-home  Print JAVA_HOME path");
    console.error("  --print-exec  Print java executable path");
    console.error("  --json        Print JSON with home, exec, version");
    process.exit(args.includes("--help") ? 0 : 1);
  }

  const version = parseInt(args[0]);
  if (isNaN(version) || version < 1) {
    console.error(`Invalid version: ${args[0]}`);
    process.exit(1);
  }

  const printHome = args.includes("--print-home");
  const printExec = args.includes("--print-exec");
  const json = args.includes("--json");

  const home = await ensureJdk(version);
  writeJdkProps(version, home);

  if (printHome) {
    process.stdout.write(home);
  } else if (printExec) {
    process.stdout.write(join(home, "bin", javaExeName()));
  } else if (json) {
    process.stdout.write(JSON.stringify({ version, home, exec: join(home, "bin", javaExeName()) }));
  } else {
    console.log(`[ensure-jdk] JDK ${version} ready: ${home}`);
  }
}

async function ensureJdk(version) {
  const existing = findExistingJdk(version);
  if (existing) {
    if (existing !== "system") console.log(`[ensure-jdk] JDK ${version} found: ${existing}`);
    else console.log(`[ensure-jdk] JDK ${version} found on system PATH`);
    return existing;
  }
  return downloadJdk(version);
}

main().catch(err => {
  console.error(`[ensure-jdk] Failed: ${err.message}`);
  process.exit(1);
});

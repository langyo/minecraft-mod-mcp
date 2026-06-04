import { readdirSync, rmSync, existsSync, statSync } from "node:fs";
import { join, resolve, dirname } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const PKG_DIR = resolve(__dirname, "..");
const ROOT = resolve(PKG_DIR, "..", "..");
const MODS_DIR = join(ROOT, "packages", "mods");
const CLI = join(PKG_DIR, "dist", "cli.js");

const filterArg = process.argv.find(a => a.startsWith("--filter="));
const filter = filterArg ? filterArg.split("=")[1] : null;

function cleanBuildCache(modDir) {
  const targets = ["build/libs", "build/classes", "build/tmp"];
  for (const t of targets) {
    const p = join(modDir, t);
    if (existsSync(p)) {
      rmSync(p, { recursive: true, force: true });
    }
  }
}

function getVersionsWithLoaders() {
  const versions = [];
  const entries = readdirSync(MODS_DIR, { withFileTypes: true });
  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    const ver = entry.name;
    const loaderEntries = readdirSync(join(MODS_DIR, ver), { withFileTypes: true });
    for (const le of loaderEntries) {
      if (!le.isDirectory()) continue;
      if (!existsSync(join(MODS_DIR, ver, le.name, "build.gradle"))) continue;
      versions.push({ version: ver, loader: le.name });
    }
  }
  return versions.sort((a, b) => {
    if (a.version !== b.version) return a.version.localeCompare(b.version, undefined, { numeric: true });
    return a.loader.localeCompare(b.loader);
  });
}

let combos = getVersionsWithLoaders();
if (filter) {
  combos = combos.filter(c => `${c.version}/${c.loader}`.includes(filter));
}

console.log(`=== SDK Build Test: ${combos.length} version/loader combos${filter ? ` (filter: ${filter})` : ""} ===\n`);

const results = [];
let pass = 0;
let fail = 0;

for (const { version, loader } of combos) {
  const modDir = join(MODS_DIR, version, loader);
  const label = `${version}/${loader}`;

  process.stdout.write(`  ${label.padEnd(30)}`);

  cleanBuildCache(modDir);

  const start = Date.now();
  const res = spawnSync("node", [CLI, "sdk", version, "--loader", loader], {
    cwd: ROOT,
    timeout: 10 * 60 * 1000,
    stdio: ["ignore", "pipe", "pipe"],
    env: { ...process.env },
  });
  const elapsed = ((Date.now() - start) / 1000).toFixed(1);

  const code = res.status ?? 1;
  const ok = code === 0;

  let jarSize = "";
  const libsDir = join(modDir, "build", "libs");
  if (existsSync(libsDir)) {
    const jars = readdirSync(libsDir).filter(f => f.endsWith(".jar") && !f.includes("sources"));
    if (jars.length > 0) {
      const sz = statSync(join(libsDir, jars[0])).size;
      jarSize = `${Math.round(sz / 1024)}KB`;
    }
  }

  if (ok) {
    pass++;
    console.log(`OK  ${elapsed}s  ${jarSize}`);
  } else {
    fail++;
    const stderr = res.stderr?.toString("utf-8") ?? "";
    const lastLines = stderr.split("\n").filter(l => l.trim()).slice(-3).join(" | ");
    console.log(`FAIL  ${elapsed}s  [${lastLines}]`);
  }

  results.push({ version, loader, ok, elapsed, jarSize, code });
}

console.log(`\n=== Results: ${pass} passed, ${fail} failed out of ${combos.length} ===\n`);

if (fail > 0) {
  console.log("Failed:");
  for (const r of results.filter(r => !r.ok)) {
    console.log(`  ${r.version}/${r.loader} (exit ${r.code})`);
  }
}

process.exit(fail > 0 ? 1 : 0);

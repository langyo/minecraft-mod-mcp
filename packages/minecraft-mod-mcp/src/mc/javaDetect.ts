import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { crossHomedir, isWindows, isMacos } from "../runtime/detector.js";

export interface JavaInfo {
  path: string;
  version: number;
  vendor: string;
  isJdk: boolean;
}

export function detectJavas(): JavaInfo[] {
  const results: JavaInfo[] = [];
  const seen = new Set<string>();

  const candidates = [
    ...envCandidates(),
    ...gradleCandidates(),
    ...platformCandidates(),
  ].filter((p) => existsSync(p));

  for (const home of candidates) {
    try {
      const canonical = home;
      if (seen.has(canonical)) continue;
      seen.add(canonical);
    } catch { continue; }

    const javaBin = isWindows()
      ? join(home, "bin", "java.exe")
      : join(home, "bin", "java");

    if (!existsSync(javaBin)) continue;

    const parsed = parseReleaseFile(home);
    if (parsed) {
      const isJdk = existsSync(join(home, "lib", "tools.jar")) || existsSync(join(home, "include"));
      results.push({ path: home, version: parsed.version, vendor: parsed.vendor, isJdk });
    }
  }

  results.sort((a, b) => b.version - a.version);
  return results;
}

function envCandidates(): string[] {
  const paths: string[] = [];
  for (const var_ of ["JAVA_HOME", "JDK_8_HOME", "JDK_16_HOME", "JDK_17_HOME", "JDK_21_HOME", "JDK_25_HOME"]) {
    const val = process.env[var_];
    if (val && existsSync(val)) paths.push(val);
  }
  return paths;
}

function gradleCandidates(): string[] {
  const paths: string[] = [];
  const jdksDir = join(crossHomedir(), ".gradle", "jdks");
  if (!existsSync(jdksDir)) return paths;

  try {
    for (const entry of readdirSync(jdksDir, { withFileTypes: true })) {
      if (entry.isDirectory() && !entry.name.includes("lock")) {
        const candidate = join(jdksDir, entry.name);
        if (existsSync(join(candidate, "release"))) paths.push(candidate);
      }
    }
  } catch {}

  return paths;
}

function platformCandidates(): string[] {
  if (isWindows()) return windowsCandidates();
  if (isMacos()) return macosCandidates();
  return linuxCandidates();
}

function windowsCandidates(): string[] {
  const paths: string[] = [];
  const roots = [
    join("C:", "Program Files", "Java"),
    join("C:", "Program Files", "Eclipse Adoptium"),
    join("C:", "Program Files", "Zulu"),
    join("C:", "Program Files", "Microsoft"),
    join("C:", "Program Files", "AdoptOpenJDK"),
    join("C:", "Program Files (x86)", "Java"),
  ];

  for (const root of roots) {
    scanSubdirs(root, paths);
  }

  const userProfile = process.env.USERPROFILE;
  if (userProfile) {
    for (const sub of [join("scoop", "apps", "java"), join("scoop", "apps", "openjdk")]) {
      const dir = join(userProfile, sub);
      if (existsSync(dir)) scanSubdirsDirect(dir, paths);
    }
  }

  return paths;
}

function macosCandidates(): string[] {
  const paths: string[] = [];
  const jvms = "/Library/Java/JavaVirtualMachines";
  if (!existsSync(jvms)) return paths;
  try {
    for (const entry of readdirSync(jvms, { withFileTypes: true })) {
      const home = join(jvms, entry.name, "Contents", "Home");
      if (existsSync(home)) paths.push(home);
    }
  } catch {}
  return paths;
}

function linuxCandidates(): string[] {
  const paths: string[] = [];
  for (const root of ["/usr/lib/jvm", "/usr/java", "/usr/local/java", "/opt/java", "/opt/jdk"]) {
    if (!existsSync(root)) continue;
    try {
      for (const entry of readdirSync(root, { withFileTypes: true })) {
        const p = join(root, entry.name);
        if (entry.isDirectory() && (existsSync(join(p, "release")) || existsSync(join(p, "bin", "java")))) {
          paths.push(p);
        }
      }
    } catch {}
  }
  return paths;
}

function scanSubdirs(root: string, paths: string[]): void {
  if (!existsSync(root)) return;
  try {
    for (const entry of readdirSync(root, { withFileTypes: true })) {
      const p = join(root, entry.name);
      if (!entry.isDirectory()) continue;
      if (existsSync(join(p, "release"))) paths.push(p);
      else scanSubdirsDirect(p, paths);
    }
  } catch {}
}

function scanSubdirsDirect(dir: string, paths: string[]): void {
  try {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const p = join(dir, entry.name);
      if (!entry.isDirectory()) continue;
      if (existsSync(join(p, "release"))) paths.push(p);
      else scanSubdirsDirect(p, paths);
    }
  } catch {}
}

function parseReleaseFile(home: string): { version: number; vendor: string } | null {
  const releasePath = join(home, "release");
  if (!existsSync(releasePath)) return null;
  try {
    const content = readFileSync(releasePath, "utf-8");
    let javaVersion: string | null = null;
    let implementor = "Unknown";

    for (const line of content.split("\n")) {
      const trimmed = line.trim();
      if (trimmed.startsWith("JAVA_VERSION=")) {
        javaVersion = stripQuotes(trimmed.slice("JAVA_VERSION=".length));
      } else if (trimmed.startsWith("IMPLEMENTOR=")) {
        implementor = stripQuotes(trimmed.slice("IMPLEMENTOR=".length));
      }
    }

    if (!javaVersion) return null;
    const major = parseJavaMajorVersion(javaVersion);
    if (major === null) return null;
    return { version: major, vendor: implementor };
  } catch {
    return null;
  }
}

function stripQuotes(s: string): string {
  const t = s.trim();
  if ((t.startsWith('"') && t.endsWith('"')) || (t.startsWith("'") && t.endsWith("'"))) {
    return t.slice(1, -1);
  }
  return t;
}

function parseJavaMajorVersion(version: string): number | null {
  const parts = version.split(".");
  if (parts.length === 0) return null;
  if (parts[0] === "1" && parts.length >= 2) return parseInt(parts[1], 10) || null;
  return parseInt(parts[0], 10) || null;
}

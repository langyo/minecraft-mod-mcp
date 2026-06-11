import { join, resolve } from "node:path";
import { existsSync, readdirSync } from "node:fs";
import { crossHomedir, isWindows, isMacos, classpathSeparator as cpSep } from "../runtime/detector.js";
import { detectJavas } from "./javaDetect.js";
import { PATHS, JAVA } from "./defaults.js";

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

export function modJarPath(mcVersion: string, loader: string): string | null {
  const projectDir = resolve("packages", "mods", mcVersion, loader);
  const libsDir = join(projectDir, "build", "libs");
  if (!existsSync(libsDir)) return null;

  try {
    const jars = readdirSync(libsDir).filter(f => f.endsWith(".jar") && !f.endsWith("-sources.jar") && !f.endsWith("-javadoc.jar"));
    if (jars.length > 0) {
      jars.sort((a, b) => b.length - a.length);
      return join(libsDir, jars[0]);
    }
  } catch {}

  return null;
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

import { join, resolve } from "node:path";
import { existsSync, readdirSync } from "node:fs";
import { crossHomedir, isWindows, isMacos, classpathSeparator as cpSep } from "../runtime/detector.js";
import { detectJavas } from "./java-detect.js";

function homedir(): string {
  return crossHomedir();
}

export function mcDir(): string {
  return join(homedir(), ".minecraft");
}

export function versionsDir(): string {
  return join(mcDir(), "versions");
}

export function librariesDir(): string {
  return join(mcDir(), "libraries");
}

export function assetsDir(): string {
  return join(mcDir(), "assets");
}

export function nativesDir(versionId: string): string {
  return join(mcDir(), "versions", versionId, "natives");
}

export function launcherDir(): string {
  return join(mcDir(), "mcp_launcher");
}

export function modJarPath(mcVersion: string, loader: string): string {
  const projectDir = resolve("packages", "mods", mcVersion, loader);
  if (loader === "fabric") {
    return join(projectDir, "build", "libs", `mcpmod-fabric-${mcVersion}-1.0.0.jar`);
  }
  return join(projectDir, "build", "libs", `mcpmod-${loader}-${mcVersion}-1.0.0.jar`);
}

export function jdkHome(javaVersion: number): string | null {
  const envVar = `JDK_${javaVersion}_HOME`;
  const envVal = process.env[envVar];
  if (envVal && existsSync(envVal)) return envVal;

  const jdksDir = join(homedir(), ".gradle", "jdks");
  if (!existsSync(jdksDir)) return null;

  const prefixes: Record<number, string> = {
    8: "eclipse_adoptium-8",
    17: "eclipse_adoptium-17",
    21: "eclipse_adoptium-21",
  };

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
  const exact = sorted.find((j) => j.version === targetVersion);
  if (exact) return exact.path;

  const higher = sorted.find((j) => j.version >= targetVersion);
  if (higher) return higher.path;

  if (sorted.length > 0) return sorted[sorted.length - 1].path;

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

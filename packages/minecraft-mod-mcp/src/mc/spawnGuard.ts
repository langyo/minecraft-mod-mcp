import { basename } from "node:path";

/** Executable basenames the launcher is allowed to spawn (security allowlist). */
export const JAVA_ALLOWLIST = ["java", "javaw", "java.exe", "javaw.exe"];
export const GRADLEW_ALLOWLIST = ["gradlew", "gradlew.bat"];
export const SERVER_SCRIPT_ALLOWLIST = ["run.bat", "run.sh"];

/**
 * Allowlist guard for spawned programs: config/user-controlled paths must
 * never reach spawn() unchecked. Throws a clear Error when basename(p) is
 * not one of `allowed`.
 */
export function assertAllowedProgram(p: string, allowed: readonly string[]): void {
  const name = basename(p).toLowerCase();
  if (!allowed.includes(name)) {
    throw new Error(`Refusing to spawn "${p}": "${name}" is not an allowed program (${allowed.join(", ")})`);
  }
}

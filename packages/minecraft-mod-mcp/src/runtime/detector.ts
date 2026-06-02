export type Runtime = "node" | "deno" | "bun";

let _runtime: Runtime | null = null;

export function detectRuntime(): Runtime {
  if (_runtime) return _runtime;
  // @ts-ignore
  if (typeof globalThis.Deno !== "undefined") { _runtime = "deno"; return "deno"; }
  // @ts-ignore
  if (typeof globalThis.Bun !== "undefined") { _runtime = "bun"; return "bun"; }
  _runtime = "node";
  return "node";
}

export function getRuntimeName(): string {
  return detectRuntime();
}

export function crossHomedir(): string {
  const env = process.env;
  if (env.HOME) return env.HOME;
  if (env.USERPROFILE) return env.USERPROFILE;
  // @ts-ignore
  if (typeof globalThis.Deno !== "undefined") {
    // @ts-ignore
    return globalThis.Deno.env.get("HOME") || globalThis.Deno.env.get("USERPROFILE") || "/";
  }
  return "/";
}

export function isWindows(): boolean {
  return process.platform === "win32";
}

export function isMacos(): boolean {
  return process.platform === "darwin";
}

export function classpathSeparator(): string {
  return process.platform === "win32" ? ";" : ":";
}

export type Runtime = "node" | "deno" | "bun";

let _runtime: Runtime | null = null;

export function detectRuntime(): Runtime {
  if (_runtime) return _runtime;
  // @ts-ignore
  if (typeof Deno !== "undefined") { _runtime = "deno"; return "deno"; }
  // @ts-ignore
  if (typeof Bun !== "undefined") { _runtime = "bun"; return "bun"; }
  _runtime = "node";
  return "node";
}

export function getRuntimeName(): string {
  return detectRuntime();
}

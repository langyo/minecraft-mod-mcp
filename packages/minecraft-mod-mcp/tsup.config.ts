import { defineConfig } from "tsup";
import { readFileSync, writeFileSync } from "node:fs";

const nodeBuiltins = [
  "assert", "buffer", "child_process", "cluster", "console", "constants",
  "crypto", "dgram", "dns", "domain", "events", "fs", "http", "http2",
  "https", "module", "net", "os", "path", "perf_hooks", "process", "punycode",
  "querystring", "readline", "repl", "stream", "string_decoder", "sys",
  "timers", "tls", "tty", "url", "util", "v8", "vm", "worker_threads", "zlib",
];

function patchNodePrefixPlugin() {
  return {
    name: "patch-node-prefix",
    setup(build: any) {
      build.onEnd((result: any) => {
        for (const file of result.outputFiles ?? []) {
          let text = file.text;
          for (const mod of nodeBuiltins) {
            text = text.replace(
              new RegExp(`(from\\s*["'])${mod}(["'])`, "g"),
              `$1node:${mod}$2`
            );
          }
          file.contents = new TextEncoder().encode(text);
        }
      });
    },
  };
}

export default defineConfig([
  {
    entry: ["src/cli.ts"],
    format: ["esm"],
    dts: false,
    clean: true,
    shims: true,
    banner: { js: "#!/usr/bin/env node" },
    platform: "node",
    target: "node20",
    noExternal: [/.*/],
    esbuildPlugins: [patchNodePrefixPlugin()],
  },
  {
    entry: ["src/index.ts"],
    format: ["esm", "cjs"],
    dts: true,
    clean: false,
    shims: true,
    platform: "node",
    target: "node20",
  },
]);

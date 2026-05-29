import { defineConfig } from "tsup";

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

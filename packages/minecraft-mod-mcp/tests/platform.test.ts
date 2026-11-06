import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, readdirSync, rmSync, utimesSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { modVersionFromJar, modJarPath, deployModJar } from "../src/mc/platform.js";
import { PACKAGE_VERSION } from "../src/mc/defaults.js";

describe("PACKAGE_VERSION", () => {
  it("is read from package.json, not a stale constant (issue #81)", () => {
    expect(PACKAGE_VERSION).toMatch(/^\d+\.\d+\.\d+/);
    expect(PACKAGE_VERSION).not.toBe("0.2.1");
    expect(PACKAGE_VERSION).not.toBe("0.0.0");
  });
});

describe("modVersionFromJar", () => {
  it("parses local build, release and snapshot names", () => {
    expect(modVersionFromJar("minecraft-moddev-mcp-mod-0.4.21.jar")).toBe("0.4.21");
    expect(modVersionFromJar("minecraft-moddev-mcp-fabric-1.21.7-0.4.18.jar")).toBe("0.4.18");
    expect(modVersionFromJar("/cache/minecraft-mcp-1.21.7-forge-v0.4.26.jar")).toBe("0.4.26");
    expect(modVersionFromJar("minecraft-moddev-mcp-forge-1.21.7-0.2.0-SNAPSHOT.jar")).toBe("0.2.0-SNAPSHOT");
  });

  it("never mistakes the MC version of an unversioned name for the mod version", () => {
    expect(modVersionFromJar("minecraft-mcp-1.12.2-forge.jar")).toBeNull();
    expect(modVersionFromJar("minecraft-mcp-26.1.2-neoforge.jar")).toBeNull();
  });
});

describe("modJarPath", () => {
  let root: string;
  let cwd: string;

  beforeEach(() => {
    cwd = process.cwd();
    root = mkdtempSync(join(tmpdir(), "mcp-platform-"));
    process.chdir(root);
  });

  afterEach(() => {
    process.chdir(cwd);
    rmSync(root, { recursive: true, force: true });
  });

  function libs(mc: string, loader: string, names: string[]): string {
    const dir = join(root, "packages", "mods", mc, loader, "build", "libs");
    mkdirSync(dir, { recursive: true });
    for (const n of names) writeFileSync(join(dir, n), "jar");
    return dir;
  }

  it("prefers the highest version over the longest name", () => {
    const dir = libs("1.12.2", "forge", [
      "minecraft-moddev-mcp-mod-0.2.1.jar",
      "minecraft-moddev-mcp-mod-0.4.17.jar",
      "minecraft-moddev-mcp-mod-0.4.21.jar",
      "minecraft-moddev-mcp-mod-0.4.21-sources.jar",
    ]);
    expect(modJarPath("1.12.2", "forge")).toBe(join(dir, "minecraft-moddev-mcp-mod-0.4.21.jar"));
  });

  it("prefers the jar matching the bridge version when present", () => {
    const dir = libs("1.21.7", "fabric", [
      `minecraft-moddev-mcp-fabric-1.21.7-${PACKAGE_VERSION}.jar`,
      "minecraft-moddev-mcp-fabric-1.21.7-99.0.0.jar",
    ]);
    expect(modJarPath("1.21.7", "fabric")).toBe(join(dir, `minecraft-moddev-mcp-fabric-1.21.7-${PACKAGE_VERSION}.jar`));
  });

  it("ranks a release above its snapshot", () => {
    const dir = libs("1.21.7", "forge", [
      "minecraft-moddev-mcp-forge-1.21.7-0.5.0-SNAPSHOT.jar",
      "minecraft-moddev-mcp-forge-1.21.7-0.5.0.jar",
    ]);
    expect(modJarPath("1.21.7", "forge")).toBe(join(dir, "minecraft-moddev-mcp-forge-1.21.7-0.5.0.jar"));
  });
});

describe("deployModJar", () => {
  let root: string;

  beforeEach(() => {
    root = mkdtempSync(join(tmpdir(), "mcp-deploy-"));
  });

  afterEach(() => {
    rmSync(root, { recursive: true, force: true });
  });

  it("replaces stale MCP mod copies but leaves other mods alone", () => {
    const mods = join(root, "mods");
    mkdirSync(mods);
    writeFileSync(join(mods, "minecraft-mcp-1.21.7-forge.jar"), "old");
    writeFileSync(join(mods, "minecraft-moddev-mcp-forge-1.21.7-0.4.18.jar"), "old");
    writeFileSync(join(mods, "jei-1.21.7.jar"), "other");
    const src = join(root, "minecraft-mcp-1.21.7-forge-v0.4.27.jar");
    writeFileSync(src, "new");

    deployModJar(mods, src);

    expect(readdirSync(mods).sort()).toEqual(["jei-1.21.7.jar", "minecraft-mcp-1.21.7-forge-v0.4.27.jar"]);
  });

  it("only treats this project's own jar names as stale copies", () => {
    const mods = join(root, "mods");
    mkdirSync(mods);
    writeFileSync(join(mods, "minecraft-mcp-server-plugin.jar"), "third party");
    const src = join(root, "minecraft-mcp-1.21.7-forge-v0.4.27.jar");
    writeFileSync(src, "new");

    deployModJar(mods, src);

    expect(readdirSync(mods).sort()).toEqual(["minecraft-mcp-1.21.7-forge-v0.4.27.jar", "minecraft-mcp-server-plugin.jar"]);
  });

  it("refreshes a same-named jar only when the source is newer", () => {
    const mods = join(root, "mods");
    const src = join(root, "minecraft-moddev-mcp-mod-0.4.27.jar");
    writeFileSync(src, "first");
    const dest = deployModJar(mods, src);

    writeFileSync(src, "rebuilt");
    utimesSync(src, new Date(0), new Date(0));
    deployModJar(mods, src);
    expect(readFileSync(dest, "utf-8")).toBe("first");

    const future = new Date(Date.now() + 60_000);
    utimesSync(src, future, future);
    deployModJar(mods, src);
    expect(readFileSync(dest, "utf-8")).toBe("rebuilt");
  });
});

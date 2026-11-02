/**
 * Outbound-URL guard: every download/API request whose URL is built at
 * runtime (version manifests, JDK assets, GitHub release assets, OAuth
 * endpoints) must pass {@link assertSafeUrl} before it is fetched.
 * Mirrors scripts/netsec.py on the Python side — keep the two host sets
 * in sync when adding a download source.
 */
export const ALLOWED_URL_HOSTS: ReadonlySet<string> = new Set([
  // Mojang / Minecraft
  "piston-meta.mojang.com", "piston-data.mojang.com",
  "launchermeta.mojang.com", "libraries.minecraft.net",
  "resources.download.minecraft.net", "sessionserver.mojang.com", "api.mojang.com",
  // Forge / NeoForge / Fabric
  "maven.minecraftforge.net", "files.minecraftforge.net",
  "maven.neoforged.net", "neoforged.forgecdn.net",
  "maven.fabricmc.net", "meta.fabricmc.net",
  // Server tooling (BuildTools / Paper API)
  "hub.spigotmc.org", "api.papermc.io",
  // GitHub (self-update releases, gradle caches)
  "api.github.com", "github.com", "objects.githubusercontent.com",
  "codeload.github.com", "raw.githubusercontent.com",
  // JDK
  "api.adoptium.net",
  // Build deps
  "repo1.maven.org", "repo.maven.apache.org", "services.gradle.org", "jitpack.io",
  // Microsoft / Minecraft auth (device-code login chain)
  "login.microsoftonline.com", "login.live.com",
  "user.auth.xboxlive.com", "xsts.auth.xboxlive.com", "api.minecraftservices.com",
  // Mirrors (parity with scripts/mirrors.py groups)
  "mirrors.aliyun.com", "maven.aliyun.com",
  "mirrors.cloud.tencent.com", "repo.huaweicloud.com",
]);

export class UnsafeUrlError extends Error {}

/** Throws {@link UnsafeUrlError} unless url is HTTPS, allowlisted,
 *  credential-free and on the standard port. Returns the url on success. */
export function assertSafeUrl(url: string): string {
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    throw new UnsafeUrlError(`invalid URL: ${url}`);
  }
  if (parsed.protocol !== "https:") {
    throw new UnsafeUrlError(`non-HTTPS scheme not allowed: ${url}`);
  }
  if (parsed.username || parsed.password) {
    throw new UnsafeUrlError(`credentials in URL are not allowed: ${url}`);
  }
  if (parsed.port !== "" && parsed.port !== "443") {
    throw new UnsafeUrlError(`non-standard port not allowed: ${url}`);
  }
  const host = parsed.hostname.toLowerCase();
  // Raw-IP hosts (loopback/private/link-local/metadata) bypass hostname
  // reputation entirely — reject them, same policy as scripts/netsec.py.
  if (/^\d{1,3}(\.\d{1,3}){3}$/.test(host) || host.includes(":")) {
    throw new UnsafeUrlError(`raw-IP hosts are not allowed: ${url}`);
  }
  if (!ALLOWED_URL_HOSTS.has(host)) {
    throw new UnsafeUrlError(`host ${host} is not in the download allowlist`);
  }
  return url;
}

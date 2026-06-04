import { execSync } from "node:child_process";

export interface ProxySettings {
  host: string;
  port: number;
}

let cachedProxy: ProxySettings | null | undefined;
let proxySetup = false;

export function detectSystemProxy(): ProxySettings | null {
  if (cachedProxy !== undefined) return cachedProxy;

  cachedProxy = detectFromEnv() ?? detectFromWindowsRegistry() ?? detectFromMacos() ?? null;
  return cachedProxy;
}

export function proxyUrl(): string | null {
  const p = detectSystemProxy();
  if (!p) return null;
  return `http://${p.host}:${p.port}`;
}

export async function setupGlobalProxy(): Promise<void> {
  if (proxySetup) return;
  proxySetup = true;
  const url = proxyUrl();
  if (!url) return;
  try {
    // @ts-ignore undici is bundled in Node >= 18
    const undici = await import("undici");
    if (undici.ProxyAgent && undici.setGlobalDispatcher) {
      const agent = new undici.ProxyAgent(url);
      undici.setGlobalDispatcher(agent);
    }
  } catch {}
}

function detectFromEnv(): ProxySettings | null {
  for (const key of ["https_proxy", "HTTPS_PROXY", "http_proxy", "HTTP_PROXY", "all_proxy", "ALL_PROXY"]) {
    const val = process.env[key];
    if (!val) continue;
    const parsed = parseProxyUrl(val);
    if (parsed) return parsed;
  }
  return null;
}

function parseProxyUrl(raw: string): ProxySettings | null {
  const s = raw.trim();
  try {
    const url = new URL(s.startsWith("http") ? s : `http://${s}`);
    const host = url.hostname || null;
    const port = parseInt(url.port || "80", 10);
    if (host && port > 0) return { host, port };
  } catch {}
  const m = s.match(/^([^:/]+):(\d+)$/);
  if (m) return { host: m[1], port: parseInt(m[2], 10) };
  return null;
}

function detectFromWindowsRegistry(): ProxySettings | null {
  if (process.platform !== "win32") return null;
  try {
    const regBase = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings";
    const serverOut = execSync(`reg query "${regBase}" /v ProxyServer`, { encoding: "utf-8", stdio: ["pipe", "pipe", "ignore"] });
    const serverMatch = serverOut.match(/REG_SZ\s+(.+)/);
    if (serverMatch) {
      const raw = serverMatch[1].trim();
      if (raw) {
        const httpsEntry = raw.split(";").find(e => e.startsWith("https="));
        const candidate = httpsEntry ? httpsEntry.split("=")[1] : raw;
        const parsed = parseProxyUrl(candidate);
        if (parsed) return parsed;
      }
    }
  } catch {}
  return null;
}

function detectFromMacos(): ProxySettings | null {
  if (process.platform !== "darwin") return null;
  try {
    for (const service of ["Wi-Fi", "Ethernet"]) {
      for (const proto of ["getwebproxy", "getsecurewebproxy"]) {
        const out = execSync(`networksetup -${proto} "${service}"`, { encoding: "utf-8", stdio: ["pipe", "pipe", "ignore"] });
        const lines = out.split("\n").map(l => l.trim());
        const enabled = lines[0] === "Yes";
        const host = lines[1];
        const port = parseInt(lines[2], 10);
        if (enabled && host && port > 0) return { host, port };
      }
    }
  } catch {}
  return null;
}

export function gradleProxyEnv(): Record<string, string> {
  const proxy = detectSystemProxy();
  if (!proxy) return {};
  const opts = [
    `-Dhttp.proxyHost=${proxy.host}`,
    `-Dhttp.proxyPort=${proxy.port}`,
    `-Dhttps.proxyHost=${proxy.host}`,
    `-Dhttps.proxyPort=${proxy.port}`,
  ].join(" ");
  const existing = process.env.GRADLE_OPTS;
  return { GRADLE_OPTS: existing ? `${existing} ${opts}` : opts };
}

export function javaProxyArgs(): string[] {
  const proxy = detectSystemProxy();
  if (!proxy) return [];
  return [
    `-Dhttp.proxyHost=${proxy.host}`,
    `-Dhttp.proxyPort=${proxy.port}`,
    `-Dhttps.proxyHost=${proxy.host}`,
    `-Dhttps.proxyPort=${proxy.port}`,
  ];
}

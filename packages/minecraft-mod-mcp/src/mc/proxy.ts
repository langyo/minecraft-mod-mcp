import { execSync } from "node:child_process";

export interface ProxySettings {
  host: string;
  port: number;
}

let cachedProxy: ProxySettings | null | undefined;
let proxySetup = false;
let proxyActive = false;

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

export function isProxyActive(): boolean {
  return proxyActive;
}

export async function setupGlobalProxy(): Promise<void> {
  if (proxySetup) return;
  proxySetup = true;
  const url = proxyUrl();
  if (!url) return;
  try {
    const undici = await import("undici");
    if (undici.ProxyAgent && undici.setGlobalDispatcher) {
      const agent = new undici.ProxyAgent(url);
      undici.setGlobalDispatcher(agent);
      proxyActive = true;
      console.warn(`[WARN] Using system proxy: ${url}`);
      const testOk = await probeProxyTls();
      if (!testOk) {
        console.warn(`[WARN] Proxy TLS interception detected — disabling TLS verification`);
        process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
      }
    }
  } catch {}
}

async function probeProxyTls(): Promise<boolean> {
  try {
    const resp = await fetch("https://piston-meta.mojang.com/", { method: "HEAD", signal: AbortSignal.timeout(8_000) });
    return resp.ok || resp.status < 500;
  } catch {
    return false;
  }
}

const sleep = (ms: number) => new Promise<void>(r => setTimeout(r, ms));

async function nativeDownload(url: string, destPath: string): Promise<void> {
  const { execFile } = await import("node:child_process");
  if (process.platform === "win32") {
    const ps = ["Invoke-WebRequest", "-Uri", url, "-OutFile", destPath, "-TimeoutSec", "60"];
    return new Promise<void>((resolve, reject) => {
      execFile("powershell", ["-NoProfile", "-Command", ...ps], {
        timeout: 120_000,
        windowsHide: true,
      }, (err) => {
        if (err) reject(err);
        else resolve();
      });
    });
  }
  return new Promise<void>((resolve, reject) => {
    execFile("curl", ["-fSL", "-o", destPath, "--connect-timeout", "60", url], {
      timeout: 120_000,
    }, (err) => {
      if (err) reject(err);
      else resolve();
    });
  });
}

export async function fetchWithFallback(url: string, init?: RequestInit): Promise<Response> {
  const maxRetries = 3;
  const proxyTimeout = 15_000;
  for (let attempt = 0; attempt < maxRetries; attempt++) {
    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), proxyTimeout);
      const resp = await fetch(url, { ...init, signal: controller.signal });
      clearTimeout(timer);
      return resp;
    } catch (err: any) {
      const isProxy = proxyActive;
      const isRetryable = /ECONNRESET|ETIMEDOUT|ECONNREFUSED|abort|socket hang up|fetch failed/i.test(err.message || "");
      if (isProxy && isRetryable && attempt === 0) {
        console.warn(`[WARN] Proxy request failed (${err.message}), retrying direct...`);
        try {
          const undici = await import("undici");
          if (undici.setGlobalDispatcher) {
            undici.setGlobalDispatcher(new undici.Agent());
          }
          proxyActive = false;
        } catch {}
      }
      if (isRetryable && attempt < maxRetries - 1) {
        console.warn(`[WARN] Retrying (${attempt + 1}/${maxRetries}): ${url.slice(0, 80)}...`);
        await sleep(2000 * (attempt + 1));
        continue;
      }
      throw err;
    }
  }
  throw new Error("unreachable");
}

export async function downloadWithNativeFallback(url: string, destPath: string): Promise<void> {
  try {
    const resp = await fetchWithFallback(url);
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    const buf = Buffer.from(await resp.arrayBuffer());
    const { writeFileSync } = await import("node:fs");
    writeFileSync(destPath, buf);
  } catch (fetchErr: any) {
    console.warn(`[WARN] Node fetch failed for ${url.slice(0, 60)}... (${fetchErr.cause?.code || fetchErr.message}), trying native download...`);
    await nativeDownload(url, destPath);
  }
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
        const lines = out.split(/\r?\n/).map(l => l.trim());
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

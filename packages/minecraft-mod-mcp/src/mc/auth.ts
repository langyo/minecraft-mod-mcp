const CLIENT_ID = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb";

const DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
const TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const XBL_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
const MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
const MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

export interface DeviceCodeInfo {
  user_code: string;
  device_code: string;
  verification_uri: string;
  interval: number;
  expires_in: number;
  message: string;
}

export interface MicrosoftProfile {
  uuid: string;
  username: string;
  access_token: string;
  refresh_token: string;
  expires_at: number;
}

export async function startDeviceAuth(): Promise<DeviceCodeInfo> {
  const resp = await fetch(DEVICE_CODE_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: CLIENT_ID,
      scope: "XboxLive.signin offline_access",
    }),
  });

  if (!resp.ok) {
    const body = await resp.text();
    throw new Error(`Device code request failed (${resp.status}): ${body}`);
  }

  return resp.json() as Promise<DeviceCodeInfo>;
}

export async function pollDeviceAuth(deviceCode: string): Promise<MicrosoftProfile> {
  const oauthTokens = await pollForTokens(deviceCode);

  const accessToken = oauthTokens.access_token as string;
  const refreshToken = oauthTokens.refresh_token as string;
  const expiresIn = (oauthTokens.expires_in as number) || 3600;
  const expiresAt = nowTimestamp() + expiresIn;

  return authChain(accessToken, refreshToken, expiresAt);
}

export async function refreshToken(refreshToken: string): Promise<MicrosoftProfile> {
  const resp = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "refresh_token",
      client_id: CLIENT_ID,
      refresh_token: refreshToken,
      scope: "XboxLive.signin offline_access",
    }),
  });

  if (!resp.ok) {
    const body = await resp.text();
    throw new Error(`Token refresh failed (${resp.status}): ${body}`);
  }

  const body = (await resp.json()) as Record<string, unknown>;
  const accessToken = body.access_token as string;
  const newRefreshToken = body.refresh_token as string;
  const expiresIn = (body.expires_in as number) || 3600;
  const expiresAt = nowTimestamp() + expiresIn;

  return authChain(accessToken, newRefreshToken, expiresAt);
}

async function pollForTokens(deviceCode: string): Promise<Record<string, unknown>> {
  while (true) {
    const resp = await fetch(TOKEN_URL, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "urn:ietf:params:oauth:grant-type:device_code",
        client_id: CLIENT_ID,
        device_code: deviceCode,
      }),
    });

    const body = (await resp.json()) as Record<string, unknown>;

    const error = body.error as string | undefined;
    if (error) {
      switch (error) {
        case "authorization_pending":
          await sleep(5000);
          continue;
        case "slow_down":
          await sleep(10000);
          continue;
        case "expired_token":
          throw new Error("Device code expired. Please try again.");
        case "cancelled":
          throw new Error("Authentication was cancelled.");
        default:
          throw new Error(`OAuth error: ${error} - ${body.error_description ?? "Unknown"}`);
      }
    }

    return body;
  }
}

async function authChain(
  liveAccessToken: string,
  refreshToken: string,
  expiresAt: number,
): Promise<MicrosoftProfile> {
  const [uhs, xstsToken] = await xboxAuth(liveAccessToken);
  const mcToken = await minecraftLogin(uhs, xstsToken);
  const [uuid, username] = await minecraftProfile(mcToken);

  return { uuid, username, access_token: mcToken, refresh_token: refreshToken, expires_at: expiresAt };
}

async function xboxAuth(liveAccessToken: string): Promise<[string, string]> {
  const xblResp = await postJson(XBL_AUTH_URL, {
    Properties: {
      AuthMethod: "RPS",
      SiteName: "user.auth.xboxlive.com",
      RpsTicket: `d=${liveAccessToken}`,
    },
    RelyingParty: "http://auth.xboxlive.com",
    TokenType: "JWT",
  });

  const xblToken = xblResp.Token as string;
  const displayClaims = xblResp.DisplayClaims as Record<string, Array<Record<string, string>>>;
  const uhsValue = displayClaims.xui[0].uhs;

  const xstsResp = await postJson(XSTS_AUTH_URL, {
    Properties: { SandboxId: "RETAIL", UserTokens: [xblToken] },
    RelyingParty: "rp://api.minecraftservices.com/",
    TokenType: "JWT",
  });

  const xstsToken = xstsResp.Token as string;
  return [uhsValue, xstsToken];
}

async function minecraftLogin(uhs: string, xstsToken: string): Promise<string> {
  const resp = await postJson(MC_LOGIN_URL, {
    identityToken: `XBL3.0 x=${uhs};${xstsToken}`,
  });
  return resp.access_token as string;
}

async function minecraftProfile(mcAccessToken: string): Promise<[string, string]> {
  const resp = await fetch(MC_PROFILE_URL, {
    headers: { Authorization: `Bearer ${mcAccessToken}` },
  });
  const body = (await resp.json()) as Record<string, unknown>;
  return [body.id as string, body.name as string];
}

async function postJson(url: string, body: unknown): Promise<Record<string, unknown>> {
  const resp = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  return (await resp.json()) as Record<string, unknown>;
}

function nowTimestamp(): number {
  return Math.floor(Date.now() / 1000);
}

function sleep(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms));
}

export function createOfflineUuid(username: string): string {
  let h = 0;
  for (let i = 0; i < username.length; i++) {
    h = (Math.imul(31, h) + username.charCodeAt(i)) | 0;
  }
  const hash = h >>> 0;
  const t = Date.now();
  return (
    `${hash.toString(16).padStart(8, "0")}-` +
    `${((hash >> 16) & 0xffff).toString(16).padStart(4, "0")}-` +
    `${((hash & 0xffff) ^ 0x4000).toString(16).padStart(4, "0")}-` +
    `${(((t >> 16) ^ 0x8000) & 0xffff).toString(16).padStart(4, "0")}-` +
    `${(hash ^ (t >> 32)).toString(16).padStart(8, "0")}` +
    `${(t & 0xffff).toString(16).padStart(4, "0")}`
  );
}

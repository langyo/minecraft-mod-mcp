use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};
use std::time::{SystemTime, UNIX_EPOCH};
use tracing::{debug, info};
use ts_rs::TS;

const CLIENT_ID: &str = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb";

const DEVICE_CODE_URL: &str =
    "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
const TOKEN_URL: &str =
    "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const XBL_AUTH_URL: &str = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_AUTH_URL: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";
const MC_LOGIN_URL: &str = "https://api.minecraftservices.com/authentication/login_with_xbox";
const MC_PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";

#[derive(Debug, Clone, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct DeviceCodeInfo {
    pub user_code: String,
    pub device_code: String,
    pub verification_uri: String,
    pub interval: u64,
    pub expires_in: u64,
    pub message: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct MicrosoftProfile {
    pub uuid: String,
    pub username: String,
    pub access_token: String,
    pub refresh_token: String,
    pub expires_at: u64,
}

pub struct MicrosoftAuth;

impl MicrosoftAuth {
    pub fn new() -> Self {
        Self
    }

    pub async fn start_device_auth(&self) -> Result<DeviceCodeInfo> {
        let client = reqwest::Client::new();
        let resp = client
            .post(DEVICE_CODE_URL)
            .form(&[
                ("client_id", CLIENT_ID),
                ("scope", "XboxLive.signin offline_access"),
            ])
            .send()
            .await
            .context("Failed to request device code")?;

        if !resp.status().is_success() {
            let status = resp.status();
            let body = resp.text().await.unwrap_or_default();
            anyhow::bail!("Device code request failed ({status}): {body}");
        }

        let info: DeviceCodeInfo = resp
            .json()
            .await
            .context("Failed to parse device code response")?;

        info!("Device auth started: user_code={}", info.user_code);
        Ok(info)
    }

    pub async fn poll_device_auth(&self, device_code: &str) -> Result<MicrosoftProfile> {
        let client = reqwest::Client::new();

        let oauth_tokens = loop {
            let resp = client
                .post(TOKEN_URL)
                .form(&[
                    ("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                    ("client_id", CLIENT_ID),
                    ("device_code", device_code),
                ])
                .send()
                .await
                .context("Failed to poll device token")?;

            let body: serde_json::Value = resp.json().await?;

            if let Some(error) = body.get("error").and_then(|e| e.as_str()) {
                match error {
                    "authorization_pending" => {
                        debug!("Authorization pending, waiting...");
                        tokio::time::sleep(std::time::Duration::from_secs(5)).await;
                        continue;
                    }
                    "slow_down" => {
                        tokio::time::sleep(std::time::Duration::from_secs(10)).await;
                        continue;
                    }
                    "expired_token" => {
                        anyhow::bail!("Device code expired. Please try again.");
                    }
                    "cancelled" => {
                        anyhow::bail!("Authentication was cancelled.");
                    }
                    _ => {
                        let desc = body
                            .get("error_description")
                            .and_then(|d| d.as_str())
                            .unwrap_or("Unknown error");
                        anyhow::bail!("OAuth error: {error} - {desc}");
                    }
                }
            }

            break body;
        };

        let access_token = oauth_tokens
            .get("access_token")
            .and_then(|t| t.as_str())
            .context("Missing access_token")?
            .to_string();
        let refresh_token = oauth_tokens
            .get("refresh_token")
            .and_then(|t| t.as_str())
            .context("Missing refresh_token")?
            .to_string();
        let expires_in = oauth_tokens
            .get("expires_in")
            .and_then(|t| t.as_u64())
            .unwrap_or(3600);

        let expires_at = now_timestamp() + expires_in;

        let profile = self
            .auth_chain(&access_token, refresh_token, expires_at)
            .await?;

        info!("Microsoft auth complete: username={}", profile.username);
        Ok(profile)
    }

    pub async fn refresh_token(&self, refresh_token: &str) -> Result<MicrosoftProfile> {
        let client = reqwest::Client::new();

        let resp = client
            .post(TOKEN_URL)
            .form(&[
                ("grant_type", "refresh_token"),
                ("client_id", CLIENT_ID),
                ("refresh_token", refresh_token),
                ("scope", "XboxLive.signin offline_access"),
            ])
            .send()
            .await
            .context("Failed to refresh token")?;

        if !resp.status().is_success() {
            let status = resp.status();
            let body = resp.text().await.unwrap_or_default();
            anyhow::bail!("Token refresh failed ({status}): {body}");
        }

        let body: serde_json::Value = resp.json().await?;

        let access_token = body
            .get("access_token")
            .and_then(|t| t.as_str())
            .context("Missing access_token")?
            .to_string();
        let new_refresh_token = body
            .get("refresh_token")
            .and_then(|t| t.as_str())
            .context("Missing refresh_token")?
            .to_string();
        let expires_in = body.get("expires_in").and_then(|t| t.as_u64()).unwrap_or(3600);

        let expires_at = now_timestamp() + expires_in;

        let profile = self
            .auth_chain(&access_token, new_refresh_token, expires_at)
            .await?;

        info!("Token refresh complete: username={}", profile.username);
        Ok(profile)
    }

    pub async fn validate_token(&self, access_token: &str) -> Result<bool> {
        let client = reqwest::Client::new();

        let resp = client
            .get(MC_PROFILE_URL)
            .bearer_auth(access_token)
            .send()
            .await
            .context("Failed to validate token")?;

        Ok(resp.status().is_success())
    }

    async fn auth_chain(
        &self,
        live_access_token: &str,
        refresh_token: String,
        expires_at: u64,
    ) -> Result<MicrosoftProfile> {
        let client = reqwest::Client::new();

        let (uhs, xsts_token) = self
            .xbox_auth(&client, live_access_token)
            .await?;

        let mc_token = self
            .minecraft_login(&client, &uhs, &xsts_token)
            .await?;

        let (uuid, username) = self
            .minecraft_profile(&client, &mc_token)
            .await?;

        Ok(MicrosoftProfile {
            uuid,
            username,
            access_token: mc_token,
            refresh_token,
            expires_at,
        })
    }

    async fn xbox_auth(
        &self,
        client: &reqwest::Client,
        live_access_token: &str,
    ) -> Result<(String, String)> {
        let xbl_resp: serde_json::Value = client
            .post(XBL_AUTH_URL)
            .json(&serde_json::json!({
                "Properties": {
                    "AuthMethod": "RPS",
                    "SiteName": "user.auth.xboxlive.com",
                    "RpsTicket": format!("d={live_access_token}")
                },
                "RelyingParty": "http://auth.xboxlive.com",
                "TokenType": "JWT"
            }))
            .send()
            .await
            .context("XBL auth request failed")?
            .json()
            .await
            .context("Failed to parse XBL response")?;

        let xbl_token = xbl_resp
            .get("Token")
            .and_then(|t| t.as_str())
            .context("Missing XBL Token")?
            .to_string();
        let uhs = xbl_resp
            .pointer("/DisplayClaims/xui/0/uhs")
            .and_then(|t| t.as_str())
            .context("Missing XBL uhs")?
            .to_string();

        let xsts_resp: serde_json::Value = client
            .post(XSTS_AUTH_URL)
            .json(&serde_json::json!({
                "Properties": {
                    "SandboxId": "RETAIL",
                    "UserTokens": [xbl_token]
                },
                "RelyingParty": "rp://api.minecraftservices.com/",
                "TokenType": "JWT"
            }))
            .send()
            .await
            .context("XSTS auth request failed")?
            .json()
            .await
            .context("Failed to parse XSTS response")?;

        let xsts_token = xsts_resp
            .get("Token")
            .and_then(|t| t.as_str())
            .context("Missing XSTS Token")?
            .to_string();

        Ok((uhs, xsts_token))
    }

    async fn minecraft_login(
        &self,
        client: &reqwest::Client,
        uhs: &str,
        xsts_token: &str,
    ) -> Result<String> {
        let resp: serde_json::Value = client
            .post(MC_LOGIN_URL)
            .json(&serde_json::json!({
                "identityToken": format!("XBL3.0 x={uhs};{xsts_token}")
            }))
            .send()
            .await
            .context("Minecraft login request failed")?
            .json()
            .await
            .context("Failed to parse Minecraft login response")?;

        resp.get("access_token")
            .and_then(|t| t.as_str())
            .map(|s| s.to_string())
            .context("Missing Minecraft access_token")
    }

    async fn minecraft_profile(
        &self,
        client: &reqwest::Client,
        mc_access_token: &str,
    ) -> Result<(String, String)> {
        let resp: serde_json::Value = client
            .get(MC_PROFILE_URL)
            .bearer_auth(mc_access_token)
            .send()
            .await
            .context("Minecraft profile request failed")?
            .json()
            .await
            .context("Failed to parse Minecraft profile response")?;

        let uuid = resp
            .get("id")
            .and_then(|t| t.as_str())
            .context("Missing profile UUID")?
            .to_string();
        let username = resp
            .get("name")
            .and_then(|t| t.as_str())
            .context("Missing profile username")?
            .to_string();

        Ok((uuid, username))
    }
}

fn now_timestamp() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs()
}

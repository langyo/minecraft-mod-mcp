use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VersionManifest {
    pub latest: LatestVersions,
    pub versions: Vec<ManifestVersion>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LatestVersions {
    pub release: String,
    pub snapshot: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ManifestVersion {
    pub id: String,
    #[serde(rename = "type")]
    pub version_type: String,
    pub url: String,
    pub time: String,
    #[serde(rename = "releaseTime")]
    pub release_time: String,
}

pub async fn fetch_version_manifest() -> Result<VersionManifest> {
    let client = reqwest::Client::new();
    let resp = client
        .get("https://piston-meta.mojang.com/mc/game/version_manifest.json")
        .send()
        .await
        .context("Failed to fetch version manifest")?;

    let manifest: VersionManifest = resp
        .json()
        .await
        .context("Failed to parse version manifest")?;

    Ok(manifest)
}

pub async fn fetch_version_json(url: &str) -> Result<_shared_mc_metadata::VersionJson> {
    let client = reqwest::Client::new();
    let resp = client
        .get(url)
        .send()
        .await
        .context("Failed to fetch version JSON")?;

    let vj: _shared_mc_metadata::VersionJson = resp
        .json()
        .await
        .context("Failed to parse version JSON")?;

    Ok(vj)
}

use anyhow::{Context, Result};
use futures::StreamExt;
use sha1::{Digest, Sha1};
use std::path::Path;
use tokio::io::AsyncWriteExt;
use tracing::{debug, info, warn};

use _shared_core::platform;
use _shared_mc_metadata::VersionJson;

#[derive(Debug, Clone)]
pub struct DownloadProgress {
    pub url: String,
    pub downloaded: u64,
    pub total: Option<u64>,
}

pub async fn download_file(
    url: &str,
    path: &Path,
    expected_sha1: Option<&str>,
) -> Result<()> {
    if path.is_file() {
        if let Some(sha) = expected_sha1 {
            if let Ok(existing_hash) = sha1_file(path).await {
                if existing_hash.eq_ignore_ascii_case(sha) {
                    debug!("Skipping {}, SHA-1 matches", path.display());
                    return Ok(());
                }
            }
        }
    }

    if let Some(parent) = path.parent() {
        tokio::fs::create_dir_all(parent)
            .await
            .with_context(|| format!("Failed to create directory {}", parent.display()))?;
    }

    let client = reqwest::Client::new();
    let resp = client
        .get(url)
        .send()
        .await
        .with_context(|| format!("Failed to download {url}"))?;

    if !resp.status().is_success() {
        anyhow::bail!("Download failed for {url}: HTTP {}", resp.status());
    }

    let total = resp.content_length();
    let mut stream = resp.bytes_stream();
    let mut file = tokio::fs::File::create(path)
        .await
        .with_context(|| format!("Failed to create file {}", path.display()))?;

    let mut hasher = Sha1::new();
    let mut downloaded: u64 = 0;

    while let Some(chunk) = stream.next().await {
        let chunk = chunk.context("Error reading download stream")?;
        file.write_all(&chunk).await?;
        hasher.update(&chunk);
        downloaded += chunk.len() as u64;
    }

    file.flush().await?;
    drop(file);

    if let Some(sha) = expected_sha1 {
        let actual = hex::encode(hasher.finalize());
        if !actual.eq_ignore_ascii_case(sha) {
            tokio::fs::remove_file(path).await.ok();
            anyhow::bail!(
                "SHA-1 mismatch for {}: expected {sha}, got {actual}",
                path.display()
            );
        }
    }

    debug!(
        "Downloaded {} ({} bytes{})",
        path.display(),
        downloaded,
        if total.is_some() {
            format!("/{}", total.unwrap())
        } else {
            String::new()
        }
    );

    Ok(())
}

async fn sha1_file(path: &Path) -> Result<String> {
    let data = tokio::fs::read(path).await?;
    let mut hasher = Sha1::new();
    hasher.update(&data);
    Ok(hex::encode(hasher.finalize()))
}

pub async fn download_version<F>(
    version_json: &VersionJson,
    on_progress: F,
) -> Result<()>
where
    F: Fn(DownloadProgress),
{
    let version_id = &version_json.id;
    let versions_dir = platform::versions_dir().join(version_id);
    tokio::fs::create_dir_all(&versions_dir).await?;

    let version_json_path = versions_dir.join(format!("{version_id}.json"));
    let json_data = serde_json::to_string_pretty(version_json)?;
    tokio::fs::write(&version_json_path, json_data)
        .await
        .context("Failed to write version JSON")?;
    info!("Saved version JSON to {}", version_json_path.display());

    if let Some(ref asset_index) = version_json.asset_index {
        if let Some(ref url) = asset_index.url {
            let asset_index_path = platform::assets_dir()
                .join("indexes")
                .join(format!("{}.json", asset_index.id));

            if !asset_index_path.is_file() {
                download_file(url, &asset_index_path, asset_index.sha1.as_deref()).await?;
                on_progress(DownloadProgress {
                    url: url.clone(),
                    downloaded: 0,
                    total: asset_index.size,
                });
            }

            let index_data = tokio::fs::read_to_string(&asset_index_path).await?;
            let index: serde_json::Value = serde_json::from_str(&index_data)?;

            if let Some(objects) = index.get("objects").and_then(|o| o.as_object()) {
                let total_objects = objects.len();
                info!("Downloading {total_objects} assets...");

                for (i, (name, obj)) in objects.iter().enumerate() {
                    let hash = obj
                        .get("hash")
                        .and_then(|h| h.as_str())
                        .unwrap_or("");
                    let size = obj.get("size").and_then(|s| s.as_u64()).unwrap_or(0);

                    let hash_prefix = &hash[..2.min(hash.len())];
                    let asset_url = format!("https://resources.download.minecraft.net/{hash_prefix}/{hash}");
                    let asset_path = platform::assets_dir()
                        .join("objects")
                        .join(hash_prefix)
                        .join(hash);

                    if !asset_path.is_file() {
                        if let Err(e) =
                            download_file(&asset_url, &asset_path, Some(hash)).await
                        {
                            warn!("Failed to download asset {name}: {e}");
                            continue;
                        }
                    }

                    if i % 100 == 0 {
                        on_progress(DownloadProgress {
                            url: format!("assets ({}/{total_objects})", i + 1),
                            downloaded: (i as u64 + 1) * size,
                            total: Some(total_objects as u64 * size),
                        });
                    }
                }
            }
        }
    }

    info!("Downloading libraries for {version_id}...");
    for lib in &version_json.libraries {
        if let Some(ref downloads) = lib.downloads {
            if let Some(ref artifact) = downloads.artifact {
                if let Some(ref url) = artifact.url {
                    let lib_path = platform::libraries_dir().join(&artifact.path);
                    download_file(url, &lib_path, artifact.sha1.as_deref()).await?;
                }
            }
        }
        // FALLBACK: construct URL from name (Maven convention)
        else if !lib.name.is_empty() {
            let path = _shared_mc_metadata::library_maven_path(&lib.name);
            let url = format!("https://libraries.minecraft.net/{}", path);
            let file_path = platform::libraries_dir().join(&path);
            if !file_path.is_file() {
                if let Some(ref dir) = file_path.parent() {
                    std::fs::create_dir_all(dir)?;
                }
                download_file(&url, &file_path, None).await?;
            }
        }
    }

    if let Some(ref downloads) = version_json.downloads {
        if let Some(ref client) = downloads.client {
            if let Some(ref url) = client.url {
                let jar_path = platform::versions_dir()
                    .join(&version_json.id)
                    .join(format!("{}.jar", version_json.id));
                if !jar_path.is_file() {
                    if let Some(ref dir) = jar_path.parent() {
                        std::fs::create_dir_all(dir)?;
                    }
                    download_file(url, &jar_path, client.sha1.as_deref()).await?;
                }
            }
        }
    }

    info!("Version {version_id} download complete");
    Ok(())
}

use thiserror::Error;

#[derive(Error, Debug)]
pub enum LauncherError {
    #[error("Java not found: version {0}")]
    JavaNotFound(u32),

    #[error("Version not found: {0}")]
    VersionNotFound(String),

    #[error("Version JSON parse error: {0}")]
    VersionJsonParse(#[from] serde_json::Error),

    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),

    #[error("Network error: {0}")]
    Network(#[from] reqwest::Error),

    #[error("Download hash mismatch: expected {expected}, got {actual}")]
    HashMismatch { expected: String, actual: String },

    #[error("Forge installation failed: {0}")]
    ForgeInstall(String),

    #[error("Missing library: {0}")]
    MissingLibrary(String),

    #[error("Missing native: {0}")]
    MissingNative(String),

    #[error("Asset index not found: {0}")]
    AssetIndexNotFound(String),

    #[error("{0}")]
    Other(#[from] anyhow::Error),
}

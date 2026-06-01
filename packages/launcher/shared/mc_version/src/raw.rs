use serde::Deserialize;
use std::collections::HashMap;

#[derive(Debug, Deserialize)]
pub struct VersionsToml {
    pub fg_eras: HashMap<String, FgEraRaw>,
    pub versions: HashMap<String, VersionRaw>,
    pub api_groups: HashMap<String, String>,
    pub fabric_loom: HashMap<String, String>,
    pub neoforge_gradle: NeoForgeGradleRaw,
    pub legacy_eras: Vec<String>,
}

#[derive(Debug, Deserialize)]
pub struct FgEraRaw {
    pub fg_version: String,
    pub gradle: String,
    pub plugin_id: String,
    pub java: u32,
    pub min_mc: String,
    pub max_mc: String,
}

#[derive(Debug, Deserialize)]
pub struct VersionRaw {
    pub forge: String,
    pub fg_era: String,
    pub java: u32,
    pub mappings: String,
    pub version_id: String,
    pub neoforge: Option<String>,
    pub mdg: Option<String>,
    pub fabric_yarn: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct NeoForgeGradleRaw {
    pub mdg_2_0_prefix: String,
    pub mdg_2_0_gradle: String,
    pub mdg_other_gradle: String,
}

static TOML_DATA: &str = include_str!("../../config/versions.toml");

pub fn parse() -> VersionsToml {
    toml::from_str(TOML_DATA).expect("versions.toml is valid")
}

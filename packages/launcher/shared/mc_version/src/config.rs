use crate::fg_era::Loader;
use crate::raw;
use serde::{Deserialize, Serialize};
use std::sync::LazyLock;
use ts_rs::TS;

#[derive(Debug, Clone, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct FgEra {
    pub key: String,
    pub fg_version: String,
    pub gradle: String,
    pub plugin_id: String,
    pub java: u32,
    pub min_mc: String,
    pub max_mc: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct VersionInfo {
    pub mc_version: String,
    pub forge: String,
    pub fg_era: String,
    pub java: u32,
    pub mappings: String,
    pub version_id: String,
    pub neoforge: Option<String>,
    pub mdg: Option<String>,
    pub fabric_yarn: Option<String>,
}

impl VersionInfo {
    pub fn loaders(&self) -> Vec<Loader> {
        let mut loaders = vec![];
        if !self.forge.is_empty() {
            loaders.push(Loader::Forge);
        }
        if self.neoforge.is_some() {
            loaders.push(Loader::Neoforge);
        }
        if self.fabric_yarn.is_some() {
            loaders.push(Loader::Fabric);
        }
        loaders
    }

    pub fn is_legacy(&self) -> bool {
        static DATA: LazyLock<raw::VersionsToml> = LazyLock::new(raw::parse);
        DATA.legacy.eras.contains(&self.fg_era)
    }
}

static DATA: LazyLock<raw::VersionsToml> = LazyLock::new(raw::parse);

fn toml_key(mc: &str) -> String {
    mc.replace('.', "_")
}

pub fn get_version(mc: &str) -> Option<VersionInfo> {
    let data = &*DATA;
    let key = toml_key(mc);
    let raw = data.versions.get(&key)?;
    Some(VersionInfo {
        mc_version: mc.to_string(),
        forge: raw.forge.clone(),
        fg_era: raw.fg_era.clone(),
        java: raw.java,
        mappings: raw.mappings.clone(),
        version_id: raw.version_id.clone(),
        neoforge: raw.neoforge.clone(),
        mdg: raw.mdg.clone(),
        fabric_yarn: raw.fabric_yarn.clone(),
    })
}

pub fn get_version_by_id(version_id: &str) -> Option<VersionInfo> {
    let data = &*DATA;
    for (key, raw) in &data.versions {
        if raw.version_id == version_id {
            let mc = key.replace('_', ".");
            return Some(VersionInfo {
                mc_version: mc,
                forge: raw.forge.clone(),
                fg_era: raw.fg_era.clone(),
                java: raw.java,
                mappings: raw.mappings.clone(),
                version_id: raw.version_id.clone(),
                neoforge: raw.neoforge.clone(),
                mdg: raw.mdg.clone(),
                fabric_yarn: raw.fabric_yarn.clone(),
            });
        }
    }
    None
}

pub fn get_version_for_loader(mc: &str, loader: Loader) -> Option<String> {
    let info = get_version(mc)?;
    match loader {
        Loader::Forge => Some(info.version_id),
        Loader::Neoforge => info
            .neoforge
            .map(|nf| format!("{mc}-neoforge-{nf}")),
        Loader::Fabric => info
            .fabric_yarn
            .map(|_| format!("{mc}-fabric")),
    }
}

pub fn all_versions() -> Vec<VersionInfo> {
    let data = &*DATA;
    data.versions
        .iter()
        .map(|(key, raw)| {
            let mc = key.replace('_', ".");
            VersionInfo {
                mc_version: mc,
                forge: raw.forge.clone(),
                fg_era: raw.fg_era.clone(),
                java: raw.java,
                mappings: raw.mappings.clone(),
                version_id: raw.version_id.clone(),
                neoforge: raw.neoforge.clone(),
                mdg: raw.mdg.clone(),
                fabric_yarn: raw.fabric_yarn.clone(),
            }
        })
        .collect()
}

pub fn get_fg_era(key: &str) -> Option<FgEra> {
    let data = &*DATA;
    let raw = data.fg_eras.get(key)?;
    Some(FgEra {
        key: key.to_string(),
        fg_version: raw.fg_version.clone(),
        gradle: raw.gradle.clone(),
        plugin_id: raw.plugin_id.clone(),
        java: raw.java,
        min_mc: raw.min_mc.clone(),
        max_mc: raw.max_mc.clone(),
    })
}

pub fn get_api_group(mc: &str) -> Option<String> {
    let data = &*DATA;
    data.api_groups.get(mc).cloned()
}

pub fn get_fabric_loom(mc: &str) -> Option<String> {
    let data = &*DATA;
    data.fabric_loom
        .get(mc)
        .or(data.fabric_loom.get("_default"))
        .cloned()
}

pub fn get_neoforge_gradle(mc: &str) -> Option<(String, String)> {
    let info = get_version(mc)?;
    let mdg = info.mdg?;
    let data = &*DATA;
    let gradle = if mdg.starts_with(&data.neoforge_gradle.mdg_2_0_prefix) {
        data.neoforge_gradle.mdg_2_0_gradle.clone()
    } else {
        data.neoforge_gradle.mdg_other_gradle.clone()
    };
    Some((mdg, gradle))
}

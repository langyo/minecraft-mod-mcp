use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VersionJson {
    pub id: String,
    #[serde(rename = "type")]
    pub version_type: String,
    #[serde(rename = "mainClass")]
    pub main_class: String,
    #[serde(rename = "minecraftArguments")]
    pub minecraft_arguments: Option<String>,
    pub arguments: Option<Arguments>,
    pub libraries: Vec<Library>,
    #[serde(rename = "assetIndex")]
    pub asset_index: Option<AssetIndex>,
    pub assets: Option<String>,
    pub downloads: Option<VersionDownloads>,
    #[serde(rename = "inheritsFrom")]
    pub inherits_from: Option<String>,
    #[serde(rename = "jar")]
    pub jar: Option<String>,
    #[serde(rename = "releaseTime")]
    pub release_time: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VersionDownloads {
    pub client: Option<ClientDownload>,
    pub server: Option<ServerDownload>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ClientDownload {
    pub sha1: Option<String>,
    pub size: Option<u64>,
    pub url: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ServerDownload {
    pub sha1: Option<String>,
    pub size: Option<u64>,
    pub url: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Arguments {
    pub game: Option<Vec<ArgumentValue>>,
    pub jvm: Option<Vec<ArgumentValue>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(untagged)]
pub enum ArgumentValue {
    String(String),
    Conditional {
        rules: Vec<Rule>,
        value: ArgumentData,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(untagged)]
pub enum ArgumentData {
    Single(String),
    Multiple(Vec<String>),
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Rule {
    pub action: String,
    pub os: Option<OsRule>,
    pub features: Option<serde_json::Value>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct OsRule {
    pub name: Option<String>,
    pub version: Option<String>,
    pub arch: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AssetIndex {
    pub id: String,
    pub sha1: Option<String>,
    pub size: Option<u64>,
    #[serde(rename = "totalSize")]
    pub total_size: Option<u64>,
    pub url: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Library {
    pub name: String,
    pub downloads: Option<LibraryDownloads>,
    pub natives: Option<serde_json::Value>,
    pub extract: Option<ExtractRule>,
    pub rules: Option<Vec<Rule>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LibraryDownloads {
    pub artifact: Option<Artifact>,
    pub classifiers: Option<serde_json::Value>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Artifact {
    pub path: String,
    pub sha1: Option<String>,
    pub size: Option<u64>,
    pub url: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExtractRule {
    pub exclude: Option<Vec<String>>,
}

impl VersionJson {
    pub fn load(path: &std::path::Path) -> anyhow::Result<Self> {
        let data = std::fs::read_to_string(path)?;
        let vj: VersionJson = serde_json::from_str(&data)?;
        Ok(vj)
    }

    pub fn load_version(version_id: &str) -> anyhow::Result<Self> {
        let path = _shared_core::platform::versions_dir()
            .join(version_id)
            .join(format!("{version_id}.json"));
        Self::load(&path)
    }

    pub fn collect_all_args(&self) -> (Vec<String>, Vec<String>) {
        let mut game_args = Vec::new();
        let mut jvm_args = Vec::new();

        if let Some(ref args) = self.arguments {
            if let Some(ref game) = args.game {
                for av in game {
                    match av {
                        ArgumentValue::String(s) => game_args.push(s.clone()),
                        ArgumentValue::Conditional { rules, value } => {
                            if should_apply(rules) {
                                match value {
                                    ArgumentData::Single(s) => game_args.push(s.clone()),
                                    ArgumentData::Multiple(v) => {
                                        game_args.extend(v.iter().cloned())
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if let Some(ref jvm) = args.jvm {
                for av in jvm {
                    match av {
                        ArgumentValue::String(s) => jvm_args.push(s.clone()),
                        ArgumentValue::Conditional { rules, value } => {
                            if should_apply(rules) {
                                match value {
                                    ArgumentData::Single(s) => jvm_args.push(s.clone()),
                                    ArgumentData::Multiple(v) => jvm_args.extend(v.iter().cloned()),
                                }
                            }
                        }
                    }
                }
            }
        }

        if let Some(ref mc_args) = self.minecraft_arguments {
            game_args.extend(mc_args.split_whitespace().map(String::from));
        }

        (jvm_args, game_args)
    }
}

fn should_apply(rules: &[Rule]) -> bool {
    let mut allow = false;
    let mut deny = false;

    for rule in rules {
        let os_match = match &rule.os {
            Some(os) => {
                let name_ok = match &os.name {
                    Some(name) => match name.as_str() {
                        "windows" => cfg!(windows),
                        "linux" => cfg!(target_os = "linux"),
                        "osx" => cfg!(target_os = "macos"),
                        _ => true,
                    },
                    None => true,
                };
                let arch_ok = match &os.arch {
                    Some(arch) => match arch.as_str() {
                        "x86" => cfg!(target_arch = "x86"),
                        _ => true,
                    },
                    None => true,
                };
                name_ok && arch_ok
            }
            None => true,
        };

        if os_match {
            match rule.action.as_str() {
                "allow" => allow = true,
                "disallow" => deny = true,
                _ => {}
            }
        }
    }

    if rules.is_empty() {
        return true;
    }
    allow && !deny
}

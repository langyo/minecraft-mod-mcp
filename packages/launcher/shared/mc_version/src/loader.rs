use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Loader {
    Forge,
    Neoforge,
    Fabric,
}

impl std::fmt::Display for Loader {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Loader::Forge => write!(f, "forge"),
            Loader::Neoforge => write!(f, "neoforge"),
            Loader::Fabric => write!(f, "fabric"),
        }
    }
}

impl std::str::FromStr for Loader {
    type Err = String;
    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s {
            "forge" => Ok(Loader::Forge),
            "neoforge" => Ok(Loader::Neoforge),
            "fabric" => Ok(Loader::Fabric),
            _ => Err(format!("unknown loader: {s}")),
        }
    }
}

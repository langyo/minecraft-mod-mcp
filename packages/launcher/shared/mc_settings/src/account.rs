use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type")]
pub enum Account {
    #[serde(rename = "microsoft")]
    Microsoft {
        uuid: String,
        username: String,
        access_token: String,
        refresh_token: String,
        not_after: u64,
    },
    #[serde(rename = "offline")]
    Offline {
        uuid: String,
        username: String,
    },
}

pub type AccountType = Account;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MicrosoftToken {
    pub access_token: String,
    pub refresh_token: String,
    pub not_after: u64,
}

impl Account {
    pub fn uuid(&self) -> &str {
        match self {
            Self::Microsoft { uuid, .. } | Self::Offline { uuid, .. } => uuid,
        }
    }

    pub fn username(&self) -> &str {
        match self {
            Self::Microsoft { username, .. } | Self::Offline { username, .. } => username,
        }
    }

    pub fn access_token(&self) -> &str {
        match self {
            Self::Microsoft { access_token, .. } => access_token,
            Self::Offline { .. } => "",
        }
    }

    pub fn user_type(&self) -> &str {
        match self {
            Self::Microsoft { .. } => "msa",
            Self::Offline { .. } => "legacy",
        }
    }

    pub fn is_microsoft(&self) -> bool {
        matches!(self, Self::Microsoft { .. })
    }

    pub fn is_offline(&self) -> bool {
        matches!(self, Self::Offline { .. })
    }

    pub fn is_token_expired(&self) -> bool {
        match self {
            Self::Microsoft { not_after, .. } => {
                let now = std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_secs();
                now >= *not_after
            }
            Self::Offline { .. } => false,
        }
    }

    pub fn new_offline(username: String) -> Self {
        let uuid = {
            let hash = {
                use std::hash::{Hash, Hasher};
                let mut hasher = std::collections::hash_map::DefaultHasher::new();
                username.hash(&mut hasher);
                hasher.finish()
            };
            format!(
                "{:08x}-{:04x}-{:04x}-{:04x}-{:08x}{:04x}",
                (hash >> 32) as u32,
                ((hash >> 16) & 0xFFFF) as u16,
                (hash & 0xFFFF) as u16,
                ((hash >> 32) & 0xFFFF) as u16 ^ 0x4000,
                (hash >> 16) as u32 ^ 0xBADC0FFE,
                (hash & 0xFFFF) as u16,
            )
        };
        Self::Offline { uuid, username }
    }
}

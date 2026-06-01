use anyhow::Result;
use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use tracing::debug;

use crate::account::Account;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LauncherConfig {
    pub java_dir: Option<String>,
    pub java_version: Option<u32>,
    pub max_memory_mb: u32,
    pub min_memory_mb: u32,
    pub game_dir: Option<String>,
    pub java_args: Option<String>,
    pub game_args: Option<String>,
    pub width: u32,
    pub height: u32,
    pub fullscreen: bool,
    pub accounts: Vec<Account>,
    pub selected_account: Option<String>,
    pub download_source: String,
    pub mcp_port: Option<u16>,
    pub language: String,
}

impl Default for LauncherConfig {
    fn default() -> Self {
        Self {
            java_dir: None,
            java_version: None,
            max_memory_mb: 2048,
            min_memory_mb: 512,
            game_dir: None,
            java_args: None,
            game_args: None,
            width: 854,
            height: 480,
            fullscreen: false,
            accounts: Vec::new(),
            selected_account: None,
            download_source: "bmclapi".to_string(),
            mcp_port: None,
            language: "zh-CN".to_string(),
        }
    }
}

impl LauncherConfig {
    pub fn config_path() -> PathBuf {
        _shared_core::platform::launcher_dir().join("config.json")
    }

    pub fn load() -> Result<Self> {
        let path = Self::config_path();
        debug!("Loading config from {:?}", path);

        if !path.is_file() {
            debug!("Config file not found, creating default");
            let config = Self::default();
            config.save()?;
            return Ok(config);
        }

        let content = std::fs::read_to_string(&path)?;
        let config: Self = serde_json::from_str(&content)?;
        Ok(config)
    }

    pub fn save(&self) -> Result<()> {
        let path = Self::config_path();
        debug!("Saving config to {:?}", path);

        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent)?;
        }

        let content = serde_json::to_string_pretty(self)?;
        std::fs::write(&path, content)?;
        Ok(())
    }

    pub fn selected_account(&self) -> Option<&Account> {
        let uuid = self.selected_account.as_ref()?;
        self.accounts.iter().find(|a| a.uuid() == uuid)
    }

    pub fn add_account(&mut self, account: Account) {
        let uuid = account.uuid().to_string();
        if let Some(existing) = self.accounts.iter_mut().find(|a| a.uuid() == uuid) {
            *existing = account;
        } else {
            self.accounts.push(account);
        }
    }

    pub fn remove_account(&mut self, uuid: &str) {
        self.accounts.retain(|a| a.uuid() != uuid);
        if self.selected_account.as_deref() == Some(uuid) {
            self.selected_account = self.accounts.first().map(|a| a.uuid().to_string());
        }
    }

    pub fn select_account(&mut self, uuid: &str) -> bool {
        if self.accounts.iter().any(|a| a.uuid() == uuid) {
            self.selected_account = Some(uuid.to_string());
            true
        } else {
            false
        }
    }

    pub fn game_dir_path(&self) -> PathBuf {
        self.game_dir
            .as_deref()
            .map(PathBuf::from)
            .unwrap_or_else(_shared_core::platform::mc_dir)
    }

    pub fn java_exec_path(&self) -> Option<PathBuf> {
        self.java_dir.as_deref().map(|dir| {
            let base = PathBuf::from(dir);
            if cfg!(windows) {
                base.join("bin").join("java.exe")
            } else {
                base.join("bin").join("java")
            }
        })
    }
}

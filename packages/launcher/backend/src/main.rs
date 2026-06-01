#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use serde::Serialize;
use tauri::Manager;
use tokio::sync::Mutex;

struct AppState {
    config: Mutex<_shared_mc_settings::LauncherConfig>,
}

#[derive(Serialize)]
struct CommandResult<T: Serialize> {
    ok: bool,
    data: Option<T>,
    error: Option<String>,
}

impl<T: Serialize> CommandResult<T> {
    fn ok(data: T) -> Self {
        Self {
            ok: true,
            data: Some(data),
            error: None,
        }
    }
    fn err(msg: impl ToString) -> Self {
        Self {
            ok: false,
            data: None,
            error: Some(msg.to_string()),
        }
    }
}

fn ok_unit() -> CommandResult<()> {
    CommandResult {
        ok: true,
        data: Some(()),
        error: None,
    }
}

#[tauri::command]
async fn get_config(state: tauri::State<'_, AppState>) -> Result<CommandResult<_shared_mc_settings::LauncherConfig>, String> {
    let cfg = state.config.lock().await;
    Ok(CommandResult::ok(cfg.clone()))
}

#[tauri::command]
async fn save_config(state: tauri::State<'_, AppState>, config: _shared_mc_settings::LauncherConfig) -> Result<CommandResult<()>, String> {
    if let Err(e) = config.save() {
        return Ok(CommandResult::err(format!("failed to save config: {e}")));
    }
    let mut cfg = state.config.lock().await;
    *cfg = config;
    Ok(ok_unit())
}

#[tauri::command]
async fn select_account(state: tauri::State<'_, AppState>, uuid: String) -> Result<CommandResult<()>, String> {
    let mut cfg = state.config.lock().await;
    if cfg.select_account(&uuid) {
        let _ = cfg.save();
        Ok(ok_unit())
    } else {
        Ok(CommandResult::err(format!("account not found: {uuid}")))
    }
}

#[tauri::command]
async fn remove_account(state: tauri::State<'_, AppState>, uuid: String) -> Result<CommandResult<()>, String> {
    let mut cfg = state.config.lock().await;
    cfg.remove_account(&uuid);
    let _ = cfg.save();
    Ok(ok_unit())
}

#[tauri::command]
async fn detect_javas() -> Result<CommandResult<Vec<_shared_mc_settings::JavaInfo>>, String> {
    let javas = _shared_mc_settings::detect_javas();
    Ok(CommandResult::ok(javas))
}

#[tauri::command]
async fn start_microsoft_auth() -> Result<CommandResult<_shared_mc_auth::DeviceCodeInfo>, String> {
    let auth = _shared_mc_auth::MicrosoftAuth::new();
    match auth.start_device_auth().await {
        Ok(info) => Ok(CommandResult::ok(info)),
        Err(e) => Ok(CommandResult::err(format!("failed to start auth: {e}"))),
    }
}

#[tauri::command]
async fn poll_microsoft_auth(state: tauri::State<'_, AppState>, device_code: String) -> Result<CommandResult<_shared_mc_auth::MicrosoftProfile>, String> {
    let auth = _shared_mc_auth::MicrosoftAuth::new();
    match auth.poll_device_auth(&device_code).await {
        Ok(profile) => {
            let account = _shared_mc_settings::Account::Microsoft {
                uuid: profile.uuid.clone(),
                username: profile.username.clone(),
                access_token: profile.access_token.clone(),
                refresh_token: profile.refresh_token.clone(),
                not_after: profile.expires_at,
            };
            let mut cfg = state.config.lock().await;
            cfg.add_account(account);
            if cfg.selected_account.is_none() {
                cfg.selected_account = Some(profile.uuid.clone());
            }
            let _ = cfg.save();
            Ok(CommandResult::ok(profile))
        }
        Err(e) => Ok(CommandResult::err(format!("auth failed: {e}"))),
    }
}

#[tauri::command]
async fn add_offline_account(state: tauri::State<'_, AppState>, username: String) -> Result<CommandResult<()>, String> {
    let account = _shared_mc_settings::Account::new_offline(username);
    let mut cfg = state.config.lock().await;
    cfg.add_account(account);
    if cfg.selected_account.is_none() {
        cfg.selected_account = Some(cfg.accounts.first().unwrap().uuid().to_string());
    }
    let _ = cfg.save();
    Ok(ok_unit())
}

#[tauri::command]
async fn refresh_account(state: tauri::State<'_, AppState>, uuid: String) -> Result<CommandResult<()>, String> {
    let auth = _shared_mc_auth::MicrosoftAuth::new();
    let mut cfg = state.config.lock().await;

    let account = match cfg.accounts.iter().find(|a| a.uuid() == uuid) {
        Some(a) => a.clone(),
        None => return Ok(CommandResult::err(format!("account not found: {uuid}"))),
    };

    let refresh_token = match &account {
        _shared_mc_settings::Account::Microsoft { refresh_token, .. } => refresh_token.clone(),
        _shared_mc_settings::Account::Offline { .. } => {
            return Ok(CommandResult::err("cannot refresh offline account"));
        }
    };

    match auth.refresh_token(&refresh_token).await {
        Ok(profile) => {
            let updated = _shared_mc_settings::Account::Microsoft {
                uuid: profile.uuid.clone(),
                username: profile.username.clone(),
                access_token: profile.access_token,
                refresh_token: profile.refresh_token,
                not_after: profile.expires_at,
            };
            cfg.add_account(updated);
            let _ = cfg.save();
            Ok(ok_unit())
        }
        Err(e) => Ok(CommandResult::err(format!("refresh failed: {e}"))),
    }
}

#[tauri::command]
async fn list_versions() -> Result<CommandResult<Vec<_shared_mc_version::VersionInfo>>, String> {
    let versions = _shared_mc_version::all_versions();
    Ok(CommandResult::ok(versions))
}

#[tauri::command]
async fn get_version(mc: String) -> Result<CommandResult<_shared_mc_version::VersionInfo>, String> {
    match _shared_mc_version::get_version(&mc) {
        Some(v) => Ok(CommandResult::ok(v)),
        None => Ok(CommandResult::err(format!("version not found: {mc}"))),
    }
}

#[tauri::command]
async fn fetch_remote_versions() -> Result<CommandResult<_shared_mc_download::VersionManifest>, String> {
    match _shared_mc_download::fetch_version_manifest().await {
        Ok(manifest) => Ok(CommandResult::ok(manifest)),
        Err(e) => Ok(CommandResult::err(format!("failed to fetch manifest: {e}"))),
    }
}

#[tauri::command]
async fn install_version(version_id: String, version_url: String) -> Result<CommandResult<()>, String> {
    let vj = match _shared_mc_download::fetch_version_json(&version_url).await {
        Ok(vj) => vj,
        Err(e) => return Ok(CommandResult::err(format!("failed to fetch version json: {e}"))),
    };

    match _shared_mc_download::download_version(&vj, |_| {}).await {
        Ok(()) => Ok(ok_unit()),
        Err(e) => Ok(CommandResult::err(format!("download failed: {e}"))),
    }
}

#[tauri::command]
async fn list_installed_versions() -> Result<CommandResult<Vec<String>>, String> {
    let versions_dir = _shared_core::platform::versions_dir();
    let mut installed = Vec::new();

    if versions_dir.is_dir() {
        if let Ok(entries) = std::fs::read_dir(&versions_dir) {
            for entry in entries.flatten() {
                if entry.path().is_dir() {
                    let name = entry.file_name().to_string_lossy().to_string();
                    let json_path = entry.path().join(format!("{name}.json"));
                    if json_path.is_file() {
                        installed.push(name);
                    }
                }
            }
        }
    }

    installed.sort();
    Ok(CommandResult::ok(installed))
}

#[tauri::command]
async fn launch_game(state: tauri::State<'_, AppState>, version_id: String) -> Result<CommandResult<()>, String> {
    let cfg = state.config.lock().await;

    let vj = match _shared_mc_metadata::VersionJson::load_version(&version_id) {
        Ok(vj) => vj,
        Err(e) => return Ok(CommandResult::err(format!("failed to load version json: {e}"))),
    };

    let account = match cfg.selected_account() {
        Some(a) => a.clone(),
        None => return Ok(CommandResult::err("no account selected")),
    };

    let java = match cfg.java_exec_path() {
        Some(p) if p.is_file() => p,
        _ => {
            let java_ver = _shared_mc_version::get_version_by_id(&version_id)
                .map(|v| v.java)
                .unwrap_or(17);
            match _shared_core::platform::java_exec(java_ver) {
                Ok(p) => p,
                Err(e) => return Ok(CommandResult::err(format!("java not found: {e}"))),
            }
        }
    };

    let mc_dir = cfg.game_dir_path();
    let mcp_port = cfg.mcp_port;

    let launch_config = _shared_mc_launch::LaunchConfig {
        version_id: version_id.clone(),
        mc_dir: Some(mc_dir),
        loader: _shared_mc_version::Loader::Forge,
        mod_jar: None,
        mcp_port,
        dry_run: false,
    };

    let mut cmd = match _shared_mc_launch::build_launch_command(&launch_config, &vj) {
        Ok(c) => c,
        Err(e) => return Ok(CommandResult::err(format!("failed to build command: {e}"))),
    };

    cmd.java = java;

    let player_name = account.username().to_string();
    let uuid = account.uuid().to_string();
    let access_token = account.access_token().to_string();
    let user_type = account.user_type().to_string();

    let args: Vec<String> = cmd
        .args
        .into_iter()
        .map(|arg| {
            arg.replace("${auth_player_name}", &player_name)
                .replace("${auth_uuid}", &uuid)
                .replace("${auth_access_token}", &access_token)
                .replace("${user_type}", &user_type)
        })
        .collect();

    drop(cfg);

    let mut child = match tokio::process::Command::new(&cmd.java)
        .args(&args)
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .spawn()
    {
        Ok(c) => c,
        Err(e) => return Ok(CommandResult::err(format!("failed to launch: {e}"))),
    };

    tokio::spawn(async move {
        let _ = child.wait().await;
    });

    Ok(ok_unit())
}

#[tauri::command]
async fn get_mcp_port(state: tauri::State<'_, AppState>) -> Result<CommandResult<Option<u16>>, String> {
    let cfg = state.config.lock().await;
    Ok(CommandResult::ok(cfg.mcp_port))
}

#[tauri::command]
async fn set_mcp_port(state: tauri::State<'_, AppState>, port: u16) -> Result<CommandResult<()>, String> {
    let mut cfg = state.config.lock().await;
    cfg.mcp_port = Some(port);
    let _ = cfg.save();
    Ok(ok_unit())
}

fn main() {
    let config = _shared_mc_settings::LauncherConfig::load()
        .unwrap_or_else(|_| _shared_mc_settings::LauncherConfig::default());

    tauri::Builder::default()
        .setup(move |app| {
            app.manage(AppState {
                config: Mutex::new(config),
            });
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            get_config,
            save_config,
            select_account,
            remove_account,
            detect_javas,
            start_microsoft_auth,
            poll_microsoft_auth,
            add_offline_account,
            refresh_account,
            list_versions,
            get_version,
            fetch_remote_versions,
            install_version,
            list_installed_versions,
            launch_game,
            get_mcp_port,
            set_mcp_port,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

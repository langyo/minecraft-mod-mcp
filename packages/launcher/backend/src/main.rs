#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use serde::Serialize;
use std::collections::HashMap;
use std::sync::atomic::{AtomicU32, Ordering};
use tauri::Manager;
use tokio::sync::Mutex;

static NEXT_PID: AtomicU32 = AtomicU32::new(1);

#[derive(Serialize, Clone)]
struct RunningProcess {
    id: u32,
    pid: u32,
    version_id: String,
    loader: String,
    started_at: u64,
    mcp_port: Option<u16>,
}

struct AppState {
    config: Mutex<_shared_mc_settings::LauncherConfig>,
    processes: Mutex<HashMap<u32, RunningProcess>>,
    child_pids: Mutex<HashMap<u32, u32>>,
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
async fn add_offline_account(state: tauri::State<'_, AppState>, username: String, uuid: Option<String>) -> Result<CommandResult<()>, String> {
    let account = match uuid {
        Some(u) if !u.is_empty() => _shared_mc_settings::Account::new_offline_with_uuid(username, u),
        _ => _shared_mc_settings::Account::new_offline(username),
    };
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
async fn launch_game(state: tauri::State<'_, AppState>, version_id: String, loader: Option<String>) -> Result<CommandResult<u32>, String> {
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

    let resolved_loader = match loader.as_deref() {
        Some(l) => l.parse::<_shared_mc_version::Loader>().unwrap_or(_shared_mc_version::Loader::Forge),
        None => _shared_mc_version::Loader::Forge,
    };

    let natives_dir = _shared_core::platform::natives_dir(&version_id);
    if let Err(e) = _shared_mc_launch::extract_natives(&vj, &natives_dir) {
        eprintln!("warning: failed to extract natives: {e}");
    }

    let launch_config = _shared_mc_launch::LaunchConfig {
        version_id: version_id.clone(),
        mc_dir: Some(mc_dir.clone()),
        loader: resolved_loader,
        mod_jar: None,
        mcp_port,
        dry_run: false,
        max_memory_mb: Some(cfg.max_memory_mb),
        min_memory_mb: Some(cfg.min_memory_mb),
        extra_jvm_args: cfg.java_args.clone(),
        extra_game_args: cfg.game_args.clone(),
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
        .current_dir(&mc_dir)
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .spawn()
    {
        Ok(c) => c,
        Err(e) => return Ok(CommandResult::err(format!("failed to launch: {e}"))),
    };

    let os_pid = child.id().unwrap_or(0);
    let proc_id = NEXT_PID.fetch_add(1, Ordering::Relaxed);
    let loader_str = format!("{resolved_loader:?}").to_lowercase();

    let proc = RunningProcess {
        id: proc_id,
        pid: os_pid,
        version_id: version_id.clone(),
        loader: loader_str,
        started_at: std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs(),
        mcp_port,
    };

    state.processes.lock().await.insert(proc_id, proc.clone());
    state.child_pids.lock().await.insert(proc_id, os_pid);

    tokio::spawn(async move {
        let _ = child.wait().await;
    });

    Ok(CommandResult::ok(proc_id))
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

#[tauri::command]
async fn list_running_processes(state: tauri::State<'_, AppState>) -> Result<CommandResult<Vec<RunningProcess>>, String> {
    let procs = state.processes.lock().await;
    Ok(CommandResult::ok(procs.values().cloned().collect()))
}

#[tauri::command]
async fn kill_process(state: tauri::State<'_, AppState>, id: u32) -> Result<CommandResult<()>, String> {
    let child_pids = state.child_pids.lock().await;
    if let Some(&os_pid) = child_pids.get(&id) {
        #[cfg(windows)]
        { let _ = std::process::Command::new("taskkill").args(["/PID", &os_pid.to_string(), "/F"]).spawn(); }
        #[cfg(not(windows))]
        { let _ = std::process::Command::new("kill").args(["-9", &os_pid.to_string()]).spawn(); }
    }
    drop(child_pids);
    state.processes.lock().await.remove(&id);
    state.child_pids.lock().await.remove(&id);
    Ok(ok_unit())
}

fn main() {
    let config = _shared_mc_settings::LauncherConfig::load()
        .unwrap_or_else(|_| _shared_mc_settings::LauncherConfig::default());

    tauri::Builder::default()
        .setup(move |app| {
            app.manage(AppState {
                config: Mutex::new(config),
                processes: Mutex::new(HashMap::new()),
                child_pids: Mutex::new(HashMap::new()),
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
            list_running_processes,
            kill_process,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

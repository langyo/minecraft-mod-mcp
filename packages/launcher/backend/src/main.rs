#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use serde::Serialize;
use tauri::Manager;
use tokio::sync::Mutex;

struct AppState {
    mcp_port: Mutex<Option<u16>>,
}

#[derive(Serialize)]
struct CommandResult<T: Serialize> {
    ok: bool,
    data: Option<T>,
    error: Option<String>,
}

impl<T: Serialize> CommandResult<T> {
    fn ok(data: T) -> Self {
        Self { ok: true, data: Some(data), error: None }
    }
    fn err(msg: impl ToString) -> Self {
        Self { ok: false, data: None, error: Some(msg.to_string()) }
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
async fn get_mcp_port(state: tauri::State<'_, AppState>) -> Result<CommandResult<Option<u16>>, String> {
    let port = state.mcp_port.lock().await;
    Ok(CommandResult::ok(*port))
}

#[tauri::command]
async fn set_mcp_port(state: tauri::State<'_, AppState>, port: u16) -> Result<CommandResult<()>, String> {
    let mut p = state.mcp_port.lock().await;
    *p = Some(port);
    Ok(CommandResult::ok(()))
}

fn main() {
    tauri::Builder::default()
        .setup(|app| {
            app.manage(AppState {
                mcp_port: Mutex::new(None),
            });
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            list_versions,
            get_version,
            get_mcp_port,
            set_mcp_port,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

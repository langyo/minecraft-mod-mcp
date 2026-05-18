use crate::bridge::Bridge;
use crate::protocol::*;
use serde_json::Value;
use std::sync::Arc;
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::TcpListener;
use tokio::process::Child;
use tokio::sync::Mutex;
use tracing::{info, warn};

pub struct ControlServer {
    bridge: Arc<Bridge>,
    mc_proc: Arc<Mutex<Option<Child>>>,
    version: Arc<Mutex<Option<String>>>,
}

impl ControlServer {
    pub fn new(bridge: Arc<Bridge>) -> Self {
        Self {
            bridge,
            mc_proc: Arc::new(Mutex::new(None)),
            version: Arc::new(Mutex::new(None)),
        }
    }

    pub async fn start_tcp(&self, port: u16) -> anyhow::Result<()> {
        let listener = TcpListener::bind(format!("127.0.0.1:{}", port)).await?;
        info!("TCP control server on 127.0.0.1:{}", port);

        loop {
            let (stream, _addr) = listener.accept().await?;
            let bridge = self.bridge.clone();
            let mc_proc = self.mc_proc.clone();
            let version = self.version.clone();

            tokio::spawn(async move {
                let (reader, mut writer) = stream.into_split();
                let mut reader = BufReader::new(reader);
                let mut line = String::new();

                loop {
                    line.clear();
                    match reader.read_line(&mut line).await {
                        Ok(0) => break,
                        Ok(_) => {},
                        Err(e) => {
                            warn!("TCP read error: {}", e);
                            break;
                        },
                    };

                    let cmd_str = line.trim();
                    if cmd_str.is_empty() {
                        continue;
                    }

                    let cmd: ControlCommand = match serde_json::from_str(cmd_str) {
                        Ok(c) => c,
                        Err(e) => {
                            let resp = serde_json::json!({"error": format!("parse error: {}", e)});
                            let _ = writer
                                .write_all(
                                    format!("{}\n", serde_json::to_string(&resp).unwrap())
                                        .as_bytes(),
                                )
                                .await;
                            continue;
                        },
                    };

                    let result = handle_command(&cmd, &bridge, &mc_proc, &version).await;
                    let resp = serde_json::to_string(&result)
                        .unwrap_or_else(|e| format!(r#"{{"error":"serialize error: {}"}}"#, e));

                    if let Err(e) = writer.write_all(format!("{}\n", resp).as_bytes()).await {
                        warn!("TCP write error: {}", e);
                        break;
                    }
                }
            });
        }
    }
}

async fn handle_command(
    cmd: &ControlCommand,
    bridge: &Bridge,
    mc_proc: &Arc<Mutex<Option<Child>>>,
    version: &Arc<Mutex<Option<String>>>,
) -> Value {
    match cmd.cmd.as_str() {
        "status" => {
            let mut proc = mc_proc.lock().await;
            let alive = match proc.as_mut() {
                Some(p) => match p.try_wait() {
                    Ok(Some(_)) => false,
                    Ok(None) => true,
                    Err(_) => false,
                },
                None => false,
            };
            let ws_connected = *bridge.is_connected().lock().await;
            let ver = version.lock().await.clone();
            serde_json::json!({
                "mc_alive": alive,
                "ws_connected": ws_connected,
                "version": ver,
                "pid": proc.as_ref().and_then(|p| p.id()),
            })
        },

        "screenshot" => {
            let name = cmd
                .params
                .get("name")
                .and_then(|v| v.as_str())
                .unwrap_or("auto");
            match bridge.take_screenshot(name).await {
                Ok(path) => serde_json::json!({"path": path.to_string_lossy().to_string()}),
                Err(e) => serde_json::json!({"error": e.to_string()}),
            }
        },

        "click" => {
            let x = cmd.params.get("x").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
            let y = cmd.params.get("y").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
            let button = cmd
                .params
                .get("button")
                .and_then(|v| v.as_str())
                .unwrap_or("left");
            match bridge.click(x, y, button).await {
                Ok(r) => serde_json::to_value(r).unwrap_or_default(),
                Err(e) => serde_json::json!({"error": e.to_string()}),
            }
        },

        "press_key" => {
            let key = cmd
                .params
                .get("key")
                .and_then(|v| v.as_str())
                .unwrap_or("Enter");
            let hold = cmd
                .params
                .get("hold")
                .and_then(|v| v.as_f64())
                .unwrap_or(0.0);
            match bridge.press_key(key, hold).await {
                Ok(r) => serde_json::to_value(r).unwrap_or_default(),
                Err(e) => serde_json::json!({"error": e.to_string()}),
            }
        },

        "type_text" => {
            let text = cmd
                .params
                .get("text")
                .and_then(|v| v.as_str())
                .unwrap_or("");
            let enter = cmd
                .params
                .get("enter")
                .and_then(|v| v.as_bool())
                .unwrap_or(false);
            match bridge.type_text(text, enter).await {
                Ok(r) => serde_json::to_value(r).unwrap_or_default(),
                Err(e) => serde_json::json!({"error": e.to_string()}),
            }
        },

        "scroll" => {
            let clicks = cmd
                .params
                .get("clicks")
                .and_then(|v| v.as_i64())
                .unwrap_or(1) as i32;
            match bridge.scroll(clicks).await {
                Ok(r) => serde_json::to_value(r).unwrap_or_default(),
                Err(e) => serde_json::json!({"error": e.to_string()}),
            }
        },

        "hotkey" => {
            let keys = cmd
                .params
                .get("keys")
                .and_then(|v| v.as_str())
                .unwrap_or("");
            match bridge.hotkey(keys).await {
                Ok(r) => serde_json::to_value(r).unwrap_or_default(),
                Err(e) => serde_json::json!({"error": e.to_string()}),
            }
        },

        "command" => {
            let command = cmd
                .params
                .get("command")
                .and_then(|v| v.as_str())
                .unwrap_or("");
            match bridge.execute_command(command).await {
                Ok(r) => serde_json::to_value(r).unwrap_or_default(),
                Err(e) => serde_json::json!({"error": e.to_string()}),
            }
        },

        "player_info" => match bridge.player_info().await {
            Ok(r) => serde_json::to_value(r).unwrap_or_default(),
            Err(e) => serde_json::json!({"error": e.to_string()}),
        },

        "world_info" => match bridge.world_info().await {
            Ok(r) => serde_json::to_value(r).unwrap_or_default(),
            Err(e) => serde_json::json!({"error": e.to_string()}),
        },

        "screen_buttons" => match bridge.screen_buttons().await {
            Ok(r) => serde_json::to_value(r).unwrap_or_default(),
            Err(e) => serde_json::json!({"error": e.to_string()}),
        },

        "click_button_id" => {
            let id = cmd.params.get("id").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
            match bridge.click_button_id(id).await {
                Ok(r) => serde_json::to_value(r).unwrap_or_default(),
                Err(e) => serde_json::json!({"error": e.to_string()}),
            }
        },

        "kill" => {
            bridge.disconnect().await;
            let mut proc = mc_proc.lock().await;
            if let Some(p) = proc.as_mut() {
                let _ = p.kill().await;
            }
            *proc = None;
            serde_json::json!({"status": "killed"})
        },

        "wait" => {
            let seconds = cmd
                .params
                .get("seconds")
                .and_then(|v| v.as_f64())
                .unwrap_or(1.0);
            tokio::time::sleep(std::time::Duration::from_secs_f64(seconds)).await;
            serde_json::json!({"waited": seconds})
        },

        "ws" => {
            let method = cmd
                .params
                .get("method")
                .and_then(|v| v.as_str())
                .unwrap_or("ping");
            let params = cmd.params.get("params").cloned().unwrap_or(Value::Null);
            match bridge.send_rpc(method, params).await {
                Ok(r) => serde_json::to_value(r).unwrap_or_default(),
                Err(e) => serde_json::json!({"error": e.to_string()}),
            }
        },

        _ => serde_json::json!({"error": format!("unknown cmd: {}", cmd.cmd)}),
    }
}

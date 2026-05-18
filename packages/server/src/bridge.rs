use crate::protocol::*;
use base64::Engine;
use futures::{SinkExt, StreamExt};
use serde_json::Value;
use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::Arc;
use tokio::net::TcpListener;
use tokio::sync::{mpsc, oneshot, Mutex};
use tracing::{info, warn};

type PendingMap = Arc<Mutex<HashMap<String, oneshot::Sender<JsonRpcResponse>>>>;

pub struct Bridge {
    ws_tx: Arc<Mutex<Option<mpsc::UnboundedSender<String>>>>,
    pending: PendingMap,
    connected: Arc<Mutex<bool>>,
    req_counter: Arc<Mutex<u64>>,
    screenshot_dir: PathBuf,
}

impl Bridge {
    pub fn new(screenshot_dir: PathBuf) -> Self {
        std::fs::create_dir_all(&screenshot_dir).ok();
        Self {
            ws_tx: Arc::new(Mutex::new(None)),
            pending: Arc::new(Mutex::new(HashMap::new())),
            connected: Arc::new(Mutex::new(false)),
            req_counter: Arc::new(Mutex::new(0)),
            screenshot_dir,
        }
    }

    pub fn is_connected(&self) -> Arc<Mutex<bool>> {
        self.connected.clone()
    }

    pub async fn next_id(&self) -> String {
        let mut c = self.req_counter.lock().await;
        *c += 1;
        format!("r_{}", c)
    }

    pub async fn start_ws_server(&self, port: u16) -> anyhow::Result<()> {
        let app = axum::Router::new()
            .route("/ws", axum::routing::get({
                let bridge = self.clone_handle();
                move |ws| handle_ws_upgrade(ws, bridge)
            }))
            .route("/status", axum::routing::get({
                let connected = self.connected.clone();
                move || async move {
                    let c = *connected.lock().await;
                    axum::Json(serde_json::json!({"ws_connected": c}))
                }
            }));

        let listener = TcpListener::bind(format!("0.0.0.0:{}", port)).await?;
        info!("WebSocket server on port {}", port);
        axum::serve(listener, app).await?;
        Ok(())
    }

    fn clone_handle(&self) -> BridgeHandle {
        BridgeHandle {
            ws_tx: self.ws_tx.clone(),
            pending: self.pending.clone(),
            connected: self.connected.clone(),
        }
    }

    pub async fn send_rpc(
        &self,
        method: &str,
        params: Value,
    ) -> anyhow::Result<JsonRpcResponse> {
        let connected = self.connected.lock().await;
        if !*connected {
            return Err(anyhow::anyhow!("MC mod not connected"));
        }
        drop(connected);

        let rid = self.next_id().await;
        let (tx, rx) = oneshot::channel();

        {
            let mut pending = self.pending.lock().await;
            pending.insert(rid.clone(), tx);
        }

        let mut params_obj = match params {
            Value::Object(m) => serde_json::Map::from_iter(m.into_iter()),
            _ => {
                let mut m = serde_json::Map::new();
                m.insert("requestId".into(), params);
                m
            }
        };
        params_obj.insert("requestId".into(), Value::String(rid.clone()));

        let req = JsonRpcRequest::new(method, Value::Object(params_obj), &rid);

        let msg_str = serde_json::to_string(&req)?;

        {
            let ws_tx = self.ws_tx.lock().await;
            if let Some(sender) = ws_tx.as_ref() {
                sender.send(msg_str)?;
            } else {
                let mut pending = self.pending.lock().await;
                pending.remove(&rid);
                return Err(anyhow::anyhow!("WS sender not available"));
            }
        }

        match tokio::time::timeout(std::time::Duration::from_secs(30), rx).await {
            Ok(Ok(resp)) => Ok(resp),
            Ok(Err(_)) => {
                let mut pending = self.pending.lock().await;
                pending.remove(&rid);
                Err(anyhow::anyhow!("response channel dropped"))
            }
            Err(_) => {
                let mut pending = self.pending.lock().await;
                pending.remove(&rid);
                Err(anyhow::anyhow!("timeout after 30s"))
            }
        }
    }

    pub async fn take_screenshot(&self, name: &str) -> anyhow::Result<PathBuf> {
        let resp = self.send_rpc("screenshot", Value::Null).await?;

        let result_val = resp.result.ok_or_else(|| anyhow::anyhow!("no result"))?;
        let data_uri = result_val
            .as_str()
            .ok_or_else(|| anyhow::anyhow!("result not string"))?;

        if !data_uri.starts_with("data:image/png;base64,") {
            return Err(anyhow::anyhow!(
                "not a data URI: {}...",
                &data_uri[..50.min(data_uri.len())]
            ));
        }

        let b64 = &data_uri["data:image/png;base64,".len()..];
        let png_bytes = base64::engine::general_purpose::STANDARD.decode(b64)?;

        let ts = chrono::Utc::now().timestamp_millis();
        let filename = format!("{}_{}.png", name, ts);
        let path = self.screenshot_dir.join(&filename);
        tokio::fs::write(&path, &png_bytes).await?;

        info!(
            "Screenshot saved: {} ({} bytes)",
            path.display(),
            png_bytes.len()
        );
        Ok(path)
    }

    pub async fn click(&self, x: i32, y: i32, button: &str) -> anyhow::Result<JsonRpcResponse> {
        self.send_rpc(
            "click",
            serde_json::json!({"x": x.to_string(), "y": y.to_string(), "button": button}),
        )
        .await
    }

    pub async fn press_key(&self, key: &str, hold: f64) -> anyhow::Result<JsonRpcResponse> {
        let mut p = serde_json::json!({"key": key});
        if hold > 0.0 {
            p["hold_seconds"] = serde_json::Value::String(hold.to_string());
        }
        self.send_rpc("press_key", p).await
    }

    pub async fn type_text(&self, text: &str, enter: bool) -> anyhow::Result<JsonRpcResponse> {
        let mut p = serde_json::json!({"text": text});
        if enter {
            p["press_enter"] = serde_json::Value::String("true".into());
        }
        self.send_rpc("type_text", p).await
    }

    pub async fn scroll(&self, clicks: i32) -> anyhow::Result<JsonRpcResponse> {
        self.send_rpc("scroll", serde_json::json!({"clicks": clicks.to_string()}))
            .await
    }

    pub async fn hotkey(&self, keys: &str) -> anyhow::Result<JsonRpcResponse> {
        self.send_rpc("hotkey", serde_json::json!({"keys": keys}))
            .await
    }

    pub async fn execute_command(&self, cmd: &str) -> anyhow::Result<JsonRpcResponse> {
        self.send_rpc("execute_command", serde_json::json!({"command": cmd}))
            .await
    }

    pub async fn screen_buttons(&self) -> anyhow::Result<JsonRpcResponse> {
        self.send_rpc("get_screen_buttons", Value::Null).await
    }

    pub async fn click_button_id(&self, id: i32) -> anyhow::Result<JsonRpcResponse> {
        self.send_rpc("click_button_id", serde_json::json!({"id": id}))
            .await
    }

    pub async fn player_info(&self) -> anyhow::Result<JsonRpcResponse> {
        self.send_rpc("get_player_info", Value::Null).await
    }

    pub async fn world_info(&self) -> anyhow::Result<JsonRpcResponse> {
        self.send_rpc("get_world_info", Value::Null).await
    }

    pub async fn disconnect(&self) {
        *self.ws_tx.lock().await = None;
        *self.connected.lock().await = false;
    }
}

#[derive(Clone)]
struct BridgeHandle {
    ws_tx: Arc<Mutex<Option<mpsc::UnboundedSender<String>>>>,
    pending: PendingMap,
    connected: Arc<Mutex<bool>>,
}

async fn handle_ws_upgrade(
    ws: axum::extract::ws::WebSocketUpgrade,
    bridge: BridgeHandle,
) -> axum::response::Response {
    ws.on_upgrade(move |socket| handle_ws_connection(socket, bridge))
}

async fn handle_ws_connection(socket: axum::extract::ws::WebSocket, bridge: BridgeHandle) {
    info!("MC mod connected via WebSocket");
    *bridge.connected.lock().await = true;

    let (mut ws_sink, mut ws_stream) = socket.split();
    let (tx, mut rx) = mpsc::unbounded_channel::<String>();

    *bridge.ws_tx.lock().await = Some(tx);

    let init_msg = serde_json::json!({
        "jsonrpc": "2.0",
        "method": "initialize",
        "params": {},
        "id": "init"
    })
    .to_string();
    if ws_sink
        .send(axum::extract::ws::Message::Text(init_msg.into()))
        .await
        .is_err()
    {
        warn!("Failed to send init message");
    }

    let pending = bridge.pending.clone();
    let connected = bridge.connected.clone();
    let ws_tx_ref = bridge.ws_tx.clone();

    let recv_task = tokio::spawn(async move {
        while let Some(msg_result) = ws_stream.next().await {
            match msg_result {
                Ok(axum::extract::ws::Message::Text(text)) => {
                    let msg: Value = match serde_json::from_str(&text) {
                        Ok(v) => v,
                        Err(_) => continue,
                    };

                    let rid = msg
                        .get("id")
                        .and_then(|v| v.as_str())
                        .unwrap_or("");
                    if rid == "init" {
                        info!("Init response received");
                        continue;
                    }

                    let resp: JsonRpcResponse =
                        serde_json::from_value(msg.clone()).unwrap_or_else(|_| JsonRpcResponse {
                            jsonrpc: "2.0".into(),
                            result: Some(msg.clone()),
                            error: None,
                            id: None,
                        });

                    let mut pending_map = pending.lock().await;
                    if let Some(sender) = pending_map.remove(rid) {
                        let _ = sender.send(resp);
                    } else {
                        let result_str =
                            serde_json::to_string(&msg.get("result")).unwrap_or_default();
                        if result_str.contains("data:image") {
                            info!("Unsolicited image ({} bytes)", result_str.len());
                        } else {
                            info!("Unsolicited msg: {:.200}", result_str);
                        }
                    }
                }
                Ok(axum::extract::ws::Message::Close(_)) => break,
                Err(e) => {
                    warn!("WS recv error: {}", e);
                    break;
                }
                _ => {}
            }
        }

        *connected.lock().await = false;
        *ws_tx_ref.lock().await = None;
        info!("MC mod disconnected");
    });

    let send_task = tokio::spawn(async move {
        while let Some(msg) = rx.recv().await {
            if ws_sink
                .send(axum::extract::ws::Message::Text(msg.into()))
                .await
                .is_err()
            {
                break;
            }
        }
    });

    tokio::select! {
        _ = recv_task => {},
        _ = send_task => {},
    }
}

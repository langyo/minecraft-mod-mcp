use crate::protocol::*;
use base64::Engine;
use futures::{SinkExt, StreamExt};
use image::Rgba;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashMap;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct McpCallEvent {
    pub timestamp: String,
    pub direction: String,
    pub method: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub params: Option<Value>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub result: Option<Value>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub id: Option<String>,
    pub duration_ms: Option<u64>,
}

fn blend_grid(underlying: Rgba<u8>, overlay: &Rgba<u8>) -> Rgba<u8> {
    let alpha = overlay.0[3] as f32 / 255.0;
    Rgba([
        (underlying.0[0] as f32 * (1.0 - alpha) + overlay.0[0] as f32 * alpha) as u8,
        (underlying.0[1] as f32 * (1.0 - alpha) + overlay.0[1] as f32 * alpha) as u8,
        (underlying.0[2] as f32 * (1.0 - alpha) + overlay.0[2] as f32 * alpha) as u8,
        255,
    ])
}

fn load_system_font() -> ab_glyph::FontArc {
    use ab_glyph::FontArc;
    let candidates: Vec<std::path::PathBuf> = if cfg!(target_os = "windows") {
        vec![
            std::path::PathBuf::from(r"C:\Windows\Fonts\arial.ttf"),
            std::path::PathBuf::from(r"C:\Windows\Fonts\segoeui.ttf"),
            std::path::PathBuf::from(r"C:\Windows\Fonts\tahoma.ttf"),
        ]
    } else if cfg!(target_os = "macos") {
        vec![
            std::path::PathBuf::from("/System/Library/Fonts/Helvetica.ttc"),
            std::path::PathBuf::from("/System/Library/Fonts/SFNSMono.ttf"),
            std::path::PathBuf::from("/Library/Fonts/Arial.ttf"),
        ]
    } else {
        vec![
            std::path::PathBuf::from("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"),
            std::path::PathBuf::from("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
            std::path::PathBuf::from("/usr/share/fonts/TTF/DejaVuSans.ttf"),
            std::path::PathBuf::from("/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"),
            std::path::PathBuf::from("/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf"),
        ]
    };
    for path in &candidates {
        if path.exists() {
            if let Ok(data) = std::fs::read(path) {
                if let Ok(font) = FontArc::try_from_vec(data) {
                    info!("Loaded system font: {}", path.display());
                    return font;
                }
            }
        }
    }
    let fallback_data: Vec<u8> = vec![
        0x00, 0x01, 0x00, 0x00, 0x00, 0x0A, 0x00, 0x80, 0x00, 0x01, 0x00, 0x00, 0x47, 0x44,
        0x00, 0x20, 0x00, 0x00, 0x00, 0x00,
    ];
    warn!("No system font found, using minimal fallback (labels will be invisible)");
    FontArc::try_from_vec(fallback_data).unwrap_or_else(|_| {
        FontArc::try_from_vec(vec![0u8; 20]).expect("impossible")
    })
}
use std::path::PathBuf;
use std::sync::Arc;
use tokio::net::TcpListener;
use tokio::sync::{Mutex, mpsc, oneshot};
use tracing::{info, warn};

type PendingMap = Arc<Mutex<HashMap<String, oneshot::Sender<JsonRpcResponse>>>>;

pub struct ScreenshotResult {
    pub original: PathBuf,
    pub grid: PathBuf,
}

pub type EventBroadcaster = tokio::sync::broadcast::Sender<McpCallEvent>;

pub struct Bridge {
    ws_tx: Arc<Mutex<Option<mpsc::UnboundedSender<String>>>>,
    pending: PendingMap,
    connected: Arc<Mutex<bool>>,
    req_counter: Arc<Mutex<u64>>,
    screenshot_dir: PathBuf,
    stream_tx: Arc<Mutex<Option<mpsc::UnboundedSender<Vec<u8>>>>>,
    event_tx: EventBroadcaster,
}

impl Bridge {
    pub fn new(screenshot_dir: PathBuf) -> Self {
        std::fs::create_dir_all(&screenshot_dir).ok();
        let (event_tx, _) = tokio::sync::broadcast::channel(256);
        Self {
            ws_tx: Arc::new(Mutex::new(None)),
            pending: Arc::new(Mutex::new(HashMap::new())),
            connected: Arc::new(Mutex::new(false)),
            req_counter: Arc::new(Mutex::new(0)),
            screenshot_dir,
            stream_tx: Arc::new(Mutex::new(None)),
            event_tx,
        }
    }

    pub fn event_subscriber(&self) -> tokio::sync::broadcast::Receiver<McpCallEvent> {
        self.event_tx.subscribe()
    }

    fn emit_event(&self, event: McpCallEvent) {
        let _ = self.event_tx.send(event);
    }

    pub fn is_connected(&self) -> Arc<Mutex<bool>> {
        self.connected.clone()
    }

    pub async fn next_id(&self) -> String {
        let mut c = self.req_counter.lock().await;
        *c += 1;
        format!("r_{}", c)
    }

    pub async fn start_ws_server(self: Arc<Self>, port: u16) -> anyhow::Result<()> {
        let app = axum::Router::new()
            .route(
                "/",
                axum::routing::any({
                    let bridge = self.clone_handle();
                    move |req| async move { handle_root(req, bridge).await }
                }),
            )
            .route(
                "/stream",
                axum::routing::any({
                    let bridge = self.clone_handle();
                    move |req| async move { handle_stream(req, bridge).await }
                }),
            )
            .route(
                "/status",
                axum::routing::get({
                    let connected = self.connected.clone();
                    move || async move {
                        let c = *connected.lock().await;
                        axum::Json(serde_json::json!({"ws_connected": c}))
                    }
                }),
            )
            .route(
                "/events",
                axum::routing::get({
                    let bridge = self.clone_handle();
                    move |req| async move { handle_events(req, bridge).await }
                }),
            )
            .route(
                "/debug",
                axum::routing::get(|| async move {
                    axum::response::Html(DEBUG_PAGE_HTML)
                }),
            )
            .route(
                "/cmd",
                axum::routing::post({
                let bridge_arc = self.clone();
                move |body| async move { handle_cmd_post(body, bridge_arc).await }
                }),
            );

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
            stream_tx: self.stream_tx.clone(),
            event_tx: self.event_tx.clone(),
        }
    }

    pub async fn send_rpc(&self, method: &str, params: Value) -> anyhow::Result<JsonRpcResponse> {
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
            },
        };
        params_obj.insert("requestId".into(), Value::String(rid.clone()));

        let req = JsonRpcRequest::new(method, Value::Object(params_obj), &rid);

        let msg_str = serde_json::to_string(&req)?;

        self.emit_event(McpCallEvent {
            timestamp: chrono::Utc::now().to_rfc3339(),
            direction: "request".into(),
            method: method.into(),
            params: Some(req.params.clone()),
            result: None,
            error: None,
            id: Some(rid.clone()),
            duration_ms: None,
        });

        let start = std::time::Instant::now();

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

        let result = match tokio::time::timeout(std::time::Duration::from_secs(30), rx).await {
            Ok(Ok(resp)) => {
                let duration = start.elapsed().as_millis() as u64;
                self.emit_event(McpCallEvent {
                    timestamp: chrono::Utc::now().to_rfc3339(),
                    direction: "response".into(),
                    method: method.into(),
                    params: None,
                    result: resp.result.clone(),
                    error: resp.error.as_ref().map(|e| e.message.clone()),
                    id: Some(rid.clone()),
                    duration_ms: Some(duration),
                });
                Ok(resp)
            },
            Ok(Err(_)) => {
                let mut pending = self.pending.lock().await;
                pending.remove(&rid);
                Err(anyhow::anyhow!("response channel dropped"))
            },
            Err(_) => {
                let mut pending = self.pending.lock().await;
                pending.remove(&rid);
                Err(anyhow::anyhow!("timeout after 30s"))
            },
        };

        result
    }

    pub async fn take_screenshot(&self, name: &str) -> anyhow::Result<ScreenshotResult> {
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

        let orig_filename = format!("{}_{}.png", name, ts);
        let orig_path = self.screenshot_dir.join(&orig_filename);
        tokio::fs::write(&orig_path, &png_bytes).await?;
        info!("Screenshot saved: {} ({} bytes)", orig_path.display(), png_bytes.len());

        let grid_path = self.generate_grid_overlay(&orig_path, &png_bytes, ts)?;

        Ok(ScreenshotResult {
            original: orig_path,
            grid: grid_path,
        })
    }

    fn generate_grid_overlay(
        &self,
        _orig_path: &std::path::Path,
        png_bytes: &[u8],
        _ts: i64,
    ) -> anyhow::Result<PathBuf> {
        use ab_glyph::{Font as _, PxScale, ScaleFont as _};
        use image::{DynamicImage, GenericImage, GenericImageView, Rgba};
        use imageproc::drawing::draw_text_mut;

        let img = image::load_from_memory(png_bytes)?;
        let (w, h) = img.dimensions();
        let mut canvas: DynamicImage = img;

        let step: u32 = 100;
        let thickness: u32 = 3;
        let grid_color = Rgba([255, 0, 0, 180]);
        let label_color = Rgba([255, 255, 0, 255]);

        for gy in (0..h).step_by(step as usize) {
            for dy in 0..thickness {
                let y = gy + dy;
                if y >= h {
                    break;
                }
                for x in 0..w {
                    canvas.put_pixel(x, y, blend_grid(canvas.get_pixel(x, y), &grid_color));
                }
            }
        }
        for gx in (0..w).step_by(step as usize) {
            for dx in 0..thickness {
                let x = gx + dx;
                if x >= w {
                    break;
                }
                for y in 0..h {
                    canvas.put_pixel(x, y, blend_grid(canvas.get_pixel(x, y), &grid_color));
                }
            }
        }

        let font = load_system_font();
        let scale = PxScale::from(18.0);

        for gx in (step..w).step_by(step as usize) {
            let label = format!("{}", gx);
            let scaled_font = font.as_scaled(scale);
            let text_w = label.chars().map(|c| scaled_font.h_advance(font.glyph_id(c))).sum::<f32>() as i32;
            let tx = (gx as i32 + 6).min((w - text_w as u32 - 4) as i32).max(0) as i32;
            let ty = 6i32;
            draw_text_mut(&mut canvas, label_color, tx, ty, scale, &font, &label);
        }
        for gy in (step..h).step_by(step as usize) {
            let label = format!("{}", gy);
            let tx = 6i32;
            let ty = (gy as i32 + 6).min((h - 24) as i32).max(0) as i32;
            draw_text_mut(&mut canvas, label_color, tx, ty, scale, &font, &label);
        }

        let stem = _orig_path
            .file_stem()
            .unwrap_or_default()
            .to_string_lossy();
        let grid_filename = format!("{}_grid.png", stem);
        let grid_path = self.screenshot_dir.join(&grid_filename);
        canvas.save(&grid_path)?;
        info!("Grid overlay saved: {}", grid_path.display());

        Ok(grid_path)
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
    stream_tx: Arc<Mutex<Option<mpsc::UnboundedSender<Vec<u8>>>>>,
    event_tx: EventBroadcaster,
}

async fn handle_cmd_post(
    axum::Json(cmd): axum::Json<serde_json::Value>,
    bridge: Arc<Bridge>,
) -> axum::Json<serde_json::Value> {
    use crate::control::handle_http_cmd;
    let result = handle_http_cmd(cmd, &bridge).await;
    axum::Json(result)
}

async fn handle_root(
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
    let stream_tx_clone = bridge.stream_tx.clone();

    let recv_task = tokio::spawn(async move {
        while let Some(msg_result) = ws_stream.next().await {
            match msg_result {
                Ok(axum::extract::ws::Message::Text(text)) => {
                    let msg: Value = match serde_json::from_str(&text) {
                        Ok(v) => v,
                        Err(_) => continue,
                    };

                    let rid = msg.get("id").and_then(|v| v.as_str()).unwrap_or("");
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

                    let method = msg.get("method").and_then(|v| v.as_str()).unwrap_or("");
                    if method == "video_frame" {
                        if let Some(data_b64) = msg.get("params").and_then(|p| p.get("data")).and_then(|v| v.as_str()) {
                            if let Ok(jpeg_bytes) = base64::engine::general_purpose::STANDARD.decode(data_b64) {
                                let stream_tx = stream_tx_clone.lock().await;
                                if let Some(tx) = stream_tx.as_ref() {
                                    let _ = tx.send(jpeg_bytes);
                                }
                            }
                        }
                        continue;
                    }

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
                },
                Ok(axum::extract::ws::Message::Close(_)) => break,
                Err(e) => {
                    warn!("WS recv error: {}", e);
                    break;
                },
                _ => {},
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

async fn handle_stream(
    ws: axum::extract::ws::WebSocketUpgrade,
    bridge: BridgeHandle,
) -> axum::response::Response {
    ws.on_upgrade(move |socket| handle_stream_connection(socket, bridge))
}

async fn handle_stream_connection(socket: axum::extract::ws::WebSocket, bridge: BridgeHandle) {
    info!("Stream viewer connected");
    let (mut ws_sink, mut ws_stream) = socket.split();
    let (tx, mut rx) = mpsc::unbounded_channel::<Vec<u8>>();

    *bridge.stream_tx.lock().await = Some(tx);

    let stream_tx_ref = bridge.stream_tx.clone();

    let recv_task = tokio::spawn(async move {
        while ws_stream.next().await.is_some() {}
        info!("Stream viewer disconnected");
    });

    let send_task = tokio::spawn(async move {
        while let Some(jpeg_bytes) = rx.recv().await {
            if ws_sink
                .send(axum::extract::ws::Message::Binary(jpeg_bytes.into()))
                .await
                .is_err()
            {
                break;
            }
        }
        *stream_tx_ref.lock().await = None;
    });

    tokio::select! {
        _ = recv_task => {},
        _ = send_task => {},
    }
}

async fn handle_events(
    axum::extract::Query(_params): axum::extract::Query<std::collections::HashMap<String, String>>,
    bridge: BridgeHandle,
) -> axum::response::Sse<impl futures::Stream<Item = Result<axum::response::sse::Event, std::convert::Infallible>>> {
    let mut rx = bridge.event_tx.subscribe();
    let stream = async_stream::stream! {
        loop {
            match rx.recv().await {
                Ok(event) => {
                    let data = serde_json::to_string(&event).unwrap_or_default();
                    yield Ok(axum::response::sse::Event::default().data(data));
                },
                Err(tokio::sync::broadcast::error::RecvError::Lagged(n)) => {
                    let data = format!(r#"{{"type":"lagged","count":{}}}"#, n);
                    yield Ok(axum::response::sse::Event::default().data(data));
                },
                Err(_) => break,
            }
        }
    };
    axum::response::Sse::new(stream).keep_alive(
        axum::response::sse::KeepAlive::new()
            .interval(std::time::Duration::from_secs(15))
            .text("ping"),
    )
}

static DEBUG_PAGE_HTML: &str = r##"<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>MCP Debug</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{background:#0d1117;color:#c9d1d9;font-family:'Segoe UI',system-ui,sans-serif;height:100vh;display:flex;flex-direction:column;overflow:hidden}
.header{background:#161b22;border-bottom:1px solid #30363d;padding:8px 16px;display:flex;align-items:center;gap:12px;flex-shrink:0}
.header h1{font-size:16px;color:#58a6ff}
.dot{width:10px;height:10px;border-radius:50%;display:inline-block}
.dot.on{background:#3fb950}.dot.off{background:#f85149}
.main{display:flex;flex:1;overflow:hidden}
.left{flex:1;display:flex;flex-direction:column;border-right:1px solid #30363d;min-width:0}
.vbox{flex:1;display:flex;align-items:center;justify-content:center;background:#010409;position:relative;overflow:hidden}
.vbox canvas{max-width:100%;max-height:100%;image-rendering:pixelated}
.vctrl{background:#161b22;padding:6px 12px;display:flex;gap:8px;align-items:center;border-top:1px solid #30363d;flex-shrink:0}
.vctrl button{background:#21262d;color:#c9d1d9;border:1px solid #30363d;padding:4px 12px;border-radius:4px;cursor:pointer;font-size:12px}
.vctrl button:hover{background:#30363d}
.vctrl button:disabled{opacity:.4;cursor:default}
.fps{color:#8b949e;font-size:11px;font-family:monospace}
.overlay{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;background:#0d1117cc;z-index:10}
.overlay span{color:#8b949e;font-size:14px}
.right{width:420px;display:flex;flex-direction:column;flex-shrink:0}
.rhdr{background:#161b22;padding:8px 12px;border-bottom:1px solid #30363d;display:flex;align-items:center;justify-content:space-between;flex-shrink:0}
.rhdr h2{font-size:14px;color:#58a6ff}
.rhdr span{font-size:11px;color:#8b949e}
.rhdr button{background:#21262d;color:#c9d1d9;border:1px solid #30363d;padding:2px 8px;border-radius:3px;cursor:pointer;font-size:10px}
.clist{flex:1;overflow-y:auto;padding:4px}
.cent{background:#161b22;border:1px solid #30363d;border-radius:4px;margin-bottom:4px;font-size:11px;overflow:hidden}
.cent .ch{padding:6px 8px;display:flex;align-items:center;gap:6px;cursor:pointer}
.cent .ch:hover{background:#1c2128}
.dir{font-size:9px;padding:1px 4px;border-radius:3px;font-weight:600;text-transform:uppercase}
.dir.req{background:#1f6feb33;color:#58a6ff}
.dir.res{background:#3fb95033;color:#3fb950}
.dir.err{background:#f8514933;color:#f85149}
.mtd{color:#d2a8ff;font-family:monospace;font-weight:600}
.dur{color:#8b949e;font-size:10px;margin-left:auto}
.ts{color:#484f58;font-size:10px;margin-left:6px}
.cb{display:none;padding:4px 8px 8px;border-top:1px solid #21262d}
.cb.open{display:block}
.cb pre{color:#8b949e;font-size:10px;white-space:pre-wrap;word-break:break-all;max-height:200px;overflow-y:auto;background:#0d1117;padding:6px;border-radius:3px;margin-top:4px}
.cpanel{background:#161b22;border-top:1px solid #30363d;padding:8px 12px;flex-shrink:0}
.crow{display:flex;gap:4px}
.crow input,.crow select{background:#0d1117;color:#c9d1d9;border:1px solid #30363d;padding:4px 6px;border-radius:3px;font-size:11px;font-family:monospace}
.crow input{flex:1}
.crow button{background:#21262d;color:#c9d1d9;border:1px solid #30363d;padding:4px 8px;border-radius:3px;cursor:pointer;font-size:11px}
.crow button:hover{background:#30363d}
.crow button.send{background:#1f6feb;border-color:#388bfd;color:#fff}
</style>
</head>
<body>
<div class="header">
<h1>MCP Debug</h1>
<span class="dot off" id="dot"></span>
<span id="dotLabel" style="font-size:12px;color:#8b949e">Connecting...</span>
<span style="margin-left:auto;font-size:11px;color:#484f58" id="mcSt"></span>
</div>
<div class="main">
<div class="left">
<div class="vbox" id="vbox"><canvas id="cvs"></canvas><div class="overlay" id="ovl"><span>Stream not started</span></div></div>
<div class="vctrl">
<button id="btnS" onclick="startV()">Start</button>
<button id="btnT" onclick="stopV()" disabled>Stop</button>
<span class="fps" id="fps">--</span>
<span class="fps" id="res"></span>
</div>
</div>
<div class="right">
<div class="rhdr"><h2>MCP Calls</h2><span id="cc">0 calls</span><button onclick="clearC()">Clear</button></div>
<div class="clist" id="cl"></div>
<div class="cpanel"><div class="crow">
<select id="sel" onchange="onSel()">
<option value="screenshot">screenshot</option>
<option value="click">click</option>
<option value="inject_click">inject_click</option>
<option value="press_key">press_key</option>
<option value="type_text">type_text</option>
<option value="scroll">scroll</option>
<option value="hotkey">hotkey</option>
<option value="command">command</option>
<option value="status">status</option>
<option value="player_info">player_info</option>
<option value="world_info">world_info</option>
<option value="screen_buttons">screen_buttons</option>
<option value="video_start">video_start</option>
<option value="video_stop">video_stop</option>
</select>
<input id="ex" placeholder='{"x":100,"y":200}'/>
<button class="send" onclick="sendC()">Send</button>
</div></div>
</div>
</div>
<script>
const P=location.port||9876,H=location.hostname||'127.0.0.1';
let cws=null,sws=null,ess=null,fc=0,lt=performance.now(),tot=0;
const cvs=document.getElementById('cvs'),cx=cvs.getContext('2d'),cl=document.getElementById('cl');
function dot(ok,t){const d=document.getElementById('dot');d.className='dot '+(ok?'on':'off');document.getElementById('dotLabel').textContent=t||(ok?'Connected':'Disconnected')}
function startV(){
if(sws)return;
if(!cws||cws.readyState!==1){cws=new WebSocket('ws://'+H+':'+P+'/');cws.onopen=()=>cws.send(JSON.stringify({jsonrpc:'2.0',method:'video_start',params:{},id:'vs'}))}
else cws.send(JSON.stringify({jsonrpc:'2.0',method:'video_start',params:{},id:'vs'}));
document.getElementById('ovl').style.display='none';
sws=new WebSocket('ws://'+H+':'+P+'/stream');
sws.binaryType='arraybuffer';
sws.onmessage=ev=>{const b=new Blob([ev.data],{type:'image/jpeg'}),u=URL.createObjectURL(b),i=new Image();i.onload=()=>{if(cvs.width!==i.width||cvs.height!==i.height){cvs.width=i.width;cvs.height=i.height;document.getElementById('res').textContent=i.width+'x'+i.height}cx.drawImage(i,0,0);URL.revokeObjectURL(u);fc++};i.src=u};
sws.onclose=()=>{sws=null;document.getElementById('btnS').disabled=false;document.getElementById('btnT').disabled=true;document.getElementById('ovl').style.display='flex';document.getElementById('ovl').querySelector('span').textContent='Stream stopped'};
document.getElementById('btnS').disabled=true;document.getElementById('btnT').disabled=false;
}
function stopV(){
if(cws&&cws.readyState===1)cws.send(JSON.stringify({jsonrpc:'2.0',method:'video_stop',params:{},id:'vt'}));
if(sws){sws.close();sws=null}
document.getElementById('btnS').disabled=false;document.getElementById('btnT').disabled=true;
}
setInterval(()=>{const n=performance.now(),f=Math.round(fc*1000/(n-lt));document.getElementById('fps').textContent=f+' fps';fc=0;lt=n},1000);
function connE(){if(ess)ess.close();ess=new EventSource('http://'+H+':'+P+'/events');ess.onmessage=ev=>{try{addC(JSON.parse(ev.data))}catch(e){}};ess.onopen=()=>dot(true,'Events OK');ess.onerror=()=>{dot(false,'Events reconnect...');setTimeout(connE,3000)}}
function esc(s){const d=document.createElement('div');d.textContent=s;return d.innerHTML}
function addC(e){
tot++;document.getElementById('cc').textContent=tot+' calls';
const d=document.createElement('div');d.className='cent';
const isR=e.direction==='response',cls=e.error?'err':(isR?'res':'req');
const dur=e.duration_ms!=null?e.duration_ms+'ms':'';
const err=e.error?' <span style="color:#f85149">'+esc(e.error.substring(0,80))+'</span>':'';
const res=e.result?'<pre>'+esc(JSON.stringify(e.result,null,2).substring(0,500))+'</pre>':'';
const par=e.params?'<pre>'+esc(JSON.stringify(e.params,null,2).substring(0,500))+'</pre>':'';
const ts=e.timestamp?e.timestamp.split('T')[1].split('.')[0]:'';
d.innerHTML='<div class="ch" onclick="this.nextElementSibling.classList.toggle(\'open\')">'
+'<span class="dir '+cls+'">'+esc(e.direction)+'</span>'
+'<span class="mtd">'+esc(e.method)+'</span>'+err
+'<span class="dur">'+dur+'</span><span class="ts">'+ts+'</span></div>'
+'<div class="cb">'+par+res+'</div>';
cl.prepend(d);
while(cl.children.length>200)cl.removeChild(cl.lastChild);
}
function clearC(){cl.innerHTML='';tot=0;document.getElementById('cc').textContent='0 calls'}
function onSel(){const v=document.getElementById('sel').value,h={click:'{"x":100,"y":200}',inject_click:'{"x":100,"y":200}',press_key:'{"key":"enter"}',type_text:'{"text":"hello"}',scroll:'{"clicks":3}',hotkey:'{"keys":"ctrl+a"}',command:'{"command":"/time set day"}'};document.getElementById('ex').placeholder=h[v]||'';document.getElementById('ex').value=''}
async function sendC(){
const cmd=document.getElementById('sel').value;let ex={};try{ex=JSON.parse(document.getElementById('ex').value||'{}')}catch(e){}
try{const r=await fetch('http://'+H+':'+P+'/cmd',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({cmd,...ex})});const d=await r.json();addC({timestamp:new Date().toISOString(),direction:'response',method:cmd,params:ex,result:d,error:d.error,id:null,duration_ms:null})}
catch(e){addC({timestamp:new Date().toISOString(),direction:'response',method:cmd,params:ex,result:null,error:e.message,id:null,duration_ms:null})}
}
connE();onSel();
setInterval(async()=>{try{const r=await fetch('http://'+H+':'+P+'/status');const d=await r.json();document.getElementById('mcSt').textContent='MC: '+(d.ws_connected?'connected':'offline')}catch(e){}},5000);
</script>
</body>
</html>"##;

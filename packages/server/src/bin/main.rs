use clap::Parser;
use mcp_server::bridge::Bridge;
use mcp_server::control::ControlServer;
use std::path::PathBuf;
use std::sync::Arc;
use tokio::net::TcpStream;
use tracing::info;

#[derive(Parser)]
#[command(name = "mcp-server", about = "Minecraft MCP control server")]
struct Cli {
    #[arg(long, default_value = "9876")]
    ws_port: u16,

    #[arg(long, default_value = "9877")]
    tcp_port: u16,

    #[arg(long, default_value = "screenshots/vtty")]
    screenshot_dir: PathBuf,

    #[command(subcommand)]
    command: Option<CliCommand>,
}

#[derive(clap::Subcommand)]
enum CliCommand {
    Send {
        #[arg(short, long)]
        cmd: String,
    },
}

async fn send_raw_command(port: u16, json: &str) -> anyhow::Result<String> {
    use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
    let stream = TcpStream::connect(format!("127.0.0.1:{}", port)).await?;
    let (reader, mut writer) = stream.into_split();
    let mut reader = BufReader::new(reader);

    writer.write_all(format!("{}\n", json).as_bytes()).await?;

    let mut response = String::new();
    reader.read_line(&mut response).await?;
    Ok(response.trim().to_string())
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "mcp_server=info".into()),
        )
        .init();

    let cli = Cli::parse();

    if let Some(CliCommand::Send { cmd }) = cli.command {
        let parts: Vec<&str> = cmd.splitn(2, ' ').collect();
        let (cmd_name, rest) = (parts[0], parts.get(1).copied().unwrap_or(""));

        let json = match cmd_name {
            "launch" => {
                let version = rest;
                serde_json::json!({"cmd": "launch", "version": version}).to_string()
            },
            "kill" => r#"{"cmd":"kill"}"#.to_string(),
            "status" => r#"{"cmd":"status"}"#.to_string(),
            "screenshot" => {
                let name = if rest.is_empty() { "auto" } else { rest };
                serde_json::json!({"cmd": "screenshot", "name": name}).to_string()
            },
            "click" => {
                let coords: Vec<&str> = rest.split_whitespace().collect();
                let x: i64 = coords.first().and_then(|s| s.parse().ok()).unwrap_or(0);
                let y: i64 = coords.get(1).and_then(|s| s.parse().ok()).unwrap_or(0);
                let button = coords.get(2).copied().unwrap_or("left");
                serde_json::json!({"cmd": "click", "x": x, "y": y, "button": button}).to_string()
            },
            "press_key" => serde_json::json!({"cmd": "press_key", "key": rest}).to_string(),
            "type_text" => serde_json::json!({"cmd": "type_text", "text": rest}).to_string(),
            "scroll" => {
                let clicks: i64 = rest.parse().unwrap_or(1);
                serde_json::json!({"cmd": "scroll", "clicks": clicks}).to_string()
            },
            "hotkey" => serde_json::json!({"cmd": "hotkey", "keys": rest}).to_string(),
            "command" => serde_json::json!({"cmd": "command", "command": rest}).to_string(),
            "screen_buttons" => r#"{"cmd":"screen_buttons"}"#.to_string(),
            "player_info" => r#"{"cmd":"player_info"}"#.to_string(),
            "world_info" => r#"{"cmd":"world_info"}"#.to_string(),
            "click_button_id" => {
                let id: i64 = rest.parse().unwrap_or(0);
                serde_json::json!({"cmd": "click_button_id", "id": id}).to_string()
            },
            "wait" => {
                let seconds: f64 = rest.parse().unwrap_or(1.0);
                serde_json::json!({"cmd": "wait", "seconds": seconds}).to_string()
            },
            _ => {
                eprintln!("Unknown command: {}", cmd_name);
                std::process::exit(1);
            },
        };

        match send_raw_command(cli.tcp_port, &json).await {
            Ok(resp) => println!("{}", resp),
            Err(e) => eprintln!("Error: {}", e),
        }
        return Ok(());
    }

    let project_root = std::env::current_dir()?;

    let ss_dir = if cli.screenshot_dir.is_absolute() {
        cli.screenshot_dir
    } else {
        project_root.join(&cli.screenshot_dir)
    };

    let bridge = Arc::new(Bridge::new(ss_dir));
    let control = ControlServer::new(bridge.clone());

    let ws_port = cli.ws_port;
    let tcp_port = cli.tcp_port;

    let ws_task = tokio::spawn(async move {
        if let Err(e) = bridge.start_ws_server(ws_port).await {
            tracing::error!("WS server error: {}", e);
        }
    });

    let tcp_task = tokio::spawn(async move {
        if let Err(e) = control.start_tcp(tcp_port).await {
            tracing::error!("TCP server error: {}", e);
        }
    });

    info!("MCP server ready. WS={} TCP={}", ws_port, tcp_port);

    tokio::select! {
        _ = ws_task => {},
        _ = tcp_task => {},
    }

    Ok(())
}

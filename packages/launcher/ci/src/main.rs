use clap::{Parser, Subcommand};

#[derive(Parser)]
#[command(name = "mcp-ci", about = "Minecraft MCP CI helper")]
struct Cli {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    Check,
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    tracing_subscriber::fmt::init();

    let cli = Cli::parse();

    match cli.command {
        Commands::Check => {
            let versions = _shared_mc_version::all_versions();
            println!("{} versions configured", versions.len());
        }
    }

    Ok(())
}

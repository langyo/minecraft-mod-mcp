use clap::{Parser, Subcommand};

#[derive(Parser)]
#[command(name = "mcp-launcher", about = "Minecraft MCP Launcher CLI")]
struct Cli {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    List,
    Show { mc: String },
    Launch { mc: String, #[arg(long, default_value = "forge")] loader: String },
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    tracing_subscriber::fmt::init();

    let cli = Cli::parse();

    match cli.command {
        Commands::List => {
            let versions = _shared_mc_version::all_versions();
            println!("{:<12} {:<8} {:<12} {}", "MC Version", "Java", "FG Era", "Loaders");
            println!("{}", "-".repeat(60));
            for v in &versions {
                let loaders: Vec<String> = v.loaders().iter().map(|l| l.to_string()).collect();
                println!("{:<12} {:<8} {:<12} {}", v.mc_version, v.java, v.fg_era, loaders.join(", "));
            }
        }
        Commands::Show { mc } => {
            let info = _shared_mc_version::get_version(&mc)
                .ok_or_else(|| anyhow::anyhow!("version not found: {mc}"))?;
            println!("{}", serde_json::to_string_pretty(&info)?);
        }
        Commands::Launch { mc, loader } => {
            println!("launching {mc} with {loader}...");
        }
    }

    Ok(())
}

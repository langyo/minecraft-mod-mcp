use _shared_core::platform;
use _shared_mc_metadata::{resolve_classpath, VersionJson};
use _shared_mc_version::Loader;
use anyhow::Result;
use std::path::PathBuf;

pub struct LaunchConfig {
    pub version_id: String,
    pub mc_dir: Option<PathBuf>,
    pub loader: Loader,
    pub mod_jar: Option<PathBuf>,
    pub mcp_port: Option<u16>,
    pub dry_run: bool,
}

pub struct LaunchCommand {
    pub java: PathBuf,
    pub args: Vec<String>,
    pub classpath: String,
    pub main_class: String,
}

pub fn build_launch_command(config: &LaunchConfig, vj: &VersionJson) -> Result<LaunchCommand> {
    let mc_dir = config
        .mc_dir
        .clone()
        .unwrap_or_else(platform::mc_dir);

    let java = platform::java_exec(
        _shared_mc_version::get_version_by_id(&config.version_id)
            .map(|v| v.java)
            .unwrap_or(17),
    )?;

    let mut classpath_paths = resolve_classpath(&vj.libraries)?;

    let version_jar = platform::versions_dir()
        .join(&config.version_id)
        .join(format!("{}.jar", config.version_id));
    if version_jar.is_file() {
        classpath_paths.push(version_jar);
    }

    if let Some(ref mod_jar) = config.mod_jar {
        if mod_jar.is_file() {
            classpath_paths.push(mod_jar.clone());
        }
    }

    let separator = if cfg!(windows) { ";" } else { ":" };
    let classpath = classpath_paths
        .iter()
        .map(|p| p.to_string_lossy().to_string())
        .collect::<Vec<_>>()
        .join(separator);

    let (jvm_args, game_args) = vj.collect_all_args();

    let natives_dir = platform::natives_dir(&config.version_id);
    let assets_dir = platform::assets_dir();
    let assets_index = vj.assets.as_deref().unwrap_or(&config.version_id);
    let game_dir = mc_dir.to_string_lossy();

    let mut all_args = Vec::new();

    all_args.extend(jvm_args);

    let mut resolved_game = Vec::new();
    for arg in &game_args {
        let s = arg
            .replace("${auth_player_name}", "Player")
            .replace("${version_name}", &config.version_id)
            .replace("${game_directory}", &game_dir)
            .replace("${assets_root}", &assets_dir.to_string_lossy())
            .replace("${assets_index_name}", assets_index)
            .replace("${auth_uuid}", "0")
            .replace("${auth_access_token}", "0")
            .replace("${user_type}", "legacy")
            .replace("${version_type}", "release")
            .replace("${natives_directory}", &natives_dir.to_string_lossy())
            .replace("${launcher_name}", "MCP-Launcher")
            .replace("${launcher_version}", "0.1.0")
            .replace("${classpath}", &classpath)
            .replace("${classpath_separator}", separator)
            .replace("${library_directory}", &platform::libraries_dir().to_string_lossy());
        resolved_game.push(s);
    }

    all_args.extend(resolved_game);

    Ok(LaunchCommand {
        java,
        args: all_args,
        classpath,
        main_class: vj.main_class.clone(),
    })
}

use _shared_core::platform;
use _shared_mc_metadata::{resolve_classpath, VersionJson};
use _shared_mc_version::Loader;
use anyhow::Result;
use std::io::Read;
use std::path::PathBuf;

pub struct LaunchConfig {
    pub version_id: String,
    pub mc_dir: Option<PathBuf>,
    pub loader: Loader,
    pub mod_jar: Option<PathBuf>,
    pub mcp_port: Option<u16>,
    pub dry_run: bool,
    pub max_memory_mb: Option<u32>,
    pub min_memory_mb: Option<u32>,
    pub extra_jvm_args: Option<String>,
    pub extra_game_args: Option<String>,
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

    if let Some(max) = config.max_memory_mb {
        all_args.push(format!("-Xmx{max}m"));
    }
    if let Some(min) = config.min_memory_mb {
        all_args.push(format!("-Xms{min}m"));
    }
    if let Some(ref extra) = config.extra_jvm_args {
        for arg in extra.split_whitespace() {
            if !arg.is_empty() {
                all_args.push(arg.to_string());
            }
        }
    }

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

    if let Some(ref extra) = config.extra_game_args {
        for arg in extra.split_whitespace() {
            if !arg.is_empty() {
                all_args.push(arg.to_string());
            }
        }
    }

    Ok(LaunchCommand {
        java,
        args: all_args,
        classpath,
        main_class: vj.main_class.clone(),
    })
}

pub fn extract_natives(version_json: &VersionJson, natives_dir: &std::path::Path) -> Result<()> {
    std::fs::create_dir_all(natives_dir)?;

    let classifier_key = get_native_classifier();

    for lib in &version_json.libraries {
        if let Some(ref rules) = lib.rules {
            if !should_include_library(rules) {
                continue;
            }
        }

        let downloads = match lib.downloads {
            Some(ref d) => d,
            None => continue,
        };

        let classifiers = match downloads.classifiers {
            Some(ref c) => c,
            None => continue,
        };

        let classifier = match classifiers.get(&classifier_key) {
            Some(c) => c,
            None => continue,
        };

        let artifact_path = match classifier.get("path").and_then(|p| p.as_str()) {
            Some(p) => p,
            None => continue,
        };

        let jar_path = platform::libraries_dir().join(artifact_path);
        if !jar_path.is_file() {
            continue;
        }

        let file = std::fs::File::open(&jar_path)?;
        let mut archive = zip::ZipArchive::new(file)?;

        let exclude_patterns: Vec<String> = lib
            .extract
            .as_ref()
            .and_then(|e| e.exclude.clone())
            .unwrap_or_default();

        for i in 0..archive.len() {
            let mut entry = archive.by_index(i)?;
            let name = entry.name().to_string();

            if name.ends_with(".sha1") || name.ends_with(".git") {
                continue;
            }

            let skip = exclude_patterns.iter().any(|pat| {
                if let Some(rest) = pat.strip_prefix('/') {
                    name.starts_with(rest)
                } else {
                    name.contains(pat)
                }
            });
            if skip {
                continue;
            }

            let out_path = natives_dir.join(&name);
            if entry.is_dir() {
                std::fs::create_dir_all(&out_path)?;
            } else {
                if let Some(parent) = out_path.parent() {
                    std::fs::create_dir_all(parent)?;
                }
                let mut buf = Vec::new();
                entry.read_to_end(&mut buf)?;
                std::fs::write(&out_path, buf)?;
            }
        }
    }

    Ok(())
}

fn get_native_classifier() -> String {
    if cfg!(target_os = "windows") {
        if cfg!(target_arch = "x86_64") {
            "natives-windows".to_string()
        } else {
            "natives-windows-arm64".to_string()
        }
    } else if cfg!(target_os = "macos") {
        if cfg!(target_arch = "aarch64") {
            "natives-macos-arm64".to_string()
        } else {
            "natives-macos".to_string()
        }
    } else {
        "natives-linux".to_string()
    }
}

fn should_include_library(rules: &[_shared_mc_metadata::Rule]) -> bool {
    let mut allow = false;
    let mut deny = false;

    for rule in rules {
        let os_match = match &rule.os {
            Some(os) => {
                let name_ok = match &os.name {
                    Some(name) => match name.as_str() {
                        "windows" => cfg!(windows),
                        "linux" => cfg!(target_os = "linux"),
                        "osx" => cfg!(target_os = "macos"),
                        _ => true,
                    },
                    None => true,
                };
                name_ok
            }
            None => true,
        };

        if os_match {
            match rule.action.as_str() {
                "allow" => allow = true,
                "disallow" => deny = true,
                _ => {}
            }
        }
    }

    if rules.is_empty() {
        return true;
    }
    allow && !deny
}

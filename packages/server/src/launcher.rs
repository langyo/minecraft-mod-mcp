use serde_json::Value;
use std::path::{Path, PathBuf};
use tracing::info;

pub fn mc_dir() -> PathBuf {
    #[cfg(target_os = "windows")]
    {
        std::env::var("APPDATA")
            .map(PathBuf::from)
            .unwrap_or_else(|_| PathBuf::from("."))
            .join(".minecraft")
    }
    #[cfg(not(target_os = "windows"))]
    {
        std::env::var("HOME")
            .map(PathBuf::from)
            .unwrap_or_else(|_| PathBuf::from("."))
            .join(".minecraft")
    }
}

pub fn find_mod_jar(project_root: &Path, version: &str, loader: &str) -> Option<PathBuf> {
    let mod_dir = project_root
        .join("packages")
        .join("mods")
        .join(version)
        .join(loader)
        .join("build")
        .join("libs");

    for entry in std::fs::read_dir(&mod_dir).ok()? {
        let entry = entry.ok()?;
        let name = entry.file_name().to_string_lossy().to_string();
        if name.ends_with(".jar") && !name.contains("sources") && !name.contains("javadoc") {
            return Some(entry.path());
        }
    }
    None
}

pub fn merge_version_json(version_name: &str) -> anyhow::Result<Value> {
    let versions_dir = mc_dir().join("versions");
    let json_path = versions_dir
        .join(version_name)
        .join(format!("{}.json", version_name));
    let vj: Value = serde_json::from_str(&std::fs::read_to_string(&json_path)?)?;

    let parent = vj.get("inheritsFrom").and_then(|v| v.as_str());
    match parent {
        Some(parent_name) => {
            let parent_path = versions_dir
                .join(parent_name)
                .join(format!("{}.json", parent_name));
            if parent_path.exists() {
                let pj: Value = serde_json::from_str(&std::fs::read_to_string(&parent_path)?)?;
                Ok(merge_values(pj, vj))
            } else {
                Ok(vj)
            }
        },
        None => Ok(vj),
    }
}

fn merge_values(base: Value, overlay: Value) -> Value {
    match (base, overlay) {
        (Value::Object(mut base_map), Value::Object(overlay_map)) => {
            for (k, v) in overlay_map {
                base_map.insert(k, v);
            }
            Value::Object(base_map)
        },
        (_, overlay) => overlay,
    }
}

pub fn build_classpath(vj: &Value) -> anyhow::Result<String> {
    let libs = vj
        .get("libraries")
        .and_then(|v| v.as_array())
        .cloned()
        .unwrap_or_default();

    let mc = mc_dir();
    let mut cp_parts: Vec<String> = Vec::new();

    for lib in &libs {
        if let Some(rules) = lib.get("rules").and_then(|v| v.as_array())
            && !should_include_lib(rules)
        {
            continue;
        }

        if let Some(path) = lib
            .get("downloads")
            .and_then(|d| d.get("artifact"))
            .and_then(|a| a.get("path"))
            .and_then(|p| p.as_str())
        {
            let full_path = mc.join("libraries").join(path);
            if full_path.exists() {
                cp_parts.push(full_path.to_string_lossy().to_string());
            }
        }
    }

    Ok(cp_parts.join(if cfg!(windows) { ";" } else { ":" }))
}

fn should_include_lib(rules: &[Value]) -> bool {
    let os_name = if cfg!(target_os = "windows") {
        "windows"
    } else if cfg!(target_os = "macos") {
        "osx"
    } else {
        "linux"
    };

    let mut allowed = false;
    let mut disallowed = false;

    for rule in rules {
        let action = rule.get("action").and_then(|v| v.as_str()).unwrap_or("");
        let os_match = rule
            .get("os")
            .and_then(|os| os.get("name"))
            .and_then(|n| n.as_str())
            .map(|n| n == os_name)
            .unwrap_or(true);

        if os_match {
            match action {
                "allow" => allowed = true,
                "disallow" => disallowed = true,
                _ => {},
            }
        }
    }

    if rules.is_empty() {
        return true;
    }
    allowed && !disallowed
}

pub fn get_main_class(vj: &Value) -> Option<String> {
    vj.get("mainClass")
        .and_then(|v| v.as_str())
        .map(String::from)
}

pub fn get_jvm_args(vj: &Value) -> Vec<String> {
    let mut args = Vec::new();

    if let Some(arguments) = vj
        .get("arguments")
        .and_then(|v| v.get("jvm"))
        .and_then(|v| v.as_array())
    {
        for arg in arguments {
            if let Value::String(s) = arg {
                args.push(resolve_arg(s));
            }
        }
    }

    args
}

pub fn get_game_args(vj: &Value) -> Vec<String> {
    let mut args = Vec::new();

    if let Some(arguments) = vj
        .get("arguments")
        .and_then(|v| v.get("game"))
        .and_then(|v| v.as_array())
    {
        for arg in arguments {
            if let Value::String(s) = arg {
                args.push(s.clone());
            }
        }
    } else if let Some(mc_args) = vj.get("minecraftArguments").and_then(|v| v.as_str()) {
        args.extend(mc_args.split_whitespace().map(String::from));
    }

    args
}

fn resolve_arg(s: &str) -> String {
    let mc = mc_dir();
    s.replace(
        "${natives_directory}",
        mc.join("versions").to_string_lossy().as_ref(),
    )
    .replace(
        "${library_directory}",
        mc.join("libraries").to_string_lossy().as_ref(),
    )
    .replace("${classpath}", "")
    .replace(
        "${classpath_separator}",
        if cfg!(windows) { ";" } else { ":" },
    )
    .replace("${game_directory}", mc.to_string_lossy().as_ref())
    .replace(
        "${assets_root}",
        mc.join("assets").to_string_lossy().as_ref(),
    )
    .replace("${player_name}", "MCPPlayer")
    .replace("${version_name}", "1.21.7")
    .replace(
        "${game_assets}",
        mc.join("assets").to_string_lossy().as_ref(),
    )
    .replace("${auth_player_name}", "MCPPlayer")
    .replace("${auth_uuid}", "00000000-0000-0000-0000-000000000000")
    .replace("${auth_access_token}", "0")
    .replace("${user_type}", "legacy")
    .replace("${version_type}", "release")
}

pub fn find_java() -> Option<String> {
    if let Ok(java_home) = std::env::var("JAVA_HOME") {
        let java = PathBuf::from(java_home).join("bin").join(if cfg!(windows) {
            "java.exe"
        } else {
            "java"
        });
        if java.exists() {
            return Some(java.to_string_lossy().to_string());
        }
    }
    which::which("java")
        .ok()
        .map(|p| p.to_string_lossy().to_string())
}

pub fn install_mod(project_root: &Path, version: &str, loader: &str) -> anyhow::Result<PathBuf> {
    let jar = find_mod_jar(project_root, version, loader)
        .ok_or_else(|| anyhow::anyhow!("mod jar not found for {} {}", version, loader))?;

    let mods_dir = mc_dir().join("mods");
    if mods_dir.exists() {
        for entry in std::fs::read_dir(&mods_dir)? {
            let entry = entry?;
            let name = entry.file_name().to_string_lossy().to_string();
            if name.ends_with(".jar") {
                std::fs::remove_file(entry.path())?;
                info!("Removed old mod: {}", name);
            }
        }
    }

    let dest = mods_dir.join(jar.file_name().unwrap_or_default());
    std::fs::create_dir_all(&mods_dir)?;
    std::fs::copy(&jar, &dest)?;
    info!("Installed mod: {} -> {}", jar.display(), dest.display());
    Ok(dest)
}

pub async fn launch_mc(
    version_name: &str,
    _project_root: &Path,
    extra_jvm_args: &[String],
) -> anyhow::Result<tokio::process::Child> {
    let vj = merge_version_json(version_name)?;
    let classpath = build_classpath(&vj)?;
    let main_class =
        get_main_class(&vj).ok_or_else(|| anyhow::anyhow!("no mainClass in version JSON"))?;

    let java = find_java().ok_or_else(|| anyhow::anyhow!("Java not found"))?;

    let mc = mc_dir();

    let mut cmd = tokio::process::Command::new(&java);
    cmd.arg("-Xmx4G")
        .arg("-Xms1G")
        .arg("-Dmcp.server=ws://127.0.0.1:9876")
        .arg(format!(
            "-Dminecraft.applet.TargetDirectory={}",
            mc.to_string_lossy()
        ));

    for arg in extra_jvm_args {
        cmd.arg(arg);
    }

    for arg in get_jvm_args(&vj) {
        if !arg.contains("natives_directory") {
            cmd.arg(&arg);
        }
    }

    cmd.arg("-cp").arg(&classpath).arg(&main_class);

    for arg in get_game_args(&vj) {
        cmd.arg(&arg);
    }

    cmd.env("MC_MCP_SERVER", "ws://127.0.0.1:9876")
        .current_dir(&mc)
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::piped());

    info!("Launching MC: {} {}", version_name, main_class);
    let child = cmd.spawn()?;
    info!("MC pid={}", child.id().unwrap_or(0));
    Ok(child)
}

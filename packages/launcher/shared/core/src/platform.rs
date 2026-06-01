use std::path::PathBuf;

pub fn mc_dir() -> PathBuf {
    dirs::home_dir()
        .expect("no home directory")
        .join(".minecraft")
}

pub fn versions_dir() -> PathBuf {
    mc_dir().join("versions")
}

pub fn libraries_dir() -> PathBuf {
    mc_dir().join("libraries")
}

pub fn assets_dir() -> PathBuf {
    mc_dir().join("assets")
}

pub fn natives_dir(version_id: &str) -> PathBuf {
    mc_dir()
        .join("versions")
        .join(version_id)
        .join("natives")
}

pub fn launcher_dir() -> PathBuf {
    mc_dir().join("mcp_launcher")
}

pub fn mod_jar_path(mc_version: &str, loader: &str) -> PathBuf {
    let project_dir = std::env::current_dir()
        .expect("no cwd")
        .join("packages")
        .join("mods")
        .join(mc_version)
        .join(loader);
    
    if loader == "fabric" {
        project_dir
            .join("build")
            .join("libs")
            .join(format!("mcpmod-fabric-{mc_version}-1.0.0.jar"))
    } else {
        project_dir
            .join("build")
            .join("libs")
            .join(format!("mcpmod-{loader}-{mc_version}-1.0.0.jar"))
    }
}

pub fn jdk_home(java_version: u32) -> Option<PathBuf> {
    if let Ok(val) = std::env::var(format!("JDK_{java_version}_HOME")) {
        let p = PathBuf::from(&val);
        if p.is_dir() {
            return Some(p);
        }
    }

    let jdks_dir = dirs::home_dir()?.join(".gradle").join("jdks");
    if !jdks_dir.is_dir() {
        return None;
    }

    let prefix = match java_version {
        8 => "eclipse_adoptium-8",
        17 => "eclipse_adoptium-17",
        21 => "eclipse_adoptium-21",
        _ => return None,
    };

    if let Ok(entries) = std::fs::read_dir(&jdks_dir) {
        for entry in entries.flatten() {
            let name = entry.file_name();
            let name = name.to_string_lossy();
            if name.starts_with(prefix) && !name.contains("lock") && entry.path().is_dir() {
                return Some(entry.path());
            }
        }
    }

    None
}

pub fn java_exec(java_version: u32) -> anyhow::Result<PathBuf> {
    let home = jdk_home(java_version).ok_or_else(|| {
        anyhow::anyhow!("Java not found: version {java_version}")
    })?;

    let exe = if cfg!(windows) {
        home.join("bin").join("java.exe")
    } else {
        home.join("bin").join("java")
    };

    if exe.is_file() {
        Ok(exe)
    } else {
        Err(anyhow::anyhow!("Java not found: version {java_version}"))
    }
}

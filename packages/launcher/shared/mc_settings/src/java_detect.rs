use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};
use tracing::{debug, warn};

use ts_rs::TS;

#[derive(Debug, Clone, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct JavaInfo {
    pub path: PathBuf,
    pub version: u32,
    pub vendor: String,
    pub is_jdk: bool,
}

pub fn detect_javas() -> Vec<JavaInfo> {
    let mut results = Vec::new();
    let mut seen = std::collections::HashSet::new();

    let candidates: Vec<PathBuf> = env_candidates()
        .into_iter()
        .chain(gradle_candidates())
        .chain(platform_candidates())
        .filter(|p| p.is_dir())
        .filter_map(|p| p.canonicalize().ok())
        .filter(|p| seen.insert(p.clone()))
        .collect();

    for home in candidates {
        let java_bin = if cfg!(windows) {
            home.join("bin").join("java.exe")
        } else {
            home.join("bin").join("java")
        };

        if !java_bin.is_file() {
            debug!("Skipping {:?}: no java binary", home);
            continue;
        }

        match parse_release_file(&home) {
            Some((version, vendor)) => {
                let is_jdk =
                    home.join("lib").join("tools.jar").exists() || home.join("include").is_dir();

                debug!(
                    "Found Java {} ({}) at {:?}, is_jdk={}",
                    version, vendor, home, is_jdk
                );
                results.push(JavaInfo {
                    path: home.clone(),
                    version,
                    vendor,
                    is_jdk,
                });
            }
            None => {
                warn!("Could not parse release file in {:?}", home);
            }
        }
    }

    results.sort_by(|a, b| b.version.cmp(&a.version));
    results
}

fn env_candidates() -> Vec<PathBuf> {
    let mut paths = Vec::new();

    for var in &[
        "JAVA_HOME",
        "JDK_8_HOME",
        "JDK_16_HOME",
        "JDK_17_HOME",
        "JDK_21_HOME",
        "JDK_25_HOME",
    ] {
        if let Ok(val) = std::env::var(var) {
            let p = PathBuf::from(&val);
            if p.is_dir() {
                debug!("Env candidate {}: {:?}", var, p);
                paths.push(p);
            }
        }
    }

    paths
}

fn gradle_candidates() -> Vec<PathBuf> {
    let mut paths = Vec::new();

    let jdks_dir = match dirs::home_dir() {
        Some(h) => h.join(".gradle").join("jdks"),
        None => return paths,
    };

    if !jdks_dir.is_dir() {
        return paths;
    }

    if let Ok(entries) = std::fs::read_dir(&jdks_dir) {
        for entry in entries.flatten() {
            let name = entry.file_name();
            let name_str = name.to_string_lossy();
            if !name_str.contains("lock") && entry.path().is_dir() {
                let candidate = entry.path();
                let release = candidate.join("release");
                if release.is_file() {
                    debug!("Gradle JDK candidate: {:?}", candidate);
                    paths.push(candidate);
                }
            }
        }
    }

    paths
}

fn platform_candidates() -> Vec<PathBuf> {
    let mut paths = Vec::new();

    if cfg!(windows) {
        windows_candidates(&mut paths);
    } else if cfg!(target_os = "macos") {
        macos_candidates(&mut paths);
    } else {
        linux_candidates(&mut paths);
    }

    paths
}

fn windows_candidates(paths: &mut Vec<PathBuf>) {
    let program_files = [
        r"C:\Program Files\Java",
        r"C:\Program Files\Eclipse Adoptium",
        r"C:\Program Files\Zulu",
        r"C:\Program Files\Microsoft",
        r"C:\Program Files\AdoptOpenJDK",
        r"C:\Program Files (x86)\Java",
    ];

    scan_subdirs(&program_files, paths);

    if let Ok(user_profile) = std::env::var("USERPROFILE") {
        let scoop_java = PathBuf::from(&user_profile)
            .join("scoop")
            .join("apps")
            .join("java");
        if scoop_java.is_dir() {
            scan_subdirs_direct(&scoop_java, paths);
        }
        let scoop_jdks = PathBuf::from(user_profile)
            .join("scoop")
            .join("apps")
            .join("openjdk");
        if scoop_jdks.is_dir() {
            scan_subdirs_direct(&scoop_jdks, paths);
        }
    }
}

fn macos_candidates(paths: &mut Vec<PathBuf>) {
    let jvms = PathBuf::from("/Library/Java/JavaVirtualMachines");
    if let Ok(entries) = std::fs::read_dir(&jvms) {
        for entry in entries.flatten() {
            let home = entry.path().join("Contents").join("Home");
            if home.is_dir() {
                paths.push(home);
            }
        }
    }
}

fn linux_candidates(paths: &mut Vec<PathBuf>) {
    let scan_roots = [
        "/usr/lib/jvm",
        "/usr/java",
        "/usr/local/java",
        "/opt/java",
        "/opt/jdk",
    ];

    for root in &scan_roots {
        let root_path = Path::new(root);
        if !root_path.is_dir() {
            continue;
        }
        if let Ok(entries) = std::fs::read_dir(root_path) {
            for entry in entries.flatten() {
                let p = entry.path();
                if !p.is_dir() {
                    continue;
                }
                let release = p.join("release");
                let bin_java = p.join("bin").join("java");
                if release.is_file() || bin_java.is_file() {
                    paths.push(p);
                }
            }
        }
    }
}

fn scan_subdirs(roots: &[&str], paths: &mut Vec<PathBuf>) {
    for root in roots {
        let root_path = Path::new(root);
        if !root_path.is_dir() {
            continue;
        }
        if let Ok(entries) = std::fs::read_dir(root_path) {
            for entry in entries.flatten() {
                let p = entry.path();
                if !p.is_dir() {
                    continue;
                }
                let release = p.join("release");
                if release.is_file() {
                    paths.push(p);
                } else {
                    scan_subdirs_direct(&p, paths);
                }
            }
        }
    }
}

fn scan_subdirs_direct(dir: &Path, paths: &mut Vec<PathBuf>) {
    if let Ok(entries) = std::fs::read_dir(dir) {
        for entry in entries.flatten() {
            let p = entry.path();
            if p.is_dir() && p.join("release").is_file() {
                paths.push(p);
            } else if p.is_dir() {
                scan_subdirs_direct(&p, paths);
            }
        }
    }
}

fn parse_release_file(home: &Path) -> Option<(u32, String)> {
    let release_path = home.join("release");
    let content = std::fs::read_to_string(&release_path).ok()?;

    let mut java_version: Option<String> = None;
    let mut implementor: Option<String> = None;

    for line in content.lines() {
        let line = line.trim();
        if let Some(rest) = line.strip_prefix("JAVA_VERSION=") {
            java_version = Some(strip_quotes(rest));
        } else if let Some(rest) = line.strip_prefix("IMPLEMENTOR=") {
            implementor = Some(strip_quotes(rest));
        }
    }

    let version_str = java_version?;
    let major = parse_java_major_version(&version_str)?;
    let vendor = implementor.unwrap_or_else(|| "Unknown".to_string());

    Some((major, vendor))
}

fn strip_quotes(s: &str) -> String {
    let s = s.trim();
    if (s.starts_with('"') && s.ends_with('"')) || (s.starts_with('\'') && s.ends_with('\'')) {
        s[1..s.len() - 1].to_string()
    } else {
        s.to_string()
    }
}

fn parse_java_major_version(version: &str) -> Option<u32> {
    let parts: Vec<&str> = version.split('.').collect();
    if parts.is_empty() {
        return None;
    }

    if parts[0] == "1" && parts.len() >= 2 {
        parts[1].parse().ok()
    } else {
        parts[0].parse().ok()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_java_version() {
        assert_eq!(parse_java_major_version("1.8.0_321"), Some(8));
        assert_eq!(parse_java_major_version("21.0.2"), Some(21));
        assert_eq!(parse_java_major_version("17"), Some(17));
        assert_eq!(parse_java_major_version("11.0.18"), Some(11));
    }
}

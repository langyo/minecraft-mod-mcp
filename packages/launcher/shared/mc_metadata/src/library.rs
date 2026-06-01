use crate::version_json::Library;
use _shared_core::platform;
use anyhow::Result;
use std::path::PathBuf;

pub fn library_path(name: &str) -> PathBuf {
    let parts: Vec<&str> = name.split(':').collect();
    if parts.len() < 3 {
        return platform::libraries_dir().join(name.replace(':', "/"));
    }

    let group = parts[0].replace('.', "/");
    let artifact = parts[1];
    let version = parts[2];
    let classifier = parts.get(3);

    let filename = match classifier {
        Some(c) => format!("{artifact}-{version}-{c}.jar"),
        None => format!("{artifact}-{version}.jar"),
    };

    platform::libraries_dir().join(&group).join(artifact).join(version).join(filename)
}

pub fn library_maven_path(name: &str) -> String {
    let parts: Vec<&str> = name.split(':').collect();
    if parts.len() < 3 {
        return name.replace(':', "/");
    }
    let group = parts[0].replace('.', "/");
    let artifact = parts[1];
    let version = parts[2];
    let classifier = parts.get(3);
    match classifier {
        Some(c) => format!("{}/{}/{}/{}-{}-{}.jar", group, artifact, version, artifact, version, c),
        None => format!("{}/{}/{}/{}-{}.jar", group, artifact, version, artifact, version),
    }
}

pub fn resolve_classpath(libraries: &[Library]) -> Result<Vec<PathBuf>> {
    let mut classpath = Vec::new();

    for lib in libraries {
        if let Some(ref rules) = lib.rules {
            if !should_include(rules) {
                continue;
            }
        }

        if let Some(ref downloads) = lib.downloads {
            if let Some(ref artifact) = downloads.artifact {
                let path = platform::libraries_dir().join(&artifact.path);
                if path.is_file() {
                    classpath.push(path);
                }
                continue;
            }
        }

        let path = library_path(&lib.name);
        if path.is_file() {
            classpath.push(path);
        }
    }

    Ok(classpath)
}

pub fn resolve_natives(libraries: &[Library], _natives_dir: &std::path::Path) -> Result<Vec<PathBuf>> {
    let mut native_jars = Vec::new();

    for lib in libraries {
        if let Some(ref downloads) = lib.downloads {
            if let Some(ref classifiers) = downloads.classifiers {
                let classifier_key = get_native_classifier();
                if let Some(classifier) = classifiers.get(&classifier_key) {
                    if let Some(path) = classifier.get("path").and_then(|p| p.as_str()) {
                        let jar_path = platform::libraries_dir().join(path);
                        if jar_path.is_file() {
                            native_jars.push(jar_path);
                        }
                    }
                }
            }
        }
    }

    Ok(native_jars)
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

fn should_include(rules: &[crate::version_json::Rule]) -> bool {
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

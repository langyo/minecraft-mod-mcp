fn main() {
    tauri_build::build();

    let bindings_dir = std::path::Path::new("../frontend/src/bindings");
    std::fs::create_dir_all(bindings_dir).ok();

    for crate_name in &["mc_version", "mc_settings", "mc_auth"] {
        let src = std::path::Path::new("../shared")
            .join(crate_name)
            .join("bindings");
        if src.is_dir() {
            if let Ok(entries) = std::fs::read_dir(&src) {
                for entry in entries.flatten() {
                    let dest = bindings_dir.join(entry.file_name());
                    std::fs::copy(entry.path(), &dest).ok();
                }
            }
        }
    }
}

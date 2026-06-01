use std::time::{SystemTime, UNIX_EPOCH};

pub fn create_offline_account(username: &str) -> (String, String) {
    let t = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_nanos();

    let uuid = format!(
        "{:08x}-0000-4000-8000-{:012x}",
        (t as u32).wrapping_mul(2654435761),
        (t as u64).wrapping_mul(6364136223846793005) & 0xffffffffffff
    );

    (uuid, username.to_string())
}

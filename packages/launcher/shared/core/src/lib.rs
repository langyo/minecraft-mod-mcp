pub mod errors;
pub mod platform;

pub use errors::LauncherError;
pub type Result<T> = std::result::Result<T, LauncherError>;

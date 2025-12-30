use anyhow::Result;
use serde::{Deserialize, Serialize};
use std::path::PathBuf;

#[derive(Debug, Serialize, Deserialize, Default)]
pub struct Config {
    /// Default server address
    #[serde(default = "default_server")]
    pub server: String,
    
    /// Default output format
    #[serde(default)]
    pub format: Option<String>,
    
    /// Source paths for source lookup
    #[serde(default)]
    pub source_paths: Vec<String>,
}

fn default_server() -> String {
    "tcp://127.0.0.1:5005".to_string()
}

impl Config {
    pub fn load() -> Result<Self> {
        let config_path = Self::config_path();
        
        if config_path.exists() {
            let contents = std::fs::read_to_string(&config_path)?;
            let config: Config = toml::from_str(&contents)?;
            Ok(config)
        } else {
            Ok(Config::default())
        }
    }
    
    pub fn save(&self) -> Result<()> {
        let config_path = Self::config_path();
        
        if let Some(parent) = config_path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        
        let contents = toml::to_string_pretty(self)?;
        std::fs::write(&config_path, contents)?;
        
        Ok(())
    }
    
    fn config_path() -> PathBuf {
        dirs::config_dir()
            .unwrap_or_else(|| PathBuf::from("."))
            .join("jdbg")
            .join("config.toml")
    }
}


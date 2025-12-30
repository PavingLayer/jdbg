use anyhow::{Context, Result};
use std::fs;
use std::io::{BufRead, BufReader};
use std::path::PathBuf;
use std::process::{Child, Command, Stdio};
use std::time::Duration;

use crate::output::Output;
use super::ServerCommands;

pub async fn execute(cmd: ServerCommands, output: &Output) -> Result<()> {
    match cmd {
        ServerCommands::Start { listen, foreground } => {
            start_server(&listen, foreground, output).await
        }
        ServerCommands::Stop => {
            stop_server(output)
        }
        ServerCommands::Status => {
            check_status(output).await
        }
    }
}

async fn start_server(listen: &str, foreground: bool, output: &Output) -> Result<()> {
    // Check if already running
    if let Some(pid) = read_pid_file() {
        if is_process_running(pid) {
            output.message(&format!("Server already running (PID: {})", pid));
            return Ok(());
        } else {
            // Stale PID file, remove it
            let _ = fs::remove_file(get_pid_file());
        }
    }
    
    let jar_path = find_server_jar()?;
    output.verbose(&format!("Using server JAR: {}", jar_path.display()));
    
    let java = find_java()?;
    
    let mut cmd = Command::new(&java);
    cmd.arg("-jar")
        .arg(&jar_path)
        .arg("--listen")
        .arg(listen);
    
    if foreground {
        output.message(&format!("Starting JDBG server on {} (foreground)...", listen));
        
        // Run in foreground - inherit stdio
        cmd.stdin(Stdio::inherit())
            .stdout(Stdio::inherit())
            .stderr(Stdio::inherit());
        
        let status = cmd.status().context("Failed to start server")?;
        if !status.success() {
            anyhow::bail!("Server exited with status: {}", status);
        }
    } else {
        output.message(&format!("Starting JDBG server on {}...", listen));
        
        // Daemonize - redirect output to log file
        let log_file = get_log_file();
        ensure_parent_dir(&log_file)?;
        
        let log = fs::File::create(&log_file)
            .context("Failed to create log file")?;
        let log_err = log.try_clone()?;
        
        cmd.stdin(Stdio::null())
            .stdout(Stdio::from(log))
            .stderr(Stdio::from(log_err));
        
        let child = cmd.spawn().context("Failed to start server")?;
        let pid = child.id();
        
        // Write PID file
        let pid_file = get_pid_file();
        ensure_parent_dir(&pid_file)?;
        fs::write(&pid_file, pid.to_string())?;
        
        // Wait a moment and check if it started successfully
        std::thread::sleep(Duration::from_millis(500));
        
        if is_process_running(pid as i32) {
            output.message(&format!("Server started (PID: {})", pid));
            output.message(&format!("Log file: {}", log_file.display()));
        } else {
            // Read the log to see what went wrong
            let log_content = fs::read_to_string(&log_file).unwrap_or_default();
            let last_lines: Vec<&str> = log_content.lines().rev().take(10).collect();
            output.error("Server failed to start. Last log lines:");
            for line in last_lines.into_iter().rev() {
                eprintln!("  {}", line);
            }
            let _ = fs::remove_file(&pid_file);
            anyhow::bail!("Server failed to start");
        }
    }
    
    Ok(())
}

fn stop_server(output: &Output) -> Result<()> {
    let pid_file = get_pid_file();
    
    if let Some(pid) = read_pid_file() {
        if is_process_running(pid) {
            output.message(&format!("Stopping server (PID: {})...", pid));
            
            // Send SIGTERM
            #[cfg(unix)]
            {
                unsafe {
                    libc::kill(pid, libc::SIGTERM);
                }
            }
            
            #[cfg(not(unix))]
            {
                let _ = Command::new("taskkill")
                    .args(["/PID", &pid.to_string()])
                    .output();
            }
            
            // Wait for process to stop (up to 5 seconds)
            for _ in 0..50 {
                std::thread::sleep(Duration::from_millis(100));
                if !is_process_running(pid) {
                    break;
                }
            }
            
            if is_process_running(pid) {
                output.message("Server did not stop gracefully, forcing...");
                #[cfg(unix)]
                {
                    unsafe {
                        libc::kill(pid, libc::SIGKILL);
                    }
                }
            }
            
            let _ = fs::remove_file(&pid_file);
            output.message("Server stopped");
        } else {
            let _ = fs::remove_file(&pid_file);
            output.message("Server was not running (removed stale PID file)");
        }
    } else {
        output.message("Server is not running");
    }
    
    Ok(())
}

async fn check_status(output: &Output) -> Result<()> {
    if let Some(pid) = read_pid_file() {
        if is_process_running(pid) {
            output.message(&format!("Server is running (PID: {})", pid));
            
            // Try to connect and get more info
            match crate::client::DebuggerClient::connect("tcp://127.0.0.1:5005").await {
                Ok(mut client) => {
                    match client.list_sessions().await {
                        Ok((sessions, active_id)) => {
                            output.message(&format!("  Active sessions: {}", sessions.len()));
                            if !active_id.is_empty() {
                                output.message(&format!("  Active session: {}", active_id));
                            }
                        }
                        Err(_) => {}
                    }
                }
                Err(_) => {
                    output.message("  (Could not connect to server)");
                }
            }
            
            // Show log file location
            let log_file = get_log_file();
            if log_file.exists() {
                output.message(&format!("  Log file: {}", log_file.display()));
            }
        } else {
            let _ = fs::remove_file(get_pid_file());
            output.message("Server is not running (removed stale PID file)");
        }
    } else {
        output.message("Server is not running");
    }
    
    Ok(())
}

// Helper functions

fn get_jdbg_dir() -> PathBuf {
    dirs::data_local_dir()
        .unwrap_or_else(|| PathBuf::from("."))
        .join("jdbg")
}

fn get_pid_file() -> PathBuf {
    // Use runtime dir if available, otherwise fall back to data dir
    dirs::runtime_dir()
        .unwrap_or_else(get_jdbg_dir)
        .join("jdbg-server.pid")
}

fn get_log_file() -> PathBuf {
    get_jdbg_dir().join("server.log")
}

fn ensure_parent_dir(path: &PathBuf) -> Result<()> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    Ok(())
}

fn read_pid_file() -> Option<i32> {
    let pid_file = get_pid_file();
    if pid_file.exists() {
        fs::read_to_string(&pid_file)
            .ok()
            .and_then(|s| s.trim().parse().ok())
    } else {
        None
    }
}

fn is_process_running(pid: i32) -> bool {
    #[cfg(unix)]
    {
        unsafe { libc::kill(pid, 0) == 0 }
    }
    
    #[cfg(not(unix))]
    {
        // On Windows, try to open the process
        false // TODO: implement for Windows
    }
}

fn find_java() -> Result<PathBuf> {
    // Check JAVA_HOME first
    if let Ok(java_home) = std::env::var("JAVA_HOME") {
        let java = PathBuf::from(&java_home).join("bin").join("java");
        if java.exists() {
            return Ok(java);
        }
    }
    
    // Try to find java in PATH
    if let Ok(output) = Command::new("which").arg("java").output() {
        if output.status.success() {
            let path = String::from_utf8_lossy(&output.stdout).trim().to_string();
            if !path.is_empty() {
                return Ok(PathBuf::from(path));
            }
        }
    }
    
    // On Windows, try where
    #[cfg(windows)]
    {
        if let Ok(output) = Command::new("where").arg("java").output() {
            if output.status.success() {
                let path = String::from_utf8_lossy(&output.stdout)
                    .lines()
                    .next()
                    .unwrap_or("")
                    .to_string();
                if !path.is_empty() {
                    return Ok(PathBuf::from(path));
                }
            }
        }
    }
    
    anyhow::bail!("Java not found. Please set JAVA_HOME or ensure java is in PATH.")
}

fn find_server_jar() -> Result<PathBuf> {
    // Check environment variable first
    if let Ok(jar) = std::env::var("JDBG_SERVER_JAR") {
        let path = PathBuf::from(&jar);
        if path.exists() {
            return Ok(path);
        }
    }
    
    // Get the CLI binary location and look relative to it
    if let Ok(exe) = std::env::current_exe() {
        // Development: cli/target/release/jdbg -> server/target/jdbg-server.jar
        let dev_jar = exe
            .parent()  // release/
            .and_then(|p| p.parent())  // target/
            .and_then(|p| p.parent())  // cli/
            .and_then(|p| p.parent())  // jdbg/
            .map(|p| p.join("server").join("target").join("jdbg-server.jar"));
        
        if let Some(jar) = dev_jar {
            if jar.exists() {
                return Ok(jar);
            }
        }
        
        // Installed: look for jar next to binary
        let installed_jar = exe.parent().map(|p| p.join("jdbg-server.jar"));
        if let Some(jar) = installed_jar {
            if jar.exists() {
                return Ok(jar);
            }
        }
    }
    
    // Common installation locations
    let locations = [
        PathBuf::from("/usr/share/jdbg/jdbg-server.jar"),
        PathBuf::from("/usr/local/share/jdbg/jdbg-server.jar"),
        get_jdbg_dir().join("jdbg-server.jar"),
    ];
    
    for loc in &locations {
        if loc.exists() {
            return Ok(loc.clone());
        }
    }
    
    anyhow::bail!(
        "Could not find jdbg-server.jar. Set JDBG_SERVER_JAR environment variable \
         or install the server JAR to one of:\n  \
         - Next to the CLI binary\n  \
         - /usr/share/jdbg/jdbg-server.jar\n  \
         - ~/.local/share/jdbg/jdbg-server.jar"
    )
}

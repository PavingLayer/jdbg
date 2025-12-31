//! Common test infrastructure for JDBG integration tests.
//!
//! This module provides:
//! - TestFixture: Manages server and test target lifecycle
//! - Shared helpers for test setup

use std::process::{Child, Command, Stdio};
use std::time::Duration;
use std::env;
use std::path::PathBuf;
use std::sync::atomic::{AtomicU16, Ordering};
use tokio::time::sleep;

#[cfg(unix)]
use libc;

use jdbg_client::DebuggerClient;

/// Atomic counter for generating unique ports
static PORT_COUNTER: AtomicU16 = AtomicU16::new(0);

/// Test fixture that manages server and test target lifecycle
pub struct TestFixture {
    pub server_process: Option<Child>,
    pub test_target_process: Option<Child>,
    pub debug_port: u16,
    pub server_port: u16,
}

impl TestFixture {
    pub fn new() -> Self {
        // Generate unique ports for each test
        let offset = PORT_COUNTER.fetch_add(1, Ordering::SeqCst);
        Self {
            server_process: None,
            test_target_process: None,
            debug_port: 18765 + offset,
            server_port: 15005 + offset,
        }
    }

    pub fn project_root() -> PathBuf {
        PathBuf::from(env::var("CARGO_MANIFEST_DIR").unwrap())
            .parent()
            .unwrap()
            .to_path_buf()
    }

    pub fn server_jar_path() -> PathBuf {
        env::var("JDBG_SERVER_JAR")
            .map(PathBuf::from)
            .unwrap_or_else(|_| Self::project_root().join("server/target/jdbg-server.jar"))
    }

    pub fn test_target_classes() -> PathBuf {
        env::var("TEST_TARGET_CLASSES")
            .map(PathBuf::from)
            .unwrap_or_else(|_| Self::project_root().join("test/test-target/target/classes"))
    }

    pub fn java_bin() -> PathBuf {
        if let Ok(java_home) = env::var("JAVA_HOME") {
            PathBuf::from(java_home).join("bin/java")
        } else {
            PathBuf::from("java") // Use system java
        }
    }

    pub fn jacoco_agent_path() -> Option<PathBuf> {
        env::var("JACOCO_AGENT")
            .ok()
            .map(PathBuf::from)
            .filter(|p| p.exists())
    }

    pub fn coverage_exec_path() -> Option<PathBuf> {
        env::var("COVERAGE_EXEC")
            .ok()
            .map(PathBuf::from)
    }

    pub async fn start_server(&mut self) -> anyhow::Result<()> {
        let jar_path = Self::server_jar_path();
        if !jar_path.exists() {
            anyhow::bail!(
                "Server JAR not found at {:?}. Run: cd server && ./mvnw package -DskipTests",
                jar_path
            );
        }

        eprintln!("[TEST] Starting server on port {}", self.server_port);
        
        let listen_addr = format!("tcp://127.0.0.1:{}", self.server_port);
        
        let mut cmd = Command::new(Self::java_bin());
        
        // Add JaCoCo agent if configured
        if let Some(jacoco_agent) = Self::jacoco_agent_path() {
            let exec_path = Self::coverage_exec_path()
                .unwrap_or_else(|| PathBuf::from("coverage/jacoco-server.exec"));
            
            // Each test instance gets its own coverage file to avoid conflicts
            let exec_file = exec_path.with_extension(format!("{}.exec", self.server_port));
            
            let agent_arg = format!(
                "-javaagent:{}=destfile={},append=true,output=file",
                jacoco_agent.display(),
                exec_file.display()
            );
            eprintln!("[TEST] JaCoCo enabled: {}", exec_file.display());
            cmd.arg(agent_arg);
        }
        
        let child = cmd
            .arg("-jar")
            .arg(&jar_path)
            .arg("--listen")
            .arg(&listen_addr)
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .spawn()?;

        self.server_process = Some(child);
        
        // Wait for server to start
        sleep(Duration::from_secs(3)).await;
        Ok(())
    }

    pub async fn start_test_target(&mut self) -> anyhow::Result<()> {
        self.start_test_target_with_mode("wait").await
    }

    pub async fn start_test_target_with_mode(&mut self, mode: &str) -> anyhow::Result<()> {
        let classes_path = Self::test_target_classes();
        if !classes_path.exists() {
            anyhow::bail!(
                "Test target classes not found at {:?}. Compile with: javac -d test/test-target/target/classes -g test/test-target/src/main/java/com/jdbg/test/*.java",
                classes_path
            );
        }

        // Use suspend=y for non-wait modes so we can attach before the program runs
        let suspend = if mode == "wait" { "n" } else { "y" };
        eprintln!("[TEST] Starting test target with mode '{}' (suspend={}) on debug port {}", mode, suspend, self.debug_port);

        let debug_opts = format!(
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend={},address=*:{}",
            suspend, self.debug_port
        );

        let child = Command::new(Self::java_bin())
            .arg(&debug_opts)
            .arg("-cp")
            .arg(&classes_path)
            .arg("com.jdbg.test.Main")
            .arg(mode)
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .spawn()?;

        self.test_target_process = Some(child);
        
        // Wait for JVM to start listening on debug port
        sleep(Duration::from_secs(2)).await;
        Ok(())
    }

    pub fn server_addr(&self) -> String {
        format!("127.0.0.1:{}", self.server_port)
    }

    pub async fn connect(&self) -> anyhow::Result<DebuggerClient> {
        DebuggerClient::connect(&self.server_addr()).await
    }

    pub async fn attach(&self, client: &mut DebuggerClient) -> anyhow::Result<jdbg_client::Session> {
        client
            .attach_remote(None, "localhost", self.debug_port as i32, 5000, None)
            .await
    }
}

impl Drop for TestFixture {
    fn drop(&mut self) {
        // Use SIGTERM for graceful shutdown so JaCoCo can flush coverage data
        #[cfg(unix)]
        fn terminate_process(child: &mut Child, name: &str) {
            eprintln!("[TEST] Terminating {} (SIGTERM)", name);
            
            // Send SIGTERM for graceful shutdown
            unsafe {
                libc::kill(child.id() as i32, libc::SIGTERM);
            }
            
            // Wait up to 5 seconds for graceful shutdown
            for _ in 0..50 {
                match child.try_wait() {
                    Ok(Some(_)) => return,
                    Ok(None) => std::thread::sleep(Duration::from_millis(100)),
                    Err(_) => break,
                }
            }
            
            // Force kill if still running
            eprintln!("[TEST] Force killing {}", name);
            let _ = child.kill();
            let _ = child.wait();
        }

        #[cfg(not(unix))]
        fn terminate_process(child: &mut Child, name: &str) {
            eprintln!("[TEST] Killing {}", name);
            let _ = child.kill();
            let _ = child.wait();
        }

        if let Some(mut child) = self.test_target_process.take() {
            terminate_process(&mut child, "test target");
        }
        if let Some(mut child) = self.server_process.take() {
            terminate_process(&mut child, "server");
        }
    }
}


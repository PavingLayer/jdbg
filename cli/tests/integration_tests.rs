//! JDBG Integration Tests
//!
//! These tests verify the debugger server functionality through the gRPC client.
//!
//! # Prerequisites
//! - Java 17+ installed (JAVA_HOME set)
//! - Server JAR built: `cd server && ./mvnw package -DskipTests`
//! - Test target compiled: `javac -d test/test-target/target/classes -g test/test-target/src/main/java/com/jdbg/test/*.java`
//!
//! # Running
//! ```bash
//! # Run all integration tests (sequentially to avoid port conflicts)
//! cargo test --test integration_tests -- --test-threads=1
//!
//! # Run specific test
//! cargo test --test integration_tests test_session_attach_and_detach -- --test-threads=1
//! ```

use std::process::{Child, Command, Stdio};
use std::time::Duration;
use std::env;
use std::path::PathBuf;
use std::sync::atomic::{AtomicU16, Ordering};
use tokio::time::sleep;

#[cfg(unix)]
use libc;

use jdbg_client::{DebuggerClient, StepDepth};

/// Atomic counter for generating unique ports
static PORT_COUNTER: AtomicU16 = AtomicU16::new(0);

/// Test fixture that manages server and test target lifecycle
struct TestFixture {
    server_process: Option<Child>,
    test_target_process: Option<Child>,
    debug_port: u16,
    server_port: u16,
}

impl TestFixture {
    fn new() -> Self {
        // Generate unique ports for each test
        let offset = PORT_COUNTER.fetch_add(1, Ordering::SeqCst);
        Self {
            server_process: None,
            test_target_process: None,
            debug_port: 18765 + offset,
            server_port: 15005 + offset,
        }
    }

    fn project_root() -> PathBuf {
        PathBuf::from(env::var("CARGO_MANIFEST_DIR").unwrap())
            .parent()
            .unwrap()
            .to_path_buf()
    }

    fn server_jar_path() -> PathBuf {
        env::var("JDBG_SERVER_JAR")
            .map(PathBuf::from)
            .unwrap_or_else(|_| Self::project_root().join("server/target/jdbg-server.jar"))
    }

    fn test_target_classes() -> PathBuf {
        env::var("TEST_TARGET_CLASSES")
            .map(PathBuf::from)
            .unwrap_or_else(|_| Self::project_root().join("test/test-target/target/classes"))
    }

    fn java_bin() -> PathBuf {
        if let Ok(java_home) = env::var("JAVA_HOME") {
            PathBuf::from(java_home).join("bin/java")
        } else {
            PathBuf::from("java") // Use system java
        }
    }

    fn jacoco_agent_path() -> Option<PathBuf> {
        env::var("JACOCO_AGENT")
            .ok()
            .map(PathBuf::from)
            .filter(|p| p.exists())
    }

    fn coverage_exec_path() -> Option<PathBuf> {
        env::var("COVERAGE_EXEC")
            .ok()
            .map(PathBuf::from)
    }

    async fn start_server(&mut self) -> anyhow::Result<()> {
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

    async fn start_test_target(&mut self) -> anyhow::Result<()> {
        let classes_path = Self::test_target_classes();
        if !classes_path.exists() {
            anyhow::bail!(
                "Test target classes not found at {:?}. Compile with: javac -d test/test-target/target/classes -g test/test-target/src/main/java/com/jdbg/test/*.java",
                classes_path
            );
        }

        eprintln!("[TEST] Starting test target on debug port {}", self.debug_port);

        let debug_opts = format!(
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:{}",
            self.debug_port
        );

        let child = Command::new(Self::java_bin())
            .arg(&debug_opts)
            .arg("-cp")
            .arg(&classes_path)
            .arg("com.jdbg.test.Main")
            .arg("wait")
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .spawn()?;

        self.test_target_process = Some(child);
        
        // Wait for JVM to start
        sleep(Duration::from_secs(3)).await;
        Ok(())
    }

    fn server_addr(&self) -> String {
        format!("127.0.0.1:{}", self.server_port)
    }

    async fn connect(&self) -> anyhow::Result<DebuggerClient> {
        DebuggerClient::connect(&self.server_addr()).await
    }

    async fn attach(&self, client: &mut DebuggerClient) -> anyhow::Result<jdbg_client::Session> {
        client
            .attach_remote(None, "localhost", self.debug_port as i32, 5000)
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

// ============================================================================
// Session Tests
// ============================================================================

#[tokio::test]
async fn test_session_attach_and_detach() {
    let mut fixture = TestFixture::new();
    fixture.start_server().await.expect("Failed to start server");
    fixture.start_test_target().await.expect("Failed to start test target");

    let mut client = fixture.connect().await.expect("Failed to connect to server");

    // Test attach
    let session = fixture.attach(&mut client).await.expect("Failed to attach");

    assert!(!session.id.is_empty(), "Session ID should not be empty");
    assert!(!session.vm_name.is_empty(), "VM name should not be empty");
    eprintln!("[TEST] Attached to session: {}, VM: {}", session.id, session.vm_name);

    // Test session status
    let status = client
        .get_session_status(Some(session.id.clone()))
        .await
        .expect("Failed to get session status");

    assert_eq!(status.id, session.id);

    // Test list sessions
    let (sessions, active_id) = client.list_sessions().await.expect("Failed to list sessions");
    assert_eq!(sessions.len(), 1);
    assert_eq!(active_id, session.id);

    // Test detach
    client
        .detach_session(Some(session.id))
        .await
        .expect("Failed to detach");

    let (sessions, _) = client.list_sessions().await.expect("Failed to list sessions");
    assert!(sessions.is_empty(), "Sessions should be empty after detach");
}

// ============================================================================
// Thread Tests
// ============================================================================

#[tokio::test]
async fn test_thread_operations() {
    let mut fixture = TestFixture::new();
    fixture.start_server().await.expect("Failed to start server");
    fixture.start_test_target().await.expect("Failed to start test target");

    let mut client = fixture.connect().await.expect("Failed to connect");
    fixture.attach(&mut client).await.expect("Failed to attach");

    // List threads
    let threads = client.list_threads(None).await.expect("Failed to list threads");

    assert!(!threads.is_empty(), "Should have at least one thread");
    eprintln!("[TEST] Found {} threads", threads.len());
    
    let main_thread = threads.iter().find(|t| t.name == "main");
    assert!(main_thread.is_some(), "Should have a main thread");
    
    let main_thread = main_thread.unwrap();
    eprintln!("[TEST] Main thread ID: {}", main_thread.id);

    // Select thread
    client
        .select_thread(None, main_thread.id)
        .await
        .expect("Failed to select thread");

    client.detach_session(None).await.ok();
}

// ============================================================================
// Breakpoint Tests
// ============================================================================

#[tokio::test]
async fn test_breakpoint_lifecycle() {
    let mut fixture = TestFixture::new();
    fixture.start_server().await.expect("Failed to start server");
    fixture.start_test_target().await.expect("Failed to start test target");

    let mut client = fixture.connect().await.expect("Failed to connect");
    fixture.attach(&mut client).await.expect("Failed to attach");

    // Add line breakpoint
    let bp = client
        .add_line_breakpoint(None, "com.jdbg.test.Main", 166, None)
        .await
        .expect("Failed to add line breakpoint");

    assert!(!bp.id.is_empty());
    assert_eq!(bp.class_name, "com.jdbg.test.Main");
    assert_eq!(bp.line_number, 166);
    assert!(bp.enabled);
    eprintln!("[TEST] Added line breakpoint: {}", bp.id);

    // Add method breakpoint
    let mbp = client
        .add_method_breakpoint(None, "com.jdbg.test.Calculator", "add", None)
        .await
        .expect("Failed to add method breakpoint");

    assert!(!mbp.id.is_empty());
    assert_eq!(mbp.method_name, "add");
    eprintln!("[TEST] Added method breakpoint: {}", mbp.id);

    // List breakpoints
    let bps = client.list_breakpoints(None).await.expect("Failed to list breakpoints");
    assert_eq!(bps.len(), 2);

    // Disable breakpoint
    let disabled = client
        .disable_breakpoint(&bp.id)
        .await
        .expect("Failed to disable breakpoint");
    assert!(!disabled.enabled);

    // Enable breakpoint
    let enabled = client
        .enable_breakpoint(&bp.id)
        .await
        .expect("Failed to enable breakpoint");
    assert!(enabled.enabled);

    // Remove breakpoint
    client
        .remove_breakpoint(&bp.id)
        .await
        .expect("Failed to remove breakpoint");

    let bps = client.list_breakpoints(None).await.expect("Failed to list breakpoints");
    assert_eq!(bps.len(), 1);

    // Clear all breakpoints
    client.clear_breakpoints(None).await.expect("Failed to clear breakpoints");

    let bps = client.list_breakpoints(None).await.expect("Failed to list breakpoints");
    assert!(bps.is_empty());

    client.detach_session(None).await.ok();
}

// ============================================================================
// Exception Breakpoint Tests
// ============================================================================

#[tokio::test]
async fn test_exception_breakpoints() {
    let mut fixture = TestFixture::new();
    fixture.start_server().await.expect("Failed to start server");
    fixture.start_test_target().await.expect("Failed to start test target");

    let mut client = fixture.connect().await.expect("Failed to connect");
    fixture.attach(&mut client).await.expect("Failed to attach");

    // Catch NullPointerException
    let exc = client
        .catch_exception(None, "java.lang.NullPointerException", true, true)
        .await
        .expect("Failed to catch exception");

    assert!(!exc.id.is_empty());
    assert_eq!(exc.exception_class, "java.lang.NullPointerException");
    assert!(exc.caught);
    assert!(exc.uncaught);
    eprintln!("[TEST] Added exception breakpoint: {}", exc.id);

    // List exception breakpoints
    let excs = client
        .list_exception_breakpoints(None)
        .await
        .expect("Failed to list exception breakpoints");
    assert_eq!(excs.len(), 1);

    // Ignore exception
    client
        .ignore_exception(&exc.id)
        .await
        .expect("Failed to ignore exception");

    let excs = client
        .list_exception_breakpoints(None)
        .await
        .expect("Failed to list exception breakpoints");
    assert!(excs.is_empty());

    client.detach_session(None).await.ok();
}

// ============================================================================
// Execution Control Tests
// ============================================================================

#[tokio::test]
async fn test_suspend_and_resume() {
    let mut fixture = TestFixture::new();
    fixture.start_server().await.expect("Failed to start server");
    fixture.start_test_target().await.expect("Failed to start test target");

    let mut client = fixture.connect().await.expect("Failed to connect");
    fixture.attach(&mut client).await.expect("Failed to attach");

    // Suspend all threads
    client.suspend(None, None).await.expect("Failed to suspend");
    sleep(Duration::from_millis(500)).await;

    // Verify main thread is suspended
    let threads = client.list_threads(None).await.expect("Failed to list threads");
    let main_thread = threads.iter().find(|t| t.name == "main").expect("No main thread");
    assert!(main_thread.suspended, "Main thread should be suspended");
    eprintln!("[TEST] Main thread suspended: {}", main_thread.suspended);

    // Resume all threads
    client.resume(None, None).await.expect("Failed to resume");
    sleep(Duration::from_millis(500)).await;

    // Verify main thread is running
    let threads = client.list_threads(None).await.expect("Failed to list threads");
    let main_thread = threads.iter().find(|t| t.name == "main").expect("No main thread");
    assert!(!main_thread.suspended, "Main thread should be running");

    client.detach_session(None).await.ok();
}

// ============================================================================
// Frame Tests
// ============================================================================

#[tokio::test]
async fn test_frame_inspection() {
    let mut fixture = TestFixture::new();
    fixture.start_server().await.expect("Failed to start server");
    fixture.start_test_target().await.expect("Failed to start test target");

    let mut client = fixture.connect().await.expect("Failed to connect");
    fixture.attach(&mut client).await.expect("Failed to attach");

    // Suspend to inspect frames
    client.suspend(None, None).await.expect("Failed to suspend");
    sleep(Duration::from_millis(500)).await;

    // Get main thread ID
    let threads = client.list_threads(None).await.expect("Failed to list threads");
    let main_thread = threads.iter().find(|t| t.name == "main").expect("No main thread");

    // Select main thread
    client
        .select_thread(None, main_thread.id)
        .await
        .expect("Failed to select thread");

    // List frames
    let frames = client
        .list_frames(None, Some(main_thread.id), None)
        .await
        .expect("Failed to list frames");

    assert!(!frames.is_empty(), "Should have at least one frame");
    eprintln!("[TEST] Found {} frames", frames.len());

    // First frame should have class and method info
    for (i, frame) in frames.iter().enumerate() {
        eprintln!(
            "[TEST] Frame {}: {}.{} (line {})",
            i, frame.class_name, frame.method_name, frame.line_number
        );
    }

    // Select a frame
    client
        .select_frame(None, 0)
        .await
        .expect("Failed to select frame");

    client.resume(None, None).await.ok();
    client.detach_session(None).await.ok();
}

// ============================================================================
// Variable Tests
// ============================================================================

#[tokio::test]
async fn test_variable_inspection() {
    let mut fixture = TestFixture::new();
    fixture.start_server().await.expect("Failed to start server");
    fixture.start_test_target().await.expect("Failed to start test target");

    let mut client = fixture.connect().await.expect("Failed to connect");
    fixture.attach(&mut client).await.expect("Failed to attach");

    // Suspend to inspect variables
    client.suspend(None, None).await.expect("Failed to suspend");
    sleep(Duration::from_millis(500)).await;

    // Get main thread
    let threads = client.list_threads(None).await.expect("Failed to list threads");
    let main_thread = threads.iter().find(|t| t.name == "main").expect("No main thread");

    // List variables in first frame
    let vars = client
        .list_variables(None, Some(main_thread.id), Some(0))
        .await
        .expect("Failed to list variables");

    eprintln!("[TEST] Found {} variables in frame 0", vars.len());
    for var in &vars {
        eprintln!("[TEST] Variable: {} ({}) = {}", var.name, var.r#type, var.value);
    }

    client.resume(None, None).await.ok();
    client.detach_session(None).await.ok();
}

// ============================================================================
// Step Tests
// ============================================================================

#[tokio::test]
async fn test_step_over() {
    let mut fixture = TestFixture::new();
    fixture.start_server().await.expect("Failed to start server");
    fixture.start_test_target().await.expect("Failed to start test target");

    let mut client = fixture.connect().await.expect("Failed to connect");
    fixture.attach(&mut client).await.expect("Failed to attach");

    // Suspend first
    client.suspend(None, None).await.expect("Failed to suspend");
    sleep(Duration::from_millis(500)).await;

    // Get main thread
    let threads = client.list_threads(None).await.expect("Failed to list threads");
    let main_thread = threads.iter().find(|t| t.name == "main").expect("No main thread");

    client
        .select_thread(None, main_thread.id)
        .await
        .expect("Failed to select thread");

    // Get initial location
    let frames_before = client
        .list_frames(None, Some(main_thread.id), Some(1))
        .await
        .expect("Failed to list frames");
    
    if !frames_before.is_empty() {
        eprintln!(
            "[TEST] Before step: {}.{} line {}",
            frames_before[0].class_name,
            frames_before[0].method_name,
            frames_before[0].line_number
        );
    }

    // Step over
    let step_result = client
        .step(None, Some(main_thread.id), StepDepth::Over)
        .await
        .expect("Failed to step over");

    assert!(step_result.success, "Step should succeed");
    eprintln!("[TEST] Step over completed");

    client.resume(None, None).await.ok();
    client.detach_session(None).await.ok();
}

// ============================================================================
// Evaluation Tests
// ============================================================================

#[tokio::test]
async fn test_evaluate_expression() {
    let mut fixture = TestFixture::new();
    fixture.start_server().await.expect("Failed to start server");
    fixture.start_test_target().await.expect("Failed to start test target");

    let mut client = fixture.connect().await.expect("Failed to connect");
    fixture.attach(&mut client).await.expect("Failed to attach");

    // Suspend to evaluate
    client.suspend(None, None).await.expect("Failed to suspend");
    sleep(Duration::from_millis(500)).await;

    // Get main thread
    let threads = client.list_threads(None).await.expect("Failed to list threads");
    let main_thread = threads.iter().find(|t| t.name == "main").expect("No main thread");

    // Try to evaluate a simple expression
    // Note: Evaluation may fail if not in the right context
    let result = client
        .evaluate(None, Some(main_thread.id), Some(0), "this")
        .await;

    match result {
        Ok(eval) => {
            eprintln!("[TEST] Evaluated 'this': {} ({})", eval.result, eval.r#type);
        }
        Err(e) => {
            eprintln!("[TEST] Evaluation failed (expected in static context): {}", e);
        }
    }

    client.resume(None, None).await.ok();
    client.detach_session(None).await.ok();
}

//! Breakpoint tests for JDBG.

mod common;

use common::TestFixture;

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


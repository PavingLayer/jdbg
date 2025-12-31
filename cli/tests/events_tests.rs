//! Integration tests for non-blocking event retrieval

mod common;

use common::TestFixture;
use std::time::Duration;
use tokio::time::sleep;

/// Test polling events from buffer
#[tokio::test]
async fn test_poll_events() {
    let mut fixture = TestFixture::new();
    
    // Start server and test target
    fixture.start_server().await.expect("Failed to start server");
    fixture.start_test_target().await.expect("Failed to start test target");
    
    // Connect and attach
    let mut client = fixture.connect().await.expect("Failed to connect");
    let _session = fixture.attach(&mut client).await.expect("Failed to attach");
    
    // Clear any existing events
    let response = client.clear_events(None).await.expect("Failed to clear events");
    assert!(response.success);
    
    // Add a breakpoint at performWork which is called every second in wait mode
    // Line 184 is inside performWork: "final int computed = iteration * 2;"
    let bp = client
        .add_line_breakpoint(None, "com.jdbg.test.Main", 184, None)
        .await
        .expect("Failed to add breakpoint");
    
    // Wait for the breakpoint to be hit (happens every ~1 second in wait mode)
    sleep(Duration::from_secs(2)).await;
    
    // Poll for events
    let response = client.poll_events(None, 0, vec![]).await.expect("Failed to poll events");
    
    // Should have at least one breakpoint event
    assert!(!response.events.is_empty(), "Expected at least one event, got none");
    
    let event = &response.events[0];
    assert_eq!(event.event_type, "breakpoint", "Expected breakpoint event, got: {}", event.event_type);
    
    // Clean up
    client.remove_breakpoint(&bp.id).await.expect("Failed to remove breakpoint");
    client.detach_session(None).await.ok();
}

/// Test waiting for events with timeout
#[tokio::test]
async fn test_wait_for_event() {
    let mut fixture = TestFixture::new();
    
    // Start server and test target
    fixture.start_server().await.expect("Failed to start server");
    fixture.start_test_target().await.expect("Failed to start test target");
    
    // Connect and attach
    let mut client = fixture.connect().await.expect("Failed to connect");
    let _session = fixture.attach(&mut client).await.expect("Failed to attach");
    
    // Clear any existing events
    client.clear_events(None).await.expect("Failed to clear events");
    
    // Add a breakpoint at performWork
    let bp = client
        .add_line_breakpoint(None, "com.jdbg.test.Main", 184, None)
        .await
        .expect("Failed to add breakpoint");
    
    // Wait for breakpoint event (should be hit within 2 seconds)
    let response = client
        .wait_for_event(None, 5000, vec!["breakpoint".to_string()])
        .await
        .expect("Failed to wait for event");
    
    // Should have received the breakpoint event
    assert!(!response.events.is_empty(), "Expected breakpoint event, got timeout");
    assert_eq!(response.events[0].event_type, "breakpoint");
    
    // Clean up
    client.remove_breakpoint(&bp.id).await.expect("Failed to remove breakpoint");
    client.detach_session(None).await.ok();
}

/// Test event info
#[tokio::test]
async fn test_event_info() {
    let mut fixture = TestFixture::new();
    
    // Start server and test target
    fixture.start_server().await.expect("Failed to start server");
    fixture.start_test_target().await.expect("Failed to start test target");
    
    // Connect and attach
    let mut client = fixture.connect().await.expect("Failed to connect");
    let _session = fixture.attach(&mut client).await.expect("Failed to attach");
    
    // Clear events first
    client.clear_events(None).await.expect("Failed to clear events");
    
    // Get event info - should be empty
    let info = client.get_event_info(None).await.expect("Failed to get event info");
    assert_eq!(info.buffered_count, 0);
    assert_eq!(info.buffer_capacity, 1000); // Default capacity
    
    // Add a breakpoint and wait for it to be hit
    let bp = client
        .add_line_breakpoint(None, "com.jdbg.test.Main", 184, None)
        .await
        .expect("Failed to add breakpoint");
    
    // Wait for breakpoint to be hit
    sleep(Duration::from_secs(2)).await;
    
    // Get event info - should have events now
    let info = client.get_event_info(None).await.expect("Failed to get event info");
    assert!(info.buffered_count > 0, "Expected buffered events, got: {}", info.buffered_count);
    
    // Clean up
    client.remove_breakpoint(&bp.id).await.expect("Failed to remove breakpoint");
    client.detach_session(None).await.ok();
}

/// Test clearing events
#[tokio::test]
async fn test_clear_events() {
    let mut fixture = TestFixture::new();
    
    // Start server and test target
    fixture.start_server().await.expect("Failed to start server");
    fixture.start_test_target().await.expect("Failed to start test target");
    
    // Connect and attach
    let mut client = fixture.connect().await.expect("Failed to connect");
    let _session = fixture.attach(&mut client).await.expect("Failed to attach");
    
    // Add a breakpoint and wait for it to be hit
    let bp = client
        .add_line_breakpoint(None, "com.jdbg.test.Main", 184, None)
        .await
        .expect("Failed to add breakpoint");
    
    sleep(Duration::from_secs(2)).await;
    
    // Verify we have events
    let info = client.get_event_info(None).await.expect("Failed to get event info");
    assert!(info.buffered_count > 0, "Expected buffered events");
    
    // Clear events
    let response = client.clear_events(None).await.expect("Failed to clear events");
    assert!(response.success);
    
    // Verify events are cleared
    let info = client.get_event_info(None).await.expect("Failed to get event info");
    assert_eq!(info.buffered_count, 0, "Expected no buffered events after clear");
    
    // Clean up
    client.remove_breakpoint(&bp.id).await.expect("Failed to remove breakpoint");
    client.detach_session(None).await.ok();
}

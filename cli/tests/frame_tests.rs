//! Frame and variable inspection tests for JDBG.

mod common;

use std::time::Duration;
use tokio::time::sleep;

use common::TestFixture;

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


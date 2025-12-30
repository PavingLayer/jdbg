//! Execution control tests for JDBG (suspend, resume, step).

mod common;

use std::time::Duration;
use tokio::time::sleep;

use common::TestFixture;
use jdbg_client::StepDepth;

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


//! Thread operation tests for JDBG.

mod common;

use common::TestFixture;

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


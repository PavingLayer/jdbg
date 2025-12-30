//! Session management tests for JDBG.

mod common;

use common::TestFixture;

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


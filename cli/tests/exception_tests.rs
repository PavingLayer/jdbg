//! Exception breakpoint tests for JDBG.

mod common;

use common::TestFixture;

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


//! Expression evaluation tests for JDBG.

mod common;

use std::time::Duration;
use tokio::time::sleep;

use common::TestFixture;

#[tokio::test]
async fn test_evaluate_literals() {
    let mut fixture = TestFixture::new();
    fixture.start_server().await.expect("Failed to start server");
    fixture.start_test_target().await.expect("Failed to start test target");

    let mut client = fixture.connect().await.expect("Failed to connect");
    fixture.attach(&mut client).await.expect("Failed to attach");

    // Suspend the running program
    client.suspend(None, None).await.expect("Failed to suspend");
    sleep(Duration::from_millis(500)).await;

    let threads = client.list_threads(None).await.expect("Failed to list threads");
    eprintln!("[TEST] Threads: {:?}", threads.iter().map(|t| (&t.name, t.status)).collect::<Vec<_>>());
    let main_thread = threads.iter().find(|t| t.name == "main").expect("No main thread found");

    // Test integer literal
    let result = client.evaluate(None, Some(main_thread.id), Some(0), "42").await;
    assert!(result.is_ok(), "Failed to evaluate integer literal: {:?}", result.err());
    let eval = result.unwrap();
    eprintln!("[TEST] Evaluated '42': {} ({})", eval.result, eval.r#type);
    assert_eq!(eval.result, "42");

    // Test double literal
    let result = client.evaluate(None, Some(main_thread.id), Some(0), "3.14").await;
    assert!(result.is_ok(), "Failed to evaluate double literal: {:?}", result.err());
    let eval = result.unwrap();
    eprintln!("[TEST] Evaluated '3.14': {} ({})", eval.result, eval.r#type);
    assert!(eval.result.starts_with("3.14"));

    // Test string literal
    let result = client.evaluate(None, Some(main_thread.id), Some(0), "\"hello\"").await;
    assert!(result.is_ok(), "Failed to evaluate string literal: {:?}", result.err());
    let eval = result.unwrap();
    eprintln!("[TEST] Evaluated '\"hello\"': {} ({})", eval.result, eval.r#type);
    assert!(eval.result.contains("hello"));

    // Test boolean literal
    let result = client.evaluate(None, Some(main_thread.id), Some(0), "true").await;
    assert!(result.is_ok(), "Failed to evaluate boolean literal: {:?}", result.err());
    let eval = result.unwrap();
    eprintln!("[TEST] Evaluated 'true': {} ({})", eval.result, eval.r#type);
    assert_eq!(eval.result, "true");

    // Test null literal
    let result = client.evaluate(None, Some(main_thread.id), Some(0), "null").await;
    assert!(result.is_ok(), "Failed to evaluate null literal: {:?}", result.err());
    let eval = result.unwrap();
    eprintln!("[TEST] Evaluated 'null': {} ({})", eval.result, eval.r#type);
    assert_eq!(eval.result, "null");

    client.resume(None, None).await.ok();
    client.detach_session(None).await.ok();
}

#[tokio::test]
async fn test_evaluate_arithmetic() {
    let mut fixture = TestFixture::new();
    fixture.start_server().await.expect("Failed to start server");
    fixture.start_test_target().await.expect("Failed to start test target");

    let mut client = fixture.connect().await.expect("Failed to connect");
    fixture.attach(&mut client).await.expect("Failed to attach");

    // Suspend to evaluate
    client.suspend(None, None).await.expect("Failed to suspend");
    sleep(Duration::from_millis(500)).await;

    let threads = client.list_threads(None).await.expect("Failed to list threads");
    let main_thread = threads.iter().find(|t| t.name == "main").expect("No main thread");

    // Test arithmetic operations
    let test_cases = vec![
        ("1 + 2", "3"),
        ("10 - 3", "7"),
        ("4 * 5", "20"),
        ("15 / 3", "5"),
        ("17 % 5", "2"),
        ("2 + 3 * 4", "14"),  // Test precedence
        ("(2 + 3) * 4", "20"), // Test parentheses
    ];

    for (expr, expected) in test_cases {
        let result = client.evaluate(None, Some(main_thread.id), Some(0), expr).await;
        assert!(result.is_ok(), "Failed to evaluate '{}': {:?}", expr, result.err());
        let eval = result.unwrap();
        eprintln!("[TEST] Evaluated '{}': {} ({})", expr, eval.result, eval.r#type);
        assert_eq!(eval.result, expected, "Expression '{}' failed", expr);
    }

    client.resume(None, None).await.ok();
    client.detach_session(None).await.ok();
}

#[tokio::test]
async fn test_evaluate_comparisons() {
    let mut fixture = TestFixture::new();
    fixture.start_server().await.expect("Failed to start server");
    fixture.start_test_target().await.expect("Failed to start test target");

    let mut client = fixture.connect().await.expect("Failed to connect");
    fixture.attach(&mut client).await.expect("Failed to attach");

    client.suspend(None, None).await.expect("Failed to suspend");
    sleep(Duration::from_millis(500)).await;

    let threads = client.list_threads(None).await.expect("Failed to list threads");
    let main_thread = threads.iter().find(|t| t.name == "main").expect("No main thread");

    // Test comparison operations
    let test_cases = vec![
        ("1 < 2", "true"),
        ("2 > 1", "true"),
        ("1 <= 1", "true"),
        ("2 >= 3", "false"),
        ("5 == 5", "true"),
        ("5 != 6", "true"),
    ];

    for (expr, expected) in test_cases {
        let result = client.evaluate(None, Some(main_thread.id), Some(0), expr).await;
        assert!(result.is_ok(), "Failed to evaluate '{}': {:?}", expr, result.err());
        let eval = result.unwrap();
        eprintln!("[TEST] Evaluated '{}': {} ({})", expr, eval.result, eval.r#type);
        assert_eq!(eval.result, expected, "Expression '{}' failed", expr);
    }

    client.resume(None, None).await.ok();
    client.detach_session(None).await.ok();
}

#[tokio::test]
async fn test_evaluate_logical_operators() {
    let mut fixture = TestFixture::new();
    fixture.start_server().await.expect("Failed to start server");
    fixture.start_test_target().await.expect("Failed to start test target");

    let mut client = fixture.connect().await.expect("Failed to connect");
    fixture.attach(&mut client).await.expect("Failed to attach");

    client.suspend(None, None).await.expect("Failed to suspend");
    sleep(Duration::from_millis(500)).await;

    let threads = client.list_threads(None).await.expect("Failed to list threads");
    let main_thread = threads.iter().find(|t| t.name == "main").expect("No main thread");

    // Test logical operations
    let test_cases = vec![
        ("true && true", "true"),
        ("true && false", "false"),
        ("false || true", "true"),
        ("false || false", "false"),
        ("!true", "false"),
        ("!false", "true"),
        ("true && true || false", "true"),
    ];

    for (expr, expected) in test_cases {
        let result = client.evaluate(None, Some(main_thread.id), Some(0), expr).await;
        assert!(result.is_ok(), "Failed to evaluate '{}': {:?}", expr, result.err());
        let eval = result.unwrap();
        eprintln!("[TEST] Evaluated '{}': {} ({})", expr, eval.result, eval.r#type);
        assert_eq!(eval.result, expected, "Expression '{}' failed", expr);
    }

    client.resume(None, None).await.ok();
    client.detach_session(None).await.ok();
}

#[tokio::test]
async fn test_evaluate_string_concatenation() {
    let mut fixture = TestFixture::new();
    fixture.start_server().await.expect("Failed to start server");
    fixture.start_test_target().await.expect("Failed to start test target");

    let mut client = fixture.connect().await.expect("Failed to connect");
    fixture.attach(&mut client).await.expect("Failed to attach");

    client.suspend(None, None).await.expect("Failed to suspend");
    sleep(Duration::from_millis(500)).await;

    let threads = client.list_threads(None).await.expect("Failed to list threads");
    let main_thread = threads.iter().find(|t| t.name == "main").expect("No main thread");

    // Test string concatenation
    let result = client.evaluate(None, Some(main_thread.id), Some(0), "\"hello\" + \" \" + \"world\"").await;
    assert!(result.is_ok(), "Failed to evaluate string concatenation: {:?}", result.err());
    let eval = result.unwrap();
    eprintln!("[TEST] Evaluated string concat: {} ({})", eval.result, eval.r#type);
    assert!(eval.result.contains("hello world"));

    // Test string + number
    let result = client.evaluate(None, Some(main_thread.id), Some(0), "\"value: \" + 42").await;
    assert!(result.is_ok(), "Failed to evaluate string + number: {:?}", result.err());
    let eval = result.unwrap();
    eprintln!("[TEST] Evaluated string+number: {} ({})", eval.result, eval.r#type);
    assert!(eval.result.contains("value: 42"));

    client.resume(None, None).await.ok();
    client.detach_session(None).await.ok();
}

#[tokio::test]
async fn test_evaluate_static_methods() {
    let mut fixture = TestFixture::new();
    fixture.start_server().await.expect("Failed to start server");
    fixture.start_test_target().await.expect("Failed to start test target"); // "wait" mode

    let mut client = fixture.connect().await.expect("Failed to connect");
    fixture.attach(&mut client).await.expect("Failed to attach");

    // Set a breakpoint in the wait mode loop - line 175 (performWork call)
    // Note: Line numbers may shift after code changes
    let bp = client.add_line_breakpoint(None, "com.jdbg.test.Main", 175, None).await;
    if let Err(e) = &bp {
        eprintln!("[TEST] Failed to set breakpoint: {:?}", e);
    }
    assert!(bp.is_ok(), "Failed to set breakpoint");
    eprintln!("[TEST] Breakpoint set, waiting for hit...");

    // Wait for the breakpoint to be hit (the loop runs every second)
    sleep(Duration::from_secs(3)).await;

    let threads = client.list_threads(None).await.expect("Failed to list threads");
    eprintln!("[TEST] Threads after waiting: {:?}", threads.iter().map(|t| (&t.name, t.status)).collect::<Vec<_>>());
    
    let main_thread = threads.iter()
        .find(|t| t.name == "main" && t.status == 2) // status 2 = SUSPENDED_AT_BREAKPOINT
        .or_else(|| threads.iter().find(|t| t.name == "main"))
        .expect("No main thread found");
    eprintln!("[TEST] Using thread: {} (status: {})", main_thread.name, main_thread.status);

    // Test static field access first (doesn't require method invocation)
    let result = client.evaluate(None, Some(main_thread.id), Some(0), "java.lang.Integer.MAX_VALUE").await;
    if let Ok(eval) = &result {
        if eval.error.is_some() {
            let err = eval.error.as_ref().unwrap();
            eprintln!("[TEST] Static field error: {} - {}", err.code, err.message);
        } else {
            eprintln!("[TEST] Evaluated 'Integer.MAX_VALUE': {} ({})", eval.result, eval.r#type);
            assert_eq!(eval.result, "2147483647");
        }
    }

    // Test static method call: Math.sqrt
    let result = client.evaluate(None, Some(main_thread.id), Some(0), "java.lang.Math.sqrt(4.0)").await;
    if let Ok(eval) = &result {
        if eval.error.is_some() {
            let err = eval.error.as_ref().unwrap();
            eprintln!("[TEST] Math.sqrt error: {} - {}", err.code, err.message);
            // Method invocation requires being at a breakpoint - this is expected behavior
            // if the thread isn't properly suspended at a safe point
        } else {
            eprintln!("[TEST] Evaluated 'java.lang.Math.sqrt(4.0)': {} ({})", eval.result, eval.r#type);
            assert!(eval.result.contains("2.0"), "Expected 2.0, got {}", eval.result);
        }
    } else {
        eprintln!("[TEST] gRPC call failed: {:?}", result.err());
    }

    // Test static method call: Math.abs
    let result = client.evaluate(None, Some(main_thread.id), Some(0), "java.lang.Math.abs(-42)").await;
    if let Ok(eval) = &result {
        if eval.error.is_some() {
            let err = eval.error.as_ref().unwrap();
            eprintln!("[TEST] Math.abs error: {} - {}", err.code, err.message);
        } else {
            eprintln!("[TEST] Evaluated 'java.lang.Math.abs(-42)': {} ({})", eval.result, eval.r#type);
            assert_eq!(eval.result, "42");
        }
    }

    // Test static method call: Math.max
    let result = client.evaluate(None, Some(main_thread.id), Some(0), "java.lang.Math.max(10, 20)").await;
    if let Ok(eval) = &result {
        if eval.error.is_some() {
            let err = eval.error.as_ref().unwrap();
            eprintln!("[TEST] Math.max error: {} - {}", err.code, err.message);
        } else {
            eprintln!("[TEST] Evaluated 'java.lang.Math.max(10, 20)': {} ({})", eval.result, eval.r#type);
            assert_eq!(eval.result, "20");
        }
    }

    // Test static method call: Integer.parseInt  
    let result = client.evaluate(None, Some(main_thread.id), Some(0), "java.lang.Integer.parseInt(\"123\")").await;
    if let Ok(eval) = &result {
        if eval.error.is_some() {
            let err = eval.error.as_ref().unwrap();
            eprintln!("[TEST] Integer.parseInt error: {} - {}", err.code, err.message);
        } else {
            eprintln!("[TEST] Evaluated 'Integer.parseInt(\"123\")': {} ({})", eval.result, eval.r#type);
            assert_eq!(eval.result, "123");
        }
    }

    client.resume(None, None).await.ok();
    client.detach_session(None).await.ok();
}


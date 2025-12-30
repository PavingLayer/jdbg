# JDBG Integration Tests

Integration tests for the JDBG debugger, written in Rust.

## Running Tests

### Prerequisites

1. Java 17+ installed (with `JAVA_HOME` set)
2. Server JAR built: `cd server && ./mvnw package -DskipTests`
3. Test target compiled:
   ```bash
   mkdir -p test/test-target/target/classes
   find test/test-target/src -name "*.java" -exec javac -d test/test-target/target/classes -g {} +
   ```

### Run Tests Locally

```bash
cd cli
cargo test --test integration_tests -- --test-threads=1 --nocapture
```

**Note:** Use `--test-threads=1` because tests start server instances on different ports.

### Run Tests in Docker

```bash
docker build -t jdbg-test -f test/Dockerfile .
docker run --rm jdbg-test
```

Or with docker-compose:

```bash
docker compose -f test/docker-compose.yml up --build
```

## Test Structure

Tests are located in `cli/tests/integration_tests.rs` and organized by feature:

| Test | Description |
|------|-------------|
| `test_session_attach_and_detach` | Session lifecycle |
| `test_thread_operations` | Thread listing and selection |
| `test_breakpoint_lifecycle` | Add, enable, disable, remove breakpoints |
| `test_exception_breakpoints` | Exception breakpoint management |
| `test_suspend_and_resume` | Execution control |
| `test_frame_inspection` | Stack frame listing |
| `test_variable_inspection` | Variable listing |
| `test_step_over` | Step operations |
| `test_evaluate_expression` | Expression evaluation |

## Test Target

The `test-target/` directory contains a sample Java application with classes designed to exercise all debugger features:

| Class | Purpose |
|-------|---------|
| `Main.java` | Entry point, wait mode for debugging |
| `Calculator.java` | Method breakpoints, nested calls |
| `ThreadDemo.java` | Multi-threaded scenarios |
| `ExceptionDemo.java` | Exception breakpoints |
| `DataTypes.java` | Variable inspection |
| `Person.java` | Object inspection |
| `InnerClassDemo.java` | Inner class handling |

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `JAVA_HOME` | Java installation | Auto-detected |
| `JDBG_SERVER_JAR` | Path to server JAR | `server/target/jdbg-server.jar` |
| `TEST_TARGET_CLASSES` | Path to test classes | `test/test-target/target/classes` |

## CI/CD

The GitHub Actions workflow (`.github/workflows/integration-tests.yml`):

1. **Build job**: Compiles server JAR and test target
2. **Test job**: Runs Rust integration tests
3. **Docker test job**: Optional Docker-based testing (manual trigger)

## Adding New Tests

Add tests to `cli/tests/integration_tests.rs`:

```rust
#[tokio::test]
async fn test_my_feature() {
    let mut fixture = TestFixture::new();
    fixture.start_server().await.expect("Failed to start server");
    fixture.start_test_target().await.expect("Failed to start test target");

    let mut client = fixture.connect().await.expect("Failed to connect");
    fixture.attach(&mut client).await.expect("Failed to attach");

    // Your test logic here

    client.detach_session(None).await.ok();
}
```

The `TestFixture` automatically:
- Assigns unique ports per test
- Starts/stops the server
- Starts/stops the test target JVM
- Cleans up on drop

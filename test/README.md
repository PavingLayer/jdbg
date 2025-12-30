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

# Run all integration tests
cargo test --test '*' -- --test-threads=1

# Run a specific test file
cargo test --test eval_tests -- --test-threads=1

# Run a specific test with output
cargo test --test eval_tests test_evaluate_literals -- --test-threads=1 --nocapture
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

Tests are organized into separate files by feature area:

```
cli/tests/
├── common/
│   └── mod.rs              # Shared TestFixture and utilities
├── session_tests.rs        # Session attach/detach tests
├── thread_tests.rs         # Thread operations tests
├── breakpoint_tests.rs     # Breakpoint lifecycle tests
├── exception_tests.rs      # Exception breakpoint tests
├── execution_tests.rs      # Suspend/resume/step tests
├── frame_tests.rs          # Frame and variable inspection
└── eval_tests.rs           # Expression evaluation tests
```

### Test Files

| File | Tests | Description |
|------|-------|-------------|
| `session_tests.rs` | 1 | Session lifecycle (attach, detach, list) |
| `thread_tests.rs` | 1 | Thread listing and selection |
| `breakpoint_tests.rs` | 1 | Add, enable, disable, remove breakpoints |
| `exception_tests.rs` | 1 | Exception breakpoint management |
| `execution_tests.rs` | 2 | Suspend, resume, step operations |
| `frame_tests.rs` | 2 | Stack frame and variable listing |
| `eval_tests.rs` | 5 | Expression evaluation (literals, arithmetic, comparisons, etc.) |

### Java Unit Tests

The server also has Java unit tests:

```bash
cd server
./mvnw test
```

| Test Class | Tests | Description |
|------------|-------|-------------|
| `SessionManagerTest` | 2 | Session management |
| `ExpressionParserTest` | 90 | Expression parser unit tests |

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
| `JACOCO_AGENT` | Path to JaCoCo agent JAR | (optional, for coverage) |
| `COVERAGE_EXEC` | Path to coverage output file | (optional, for coverage) |

## CI/CD

The GitHub Actions workflow (`.github/workflows/integration-tests.yml`):

1. **Build job**: Compiles server JAR and test target
2. **Test job**: Runs Rust integration tests
3. **Coverage job**: Runs tests with JaCoCo for Java coverage and cargo-llvm-cov for Rust coverage
4. **Docker test job**: Optional Docker-based testing (manual trigger)

## Adding New Tests

1. Choose the appropriate test file or create a new one
2. Import the common module and required types:

```rust
mod common;

use std::time::Duration;
use tokio::time::sleep;
use common::TestFixture;

#[tokio::test]
async fn test_my_feature() {
    let mut fixture = TestFixture::new();
    fixture.start_server().await.expect("Failed to start server");
    fixture.start_test_target().await.expect("Failed to start test target");

    let mut client = fixture.connect().await.expect("Failed to connect");
    fixture.attach(&mut client).await.expect("Failed to attach");

    // Suspend if needed for inspection
    client.suspend(None, None).await.expect("Failed to suspend");
    sleep(Duration::from_millis(500)).await;

    // Your test logic here

    client.resume(None, None).await.ok();
    client.detach_session(None).await.ok();
}
```

The `TestFixture` automatically:
- Assigns unique ports per test
- Starts/stops the server
- Starts/stops the test target JVM
- Cleans up on drop (with graceful SIGTERM for coverage collection)

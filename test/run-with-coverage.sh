#!/bin/bash
# Run integration tests with coverage for both Java server and Rust CLI
set -e

# Ensure cargo is in PATH
[[ -f "$HOME/.cargo/env" ]] && source "$HOME/.cargo/env"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Configuration
COVERAGE_DIR="${PROJECT_ROOT}/coverage"
JACOCO_VERSION="0.8.11"
JACOCO_AGENT="${HOME}/.m2/repository/org/jacoco/org.jacoco.agent/${JACOCO_VERSION}/org.jacoco.agent-${JACOCO_VERSION}-runtime.jar"
JACOCO_CLI="${HOME}/.m2/repository/org/jacoco/org.jacoco.cli/${JACOCO_VERSION}/org.jacoco.cli-${JACOCO_VERSION}-nodeps.jar"
SERVER_JAR="${JDBG_SERVER_JAR:-${PROJECT_ROOT}/server/target/jdbg-server.jar}"
TEST_TARGET="${TEST_TARGET_CLASSES:-${PROJECT_ROOT}/test/test-target/target/classes}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() { echo -e "${GREEN}[coverage]${NC} $1"; }
warn() { echo -e "${YELLOW}[coverage]${NC} $1"; }
error() { echo -e "${RED}[coverage]${NC} $1"; }

# Ensure JaCoCo is available
ensure_jacoco() {
    if [[ ! -f "$JACOCO_AGENT" ]]; then
        log "Downloading JaCoCo agent..."
        cd "${PROJECT_ROOT}/server"
        ./mvnw dependency:get -Dartifact=org.jacoco:org.jacoco.agent:${JACOCO_VERSION}:jar:runtime -q
    fi
    
    if [[ ! -f "$JACOCO_CLI" ]]; then
        log "Downloading JaCoCo CLI..."
        cd "${PROJECT_ROOT}/server"
        ./mvnw dependency:get -Dartifact=org.jacoco:org.jacoco.cli:${JACOCO_VERSION}:jar:nodeps -q
    fi
}

# Run Rust integration tests (tests start their own servers with JaCoCo)
run_rust_tests() {
    log "Running Rust integration tests with coverage..."
    
    mkdir -p "$COVERAGE_DIR"
    
    cd "${PROJECT_ROOT}/cli"
    
    # Set environment for tests to use JaCoCo
    export JDBG_SERVER_JAR="$SERVER_JAR"
    export TEST_TARGET_CLASSES="$TEST_TARGET"
    export JACOCO_AGENT="$JACOCO_AGENT"
    export COVERAGE_EXEC="${COVERAGE_DIR}/jacoco-server"
    
    # Run tests with Rust coverage, tests will instrument Java server
    cargo llvm-cov --test integration_tests \
        --lcov --output-path "${COVERAGE_DIR}/rust-lcov.info" \
        -- --test-threads=1
    
    log "Rust coverage written to ${COVERAGE_DIR}/rust-lcov.info"
}

# Merge Java coverage files and generate report
generate_java_report() {
    log "Generating Java coverage report..."
    
    # Find all .exec files
    EXEC_FILES=$(find "$COVERAGE_DIR" -name "*.exec" 2>/dev/null | tr '\n' ' ')
    
    if [[ -z "$EXEC_FILES" ]]; then
        warn "No Java coverage data found"
        return 1
    fi
    
    log "Found coverage files: $EXEC_FILES"
    
    # Merge all .exec files into one
    java -jar "$JACOCO_CLI" merge $EXEC_FILES \
        --destfile "${COVERAGE_DIR}/jacoco-merged.exec"
    
    # Generate XML report for Codecov
    java -jar "$JACOCO_CLI" report "${COVERAGE_DIR}/jacoco-merged.exec" \
        --classfiles "${PROJECT_ROOT}/server/target/classes" \
        --sourcefiles "${PROJECT_ROOT}/server/src/main/java" \
        --xml "${COVERAGE_DIR}/java-coverage.xml" \
        --html "${COVERAGE_DIR}/java-html"
    
    log "Java coverage reports:"
    log "  XML:  ${COVERAGE_DIR}/java-coverage.xml"
    log "  HTML: ${COVERAGE_DIR}/java-html/index.html"
}

# Print summary
print_summary() {
    echo ""
    log "========== Coverage Summary =========="
    
    if [[ -f "${COVERAGE_DIR}/java-coverage.xml" ]]; then
        # Extract coverage stats from XML
        JAVA_COV=$(grep -oP '<counter type="INSTRUCTION"[^>]* covered="\K[0-9]+' "${COVERAGE_DIR}/java-coverage.xml" | head -1 || echo "0")
        JAVA_MISSED=$(grep -oP '<counter type="INSTRUCTION"[^>]* missed="\K[0-9]+' "${COVERAGE_DIR}/java-coverage.xml" | head -1 || echo "0")
        if [[ -n "$JAVA_COV" && -n "$JAVA_MISSED" && "$JAVA_COV" != "0" ]]; then
            JAVA_TOTAL=$((JAVA_COV + JAVA_MISSED))
            JAVA_PCT=$((JAVA_COV * 100 / JAVA_TOTAL))
            log "Java Server:  ${JAVA_PCT}% instruction coverage (${JAVA_COV}/${JAVA_TOTAL})"
        else
            log "Java Server:  Coverage data available"
        fi
    fi
    
    if [[ -f "${COVERAGE_DIR}/rust-lcov.info" ]]; then
        RUST_LINES=$(grep -c "^DA:" "${COVERAGE_DIR}/rust-lcov.info" 2>/dev/null || echo "0")
        log "Rust CLI:     ${RUST_LINES} lines covered"
    fi
    
    echo ""
    log "Coverage files for Codecov upload:"
    log "  - ${COVERAGE_DIR}/java-coverage.xml"
    log "  - ${COVERAGE_DIR}/rust-lcov.info"
    log "======================================"
}

# Main
main() {
    log "Starting coverage run..."
    
    ensure_jacoco
    run_rust_tests
    generate_java_report
    print_summary
    
    log "Coverage run complete!"
}

main "$@"

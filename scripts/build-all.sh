#!/bin/bash
# Build all JDBG components

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "=== Building JDBG ==="
echo ""

# Build Java server
echo "Building Java server..."
cd "$PROJECT_ROOT/server"
./mvnw package -DskipTests -q

# Build Rust CLI
echo "Building Rust CLI..."
cd "$PROJECT_ROOT/cli"
cargo build --release

echo ""
echo "=== Build complete ==="
echo ""
echo "Artifacts:"
echo "  CLI:    $PROJECT_ROOT/cli/target/release/jdbg"
echo "  Server: $PROJECT_ROOT/server/target/jdbg-server.jar"


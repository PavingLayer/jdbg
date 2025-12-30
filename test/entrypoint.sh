#!/bin/bash
set -e

case "$1" in
    test)
        echo "Running integration tests..."
        cargo test --test integration_tests -- --test-threads=1 --nocapture
        ;;
    coverage)
        echo "Running integration tests with coverage..."
        mkdir -p /jdbg/coverage
        cargo llvm-cov --test integration_tests --lcov --output-path /jdbg/coverage/lcov.info -- --test-threads=1
        cargo llvm-cov --test integration_tests --html --output-dir /jdbg/coverage/html -- --test-threads=1 || true
        echo "Coverage reports written to /jdbg/coverage/"
        ;;
    *)
        exec "$@"
        ;;
esac


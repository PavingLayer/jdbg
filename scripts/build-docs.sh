#!/bin/bash
# Build the documentation site using mdBook

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
DOC_DIR="$PROJECT_ROOT/doc"

# Check if mdBook is installed
if ! command -v mdbook &> /dev/null; then
    echo "mdBook not found. Installing..."
    cargo install mdbook
fi

# Check if mdbook-mermaid is installed
if ! command -v mdbook-mermaid &> /dev/null; then
    echo "mdbook-mermaid not found. Installing..."
    cargo install mdbook-mermaid
fi

# Install mermaid assets if not present
cd "$DOC_DIR"
if [[ ! -f "mermaid.min.js" ]]; then
    echo "Installing Mermaid assets..."
    mdbook-mermaid install .
fi

# Build the documentation
mdbook build

# mdBook's book.js assigns window.onunload to disable bfcache. Unload
# listeners are deprecated (Lighthouse: "Uses deprecated APIs").
find book -name 'book-*.js' -exec sed -i \
    -e '/Fix back button cache problem/d' \
    -e '/window\.onunload = function() { };/d' \
    {} +

echo ""
echo "Documentation built successfully!"
echo "Output: $DOC_DIR/book/"
echo ""
echo "To view locally, run:"
echo "  cd $DOC_DIR && mdbook serve --open"


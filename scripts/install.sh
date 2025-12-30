#!/bin/bash
# Install JDBG to user's local directory

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Default installation prefix
PREFIX="${PREFIX:-$HOME/.local}"
BIN_DIR="$PREFIX/bin"
SHARE_DIR="$PREFIX/share/jdbg"

echo "Installing JDBG to $PREFIX"
echo ""

# Create directories
mkdir -p "$BIN_DIR" "$SHARE_DIR"

# Check if built
CLI="$PROJECT_ROOT/cli/target/release/jdbg"
SERVER="$PROJECT_ROOT/server/target/jdbg-server.jar"

if [[ ! -f "$CLI" ]] || [[ ! -f "$SERVER" ]]; then
    echo "Components not built. Building..."
    "$SCRIPT_DIR/build-all.sh"
fi

# Install CLI
echo "Installing CLI..."
cp "$CLI" "$BIN_DIR/jdbg"
chmod +x "$BIN_DIR/jdbg"

# Install server JAR
echo "Installing server..."
cp "$SERVER" "$SHARE_DIR/jdbg-server.jar"

echo ""
echo "=== Installation complete ==="
echo ""
echo "Installed to:"
echo "  CLI:    $BIN_DIR/jdbg"
echo "  Server: $SHARE_DIR/jdbg-server.jar"
echo ""

# Check if bin dir is in PATH
if [[ ":$PATH:" != *":$BIN_DIR:"* ]]; then
    echo "Note: $BIN_DIR is not in your PATH"
    echo "Add this to your shell profile:"
    echo ""
    echo "  export PATH=\"$BIN_DIR:\$PATH\""
    echo ""
fi

# Install shell completions
echo "To install shell completions:"
echo ""
echo "  # Bash"
echo "  jdbg completions bash > ~/.local/share/bash-completion/completions/jdbg"
echo ""
echo "  # Zsh"
echo "  jdbg completions zsh > ~/.zfunc/_jdbg"
echo ""
echo "  # Fish"
echo "  jdbg completions fish > ~/.config/fish/completions/jdbg.fish"


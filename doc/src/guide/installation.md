# Installation

## Requirements

- **Rust CLI**: No runtime dependencies (statically linked)
- **Java Server**: Java 17+ runtime

## Installation Methods

### Option 1: Download Binary Release

```bash
# Download and extract
curl -LO https://github.com/your-org/jdbg/releases/latest/download/jdbg-linux-x86_64.tar.gz
tar xzf jdbg-linux-x86_64.tar.gz

# Install CLI
sudo mv jdbg /usr/local/bin/

# Install server JAR
sudo mkdir -p /usr/local/share/jdbg
sudo mv jdbg-server.jar /usr/local/share/jdbg/
```

### Option 2: Build from Source

```bash
# Prerequisites
# - Rust toolchain (rustup.rs)
# - Java 17+ and Maven

git clone https://github.com/your-org/jdbg.git
cd jdbg

# Build CLI
cd cli && cargo build --release
cd ..

# Build server
cd server && ./mvnw package -DskipTests
cd ..
```

### Option 3: User Installation

```bash
# Install to ~/.local (no root required)
mkdir -p ~/.local/bin ~/.local/share/jdbg

cp cli/target/release/jdbg ~/.local/bin/
cp server/target/jdbg-server.jar ~/.local/share/jdbg/

# Add to PATH (add to ~/.bashrc or ~/.zshrc)
export PATH="$HOME/.local/bin:$PATH"
```

## Shell Completions

Generate and install shell completions:

```bash
# Bash
jdbg completions bash > ~/.local/share/bash-completion/completions/jdbg

# Zsh
jdbg completions zsh > ~/.zfunc/_jdbg
# Add to ~/.zshrc: fpath=(~/.zfunc $fpath)

# Fish
jdbg completions fish > ~/.config/fish/completions/jdbg.fish
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `JDBG_SERVER` | Server address | `tcp://127.0.0.1:5005` |
| `JDBG_SERVER_JAR` | Path to server JAR | Auto-detected |
| `JAVA_HOME` | Java installation | Auto-detected |

## Verify Installation

```bash
# Check CLI version
jdbg --version

# Start server and check status
jdbg server start
jdbg server status
jdbg server stop
```


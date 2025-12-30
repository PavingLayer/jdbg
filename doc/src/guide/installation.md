# Installation

## Requirements

- **Rust CLI**: No runtime dependencies (statically linked)
- **Java Server**: Java 17+ runtime

## Quick Install

### Linux (x86_64)

```bash
curl -LO https://github.com/PavingLayer/jdbg/releases/latest/download/jdbg-linux-x86_64.tar.gz
tar xzf jdbg-linux-x86_64.tar.gz
sudo mv jdbg /usr/local/bin/
sudo mkdir -p /usr/local/share/jdbg
sudo mv jdbg-server.jar /usr/local/share/jdbg/
```

### Linux (ARM64)

```bash
curl -LO https://github.com/PavingLayer/jdbg/releases/latest/download/jdbg-linux-aarch64.tar.gz
tar xzf jdbg-linux-aarch64.tar.gz
sudo mv jdbg /usr/local/bin/
sudo mkdir -p /usr/local/share/jdbg
sudo mv jdbg-server.jar /usr/local/share/jdbg/
```

### macOS (Intel)

```bash
curl -LO https://github.com/PavingLayer/jdbg/releases/latest/download/jdbg-macos-x86_64.tar.gz
tar xzf jdbg-macos-x86_64.tar.gz
sudo mv jdbg /usr/local/bin/
sudo mkdir -p /usr/local/share/jdbg
sudo mv jdbg-server.jar /usr/local/share/jdbg/
```

### macOS (Apple Silicon)

```bash
curl -LO https://github.com/PavingLayer/jdbg/releases/latest/download/jdbg-macos-aarch64.tar.gz
tar xzf jdbg-macos-aarch64.tar.gz
sudo mv jdbg /usr/local/bin/
sudo mkdir -p /usr/local/share/jdbg
sudo mv jdbg-server.jar /usr/local/share/jdbg/
```

### Windows

1. Download [jdbg-windows-x86_64.zip](https://github.com/PavingLayer/jdbg/releases/latest/download/jdbg-windows-x86_64.zip)
2. Extract the archive
3. Add the extracted folder to your PATH, or move files to a location in PATH

```powershell
# PowerShell
Invoke-WebRequest -Uri "https://github.com/PavingLayer/jdbg/releases/latest/download/jdbg-windows-x86_64.zip" -OutFile "jdbg.zip"
Expand-Archive -Path "jdbg.zip" -DestinationPath "$env:LOCALAPPDATA\jdbg"

# Add to PATH (run as Administrator, or add manually)
[Environment]::SetEnvironmentVariable("Path", $env:Path + ";$env:LOCALAPPDATA\jdbg", "User")
```

## Build from Source

### Prerequisites

- [Rust toolchain](https://rustup.rs/) (1.70+)
- Java 17+ and Maven
- Protocol Buffers compiler (`protoc`)

### Install protoc

```bash
# Ubuntu/Debian
sudo apt-get install protobuf-compiler

# macOS
brew install protobuf

# Windows (with Chocolatey)
choco install protoc
```

### Build

```bash
git clone https://github.com/PavingLayer/jdbg.git
cd jdbg

# Build CLI
cd cli && cargo build --release
cd ..

# Build server
cd server && ./mvnw package -DskipTests
cd ..
```

### Install from Build

```bash
# Linux/macOS - System-wide (requires root)
sudo cp cli/target/release/jdbg /usr/local/bin/
sudo mkdir -p /usr/local/share/jdbg
sudo cp server/target/jdbg-server.jar /usr/local/share/jdbg/

# Linux/macOS - User installation (no root)
mkdir -p ~/.local/bin ~/.local/share/jdbg
cp cli/target/release/jdbg ~/.local/bin/
cp server/target/jdbg-server.jar ~/.local/share/jdbg/
# Add to ~/.bashrc or ~/.zshrc:
export PATH="$HOME/.local/bin:$PATH"
```

## Shell Completions

Generate and install shell completions:

```bash
# Bash
jdbg completions bash > ~/.local/share/bash-completion/completions/jdbg

# Zsh
mkdir -p ~/.zfunc
jdbg completions zsh > ~/.zfunc/_jdbg
# Add to ~/.zshrc: fpath=(~/.zfunc $fpath)

# Fish
jdbg completions fish > ~/.config/fish/completions/jdbg.fish

# PowerShell
jdbg completions powershell >> $PROFILE
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

## Troubleshooting

### "jdbg: command not found"

Ensure the binary is in your PATH:
```bash
which jdbg
echo $PATH
```

### "Java not found" or "JAVA_HOME not set"

Install Java 17+ and set JAVA_HOME:
```bash
# Check Java version
java -version

# Set JAVA_HOME (add to shell profile)
export JAVA_HOME=/path/to/java
```

### "Server JAR not found"

Set the path explicitly:
```bash
export JDBG_SERVER_JAR=/path/to/jdbg-server.jar
```

Or place it in the default location: `/usr/local/share/jdbg/jdbg-server.jar`

# Getting Started

## Prerequisites

- **Java 17+** - Required for the server component
- **A target JVM** - Started with debug options enabled

### Enable Debugging on Target JVM

Add these JVM arguments to your target application:

```bash
# For Java 9+ (allows remote connections)
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:8000

# For Java 8
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000
```

## Installation

See the [Installation Guide](installation.md) for detailed instructions.

### Quick Install (Linux x86_64)

```bash
curl -LO https://github.com/PavingLayer/jdbg/releases/latest/download/jdbg-linux-x86_64.tar.gz
tar xzf jdbg-linux-x86_64.tar.gz
sudo mv jdbg /usr/local/bin/
sudo mkdir -p /usr/local/share/jdbg
sudo mv jdbg-server.jar /usr/local/share/jdbg/
```

### Quick Install (macOS Apple Silicon)

```bash
curl -LO https://github.com/PavingLayer/jdbg/releases/latest/download/jdbg-macos-aarch64.tar.gz
tar xzf jdbg-macos-aarch64.tar.gz
sudo mv jdbg /usr/local/bin/
sudo mkdir -p /usr/local/share/jdbg
sudo mv jdbg-server.jar /usr/local/share/jdbg/
```

### Build from Source

```bash
git clone https://github.com/PavingLayer/jdbg.git
cd jdbg

# Build the Rust CLI
cd cli && cargo build --release && cd ..

# Build the Java server
cd server && ./mvnw package -DskipTests && cd ..

# Install
sudo cp cli/target/release/jdbg /usr/local/bin/
sudo mkdir -p /usr/local/share/jdbg
sudo cp server/target/jdbg-server.jar /usr/local/share/jdbg/
```

## First Steps

### 1. Start the JDBG Server

```bash
jdbg server start
```

### 2. Attach to Your JVM

```bash
jdbg session attach --host localhost --port 8000
```

### 3. Explore

```bash
# List threads
jdbg thread list

# Add a breakpoint
jdbg bp add --class com.example.Main --line 42

# Suspend execution
jdbg exec suspend

# List stack frames
jdbg frame list

# List variables in current frame
jdbg var list

# Evaluate an expression
jdbg eval "myVariable + 10"

# Resume execution
jdbg exec resume
```

### 4. Cleanup

```bash
jdbg session detach
jdbg server stop
```

## Next Steps

- [Commands Reference](../commands/index.md) - Full command documentation
- [Architecture](../architecture.md) - How JDBG works
- [Scripting Guide](scripting.md) - Automate debugging tasks

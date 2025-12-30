# Getting Started

## Prerequisites

- **Java 17+** - Required for the server component
- **A target JVM** - Started with debug options enabled

### Enable Debugging on Target JVM

Add these JVM arguments to your target application:

```bash
# For Java 9+
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:8000

# For Java 8
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000
```

## Installation

### From Binary Releases

Download the latest release for your platform:

```bash
# Linux/macOS
curl -LO https://github.com/PavingLayer/jdbg/releases/latest/download/jdbg-linux-x86_64.tar.gz
tar xzf jdbg-linux-x86_64.tar.gz
sudo mv jdbg /usr/local/bin/
sudo mv jdbg-server.jar /usr/local/share/jdbg/
```

### From Source

```bash
# Clone the repository
git clone https://github.com/PavingLayer/jdbg.git
cd jdbg

# Build the Rust CLI
cd cli && cargo build --release
cd ..

# Build the Java server
cd server && ./mvnw package -DskipTests
cd ..

# Install (optional)
cp cli/target/release/jdbg ~/.local/bin/
mkdir -p ~/.local/share/jdbg
cp server/target/jdbg-server.jar ~/.local/share/jdbg/
```

## First Steps

1. **Start the server**:
   ```bash
   jdbg server start
   ```

2. **Attach to your JVM**:
   ```bash
   jdbg session attach --host localhost --port 8000
   ```

3. **Explore**:
   ```bash
   jdbg thread list
   jdbg bp add --class com.example.Main --method main
   jdbg exec suspend
   jdbg frame list
   jdbg exec resume
   ```

4. **Cleanup**:
   ```bash
   jdbg session detach
   jdbg server stop
   ```


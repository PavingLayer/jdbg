# Configuration

JDBG can be configured via environment variables and configuration files.

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `JDBG_SERVER` | Server address | `tcp://127.0.0.1:5005` |
| `JDBG_SERVER_JAR` | Path to server JAR | Auto-detected |
| `JAVA_HOME` | Java installation | Auto-detected |

## Server Address Formats

The server address can be specified in these formats:

- `tcp://host:port` - TCP socket (default: `tcp://127.0.0.1:5005`)
- `unix:///path/to/socket` - Unix domain socket (Linux/macOS)

## JAR Location

The CLI looks for `jdbg-server.jar` in these locations (in order):

1. `$JDBG_SERVER_JAR` environment variable
2. `../server/target/jdbg-server.jar` (relative to CLI binary, for development)
3. Same directory as CLI binary
4. `/usr/share/jdbg/jdbg-server.jar`
5. `/usr/local/share/jdbg/jdbg-server.jar`
6. `~/.local/share/jdbg/jdbg-server.jar`

## Data Directory

JDBG stores data in:
- **Linux/macOS**: `~/.local/share/jdbg/`
- **Windows**: `%LOCALAPPDATA%\jdbg\`

Contents:
- `server.log` - Server output log
- `jdbg-server.jar` - Server JAR (if installed here)

## Runtime Directory

PID file location:
- **Linux**: `$XDG_RUNTIME_DIR/jdbg-server.pid` (usually `/run/user/<uid>/`)
- **macOS/Windows**: Same as data directory

## Configuration File (Future)

A future version may support a configuration file at `~/.config/jdbg/config.toml`:

```toml
# Default server address
server = "tcp://127.0.0.1:5005"

# Default output format
format = "text"

# Source paths for source code lookup
source_paths = [
    "/path/to/project/src",
    "/path/to/dependencies/src"
]
```


# Server Management

The JDBG server is a daemon process that maintains persistent JDI connections to target JVMs.

## Commands

### `jdbg server start`

Start the JDBG server daemon.

```bash
jdbg server start [OPTIONS]
```

**Options:**
- `--listen <ADDRESS>` - Listen address (default: `tcp://127.0.0.1:5005`)
- `--foreground` - Run in foreground instead of daemonizing

**Examples:**
```bash
# Start with defaults
jdbg server start

# Start on a different port
jdbg server start --listen tcp://127.0.0.1:9999

# Run in foreground (for debugging)
jdbg server start --foreground
```

### `jdbg server stop`

Stop the running JDBG server.

```bash
jdbg server stop
```

This sends a graceful shutdown signal (SIGTERM) and waits up to 5 seconds for the server to stop.

### `jdbg server status`

Check if the server is running and show current status.

```bash
jdbg server status
```

**Example output:**
```
Server is running (PID: 12345)
  Active sessions: 2
  Active session: abc12345
  Log file: /home/user/.local/share/jdbg/server.log
```

## Files

| File | Location | Description |
|------|----------|-------------|
| PID file | `$XDG_RUNTIME_DIR/jdbg-server.pid` | Server process ID |
| Log file | `~/.local/share/jdbg/server.log` | Server output log |

## Environment Variables

| Variable | Description |
|----------|-------------|
| `JDBG_SERVER` | Default server address for CLI commands |
| `JDBG_SERVER_JAR` | Path to `jdbg-server.jar` |
| `JAVA_HOME` | Java installation directory |

## Troubleshooting

### Server fails to start

Check the log file:
```bash
cat ~/.local/share/jdbg/server.log
```

Common issues:
- Port already in use
- Java not found
- Server JAR not found

### Server JAR not found

Set the `JDBG_SERVER_JAR` environment variable:
```bash
export JDBG_SERVER_JAR=/path/to/jdbg-server.jar
jdbg server start
```

### Connection refused

Ensure the server is running:
```bash
jdbg server status
```

If not running, start it:
```bash
jdbg server start
```


# Session Management

Sessions represent connections to target JVMs.

## Commands

### `jdbg session attach`

Attach to a remote JVM via TCP.

```bash
jdbg session attach [OPTIONS]
```

**Options:**
- `--host, -H <HOST>` - Remote host (default: `localhost`)
- `--port, -p <PORT>` - Debug port (required)
- `--name, -n <NAME>` - Session name (auto-generated if not specified)
- `--timeout <MS>` - Connection timeout in milliseconds (default: 5000)

**Examples:**
```bash
# Attach to local JVM
jdbg session attach --port 8000

# Attach to remote JVM
jdbg session attach --host server.example.com --port 8000

# Named session
jdbg session attach --host prod-server --port 8000 --name prod-debug
```

### `jdbg session attach-pid`

Attach to a local JVM by process ID.

```bash
jdbg session attach-pid <PID> [OPTIONS]
```

**Options:**
- `--name, -n <NAME>` - Session name
- `--timeout <MS>` - Connection timeout

**Example:**
```bash
jdbg session attach-pid 12345
```

### `jdbg session detach`

Detach from a session.

```bash
jdbg session detach [OPTIONS]
```

**Options:**
- `--session, -s <ID>` - Session to detach (default: active session)
- `--terminate, -t` - Terminate the target JVM

### `jdbg session status`

Get the status of a session.

```bash
jdbg session status [OPTIONS]
```

**Options:**
- `--session, -s <ID>` - Session to query (default: active session)

### `jdbg session list`

List all sessions.

```bash
jdbg session list
```

**Example output:**
```
Sessions:
  * abc12345 [Connected] AttachedRemote
    def67890 [Suspended] AttachedRemote
```

The `*` indicates the active session.

### `jdbg session select`

Select the active session.

```bash
jdbg session select <SESSION_ID>
```

## JSON Output

Use `-f json` for machine-readable output:

```bash
jdbg -f json session list | jq '.data.sessions[].id'
```


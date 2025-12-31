# Session Management

Sessions represent connections to target JVMs. Each session can be identified by either its auto-generated ID or a human-readable name.

## Session Naming

Sessions can be given human-readable names for easier identification:

```bash
# Attach with a name
jdbg session attach --host prod-server --port 8000 --name prod

# Reference by name
jdbg status --session prod
jdbg session select prod
jdbg exec continue --session prod
```

## Commands

### `jdbg session attach`

Attach to a remote JVM via TCP.

```bash
jdbg session attach [OPTIONS]
```

**Options:**
- `--host, -H <HOST>` - Remote host (default: `localhost`)
- `--port, -p <PORT>` - Debug port (required)
- `--name, -n <NAME>` - Human-readable session name (optional)
- `--timeout <MS>` - Connection timeout in milliseconds (default: 5000)

**Examples:**
```bash
# Attach to local JVM
jdbg session attach --port 8000

# Attach to remote JVM
jdbg session attach --host server.example.com --port 8000

# Named session for easier reference
jdbg session attach --host prod-server --port 8000 --name prod
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
jdbg session attach-pid 12345 --name local-app
```

### `jdbg session detach`

Detach from a session.

```bash
jdbg session detach [OPTIONS]
```

**Options:**
- `--session, -s <ID|NAME>` - Session to detach (default: active session)
- `--terminate, -t` - Terminate the target JVM

### `jdbg session info`

Get detailed information about a session.

```bash
jdbg session info [OPTIONS]
```

**Options:**
- `--session, -s <ID|NAME>` - Session to query (default: active session)

**Example output:**
```
Session: prod (c48bc02d)
  Type: AttachedRemote
  State: Connected
  Target: prod-server.local:8000
  VM: OpenJDK 64-Bit Server VM 17.0.8
```

### `jdbg session list`

List all sessions with connection info.

```bash
jdbg session list
```

**Example output:**
```
Sessions:
  * prod (c48bc02d) [Connected] AttachedRemote prod-server:8000
    staging (def67890) [Suspended] AttachedRemote staging-server:8000
    local (abc12345) [Connected] AttachedLocal pid:12345
```

- `*` indicates the active session
- Format: `name (id) [state] type connection-info`

### `jdbg session select`

Select the active session (by ID or name).

```bash
jdbg session select <SESSION>
```

**Examples:**
```bash
jdbg session select prod
jdbg session select c48bc02d
```

### `jdbg session rename`

Rename a session.

```bash
jdbg session rename <NEW_NAME> [OPTIONS]
```

**Options:**
- `--session, -s <ID|NAME>` - Session to rename (default: active session)

**Examples:**
```bash
jdbg session rename production
jdbg session rename staging --session def67890
```

## JSON Output

Use `-f json` for machine-readable output:

```bash
jdbg -f json session list | jq '.sessions[] | {name: .name, id: .id, host: .host, port: .port}'
```

## Working with Multiple Sessions

```bash
# Attach to multiple servers
jdbg session attach --host prod-server --port 8000 --name prod
jdbg session attach --host staging-server --port 8000 --name staging

# List sessions
jdbg session list

# Work with specific session
jdbg bp add --class com.example.Main --line 42 --session prod
jdbg exec continue --session staging

# Switch active session
jdbg session select prod
```


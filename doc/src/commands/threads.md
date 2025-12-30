# Thread Management

Inspect and manage threads in the target JVM.

## Commands

### `jdbg thread list` (alias: `jdbg thread ls`)

List all threads.

```bash
jdbg thread list [OPTIONS]
```

**Options:**
- `--suspended-only` - Show only suspended threads
- `--session, -s <ID>` - Session ID

**Example output:**
```
Threads:
  1 "main" Running
  12 "http-nio-8080-exec-1" Wait (suspended)
  13 "http-nio-8080-exec-2" Running
```

### `jdbg thread select`

Select a thread as the current thread for frame/variable operations.

```bash
jdbg thread select <THREAD_ID> [OPTIONS]
```

**Options:**
- `--session, -s <ID>` - Session ID

### `jdbg thread suspend`

Suspend a specific thread.

```bash
jdbg thread suspend <THREAD_ID> [OPTIONS]
```

**Options:**
- `--session, -s <ID>` - Session ID

### `jdbg thread resume`

Resume a specific thread.

```bash
jdbg thread resume <THREAD_ID> [OPTIONS]
```

**Options:**
- `--session, -s <ID>` - Session ID

### `jdbg thread info`

Get detailed information about a thread.

```bash
jdbg thread info [OPTIONS]
```

**Options:**
- `--thread, -t <ID>` - Thread ID (default: selected thread)
- `--session, -s <ID>` - Session ID

## Thread States

| State | Description |
|-------|-------------|
| `Running` | Thread is running |
| `Sleeping` | Thread is sleeping (Thread.sleep) |
| `Wait` | Thread is waiting (Object.wait) |
| `Monitor` | Thread is waiting for a monitor lock |
| `Zombie` | Thread has terminated |
| `NotStarted` | Thread hasn't started yet |

## JSON Output

```bash
# Get IDs of suspended threads
jdbg -f json thread list | jq '.data[] | select(.suspended) | .id'

# Get thread names
jdbg -f json thread list | jq -r '.data[] | "\(.id): \(.name)"'
```


# Status

Show a comprehensive overview of the JVM state, suspended threads, and breakpoints.

## Usage

```bash
jdbg status [OPTIONS]
```

**Options:**
- `-s, --session <ID>` - Session ID or name (default: active session)

## Output

The status command provides:

1. **Session info** - Current session ID, name, and connection details
2. **JVM state** - Whether the JVM is running or suspended
3. **Threads at breakpoints** - Which threads hit breakpoints and where
4. **Other suspended threads** - Threads suspended for other reasons

## Example Output

```
Session: hybris (c48bc02d)
  Target: test-server.local:8000

JVM State: 2 of 147 threads suspended

Threads at Breakpoints:
  1 "main" at com.example.Main:42 (bp: bp-001)
    → com.example.Main.process:42
  23 "worker-1" at com.example.Service:128 (bp: bp-002)
    → com.example.Service.handle:128

Other Suspended Threads:
  45 "gc-thread" at sun.misc.GC:15
```

## Use Cases

### Check if execution is paused

```bash
status=$(jdbg -f json status | jq '.suspended_thread_count')
if [ "$status" -gt 0 ]; then
    echo "Execution paused"
fi
```

### Find which breakpoint was hit

```bash
jdbg -f json status | jq '.suspended_threads[] | select(.at_breakpoint) | {thread: .thread_name, breakpoint: .breakpoint_id, location: .breakpoint_location}'
```

### Automated debugging flow

```bash
#!/bin/bash
# Wait for a breakpoint, then check status
jdbg exec continue
jdbg events wait -t breakpoint --timeout 30000

# Show status
jdbg status

# Inspect the thread at breakpoint
thread=$(jdbg -f json status | jq -r '.suspended_threads[] | select(.at_breakpoint) | .thread_id' | head -1)
jdbg var list -t "$thread"
```

## JSON Output

```bash
jdbg -f json status
```

```json
{
  "session": {
    "id": "c48bc02d",
    "name": "hybris",
    "type": "ATTACHED_REMOTE",
    "state": "SUSPENDED",
    "host": "test-server.local",
    "port": 8000
  },
  "jvm_suspended": false,
  "suspended_thread_count": 2,
  "total_thread_count": 147,
  "suspended_threads": [
    {
      "thread_id": 1,
      "thread_name": "main",
      "at_breakpoint": true,
      "breakpoint_id": "bp-001",
      "breakpoint_location": "com.example.Main:42",
      "current_location": "com.example.Main.process:42"
    },
    {
      "thread_id": 23,
      "thread_name": "worker-1",
      "at_breakpoint": true,
      "breakpoint_id": "bp-002",
      "breakpoint_location": "com.example.Service:128",
      "current_location": "com.example.Service.handle:128"
    }
  ]
}
```


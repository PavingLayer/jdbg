# Automate Debugging Tasks

This guide shows how to script debugging workflows for automation, CI/CD pipelines, and repeatable debugging sessions.

## Why Automate Debugging?

- **Reproducible investigations** - Same steps every time
- **CI/CD integration** - Automated regression debugging
- **Data collection** - Capture state at specific points
- **Test harnesses** - Verify debugger behavior

## JSON Output

All JDBG commands support JSON output for scripting:

```bash
jdbg -f json status
jdbg -f json bp list
jdbg -f json var list
```

Parse with `jq`:

```bash
# Get session state
jdbg -f json status | jq -r '.data.state'

# List suspended thread IDs
jdbg -f json thread list | jq -r '.data[] | select(.suspended) | .id'

# Get a specific variable value
jdbg -f json var get --name userId | jq -r '.data.value'
```

## Basic Automation Script

```bash
#!/bin/bash
set -e

# Configuration
TARGET_HOST="${TARGET_HOST:-localhost}"
TARGET_PORT="${TARGET_PORT:-8000}"
BREAKPOINT_CLASS="com.example.UserService"
BREAKPOINT_LINE=42
TIMEOUT=30000

# Start server if not running
if ! jdbg server status &>/dev/null; then
    jdbg server start
    sleep 1
fi

# Attach to target
jdbg session attach --host "$TARGET_HOST" --port "$TARGET_PORT" --name auto-debug

# Set breakpoint
BP_ID=$(jdbg -f json bp add --class "$BREAKPOINT_CLASS" --line "$BREAKPOINT_LINE" | jq -r '.data.id')
echo "Breakpoint set: $BP_ID"

# Wait for hit
echo "Waiting for breakpoint (${TIMEOUT}ms timeout)..."
EVENT=$(jdbg -f json events wait -t breakpoint --timeout "$TIMEOUT")

if echo "$EVENT" | jq -e '.data.events[0]' &>/dev/null; then
    echo "Breakpoint hit!"
    
    # Collect debugging data
    echo "=== Status ===" 
    jdbg status
    
    echo "=== Variables ==="
    jdbg var list
    
    echo "=== Call Stack ==="
    jdbg frame list
else
    echo "Timeout waiting for breakpoint"
    exit 1
fi

# Cleanup
jdbg bp remove --id "$BP_ID"
jdbg session detach
```

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Debug Test Failures

on:
  workflow_dispatch:
    inputs:
      breakpoint_class:
        description: 'Class to debug'
        required: true
      breakpoint_line:
        description: 'Line number'
        required: true

jobs:
  debug:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Install JDBG
        run: |
          curl -LO https://github.com/PavingLayer/jdbg/releases/latest/download/jdbg-linux-x86_64.tar.gz
          tar xzf jdbg-linux-x86_64.tar.gz
          sudo mv jdbg /usr/local/bin/
          sudo mv jdbg-server.jar /usr/local/share/jdbg/
      
      - name: Start application with debug
        run: |
          java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:8000 \
               -jar target/myapp.jar &
          sleep 2
      
      - name: Debug session
        run: |
          jdbg server start
          jdbg session attach --host localhost --port 8000
          jdbg bp add --class "${{ inputs.breakpoint_class }}" --line "${{ inputs.breakpoint_line }}"
          jdbg exec continue  # Release suspend=y
          
          # Wait for breakpoint
          jdbg events wait -t breakpoint --timeout 60000
          
          # Capture state
          jdbg -f json status > debug-status.json
          jdbg -f json var list > debug-vars.json
          jdbg -f json frame list > debug-frames.json
          
          jdbg session detach
      
      - name: Upload debug artifacts
        uses: actions/upload-artifact@v4
        with:
          name: debug-output
          path: debug-*.json
```

## Data Collection Script

Collect debugging data at multiple breakpoints:

```bash
#!/bin/bash
# Collect debugging snapshots at multiple points

OUTPUT_DIR="debug-snapshots-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUTPUT_DIR"

jdbg server start
jdbg session attach --host localhost --port 8000 --name collector

# Define collection points
BREAKPOINTS=(
    "com.example.OrderService:createOrder:45"
    "com.example.PaymentService:processPayment:30"
    "com.example.InventoryService:reserve:22"
)

# Set all breakpoints
for bp in "${BREAKPOINTS[@]}"; do
    IFS=':' read -r class method line <<< "$bp"
    if [ -n "$method" ]; then
        jdbg bp add --class "$class" --method "$method"
    else
        jdbg bp add --class "$class" --line "$line"
    fi
done

echo "Collecting data at ${#BREAKPOINTS[@]} breakpoints..."

SNAPSHOT=0
while true; do
    # Wait for any breakpoint
    EVENT=$(jdbg -f json events wait -t breakpoint --timeout 5000 2>/dev/null)
    
    if echo "$EVENT" | jq -e '.data.events[0]' &>/dev/null; then
        SNAPSHOT=$((SNAPSHOT + 1))
        SNAPSHOT_DIR="$OUTPUT_DIR/snapshot-$SNAPSHOT"
        mkdir -p "$SNAPSHOT_DIR"
        
        # Get location
        LOCATION=$(jdbg -f json status | jq -r '.data.suspended_threads[0].current_location')
        echo "Snapshot $SNAPSHOT at $LOCATION"
        
        # Collect data
        jdbg -f json status > "$SNAPSHOT_DIR/status.json"
        jdbg -f json thread list > "$SNAPSHOT_DIR/threads.json"
        jdbg -f json frame list > "$SNAPSHOT_DIR/frames.json"
        jdbg -f json var list > "$SNAPSHOT_DIR/variables.json"
        
        # Continue to next breakpoint
        jdbg exec continue
    fi
    
    # Check if application is still running
    if ! jdbg -f json status | jq -e '.data.state == "RUNNING" or .data.state == "SUSPENDED"' &>/dev/null; then
        echo "Application terminated"
        break
    fi
done

echo "Collected $SNAPSHOT snapshots in $OUTPUT_DIR"
jdbg session detach
```

## Conditional Data Collection

Only collect data when conditions are met:

```bash
#!/bin/bash
# Collect data only when error conditions occur

jdbg server start
jdbg session attach --host localhost --port 8000

# Catch all exceptions
jdbg exception catch --class java.lang.Exception --uncaught

while true; do
    EVENT=$(jdbg -f json events wait -t exception --timeout 10000 2>/dev/null)
    
    if echo "$EVENT" | jq -e '.data.events[0].exception' &>/dev/null; then
        EXCEPTION=$(echo "$EVENT" | jq -r '.data.events[0].exception.exception_class')
        echo "Exception caught: $EXCEPTION"
        
        # Collect diagnostic data
        TIMESTAMP=$(date +%Y%m%d-%H%M%S)
        jdbg -f json status > "exception-$TIMESTAMP-status.json"
        jdbg -f json frame list > "exception-$TIMESTAMP-stack.json"
        jdbg -f json var list > "exception-$TIMESTAMP-vars.json"
        
        # Evaluate specific diagnostics
        jdbg -f json eval "e.getMessage()" > "exception-$TIMESTAMP-message.json"
        
        jdbg exec continue
    fi
done
```

## gRPC API for Custom Tools

Build custom debugging tools using the gRPC API:

```python
#!/usr/bin/env python3
"""Custom debugging tool using JDBG gRPC API"""

import grpc
import jdbg_pb2
import jdbg_pb2_grpc

def main():
    channel = grpc.insecure_channel('localhost:5005')
    stub = jdbg_pb2_grpc.DebuggerServiceStub(channel)
    
    # Attach to JVM
    response = stub.AttachSession(jdbg_pb2.AttachRequest(
        name="python-debug",
        remote=jdbg_pb2.RemoteTarget(host="localhost", port=8000)
    ))
    session_id = response.session.id
    print(f"Attached: {session_id}")
    
    # Add breakpoint
    bp = stub.AddBreakpoint(jdbg_pb2.AddBreakpointRequest(
        session_id=session_id,
        line=jdbg_pb2.LineLocation(
            class_name="com.example.MyClass",
            line_number=42
        )
    ))
    print(f"Breakpoint: {bp.breakpoint.id}")
    
    # Wait for breakpoint
    events = stub.WaitForEvent(jdbg_pb2.WaitForEventRequest(
        session_id=session_id,
        timeout_ms=30000,
        event_types=["breakpoint"]
    ))
    
    if events.events:
        print("Breakpoint hit!")
        
        # Get variables
        vars = stub.ListVariables(jdbg_pb2.VariableListRequest(
            session_id=session_id,
            thread_id=events.events[0].breakpoint_hit.location.thread_id,
            frame_index=0
        ))
        
        for var in vars.variables:
            print(f"  {var.name}: {var.type} = {var.value}")
    
    # Cleanup
    stub.DetachSession(jdbg_pb2.SessionIdRequest(session_id=session_id))

if __name__ == "__main__":
    main()
```

## Tips for Automation

1. **Always use `-f json`** - Structured output is easier to parse
2. **Set timeouts** - Prevent scripts from hanging indefinitely
3. **Check return codes** - `$?` or use `set -e`
4. **Handle disconnections** - Target app might crash or exit
5. **Clean up resources** - Detach sessions, stop servers
6. **Log everything** - Redirect output for debugging the debugger


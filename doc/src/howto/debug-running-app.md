# Debug a Running Application

This guide shows how to attach to a running JVM and start debugging.

## Prerequisites

Your Java application must be started with debugging enabled:

```bash
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:8000 -jar myapp.jar
```

Key options:
- `server=y` - JVM listens for debugger connections
- `suspend=n` - Application starts immediately (use `suspend=y` to wait for debugger)
- `address=*:8000` - Listen on all interfaces, port 8000

## Step 1: Start the JDBG Server

```bash
jdbg server start
```

Verify it's running:
```bash
jdbg server status
```

## Step 2: Attach to the Application

```bash
# Attach with a friendly name
jdbg session attach --host localhost --port 8000 --name myapp
```

For remote debugging:
```bash
jdbg session attach --host prod-server.example.com --port 8000 --name prod
```

## Step 3: Verify Connection

```bash
# Check session status
jdbg status
```

Output shows:
- Session state (RUNNING or SUSPENDED)
- JVM information (version, main class)
- Thread counts
- Active breakpoints

## Step 4: Set Breakpoints

Add a breakpoint where you want execution to pause:

```bash
# Line breakpoint
jdbg bp add --class com.example.UserService --line 42

# Method breakpoint (breaks at method entry)
jdbg bp add --class com.example.UserService --method processUser
```

List your breakpoints:
```bash
jdbg bp list
```

## Step 5: Trigger the Breakpoint

Interact with your application to hit the breakpoint (e.g., make an HTTP request).

Wait for the breakpoint:
```bash
jdbg events wait -t breakpoint --timeout 60000
```

Or poll for events:
```bash
jdbg events poll
```

## Step 6: Inspect State

Once stopped at a breakpoint:

```bash
# See where you are
jdbg status

# View the call stack
jdbg frame list

# List local variables
jdbg var list

# Get a specific variable's value
jdbg var get --name userId

# Evaluate an expression
jdbg eval "user.getName()"
```

## Step 7: Continue Debugging

```bash
# Step over (next line)
jdbg exec step --mode over

# Step into (enter method call)
jdbg exec step --mode into

# Step out (finish current method)
jdbg exec step --mode out

# Continue execution
jdbg exec continue
```

## Step 8: Cleanup

```bash
# Remove breakpoints
jdbg bp clear

# Detach from the application (app continues running)
jdbg session detach

# Stop the JDBG server when done
jdbg server stop
```

## Complete Example Script

```bash
#!/bin/bash
set -e

# Start server
jdbg server start

# Attach to application
jdbg session attach --host localhost --port 8000 --name myapp

# Set breakpoint
jdbg bp add --class com.example.OrderService --line 55

echo "Breakpoint set. Waiting for hit..."

# Wait for breakpoint (timeout 2 minutes)
jdbg events wait -t breakpoint --timeout 120000

# Show where we stopped
echo "=== Stopped at ==="
jdbg status

# Show call stack
echo "=== Call Stack ==="
jdbg frame list

# Show variables
echo "=== Variables ==="
jdbg var list

# Continue and cleanup
jdbg exec continue
jdbg session detach
```

## Troubleshooting

### Connection refused

- Verify the target JVM is running with debug options
- Check firewall rules allow the debug port
- Ensure no other debugger is already attached

### Breakpoint not hit

- Verify the class name is fully qualified (e.g., `com.example.MyClass`)
- Check the line number is a valid executable line
- Ensure the code path is actually executed

### Session disconnected

- The target JVM may have terminated
- Network issues between debugger and target
- Use `jdbg session list` to check session status


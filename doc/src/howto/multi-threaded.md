# Debug Multi-threaded Code

This guide covers debugging concurrent Java applications where multiple threads interact.

## Understanding Thread States

List all threads and their states:

```bash
jdbg thread list
```

Common thread states:
- **RUNNING** - Actively executing
- **SLEEPING** - In `Thread.sleep()` or timed wait
- **MONITOR** - Waiting to acquire a lock
- **WAIT** - In `Object.wait()` or waiting on a condition

## Scenario: Race Condition

You suspect a race condition in a counter increment:

```java
public void increment() {
    int current = counter;  // Read
    counter = current + 1;  // Write
}
```

## Step 1: Set Breakpoint in Shared Code

```bash
jdbg bp add --class com.example.Counter --line 15
```

## Step 2: Wait for Multiple Threads

When the breakpoint hits, check which threads are suspended:

```bash
jdbg status
```

Output might show:
```
Suspended threads: 3
  - Thread-1 (at breakpoint) at com.example.Counter:15
  - Thread-2 (at breakpoint) at com.example.Counter:15
  - Thread-3 (at breakpoint) at com.example.Counter:15
```

## Step 3: Inspect Each Thread

```bash
# Select Thread-1
jdbg thread select --id 1

# Check its variables
jdbg var list
jdbg eval "current"  # Shows: 5

# Select Thread-2
jdbg thread select --id 2

jdbg var list
jdbg eval "current"  # Also shows: 5 - Race condition!
```

Both threads read the same value before either wrote.

## Step 4: Control Individual Threads

Resume threads one at a time to observe the race:

```bash
# Resume only Thread-1
jdbg exec resume --thread 1

# Thread-1 writes counter = 6

# Now resume Thread-2
jdbg exec resume --thread 2

# Thread-2 also writes counter = 6 (should be 7!)
```

## Suspend All vs. Selected Threads

```bash
# Suspend all threads
jdbg exec suspend

# Suspend a specific thread
jdbg thread suspend --id 5

# Resume a specific thread while others stay suspended
jdbg thread resume --id 5
```

## Debugging Deadlocks

### Detect a Deadlock

When your application hangs:

```bash
jdbg thread list
```

Look for threads in MONITOR state waiting for each other:

```
Thread-1: MONITOR (waiting for lock@0x123)
Thread-2: MONITOR (waiting for lock@0x456)
```

### Inspect Lock Holders

```bash
# Select the blocked thread
jdbg thread select --id 1

# Show the call stack
jdbg frame list

# Check what lock it's waiting for
jdbg eval "Thread.currentThread().getState()"
```

### Find the Lock Holder

```bash
# Check each thread's stack for who holds the lock
jdbg thread select --id 2
jdbg frame list
```

## Thread-Specific Breakpoints

Break only when a specific thread hits the line:

```bash
# Add breakpoint
jdbg bp add --class com.example.Worker --line 30 --condition "Thread.currentThread().getName().equals(\"worker-1\")"
```

## Debugging Thread Pools

### List Worker Threads

```bash
jdbg thread list | grep pool
```

### Break on Thread Pool Task

```bash
# Break at task execution
jdbg bp add --class java.util.concurrent.ThreadPoolExecutor --method runWorker
```

## Synchronization Issues

### Check Monitor State

When stopped in synchronized code:

```bash
# Evaluate lock state
jdbg eval "Thread.holdsLock(this)"
jdbg eval "Thread.holdsLock(sharedResource)"
```

### Step Through Synchronized Blocks

```bash
# Step into synchronized method
jdbg exec step --mode into

# Check if lock was acquired
jdbg thread list  # Other threads should be MONITOR if waiting
```

## Complete Multi-thread Debug Script

```bash
#!/bin/bash
# Multi-threaded debugging helper

jdbg server start
jdbg session attach --host localhost --port 8000 --name debug

# Set breakpoint in shared code
jdbg bp add --class com.example.SharedResource --method update

echo "Waiting for threads to hit breakpoint..."

while true; do
    jdbg events wait -t breakpoint --timeout 5000 2>/dev/null
    
    echo "=== Thread Status ==="
    jdbg thread list
    
    echo ""
    echo "=== Suspended Threads ==="
    jdbg status
    
    # Check each suspended thread
    for tid in $(jdbg -f json thread list | jq -r '.data[] | select(.suspended==true) | .id'); do
        echo ""
        echo "--- Thread $tid ---"
        jdbg thread select --id $tid
        jdbg frame list --count 3
        jdbg var list
    done
    
    read -p "Resume all (a), Resume one (thread id), Step (s), Quit (q): " action
    case $action in
        a) jdbg exec continue ;;
        s) jdbg exec step --mode over ;;
        q) break ;;
        *) jdbg thread resume --id $action ;;
    esac
done

jdbg session detach
```

## Tips for Multi-threaded Debugging

1. **Suspend all first** - Get a consistent snapshot before inspecting
2. **Check all suspended threads** - The bug might be in a different thread
3. **Use thread names** - Name your threads for easier identification
4. **Watch for state changes** - Variables might change between steps due to other threads
5. **Consider timing** - Bugs may not reproduce with debugger attached (Heisenbug)
6. **Use logging too** - Some race conditions are easier to find with logs


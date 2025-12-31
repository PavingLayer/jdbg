# How-to Guides

This section provides practical, goal-oriented guides for common debugging tasks. Unlike the Commands reference which documents individual commands, these guides show complete workflows to accomplish specific debugging goals.

## Guides

| Guide | Description |
|-------|-------------|
| [Debug a Running Application](./debug-running-app.md) | Attach to a JVM and start debugging |
| [Find the Cause of a Bug](./find-bug.md) | Set breakpoints, inspect state, trace execution |
| [Debug Multi-threaded Code](./multi-threaded.md) | Handle concurrent execution, thread-specific breakpoints |
| [Automate Debugging Tasks](./automation.md) | Script debugging workflows, CI/CD integration |

## Quick Reference

### Basic Workflow

```bash
# 1. Start the server
jdbg server start

# 2. Attach to your application
jdbg session attach --host localhost --port 8000 --name myapp

# 3. Set a breakpoint
jdbg bp add --class com.example.MyClass --line 42

# 4. Wait for breakpoint hit
jdbg events wait -t breakpoint --timeout 60000

# 5. Inspect the state
jdbg status
jdbg var list
jdbg frame list

# 6. Continue or step
jdbg exec step --mode over
jdbg exec continue
```


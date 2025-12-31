# JDBG - Scriptable Java Debugger

JDBG is a **non-interactive, scriptable Java debugger CLI** designed for automation, CI/CD pipelines, and IDE integrations. Unlike `jdb`, which requires a REPL-based interaction, JDBG exposes each debugger operation as a standalone CLI command.

## Key Features

- **One command = one action** - No interactive sessions required
- **Machine-readable output** - JSON output for scripting and automation
- **Persistent sessions** - Server maintains JDI connections across CLI invocations
- **Shell completions** - Tab completion for classes, methods, threads, variables
- **Fast CLI** - Rust-based CLI with instant startup

## Architecture

<div align="center">

```mermaid
flowchart LR
    subgraph CLI["CLI"]
        direction TB
        C1["⚡ Fast startup"]
        C2["🔤 Tab completion"]
        C3["📄 JSON / Text"]
        C1 ~~~ C2 ~~~ C3
    end

    subgraph Server["Server"]
        direction TB
        S1["🔗 Persistent connections"]
        S2["📡 Event streaming"]
        S1 ~~~ S2
    end

    subgraph Target["Target JVM"]
        T1["🎯 Debug port"]
    end

    CLI -->|"gRPC"| Server
    Server -->|"JDI"| Target
```

</div>

## Quick Example

```bash
# Start the daemon server
jdbg server start

# Attach to a remote JVM
jdbg session attach --host localhost --port 8000

# Add a breakpoint
jdbg bp add --class com.example.MyClass --line 42

# List threads
jdbg thread list

# Get JSON output for scripting
jdbg -f json bp list | jq '.data[].id'

# Stop the server
jdbg server stop
```

## What JDBG Is Not

- **A GUI debugger** - JDBG is designed for terminal-based workflows, scripting, and automation. If you need visual debugging with breakpoint markers in source code, use an IDE.
- **An interactive REPL** - Each command is standalone. For step-by-step interactive debugging, use `jdb`.


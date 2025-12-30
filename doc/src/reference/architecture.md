# Architecture

JDBG uses a client-server architecture to provide persistent debugging sessions.

## Overview

```mermaid
flowchart TB
    subgraph System["JDBG System"]
        subgraph CLI["Rust CLI (jdbg)"]
            CLI_Fast["Fast startup"]
            CLI_Tab["Tab completion"]
            CLI_JSON["JSON output"]
        end

        subgraph Server["Java Server (jdbg-server.jar)"]
            SM["SessionManager"]
            DS["DebugSession(s)"]
            EP["EventProcessor"]
        end

        subgraph Targets["Target JVMs"]
            JVM1[":8000"]
            JVM2[":8001"]
            JVM3["..."]
        end
    end

    CLI <-->|"gRPC<br/>port 5005"| Server
    Server -->|"JDI"| JVM1
    Server -->|"JDI"| JVM2
    Server -->|"JDI"| JVM3
```

## Components

### Rust CLI (`cli/`)

The command-line interface is written in Rust for:
- **Instant startup** - No JVM warmup required
- **Shell completions** - Native support via `clap`
- **Small binary** - ~3MB statically linked

The CLI communicates with the server via gRPC and can produce both human-readable text and machine-readable JSON output.

### Java Server (`server/`)

The server component is written in Java because:
- **JDI is Java-only** - The Java Debug Interface is part of the JDK
- **Persistent connections** - Maintains JDI connections across CLI invocations
- **Event processing** - Handles breakpoint hits, exceptions, etc.

### Protocol Buffers (`proto/`)

The gRPC interface is defined in `proto/jdbg.proto` and includes:
- `DebuggerService` - Main debugging operations
- `CompletionService` - Tab completion support

## Data Flow

### Command Execution

```mermaid
sequenceDiagram
    participant U as User
    participant C as CLI
    participant S as Server
    participant J as Target JVM

    U->>C: jdbg thread list
    C->>S: gRPC: ListThreads
    S->>J: JDI: allThreads()
    J-->>S: ThreadReference[]
    S-->>C: ThreadListResponse
    C-->>U: Thread list output
```

### Event Streaming

```mermaid
sequenceDiagram
    participant J as Target JVM
    participant S as Server
    participant C as CLI
    participant U as User

    J->>S: JDI: BreakpointEvent
    S->>C: gRPC Stream: DebugEvent
    C->>U: "Breakpoint hit at..."
    
    J->>S: JDI: ExceptionEvent
    S->>C: gRPC Stream: DebugEvent
    C->>U: "Exception thrown..."
```

## Session Management

```mermaid
flowchart TB
    subgraph Server
        subgraph SM["SessionManager"]
            Active["Active Session: Session 1"]
            
            subgraph S1["DebugSession 1"]
                VM1["VirtualMachine"]
                BP1["Breakpoints"]
                EP1["EventProcessor"]
            end
            
            subgraph S2["DebugSession 2"]
                VM2["VirtualMachine"]
                BP2["Breakpoints"]
                EP2["EventProcessor"]
            end
        end
    end
    
    S1 -->|"JDI"| JVM1[("JVM :8000")]
    S2 -->|"JDI"| JVM2[("JVM :8001")]
```

## Why This Architecture?

### Problem with Direct JDI Access

JDI requires a persistent connection to the target JVM. If each CLI command created a new connection:
- Suspend state would be lost between commands
- Breakpoints would need to be re-registered
- Performance would suffer from connection overhead

### Solution: Daemon Server

By using a daemon server:
- **Persistent connections** - JDI state is maintained
- **Multiple sessions** - Debug multiple JVMs simultaneously
- **Event handling** - Background processing of JDI events
- **Fast CLI** - No JVM startup per command


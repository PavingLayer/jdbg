# gRPC API Reference

JDBG exposes two gRPC services defined in [`proto/jdbg.proto`](https://github.com/PavingLayer/jdbg/blob/main/proto/jdbg.proto).

> **Note:** Message definitions below are derived from the proto file. For the authoritative schema, see the proto file directly.

## Connection

| Setting | Value |
|---------|-------|
| Default address | `tcp://127.0.0.1:5005` |
| Environment variable | `JDBG_SERVER` |
| Protocol | gRPC (HTTP/2) |

---

## DebuggerService

Main debugging operations organized by category.

### Session Management

| Method | Request → Response | Description |
|--------|-------------------|-------------|
| `AttachSession` | [AttachRequest](#attachrequest) → [Session](#session) | Attach to a running JVM via socket or PID |
| `LaunchSession` | LaunchRequest → [Session](#session) | Launch a new JVM process |
| `DetachSession` | SessionIdRequest → [StatusResponse](#statusresponse) | Disconnect from a session |
| `GetSessionStatus` | SessionIdRequest → [Session](#session) | Get session details |
| `ListSessions` | Empty → [Session](#session)[] | List all active sessions |
| `SetActiveSession` | SessionIdRequest → [StatusResponse](#statusresponse) | Set the default session |
| `RenameSession` | RenameSessionRequest → [StatusResponse](#statusresponse) | Rename a session |
| `GetStatus` | SessionIdRequest → StatusOverviewResponse | Get JVM state overview |

### Breakpoints

| Method | Request → Response | Description |
|--------|-------------------|-------------|
| `AddBreakpoint` | [AddBreakpointRequest](#addbreakpointrequest) → [Breakpoint](#breakpoint) | Add a line or method breakpoint |
| `RemoveBreakpoint` | BreakpointIdRequest → [StatusResponse](#statusresponse) | Remove a breakpoint by ID |
| `EnableBreakpoint` | BreakpointIdRequest → [Breakpoint](#breakpoint) | Enable a disabled breakpoint |
| `DisableBreakpoint` | BreakpointIdRequest → [Breakpoint](#breakpoint) | Disable without removing |
| `ListBreakpoints` | SessionIdRequest → [Breakpoint](#breakpoint)[] | List all breakpoints |
| `ClearBreakpoints` | SessionIdRequest → [StatusResponse](#statusresponse) | Remove all breakpoints |

### Exception Breakpoints

| Method | Request → Response | Description |
|--------|-------------------|-------------|
| `CatchException` | CatchExceptionRequest → ExceptionBreakpoint | Break when exception is thrown |
| `IgnoreException` | ExceptionBreakpointIdRequest → [StatusResponse](#statusresponse) | Remove exception breakpoint |
| `ListExceptionBreakpoints` | SessionIdRequest → ExceptionBreakpoint[] | List exception breakpoints |

### Execution Control

| Method | Request → Response | Description |
|--------|-------------------|-------------|
| `Continue` | SessionIdRequest → [StatusResponse](#statusresponse) | Resume all threads |
| `Suspend` | SuspendRequest → [StatusResponse](#statusresponse) | Suspend thread(s) |
| `Resume` | ResumeRequest → [StatusResponse](#statusresponse) | Resume specific thread(s) |
| `Step` | StepRequest → StepResponse | Step into/over/out |
| `RunTo` | RunToRequest → [StatusResponse](#statusresponse) | Run to a specific line |

### Threads

| Method | Request → Response | Description |
|--------|-------------------|-------------|
| `ListThreads` | SessionIdRequest → [ThreadInfo](#threadinfo)[] | List all threads |
| `GetThreadInfo` | ThreadRequest → [ThreadInfo](#threadinfo) | Get thread details |
| `SelectThread` | SelectThreadRequest → [StatusResponse](#statusresponse) | Set current thread |
| `SuspendThread` | ThreadRequest → [StatusResponse](#statusresponse) | Suspend a specific thread |
| `ResumeThread` | ThreadRequest → [StatusResponse](#statusresponse) | Resume a specific thread |

### Stack Frames

| Method | Request → Response | Description |
|--------|-------------------|-------------|
| `ListFrames` | FrameListRequest → [FrameInfo](#frameinfo)[] | List stack frames for a thread |
| `SelectFrame` | SelectFrameRequest → [StatusResponse](#statusresponse) | Set current frame |
| `GetFrameInfo` | FrameRequest → [FrameInfo](#frameinfo) | Get frame details |

### Variables

| Method | Request → Response | Description |
|--------|-------------------|-------------|
| `ListVariables` | VariableListRequest → [Variable](#variable)[] | List variables in scope |
| `GetVariable` | GetVariableRequest → [Variable](#variable) | Get variable with expansion |
| `SetVariable` | SetVariableRequest → [StatusResponse](#statusresponse) | Modify a variable's value |

### Evaluation

| Method | Request → Response | Description |
|--------|-------------------|-------------|
| `Evaluate` | EvaluateRequest → EvaluateResponse | Evaluate an expression |

### Events

| Method | Request → Response | Description |
|--------|-------------------|-------------|
| `PollEvents` | PollEventsRequest → [DebugEvent](#debugevent)[] | Get buffered events (non-blocking) |
| `WaitForEvent` | WaitForEventRequest → [DebugEvent](#debugevent)[] | Wait for next event with timeout |
| `ClearEvents` | SessionIdRequest → [StatusResponse](#statusresponse) | Clear event buffer |
| `GetEventInfo` | SessionIdRequest → EventInfoResponse | Get event buffer statistics |
| `SubscribeEvents` | SessionIdRequest → stream [DebugEvent](#debugevent) | Stream events (deprecated) |

---

## CompletionService

Tab completion support for shell integration.

| Method | Request → Response | Description |
|--------|-------------------|-------------|
| `CompleteSessions` | Empty → CompletionResponse | Complete session IDs/names |
| `CompleteClasses` | ClassCompletionRequest → CompletionResponse | Complete class names by prefix |
| `CompleteMethods` | MethodCompletionRequest → CompletionResponse | Complete method names |
| `CompleteThreads` | SessionIdRequest → CompletionResponse | Complete thread IDs/names |
| `CompleteVariables` | VariableCompletionRequest → CompletionResponse | Complete variable names |
| `CompleteBreakpoints` | SessionIdRequest → CompletionResponse | Complete breakpoint IDs |
| `CompleteFields` | FieldCompletionRequest → CompletionResponse | Complete field names |

---

## Message Structures

### Common

#### StatusResponse

Standard response for operations without specific return data.

| Field | Type | Description |
|-------|------|-------------|
| `success` | bool | Whether operation succeeded |
| `message` | string | Human-readable message |
| `error` | [JdbgError](#jdbgerror) | Error details (if failed) |

#### JdbgError

| Field | Type | Description |
|-------|------|-------------|
| `code` | string | Error code (e.g., `SESSION_NOT_FOUND`) |
| `message` | string | Error message |
| `detail` | string | Additional context |

### Sessions

#### AttachRequest

| Field | Type | Description |
|-------|------|-------------|
| `session_id` | string | Optional custom ID (auto-generated if empty) |
| `name` | string | Human-readable name |
| `remote` | [RemoteTarget](#remotetarget) | Connect via socket (oneof target) |
| `local` | LocalTarget | Connect via PID (oneof target) |

#### RemoteTarget

| Field | Type | Description |
|-------|------|-------------|
| `host` | string | Hostname or IP |
| `port` | int32 | Debug port |
| `timeout_ms` | int32 | Connection timeout |

#### Session

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Unique session ID |
| `name` | string | Human-readable name |
| `type` | SessionType | `ATTACHED_REMOTE`, `ATTACHED_LOCAL`, `LAUNCHED` |
| `state` | SessionState | `CONNECTED`, `SUSPENDED`, `RUNNING`, etc. |
| `host` | string | Remote host |
| `port` | int32 | Debug port |
| `pid` | int32 | Process ID (if local) |
| `main_class` | string | Main class name |
| `vm_name` | string | JVM name |
| `vm_version` | string | JVM version |
| `selected_thread_id` | int64 | Currently selected thread |
| `selected_frame_index` | int32 | Currently selected frame |

### Breakpoints

#### AddBreakpointRequest

| Field | Type | Description |
|-------|------|-------------|
| `session_id` | string | Target session (empty = active) |
| `line` | [LineLocation](#linelocation) | Line breakpoint (oneof location) |
| `method` | MethodLocation | Method breakpoint (oneof location) |
| `condition` | string | Conditional expression |
| `enabled` | bool | Initially enabled |

#### LineLocation

| Field | Type | Description |
|-------|------|-------------|
| `class_name` | string | Fully qualified class name |
| `line_number` | int32 | Line number |

#### Breakpoint

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Breakpoint ID |
| `session_id` | string | Owning session |
| `type` | BreakpointType | `LINE`, `METHOD`, `EXCEPTION` |
| `class_name` | string | Class name |
| `line_number` | int32 | Line (for line breakpoints) |
| `method_name` | string | Method (for method breakpoints) |
| `condition` | string | Conditional expression |
| `enabled` | bool | Whether enabled |
| `hit_count` | int32 | Times hit |
| `location_string` | string | Human-readable location |

### Threads

#### ThreadInfo

| Field | Type | Description |
|-------|------|-------------|
| `id` | int64 | Thread ID |
| `name` | string | Thread name |
| `status` | ThreadStatus | `RUNNING`, `SLEEPING`, `MONITOR`, `WAIT` |
| `suspended` | bool | Whether suspended |
| `at_breakpoint` | bool | Stopped at breakpoint |
| `frame_count` | int32 | Stack depth |
| `thread_group` | string | Thread group name |

### Stack Frames

#### FrameInfo

| Field | Type | Description |
|-------|------|-------------|
| `index` | int32 | Frame index (0 = top) |
| `class_name` | string | Class name |
| `method_name` | string | Method name |
| `source_name` | string | Source file name |
| `line_number` | int32 | Current line |
| `is_native` | bool | Native method |
| `location_string` | string | Human-readable location |

### Variables

#### Variable

| Field | Type | Description |
|-------|------|-------------|
| `name` | string | Variable name |
| `type` | string | Type name |
| `value` | string | String representation |
| `kind` | VariableKind | `LOCAL`, `ARGUMENT`, `FIELD`, `STATIC`, `THIS` |
| `is_expandable` | bool | Has child fields |
| `children` | [Variable](#variable)[] | Expanded child variables |

### Events

#### DebugEvent

| Field | Type | Description |
|-------|------|-------------|
| `timestamp` | int64 | Event timestamp (ms) |
| `session_id` | string | Session ID |
| `sequence_number` | int64 | Monotonic sequence |
| `event_type` | string | `breakpoint`, `step`, `exception`, `thread_start`, `thread_death`, `vm_death` |

Event-specific payload (oneof):

| Event | Fields |
|-------|--------|
| `breakpoint_hit` | `breakpoint_id`, [ThreadLocation](#threadlocation) |
| `step_completed` | [ThreadLocation](#threadlocation) |
| `exception` | `exception_class`, `message`, `caught`, [ThreadLocation](#threadlocation) |
| `thread_start` | [ThreadInfo](#threadinfo) |
| `thread_death` | `thread_id`, `thread_name` |
| `vm_death` | `exit_code` |
| `vm_disconnect` | `reason` |

#### ThreadLocation

| Field | Type | Description |
|-------|------|-------------|
| `thread_id` | int64 | Thread ID |
| `thread_name` | string | Thread name |
| `class_name` | string | Class name |
| `method_name` | string | Method name |
| `source_name` | string | Source file |
| `line_number` | int32 | Line number |

---

## API Walkthroughs

### Attach to a Remote JVM

Uses [AttachRequest](#attachrequest) → [Session](#session)

**Request:**
```json
{
  "name": "my-app",
  "remote": {
    "host": "localhost",
    "port": 8000,
    "timeout_ms": 5000
  }
}
```

**Response:**
```json
{
  "session": {
    "id": "sess-a1b2c3",
    "name": "my-app",
    "type": "SESSION_TYPE_ATTACHED_REMOTE",
    "state": "SESSION_STATE_RUNNING",
    "host": "localhost",
    "port": 8000,
    "vm_name": "OpenJDK 64-Bit Server VM",
    "vm_version": "21.0.1"
  }
}
```

### Add a Line Breakpoint

Uses [AddBreakpointRequest](#addbreakpointrequest) → [Breakpoint](#breakpoint)

**Request:**
```json
{
  "session_id": "sess-a1b2c3",
  "line": {
    "class_name": "com.example.UserService",
    "line_number": 42
  },
  "enabled": true
}
```

**Response:**
```json
{
  "breakpoint": {
    "id": "bp-1",
    "session_id": "sess-a1b2c3",
    "type": "BREAKPOINT_TYPE_LINE",
    "class_name": "com.example.UserService",
    "line_number": 42,
    "enabled": true,
    "hit_count": 0,
    "location_string": "com.example.UserService:42"
  }
}
```

### Wait for Breakpoint Hit

Uses WaitForEventRequest → [DebugEvent](#debugevent)[]

**Request:**
```json
{
  "session_id": "sess-a1b2c3",
  "timeout_ms": 30000,
  "event_types": ["breakpoint"]
}
```

**Response:**
```json
{
  "events": [
    {
      "timestamp": 1703952000000,
      "session_id": "sess-a1b2c3",
      "sequence_number": 1,
      "event_type": "breakpoint",
      "breakpoint_hit": {
        "breakpoint_id": "bp-1",
        "location": {
          "thread_id": 1,
          "thread_name": "main",
          "class_name": "com.example.UserService",
          "method_name": "getUser",
          "source_name": "UserService.java",
          "line_number": 42
        }
      }
    }
  ],
  "remaining_count": 0
}
```

### Inspect Variables

Uses VariableListRequest → [Variable](#variable)[]

**Request:**
```json
{
  "session_id": "sess-a1b2c3",
  "thread_id": 1,
  "frame_index": 0
}
```

**Response:**
```json
{
  "variables": [
    {
      "name": "this",
      "type": "com.example.UserService",
      "value": "UserService@7a81197d",
      "kind": "VARIABLE_KIND_THIS",
      "is_expandable": true
    },
    {
      "name": "userId",
      "type": "long",
      "value": "12345",
      "kind": "VARIABLE_KIND_ARGUMENT",
      "is_expandable": false
    },
    {
      "name": "user",
      "type": "com.example.User",
      "value": "null",
      "kind": "VARIABLE_KIND_LOCAL",
      "is_expandable": false
    }
  ]
}
```

### Evaluate Expression

Uses EvaluateRequest → EvaluateResponse

**Request:**
```json
{
  "session_id": "sess-a1b2c3",
  "thread_id": 1,
  "frame_index": 0,
  "expression": "userId * 2 + userRepository.count()"
}
```

**Response:**
```json
{
  "result": "24695",
  "type": "long"
}
```

### Step Over and Check Location

Uses StepRequest → StepResponse

**Request:**
```json
{
  "session_id": "sess-a1b2c3",
  "thread_id": 1,
  "depth": "STEP_DEPTH_OVER",
  "size": "STEP_SIZE_LINE"
}
```

**Response:**
```json
{
  "success": true,
  "stop_reason": {
    "type": "STOP_REASON_STEP"
  },
  "location": {
    "thread_id": 1,
    "thread_name": "main",
    "class_name": "com.example.UserService",
    "method_name": "getUser",
    "source_name": "UserService.java",
    "line_number": 43
  }
}
```

---

## Using grpcurl

```bash
# List sessions
grpcurl -plaintext localhost:5005 jdbg.DebuggerService/ListSessions

# Attach to JVM
grpcurl -plaintext -d '{
  "name": "my-app",
  "remote": {"host": "localhost", "port": 8000}
}' localhost:5005 jdbg.DebuggerService/AttachSession

# Add breakpoint
grpcurl -plaintext -d '{
  "line": {"class_name": "com.example.Main", "line_number": 42}
}' localhost:5005 jdbg.DebuggerService/AddBreakpoint

# Wait for breakpoint
grpcurl -plaintext -d '{
  "timeout_ms": 30000,
  "event_types": ["breakpoint"]
}' localhost:5005 jdbg.DebuggerService/WaitForEvent

# List variables
grpcurl -plaintext -d '{
  "thread_id": 1,
  "frame_index": 0
}' localhost:5005 jdbg.DebuggerService/ListVariables
```

---

## Generating Clients

The proto file can be used to generate clients in any gRPC-supported language:

```bash
# Python
python -m grpc_tools.protoc -I proto --python_out=. --grpc_python_out=. proto/jdbg.proto

# Go
protoc -I proto --go_out=. --go-grpc_out=. proto/jdbg.proto

# TypeScript (grpc-web)
protoc -I proto --js_out=import_style=commonjs:. --grpc-web_out=import_style=typescript,mode=grpcwebtext:. proto/jdbg.proto
```

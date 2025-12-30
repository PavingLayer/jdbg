# JDBG Integration Test Plan

## Overview

This document defines a comprehensive integration test plan for the JDBG (Scriptable Java Debugger) tool. The focus is on testing the **server component** which handles JDI connections and debugging operations.

## Testing Strategy

### Testing Approach: Black-Box Integration Testing via CLI

Since the server is a gRPC service that communicates with a Rust CLI, the most effective testing approach is:

1. **Black-Box Integration Tests**: Test through the CLI interface, verifying end-to-end functionality
2. **Reproducible Test Target**: Use a dedicated Java application with known behavior
3. **Automated Test Scripts**: Shell scripts that execute CLI commands and verify outputs
4. **JSON Output Parsing**: Use `-f json` output for machine-readable verification

### Test Environment Requirements

- JDBG server JAR built and accessible
- JDBG CLI compiled and in PATH
- Java 17+ with debug support
- Test target application compiled with debug symbols (`-g` flag)

---

## Test Categories

### 1. Server Lifecycle Tests

| Test ID | Test Case | Expected Behavior | Priority |
|---------|-----------|-------------------|----------|
| SRV-001 | Start server when not running | Server starts, PID file created | Critical |
| SRV-002 | Start server when already running | Error message, existing server unchanged | High |
| SRV-003 | Stop running server | Server terminates, PID file removed | Critical |
| SRV-004 | Stop when no server running | Graceful error message | Medium |
| SRV-005 | Server status when running | Returns running status with port | High |
| SRV-006 | Server status when not running | Returns not running status | Medium |
| SRV-007 | Server handles multiple sessions | Multiple concurrent sessions work | High |

---

### 2. Session Management Tests

| Test ID | Test Case | Expected Behavior | Priority |
|---------|-----------|-------------------|----------|
| SES-001 | Attach to running JVM via socket | Session created, connected state | Critical |
| SES-002 | Attach with custom session ID | Session uses provided ID | Medium |
| SES-003 | Attach with invalid host/port | Clear error message | High |
| SES-004 | Attach with timeout | Connection times out gracefully | Medium |
| SES-005 | List sessions (empty) | Empty list returned | High |
| SES-006 | List sessions (multiple) | All sessions listed | High |
| SES-007 | Session status | Returns session details (VM info, state) | High |
| SES-008 | Select session | Active session changes | High |
| SES-009 | Detach session | Session removed, JVM continues | Critical |
| SES-010 | Detach non-existent session | Error message | Medium |
| SES-011 | Session persists across CLI calls | Same session accessible | Critical |
| SES-012 | Auto-select first session | First attached session is active | Medium |

---

### 3. Breakpoint Tests

| Test ID | Test Case | Expected Behavior | Priority |
|---------|-----------|-------------------|----------|
| BP-001 | Add line breakpoint (existing class) | Breakpoint created immediately | Critical |
| BP-002 | Add line breakpoint (not yet loaded class) | Deferred breakpoint works | High |
| BP-003 | Add method breakpoint | Breakpoint on method entry | High |
| BP-004 | Add breakpoint with invalid class | Error or deferred | Medium |
| BP-005 | Add breakpoint with invalid line | Error message | Medium |
| BP-006 | List breakpoints (empty) | Empty list | Medium |
| BP-007 | List breakpoints (multiple) | All breakpoints shown | High |
| BP-008 | Remove breakpoint by ID | Breakpoint removed | High |
| BP-009 | Remove non-existent breakpoint | Graceful handling | Medium |
| BP-010 | Enable/disable breakpoint | State changes, hits respected | High |
| BP-011 | Clear all breakpoints | All removed | Medium |
| BP-012 | Breakpoint hit count tracking | Counter increments on each hit | Medium |
| BP-013 | Conditional breakpoint | Only stops when condition true | Low |
| BP-014 | Multiple breakpoints same class | All work independently | High |

---

### 4. Exception Breakpoint Tests

| Test ID | Test Case | Expected Behavior | Priority |
|---------|-----------|-------------------|----------|
| EXC-001 | Catch all exceptions | Stops on any exception | High |
| EXC-002 | Catch specific exception type | Stops only on that type | High |
| EXC-003 | Catch caught exceptions only | Stops on caught, not uncaught | High |
| EXC-004 | Catch uncaught exceptions only | Stops on uncaught only | High |
| EXC-005 | Ignore exception | Removes exception breakpoint | High |
| EXC-006 | List exception breakpoints | Shows all exception BPs | Medium |
| EXC-007 | Exception with subclass matching | Subclasses also caught | Medium |

---

### 5. Execution Control Tests

| Test ID | Test Case | Expected Behavior | Priority |
|---------|-----------|-------------------|----------|
| EXEC-001 | Suspend all threads | VM paused, state=SUSPENDED | Critical |
| EXEC-002 | Resume all threads | VM running, state=RUNNING | Critical |
| EXEC-003 | Continue from breakpoint | Execution continues | Critical |
| EXEC-004 | Step over (line) | Advances one line | Critical |
| EXEC-005 | Step into (method call) | Enters called method | Critical |
| EXEC-006 | Step out (of method) | Returns to caller | Critical |
| EXEC-007 | Step when not suspended | Error message | Medium |
| EXEC-008 | Continue when already running | No-op or message | Medium |
| EXEC-009 | Suspend specific thread | Only that thread paused | Medium |
| EXEC-010 | Resume specific thread | Only that thread resumed | Medium |

---

### 6. Thread Management Tests

| Test ID | Test Case | Expected Behavior | Priority |
|---------|-----------|-------------------|----------|
| THR-001 | List threads | All threads shown with status | Critical |
| THR-002 | Thread info includes name, status | Correct attributes | High |
| THR-003 | Select thread | Thread becomes active for ops | High |
| THR-004 | Suspend individual thread | Thread suspended | High |
| THR-005 | Resume individual thread | Thread resumed | High |
| THR-006 | Thread at breakpoint flag | Correctly identified | Medium |
| THR-007 | Thread frame count | Correct count when suspended | Medium |
| THR-008 | Thread group displayed | Group name shown | Low |

---

### 7. Stack Frame Tests

| Test ID | Test Case | Expected Behavior | Priority |
|---------|-----------|-------------------|----------|
| FRM-001 | List frames (suspended thread) | Stack trace shown | Critical |
| FRM-002 | List frames (running thread) | Error message | High |
| FRM-003 | Frame includes class/method/line | All info present | High |
| FRM-004 | Frame pagination | start_index/count work | Medium |
| FRM-005 | Select frame | Changes active frame | High |
| FRM-006 | Frame info for selected | Details returned | Medium |
| FRM-007 | Native method frame | Marked as native | Low |
| FRM-008 | Source name available | When compiled with -g | Medium |

---

### 8. Variable Inspection Tests

| Test ID | Test Case | Expected Behavior | Priority |
|---------|-----------|-------------------|----------|
| VAR-001 | List variables in frame | All locals shown | Critical |
| VAR-002 | 'this' reference available | Object reference shown | High |
| VAR-003 | Method arguments shown | Arguments marked as such | High |
| VAR-004 | Primitive types displayed | int, long, boolean, etc. | High |
| VAR-005 | String values displayed | Content shown in quotes | High |
| VAR-006 | Object references displayed | Type and ID shown | High |
| VAR-007 | Array values displayed | Type and length | High |
| VAR-008 | Null values handled | "null" displayed | High |
| VAR-009 | Get specific variable | Single var retrieved | High |
| VAR-010 | Get non-existent variable | Error message | Medium |
| VAR-011 | Set variable value | Value changed | Medium |
| VAR-012 | Variables without debug info | Graceful degradation | Medium |

---

### 9. Expression Evaluation Tests

| Test ID | Test Case | Expected Behavior | Priority |
|---------|-----------|-------------------|----------|
| EVAL-001 | Evaluate simple variable | Value returned | High |
| EVAL-002 | Evaluate 'this' | Object info returned | High |
| EVAL-003 | Evaluate non-existent var | Error message | Medium |
| EVAL-004 | Evaluate when not suspended | Error message | Medium |

---

### 10. Event Streaming Tests

| Test ID | Test Case | Expected Behavior | Priority |
|---------|-----------|-------------------|----------|
| EVT-001 | Breakpoint hit event | Event dispatched with location | Critical |
| EVT-002 | Step completed event | Event with new location | High |
| EVT-003 | Exception event | Exception details in event | High |
| EVT-004 | Thread start event | New thread info in event | Medium |
| EVT-005 | Thread death event | Thread ID in event | Medium |
| EVT-006 | VM disconnect event | Reason provided | High |
| EVT-007 | Multiple event subscribers | All receive events | Medium |

---

### 11. Error Handling Tests

| Test ID | Test Case | Expected Behavior | Priority |
|---------|-----------|-------------------|----------|
| ERR-001 | Command with no active session | Clear error message | High |
| ERR-002 | Invalid session ID | "Session not found" error | High |
| ERR-003 | Operation on disconnected VM | Graceful error | High |
| ERR-004 | gRPC server unreachable | Connection error message | High |
| ERR-005 | Invalid breakpoint location | Descriptive error | Medium |
| ERR-006 | Thread not found | Error with thread ID | Medium |
| ERR-007 | Frame index out of bounds | Range error | Medium |

---

### 12. Output Format Tests

| Test ID | Test Case | Expected Behavior | Priority |
|---------|-----------|-------------------|----------|
| OUT-001 | JSON output format | Valid JSON returned | Critical |
| OUT-002 | Text output format | Human-readable format | High |
| OUT-003 | Error in JSON format | error field populated | High |
| OUT-004 | Empty results in JSON | Empty array/object | Medium |

---

### 13. Completion Service Tests

| Test ID | Test Case | Expected Behavior | Priority |
|---------|-----------|-------------------|----------|
| CMP-001 | Complete sessions | Session IDs returned | Medium |
| CMP-002 | Complete classes with prefix | Matching classes | Medium |
| CMP-003 | Complete thread IDs | Thread list for completion | Medium |
| CMP-004 | Complete variable names | Variables in scope | Medium |
| CMP-005 | Complete breakpoint IDs | Existing BP IDs | Low |

---

## Test Scenarios (End-to-End Workflows)

### Scenario 1: Basic Debugging Session
```bash
# Start server → Attach → Set BP → Continue to BP → Inspect → Detach
jdbg server start
jdbg session attach --host localhost --port 8000
jdbg bp add --class com.example.Main --line 15
jdbg exec continue
# (wait for BP hit via events or polling)
jdbg thread list
jdbg frame list
jdbg var list
jdbg session detach
jdbg server stop
```

### Scenario 2: Step Debugging
```bash
# Attach → BP → Step through code
jdbg session attach --host localhost --port 8000
jdbg bp add --class com.example.Calculator --method calculate
jdbg exec continue
# (wait for BP hit)
jdbg exec step --depth over
jdbg exec step --depth into
jdbg var list
jdbg exec step --depth out
jdbg exec continue
```

### Scenario 3: Exception Debugging
```bash
# Catch exceptions → Inspect when hit
jdbg session attach --host localhost --port 8000
jdbg exception catch java.lang.NullPointerException --uncaught
jdbg exec continue
# (wait for exception)
jdbg thread list
jdbg frame list
jdbg var list
jdbg eval "this"
```

### Scenario 4: Multi-threaded Debugging
```bash
# Debug multiple threads
jdbg session attach --host localhost --port 8000
jdbg thread list
jdbg thread select --id <thread_id>
jdbg thread suspend --id <thread_id>
jdbg frame list
jdbg var list
jdbg thread resume --id <thread_id>
```

### Scenario 5: Multiple Sessions
```bash
# Manage multiple debug sessions
jdbg session attach --host localhost --port 8000 --session app1
jdbg session attach --host localhost --port 8001 --session app2
jdbg session list
jdbg session select app1
jdbg bp add --class com.example.Main --line 10
jdbg session select app2
jdbg bp add --class com.other.Service --line 20
```

---

## Test Prioritization

### Critical (Must Pass for Release)
- Server start/stop
- Session attach/detach
- Basic breakpoint operations
- Execution control (suspend/resume/continue)
- Thread listing
- Frame listing
- Variable listing
- JSON output

### High Priority
- Exception breakpoints
- Step operations
- Thread selection
- Variable inspection details
- Error handling
- Event streaming (breakpoint hit)

### Medium Priority
- Deferred breakpoints
- Breakpoint enable/disable
- Frame selection
- Specific thread suspend/resume
- Completion service

### Low Priority
- Conditional breakpoints
- Native frame handling
- Thread group display

---

## Test Data Requirements

The test target application (`test-target`) must include:

1. **Simple class with main method** - basic breakpoint testing
2. **Calculator class** - method entry breakpoints, step testing
3. **Multi-threaded class** - concurrent thread testing
4. **Exception throwing class** - exception breakpoint testing
5. **Various data types** - variable inspection testing
6. **Nested method calls** - stack frame testing
7. **Loops and conditionals** - step over/into testing
8. **Inner classes** - class name handling

---

## Automation Framework

### Recommended Approach

1. **Test Runner**: Bash script with helper functions
2. **Assertions**: Compare JSON output with expected values using `jq`
3. **Test Target**: Java app started in suspended mode
4. **Cleanup**: Always stop server and kill test target after tests

### Example Test Structure
```bash
#!/bin/bash
# test-session.sh

setup() {
    # Start test target JVM
    java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:8000 \
         -cp test-target/target/classes com.jdbg.test.Main &
    TEST_PID=$!
    sleep 2
    
    # Start JDBG server
    jdbg server start
}

teardown() {
    jdbg server stop 2>/dev/null
    kill $TEST_PID 2>/dev/null
}

test_attach_session() {
    local result=$(jdbg -f json session attach --host localhost --port 8000)
    local success=$(echo "$result" | jq -r '.session.state')
    
    if [[ "$success" == "SESSION_STATE_CONNECTED" ]]; then
        echo "PASS: test_attach_session"
        return 0
    else
        echo "FAIL: test_attach_session - got state: $success"
        return 1
    fi
}
```

---

## Metrics & Coverage

Track the following metrics:
- Number of tests passing/failing
- Commands tested / total commands
- Error paths tested
- Multi-session scenarios tested
- Event types verified


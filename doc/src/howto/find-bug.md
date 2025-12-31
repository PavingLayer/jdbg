# Find the Cause of a Bug

This guide shows how to systematically track down the source of a bug using JDBG.

## Scenario

You have a bug report: "Users with names containing special characters get 500 errors." Let's find the cause.

## Step 1: Identify the Entry Point

First, identify where the problematic code path starts. For a REST API:

```bash
jdbg session attach --host localhost --port 8000 --name debug

# Set breakpoint at the controller method
jdbg bp add --class com.example.UserController --method createUser
```

## Step 2: Reproduce the Issue

Trigger the bug (e.g., send a request with special characters):

```bash
curl -X POST http://localhost:8080/users -d '{"name": "José García"}'
```

Wait for the breakpoint:
```bash
jdbg events wait -t breakpoint --timeout 30000
```

## Step 3: Inspect Input Parameters

```bash
# List all variables in current frame
jdbg var list

# Check the request parameter
jdbg var get --name request
jdbg eval "request.getName()"
```

Output:
```
"José García"
```

The input looks correct. Let's trace deeper.

## Step 4: Step Through the Code

```bash
# Step into the service method
jdbg exec step --mode into

# Check where we are now
jdbg status

# Step over each line, checking state
jdbg exec step --mode over
jdbg var list
```

## Step 5: Use Conditional Breakpoints

If the bug only occurs with certain data, use a conditional breakpoint:

```bash
# Only break when name contains non-ASCII
jdbg bp add --class com.example.UserService --line 42 \
    --condition "name.contains(\"é\") || name.contains(\"ñ\")"
```

## Step 6: Catch the Exception

If you know an exception is thrown:

```bash
# Break when NullPointerException is thrown
jdbg exception catch --class java.lang.NullPointerException

# Or catch all exceptions
jdbg exception catch --class java.lang.Exception --uncaught
```

Wait for the exception:
```bash
jdbg events wait -t exception --timeout 60000
```

When caught, inspect the state:
```bash
jdbg status
jdbg frame list
jdbg var list
```

## Step 7: Examine the Call Stack

```bash
# Show full call stack
jdbg frame list

# Select a specific frame to inspect
jdbg frame select --index 2

# Now var list shows that frame's variables
jdbg var list
```

## Step 8: Evaluate Hypotheses

Use expression evaluation to test theories:

```bash
# Check encoding
jdbg eval "name.getBytes(\"UTF-8\").length"
jdbg eval "name.getBytes(\"ISO-8859-1\").length"

# Check for null
jdbg eval "userRepository.findByName(name) == null"

# Test a fix
jdbg eval "name.replaceAll(\"[^a-zA-Z0-9]\", \"_\")"
```

## Step 9: Binary Search with Breakpoints

If the bug is in a long method, use multiple breakpoints to narrow down:

```bash
# Add breakpoints at different points
jdbg bp add --class com.example.UserService --line 50
jdbg bp add --class com.example.UserService --line 75
jdbg bp add --class com.example.UserService --line 100

# Continue to each, checking state
jdbg exec continue
jdbg var list

# Disable breakpoints you've passed
jdbg bp disable --id bp-1
```

## Step 10: Document Findings

Use JSON output for logging:

```bash
# Capture state at the bug location
jdbg -f json status > bug-state.json
jdbg -f json var list >> bug-state.json
jdbg -f json frame list >> bug-state.json
```

## Complete Investigation Script

```bash
#!/bin/bash
# Bug investigation script

jdbg server start
jdbg session attach --host localhost --port 8000 --name debug

# Set up exception catching
jdbg exception catch --class java.lang.Exception --uncaught

# Set strategic breakpoints
jdbg bp add --class com.example.UserController --method createUser
jdbg bp add --class com.example.UserService --method saveUser
jdbg bp add --class com.example.UserRepository --method insert

echo "Ready to debug. Reproduce the bug now..."

# Wait for any event
while true; do
    EVENT=$(jdbg -f json events wait -t breakpoint,exception --timeout 5000 2>/dev/null)
    if [ -n "$EVENT" ]; then
        echo "=== Event captured ==="
        jdbg status
        jdbg var list
        
        read -p "Continue (c), Step (s), or Quit (q)? " action
        case $action in
            c) jdbg exec continue ;;
            s) jdbg exec step --mode over ;;
            q) break ;;
        esac
    fi
done

jdbg session detach
```

## Tips

1. **Start broad, then narrow** - Begin at entry points, step into suspicious code
2. **Use conditional breakpoints** - Don't break on every iteration of a loop
3. **Check boundary conditions** - null values, empty strings, edge cases
4. **Compare working vs broken** - Debug with both good and bad input
5. **Use JSON output** - Easier to parse and compare states


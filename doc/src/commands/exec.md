# Execution Control

Control execution of the target JVM.

## Commands

### `jdbg exec continue` (alias: `jdbg exec c`)

Resume execution of all threads.

```bash
jdbg exec continue [OPTIONS]
```

**Options:**
- `--session, -s <ID>` - Session ID

### `jdbg exec suspend`

Suspend execution.

```bash
jdbg exec suspend [OPTIONS]
```

**Options:**
- `--thread, -t <ID>` - Suspend only this thread (default: all threads)
- `--session, -s <ID>` - Session ID

### `jdbg exec resume`

Resume execution.

```bash
jdbg exec resume [OPTIONS]
```

**Options:**
- `--thread, -t <ID>` - Resume only this thread (default: all threads)
- `--session, -s <ID>` - Session ID

### `jdbg exec step`

Step execution.

```bash
jdbg exec step [OPTIONS]
```

**Options:**
- `--into, -i` - Step into method calls
- `--over, -o` - Step over method calls (default)
- `--out, -u` - Step out of current method
- `--thread, -t <ID>` - Thread to step (default: selected thread)
- `--session, -s <ID>` - Session ID

**Examples:**
```bash
# Step over (next line)
jdbg exec step
jdbg exec step --over

# Step into method
jdbg exec step --into

# Step out of method
jdbg exec step --out
```

## Execution Flow Example

```bash
# 1. Set a breakpoint
jdbg bp add --class com.example.Main --method main

# 2. Wait for breakpoint hit (or check thread list)
jdbg thread list --suspended-only

# 3. Step through code
jdbg exec step --into
jdbg var list
jdbg exec step --over
jdbg var list

# 4. Continue execution
jdbg exec continue
```


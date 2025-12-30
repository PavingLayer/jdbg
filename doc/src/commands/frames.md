# Stack Frames

Inspect and navigate the call stack.

## Commands

### `jdbg frame list` (aliases: `jdbg frame ls`, `jdbg frame bt`)

List stack frames (backtrace) for a thread.

```bash
jdbg frame list [OPTIONS]
```

**Options:**
- `--thread, -t <ID>` - Thread ID (default: selected thread)
- `--limit, -n <COUNT>` - Maximum frames to show
- `--session, -s <ID>` - Session ID

**Example output:**
```
Stack frames:
  #0 com.example.MyService.processRequest (MyService.java:42)
  #1 com.example.MyService.handleRequest (MyService.java:28)
  #2 com.example.Controller.doGet (Controller.java:15)
  #3 javax.servlet.http.HttpServlet.service (HttpServlet.java:750)
```

### `jdbg frame select`

Select a stack frame for variable inspection.

```bash
jdbg frame select <FRAME_INDEX> [OPTIONS]
```

**Options:**
- `--session, -s <ID>` - Session ID

Frame index 0 is the top of the stack (most recent call).

### `jdbg frame info`

Get detailed information about a stack frame.

```bash
jdbg frame info [OPTIONS]
```

**Options:**
- `--frame <INDEX>` - Frame index (default: selected frame)
- `--thread, -t <ID>` - Thread ID (default: selected thread)
- `--session, -s <ID>` - Session ID

## Usage Pattern

```bash
# 1. Suspend the JVM
jdbg exec suspend

# 2. Select a thread
jdbg thread select 12

# 3. View the stack
jdbg frame list

# 4. Select a frame
jdbg frame select 2

# 5. Inspect variables in that frame
jdbg var list
```

## Notes

- Frame operations require the thread to be suspended
- Frame index 0 is always the current execution point
- Selecting a frame affects which variables are visible in `var list`


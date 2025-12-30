# Breakpoints

Manage line and method breakpoints.

## Commands

### `jdbg breakpoint add` (alias: `jdbg bp add`)

Add a breakpoint.

```bash
jdbg bp add --class <CLASS> [OPTIONS]
```

**Options:**
- `--class, -c <CLASS>` - Fully qualified class name (required)
- `--line, -l <LINE>` - Line number (for line breakpoint)
- `--method, -m <METHOD>` - Method name (for method breakpoint)
- `--condition <EXPR>` - Conditional expression
- `--session, -s <ID>` - Session ID

Either `--line` or `--method` must be specified.

**Examples:**
```bash
# Line breakpoint
jdbg bp add --class com.example.MyService --line 42

# Method breakpoint
jdbg bp add --class com.example.MyService --method processRequest

# Conditional breakpoint
jdbg bp add --class com.example.MyService --line 42 --condition "count > 10"
```

### `jdbg breakpoint remove` (alias: `jdbg bp rm`)

Remove a breakpoint.

```bash
jdbg bp remove <BREAKPOINT_ID>
```

### `jdbg breakpoint enable`

Enable a disabled breakpoint.

```bash
jdbg bp enable <BREAKPOINT_ID>
```

### `jdbg breakpoint disable`

Disable a breakpoint without removing it.

```bash
jdbg bp disable <BREAKPOINT_ID>
```

### `jdbg breakpoint list` (alias: `jdbg bp ls`)

List all breakpoints.

```bash
jdbg bp list [OPTIONS]
```

**Options:**
- `--session, -s <ID>` - Session ID

**Example output:**
```
Breakpoints:
  [+] abc123 com.example.MyService:42
  [-] def456 com.example.MyService.processRequest
```

`[+]` indicates enabled, `[-]` indicates disabled.

### `jdbg breakpoint clear`

Remove all breakpoints.

```bash
jdbg bp clear [OPTIONS]
```

**Options:**
- `--session, -s <ID>` - Session ID

## Deferred Breakpoints

If you set a breakpoint on a class that hasn't been loaded yet, JDBG creates a deferred breakpoint. The breakpoint will be activated when the class is loaded.

## JSON Output

```bash
jdbg -f json bp list | jq '.data[] | {id, location: .location_string, hits: .hit_count}'
```


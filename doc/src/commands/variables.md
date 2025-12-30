# Variable Inspection

Inspect and modify local variables.

## Commands

### `jdbg var list` (alias: `jdbg var ls`)

List all variables in the current frame.

```bash
jdbg var list [OPTIONS]
```

**Options:**
- `--thread, -t <ID>` - Thread ID (default: selected thread)
- `--frame <INDEX>` - Frame index (default: selected frame)
- `--session, -s <ID>` - Session ID

**Example output:**
```
Variables:
  MyService this = com.example.MyService@12345 [this]
  String name = "John" [argument]
  int count = 42 [local]
  List items = java.util.ArrayList@67890 [local]
```

### `jdbg var get`

Get a specific variable's value.

```bash
jdbg var get <NAME> [OPTIONS]
```

**Options:**
- `--thread, -t <ID>` - Thread ID
- `--frame <INDEX>` - Frame index
- `--session, -s <ID>` - Session ID

**Example:**
```bash
jdbg var get count
# Output: int count = 42 [local]
```

### `jdbg var set`

Set a variable's value.

```bash
jdbg var set <NAME> <VALUE> [OPTIONS]
```

**Options:**
- `--thread, -t <ID>` - Thread ID
- `--frame <INDEX>` - Frame index
- `--session, -s <ID>` - Session ID

**Examples:**
```bash
# Set primitive
jdbg var set count 100

# Set string
jdbg var set name "Jane"

# Set null
jdbg var set obj null
```

## Variable Types

| Kind | Description |
|------|-------------|
| `this` | The `this` reference |
| `argument` | Method parameter |
| `local` | Local variable |
| `field` | Object field |
| `static` | Static field |

## JSON Output

```bash
jdbg -f json var list | jq '.data[] | {name, type, value}'
```

## Notes

- Variable inspection requires the thread to be suspended
- Variables may not be available if compiled without debug information
- The `this` reference is only available in instance methods


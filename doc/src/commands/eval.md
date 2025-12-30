# Expression Evaluation

Evaluate expressions in the context of the current frame.

## Command

### `jdbg eval`

Evaluate an expression.

```bash
jdbg eval <EXPRESSION> [OPTIONS]
```

**Options:**
- `--thread, -t <ID>` - Thread ID (default: selected thread)
- `--frame <INDEX>` - Frame index (default: selected frame)
- `--session, -s <ID>` - Session ID

## Examples

```bash
# Simple variable
jdbg eval count
# Output: count = 42 (int)

# Field access
jdbg eval "this.name"
# Output: this.name = "John" (java.lang.String)

# Method call (if implemented)
jdbg eval "list.size()"
# Output: list.size() = 5 (int)

# Expression
jdbg eval "count * 2"
# Output: count * 2 = 84 (int)
```

## Current Limitations

The current implementation supports:
- Simple variable lookup
- Field access via `this.field`

Future versions may support:
- Arbitrary expression evaluation
- Method invocation
- Object construction

## JSON Output

```bash
jdbg -f json eval "this.name" | jq '{result, type}'
```

## Notes

- Evaluation requires the thread to be suspended
- Complex expressions may not be supported
- Be careful with side effects (method calls may modify state)


# Exception Breakpoints

Break when exceptions are thrown.

## Commands

### `jdbg exception catch`

Add an exception breakpoint.

```bash
jdbg exception catch <EXCEPTION_CLASS> [OPTIONS]
```

**Options:**
- `--caught` - Break on caught exceptions (default: true)
- `--uncaught` - Break on uncaught exceptions (default: true)
- `--session, -s <ID>` - Session ID

**Examples:**
```bash
# Break on NullPointerException
jdbg exception catch java.lang.NullPointerException

# Break only on uncaught exceptions
jdbg exception catch java.lang.RuntimeException --caught=false

# Break on all exceptions
jdbg exception catch java.lang.Throwable
```

### `jdbg exception ignore`

Remove an exception breakpoint.

```bash
jdbg exception ignore <ID>
```

### `jdbg exception list` (alias: `jdbg exception ls`)

List all exception breakpoints.

```bash
jdbg exception list [OPTIONS]
```

**Options:**
- `--session, -s <ID>` - Session ID

**Example output:**
```
Exception breakpoints:
  abc123 java.lang.NullPointerException (caught: true, uncaught: true)
  def456 java.lang.RuntimeException (caught: false, uncaught: true)
```

## Caught vs Uncaught

- **Caught**: Exception is caught by a `try-catch` block
- **Uncaught**: Exception propagates up and may terminate the thread

## Common Exception Classes

| Exception | Description |
|-----------|-------------|
| `java.lang.Throwable` | All exceptions and errors |
| `java.lang.Exception` | All exceptions |
| `java.lang.RuntimeException` | Unchecked exceptions |
| `java.lang.NullPointerException` | Null pointer access |
| `java.lang.IllegalArgumentException` | Invalid argument |
| `java.lang.IllegalStateException` | Invalid state |
| `java.io.IOException` | I/O errors |

## JSON Output

```bash
jdbg -f json exception list | jq '.data[] | {id, class: .exception_class}'
```


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

## Supported Expressions

The expression evaluator supports a wide range of Java expressions:

### Literals

```bash
jdbg eval 42              # Integer
jdbg eval 3.14            # Double
jdbg eval 123L            # Long
jdbg eval '"hello"'       # String
jdbg eval true            # Boolean
jdbg eval null            # Null
```

### Variables

```bash
jdbg eval count           # Local variable
jdbg eval this            # Current object (in instance methods)
jdbg eval args            # Method parameters
```

### Field Access

```bash
jdbg eval "this.name"           # Instance field
jdbg eval "person.address"      # Chained field access
jdbg eval "person.address.city" # Deep field access
jdbg eval "array.length"        # Array length
```

### Method Calls

```bash
jdbg eval "list.size()"              # No-arg method
jdbg eval "str.substring(0, 5)"      # Method with arguments
jdbg eval "person.getName()"         # Getter method
jdbg eval "str.toUpperCase()"        # String method
jdbg eval '"hello".length()'         # Method on literal
```

### Array Access

```bash
jdbg eval "arr[0]"           # Array element
jdbg eval "matrix[i][j]"     # Multi-dimensional
jdbg eval "list.get(0)"      # List element via method
```

### Arithmetic Operators

```bash
jdbg eval "a + b"            # Addition
jdbg eval "x - y"            # Subtraction
jdbg eval "n * 2"            # Multiplication
jdbg eval "total / count"    # Division
jdbg eval "value % 10"       # Modulo
jdbg eval "(a + b) * c"      # Parentheses for precedence
```

### Comparison Operators

```bash
jdbg eval "x < y"            # Less than
jdbg eval "x > y"            # Greater than
jdbg eval "x <= y"           # Less than or equal
jdbg eval "x >= y"           # Greater than or equal
jdbg eval "a == b"           # Equality
jdbg eval "a != b"           # Inequality
```

### Logical Operators

```bash
jdbg eval "a && b"           # Logical AND
jdbg eval "a || b"           # Logical OR
jdbg eval "!flag"            # Logical NOT
```

### String Concatenation

```bash
jdbg eval '"Hello, " + name'        # String + variable
jdbg eval '"Value: " + 42'          # String + number
jdbg eval '"a" + "b" + "c"'         # Multiple concatenation
```

### Type Operations

```bash
jdbg eval "(int) value"             # Cast
jdbg eval "obj instanceof String"   # Type check
```

### Object Creation

```bash
jdbg eval "new StringBuilder()"           # No-arg constructor
jdbg eval 'new String("hello")'           # Constructor with args
jdbg eval "new int[10]"                   # Array creation
```

## Examples

```bash
# Simple variable
jdbg eval count
# Output: count = 42 (int)

# Complex expression
jdbg eval "items.size() > 0 && items.get(0) != null"
# Output: items.size() > 0 && items.get(0) != null = true (boolean)

# String manipulation
jdbg eval "name.toUpperCase().substring(0, 3)"
# Output: name.toUpperCase().substring(0, 3) = "JOH" (java.lang.String)

# Arithmetic with variables
jdbg eval "price * quantity * (1 + taxRate)"
# Output: price * quantity * (1 + taxRate) = 107.5 (double)
```

## JSON Output

```bash
jdbg -f json eval "this.name" | jq '{result, type}'
```

```json
{
  "result": "\"John\"",
  "type": "java.lang.String"
}
```

## Notes

- Evaluation requires the thread to be suspended
- Method calls execute in the target JVM and may have side effects
- Be careful with methods that modify state
- Some expressions may fail if required classes are not loaded
- The evaluator uses JDI method invocation for method calls

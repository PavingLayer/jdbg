# Command Reference

Each debugger operation is a standalone CLI command. Use `--help` on any command for flags and examples. JSON output is available with `-f json`.

| Command | Description |
|---------|-------------|
| [Status](./status.md) | Overview of JVM state, suspended threads, and breakpoints |
| [Session Management](./session.md) | Attach, launch, list, and switch debug sessions |
| [Breakpoints](./breakpoints.md) | Line and method breakpoints |
| [Execution Control](./exec.md) | Continue, suspend, step, and run-to |
| [Threads](./threads.md) | List, select, suspend, and resume threads |
| [Stack Frames](./frames.md) | Inspect and navigate the call stack |
| [Variables](./variables.md) | Inspect and modify locals |
| [Evaluation](./eval.md) | Evaluate expressions in the current frame |
| [Exceptions](./exceptions.md) | Break when exceptions are thrown |
| [Events](./events.md) | Wait for and poll debug events |
| [Server Management](./server.md) | Start, stop, and inspect the JDBG daemon |

## Typical session

```bash
jdbg server start
jdbg session attach --host localhost --port 8000
jdbg bp add --class com.example.Main --line 42
jdbg events wait -t breakpoint --timeout 60000
jdbg status
jdbg exec continue
jdbg session detach
jdbg server stop
```

See the [How-to Guides](../howto/index.md) for full workflows.

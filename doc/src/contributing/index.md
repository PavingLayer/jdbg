# Contributing to JDBG

This guide covers the project structure, documentation organization, and the checklist for making changes.

## Project Structure

```
jdbg/
├── cli/                    # Rust CLI client
│   ├── src/
│   │   ├── commands/       # Command implementations
│   │   ├── client.rs       # gRPC client
│   │   ├── config.rs       # Configuration
│   │   └── output.rs       # JSON/text formatting
│   └── tests/              # Integration tests
│
├── server/                 # Java gRPC server
│   └── src/
│       ├── main/java/      # Server implementation
│       └── test/java/      # Unit tests
│
├── proto/                  # Shared protobuf definitions
│   └── jdbg.proto          # gRPC service & message definitions
│
├── doc/                    # Documentation (mdBook)
│   └── src/
│       ├── guide/          # Getting started guides
│       ├── howto/          # Task-oriented tutorials
│       ├── commands/       # Command reference
│       ├── reference/      # Technical reference
│       └── contributing/   # This guide
│
├── test/                   # Integration test environment
│   ├── test-target/        # Sample Java app for testing
│   └── docker-compose.yml  # Test environment
│
└── scripts/                # Build and install scripts
```

## Documentation Structure

| Section | Purpose | When to Update |
|---------|---------|----------------|
| **User Guide** (`guide/`) | Getting started, installation, quick start | Installation changes, new dependencies |
| **How-to Guides** (`howto/`) | Task-oriented tutorials | New use cases, workflow improvements |
| **Commands** (`commands/`) | CLI command reference | New commands, option changes |
| **Reference** (`reference/`) | Architecture, API, config | API changes, new config options |
| **Contributing** (`contributing/`) | Development guide | Process changes |

### Documentation Principles

1. **User Guide** - Answers "How do I get started?"
2. **How-to Guides** - Answers "How do I accomplish X?"
3. **Commands** - Answers "What does command Y do?"
4. **Reference** - Answers "What are all the options/fields?"

## Change Checklist

Use this checklist when making changes to ensure nothing is missed.

### For Any Change

- [ ] Code compiles without errors
- [ ] No new linter warnings (Rust: `cargo clippy`, Java: Maven checks)
- [ ] Changes follow existing code style

### For Proto Changes (`proto/jdbg.proto`)

Proto files define data structures, not logic. Tests are added when implementing server/CLI logic, not for proto changes themselves.

- [ ] Update proto file with new messages/services
- [ ] Regenerate Java code: `cd server && ./mvnw generate-sources`
- [ ] Regenerate Rust code: `cd cli && cargo build` (build.rs handles this)
- [ ] Update server implementation (`server/src/main/java/`)
- [ ] Update CLI commands (`cli/src/commands/`)
- [ ] Update [gRPC API Reference](../reference/grpc-api.md)
- [ ] Update affected command documentation (`commands/*.md`)

### For New CLI Commands

- [ ] Add command implementation in `cli/src/commands/`
- [ ] Register in `cli/src/commands/mod.rs`
- [ ] Add shell completions in `cli/src/completion.rs`
- [ ] Add integration tests in `cli/tests/`
- [ ] Create command documentation in `doc/src/commands/`
- [ ] Add to `doc/src/SUMMARY.md`
- [ ] Update relevant how-to guides if applicable

### For New Server Features

- [ ] Implement in `server/src/main/java/`
- [ ] Add corresponding proto definitions
- [ ] Add unit tests
- [ ] Update gRPC API documentation
- [ ] Expose via CLI if user-facing

### For Configuration Changes

- [ ] Update CLI config handling (`cli/src/config.rs`)
- [ ] Update server config if applicable
- [ ] Document in [Configuration Reference](../reference/config.md)
- [ ] Update [Installation Guide](../guide/installation.md) if env vars change

## Running Tests

### Java Server Tests

```bash
cd server
./mvnw test
```

### Rust CLI Tests

```bash
cd cli
cargo test
```

### Integration Tests

Integration tests require a running test environment:

```bash
cd test
docker-compose up -d
./run-with-coverage.sh
```

### Manual Testing

```bash
# Start the test target
cd test/test-target
mvn package
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:8000 \
     -jar target/test-target.jar &

# Test your changes
jdbg server start
jdbg session attach --host localhost --port 8000
# ... test your feature ...
jdbg session detach
jdbg server stop
```

## Building Documentation

```bash
cd doc

# Build
mdbook build

# Serve locally with hot reload
mdbook serve --open
```

## Code Style

### Rust (CLI)

- Use `rustfmt` for formatting
- Follow Clippy suggestions
- Use `final` where possible (per project preference)
- Specific exception handling, no generic `Exception` catches

### Java (Server)

- Use consistent indentation (4 spaces)
- Use `final` keyword where possible
- Handle specific exceptions, not generic `Exception`
- Follow existing patterns in codebase

## Commit Messages

Use conventional commits:

```
feat: add conditional breakpoints
fix: handle null thread names
docs: update gRPC API reference
test: add breakpoint integration tests
refactor: extract event processing logic
```

## Pull Request Process

1. Create a feature branch
2. Make changes following the checklist
3. Run all tests
4. Update documentation
5. Submit PR with description of changes
6. Address review feedback

## Documentation Update Examples

### Adding a New Command

1. Create `doc/src/commands/newcmd.md`:

```markdown
# New Command

Description of what the command does.

## Usage

\`\`\`bash
jdbg newcmd [OPTIONS]
\`\`\`

## Options

| Option | Description |
|--------|-------------|
| `--flag` | What this flag does |

## Examples

\`\`\`bash
jdbg newcmd --flag value
\`\`\`

## See Also

- [Related Command](./related.md)
```

2. Add to `SUMMARY.md`:

```markdown
- [New Command](./commands/newcmd.md)
```

### Updating API Reference

When adding a new gRPC method:

1. Add method to the appropriate table in `reference/grpc-api.md`
2. Add message structures if new types are introduced
3. Add links between methods and structures
4. Add an API walkthrough example if the feature is significant

## Getting Help

- Check existing documentation
- Review similar implementations in codebase
- Open an issue for discussion before large changes


# Shell Completions

JDBG supports tab completion for Bash, Zsh, and Fish shells.

## Installation

### Bash

```bash
# Generate completions
jdbg completions bash > ~/.local/share/bash-completion/completions/jdbg

# Or system-wide (requires root)
sudo jdbg completions bash > /etc/bash_completion.d/jdbg

# Reload bash or source the file
source ~/.local/share/bash-completion/completions/jdbg
```

### Zsh

```bash
# Create completions directory if needed
mkdir -p ~/.zfunc

# Generate completions
jdbg completions zsh > ~/.zfunc/_jdbg

# Add to ~/.zshrc (before compinit):
# fpath=(~/.zfunc $fpath)
# autoload -Uz compinit && compinit

# Reload zsh
exec zsh
```

### Fish

```bash
# Generate completions
jdbg completions fish > ~/.config/fish/completions/jdbg.fish

# Completions are automatically loaded
```

## What Gets Completed

### Static Completions

These work without a running server:

- Command names (`session`, `breakpoint`, `thread`, etc.)
- Subcommand names (`attach`, `list`, `add`, etc.)
- Option names (`--host`, `--port`, `--class`, etc.)
- Output formats (`text`, `json`)

### Dynamic Completions (Future)

With a running server, the CLI can query for:

- Session IDs
- Class names
- Method names
- Thread IDs/names
- Variable names
- Breakpoint IDs

## Example Usage

```bash
# Complete commands
jdbg <TAB>
# → session  breakpoint  exec  thread  frame  var  ...

# Complete subcommands
jdbg session <TAB>
# → attach  attach-pid  detach  status  list  select

# Complete options
jdbg session attach --<TAB>
# → --host  --port  --name  --timeout

# Complete output format
jdbg -f <TAB>
# → text  json
```


package dev.jdbg.cli.breakpoint;

import picocli.CommandLine.Command;

/**
 * Breakpoint management commands.
 */
@Command(
    name = "breakpoint",
    aliases = {"bp"},
    description = "Manage breakpoints",
    subcommands = {
        BreakpointAddCommand.class,
        BreakpointRemoveCommand.class,
        BreakpointListCommand.class,
        BreakpointEnableCommand.class,
        BreakpointDisableCommand.class,
        BreakpointClearCommand.class
    }
)
public final class BreakpointCommand {
}


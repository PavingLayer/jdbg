package dev.jdbg.cli.exception;

import picocli.CommandLine.Command;

/**
 * Exception breakpoint commands.
 */
@Command(
    name = "exception",
    description = "Exception breakpoints",
    subcommands = {
        ExceptionCatchCommand.class,
        ExceptionIgnoreCommand.class,
        ExceptionListCommand.class
    }
)
public final class ExceptionCommand {
}


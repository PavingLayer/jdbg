package dev.jdbg.cli.session;

import picocli.CommandLine.Command;

/**
 * Session management commands.
 */
@Command(
    name = "session",
    description = "Manage debugging sessions",
    subcommands = {
        SessionAttachCommand.class,
        SessionStartCommand.class,
        SessionStatusCommand.class,
        SessionListCommand.class,
        SessionStopCommand.class,
        SessionSelectCommand.class
    }
)
public final class SessionCommand {
}


package dev.jdbg.cli.thread;

import picocli.CommandLine.Command;

/**
 * Thread management commands.
 */
@Command(
    name = "thread",
    description = "Thread management",
    subcommands = {
        ThreadListCommand.class,
        ThreadSelectCommand.class,
        ThreadSuspendCommand.class,
        ThreadResumeCommand.class,
        ThreadInfoCommand.class
    }
)
public final class ThreadCommand {
}


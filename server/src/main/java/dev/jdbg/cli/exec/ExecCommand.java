package dev.jdbg.cli.exec;

import picocli.CommandLine.Command;

/**
 * Execution control commands.
 */
@Command(
    name = "exec",
    description = "Execution control",
    subcommands = {
        ExecContinueCommand.class,
        ExecStepCommand.class,
        ExecSuspendCommand.class,
        ExecResumeCommand.class,
        ExecRunToCommand.class
    }
)
public final class ExecCommand {
}


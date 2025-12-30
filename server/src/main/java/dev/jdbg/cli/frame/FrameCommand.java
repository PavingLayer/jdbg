package dev.jdbg.cli.frame;

import picocli.CommandLine.Command;

/**
 * Frame management commands.
 */
@Command(
    name = "frame",
    description = "Stack frame management",
    subcommands = {
        FrameListCommand.class,
        FrameSelectCommand.class,
        FrameInfoCommand.class
    }
)
public final class FrameCommand {
}


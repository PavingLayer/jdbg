package dev.jdbg.cli.var;

import picocli.CommandLine.Command;

/**
 * Variable inspection commands.
 */
@Command(
    name = "var",
    description = "Variable inspection",
    subcommands = {
        VarListCommand.class,
        VarGetCommand.class,
        VarSetCommand.class
    }
)
public final class VarCommand {
}


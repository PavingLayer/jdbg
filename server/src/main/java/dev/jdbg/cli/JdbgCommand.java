package dev.jdbg.cli;

import dev.jdbg.cli.breakpoint.BreakpointCommand;
import dev.jdbg.cli.eval.EvalCommand;
import dev.jdbg.cli.exception.ExceptionCommand;
import dev.jdbg.cli.exec.ExecCommand;
import dev.jdbg.cli.frame.FrameCommand;
import dev.jdbg.cli.session.SessionCommand;
import dev.jdbg.cli.source.SourceCommand;
import dev.jdbg.cli.thread.ThreadCommand;
import dev.jdbg.cli.var.VarCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Root command for JDBG CLI.
 */
@Command(
    name = "jdbg",
    description = "Non-interactive, scriptable Java debugger",
    mixinStandardHelpOptions = true,
    version = "jdbg 0.1.0",
    subcommands = {
        SessionCommand.class,
        BreakpointCommand.class,
        ExecCommand.class,
        ThreadCommand.class,
        FrameCommand.class,
        VarCommand.class,
        EvalCommand.class,
        ExceptionCommand.class,
        SourceCommand.class,
        CommandLine.HelpCommand.class
    }
)
public final class JdbgCommand implements Runnable {

    @Option(names = {"-v", "--verbose"}, description = "Verbose output")
    private boolean verbose;

    @Override
    public void run() {
        // If no subcommand is specified, print help
        CommandLine.usage(this, System.out);
    }
}


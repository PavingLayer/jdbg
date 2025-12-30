package dev.jdbg;

import dev.jdbg.cli.JdbgCommand;
import picocli.CommandLine;

/**
 * Main entry point for JDBG.
 */
public final class Main {
    
    private Main() {
    }

    public static void main(final String[] args) {
        final int exitCode = new CommandLine(new JdbgCommand())
            .setExecutionStrategy(new CommandLine.RunLast())
            .execute(args);
        System.exit(exitCode);
    }
}


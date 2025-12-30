package dev.jdbg.cli.exception;

import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.ExceptionBreakpoint;
import dev.jdbg.model.Session;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Catch exceptions of a specific type.
 */
@Command(
    name = "catch",
    description = "Break on exceptions of specified type"
)
public final class ExceptionCatchCommand extends BaseCommand {

    @Parameters(index = "0", description = "Exception class name (e.g., java.lang.NullPointerException)")
    private String exceptionClassName;

    @Option(names = {"--caught"}, description = "Break on caught exceptions", defaultValue = "true")
    private boolean caught;

    @Option(names = {"--uncaught"}, description = "Break on uncaught exceptions", defaultValue = "true")
    private boolean uncaught;

    @Override
    protected int execute() {
        final Session session = getSession();
        final String id = generateId();
        
        final ExceptionBreakpoint eb = new ExceptionBreakpoint(id, session.getId(), exceptionClassName);
        eb.setCaught(caught);
        eb.setUncaught(uncaught);
        
        // Save to storage
        storage.saveExceptionBreakpoint(eb);
        
        // Install in VM
        try (final JdiConnection conn = connect(session)) {
            conn.addExceptionBreakpoint(eb);
        } catch (final Exception e) {
            // Best effort - class may not be loaded yet
        }
        
        output.writeSuccess(eb);
        return 0;
    }
}


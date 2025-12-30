package dev.jdbg.cli.exception;

import dev.jdbg.ErrorCodes;
import dev.jdbg.JdbgException;
import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.ExceptionBreakpoint;
import dev.jdbg.model.Session;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.HashMap;
import java.util.Map;

/**
 * Stop catching exceptions of a specific type.
 */
@Command(
    name = "ignore",
    description = "Stop breaking on specified exception type"
)
public final class ExceptionIgnoreCommand extends BaseCommand {

    @Parameters(index = "0", description = "Exception breakpoint ID or class name")
    private String idOrClassName;

    @Override
    protected int execute() {
        final Session session = getSession();
        
        // Find the exception breakpoint
        ExceptionBreakpoint eb = storage.getExceptionBreakpoint(idOrClassName).orElse(null);
        
        if (eb == null) {
            // Try to find by class name
            eb = storage.listExceptionBreakpoints(session.getId()).stream()
                .filter(e -> e.getExceptionClassName().equals(idOrClassName))
                .findFirst()
                .orElseThrow(() -> new JdbgException(ErrorCodes.BREAKPOINT_NOT_FOUND, 
                    "Exception breakpoint not found: " + idOrClassName));
        }
        
        // Remove from VM
        try (final JdiConnection conn = connect(session)) {
            conn.removeExceptionBreakpoint(eb.getId());
        } catch (final Exception e) {
            // Best effort
        }
        
        // Remove from storage
        storage.deleteExceptionBreakpoint(eb.getId());
        
        final Map<String, Object> result = new HashMap<>();
        result.put("removed", eb.getId());
        result.put("exceptionClass", eb.getExceptionClassName());
        
        output.writeSuccess(result);
        return 0;
    }
}


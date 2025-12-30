package dev.jdbg.cli.breakpoint;

import dev.jdbg.ErrorCodes;
import dev.jdbg.JdbgException;
import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.Breakpoint;
import dev.jdbg.model.Session;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Disable a breakpoint.
 */
@Command(
    name = "disable",
    description = "Disable a breakpoint"
)
public final class BreakpointDisableCommand extends BaseCommand {

    @Parameters(index = "0", description = "Breakpoint ID to disable")
    private String breakpointId;

    @Override
    protected int execute() {
        final Breakpoint bp = storage.getBreakpoint(breakpointId)
            .orElseThrow(() -> new JdbgException(ErrorCodes.BREAKPOINT_NOT_FOUND, 
                "Breakpoint not found: " + breakpointId));
        
        bp.setEnabled(false);
        storage.saveBreakpoint(bp);
        
        final Session session = storage.getSession(bp.getSessionId())
            .orElseThrow(() -> new JdbgException(ErrorCodes.SESSION_NOT_FOUND, 
                "Session not found for breakpoint"));
        
        // Try to disable in VM
        try (final JdiConnection conn = connect(session)) {
            conn.setBreakpointEnabled(breakpointId, false);
        } catch (final Exception e) {
            // Best effort
        }
        
        output.writeSuccess(bp);
        return 0;
    }
}


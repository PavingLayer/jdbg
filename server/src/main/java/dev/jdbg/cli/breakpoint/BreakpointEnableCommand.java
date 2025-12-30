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
 * Enable a breakpoint.
 */
@Command(
    name = "enable",
    description = "Enable a breakpoint"
)
public final class BreakpointEnableCommand extends BaseCommand {

    @Parameters(index = "0", description = "Breakpoint ID to enable")
    private String breakpointId;

    @Override
    protected int execute() {
        final Breakpoint bp = storage.getBreakpoint(breakpointId)
            .orElseThrow(() -> new JdbgException(ErrorCodes.BREAKPOINT_NOT_FOUND, 
                "Breakpoint not found: " + breakpointId));
        
        bp.setEnabled(true);
        storage.saveBreakpoint(bp);
        
        final Session session = storage.getSession(bp.getSessionId())
            .orElseThrow(() -> new JdbgException(ErrorCodes.SESSION_NOT_FOUND, 
                "Session not found for breakpoint"));
        
        // Try to enable in VM
        try (final JdiConnection conn = connect(session)) {
            conn.setBreakpointEnabled(breakpointId, true);
        } catch (final Exception e) {
            // Best effort
        }
        
        output.writeSuccess(bp);
        return 0;
    }
}


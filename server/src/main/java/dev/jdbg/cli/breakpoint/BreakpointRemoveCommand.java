package dev.jdbg.cli.breakpoint;

import dev.jdbg.ErrorCodes;
import dev.jdbg.JdbgException;
import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.Breakpoint;
import dev.jdbg.model.Session;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.HashMap;
import java.util.Map;

/**
 * Remove a breakpoint.
 */
@Command(
    name = "remove",
    aliases = {"rm", "delete"},
    description = "Remove a breakpoint"
)
public final class BreakpointRemoveCommand extends BaseCommand {

    @Parameters(index = "0", description = "Breakpoint ID to remove")
    private String breakpointId;

    @Override
    protected int execute() {
        final Breakpoint bp = storage.getBreakpoint(breakpointId)
            .orElseThrow(() -> new JdbgException(ErrorCodes.BREAKPOINT_NOT_FOUND, 
                "Breakpoint not found: " + breakpointId));
        
        final Session session = storage.getSession(bp.getSessionId())
            .orElseThrow(() -> new JdbgException(ErrorCodes.SESSION_NOT_FOUND, 
                "Session not found for breakpoint"));
        
        // Try to remove from VM
        try (final JdiConnection conn = connect(session)) {
            conn.removeBreakpoint(breakpointId);
        } catch (final Exception e) {
            // Best effort - breakpoint may not be installed
        }
        
        // Remove from storage
        storage.deleteBreakpoint(breakpointId);
        
        final Map<String, Object> result = new HashMap<>();
        result.put("removed", breakpointId);
        result.put("location", bp.getLocationString());
        
        output.writeSuccess(result);
        return 0;
    }
}


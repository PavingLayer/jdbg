package dev.jdbg.cli.breakpoint;

import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.Breakpoint;
import dev.jdbg.model.Session;
import picocli.CommandLine.Command;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Clear all breakpoints.
 */
@Command(
    name = "clear",
    description = "Clear all breakpoints"
)
public final class BreakpointClearCommand extends BaseCommand {

    @Override
    protected int execute() {
        final Session session = getSession();
        final List<Breakpoint> breakpoints = storage.listBreakpoints(session.getId());
        
        // Try to remove from VM
        try (final JdiConnection conn = connect(session)) {
            for (final Breakpoint bp : breakpoints) {
                conn.removeBreakpoint(bp.getId());
            }
        } catch (final Exception e) {
            // Best effort
        }
        
        // Remove from storage
        storage.deleteBreakpointsForSession(session.getId());
        
        final Map<String, Object> result = new HashMap<>();
        result.put("cleared", breakpoints.size());
        
        output.writeSuccess(result);
        return 0;
    }
}


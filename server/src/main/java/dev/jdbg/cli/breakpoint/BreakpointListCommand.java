package dev.jdbg.cli.breakpoint;

import dev.jdbg.cli.BaseCommand;
import dev.jdbg.model.Breakpoint;
import dev.jdbg.model.Session;
import picocli.CommandLine.Command;

import java.util.List;

/**
 * List all breakpoints.
 */
@Command(
    name = "list",
    aliases = {"ls"},
    description = "List all breakpoints"
)
public final class BreakpointListCommand extends BaseCommand {

    @Override
    protected int execute() {
        final Session session = getSession();
        final List<Breakpoint> breakpoints = storage.listBreakpoints(session.getId());
        
        output.writeSuccess(breakpoints);
        return 0;
    }
}


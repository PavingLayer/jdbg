package dev.jdbg.cli.exception;

import dev.jdbg.cli.BaseCommand;
import dev.jdbg.model.ExceptionBreakpoint;
import dev.jdbg.model.Session;
import picocli.CommandLine.Command;

import java.util.List;

/**
 * List exception breakpoints.
 */
@Command(
    name = "list",
    aliases = {"ls"},
    description = "List exception breakpoints"
)
public final class ExceptionListCommand extends BaseCommand {

    @Override
    protected int execute() {
        final Session session = getSession();
        final List<ExceptionBreakpoint> breakpoints = storage.listExceptionBreakpoints(session.getId());
        
        output.writeSuccess(breakpoints);
        return 0;
    }
}


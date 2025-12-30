package dev.jdbg.cli.breakpoint;

import dev.jdbg.ErrorCodes;
import dev.jdbg.JdbgException;
import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.Breakpoint;
import dev.jdbg.model.BreakpointType;
import dev.jdbg.model.Session;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Add a breakpoint.
 */
@Command(
    name = "add",
    description = "Add a breakpoint"
)
public final class BreakpointAddCommand extends BaseCommand {

    @Option(names = {"--class", "-c"}, description = "Fully qualified class name", required = true)
    private String className;

    @Option(names = {"--line", "-l"}, description = "Line number")
    private Integer lineNumber;

    @Option(names = {"--method", "-m"}, description = "Method name")
    private String methodName;

    @Option(names = {"--condition"}, description = "Conditional expression")
    private String condition;

    @Option(names = {"--disabled"}, description = "Create breakpoint in disabled state")
    private boolean disabled;

    @Override
    protected int execute() {
        if (lineNumber == null && methodName == null) {
            throw new JdbgException(ErrorCodes.INVALID_ARGUMENT, 
                "Either --line or --method must be specified");
        }

        final Session session = getSession();
        final String id = generateId();
        
        final BreakpointType type = lineNumber != null ? BreakpointType.LINE : BreakpointType.METHOD;
        final Breakpoint bp = new Breakpoint(id, session.getId(), type);
        bp.setClassName(className);
        bp.setLineNumber(lineNumber);
        bp.setMethodName(methodName);
        bp.setCondition(condition);
        bp.setEnabled(!disabled);
        
        // Save to storage first
        storage.saveBreakpoint(bp);
        
        // Try to install the breakpoint in the VM
        try (final JdiConnection conn = connect(session)) {
            if (type == BreakpointType.LINE) {
                conn.addLineBreakpoint(bp);
            } else {
                conn.addMethodBreakpoint(bp);
            }
        } catch (final Exception e) {
            // Breakpoint saved but not yet active (deferred)
            // This is expected for classes not yet loaded
        }
        
        output.writeSuccess(bp);
        return 0;
    }
}


package dev.jdbg.cli.exec;

import dev.jdbg.ErrorCodes;
import dev.jdbg.JdbgException;
import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.Breakpoint;
import dev.jdbg.model.BreakpointType;
import dev.jdbg.model.Session;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.HashMap;
import java.util.Map;

/**
 * Run to a specific location (temporary breakpoint).
 */
@Command(
    name = "run-to",
    description = "Run to a specific location"
)
public final class ExecRunToCommand extends BaseCommand {

    @Option(names = {"--class", "-c"}, description = "Class name", required = true)
    private String className;

    @Option(names = {"--line", "-l"}, description = "Line number")
    private Integer lineNumber;

    @Option(names = {"--method", "-m"}, description = "Method name")
    private String methodName;

    @Override
    protected int execute() {
        if (lineNumber == null && methodName == null) {
            throw new JdbgException(ErrorCodes.INVALID_ARGUMENT, 
                "Either --line or --method must be specified");
        }

        final Session session = getSession();
        
        try (final JdiConnection conn = connect(session)) {
            // Create a temporary breakpoint
            final String tempId = "temp-" + generateId();
            final BreakpointType type = lineNumber != null ? BreakpointType.LINE : BreakpointType.METHOD;
            final Breakpoint bp = new Breakpoint(tempId, session.getId(), type);
            bp.setClassName(className);
            bp.setLineNumber(lineNumber);
            bp.setMethodName(methodName);
            
            // Install the breakpoint
            if (type == BreakpointType.LINE) {
                conn.addLineBreakpoint(bp);
            } else {
                conn.addMethodBreakpoint(bp);
            }
            
            // Resume execution
            conn.resumeAll();
            
            final Map<String, Object> result = new HashMap<>();
            result.put("action", "run-to");
            result.put("location", bp.getLocationString());
            result.put("tempBreakpoint", tempId);
            
            output.writeSuccess(result);
        }
        
        return 0;
    }
}


package dev.jdbg.cli.var;

import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.Session;
import dev.jdbg.model.VariableInfo;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.HashMap;
import java.util.Map;

/**
 * Set a variable value.
 */
@Command(
    name = "set",
    description = "Set a variable value"
)
public final class VarSetCommand extends BaseCommand {

    @Parameters(index = "0", description = "Variable name")
    private String variableName;

    @Parameters(index = "1", description = "New value")
    private String value;

    @Option(names = {"--thread", "-t"}, description = "Thread ID (default: selected thread)")
    private String threadIdArg;

    @Option(names = {"--frame"}, description = "Frame index (default: selected frame)")
    private Integer frameIndexArg;

    @Override
    protected int execute() {
        final Session session = getSession();
        
        try (final JdiConnection conn = connect(session)) {
            final long threadId = getThreadId(session, threadIdArg);
            final int frameIndex = getFrameIndex(session, frameIndexArg);
            
            conn.setVariable(threadId, frameIndex, variableName, value);
            
            // Get the updated value
            final VariableInfo variable = conn.getVariable(threadId, frameIndex, variableName);
            
            final Map<String, Object> result = new HashMap<>();
            result.put("variable", variableName);
            result.put("newValue", variable.getValue());
            
            output.writeSuccess(result);
        }
        
        return 0;
    }
}


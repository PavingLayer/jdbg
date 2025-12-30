package dev.jdbg.cli.var;

import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.Session;
import dev.jdbg.model.VariableInfo;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Get a variable value.
 */
@Command(
    name = "get",
    description = "Get a variable value"
)
public final class VarGetCommand extends BaseCommand {

    @Parameters(index = "0", description = "Variable name")
    private String variableName;

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
            
            final VariableInfo variable = conn.getVariable(threadId, frameIndex, variableName);
            
            output.writeSuccess(variable);
        }
        
        return 0;
    }
}


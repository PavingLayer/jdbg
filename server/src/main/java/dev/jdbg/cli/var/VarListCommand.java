package dev.jdbg.cli.var;

import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.Session;
import dev.jdbg.model.VariableInfo;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;

/**
 * List variables in scope.
 */
@Command(
    name = "list",
    aliases = {"ls"},
    description = "List variables in scope"
)
public final class VarListCommand extends BaseCommand {

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
            
            final List<VariableInfo> variables = conn.getVariables(threadId, frameIndex);
            
            output.writeSuccess(variables);
        }
        
        return 0;
    }
}


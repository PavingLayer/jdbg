package dev.jdbg.cli.eval;

import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.Session;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.HashMap;
import java.util.Map;

/**
 * Evaluate an expression.
 */
@Command(
    name = "eval",
    description = "Evaluate an expression"
)
public final class EvalCommand extends BaseCommand {

    @Parameters(index = "0", description = "Expression to evaluate")
    private String expression;

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
            
            final String result = conn.evaluate(threadId, frameIndex, expression);
            
            final Map<String, Object> output_data = new HashMap<>();
            output_data.put("expression", expression);
            output_data.put("result", result);
            
            output.writeSuccess(output_data);
        }
        
        return 0;
    }
}


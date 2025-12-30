package dev.jdbg.cli.exec;

import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.HashMap;
import java.util.Map;

/**
 * Step execution.
 */
@Command(
    name = "step",
    description = "Step execution"
)
public final class ExecStepCommand extends BaseCommand {

    @Option(names = {"--into", "-i"}, description = "Step into method calls")
    private boolean into;

    @Option(names = {"--over", "-o"}, description = "Step over method calls")
    private boolean over;

    @Option(names = {"--out", "-u"}, description = "Step out of current method")
    private boolean out;

    @Option(names = {"--thread", "-t"}, description = "Thread ID")
    private String threadIdArg;

    @Override
    protected int execute() {
        final Session session = getSession();
        
        // Determine step depth (default to OVER)
        final StepDepth depth;
        if (into) {
            depth = StepDepth.INTO;
        } else if (out) {
            depth = StepDepth.OUT;
        } else {
            depth = StepDepth.OVER;
        }
        
        try (final JdiConnection conn = connect(session)) {
            final long threadId = getThreadId(session, threadIdArg);
            
            // Create step request
            conn.createStepRequest(threadId, depth, StepType.LINE);
            
            // Resume to execute the step
            conn.resumeAll();
            
            // Save step request for tracking
            final StepRequest stepRequest = new StepRequest(
                generateId(), session.getId(), String.valueOf(threadId), 
                StepType.LINE, depth);
            storage.saveStepRequest(stepRequest);
            
            final Map<String, Object> result = new HashMap<>();
            result.put("action", "step");
            result.put("depth", depth);
            result.put("threadId", threadId);
            
            output.writeSuccess(result);
        }
        
        return 0;
    }
}


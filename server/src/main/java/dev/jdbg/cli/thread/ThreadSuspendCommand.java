package dev.jdbg.cli.thread;

import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.Session;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.HashMap;
import java.util.Map;

/**
 * Suspend a thread.
 */
@Command(
    name = "suspend",
    description = "Suspend a thread"
)
public final class ThreadSuspendCommand extends BaseCommand {

    @Parameters(index = "0", description = "Thread ID to suspend")
    private long threadId;

    @Override
    protected int execute() {
        final Session session = getSession();
        
        try (final JdiConnection conn = connect(session)) {
            conn.suspendThread(threadId);
            
            final Map<String, Object> result = new HashMap<>();
            result.put("action", "suspend");
            result.put("threadId", threadId);
            
            output.writeSuccess(result);
        }
        
        return 0;
    }
}


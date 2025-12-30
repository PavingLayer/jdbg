package dev.jdbg.cli.thread;

import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.Session;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.HashMap;
import java.util.Map;

/**
 * Resume a thread.
 */
@Command(
    name = "resume",
    description = "Resume a thread"
)
public final class ThreadResumeCommand extends BaseCommand {

    @Parameters(index = "0", description = "Thread ID to resume")
    private long threadId;

    @Override
    protected int execute() {
        final Session session = getSession();
        
        try (final JdiConnection conn = connect(session)) {
            conn.resumeThread(threadId);
            
            final Map<String, Object> result = new HashMap<>();
            result.put("action", "resume");
            result.put("threadId", threadId);
            
            output.writeSuccess(result);
        }
        
        return 0;
    }
}


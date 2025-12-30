package dev.jdbg.cli.thread;

import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.Session;
import dev.jdbg.model.ThreadInfo;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.HashMap;
import java.util.Map;

/**
 * Select a thread as the current thread.
 */
@Command(
    name = "select",
    description = "Select a thread as current"
)
public final class ThreadSelectCommand extends BaseCommand {

    @Parameters(index = "0", description = "Thread ID to select")
    private long threadId;

    @Override
    protected int execute() {
        final Session session = getSession();
        
        try (final JdiConnection conn = connect(session)) {
            // Verify thread exists
            final ThreadInfo thread = conn.listThreads().stream()
                .filter(t -> t.getId() == threadId)
                .findFirst()
                .orElseThrow(() -> new dev.jdbg.JdbgException(
                    dev.jdbg.ErrorCodes.THREAD_NOT_FOUND, "Thread not found: " + threadId));
            
            // Update session
            session.setSelectedThreadId(String.valueOf(threadId));
            session.setSelectedFrameIndex(0); // Reset frame selection
            storage.saveSession(session);
            
            final Map<String, Object> result = new HashMap<>();
            result.put("selectedThread", threadId);
            result.put("thread", thread);
            
            output.writeSuccess(result);
        }
        
        return 0;
    }
}


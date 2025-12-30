package dev.jdbg.cli.thread;

import dev.jdbg.ErrorCodes;
import dev.jdbg.JdbgException;
import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.Session;
import dev.jdbg.model.ThreadInfo;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Get information about a thread.
 */
@Command(
    name = "info",
    description = "Get thread information"
)
public final class ThreadInfoCommand extends BaseCommand {

    @Option(names = {"--thread", "-t"}, description = "Thread ID (default: selected thread)")
    private String threadIdArg;

    @Override
    protected int execute() {
        final Session session = getSession();
        
        try (final JdiConnection conn = connect(session)) {
            final long threadId = getThreadId(session, threadIdArg);
            
            final ThreadInfo thread = conn.listThreads().stream()
                .filter(t -> t.getId() == threadId)
                .findFirst()
                .orElseThrow(() -> new JdbgException(ErrorCodes.THREAD_NOT_FOUND, 
                    "Thread not found: " + threadId));
            
            output.writeSuccess(thread);
        }
        
        return 0;
    }
}


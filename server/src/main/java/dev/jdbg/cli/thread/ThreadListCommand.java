package dev.jdbg.cli.thread;

import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.Session;
import dev.jdbg.model.ThreadInfo;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;

/**
 * List all threads.
 */
@Command(
    name = "list",
    aliases = {"ls"},
    description = "List all threads"
)
public final class ThreadListCommand extends BaseCommand {

    @Option(names = {"--suspended-only"}, description = "Show only suspended threads")
    private boolean suspendedOnly;

    @Override
    protected int execute() {
        final Session session = getSession();
        
        try (final JdiConnection conn = connect(session)) {
            List<ThreadInfo> threads = conn.listThreads();
            
            if (suspendedOnly) {
                threads = threads.stream()
                    .filter(ThreadInfo::isSuspended)
                    .toList();
            }
            
            output.writeSuccess(threads);
        }
        
        return 0;
    }
}


package dev.jdbg.cli.exec;

import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.Session;
import dev.jdbg.model.SessionState;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.HashMap;
import java.util.Map;

/**
 * Suspend execution.
 */
@Command(
    name = "suspend",
    description = "Suspend execution"
)
public final class ExecSuspendCommand extends BaseCommand {

    @Option(names = {"--thread", "-t"}, description = "Suspend specific thread (default: all)")
    private String threadIdArg;

    @Override
    protected int execute() {
        final Session session = getSession();
        
        try (final JdiConnection conn = connect(session)) {
            if (threadIdArg != null) {
                final long threadId = Long.parseLong(threadIdArg);
                conn.suspendThread(threadId);
                
                final Map<String, Object> result = new HashMap<>();
                result.put("action", "suspend");
                result.put("threadId", threadId);
                
                output.writeSuccess(result);
            } else {
                conn.suspendAll();
                
                session.setState(SessionState.SUSPENDED);
                storage.saveSession(session);
                
                final Map<String, Object> result = new HashMap<>();
                result.put("action", "suspend");
                result.put("scope", "all");
                
                output.writeSuccess(result);
            }
        }
        
        return 0;
    }
}


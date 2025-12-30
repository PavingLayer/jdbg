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
 * Resume execution.
 */
@Command(
    name = "resume",
    description = "Resume execution"
)
public final class ExecResumeCommand extends BaseCommand {

    @Option(names = {"--thread", "-t"}, description = "Resume specific thread (default: all)")
    private String threadIdArg;

    @Override
    protected int execute() {
        final Session session = getSession();
        
        try (final JdiConnection conn = connect(session)) {
            if (threadIdArg != null) {
                final long threadId = Long.parseLong(threadIdArg);
                conn.resumeThread(threadId);
                
                final Map<String, Object> result = new HashMap<>();
                result.put("action", "resume");
                result.put("threadId", threadId);
                
                output.writeSuccess(result);
            } else {
                conn.resumeAll();
                
                session.setState(SessionState.RUNNING);
                storage.saveSession(session);
                
                final Map<String, Object> result = new HashMap<>();
                result.put("action", "resume");
                result.put("scope", "all");
                
                output.writeSuccess(result);
            }
        }
        
        return 0;
    }
}


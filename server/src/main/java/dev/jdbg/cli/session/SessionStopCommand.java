package dev.jdbg.cli.session;

import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.Session;
import dev.jdbg.model.SessionState;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.HashMap;
import java.util.Map;

/**
 * Stop a debugging session.
 */
@Command(
    name = "stop",
    description = "Stop a debugging session"
)
public final class SessionStopCommand extends BaseCommand {

    @Option(names = {"--terminate", "-t"}, description = "Terminate the target VM")
    private boolean terminate;

    @Option(names = {"--keep", "-k"}, description = "Keep session record (don't delete)")
    private boolean keep;

    @Override
    protected int execute() {
        final Session session = getSession();
        
        // Try to disconnect cleanly
        try (final JdiConnection conn = connect(session)) {
            if (terminate) {
                conn.getVm().exit(0);
                session.setState(SessionState.TERMINATED);
            } else {
                conn.dispose();
                session.setState(SessionState.DISCONNECTED);
            }
        } catch (final Exception e) {
            session.setState(SessionState.DISCONNECTED);
        }
        
        if (keep) {
            storage.saveSession(session);
        } else {
            storage.deleteSession(session.getId());
        }
        
        // Clear active session if this was it
        storage.getActiveSession().ifPresent(active -> {
            if (active.getId().equals(session.getId())) {
                storage.clearActiveSession();
            }
        });
        
        final Map<String, Object> result = new HashMap<>();
        result.put("sessionId", session.getId());
        result.put("state", session.getState());
        result.put("deleted", !keep);
        
        output.writeSuccess(result);
        return 0;
    }
}


package dev.jdbg.cli.session;

import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.Session;
import dev.jdbg.model.SessionState;
import picocli.CommandLine.Command;

/**
 * Get status of a session.
 */
@Command(
    name = "status",
    description = "Get status of a session"
)
public final class SessionStatusCommand extends BaseCommand {

    @Override
    protected int execute() {
        final Session session = getSession();
        
        // Try to connect and update status
        try (final JdiConnection conn = connect(session)) {
            if (conn.isConnected()) {
                session.setState(SessionState.CONNECTED);
            } else {
                session.setState(SessionState.DISCONNECTED);
            }
            storage.saveSession(session);
        } catch (final Exception e) {
            session.setState(SessionState.DISCONNECTED);
            storage.saveSession(session);
        }
        
        output.writeSuccess(session);
        return 0;
    }
}


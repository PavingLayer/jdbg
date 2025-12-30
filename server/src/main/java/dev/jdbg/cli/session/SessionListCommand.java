package dev.jdbg.cli.session;

import dev.jdbg.cli.BaseCommand;
import dev.jdbg.model.Session;
import picocli.CommandLine.Command;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * List all sessions.
 */
@Command(
    name = "list",
    description = "List all sessions"
)
public final class SessionListCommand extends BaseCommand {

    @Override
    protected int execute() {
        final List<Session> sessions = storage.listSessions();
        final Optional<Session> active = storage.getActiveSession();
        
        // Mark active session
        if (active.isPresent()) {
            final String activeId = active.get().getId();
            final Map<String, Object> result = new HashMap<>();
            result.put("sessions", sessions);
            result.put("activeSession", activeId);
            output.writeSuccess(result);
        } else {
            output.writeSuccess(sessions);
        }
        
        return 0;
    }
}


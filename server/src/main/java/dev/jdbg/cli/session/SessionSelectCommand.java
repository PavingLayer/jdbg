package dev.jdbg.cli.session;

import dev.jdbg.ErrorCodes;
import dev.jdbg.JdbgException;
import dev.jdbg.cli.BaseCommand;
import dev.jdbg.model.Session;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.HashMap;
import java.util.Map;

/**
 * Select the active session.
 */
@Command(
    name = "select",
    description = "Select the active session"
)
public final class SessionSelectCommand extends BaseCommand {

    @Parameters(index = "0", description = "Session ID to select")
    private String targetSessionId;

    @Override
    protected int execute() {
        final Session session = storage.getSession(targetSessionId)
            .orElseThrow(() -> new JdbgException(ErrorCodes.SESSION_NOT_FOUND, 
                "Session not found: " + targetSessionId));
        
        storage.setActiveSession(targetSessionId);
        
        final Map<String, Object> result = new HashMap<>();
        result.put("activeSession", targetSessionId);
        result.put("session", session);
        
        output.writeSuccess(result);
        return 0;
    }
}


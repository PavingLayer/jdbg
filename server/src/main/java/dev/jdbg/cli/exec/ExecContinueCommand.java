package dev.jdbg.cli.exec;

import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.Session;
import dev.jdbg.model.SessionState;
import picocli.CommandLine.Command;

import java.util.HashMap;
import java.util.Map;

/**
 * Continue execution.
 */
@Command(
    name = "continue",
    aliases = {"cont", "c"},
    description = "Continue execution"
)
public final class ExecContinueCommand extends BaseCommand {

    @Override
    protected int execute() {
        final Session session = getSession();
        
        try (final JdiConnection conn = connect(session)) {
            conn.resumeAll();
            
            session.setState(SessionState.RUNNING);
            storage.saveSession(session);
            
            final Map<String, Object> result = new HashMap<>();
            result.put("action", "continue");
            result.put("state", "RUNNING");
            
            output.writeSuccess(result);
        }
        
        return 0;
    }
}


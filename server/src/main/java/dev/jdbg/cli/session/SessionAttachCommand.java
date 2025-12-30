package dev.jdbg.cli.session;

import dev.jdbg.ErrorCodes;
import dev.jdbg.JdbgException;
import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.Session;
import dev.jdbg.model.SessionState;
import dev.jdbg.model.SessionType;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Attach to a running JVM.
 */
@Command(
    name = "attach",
    description = "Attach to a running JVM"
)
public final class SessionAttachCommand extends BaseCommand {

    @Option(names = {"--host", "-h"}, description = "Remote host (default: localhost)")
    private String host = "localhost";

    @Option(names = {"--port", "-p"}, description = "Debug port")
    private Integer port;

    @Option(names = {"--pid"}, description = "Process ID for local attach")
    private Integer pid;

    @Option(names = {"--name", "-n"}, description = "Optional session name/id")
    private String name;

    @Override
    protected int execute() {
        if (port == null && pid == null) {
            throw new JdbgException(ErrorCodes.INVALID_ARGUMENT, 
                "Either --port or --pid must be specified");
        }

        final String id = name != null ? name : generateId();
        
        // Check if session already exists
        if (storage.getSession(id).isPresent()) {
            throw new JdbgException(ErrorCodes.SESSION_ALREADY_EXISTS, 
                "Session already exists: " + id);
        }

        // Create session
        final SessionType type = pid != null ? SessionType.ATTACHED_LOCAL : SessionType.ATTACHED_REMOTE;
        final Session session = new Session(id, type);
        
        if (pid != null) {
            session.setPid(pid);
        } else {
            session.setHost(host);
            session.setPort(port);
        }

        // Try to connect
        try (final JdiConnection conn = connect(session)) {
            session.setState(SessionState.CONNECTED);
            session.setVmName(conn.getVmName());
            session.setVmVersion(conn.getVmVersion());
        }

        // Save session
        storage.saveSession(session);
        storage.setActiveSession(session.getId());

        output.writeSuccess(session);
        return 0;
    }
}


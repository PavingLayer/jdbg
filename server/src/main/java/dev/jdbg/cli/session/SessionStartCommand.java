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
import picocli.CommandLine.Parameters;

/**
 * Launch a new JVM with debugging enabled.
 */
@Command(
    name = "start",
    description = "Launch a new JVM with debugging enabled"
)
public final class SessionStartCommand extends BaseCommand {

    @Parameters(index = "0", description = "Main class to launch")
    private String mainClass;

    @Option(names = {"--options", "-o"}, description = "JVM options")
    private String options;

    @Option(names = {"--classpath", "-cp"}, description = "Classpath")
    private String classpath;

    @Option(names = {"--name", "-n"}, description = "Optional session name/id")
    private String name;

    @Option(names = {"--suspend"}, description = "Suspend JVM at start", defaultValue = "true")
    private boolean suspend;

    @Override
    protected int execute() {
        final String id = name != null ? name : generateId();
        
        // Check if session already exists
        if (storage.getSession(id).isPresent()) {
            throw new JdbgException(ErrorCodes.SESSION_ALREADY_EXISTS, 
                "Session already exists: " + id);
        }

        // Create session
        final Session session = new Session(id, SessionType.LAUNCHED);
        session.setMainClass(mainClass);

        // Launch the VM
        final JdiConnection conn = JdiConnection.launch(mainClass, options, classpath);
        
        session.setState(suspend ? SessionState.SUSPENDED : SessionState.RUNNING);
        session.setVmName(conn.getVmName());
        session.setVmVersion(conn.getVmVersion());

        // Save session
        storage.saveSession(session);
        storage.setActiveSession(session.getId());

        // Note: For launched VMs, we'd need to keep the connection alive
        // This is a limitation of the current design - launched VMs require
        // keeping the connection open, which doesn't fit the "reconnect on each command" model
        
        output.writeSuccess(session);
        return 0;
    }
}


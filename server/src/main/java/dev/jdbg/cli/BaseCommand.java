package dev.jdbg.cli;

import dev.jdbg.ErrorCodes;
import dev.jdbg.JdbgException;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.Session;
import dev.jdbg.model.SessionState;
import dev.jdbg.output.OutputFormat;
import dev.jdbg.output.OutputWriter;
import dev.jdbg.storage.SqliteStorage;
import dev.jdbg.storage.Storage;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Base class for all CLI commands.
 */
public abstract class BaseCommand implements Callable<Integer> {
    
    private static final int DEFAULT_TIMEOUT = 5000;

    @Option(names = {"--session", "-s"}, description = "Session ID to use")
    protected String sessionId;

    @Option(names = {"--db"}, description = "Path to database file (default: ~/.jdbg/jdbg.db)")
    protected String dbPath;

    @Option(names = {"--format", "-f"}, description = "Output format: JSON or TEXT", defaultValue = "JSON")
    protected OutputFormat outputFormat;

    @Option(names = {"--timeout"}, description = "Connection timeout in milliseconds", defaultValue = "5000")
    protected int timeout;

    protected OutputWriter output;
    protected Storage storage;

    @Override
    public final Integer call() {
        output = new OutputWriter();
        output.setFormat(outputFormat);
        
        try {
            // Resolve default database path if not specified
            final String resolvedDbPath = dbPath != null ? dbPath 
                : System.getProperty("user.home") + "/.jdbg/jdbg.db";
            
            // Ensure database directory exists
            final Path dbFile = Path.of(resolvedDbPath);
            if (dbFile.getParent() != null) {
                dbFile.getParent().toFile().mkdirs();
            }
            
            storage = new SqliteStorage(dbFile);
            
            return execute();
        } catch (final JdbgException e) {
            output.writeError(e.getCode(), e.getMessage());
            return 1;
        } catch (final Exception e) {
            output.writeError(ErrorCodes.INTERNAL_ERROR, e.getMessage(), 
                e.getClass().getName());
            return 1;
        } finally {
            if (storage != null) {
                storage.close();
            }
        }
    }

    /**
     * Execute the command logic.
     * @return exit code (0 = success)
     */
    protected abstract int execute();

    /**
     * Get the active or specified session.
     */
    protected Session getSession() {
        if (sessionId != null) {
            return storage.getSession(sessionId)
                .orElseThrow(() -> new JdbgException(ErrorCodes.SESSION_NOT_FOUND, 
                    "No session with id: " + sessionId));
        }
        
        return storage.getActiveSession()
            .orElseThrow(() -> new JdbgException(ErrorCodes.NO_ACTIVE_SESSION, 
                "No active session. Use --session or set an active session."));
    }

    /**
     * Get a JDI connection for the session.
     */
    protected JdiConnection connect(final Session session) {
        final JdiConnection conn = switch (session.getType()) {
            case ATTACHED_REMOTE -> JdiConnection.attachRemote(
                session.getHost(), session.getPort(), timeout);
            case ATTACHED_LOCAL -> JdiConnection.attachLocal(
                session.getPid(), timeout);
            case LAUNCHED -> throw new JdbgException(ErrorCodes.INTERNAL_ERROR, 
                "Cannot reconnect to launched VM");
        };
        
        // Update session state
        session.setState(SessionState.CONNECTED);
        session.setVmName(conn.getVmName());
        session.setVmVersion(conn.getVmVersion());
        session.touch();
        storage.saveSession(session);
        
        return conn;
    }

    /**
     * Get or resolve the thread ID to use.
     */
    protected long getThreadId(final Session session, final String threadIdArg) {
        if (threadIdArg != null) {
            return Long.parseLong(threadIdArg);
        }
        
        final String selected = session.getSelectedThreadId();
        if (selected != null) {
            return Long.parseLong(selected);
        }
        
        throw new JdbgException(ErrorCodes.NO_THREAD_SELECTED, 
            "No thread selected. Use --thread or select a thread.");
    }

    /**
     * Get the frame index to use.
     */
    protected int getFrameIndex(final Session session, final Integer frameIndexArg) {
        if (frameIndexArg != null) {
            return frameIndexArg;
        }
        
        final Integer selected = session.getSelectedFrameIndex();
        return selected != null ? selected : 0;
    }

    /**
     * Generate a short unique ID.
     */
    protected String generateId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}


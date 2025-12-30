package dev.jdbg.storage;

import dev.jdbg.ErrorCodes;
import dev.jdbg.JdbgException;
import dev.jdbg.model.*;

import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite-based storage implementation.
 */
public final class SqliteStorage implements Storage {
    
    private final Connection connection;

    public SqliteStorage(final Path dbPath) {
        try {
            final String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
            this.connection = DriverManager.getConnection(url);
            this.connection.setAutoCommit(true);
            initializeSchema();
        } catch (final SQLException e) {
            throw new JdbgException(ErrorCodes.STORAGE_ERROR, "Failed to open database: " + e.getMessage(), e);
        }
    }

    private void initializeSchema() {
        try (final Statement stmt = connection.createStatement()) {
            // Sessions table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sessions (
                    id TEXT PRIMARY KEY,
                    type TEXT NOT NULL,
                    state TEXT NOT NULL,
                    host TEXT,
                    port INTEGER,
                    pid INTEGER,
                    main_class TEXT,
                    vm_name TEXT,
                    vm_version TEXT,
                    created_at TEXT NOT NULL,
                    last_accessed_at TEXT NOT NULL,
                    selected_thread_id TEXT,
                    selected_frame_index INTEGER
                )
                """);

            // Active session tracker
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS active_session (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    session_id TEXT NOT NULL,
                    FOREIGN KEY (session_id) REFERENCES sessions(id)
                )
                """);

            // Breakpoints table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS breakpoints (
                    id TEXT PRIMARY KEY,
                    session_id TEXT NOT NULL,
                    type TEXT NOT NULL,
                    class_name TEXT,
                    line_number INTEGER,
                    method_name TEXT,
                    condition TEXT,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    hit_count INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY (session_id) REFERENCES sessions(id)
                )
                """);

            // Exception breakpoints table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS exception_breakpoints (
                    id TEXT PRIMARY KEY,
                    session_id TEXT NOT NULL,
                    exception_class_name TEXT NOT NULL,
                    caught INTEGER NOT NULL DEFAULT 1,
                    uncaught INTEGER NOT NULL DEFAULT 1,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY (session_id) REFERENCES sessions(id)
                )
                """);

            // Step requests table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS step_requests (
                    id TEXT PRIMARY KEY,
                    session_id TEXT NOT NULL,
                    thread_id TEXT NOT NULL,
                    step_type TEXT NOT NULL,
                    step_depth TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY (session_id) REFERENCES sessions(id)
                )
                """);

            // Indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_breakpoints_session ON breakpoints(session_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_exception_breakpoints_session ON exception_breakpoints(session_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_step_requests_session ON step_requests(session_id)");

        } catch (final SQLException e) {
            throw new JdbgException(ErrorCodes.STORAGE_ERROR, "Failed to initialize schema: " + e.getMessage(), e);
        }
    }

    @Override
    public void saveSession(final Session session) {
        final String sql = """
            INSERT OR REPLACE INTO sessions 
            (id, type, state, host, port, pid, main_class, vm_name, vm_version, 
             created_at, last_accessed_at, selected_thread_id, selected_frame_index)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (final PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, session.getId());
            stmt.setString(2, session.getType().name());
            stmt.setString(3, session.getState().name());
            stmt.setString(4, session.getHost());
            setNullableInt(stmt, 5, session.getPort());
            setNullableInt(stmt, 6, session.getPid());
            stmt.setString(7, session.getMainClass());
            stmt.setString(8, session.getVmName());
            stmt.setString(9, session.getVmVersion());
            stmt.setString(10, session.getCreatedAt().toString());
            stmt.setString(11, session.getLastAccessedAt().toString());
            stmt.setString(12, session.getSelectedThreadId());
            setNullableInt(stmt, 13, session.getSelectedFrameIndex());
            stmt.executeUpdate();
        } catch (final SQLException e) {
            throw new JdbgException(ErrorCodes.STORAGE_ERROR, "Failed to save session: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Session> getSession(final String sessionId) {
        final String sql = "SELECT * FROM sessions WHERE id = ?";
        try (final PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            try (final ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapSession(rs));
                }
            }
        } catch (final SQLException e) {
            throw new JdbgException(ErrorCodes.STORAGE_ERROR, "Failed to get session: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Session> getActiveSession() {
        final String sql = """
            SELECT s.* FROM sessions s
            JOIN active_session a ON s.id = a.session_id
            """;
        try (final PreparedStatement stmt = connection.prepareStatement(sql);
             final ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return Optional.of(mapSession(rs));
            }
        } catch (final SQLException e) {
            throw new JdbgException(ErrorCodes.STORAGE_ERROR, "Failed to get active session: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Session> listSessions() {
        final String sql = "SELECT * FROM sessions ORDER BY created_at DESC";
        final List<Session> sessions = new ArrayList<>();
        try (final PreparedStatement stmt = connection.prepareStatement(sql);
             final ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                sessions.add(mapSession(rs));
            }
        } catch (final SQLException e) {
            throw new JdbgException(ErrorCodes.STORAGE_ERROR, "Failed to list sessions: " + e.getMessage(), e);
        }
        return sessions;
    }

    @Override
    public void deleteSession(final String sessionId) {
        try {
            // Delete related data first
            deleteBreakpointsForSession(sessionId);
            deleteExceptionBreakpointsForSession(sessionId);
            deleteStepRequestsForSession(sessionId);
            
            // Clear active session if it's this session
            final String clearActiveSql = "DELETE FROM active_session WHERE session_id = ?";
            try (final PreparedStatement stmt = connection.prepareStatement(clearActiveSql)) {
                stmt.setString(1, sessionId);
                stmt.executeUpdate();
            }

            // Delete session
            final String sql = "DELETE FROM sessions WHERE id = ?";
            try (final PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, sessionId);
                stmt.executeUpdate();
            }
        } catch (final SQLException e) {
            throw new JdbgException(ErrorCodes.STORAGE_ERROR, "Failed to delete session: " + e.getMessage(), e);
        }
    }

    @Override
    public void setActiveSession(final String sessionId) {
        final String sql = "INSERT OR REPLACE INTO active_session (id, session_id) VALUES (1, ?)";
        try (final PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            stmt.executeUpdate();
        } catch (final SQLException e) {
            throw new JdbgException(ErrorCodes.STORAGE_ERROR, "Failed to set active session: " + e.getMessage(), e);
        }
    }

    @Override
    public void clearActiveSession() {
        final String sql = "DELETE FROM active_session";
        try (final PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (final SQLException e) {
            throw new JdbgException(ErrorCodes.STORAGE_ERROR, "Failed to clear active session: " + e.getMessage(), e);
        }
    }

    @Override
    public void saveBreakpoint(final Breakpoint breakpoint) {
        final String sql = """
            INSERT OR REPLACE INTO breakpoints 
            (id, session_id, type, class_name, line_number, method_name, condition, enabled, hit_count, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (final PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, breakpoint.getId());
            stmt.setString(2, breakpoint.getSessionId());
            stmt.setString(3, breakpoint.getType().name());
            stmt.setString(4, breakpoint.getClassName());
            setNullableInt(stmt, 5, breakpoint.getLineNumber());
            stmt.setString(6, breakpoint.getMethodName());
            stmt.setString(7, breakpoint.getCondition());
            stmt.setInt(8, breakpoint.isEnabled() ? 1 : 0);
            stmt.setInt(9, breakpoint.getHitCount());
            stmt.setString(10, breakpoint.getCreatedAt().toString());
            stmt.executeUpdate();
        } catch (final SQLException e) {
            throw new JdbgException(ErrorCodes.STORAGE_ERROR, "Failed to save breakpoint: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Breakpoint> getBreakpoint(final String breakpointId) {
        final String sql = "SELECT * FROM breakpoints WHERE id = ?";
        try (final PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, breakpointId);
            try (final ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapBreakpoint(rs));
                }
            }
        } catch (final SQLException e) {
            throw new JdbgException(ErrorCodes.STORAGE_ERROR, "Failed to get breakpoint: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Breakpoint> listBreakpoints(final String sessionId) {
        final String sql = "SELECT * FROM breakpoints WHERE session_id = ? ORDER BY created_at";
        final List<Breakpoint> breakpoints = new ArrayList<>();
        try (final PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            try (final ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    breakpoints.add(mapBreakpoint(rs));
                }
            }
        } catch (final SQLException e) {
            throw new JdbgException(ErrorCodes.STORAGE_ERROR, "Failed to list breakpoints: " + e.getMessage(), e);
        }
        return breakpoints;
    }

    @Override
    public void deleteBreakpoint(final String breakpointId) {
        final String sql = "DELETE FROM breakpoints WHERE id = ?";
        try (final PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, breakpointId);
            stmt.executeUpdate();
        } catch (final SQLException e) {
            throw new JdbgException(ErrorCodes.STORAGE_ERROR, "Failed to delete breakpoint: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteBreakpointsForSession(final String sessionId) {
        final String sql = "DELETE FROM breakpoints WHERE session_id = ?";
        try (final PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            stmt.executeUpdate();
        } catch (final SQLException e) {
            throw new JdbgException(ErrorCodes.STORAGE_ERROR, "Failed to delete breakpoints: " + e.getMessage(), e);
        }
    }

    @Override
    public void saveExceptionBreakpoint(final ExceptionBreakpoint exceptionBreakpoint) {
        final String sql = """
            INSERT OR REPLACE INTO exception_breakpoints 
            (id, session_id, exception_class_name, caught, uncaught, enabled, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (final PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, exceptionBreakpoint.getId());
            stmt.setString(2, exceptionBreakpoint.getSessionId());
            stmt.setString(3, exceptionBreakpoint.getExceptionClassName());
            stmt.setInt(4, exceptionBreakpoint.isCaught() ? 1 : 0);
            stmt.setInt(5, exceptionBreakpoint.isUncaught() ? 1 : 0);
            stmt.setInt(6, exceptionBreakpoint.isEnabled() ? 1 : 0);
            stmt.setString(7, exceptionBreakpoint.getCreatedAt().toString());
            stmt.executeUpdate();
        } catch (final SQLException e) {
            throw new JdbgException(ErrorCodes.STORAGE_ERROR, "Failed to save exception breakpoint: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<ExceptionBreakpoint> getExceptionBreakpoint(final String id) {
        final String sql = "SELECT * FROM exception_breakpoints WHERE id = ?";
        try (final PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (final ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapExceptionBreakpoint(rs));
                }
            }
        } catch (final SQLException e) {
            throw new JdbgException(ErrorCodes.STORAGE_ERROR, "Failed to get exception breakpoint: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<ExceptionBreakpoint> listExceptionBreakpoints(final String sessionId) {
        final String sql = "SELECT * FROM exception_breakpoints WHERE session_id = ? ORDER BY created_at";
        final List<ExceptionBreakpoint> breakpoints = new ArrayList<>();
        try (final PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            try (final ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    breakpoints.add(mapExceptionBreakpoint(rs));
                }
            }
        } catch (final SQLException e) {
            throw new JdbgException(ErrorCodes.STORAGE_ERROR, "Failed to list exception breakpoints: " + e.getMessage(), e);
        }
        return breakpoints;
    }

    @Override
    public void deleteExceptionBreakpoint(final String id) {
        final String sql = "DELETE FROM exception_breakpoints WHERE id = ?";
        try (final PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.executeUpdate();
        } catch (final SQLException e) {
            throw new JdbgException(ErrorCodes.STORAGE_ERROR, "Failed to delete exception breakpoint: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteExceptionBreakpointsForSession(final String sessionId) {
        final String sql = "DELETE FROM exception_breakpoints WHERE session_id = ?";
        try (final PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            stmt.executeUpdate();
        } catch (final SQLException e) {
            throw new JdbgException(ErrorCodes.STORAGE_ERROR, "Failed to delete exception breakpoints: " + e.getMessage(), e);
        }
    }

    @Override
    public void saveStepRequest(final StepRequest stepRequest) {
        final String sql = """
            INSERT OR REPLACE INTO step_requests 
            (id, session_id, thread_id, step_type, step_depth, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (final PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, stepRequest.getId());
            stmt.setString(2, stepRequest.getSessionId());
            stmt.setString(3, stepRequest.getThreadId());
            stmt.setString(4, stepRequest.getStepType().name());
            stmt.setString(5, stepRequest.getStepDepth().name());
            stmt.setString(6, stepRequest.getCreatedAt().toString());
            stmt.executeUpdate();
        } catch (final SQLException e) {
            throw new JdbgException(ErrorCodes.STORAGE_ERROR, "Failed to save step request: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<StepRequest> getPendingStepRequest(final String sessionId) {
        final String sql = "SELECT * FROM step_requests WHERE session_id = ? ORDER BY created_at DESC LIMIT 1";
        try (final PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            try (final ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapStepRequest(rs));
                }
            }
        } catch (final SQLException e) {
            throw new JdbgException(ErrorCodes.STORAGE_ERROR, "Failed to get step request: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public void deleteStepRequest(final String id) {
        final String sql = "DELETE FROM step_requests WHERE id = ?";
        try (final PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.executeUpdate();
        } catch (final SQLException e) {
            throw new JdbgException(ErrorCodes.STORAGE_ERROR, "Failed to delete step request: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteStepRequestsForSession(final String sessionId) {
        final String sql = "DELETE FROM step_requests WHERE session_id = ?";
        try (final PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            stmt.executeUpdate();
        } catch (final SQLException e) {
            throw new JdbgException(ErrorCodes.STORAGE_ERROR, "Failed to delete step requests: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (final SQLException e) {
            // Log but don't throw
        }
    }

    private Session mapSession(final ResultSet rs) throws SQLException {
        final Session session = new Session();
        session.setId(rs.getString("id"));
        session.setType(SessionType.valueOf(rs.getString("type")));
        session.setState(SessionState.valueOf(rs.getString("state")));
        session.setHost(rs.getString("host"));
        session.setPort(getNullableInt(rs, "port"));
        session.setPid(getNullableInt(rs, "pid"));
        session.setMainClass(rs.getString("main_class"));
        session.setVmName(rs.getString("vm_name"));
        session.setVmVersion(rs.getString("vm_version"));
        session.setCreatedAt(Instant.parse(rs.getString("created_at")));
        session.setLastAccessedAt(Instant.parse(rs.getString("last_accessed_at")));
        session.setSelectedThreadId(rs.getString("selected_thread_id"));
        session.setSelectedFrameIndex(getNullableInt(rs, "selected_frame_index"));
        return session;
    }

    private Breakpoint mapBreakpoint(final ResultSet rs) throws SQLException {
        final Breakpoint bp = new Breakpoint();
        bp.setId(rs.getString("id"));
        bp.setSessionId(rs.getString("session_id"));
        bp.setType(BreakpointType.valueOf(rs.getString("type")));
        bp.setClassName(rs.getString("class_name"));
        bp.setLineNumber(getNullableInt(rs, "line_number"));
        bp.setMethodName(rs.getString("method_name"));
        bp.setCondition(rs.getString("condition"));
        bp.setEnabled(rs.getInt("enabled") == 1);
        bp.setHitCount(rs.getInt("hit_count"));
        bp.setCreatedAt(Instant.parse(rs.getString("created_at")));
        return bp;
    }

    private ExceptionBreakpoint mapExceptionBreakpoint(final ResultSet rs) throws SQLException {
        final ExceptionBreakpoint eb = new ExceptionBreakpoint();
        eb.setId(rs.getString("id"));
        eb.setSessionId(rs.getString("session_id"));
        eb.setExceptionClassName(rs.getString("exception_class_name"));
        eb.setCaught(rs.getInt("caught") == 1);
        eb.setUncaught(rs.getInt("uncaught") == 1);
        eb.setEnabled(rs.getInt("enabled") == 1);
        eb.setCreatedAt(Instant.parse(rs.getString("created_at")));
        return eb;
    }

    private StepRequest mapStepRequest(final ResultSet rs) throws SQLException {
        final StepRequest sr = new StepRequest();
        sr.setId(rs.getString("id"));
        sr.setSessionId(rs.getString("session_id"));
        sr.setThreadId(rs.getString("thread_id"));
        sr.setStepType(StepType.valueOf(rs.getString("step_type")));
        sr.setStepDepth(StepDepth.valueOf(rs.getString("step_depth")));
        sr.setCreatedAt(Instant.parse(rs.getString("created_at")));
        return sr;
    }

    private void setNullableInt(final PreparedStatement stmt, final int index, final Integer value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.INTEGER);
        } else {
            stmt.setInt(index, value);
        }
    }

    private Integer getNullableInt(final ResultSet rs, final String column) throws SQLException {
        final int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}


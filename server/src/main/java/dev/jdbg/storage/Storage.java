package dev.jdbg.storage;

import dev.jdbg.model.Breakpoint;
import dev.jdbg.model.ExceptionBreakpoint;
import dev.jdbg.model.Session;
import dev.jdbg.model.StepRequest;

import java.util.List;
import java.util.Optional;

/**
 * Storage interface for debugger state persistence.
 */
public interface Storage extends AutoCloseable {

    // Session operations
    void saveSession(Session session);
    Optional<Session> getSession(String sessionId);
    Optional<Session> getActiveSession();
    List<Session> listSessions();
    void deleteSession(String sessionId);
    void setActiveSession(String sessionId);
    void clearActiveSession();

    // Breakpoint operations
    void saveBreakpoint(Breakpoint breakpoint);
    Optional<Breakpoint> getBreakpoint(String breakpointId);
    List<Breakpoint> listBreakpoints(String sessionId);
    void deleteBreakpoint(String breakpointId);
    void deleteBreakpointsForSession(String sessionId);

    // Exception breakpoint operations
    void saveExceptionBreakpoint(ExceptionBreakpoint exceptionBreakpoint);
    Optional<ExceptionBreakpoint> getExceptionBreakpoint(String id);
    List<ExceptionBreakpoint> listExceptionBreakpoints(String sessionId);
    void deleteExceptionBreakpoint(String id);
    void deleteExceptionBreakpointsForSession(String sessionId);

    // Step request operations
    void saveStepRequest(StepRequest stepRequest);
    Optional<StepRequest> getPendingStepRequest(String sessionId);
    void deleteStepRequest(String id);
    void deleteStepRequestsForSession(String sessionId);

    @Override
    void close();
}


package dev.jdbg.server;

import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import dev.jdbg.grpc.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Manages debugging sessions with persistent JDI connections.
 */
public final class SessionManager {
    
    private final Map<String, DebugSession> sessions = new ConcurrentHashMap<>();
    private volatile String activeSessionId;
    
    public synchronized String createSession(final String requestedId) {
        final String id = requestedId != null && !requestedId.isEmpty() 
            ? requestedId 
            : UUID.randomUUID().toString().substring(0, 8);
        
        if (sessions.containsKey(id)) {
            throw new IllegalArgumentException("Session already exists: " + id);
        }
        
        return id;
    }
    
    public DebugSession attachRemote(final String sessionId, final String host, final int port, final int timeout) 
            throws IOException {
        final String id = createSession(sessionId);
        
        final VirtualMachineManager vmm = Bootstrap.virtualMachineManager();
        final AttachingConnector connector = findSocketAttachConnector(vmm);
        
        final Map<String, Connector.Argument> args = connector.defaultArguments();
        args.get("hostname").setValue(host);
        args.get("port").setValue(String.valueOf(port));
        if (args.containsKey("timeout")) {
            args.get("timeout").setValue(String.valueOf(timeout));
        }
        
        try {
            final VirtualMachine vm = connector.attach(args);
            final DebugSession session = new DebugSession(id, vm, SessionType.SESSION_TYPE_ATTACHED_REMOTE);
            session.setHost(host);
            session.setPort(port);
            sessions.put(id, session);
            
            if (activeSessionId == null) {
                activeSessionId = id;
            }
            
            return session;
        } catch (final IllegalConnectorArgumentsException e) {
            throw new IOException("Invalid connection arguments: " + e.getMessage(), e);
        }
    }
    
    public DebugSession attachLocal(final String sessionId, final int pid, final int timeout) 
            throws IOException {
        final String id = createSession(sessionId);
        
        final VirtualMachineManager vmm = Bootstrap.virtualMachineManager();
        final AttachingConnector connector = findProcessAttachConnector(vmm);
        
        final Map<String, Connector.Argument> args = connector.defaultArguments();
        args.get("pid").setValue(String.valueOf(pid));
        if (args.containsKey("timeout")) {
            args.get("timeout").setValue(String.valueOf(timeout));
        }
        
        try {
            final VirtualMachine vm = connector.attach(args);
            final DebugSession session = new DebugSession(id, vm, SessionType.SESSION_TYPE_ATTACHED_LOCAL);
            session.setPid(pid);
            sessions.put(id, session);
            
            if (activeSessionId == null) {
                activeSessionId = id;
            }
            
            return session;
        } catch (final IllegalConnectorArgumentsException e) {
            throw new IOException("Invalid connection arguments: " + e.getMessage(), e);
        }
    }
    
    public DebugSession getSession(final String sessionId) {
        final String id = sessionId != null && !sessionId.isEmpty() ? sessionId : activeSessionId;
        if (id == null) {
            throw new IllegalStateException("No active session");
        }
        
        final DebugSession session = sessions.get(id);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + id);
        }
        
        return session;
    }
    
    public Optional<DebugSession> getSessionOptional(final String sessionId) {
        final String id = sessionId != null && !sessionId.isEmpty() ? sessionId : activeSessionId;
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessions.get(id));
    }
    
    public List<DebugSession> listSessions() {
        return new ArrayList<>(sessions.values());
    }
    
    public String getActiveSessionId() {
        return activeSessionId;
    }
    
    public void setActiveSession(final String sessionId) {
        if (!sessions.containsKey(sessionId)) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        this.activeSessionId = sessionId;
    }
    
    public void removeSession(final String sessionId) {
        final DebugSession session = sessions.remove(sessionId);
        if (session != null) {
            session.close();
            
            if (sessionId.equals(activeSessionId)) {
                activeSessionId = sessions.isEmpty() ? null : sessions.keySet().iterator().next();
            }
        }
    }
    
    public void closeAll() {
        for (final DebugSession session : sessions.values()) {
            session.close();
        }
        sessions.clear();
        activeSessionId = null;
    }
    
    private static AttachingConnector findSocketAttachConnector(final VirtualMachineManager vmm) {
        for (final AttachingConnector connector : vmm.attachingConnectors()) {
            if (connector.name().contains("SocketAttach")) {
                return connector;
            }
        }
        throw new RuntimeException("Socket attach connector not found");
    }
    
    private static AttachingConnector findProcessAttachConnector(final VirtualMachineManager vmm) {
        for (final AttachingConnector connector : vmm.attachingConnectors()) {
            if (connector.name().contains("ProcessAttach")) {
                return connector;
            }
        }
        throw new RuntimeException("Process attach connector not found");
    }
}


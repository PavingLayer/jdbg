package dev.jdbg.model;

/**
 * Type of debugging session.
 */
public enum SessionType {
    /**
     * JVM launched by jdbg.
     */
    LAUNCHED,
    
    /**
     * Attached to local JVM via PID.
     */
    ATTACHED_LOCAL,
    
    /**
     * Attached to remote JVM via host/port.
     */
    ATTACHED_REMOTE
}


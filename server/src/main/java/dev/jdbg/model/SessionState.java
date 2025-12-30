package dev.jdbg.model;

/**
 * State of a debugging session.
 */
public enum SessionState {
    /**
     * Session created but not yet connected.
     */
    CREATED,
    
    /**
     * Session is connected to target JVM.
     */
    CONNECTED,
    
    /**
     * Target JVM is suspended.
     */
    SUSPENDED,
    
    /**
     * Target JVM is running.
     */
    RUNNING,
    
    /**
     * Session has been disconnected.
     */
    DISCONNECTED,
    
    /**
     * Target JVM has terminated.
     */
    TERMINATED
}


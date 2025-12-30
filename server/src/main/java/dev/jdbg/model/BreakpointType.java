package dev.jdbg.model;

/**
 * Type of breakpoint.
 */
public enum BreakpointType {
    /**
     * Breakpoint at a specific line.
     */
    LINE,
    
    /**
     * Breakpoint at method entry.
     */
    METHOD,
    
    /**
     * Breakpoint on exception.
     */
    EXCEPTION
}


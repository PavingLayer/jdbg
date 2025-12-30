package dev.jdbg.model;

/**
 * Depth of step operation.
 */
public enum StepDepth {
    /**
     * Step into method calls.
     */
    INTO,
    
    /**
     * Step over method calls.
     */
    OVER,
    
    /**
     * Step out of current method.
     */
    OUT
}


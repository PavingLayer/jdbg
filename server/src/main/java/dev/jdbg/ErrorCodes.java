package dev.jdbg;

/**
 * Standard error codes for JDBG.
 */
public final class ErrorCodes {
    
    private ErrorCodes() {
    }

    // Session errors
    public static final String SESSION_NOT_FOUND = "SESSION_NOT_FOUND";
    public static final String SESSION_NOT_CONNECTED = "SESSION_NOT_CONNECTED";
    public static final String SESSION_ALREADY_EXISTS = "SESSION_ALREADY_EXISTS";
    public static final String SESSION_TERMINATED = "SESSION_TERMINATED";
    public static final String NO_ACTIVE_SESSION = "NO_ACTIVE_SESSION";

    // Connection errors
    public static final String CONNECTION_FAILED = "CONNECTION_FAILED";
    public static final String CONNECTION_REFUSED = "CONNECTION_REFUSED";
    public static final String CONNECTION_TIMEOUT = "CONNECTION_TIMEOUT";
    public static final String VM_NOT_FOUND = "VM_NOT_FOUND";

    // Breakpoint errors
    public static final String BREAKPOINT_NOT_FOUND = "BREAKPOINT_NOT_FOUND";
    public static final String BREAKPOINT_ALREADY_EXISTS = "BREAKPOINT_ALREADY_EXISTS";
    public static final String INVALID_BREAKPOINT_LOCATION = "INVALID_BREAKPOINT_LOCATION";
    public static final String CLASS_NOT_FOUND = "CLASS_NOT_FOUND";

    // Thread errors
    public static final String THREAD_NOT_FOUND = "THREAD_NOT_FOUND";
    public static final String THREAD_NOT_SUSPENDED = "THREAD_NOT_SUSPENDED";
    public static final String NO_THREAD_SELECTED = "NO_THREAD_SELECTED";

    // Frame errors
    public static final String FRAME_NOT_FOUND = "FRAME_NOT_FOUND";
    public static final String NO_FRAME_SELECTED = "NO_FRAME_SELECTED";
    public static final String INVALID_FRAME_INDEX = "INVALID_FRAME_INDEX";

    // Variable errors
    public static final String VARIABLE_NOT_FOUND = "VARIABLE_NOT_FOUND";
    public static final String VARIABLE_NOT_WRITABLE = "VARIABLE_NOT_WRITABLE";
    public static final String INVALID_VALUE = "INVALID_VALUE";

    // Evaluation errors
    public static final String EVALUATION_FAILED = "EVALUATION_FAILED";
    public static final String INVALID_EXPRESSION = "INVALID_EXPRESSION";

    // Execution errors
    public static final String VM_NOT_RUNNING = "VM_NOT_RUNNING";
    public static final String VM_NOT_SUSPENDED = "VM_NOT_SUSPENDED";
    public static final String STEP_FAILED = "STEP_FAILED";

    // Storage errors
    public static final String STORAGE_ERROR = "STORAGE_ERROR";

    // General errors
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String INVALID_ARGUMENT = "INVALID_ARGUMENT";
}


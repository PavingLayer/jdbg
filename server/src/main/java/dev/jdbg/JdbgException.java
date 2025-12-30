package dev.jdbg;

/**
 * Base exception for JDBG errors.
 */
public class JdbgException extends RuntimeException {
    
    private final String code;

    public JdbgException(final String code, final String message) {
        super(message);
        this.code = code;
    }

    public JdbgException(final String code, final String message, final Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}


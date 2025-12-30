package dev.jdbg.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Wrapper for command results, supporting both success and error cases.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class JdbgResult<T> {
    
    private T data;
    private JdbgError error;

    private JdbgResult() {
    }

    public static <T> JdbgResult<T> success(final T data) {
        final JdbgResult<T> result = new JdbgResult<>();
        result.data = data;
        return result;
    }

    public static <T> JdbgResult<T> error(final String code, final String message) {
        final JdbgResult<T> result = new JdbgResult<>();
        result.error = new JdbgError(code, message);
        return result;
    }

    public static <T> JdbgResult<T> error(final String code, final String message, final String detail) {
        final JdbgResult<T> result = new JdbgResult<>();
        result.error = new JdbgError(code, message, detail);
        return result;
    }

    public static <T> JdbgResult<T> error(final JdbgError error) {
        final JdbgResult<T> result = new JdbgResult<>();
        result.error = error;
        return result;
    }

    public T getData() {
        return data;
    }

    public void setData(final T data) {
        this.data = data;
    }

    public JdbgError getError() {
        return error;
    }

    public void setError(final JdbgError error) {
        this.error = error;
    }

    public boolean isSuccess() {
        return error == null;
    }
}


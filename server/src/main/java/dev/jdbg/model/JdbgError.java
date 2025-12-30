package dev.jdbg.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Structured error representation for JSON output.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class JdbgError {
    
    private String code;
    private String message;
    private String detail;

    public JdbgError() {
    }

    public JdbgError(final String code, final String message) {
        this.code = code;
        this.message = message;
    }

    public JdbgError(final String code, final String message, final String detail) {
        this.code = code;
        this.message = message;
        this.detail = detail;
    }

    public String getCode() {
        return code;
    }

    public void setCode(final String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(final String detail) {
        this.detail = detail;
    }
}


package dev.jdbg.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Represents an exception breakpoint configuration.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ExceptionBreakpoint {
    
    private String id;
    private String sessionId;
    private String exceptionClassName;
    private boolean caught;
    private boolean uncaught;
    private boolean enabled;
    private Instant createdAt;

    public ExceptionBreakpoint() {
    }

    public ExceptionBreakpoint(final String id, final String sessionId, final String exceptionClassName) {
        this.id = id;
        this.sessionId = sessionId;
        this.exceptionClassName = exceptionClassName;
        this.caught = true;
        this.uncaught = true;
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    public String getExceptionClassName() {
        return exceptionClassName;
    }

    public void setExceptionClassName(final String exceptionClassName) {
        this.exceptionClassName = exceptionClassName;
    }

    public boolean isCaught() {
        return caught;
    }

    public void setCaught(final boolean caught) {
        this.caught = caught;
    }

    public boolean isUncaught() {
        return uncaught;
    }

    public void setUncaught(final boolean uncaught) {
        this.uncaught = uncaught;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }
}


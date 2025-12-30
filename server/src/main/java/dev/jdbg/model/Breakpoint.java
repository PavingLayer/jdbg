package dev.jdbg.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Represents a breakpoint declaration.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Breakpoint {
    
    private String id;
    private String sessionId;
    private BreakpointType type;
    private String className;
    private Integer lineNumber;
    private String methodName;
    private String condition;
    private boolean enabled;
    private int hitCount;
    private Instant createdAt;

    public Breakpoint() {
    }

    public Breakpoint(final String id, final String sessionId, final BreakpointType type) {
        this.id = id;
        this.sessionId = sessionId;
        this.type = type;
        this.enabled = true;
        this.hitCount = 0;
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

    public BreakpointType getType() {
        return type;
    }

    public void setType(final BreakpointType type) {
        this.type = type;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(final String className) {
        this.className = className;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(final Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(final String methodName) {
        this.methodName = methodName;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(final String condition) {
        this.condition = condition;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public int getHitCount() {
        return hitCount;
    }

    public void setHitCount(final int hitCount) {
        this.hitCount = hitCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void incrementHitCount() {
        this.hitCount++;
    }

    /**
     * Returns a human-readable location string for this breakpoint.
     */
    public String getLocationString() {
        return switch (type) {
            case LINE -> className + ":" + lineNumber;
            case METHOD -> className + "." + methodName;
            case EXCEPTION -> className;
        };
    }
}


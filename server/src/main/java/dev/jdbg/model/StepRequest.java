package dev.jdbg.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Represents a pending step request.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class StepRequest {
    
    private String id;
    private String sessionId;
    private String threadId;
    private StepType stepType;
    private StepDepth stepDepth;
    private Instant createdAt;

    public StepRequest() {
    }

    public StepRequest(final String id, final String sessionId, final String threadId,
                       final StepType stepType, final StepDepth stepDepth) {
        this.id = id;
        this.sessionId = sessionId;
        this.threadId = threadId;
        this.stepType = stepType;
        this.stepDepth = stepDepth;
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

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(final String threadId) {
        this.threadId = threadId;
    }

    public StepType getStepType() {
        return stepType;
    }

    public void setStepType(final StepType stepType) {
        this.stepType = stepType;
    }

    public StepDepth getStepDepth() {
        return stepDepth;
    }

    public void setStepDepth(final StepDepth stepDepth) {
        this.stepDepth = stepDepth;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }
}


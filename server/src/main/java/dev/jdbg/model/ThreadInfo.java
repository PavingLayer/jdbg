package dev.jdbg.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Information about a thread in the target JVM.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ThreadInfo {
    
    private long id;
    private String name;
    private String status;
    private boolean suspended;
    private boolean atBreakpoint;
    private int frameCount;
    private String threadGroup;

    public ThreadInfo() {
    }

    public long getId() {
        return id;
    }

    public void setId(final long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public void setSuspended(final boolean suspended) {
        this.suspended = suspended;
    }

    public boolean isAtBreakpoint() {
        return atBreakpoint;
    }

    public void setAtBreakpoint(final boolean atBreakpoint) {
        this.atBreakpoint = atBreakpoint;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public void setFrameCount(final int frameCount) {
        this.frameCount = frameCount;
    }

    public String getThreadGroup() {
        return threadGroup;
    }

    public void setThreadGroup(final String threadGroup) {
        this.threadGroup = threadGroup;
    }
}


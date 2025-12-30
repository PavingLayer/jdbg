package dev.jdbg.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Represents a debugging session with a target JVM.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Session {
    
    private String id;
    private SessionType type;
    private SessionState state;
    private String host;
    private Integer port;
    private Integer pid;
    private String mainClass;
    private String vmName;
    private String vmVersion;
    private Instant createdAt;
    private Instant lastAccessedAt;
    private String selectedThreadId;
    private Integer selectedFrameIndex;

    public Session() {
    }

    public Session(final String id, final SessionType type) {
        this.id = id;
        this.type = type;
        this.state = SessionState.CREATED;
        this.createdAt = Instant.now();
        this.lastAccessedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public SessionType getType() {
        return type;
    }

    public void setType(final SessionType type) {
        this.type = type;
    }

    public SessionState getState() {
        return state;
    }

    public void setState(final SessionState state) {
        this.state = state;
    }

    public String getHost() {
        return host;
    }

    public void setHost(final String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(final Integer port) {
        this.port = port;
    }

    public Integer getPid() {
        return pid;
    }

    public void setPid(final Integer pid) {
        this.pid = pid;
    }

    public String getMainClass() {
        return mainClass;
    }

    public void setMainClass(final String mainClass) {
        this.mainClass = mainClass;
    }

    public String getVmName() {
        return vmName;
    }

    public void setVmName(final String vmName) {
        this.vmName = vmName;
    }

    public String getVmVersion() {
        return vmVersion;
    }

    public void setVmVersion(final String vmVersion) {
        this.vmVersion = vmVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(final Instant lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public String getSelectedThreadId() {
        return selectedThreadId;
    }

    public void setSelectedThreadId(final String selectedThreadId) {
        this.selectedThreadId = selectedThreadId;
    }

    public Integer getSelectedFrameIndex() {
        return selectedFrameIndex;
    }

    public void setSelectedFrameIndex(final Integer selectedFrameIndex) {
        this.selectedFrameIndex = selectedFrameIndex;
    }

    public void touch() {
        this.lastAccessedAt = Instant.now();
    }
}


package dev.jdbg.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Information about a stack frame.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class FrameInfo {
    
    private int index;
    private String className;
    private String methodName;
    private String sourceName;
    private Integer lineNumber;
    private boolean isNative;

    public FrameInfo() {
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(final int index) {
        this.index = index;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(final String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(final String methodName) {
        this.methodName = methodName;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(final String sourceName) {
        this.sourceName = sourceName;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(final Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    public boolean isNative() {
        return isNative;
    }

    public void setNative(final boolean isNative) {
        this.isNative = isNative;
    }

    /**
     * Returns a human-readable location string.
     */
    public String getLocationString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(className).append(".").append(methodName);
        if (sourceName != null) {
            sb.append(" (").append(sourceName);
            if (lineNumber != null) {
                sb.append(":").append(lineNumber);
            }
            sb.append(")");
        } else if (isNative) {
            sb.append(" (Native Method)");
        }
        return sb.toString();
    }
}


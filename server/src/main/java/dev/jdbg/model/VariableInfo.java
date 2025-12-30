package dev.jdbg.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Information about a variable.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class VariableInfo {
    
    private String name;
    private String type;
    private String value;
    private boolean isLocal;
    private boolean isArgument;
    private boolean isField;
    private boolean isStatic;

    public VariableInfo() {
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(final String value) {
        this.value = value;
    }

    public boolean isLocal() {
        return isLocal;
    }

    public void setLocal(final boolean local) {
        isLocal = local;
    }

    public boolean isArgument() {
        return isArgument;
    }

    public void setArgument(final boolean argument) {
        isArgument = argument;
    }

    public boolean isField() {
        return isField;
    }

    public void setField(final boolean field) {
        isField = field;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public void setStatic(final boolean aStatic) {
        isStatic = aStatic;
    }
}


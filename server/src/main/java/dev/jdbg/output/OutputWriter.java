package dev.jdbg.output;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.jdbg.model.*;

import java.io.PrintStream;
import java.util.List;

/**
 * Handles output formatting for CLI commands.
 */
public final class OutputWriter {
    
    private final ObjectMapper mapper;
    private final PrintStream out;
    private final PrintStream err;
    private OutputFormat format = OutputFormat.JSON;

    public OutputWriter() {
        this(System.out, System.err);
    }

    public OutputWriter(final PrintStream out, final PrintStream err) {
        this.out = out;
        this.err = err;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void setFormat(final OutputFormat format) {
        this.format = format;
    }

    public OutputFormat getFormat() {
        return format;
    }

    public <T> void writeSuccess(final T data) {
        if (format == OutputFormat.JSON) {
            writeJson(JdbgResult.success(data));
        } else {
            writeText(data);
        }
    }

    public void writeError(final String code, final String message) {
        writeError(code, message, null);
    }

    public void writeError(final String code, final String message, final String detail) {
        if (format == OutputFormat.JSON) {
            writeJson(JdbgResult.error(code, message, detail));
        } else {
            err.println("Error [" + code + "]: " + message);
            if (detail != null) {
                err.println("Detail: " + detail);
            }
        }
    }

    private void writeJson(final Object obj) {
        try {
            out.println(mapper.writeValueAsString(obj));
        } catch (final JsonProcessingException e) {
            err.println("{\"error\":{\"code\":\"SERIALIZATION_ERROR\",\"message\":\"" + 
                e.getMessage().replace("\"", "\\\"") + "\"}}");
        }
    }

    private void writeText(final Object data) {
        if (data == null) {
            out.println("OK");
            return;
        }

        if (data instanceof Session session) {
            writeSessionText(session);
        } else if (data instanceof List<?> list && !list.isEmpty()) {
            writeListText(list);
        } else if (data instanceof Breakpoint bp) {
            writeBreakpointText(bp);
        } else if (data instanceof ThreadInfo thread) {
            writeThreadText(thread);
        } else if (data instanceof FrameInfo frame) {
            writeFrameText(frame);
        } else if (data instanceof VariableInfo var) {
            writeVariableText(var);
        } else if (data instanceof String str) {
            out.println(str);
        } else {
            // Fallback to JSON for unknown types
            writeJson(JdbgResult.success(data));
        }
    }

    private void writeSessionText(final Session session) {
        out.println("Session: " + session.getId());
        out.println("  Type: " + session.getType());
        out.println("  State: " + session.getState());
        if (session.getHost() != null) {
            out.println("  Host: " + session.getHost());
        }
        if (session.getPort() != null) {
            out.println("  Port: " + session.getPort());
        }
        if (session.getPid() != null) {
            out.println("  PID: " + session.getPid());
        }
        if (session.getMainClass() != null) {
            out.println("  Main Class: " + session.getMainClass());
        }
        if (session.getVmName() != null) {
            out.println("  VM: " + session.getVmName() + " " + session.getVmVersion());
        }
        out.println("  Created: " + session.getCreatedAt());
    }

    private void writeListText(final List<?> list) {
        final Object first = list.get(0);
        
        if (first instanceof Session) {
            out.println("Sessions:");
            for (final Object item : list) {
                final Session s = (Session) item;
                out.println("  " + s.getId() + " [" + s.getState() + "] " + s.getType());
            }
        } else if (first instanceof Breakpoint) {
            out.println("Breakpoints:");
            for (final Object item : list) {
                final Breakpoint bp = (Breakpoint) item;
                final String status = bp.isEnabled() ? "+" : "-";
                out.println("  [" + status + "] " + bp.getId() + " " + bp.getLocationString());
            }
        } else if (first instanceof ThreadInfo) {
            out.println("Threads:");
            for (final Object item : list) {
                final ThreadInfo t = (ThreadInfo) item;
                final String status = t.isSuspended() ? " (suspended)" : "";
                out.println("  " + t.getId() + " \"" + t.getName() + "\" " + t.getStatus() + status);
            }
        } else if (first instanceof FrameInfo) {
            out.println("Stack frames:");
            for (final Object item : list) {
                final FrameInfo f = (FrameInfo) item;
                out.println("  #" + f.getIndex() + " " + f.getLocationString());
            }
        } else if (first instanceof VariableInfo) {
            out.println("Variables:");
            for (final Object item : list) {
                final VariableInfo v = (VariableInfo) item;
                out.println("  " + v.getType() + " " + v.getName() + " = " + v.getValue());
            }
        } else {
            // Fallback
            for (final Object item : list) {
                out.println(item);
            }
        }
    }

    private void writeBreakpointText(final Breakpoint bp) {
        final String status = bp.isEnabled() ? "enabled" : "disabled";
        out.println("Breakpoint " + bp.getId() + " (" + status + ")");
        out.println("  Location: " + bp.getLocationString());
        out.println("  Type: " + bp.getType());
        out.println("  Hit count: " + bp.getHitCount());
    }

    private void writeThreadText(final ThreadInfo thread) {
        out.println("Thread " + thread.getId() + " \"" + thread.getName() + "\"");
        out.println("  Status: " + thread.getStatus());
        out.println("  Suspended: " + thread.isSuspended());
        if (thread.getFrameCount() >= 0) {
            out.println("  Frame count: " + thread.getFrameCount());
        }
    }

    private void writeFrameText(final FrameInfo frame) {
        out.println("Frame #" + frame.getIndex());
        out.println("  Location: " + frame.getLocationString());
    }

    private void writeVariableText(final VariableInfo var) {
        out.println(var.getType() + " " + var.getName() + " = " + var.getValue());
    }
}


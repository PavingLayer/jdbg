package dev.jdbg.jdi;

import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import dev.jdbg.ErrorCodes;
import dev.jdbg.JdbgException;
import dev.jdbg.model.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the JDI connection to a target JVM.
 */
public final class JdiConnection implements AutoCloseable {
    
    private final VirtualMachine vm;
    private final Map<String, BreakpointRequest> activeBreakpoints = new ConcurrentHashMap<>();
    private final Map<String, ExceptionRequest> activeExceptionRequests = new ConcurrentHashMap<>();

    private JdiConnection(final VirtualMachine vm) {
        this.vm = vm;
    }

    /**
     * Attach to a remote JVM via socket.
     */
    public static JdiConnection attachRemote(final String host, final int port, final int timeout) {
        final VirtualMachineManager vmm = Bootstrap.virtualMachineManager();
        final AttachingConnector connector = findSocketAttachConnector(vmm);
        
        final Map<String, Connector.Argument> args = connector.defaultArguments();
        args.get("hostname").setValue(host);
        args.get("port").setValue(String.valueOf(port));
        if (args.containsKey("timeout")) {
            args.get("timeout").setValue(String.valueOf(timeout));
        }
        
        try {
            final VirtualMachine vm = connector.attach(args);
            return new JdiConnection(vm);
        } catch (final IOException e) {
            throw new JdbgException(ErrorCodes.CONNECTION_FAILED, 
                "Failed to connect to " + host + ":" + port + ": " + e.getMessage(), e);
        } catch (final IllegalConnectorArgumentsException e) {
            throw new JdbgException(ErrorCodes.INVALID_ARGUMENT, 
                "Invalid connection arguments: " + e.getMessage(), e);
        }
    }

    /**
     * Attach to a local JVM via process ID.
     */
    public static JdiConnection attachLocal(final int pid, final int timeout) {
        final VirtualMachineManager vmm = Bootstrap.virtualMachineManager();
        final AttachingConnector connector = findProcessAttachConnector(vmm);
        
        final Map<String, Connector.Argument> args = connector.defaultArguments();
        args.get("pid").setValue(String.valueOf(pid));
        if (args.containsKey("timeout")) {
            args.get("timeout").setValue(String.valueOf(timeout));
        }
        
        try {
            final VirtualMachine vm = connector.attach(args);
            return new JdiConnection(vm);
        } catch (final IOException e) {
            throw new JdbgException(ErrorCodes.CONNECTION_FAILED, 
                "Failed to attach to PID " + pid + ": " + e.getMessage(), e);
        } catch (final IllegalConnectorArgumentsException e) {
            throw new JdbgException(ErrorCodes.INVALID_ARGUMENT, 
                "Invalid connection arguments: " + e.getMessage(), e);
        }
    }

    /**
     * Launch a new JVM with debugging enabled.
     */
    public static JdiConnection launch(final String mainClass, final String options, final String classpath) {
        final VirtualMachineManager vmm = Bootstrap.virtualMachineManager();
        final LaunchingConnector connector = vmm.defaultConnector();
        
        final Map<String, Connector.Argument> args = connector.defaultArguments();
        args.get("main").setValue(mainClass);
        if (options != null && !options.isEmpty() && args.containsKey("options")) {
            args.get("options").setValue(options);
        }
        
        try {
            final VirtualMachine vm = connector.launch(args);
            return new JdiConnection(vm);
        } catch (final IOException e) {
            throw new JdbgException(ErrorCodes.CONNECTION_FAILED, 
                "Failed to launch " + mainClass + ": " + e.getMessage(), e);
        } catch (final IllegalConnectorArgumentsException e) {
            throw new JdbgException(ErrorCodes.INVALID_ARGUMENT, 
                "Invalid launch arguments: " + e.getMessage(), e);
        } catch (final VMStartException e) {
            throw new JdbgException(ErrorCodes.CONNECTION_FAILED, 
                "VM failed to start: " + e.getMessage(), e);
        }
    }

    public VirtualMachine getVm() {
        return vm;
    }

    public String getVmName() {
        return vm.name();
    }

    public String getVmVersion() {
        return vm.version();
    }

    public boolean isConnected() {
        try {
            vm.allThreads();
            return true;
        } catch (final VMDisconnectedException e) {
            return false;
        }
    }

    /**
     * Suspend all threads in the VM.
     */
    public void suspendAll() {
        vm.suspend();
    }

    /**
     * Resume all threads in the VM.
     */
    public void resumeAll() {
        vm.resume();
    }

    /**
     * List all threads in the VM.
     */
    public List<ThreadInfo> listThreads() {
        final List<ThreadInfo> result = new ArrayList<>();
        for (final ThreadReference thread : vm.allThreads()) {
            result.add(mapThread(thread));
        }
        return result;
    }

    /**
     * Get a thread by ID.
     */
    public Optional<ThreadReference> getThread(final long threadId) {
        for (final ThreadReference thread : vm.allThreads()) {
            if (thread.uniqueID() == threadId) {
                return Optional.of(thread);
            }
        }
        return Optional.empty();
    }

    /**
     * Suspend a specific thread.
     */
    public void suspendThread(final long threadId) {
        final ThreadReference thread = getThread(threadId)
            .orElseThrow(() -> new JdbgException(ErrorCodes.THREAD_NOT_FOUND, 
                "Thread not found: " + threadId));
        thread.suspend();
    }

    /**
     * Resume a specific thread.
     */
    public void resumeThread(final long threadId) {
        final ThreadReference thread = getThread(threadId)
            .orElseThrow(() -> new JdbgException(ErrorCodes.THREAD_NOT_FOUND, 
                "Thread not found: " + threadId));
        thread.resume();
    }

    /**
     * Get stack frames for a thread.
     */
    public List<FrameInfo> getFrames(final long threadId) {
        final ThreadReference thread = getThread(threadId)
            .orElseThrow(() -> new JdbgException(ErrorCodes.THREAD_NOT_FOUND, 
                "Thread not found: " + threadId));
        
        try {
            final List<FrameInfo> result = new ArrayList<>();
            final List<StackFrame> frames = thread.frames();
            for (int i = 0; i < frames.size(); i++) {
                result.add(mapFrame(frames.get(i), i));
            }
            return result;
        } catch (final IncompatibleThreadStateException e) {
            throw new JdbgException(ErrorCodes.THREAD_NOT_SUSPENDED, 
                "Thread must be suspended to get frames", e);
        }
    }

    /**
     * Get a specific frame from a thread.
     */
    public StackFrame getFrame(final long threadId, final int frameIndex) {
        final ThreadReference thread = getThread(threadId)
            .orElseThrow(() -> new JdbgException(ErrorCodes.THREAD_NOT_FOUND, 
                "Thread not found: " + threadId));
        
        try {
            final List<StackFrame> frames = thread.frames();
            if (frameIndex < 0 || frameIndex >= frames.size()) {
                throw new JdbgException(ErrorCodes.INVALID_FRAME_INDEX, 
                    "Frame index out of range: " + frameIndex + " (0-" + (frames.size() - 1) + ")");
            }
            return frames.get(frameIndex);
        } catch (final IncompatibleThreadStateException e) {
            throw new JdbgException(ErrorCodes.THREAD_NOT_SUSPENDED, 
                "Thread must be suspended to get frame", e);
        }
    }

    /**
     * Get variables visible in a frame.
     */
    public List<VariableInfo> getVariables(final long threadId, final int frameIndex) {
        final StackFrame frame = getFrame(threadId, frameIndex);
        final List<VariableInfo> result = new ArrayList<>();
        
        try {
            // Get 'this' if available
            final ObjectReference thisObj = frame.thisObject();
            if (thisObj != null) {
                final VariableInfo thisVar = new VariableInfo();
                thisVar.setName("this");
                thisVar.setType(thisObj.type().name());
                thisVar.setValue(formatValue(thisObj));
                thisVar.setLocal(false);
                result.add(thisVar);
            }

            // Get local variables
            for (final LocalVariable var : frame.visibleVariables()) {
                final Value value = frame.getValue(var);
                final VariableInfo varInfo = new VariableInfo();
                varInfo.setName(var.name());
                varInfo.setType(var.typeName());
                varInfo.setValue(formatValue(value));
                varInfo.setLocal(true);
                varInfo.setArgument(var.isArgument());
                result.add(varInfo);
            }
        } catch (final AbsentInformationException e) {
            // No debug info available, return what we have
        }

        return result;
    }

    /**
     * Get a variable value.
     */
    public VariableInfo getVariable(final long threadId, final int frameIndex, final String name) {
        final StackFrame frame = getFrame(threadId, frameIndex);
        
        try {
            // Check for 'this'
            if ("this".equals(name)) {
                final ObjectReference thisObj = frame.thisObject();
                if (thisObj != null) {
                    final VariableInfo varInfo = new VariableInfo();
                    varInfo.setName("this");
                    varInfo.setType(thisObj.type().name());
                    varInfo.setValue(formatValue(thisObj));
                    varInfo.setLocal(false);
                    return varInfo;
                }
            }

            // Check local variables
            for (final LocalVariable var : frame.visibleVariables()) {
                if (var.name().equals(name)) {
                    final Value value = frame.getValue(var);
                    final VariableInfo varInfo = new VariableInfo();
                    varInfo.setName(var.name());
                    varInfo.setType(var.typeName());
                    varInfo.setValue(formatValue(value));
                    varInfo.setLocal(true);
                    varInfo.setArgument(var.isArgument());
                    return varInfo;
                }
            }

            // Check instance fields
            final ObjectReference thisObj = frame.thisObject();
            if (thisObj != null) {
                for (final Field field : thisObj.referenceType().allFields()) {
                    if (field.name().equals(name)) {
                        final Value value = thisObj.getValue(field);
                        final VariableInfo varInfo = new VariableInfo();
                        varInfo.setName(field.name());
                        varInfo.setType(field.typeName());
                        varInfo.setValue(formatValue(value));
                        varInfo.setField(true);
                        varInfo.setStatic(field.isStatic());
                        return varInfo;
                    }
                }
            }

        } catch (final AbsentInformationException e) {
            throw new JdbgException(ErrorCodes.VARIABLE_NOT_FOUND, 
                "No debug information available for variable: " + name, e);
        }

        throw new JdbgException(ErrorCodes.VARIABLE_NOT_FOUND, "Variable not found: " + name);
    }

    /**
     * Set a variable value.
     */
    public void setVariable(final long threadId, final int frameIndex, final String name, final String valueStr) {
        final StackFrame frame = getFrame(threadId, frameIndex);
        
        try {
            // Find the variable
            for (final LocalVariable var : frame.visibleVariables()) {
                if (var.name().equals(name)) {
                    final Value newValue = parseValue(var.type(), valueStr);
                    frame.setValue(var, newValue);
                    return;
                }
            }

            // Check instance fields
            final ObjectReference thisObj = frame.thisObject();
            if (thisObj != null) {
                for (final Field field : thisObj.referenceType().allFields()) {
                    if (field.name().equals(name) && !field.isFinal()) {
                        final Value newValue = parseValue(field.type(), valueStr);
                        thisObj.setValue(field, newValue);
                        return;
                    }
                }
            }

            throw new JdbgException(ErrorCodes.VARIABLE_NOT_FOUND, "Variable not found: " + name);
        } catch (final AbsentInformationException e) {
            throw new JdbgException(ErrorCodes.VARIABLE_NOT_FOUND, 
                "No debug information available", e);
        } catch (final InvalidTypeException | ClassNotLoadedException e) {
            throw new JdbgException(ErrorCodes.INVALID_VALUE, 
                "Invalid value for variable: " + e.getMessage(), e);
        }
    }

    /**
     * Add a line breakpoint.
     */
    public void addLineBreakpoint(final Breakpoint bp) {
        final List<ReferenceType> classes = vm.classesByName(bp.getClassName());
        
        if (classes.isEmpty()) {
            // Class not loaded yet, set up a deferred breakpoint via class prepare event
            setupDeferredBreakpoint(bp);
            return;
        }

        for (final ReferenceType refType : classes) {
            try {
                final List<Location> locations = refType.locationsOfLine(bp.getLineNumber());
                if (!locations.isEmpty()) {
                    final BreakpointRequest request = vm.eventRequestManager()
                        .createBreakpointRequest(locations.get(0));
                    request.setEnabled(bp.isEnabled());
                    request.putProperty("breakpointId", bp.getId());
                    activeBreakpoints.put(bp.getId(), request);
                }
            } catch (final AbsentInformationException e) {
                throw new JdbgException(ErrorCodes.INVALID_BREAKPOINT_LOCATION, 
                    "No line information for class: " + bp.getClassName(), e);
            }
        }
    }

    /**
     * Add a method breakpoint.
     */
    public void addMethodBreakpoint(final Breakpoint bp) {
        final List<ReferenceType> classes = vm.classesByName(bp.getClassName());
        
        if (classes.isEmpty()) {
            setupDeferredBreakpoint(bp);
            return;
        }

        for (final ReferenceType refType : classes) {
            for (final Method method : refType.methodsByName(bp.getMethodName())) {
                final Location location = method.location();
                if (location != null) {
                    final BreakpointRequest request = vm.eventRequestManager()
                        .createBreakpointRequest(location);
                    request.setEnabled(bp.isEnabled());
                    request.putProperty("breakpointId", bp.getId());
                    activeBreakpoints.put(bp.getId(), request);
                }
            }
        }
    }

    /**
     * Remove a breakpoint.
     */
    public void removeBreakpoint(final String breakpointId) {
        final BreakpointRequest request = activeBreakpoints.remove(breakpointId);
        if (request != null) {
            vm.eventRequestManager().deleteEventRequest(request);
        }
    }

    /**
     * Enable/disable a breakpoint.
     */
    public void setBreakpointEnabled(final String breakpointId, final boolean enabled) {
        final BreakpointRequest request = activeBreakpoints.get(breakpointId);
        if (request != null) {
            request.setEnabled(enabled);
        }
    }

    /**
     * Add an exception breakpoint.
     */
    public void addExceptionBreakpoint(final ExceptionBreakpoint eb) {
        final List<ReferenceType> classes = vm.classesByName(eb.getExceptionClassName());
        ReferenceType exceptionType = classes.isEmpty() ? null : classes.get(0);

        final ExceptionRequest request = vm.eventRequestManager()
            .createExceptionRequest(exceptionType, eb.isCaught(), eb.isUncaught());
        request.setEnabled(eb.isEnabled());
        request.putProperty("exceptionBreakpointId", eb.getId());
        activeExceptionRequests.put(eb.getId(), request);
    }

    /**
     * Remove an exception breakpoint.
     */
    public void removeExceptionBreakpoint(final String id) {
        final ExceptionRequest request = activeExceptionRequests.remove(id);
        if (request != null) {
            vm.eventRequestManager().deleteEventRequest(request);
        }
    }

    /**
     * Create a step request.
     */
    public void createStepRequest(final long threadId, final StepDepth depth, final StepType type) {
        final ThreadReference thread = getThread(threadId)
            .orElseThrow(() -> new JdbgException(ErrorCodes.THREAD_NOT_FOUND, 
                "Thread not found: " + threadId));

        // Delete any existing step requests for this thread
        for (final com.sun.jdi.request.StepRequest existing : vm.eventRequestManager().stepRequests()) {
            if (existing.thread().equals(thread)) {
                vm.eventRequestManager().deleteEventRequest(existing);
            }
        }

        final int jdiDepth = switch (depth) {
            case INTO -> com.sun.jdi.request.StepRequest.STEP_INTO;
            case OVER -> com.sun.jdi.request.StepRequest.STEP_OVER;
            case OUT -> com.sun.jdi.request.StepRequest.STEP_OUT;
        };

        final int jdiSize = switch (type) {
            case LINE -> com.sun.jdi.request.StepRequest.STEP_LINE;
            case MIN -> com.sun.jdi.request.StepRequest.STEP_MIN;
        };

        final com.sun.jdi.request.StepRequest request = vm.eventRequestManager()
            .createStepRequest(thread, jdiSize, jdiDepth);
        request.addCountFilter(1);
        request.setEnabled(true);
    }

    /**
     * Evaluate an expression in the context of a frame.
     */
    public String evaluate(final long threadId, final int frameIndex, final String expression) {
        // Note: JDI doesn't have built-in expression evaluation.
        // This is a simplified implementation that handles basic cases.
        // Full expression evaluation would require parsing and interpreting the expression.
        
        final StackFrame frame = getFrame(threadId, frameIndex);
        
        try {
            // Simple variable lookup
            if (expression.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
                final VariableInfo var = getVariable(threadId, frameIndex, expression);
                return var.getValue();
            }
            
            // Handle 'this.field' syntax
            if (expression.startsWith("this.")) {
                final String fieldName = expression.substring(5);
                final ObjectReference thisObj = frame.thisObject();
                if (thisObj != null) {
                    for (final Field field : thisObj.referenceType().allFields()) {
                        if (field.name().equals(fieldName)) {
                            return formatValue(thisObj.getValue(field));
                        }
                    }
                }
            }

            // For more complex expressions, we'd need a proper expression parser
            throw new JdbgException(ErrorCodes.EVALUATION_FAILED, 
                "Expression evaluation not supported: " + expression);
        } catch (final JdbgException e) {
            throw e;
        } catch (final Exception e) {
            throw new JdbgException(ErrorCodes.EVALUATION_FAILED, 
                "Failed to evaluate: " + e.getMessage(), e);
        }
    }

    /**
     * Wait for the next event from the VM.
     */
    public Optional<Event> waitForEvent(final long timeoutMs) {
        try {
            final EventQueue queue = vm.eventQueue();
            final EventSet eventSet = queue.remove(timeoutMs);
            if (eventSet != null) {
                for (final Event event : eventSet) {
                    return Optional.of(event);
                }
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final VMDisconnectedException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Dispose of the VM connection.
     */
    public void dispose() {
        try {
            vm.dispose();
        } catch (final VMDisconnectedException e) {
            // Already disconnected
        }
    }

    @Override
    public void close() {
        dispose();
    }

    private void setupDeferredBreakpoint(final Breakpoint bp) {
        // Set up class prepare request to install breakpoint when class loads
        final ClassPrepareRequest request = vm.eventRequestManager()
            .createClassPrepareRequest();
        request.addClassFilter(bp.getClassName());
        request.putProperty("deferredBreakpoint", bp);
        request.setEnabled(true);
    }

    private static AttachingConnector findSocketAttachConnector(final VirtualMachineManager vmm) {
        for (final AttachingConnector connector : vmm.attachingConnectors()) {
            if (connector.name().contains("SocketAttach")) {
                return connector;
            }
        }
        throw new JdbgException(ErrorCodes.INTERNAL_ERROR, "Socket attach connector not found");
    }

    private static AttachingConnector findProcessAttachConnector(final VirtualMachineManager vmm) {
        for (final AttachingConnector connector : vmm.attachingConnectors()) {
            if (connector.name().contains("ProcessAttach")) {
                return connector;
            }
        }
        throw new JdbgException(ErrorCodes.INTERNAL_ERROR, "Process attach connector not found");
    }

    private ThreadInfo mapThread(final ThreadReference thread) {
        final ThreadInfo info = new ThreadInfo();
        info.setId(thread.uniqueID());
        info.setName(thread.name());
        info.setSuspended(thread.isSuspended());
        info.setStatus(getThreadStatus(thread.status()));
        
        try {
            info.setFrameCount(thread.frameCount());
        } catch (final IncompatibleThreadStateException e) {
            info.setFrameCount(-1);
        }
        
        final ThreadGroupReference group = thread.threadGroup();
        if (group != null) {
            info.setThreadGroup(group.name());
        }
        
        return info;
    }

    private String getThreadStatus(final int status) {
        return switch (status) {
            case ThreadReference.THREAD_STATUS_UNKNOWN -> "UNKNOWN";
            case ThreadReference.THREAD_STATUS_ZOMBIE -> "ZOMBIE";
            case ThreadReference.THREAD_STATUS_RUNNING -> "RUNNING";
            case ThreadReference.THREAD_STATUS_SLEEPING -> "SLEEPING";
            case ThreadReference.THREAD_STATUS_MONITOR -> "MONITOR";
            case ThreadReference.THREAD_STATUS_WAIT -> "WAIT";
            case ThreadReference.THREAD_STATUS_NOT_STARTED -> "NOT_STARTED";
            default -> "UNKNOWN";
        };
    }

    private FrameInfo mapFrame(final StackFrame frame, final int index) {
        final FrameInfo info = new FrameInfo();
        info.setIndex(index);
        
        final Location location = frame.location();
        info.setClassName(location.declaringType().name());
        info.setMethodName(location.method().name());
        info.setNative(location.method().isNative());
        
        try {
            info.setSourceName(location.sourceName());
            info.setLineNumber(location.lineNumber());
        } catch (final AbsentInformationException e) {
            // No source info available
        }
        
        return info;
    }

    private String formatValue(final Value value) {
        if (value == null) {
            return "null";
        }
        
        if (value instanceof PrimitiveValue) {
            return value.toString();
        }
        
        if (value instanceof StringReference stringRef) {
            return "\"" + stringRef.value() + "\"";
        }
        
        if (value instanceof ArrayReference arrayRef) {
            return arrayRef.type().name() + "[" + arrayRef.length() + "]";
        }
        
        if (value instanceof ObjectReference objRef) {
            return objRef.type().name() + "@" + objRef.uniqueID();
        }
        
        return value.toString();
    }

    private Value parseValue(final Type type, final String valueStr) throws InvalidTypeException, ClassNotLoadedException {
        final String typeName = type.name();
        
        return switch (typeName) {
            case "boolean" -> vm.mirrorOf(Boolean.parseBoolean(valueStr));
            case "byte" -> vm.mirrorOf(Byte.parseByte(valueStr));
            case "char" -> vm.mirrorOf(valueStr.charAt(0));
            case "short" -> vm.mirrorOf(Short.parseShort(valueStr));
            case "int" -> vm.mirrorOf(Integer.parseInt(valueStr));
            case "long" -> vm.mirrorOf(Long.parseLong(valueStr));
            case "float" -> vm.mirrorOf(Float.parseFloat(valueStr));
            case "double" -> vm.mirrorOf(Double.parseDouble(valueStr));
            case "java.lang.String" -> vm.mirrorOf(valueStr);
            default -> {
                if ("null".equals(valueStr)) {
                    yield null;
                }
                throw new InvalidTypeException("Cannot parse value for type: " + typeName);
            }
        };
    }
}


package dev.jdbg.server;

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.StepRequest;
import dev.jdbg.grpc.*;

/**
 * Processes JDI events and dispatches them to listeners.
 */
public final class EventProcessor extends Thread {
    
    private final DebugSession session;
    private volatile boolean running = true;
    
    public EventProcessor(final DebugSession session) {
        super("EventProcessor-" + session.getId());
        setDaemon(true);
        this.session = session;
    }
    
    @Override
    public void run() {
        final VirtualMachine vm = session.getVm();
        final EventQueue queue = vm.eventQueue();
        
        while (running) {
            try {
                final EventSet eventSet = queue.remove(1000); // 1 second timeout
                if (eventSet == null) {
                    continue;
                }
                
                for (final Event event : eventSet) {
                    processEvent(event);
                }
                
                // Resume if needed
                if (shouldResume(eventSet)) {
                    eventSet.resume();
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (final VMDisconnectedException e) {
                session.setState(SessionState.SESSION_STATE_DISCONNECTED);
                
                final DebugEvent disconnectEvent = DebugEvent.newBuilder()
                    .setTimestamp(System.currentTimeMillis())
                    .setSessionId(session.getId())
                    .setVmDisconnect(VmDisconnectEvent.newBuilder()
                        .setReason("VM disconnected")
                        .build())
                    .build();
                session.dispatchEvent(disconnectEvent);
                break;
            }
        }
    }
    
    public void shutdown() {
        running = false;
        interrupt();
    }
    
    private void processEvent(final Event event) {
        if (event instanceof BreakpointEvent bpEvent) {
            handleBreakpointEvent(bpEvent);
        } else if (event instanceof StepEvent stepEvent) {
            handleStepEvent(stepEvent);
        } else if (event instanceof com.sun.jdi.event.ExceptionEvent exEvent) {
            handleExceptionEvent(exEvent);
        } else if (event instanceof com.sun.jdi.event.ThreadStartEvent tsEvent) {
            handleThreadStartEvent(tsEvent);
        } else if (event instanceof com.sun.jdi.event.ThreadDeathEvent tdEvent) {
            handleThreadDeathEvent(tdEvent);
        } else if (event instanceof VMDeathEvent vmDeathEvent) {
            handleVmDeathEvent(vmDeathEvent);
        } else if (event instanceof VMDisconnectEvent) {
            handleVmDisconnectEvent();
        } else if (event instanceof ClassPrepareEvent cpEvent) {
            handleClassPrepareEvent(cpEvent);
        }
    }
    
    private void handleBreakpointEvent(final BreakpointEvent event) {
        final String bpId = (String) event.request().getProperty("breakpointId");
        if (bpId == null) {
            return;
        }
        
        final ThreadReference thread = event.thread();
        final Location location = event.location();
        
        session.handleBreakpointHit(bpId, thread, location);
        
        final DebugEvent debugEvent = DebugEvent.newBuilder()
            .setTimestamp(System.currentTimeMillis())
            .setSessionId(session.getId())
            .setBreakpointHit(BreakpointHitEvent.newBuilder()
                .setBreakpointId(bpId)
                .setLocation(buildThreadLocation(thread, location))
                .build())
            .build();
        session.dispatchEvent(debugEvent);
    }
    
    private void handleStepEvent(final StepEvent event) {
        final ThreadReference thread = event.thread();
        final Location location = event.location();
        
        // Auto-select thread
        session.setSelectedThreadId(thread.uniqueID());
        session.setSelectedFrameIndex(0);
        session.setState(SessionState.SESSION_STATE_SUSPENDED);
        
        // Delete the step request
        session.getVm().eventRequestManager().deleteEventRequest(event.request());
        
        final DebugEvent debugEvent = DebugEvent.newBuilder()
            .setTimestamp(System.currentTimeMillis())
            .setSessionId(session.getId())
            .setStepCompleted(StepCompletedEvent.newBuilder()
                .setLocation(buildThreadLocation(thread, location))
                .build())
            .build();
        session.dispatchEvent(debugEvent);
    }
    
    private void handleExceptionEvent(final com.sun.jdi.event.ExceptionEvent event) {
        final ThreadReference thread = event.thread();
        final Location location = event.catchLocation();
        final ObjectReference exception = event.exception();
        
        String message = "";
        try {
            // Try to get the exception message
            final Field messageField = exception.referenceType().fieldByName("detailMessage");
            if (messageField != null) {
                final Value msgValue = exception.getValue(messageField);
                if (msgValue instanceof StringReference strRef) {
                    message = strRef.value();
                }
            }
        } catch (final Exception e) {
            // Ignore
        }
        
        session.setSelectedThreadId(thread.uniqueID());
        session.setSelectedFrameIndex(0);
        session.setState(SessionState.SESSION_STATE_SUSPENDED);
        
        final DebugEvent debugEvent = DebugEvent.newBuilder()
            .setTimestamp(System.currentTimeMillis())
            .setSessionId(session.getId())
            .setException(dev.jdbg.grpc.ExceptionEvent.newBuilder()
                .setExceptionClass(exception.referenceType().name())
                .setMessage(message)
                .setCaught(event.catchLocation() != null)
                .setLocation(buildThreadLocation(thread, location != null ? location : event.location()))
                .build())
            .build();
        session.dispatchEvent(debugEvent);
    }
    
    private void handleThreadStartEvent(final com.sun.jdi.event.ThreadStartEvent event) {
        final ThreadReference thread = event.thread();
        
        final DebugEvent debugEvent = DebugEvent.newBuilder()
            .setTimestamp(System.currentTimeMillis())
            .setSessionId(session.getId())
            .setThreadStart(dev.jdbg.grpc.ThreadStartEvent.newBuilder()
                .setThread(buildThreadInfo(thread))
                .build())
            .build();
        session.dispatchEvent(debugEvent);
    }
    
    private void handleThreadDeathEvent(final com.sun.jdi.event.ThreadDeathEvent event) {
        final ThreadReference thread = event.thread();
        
        final DebugEvent debugEvent = DebugEvent.newBuilder()
            .setTimestamp(System.currentTimeMillis())
            .setSessionId(session.getId())
            .setThreadDeath(dev.jdbg.grpc.ThreadDeathEvent.newBuilder()
                .setThreadId(thread.uniqueID())
                .setThreadName(thread.name())
                .build())
            .build();
        session.dispatchEvent(debugEvent);
    }
    
    private void handleVmDeathEvent(final VMDeathEvent event) {
        session.setState(SessionState.SESSION_STATE_TERMINATED);
        
        final DebugEvent debugEvent = DebugEvent.newBuilder()
            .setTimestamp(System.currentTimeMillis())
            .setSessionId(session.getId())
            .setVmDeath(dev.jdbg.grpc.VmDeathEvent.newBuilder()
                .setExitCode(0) // JDI doesn't provide exit code in VMDeathEvent
                .build())
            .build();
        session.dispatchEvent(debugEvent);
    }
    
    private void handleVmDisconnectEvent() {
        session.setState(SessionState.SESSION_STATE_DISCONNECTED);
        
        final DebugEvent debugEvent = DebugEvent.newBuilder()
            .setTimestamp(System.currentTimeMillis())
            .setSessionId(session.getId())
            .setVmDisconnect(VmDisconnectEvent.newBuilder()
                .setReason("VM disconnected")
                .build())
            .build();
        session.dispatchEvent(debugEvent);
    }
    
    private void handleClassPrepareEvent(final ClassPrepareEvent event) {
        // Handle deferred breakpoints
        final String bpId = (String) event.request().getProperty("deferredBreakpointId");
        if (bpId == null) {
            return;
        }
        
        final ReferenceType refType = event.referenceType();
        final Integer lineNumber = (Integer) event.request().getProperty("lineNumber");
        final String methodName = (String) event.request().getProperty("methodName");
        
        try {
            if (lineNumber != null && lineNumber > 0) {
                final var locations = refType.locationsOfLine(lineNumber);
                if (!locations.isEmpty()) {
                    final var bpRequest = session.getVm().eventRequestManager()
                        .createBreakpointRequest(locations.get(0));
                    bpRequest.putProperty("breakpointId", bpId);
                    bpRequest.setEnabled(true);
                }
            } else if (methodName != null) {
                for (final Method method : refType.methodsByName(methodName)) {
                    final Location location = method.location();
                    if (location != null) {
                        final var bpRequest = session.getVm().eventRequestManager()
                            .createBreakpointRequest(location);
                        bpRequest.putProperty("breakpointId", bpId);
                        bpRequest.setEnabled(true);
                    }
                }
            }
        } catch (final AbsentInformationException e) {
            // Ignore
        }
        
        // Remove the class prepare request
        session.getVm().eventRequestManager().deleteEventRequest(event.request());
    }
    
    private boolean shouldResume(final EventSet eventSet) {
        for (final Event event : eventSet) {
            if (event instanceof BreakpointEvent ||
                event instanceof StepEvent ||
                event instanceof com.sun.jdi.event.ExceptionEvent) {
                return false; // Don't auto-resume for these
            }
        }
        return true;
    }
    
    private ThreadLocation buildThreadLocation(final ThreadReference thread, final Location location) {
        final ThreadLocation.Builder builder = ThreadLocation.newBuilder()
            .setThreadId(thread.uniqueID())
            .setThreadName(thread.name());
        
        if (location != null) {
            builder.setClassName(location.declaringType().name())
                .setMethodName(location.method().name())
                .setLineNumber(location.lineNumber());
            
            try {
                builder.setSourceName(location.sourceName());
            } catch (final AbsentInformationException e) {
                // Ignore
            }
        }
        
        return builder.build();
    }
    
    private ThreadInfo buildThreadInfo(final ThreadReference thread) {
        final ThreadInfo.Builder builder = ThreadInfo.newBuilder()
            .setId(thread.uniqueID())
            .setName(thread.name())
            .setSuspended(thread.isSuspended())
            .setStatus(mapThreadStatus(thread.status()));
        
        final ThreadGroupReference group = thread.threadGroup();
        if (group != null) {
            builder.setThreadGroup(group.name());
        }
        
        try {
            builder.setFrameCount(thread.frameCount());
        } catch (final IncompatibleThreadStateException e) {
            builder.setFrameCount(-1);
        }
        
        return builder.build();
    }
    
    private ThreadStatus mapThreadStatus(final int status) {
        return switch (status) {
            case ThreadReference.THREAD_STATUS_UNKNOWN -> ThreadStatus.THREAD_STATUS_UNKNOWN;
            case ThreadReference.THREAD_STATUS_ZOMBIE -> ThreadStatus.THREAD_STATUS_ZOMBIE;
            case ThreadReference.THREAD_STATUS_RUNNING -> ThreadStatus.THREAD_STATUS_RUNNING;
            case ThreadReference.THREAD_STATUS_SLEEPING -> ThreadStatus.THREAD_STATUS_SLEEPING;
            case ThreadReference.THREAD_STATUS_MONITOR -> ThreadStatus.THREAD_STATUS_MONITOR;
            case ThreadReference.THREAD_STATUS_WAIT -> ThreadStatus.THREAD_STATUS_WAIT;
            case ThreadReference.THREAD_STATUS_NOT_STARTED -> ThreadStatus.THREAD_STATUS_NOT_STARTED;
            default -> ThreadStatus.THREAD_STATUS_UNKNOWN;
        };
    }
}


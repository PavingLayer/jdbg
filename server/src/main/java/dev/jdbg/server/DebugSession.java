package dev.jdbg.server;

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import dev.jdbg.grpc.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Represents a debugging session with a persistent JDI connection.
 */
public final class DebugSession {
    
    private static final int DEFAULT_EVENT_BUFFER_CAPACITY = 1000;
    
    private final String id;
    private final VirtualMachine vm;
    private final SessionType type;
    private final Instant createdAt;
    private final Map<String, BreakpointRequest> breakpoints = new ConcurrentHashMap<>();
    private final Map<String, ExceptionRequest> exceptionBreakpoints = new ConcurrentHashMap<>();
    private final Map<String, BreakpointInfo> breakpointInfos = new ConcurrentHashMap<>();
    private final Map<String, ExceptionBreakpointInfo> exceptionInfos = new ConcurrentHashMap<>();
    private final List<Consumer<DebugEvent>> eventListeners = new CopyOnWriteArrayList<>();
    private final EventProcessor eventProcessor;
    
    // Event buffer for non-blocking event retrieval
    private final LinkedBlockingDeque<DebugEvent> eventBuffer = new LinkedBlockingDeque<>(DEFAULT_EVENT_BUFFER_CAPACITY);
    private final AtomicLong eventSequence = new AtomicLong(0);
    private volatile boolean eventsDropped = false;
    
    private String name;
    private String host;
    private int port;
    private int pid;
    private volatile long selectedThreadId;
    private volatile int selectedFrameIndex;
    private volatile SessionState state = SessionState.SESSION_STATE_CONNECTED;
    
    // Track which threads hit which breakpoints
    private final Map<Long, String> threadsAtBreakpoints = new ConcurrentHashMap<>();
    
    public DebugSession(final String id, final VirtualMachine vm, final SessionType type) {
        this.id = id;
        this.vm = vm;
        this.type = type;
        this.createdAt = Instant.now();
        
        // Start event processor thread
        this.eventProcessor = new EventProcessor(this);
        this.eventProcessor.start();
    }
    
    public String getId() {
        return id;
    }
    
    public VirtualMachine getVm() {
        return vm;
    }
    
    public SessionType getType() {
        return type;
    }
    
    public SessionState getState() {
        return state;
    }
    
    public void setState(final SessionState state) {
        this.state = state;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(final String name) {
        this.name = name;
    }
    
    public String getHost() {
        return host;
    }
    
    public void setHost(final String host) {
        this.host = host;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(final int port) {
        this.port = port;
    }
    
    public int getPid() {
        return pid;
    }
    
    public void setPid(final int pid) {
        this.pid = pid;
    }
    
    public long getSelectedThreadId() {
        return selectedThreadId;
    }
    
    public void setSelectedThreadId(final long threadId) {
        this.selectedThreadId = threadId;
    }
    
    public int getSelectedFrameIndex() {
        return selectedFrameIndex;
    }
    
    public void setSelectedFrameIndex(final int frameIndex) {
        this.selectedFrameIndex = frameIndex;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    // Thread operations
    public List<ThreadReference> getThreads() {
        return vm.allThreads();
    }
    
    public Optional<ThreadReference> getThread(final long threadId) {
        if (threadId == 0) {
            // Use selected thread
            return getThread(selectedThreadId);
        }
        for (final ThreadReference thread : vm.allThreads()) {
            if (thread.uniqueID() == threadId) {
                return Optional.of(thread);
            }
        }
        return Optional.empty();
    }
    
    public ThreadReference getSelectedThread() {
        return getThread(selectedThreadId)
            .orElseThrow(() -> new IllegalStateException("No thread selected"));
    }
    
    // Breakpoint operations
    public String addLineBreakpoint(final String className, final int lineNumber, final String condition) {
        final String bpId = UUID.randomUUID().toString().substring(0, 8);
        
        final List<ReferenceType> classes = vm.classesByName(className);
        if (classes.isEmpty()) {
            // Deferred breakpoint
            setupDeferredBreakpoint(bpId, className, lineNumber, null, condition);
        } else {
            for (final ReferenceType refType : classes) {
                try {
                    final List<Location> locations = refType.locationsOfLine(lineNumber);
                    if (!locations.isEmpty()) {
                        final BreakpointRequest request = vm.eventRequestManager()
                            .createBreakpointRequest(locations.get(0));
                        request.putProperty("breakpointId", bpId);
                        request.setEnabled(true);
                        breakpoints.put(bpId, request);
                    }
                } catch (final AbsentInformationException e) {
                    // Ignore
                }
            }
        }
        
        final BreakpointInfo info = new BreakpointInfo(bpId, className, lineNumber, null, condition);
        breakpointInfos.put(bpId, info);
        
        return bpId;
    }
    
    public String addMethodBreakpoint(final String className, final String methodName, final String condition) {
        final String bpId = UUID.randomUUID().toString().substring(0, 8);
        
        final List<ReferenceType> classes = vm.classesByName(className);
        if (classes.isEmpty()) {
            setupDeferredBreakpoint(bpId, className, 0, methodName, condition);
        } else {
            for (final ReferenceType refType : classes) {
                for (final Method method : refType.methodsByName(methodName)) {
                    final Location location = method.location();
                    if (location != null) {
                        final BreakpointRequest request = vm.eventRequestManager()
                            .createBreakpointRequest(location);
                        request.putProperty("breakpointId", bpId);
                        request.setEnabled(true);
                        breakpoints.put(bpId, request);
                    }
                }
            }
        }
        
        final BreakpointInfo info = new BreakpointInfo(bpId, className, 0, methodName, condition);
        breakpointInfos.put(bpId, info);
        
        return bpId;
    }
    
    private void setupDeferredBreakpoint(final String bpId, final String className, 
            final int lineNumber, final String methodName, final String condition) {
        final ClassPrepareRequest request = vm.eventRequestManager().createClassPrepareRequest();
        request.addClassFilter(className);
        request.putProperty("deferredBreakpointId", bpId);
        request.putProperty("lineNumber", lineNumber);
        request.putProperty("methodName", methodName);
        request.putProperty("condition", condition);
        request.setEnabled(true);
    }
    
    public void removeBreakpoint(final String bpId) {
        final BreakpointRequest request = breakpoints.remove(bpId);
        if (request != null) {
            vm.eventRequestManager().deleteEventRequest(request);
        }
        breakpointInfos.remove(bpId);
    }
    
    public void setBreakpointEnabled(final String bpId, final boolean enabled) {
        final BreakpointRequest request = breakpoints.get(bpId);
        if (request != null) {
            request.setEnabled(enabled);
        }
        final BreakpointInfo info = breakpointInfos.get(bpId);
        if (info != null) {
            info.setEnabled(enabled);
        }
    }
    
    public Map<String, BreakpointInfo> getBreakpointInfos() {
        return Collections.unmodifiableMap(breakpointInfos);
    }
    
    public void clearBreakpoints() {
        for (final BreakpointRequest request : breakpoints.values()) {
            vm.eventRequestManager().deleteEventRequest(request);
        }
        breakpoints.clear();
        breakpointInfos.clear();
    }
    
    // Exception breakpoint operations
    public String addExceptionBreakpoint(final String exceptionClassName, final boolean caught, final boolean uncaught) {
        final String id = UUID.randomUUID().toString().substring(0, 8);
        
        ReferenceType exceptionType = null;
        final List<ReferenceType> classes = vm.classesByName(exceptionClassName);
        if (!classes.isEmpty()) {
            exceptionType = classes.get(0);
        }
        
        final ExceptionRequest request = vm.eventRequestManager()
            .createExceptionRequest(exceptionType, caught, uncaught);
        request.putProperty("exceptionBreakpointId", id);
        request.setEnabled(true);
        exceptionBreakpoints.put(id, request);
        
        final ExceptionBreakpointInfo info = new ExceptionBreakpointInfo(id, exceptionClassName, caught, uncaught);
        exceptionInfos.put(id, info);
        
        return id;
    }
    
    public void removeExceptionBreakpoint(final String id) {
        final ExceptionRequest request = exceptionBreakpoints.remove(id);
        if (request != null) {
            vm.eventRequestManager().deleteEventRequest(request);
        }
        exceptionInfos.remove(id);
    }
    
    public Map<String, ExceptionBreakpointInfo> getExceptionBreakpointInfos() {
        return Collections.unmodifiableMap(exceptionInfos);
    }
    
    // Execution control
    public void resumeAll() {
        vm.resume();
        state = SessionState.SESSION_STATE_RUNNING;
    }
    
    public void suspendAll() {
        vm.suspend();
        state = SessionState.SESSION_STATE_SUSPENDED;
    }
    
    public void step(final long threadId, final int depth, final int size) {
        final ThreadReference thread = getThread(threadId)
            .orElseThrow(() -> new IllegalArgumentException("Thread not found: " + threadId));
        
        // Remove existing step requests for this thread
        for (final com.sun.jdi.request.StepRequest existing : vm.eventRequestManager().stepRequests()) {
            if (existing.thread().equals(thread)) {
                vm.eventRequestManager().deleteEventRequest(existing);
            }
        }
        
        final com.sun.jdi.request.StepRequest request = vm.eventRequestManager().createStepRequest(thread, size, depth);
        request.addCountFilter(1);
        request.setEnabled(true);
        
        vm.resume();
    }
    
    // Event handling
    public void addEventListener(final Consumer<DebugEvent> listener) {
        eventListeners.add(listener);
    }
    
    public void removeEventListener(final Consumer<DebugEvent> listener) {
        eventListeners.remove(listener);
    }
    
    void dispatchEvent(final DebugEvent event) {
        // Add sequence number to event
        final DebugEvent sequencedEvent = event.toBuilder()
            .setSequenceNumber(eventSequence.incrementAndGet())
            .build();
        
        // Buffer the event for polling
        if (!eventBuffer.offerLast(sequencedEvent)) {
            // Buffer full, remove oldest event
            eventsDropped = true;
            eventBuffer.pollFirst();
            eventBuffer.offerLast(sequencedEvent);
        }
        
        // Notify streaming listeners
        for (final Consumer<DebugEvent> listener : eventListeners) {
            try {
                listener.accept(sequencedEvent);
            } catch (final Exception e) {
                // Log but don't propagate
                e.printStackTrace();
            }
        }
    }
    
    // Non-blocking event operations
    
    /**
     * Poll for events without blocking.
     * @param limit Maximum events to return (0 = all buffered)
     * @param eventTypes Filter by event types (empty = all types)
     * @return List of events
     */
    public List<DebugEvent> pollEvents(final int limit, final List<String> eventTypes) {
        final List<DebugEvent> result = new ArrayList<>();
        final int maxEvents = limit > 0 ? limit : Integer.MAX_VALUE;
        
        while (result.size() < maxEvents) {
            final DebugEvent event = eventBuffer.pollFirst();
            if (event == null) {
                break;
            }
            if (eventTypes.isEmpty() || eventTypes.contains(event.getEventType())) {
                result.add(event);
            }
        }
        
        return result;
    }
    
    /**
     * Wait for events with timeout.
     * @param timeoutMs Maximum time to wait in milliseconds (0 = use default timeout)
     * @param eventTypes Filter by event types (empty = all types)
     * @return List containing the event(s), or empty if timeout
     */
    public List<DebugEvent> waitForEvent(final int timeoutMs, final List<String> eventTypes) throws InterruptedException {
        final int effectiveTimeout = timeoutMs > 0 ? timeoutMs : 30000; // Default 30s max
        final long deadline = System.currentTimeMillis() + effectiveTimeout;
        
        while (System.currentTimeMillis() < deadline) {
            final long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                break;
            }
            
            final DebugEvent event = eventBuffer.pollFirst(remaining, TimeUnit.MILLISECONDS);
            if (event != null) {
                if (eventTypes.isEmpty() || eventTypes.contains(event.getEventType())) {
                    return List.of(event);
                }
                // Event didn't match filter, keep waiting
            }
        }
        
        return List.of();
    }
    
    /**
     * Clear all buffered events.
     */
    public void clearEvents() {
        eventBuffer.clear();
        eventsDropped = false;
    }
    
    /**
     * Get event buffer info.
     */
    public EventInfoResponse getEventInfo() {
        final EventInfoResponse.Builder builder = EventInfoResponse.newBuilder()
            .setBufferedCount(eventBuffer.size())
            .setBufferCapacity(DEFAULT_EVENT_BUFFER_CAPACITY)
            .setEventsDropped(eventsDropped);
        
        // Get oldest/newest timestamps if buffer not empty
        final DebugEvent oldest = eventBuffer.peekFirst();
        final DebugEvent newest = eventBuffer.peekLast();
        
        if (oldest != null) {
            builder.setOldestEventTimestamp(oldest.getTimestamp());
        }
        if (newest != null) {
            builder.setNewestEventTimestamp(newest.getTimestamp());
        }
        
        return builder.build();
    }
    
    /**
     * Get count of buffered events.
     */
    public int getEventBufferSize() {
        return eventBuffer.size();
    }
    
    void handleBreakpointHit(final String bpId, final ThreadReference thread, final Location location) {
        final BreakpointInfo info = breakpointInfos.get(bpId);
        if (info != null) {
            info.incrementHitCount();
        }
        
        // Track this thread as being at a breakpoint
        threadsAtBreakpoints.put(thread.uniqueID(), bpId);
        
        // Auto-select the thread
        selectedThreadId = thread.uniqueID();
        selectedFrameIndex = 0;
        state = SessionState.SESSION_STATE_SUSPENDED;
    }
    
    /**
     * Check if a thread is suspended at a breakpoint.
     */
    public Optional<String> getBreakpointForThread(final long threadId) {
        return Optional.ofNullable(threadsAtBreakpoints.get(threadId));
    }
    
    /**
     * Clear breakpoint tracking for a thread (called when resumed).
     */
    public void clearBreakpointForThread(final long threadId) {
        threadsAtBreakpoints.remove(threadId);
    }
    
    /**
     * Get all threads currently at breakpoints.
     */
    public Map<Long, String> getThreadsAtBreakpoints() {
        return Collections.unmodifiableMap(threadsAtBreakpoints);
    }
    
    public void close() {
        eventProcessor.shutdown();
        try {
            vm.dispose();
        } catch (final VMDisconnectedException e) {
            // Already disconnected
        }
        state = SessionState.SESSION_STATE_DISCONNECTED;
    }
    
    public Session toProto() {
        return Session.newBuilder()
            .setId(id)
            .setName(name != null ? name : "")
            .setType(type)
            .setState(state)
            .setHost(host != null ? host : "")
            .setPort(port)
            .setPid(pid)
            .setVmName(vm.name())
            .setVmVersion(vm.version())
            .setCreatedAt(createdAt.toEpochMilli())
            .setLastAccessedAt(System.currentTimeMillis())
            .setSelectedThreadId(selectedThreadId)
            .setSelectedFrameIndex(selectedFrameIndex)
            .build();
    }
    
    // Inner classes for breakpoint info
    public static final class BreakpointInfo {
        private final String id;
        private final String className;
        private final int lineNumber;
        private final String methodName;
        private final String condition;
        private boolean enabled = true;
        private int hitCount = 0;
        private final Instant createdAt = Instant.now();
        
        public BreakpointInfo(final String id, final String className, final int lineNumber, 
                              final String methodName, final String condition) {
            this.id = id;
            this.className = className;
            this.lineNumber = lineNumber;
            this.methodName = methodName;
            this.condition = condition;
        }
        
        public String getId() { return id; }
        public String getClassName() { return className; }
        public int getLineNumber() { return lineNumber; }
        public String getMethodName() { return methodName; }
        public String getCondition() { return condition; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getHitCount() { return hitCount; }
        public void incrementHitCount() { hitCount++; }
        public Instant getCreatedAt() { return createdAt; }
        
        public BreakpointType getType() {
            return methodName != null ? BreakpointType.BREAKPOINT_TYPE_METHOD : BreakpointType.BREAKPOINT_TYPE_LINE;
        }
        
        public String getLocationString() {
            if (methodName != null) {
                return className + "." + methodName;
            }
            return className + ":" + lineNumber;
        }
        
        public Breakpoint toProto(final String sessionId) {
            return Breakpoint.newBuilder()
                .setId(id)
                .setSessionId(sessionId)
                .setType(getType())
                .setClassName(className)
                .setLineNumber(lineNumber)
                .setMethodName(methodName != null ? methodName : "")
                .setCondition(condition != null ? condition : "")
                .setEnabled(enabled)
                .setHitCount(hitCount)
                .setCreatedAt(createdAt.toEpochMilli())
                .setLocationString(getLocationString())
                .build();
        }
    }
    
    public static final class ExceptionBreakpointInfo {
        private final String id;
        private final String exceptionClassName;
        private final boolean caught;
        private final boolean uncaught;
        private boolean enabled = true;
        private final Instant createdAt = Instant.now();
        
        public ExceptionBreakpointInfo(final String id, final String exceptionClassName, 
                                       final boolean caught, final boolean uncaught) {
            this.id = id;
            this.exceptionClassName = exceptionClassName;
            this.caught = caught;
            this.uncaught = uncaught;
        }
        
        public String getId() { return id; }
        public String getExceptionClassName() { return exceptionClassName; }
        public boolean isCaught() { return caught; }
        public boolean isUncaught() { return uncaught; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Instant getCreatedAt() { return createdAt; }
        
        public ExceptionBreakpoint toProto(final String sessionId) {
            return ExceptionBreakpoint.newBuilder()
                .setId(id)
                .setSessionId(sessionId)
                .setExceptionClass(exceptionClassName)
                .setCaught(caught)
                .setUncaught(uncaught)
                .setEnabled(enabled)
                .setCreatedAt(createdAt.toEpochMilli())
                .build();
        }
    }
}


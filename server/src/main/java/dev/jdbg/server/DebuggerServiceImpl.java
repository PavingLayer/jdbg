package dev.jdbg.server;

import com.sun.jdi.*;
import com.sun.jdi.request.StepRequest;
import dev.jdbg.grpc.*;
import dev.jdbg.server.eval.ExpressionParser;
import dev.jdbg.server.eval.JdiInterpreter;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.*;

/**
 * Implementation of the DebuggerService gRPC service.
 */
public final class DebuggerServiceImpl extends DebuggerServiceGrpc.DebuggerServiceImplBase {
    
    private final SessionManager sessionManager;
    
    public DebuggerServiceImpl(final SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
    
    @Override
    public void attachSession(final AttachRequest request, final StreamObserver<SessionResponse> responseObserver) {
        try {
            final DebugSession session;
            if (request.hasRemote()) {
                final RemoteTarget remote = request.getRemote();
                session = sessionManager.attachRemote(
                    request.getSessionId(), 
                    remote.getHost(), 
                    remote.getPort(), 
                    remote.getTimeoutMs()
                );
            } else if (request.hasLocal()) {
                final LocalTarget local = request.getLocal();
                session = sessionManager.attachLocal(
                    request.getSessionId(),
                    local.getPid(),
                    local.getTimeoutMs()
                );
            } else {
                responseObserver.onNext(SessionResponse.newBuilder()
                    .setError(JdbgError.newBuilder()
                        .setCode("INVALID_ARGUMENT")
                        .setMessage("Either remote or local target must be specified")
                        .build())
                    .build());
                responseObserver.onCompleted();
                return;
            }
            
            // Set the session name if provided
            if (!request.getName().isEmpty()) {
                session.setName(request.getName());
            }
            
            responseObserver.onNext(SessionResponse.newBuilder()
                .setSession(session.toProto())
                .build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(SessionResponse.newBuilder()
                .setError(JdbgError.newBuilder()
                    .setCode("CONNECTION_FAILED")
                    .setMessage(e.getMessage())
                    .build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void renameSession(final RenameSessionRequest request, final StreamObserver<StatusResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            session.setName(request.getNewName());
            responseObserver.onNext(StatusResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(StatusResponse.newBuilder()
                .setSuccess(false)
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void getStatus(final SessionIdRequest request, final StreamObserver<StatusOverviewResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            final List<ThreadReference> threads = session.getThreads();
            
            int suspendedCount = 0;
            final List<SuspendedThreadInfo> suspendedThreads = new ArrayList<>();
            
            for (final ThreadReference thread : threads) {
                if (thread.isSuspended()) {
                    suspendedCount++;
                    
                    // Check if this thread is at a breakpoint
                    final Optional<String> bpId = session.getBreakpointForThread(thread.uniqueID());
                    final boolean atBreakpoint = bpId.isPresent();
                    
                    String breakpointLocation = "";
                    if (atBreakpoint) {
                        final var bpInfo = session.getBreakpointInfos().get(bpId.get());
                        if (bpInfo != null) {
                            breakpointLocation = bpInfo.getLocationString();
                        }
                    }
                    
                    // Get current location
                    String currentLocation = "";
                    try {
                        if (thread.frameCount() > 0) {
                            final Location loc = thread.frame(0).location();
                            currentLocation = loc.declaringType().name() + "." + 
                                            loc.method().name() + ":" + loc.lineNumber();
                        }
                    } catch (final IncompatibleThreadStateException e) {
                        // Thread not suspended properly
                    }
                    
                    suspendedThreads.add(SuspendedThreadInfo.newBuilder()
                        .setThreadId(thread.uniqueID())
                        .setThreadName(thread.name())
                        .setAtBreakpoint(atBreakpoint)
                        .setBreakpointId(bpId.orElse(""))
                        .setBreakpointLocation(breakpointLocation)
                        .setCurrentLocation(currentLocation)
                        .build());
                }
            }
            
            // JVM is "suspended" if all threads are suspended
            final boolean jvmSuspended = suspendedCount == threads.size() && !threads.isEmpty();
            
            responseObserver.onNext(StatusOverviewResponse.newBuilder()
                .setSession(session.toProto())
                .setJvmSuspended(jvmSuspended)
                .setSuspendedThreadCount(suspendedCount)
                .setTotalThreadCount(threads.size())
                .addAllSuspendedThreads(suspendedThreads)
                .build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(StatusOverviewResponse.newBuilder()
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void detachSession(final SessionIdRequest request, final StreamObserver<StatusResponse> responseObserver) {
        try {
            final String sessionId = request.getSessionId().isEmpty() 
                ? sessionManager.getActiveSessionId() 
                : request.getSessionId();
            sessionManager.removeSession(sessionId);
            responseObserver.onNext(StatusResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(StatusResponse.newBuilder()
                .setSuccess(false)
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void getSessionStatus(final SessionIdRequest request, final StreamObserver<SessionResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            responseObserver.onNext(SessionResponse.newBuilder()
                .setSession(session.toProto())
                .build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(SessionResponse.newBuilder()
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void listSessions(final Empty request, final StreamObserver<SessionListResponse> responseObserver) {
        final List<Session> sessions = sessionManager.listSessions().stream()
            .map(DebugSession::toProto)
            .toList();
        
        responseObserver.onNext(SessionListResponse.newBuilder()
            .addAllSessions(sessions)
            .setActiveSessionId(sessionManager.getActiveSessionId() != null ? sessionManager.getActiveSessionId() : "")
            .build());
        responseObserver.onCompleted();
    }
    
    @Override
    public void setActiveSession(final SessionIdRequest request, final StreamObserver<StatusResponse> responseObserver) {
        try {
            sessionManager.setActiveSession(request.getSessionId());
            responseObserver.onNext(StatusResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(StatusResponse.newBuilder()
                .setSuccess(false)
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    // Breakpoint operations
    @Override
    public void addBreakpoint(final AddBreakpointRequest request, final StreamObserver<BreakpointResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            final String bpId;
            
            if (request.hasLine()) {
                final LineLocation line = request.getLine();
                bpId = session.addLineBreakpoint(line.getClassName(), line.getLineNumber(), request.getCondition());
            } else if (request.hasMethod()) {
                final MethodLocation method = request.getMethod();
                bpId = session.addMethodBreakpoint(method.getClassName(), method.getMethodName(), request.getCondition());
            } else {
                responseObserver.onNext(BreakpointResponse.newBuilder()
                    .setError(JdbgError.newBuilder().setMessage("Either line or method location required").build())
                    .build());
                responseObserver.onCompleted();
                return;
            }
            
            final DebugSession.BreakpointInfo info = session.getBreakpointInfos().get(bpId);
            responseObserver.onNext(BreakpointResponse.newBuilder()
                .setBreakpoint(info.toProto(session.getId()))
                .build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(BreakpointResponse.newBuilder()
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void removeBreakpoint(final BreakpointIdRequest request, final StreamObserver<StatusResponse> responseObserver) {
        try {
            // Find which session has this breakpoint
            for (final DebugSession session : sessionManager.listSessions()) {
                if (session.getBreakpointInfos().containsKey(request.getBreakpointId())) {
                    session.removeBreakpoint(request.getBreakpointId());
                    break;
                }
            }
            responseObserver.onNext(StatusResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(StatusResponse.newBuilder()
                .setSuccess(false)
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void enableBreakpoint(final BreakpointIdRequest request, final StreamObserver<BreakpointResponse> responseObserver) {
        setBreakpointState(request.getBreakpointId(), true, responseObserver);
    }
    
    @Override
    public void disableBreakpoint(final BreakpointIdRequest request, final StreamObserver<BreakpointResponse> responseObserver) {
        setBreakpointState(request.getBreakpointId(), false, responseObserver);
    }
    
    private void setBreakpointState(final String bpId, final boolean enabled, final StreamObserver<BreakpointResponse> responseObserver) {
        try {
            for (final DebugSession session : sessionManager.listSessions()) {
                final DebugSession.BreakpointInfo info = session.getBreakpointInfos().get(bpId);
                if (info != null) {
                    session.setBreakpointEnabled(bpId, enabled);
                    responseObserver.onNext(BreakpointResponse.newBuilder()
                        .setBreakpoint(info.toProto(session.getId()))
                        .build());
                    responseObserver.onCompleted();
                    return;
                }
            }
            responseObserver.onNext(BreakpointResponse.newBuilder()
                .setError(JdbgError.newBuilder().setMessage("Breakpoint not found: " + bpId).build())
                .build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(BreakpointResponse.newBuilder()
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void listBreakpoints(final SessionIdRequest request, final StreamObserver<BreakpointListResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            final List<Breakpoint> breakpoints = session.getBreakpointInfos().values().stream()
                .map(info -> info.toProto(session.getId()))
                .toList();
            
            responseObserver.onNext(BreakpointListResponse.newBuilder()
                .addAllBreakpoints(breakpoints)
                .build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(BreakpointListResponse.newBuilder()
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void clearBreakpoints(final SessionIdRequest request, final StreamObserver<StatusResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            session.clearBreakpoints();
            responseObserver.onNext(StatusResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(StatusResponse.newBuilder()
                .setSuccess(false)
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    // Exception breakpoints
    @Override
    public void catchException(final CatchExceptionRequest request, final StreamObserver<ExceptionBreakpointResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            final String id = session.addExceptionBreakpoint(
                request.getExceptionClass(), request.getCaught(), request.getUncaught());
            
            final DebugSession.ExceptionBreakpointInfo info = session.getExceptionBreakpointInfos().get(id);
            responseObserver.onNext(ExceptionBreakpointResponse.newBuilder()
                .setBreakpoint(info.toProto(session.getId()))
                .build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(ExceptionBreakpointResponse.newBuilder()
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void ignoreException(final ExceptionBreakpointIdRequest request, final StreamObserver<StatusResponse> responseObserver) {
        try {
            for (final DebugSession session : sessionManager.listSessions()) {
                if (session.getExceptionBreakpointInfos().containsKey(request.getId())) {
                    session.removeExceptionBreakpoint(request.getId());
                    break;
                }
            }
            responseObserver.onNext(StatusResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(StatusResponse.newBuilder()
                .setSuccess(false)
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void listExceptionBreakpoints(final SessionIdRequest request, final StreamObserver<ExceptionBreakpointListResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            final List<ExceptionBreakpoint> breakpoints = session.getExceptionBreakpointInfos().values().stream()
                .map(info -> info.toProto(session.getId()))
                .toList();
            
            responseObserver.onNext(ExceptionBreakpointListResponse.newBuilder()
                .addAllBreakpoints(breakpoints)
                .build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(ExceptionBreakpointListResponse.newBuilder()
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    // Execution control
    @Override
    public void continue_(final SessionIdRequest request, final StreamObserver<StatusResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            session.resumeAll();
            responseObserver.onNext(StatusResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(StatusResponse.newBuilder()
                .setSuccess(false)
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void suspend(final SuspendRequest request, final StreamObserver<StatusResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            if (request.getThreadId() != 0) {
                session.getThread(request.getThreadId())
                    .orElseThrow(() -> new IllegalArgumentException("Thread not found"))
                    .suspend();
            } else {
                session.suspendAll();
            }
            responseObserver.onNext(StatusResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(StatusResponse.newBuilder()
                .setSuccess(false)
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void resume(final ResumeRequest request, final StreamObserver<StatusResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            if (request.getThreadId() != 0) {
                session.getThread(request.getThreadId())
                    .orElseThrow(() -> new IllegalArgumentException("Thread not found"))
                    .resume();
            } else {
                session.resumeAll();
            }
            responseObserver.onNext(StatusResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(StatusResponse.newBuilder()
                .setSuccess(false)
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void step(final dev.jdbg.grpc.StepRequest request, final StreamObserver<StepResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            
            final int depth = switch (request.getDepth()) {
                case STEP_DEPTH_INTO -> StepRequest.STEP_INTO;
                case STEP_DEPTH_OUT -> StepRequest.STEP_OUT;
                default -> StepRequest.STEP_OVER;
            };
            
            final int size = request.getSize() == StepSize.STEP_SIZE_INSTRUCTION 
                ? StepRequest.STEP_MIN 
                : StepRequest.STEP_LINE;
            
            final long threadId = request.getThreadId() != 0 
                ? request.getThreadId() 
                : session.getSelectedThreadId();
            
            session.step(threadId, depth, size);
            
            responseObserver.onNext(StepResponse.newBuilder()
                .setSuccess(true)
                .build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(StepResponse.newBuilder()
                .setSuccess(false)
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    // Thread operations
    @Override
    public void listThreads(final SessionIdRequest request, final StreamObserver<ThreadListResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            final List<ThreadInfo> threads = session.getThreads().stream()
                .map(this::buildThreadInfo)
                .toList();
            
            responseObserver.onNext(ThreadListResponse.newBuilder()
                .addAllThreads(threads)
                .build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(ThreadListResponse.newBuilder()
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void selectThread(final SelectThreadRequest request, final StreamObserver<StatusResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            session.setSelectedThreadId(request.getThreadId());
            session.setSelectedFrameIndex(0);
            responseObserver.onNext(StatusResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(StatusResponse.newBuilder()
                .setSuccess(false)
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void suspendThread(final ThreadRequest request, final StreamObserver<StatusResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            session.getThread(request.getThreadId())
                .orElseThrow(() -> new IllegalArgumentException("Thread not found"))
                .suspend();
            responseObserver.onNext(StatusResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(StatusResponse.newBuilder()
                .setSuccess(false)
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void resumeThread(final ThreadRequest request, final StreamObserver<StatusResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            session.getThread(request.getThreadId())
                .orElseThrow(() -> new IllegalArgumentException("Thread not found"))
                .resume();
            responseObserver.onNext(StatusResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(StatusResponse.newBuilder()
                .setSuccess(false)
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    // Frame operations
    @Override
    public void listFrames(final FrameListRequest request, final StreamObserver<FrameListResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            final long threadId = request.getThreadId() != 0 
                ? request.getThreadId() 
                : session.getSelectedThreadId();
            
            final ThreadReference thread = session.getThread(threadId)
                .orElseThrow(() -> new IllegalArgumentException("Thread not found"));
            
            final List<StackFrame> frames = thread.frames();
            final int start = request.getStartIndex();
            final int count = request.getCount() > 0 ? request.getCount() : frames.size();
            final int end = Math.min(start + count, frames.size());
            
            final List<FrameInfo> frameInfos = new ArrayList<>();
            for (int i = start; i < end; i++) {
                frameInfos.add(buildFrameInfo(frames.get(i), i));
            }
            
            responseObserver.onNext(FrameListResponse.newBuilder()
                .addAllFrames(frameInfos)
                .setTotalCount(frames.size())
                .build());
            responseObserver.onCompleted();
        } catch (final IncompatibleThreadStateException e) {
            responseObserver.onNext(FrameListResponse.newBuilder()
                .setError(JdbgError.newBuilder()
                    .setCode("THREAD_NOT_SUSPENDED")
                    .setMessage("Thread must be suspended to get frames")
                    .build())
                .build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(FrameListResponse.newBuilder()
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void selectFrame(final SelectFrameRequest request, final StreamObserver<StatusResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            session.setSelectedFrameIndex(request.getFrameIndex());
            responseObserver.onNext(StatusResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(StatusResponse.newBuilder()
                .setSuccess(false)
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    // Variable operations
    @Override
    public void listVariables(final VariableListRequest request, final StreamObserver<VariableListResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            final StackFrame frame = getFrame(session, request.getThreadId(), request.getFrameIndex());
            
            final List<Variable> variables = new ArrayList<>();
            
            // Add 'this' if available
            final ObjectReference thisObj = frame.thisObject();
            if (thisObj != null) {
                variables.add(Variable.newBuilder()
                    .setName("this")
                    .setType(thisObj.type().name())
                    .setValue(formatValue(thisObj))
                    .setKind(VariableKind.VARIABLE_KIND_THIS)
                    .setIsExpandable(true)
                    .build());
            }
            
            // Add local variables
            try {
                for (final LocalVariable var : frame.visibleVariables()) {
                    final Value value = frame.getValue(var);
                    variables.add(Variable.newBuilder()
                        .setName(var.name())
                        .setType(var.typeName())
                        .setValue(formatValue(value))
                        .setKind(var.isArgument() ? VariableKind.VARIABLE_KIND_ARGUMENT : VariableKind.VARIABLE_KIND_LOCAL)
                        .setIsExpandable(value instanceof ObjectReference)
                        .build());
                }
            } catch (final AbsentInformationException e) {
                // No debug info
            }
            
            responseObserver.onNext(VariableListResponse.newBuilder()
                .addAllVariables(variables)
                .build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(VariableListResponse.newBuilder()
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void getVariable(final GetVariableRequest request, final StreamObserver<VariableResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            final StackFrame frame = getFrame(session, request.getThreadId(), request.getFrameIndex());
            
            Variable variable = null;
            
            // Check for 'this'
            if ("this".equals(request.getName())) {
                final ObjectReference thisObj = frame.thisObject();
                if (thisObj != null) {
                    variable = Variable.newBuilder()
                        .setName("this")
                        .setType(thisObj.type().name())
                        .setValue(formatValue(thisObj))
                        .setKind(VariableKind.VARIABLE_KIND_THIS)
                        .build();
                }
            }
            
            // Check local variables
            if (variable == null) {
                try {
                    for (final LocalVariable var : frame.visibleVariables()) {
                        if (var.name().equals(request.getName())) {
                            final Value value = frame.getValue(var);
                            variable = Variable.newBuilder()
                                .setName(var.name())
                                .setType(var.typeName())
                                .setValue(formatValue(value))
                                .setKind(var.isArgument() ? VariableKind.VARIABLE_KIND_ARGUMENT : VariableKind.VARIABLE_KIND_LOCAL)
                                .build();
                            break;
                        }
                    }
                } catch (final AbsentInformationException e) {
                    // No debug info
                }
            }
            
            if (variable != null) {
                responseObserver.onNext(VariableResponse.newBuilder()
                    .setVariable(variable)
                    .build());
            } else {
                responseObserver.onNext(VariableResponse.newBuilder()
                    .setError(JdbgError.newBuilder()
                        .setCode("VARIABLE_NOT_FOUND")
                        .setMessage("Variable not found: " + request.getName())
                        .build())
                    .build());
            }
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(VariableResponse.newBuilder()
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    // Evaluation
    @Override
    public void evaluate(final EvaluateRequest request, final StreamObserver<EvaluateResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            final StackFrame frame = getFrame(session, request.getThreadId(), request.getFrameIndex());
            final ThreadReference thread = session.getThread(request.getThreadId())
                .orElseThrow(() -> new IllegalArgumentException("Thread not found"));
            
            final String exprStr = request.getExpression().trim();
            
            // Parse the expression
            final ExpressionParser parser = new ExpressionParser(exprStr);
            final ExpressionParser.Expr expr = parser.parse();
            
            // Evaluate using JDI interpreter
            final JdiInterpreter interpreter = new JdiInterpreter(session.getVm(), thread, frame);
            final Value result = interpreter.evaluate(expr);
            
            responseObserver.onNext(EvaluateResponse.newBuilder()
                .setResult(formatValue(result))
                .setType(getValueTypeName(result))
                .build());
            responseObserver.onCompleted();
            
        } catch (final ExpressionParser.ParseException e) {
            responseObserver.onNext(EvaluateResponse.newBuilder()
                .setError(JdbgError.newBuilder()
                    .setCode("PARSE_ERROR")
                    .setMessage("Parse error: " + e.getMessage())
                    .build())
                .build());
            responseObserver.onCompleted();
        } catch (final JdiInterpreter.EvaluationException e) {
            responseObserver.onNext(EvaluateResponse.newBuilder()
                .setError(JdbgError.newBuilder()
                    .setCode("EVALUATION_FAILED")
                    .setMessage(e.getMessage())
                    .build())
                .build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(EvaluateResponse.newBuilder()
                .setError(JdbgError.newBuilder()
                    .setCode("EVALUATION_ERROR")
                    .setMessage(e.getMessage())
                    .build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    private String getValueTypeName(final Value value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof ObjectReference) {
            return ((ObjectReference) value).referenceType().name();
        }
        return value.type().name();
    }
    
    // Source context
    @Override
    public void getSourceContext(final SourceContextRequest request, final StreamObserver<SourceContextResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            final StackFrame frame = getFrame(session, request.getThreadId(), request.getFrameIndex());
            final Location location = frame.location();
            
            final SourceContextResponse.Builder builder = SourceContextResponse.newBuilder()
                .setClassName(location.declaringType().name())
                .setMethodName(location.method().name())
                .setCurrentLine(location.lineNumber());
            
            try {
                builder.setSourceName(location.sourceName());
            } catch (final AbsentInformationException e) {
                // No source info
            }
            
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(SourceContextResponse.newBuilder()
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    // Event management (non-blocking)
    @Override
    public void pollEvents(final PollEventsRequest request, final StreamObserver<EventListResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            final List<DebugEvent> events = session.pollEvents(
                request.getLimit(), 
                request.getEventTypesList()
            );
            
            responseObserver.onNext(EventListResponse.newBuilder()
                .addAllEvents(events)
                .setRemainingCount(session.getEventBufferSize())
                .build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(EventListResponse.newBuilder()
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void waitForEvent(final WaitForEventRequest request, final StreamObserver<EventListResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            final List<DebugEvent> events = session.waitForEvent(
                request.getTimeoutMs(),
                request.getEventTypesList()
            );
            
            responseObserver.onNext(EventListResponse.newBuilder()
                .addAllEvents(events)
                .setRemainingCount(session.getEventBufferSize())
                .build());
            responseObserver.onCompleted();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            responseObserver.onNext(EventListResponse.newBuilder()
                .setError(JdbgError.newBuilder()
                    .setCode("INTERRUPTED")
                    .setMessage("Wait was interrupted")
                    .build())
                .build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(EventListResponse.newBuilder()
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void clearEvents(final SessionIdRequest request, final StreamObserver<StatusResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            session.clearEvents();
            responseObserver.onNext(StatusResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(StatusResponse.newBuilder()
                .setSuccess(false)
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void getEventInfo(final SessionIdRequest request, final StreamObserver<EventInfoResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            responseObserver.onNext(session.getEventInfo());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(EventInfoResponse.newBuilder()
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    // Event streaming (deprecated - use pollEvents/waitForEvent for scripting)
    @Override
    public void subscribeEvents(final SessionIdRequest request, final StreamObserver<DebugEvent> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            
            final java.util.function.Consumer<DebugEvent> listener = event -> {
                try {
                    responseObserver.onNext(event);
                } catch (final Exception e) {
                    // Client disconnected
                }
            };
            
            session.addEventListener(listener);
            
            // Keep the stream open until the client disconnects
            // The gRPC framework will handle the lifecycle
        } catch (final Exception e) {
            responseObserver.onError(e);
        }
    }
    
    // Helper methods
    private StackFrame getFrame(final DebugSession session, final long threadId, final int frameIndex) 
            throws IncompatibleThreadStateException {
        final long tid = threadId != 0 ? threadId : session.getSelectedThreadId();
        final int fidx = frameIndex >= 0 ? frameIndex : session.getSelectedFrameIndex();
        
        final ThreadReference thread = session.getThread(tid)
            .orElseThrow(() -> new IllegalArgumentException("Thread not found: " + tid));
        
        final List<StackFrame> frames = thread.frames();
        if (fidx >= frames.size()) {
            throw new IllegalArgumentException("Frame index out of range: " + fidx);
        }
        
        return frames.get(fidx);
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
    
    private FrameInfo buildFrameInfo(final StackFrame frame, final int index) {
        final Location location = frame.location();
        final FrameInfo.Builder builder = FrameInfo.newBuilder()
            .setIndex(index)
            .setClassName(location.declaringType().name())
            .setMethodName(location.method().name())
            .setLineNumber(location.lineNumber())
            .setIsNative(location.method().isNative());
        
        try {
            builder.setSourceName(location.sourceName());
        } catch (final AbsentInformationException e) {
            // No source info
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
    
    private String formatValue(final Value value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof PrimitiveValue) {
            return value.toString();
        }
        if (value instanceof StringReference strRef) {
            return "\"" + strRef.value() + "\"";
        }
        if (value instanceof ArrayReference arrayRef) {
            return arrayRef.type().name() + "[" + arrayRef.length() + "]";
        }
        if (value instanceof ObjectReference objRef) {
            return objRef.type().name() + "@" + objRef.uniqueID();
        }
        return value.toString();
    }
}


package dev.jdbg.server;

import com.sun.jdi.*;
import dev.jdbg.grpc.*;
import io.grpc.stub.StreamObserver;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of the CompletionService gRPC service.
 * Provides dynamic completions for the CLI.
 */
public final class CompletionServiceImpl extends CompletionServiceGrpc.CompletionServiceImplBase {
    
    private final SessionManager sessionManager;
    
    public CompletionServiceImpl(final SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
    
    @Override
    public void completeSessions(final Empty request, final StreamObserver<CompletionResponse> responseObserver) {
        final List<CompletionItem> items = sessionManager.listSessions().stream()
            .map(session -> CompletionItem.newBuilder()
                .setValue(session.getId())
                .setDisplay(session.getId() + " (" + session.getState() + ")")
                .setDescription(session.getHost() + ":" + session.getPort())
                .build())
            .toList();
        
        responseObserver.onNext(CompletionResponse.newBuilder()
            .addAllItems(items)
            .build());
        responseObserver.onCompleted();
    }
    
    @Override
    public void completeClasses(final ClassCompletionRequest request, final StreamObserver<CompletionResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            final String prefix = request.getPrefix().toLowerCase();
            final int limit = request.getLimit() > 0 ? request.getLimit() : 50;
            
            final List<CompletionItem> items = session.getVm().allClasses().stream()
                .map(ReferenceType::name)
                .filter(name -> name.toLowerCase().contains(prefix))
                .sorted((a, b) -> {
                    // Prioritize classes that start with the prefix
                    final boolean aStarts = a.toLowerCase().startsWith(prefix);
                    final boolean bStarts = b.toLowerCase().startsWith(prefix);
                    if (aStarts && !bStarts) return -1;
                    if (!aStarts && bStarts) return 1;
                    return a.compareTo(b);
                })
                .limit(limit)
                .map(name -> CompletionItem.newBuilder()
                    .setValue(name)
                    .setDisplay(name)
                    .build())
                .toList();
            
            responseObserver.onNext(CompletionResponse.newBuilder()
                .addAllItems(items)
                .build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(CompletionResponse.newBuilder()
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void completeMethods(final MethodCompletionRequest request, final StreamObserver<CompletionResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            final String prefix = request.getPrefix().toLowerCase();
            
            final List<ReferenceType> classes = session.getVm().classesByName(request.getClassName());
            if (classes.isEmpty()) {
                responseObserver.onNext(CompletionResponse.newBuilder().build());
                responseObserver.onCompleted();
                return;
            }
            
            final Set<String> seen = new HashSet<>();
            final List<CompletionItem> items = new ArrayList<>();
            
            for (final ReferenceType refType : classes) {
                for (final Method method : refType.allMethods()) {
                    final String name = method.name();
                    if (name.toLowerCase().startsWith(prefix) && !name.startsWith("<") && seen.add(name)) {
                        items.add(CompletionItem.newBuilder()
                            .setValue(name)
                            .setDisplay(name + "(" + formatMethodParams(method) + ")")
                            .setDescription(method.returnTypeName())
                            .build());
                    }
                }
            }
            
            items.sort(Comparator.comparing(CompletionItem::getValue));
            
            responseObserver.onNext(CompletionResponse.newBuilder()
                .addAllItems(items)
                .build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(CompletionResponse.newBuilder()
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void completeThreads(final SessionIdRequest request, final StreamObserver<CompletionResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            
            final List<CompletionItem> items = session.getThreads().stream()
                .map(thread -> CompletionItem.newBuilder()
                    .setValue(String.valueOf(thread.uniqueID()))
                    .setDisplay(thread.uniqueID() + " \"" + thread.name() + "\"")
                    .setDescription(thread.isSuspended() ? "suspended" : "running")
                    .build())
                .toList();
            
            responseObserver.onNext(CompletionResponse.newBuilder()
                .addAllItems(items)
                .build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(CompletionResponse.newBuilder()
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void completeVariables(final VariableCompletionRequest request, final StreamObserver<CompletionResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            final String prefix = request.getPrefix().toLowerCase();
            
            final long threadId = request.getThreadId() != 0 
                ? request.getThreadId() 
                : session.getSelectedThreadId();
            final int frameIndex = request.getFrameIndex() >= 0 
                ? request.getFrameIndex() 
                : session.getSelectedFrameIndex();
            
            final ThreadReference thread = session.getThread(threadId)
                .orElseThrow(() -> new IllegalArgumentException("Thread not found"));
            
            final List<StackFrame> frames = thread.frames();
            if (frameIndex >= frames.size()) {
                responseObserver.onNext(CompletionResponse.newBuilder().build());
                responseObserver.onCompleted();
                return;
            }
            
            final StackFrame frame = frames.get(frameIndex);
            final List<CompletionItem> items = new ArrayList<>();
            
            // Add 'this' if available
            if (frame.thisObject() != null && "this".startsWith(prefix)) {
                items.add(CompletionItem.newBuilder()
                    .setValue("this")
                    .setDisplay("this")
                    .setDescription(frame.thisObject().type().name())
                    .build());
            }
            
            // Add local variables
            try {
                for (final LocalVariable var : frame.visibleVariables()) {
                    if (var.name().toLowerCase().startsWith(prefix)) {
                        items.add(CompletionItem.newBuilder()
                            .setValue(var.name())
                            .setDisplay(var.name())
                            .setDescription(var.typeName())
                            .build());
                    }
                }
            } catch (final AbsentInformationException e) {
                // No debug info
            }
            
            items.sort(Comparator.comparing(CompletionItem::getValue));
            
            responseObserver.onNext(CompletionResponse.newBuilder()
                .addAllItems(items)
                .build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(CompletionResponse.newBuilder()
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void completeBreakpoints(final SessionIdRequest request, final StreamObserver<CompletionResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            
            final List<CompletionItem> items = session.getBreakpointInfos().values().stream()
                .map(info -> CompletionItem.newBuilder()
                    .setValue(info.getId())
                    .setDisplay(info.getId())
                    .setDescription(info.getLocationString())
                    .build())
                .toList();
            
            responseObserver.onNext(CompletionResponse.newBuilder()
                .addAllItems(items)
                .build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(CompletionResponse.newBuilder()
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void completeFields(final FieldCompletionRequest request, final StreamObserver<CompletionResponse> responseObserver) {
        try {
            final DebugSession session = sessionManager.getSession(request.getSessionId());
            final String prefix = request.getPrefix().toLowerCase();
            
            // For now, complete fields on 'this' object
            final long threadId = request.getThreadId() != 0 
                ? request.getThreadId() 
                : session.getSelectedThreadId();
            final int frameIndex = request.getFrameIndex() >= 0 
                ? request.getFrameIndex() 
                : session.getSelectedFrameIndex();
            
            final ThreadReference thread = session.getThread(threadId)
                .orElseThrow(() -> new IllegalArgumentException("Thread not found"));
            
            final List<StackFrame> frames = thread.frames();
            if (frameIndex >= frames.size()) {
                responseObserver.onNext(CompletionResponse.newBuilder().build());
                responseObserver.onCompleted();
                return;
            }
            
            final StackFrame frame = frames.get(frameIndex);
            final ObjectReference thisObj = frame.thisObject();
            if (thisObj == null) {
                responseObserver.onNext(CompletionResponse.newBuilder().build());
                responseObserver.onCompleted();
                return;
            }
            
            final List<CompletionItem> items = new ArrayList<>();
            
            for (final Field field : thisObj.referenceType().allFields()) {
                if (field.name().toLowerCase().startsWith(prefix)) {
                    items.add(CompletionItem.newBuilder()
                        .setValue(field.name())
                        .setDisplay(field.name())
                        .setDescription(field.typeName())
                        .build());
                }
            }
            
            items.sort(Comparator.comparing(CompletionItem::getValue));
            
            responseObserver.onNext(CompletionResponse.newBuilder()
                .addAllItems(items)
                .build());
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onNext(CompletionResponse.newBuilder()
                .setError(JdbgError.newBuilder().setMessage(e.getMessage()).build())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    private String formatMethodParams(final Method method) {
        try {
            return method.argumentTypeNames().stream()
                .map(this::simplifyTypeName)
                .collect(Collectors.joining(", "));
        } catch (final Exception e) {
            return "...";
        }
    }
    
    private String simplifyTypeName(final String typeName) {
        final int lastDot = typeName.lastIndexOf('.');
        return lastDot >= 0 ? typeName.substring(lastDot + 1) : typeName;
    }
}


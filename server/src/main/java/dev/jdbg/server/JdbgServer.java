package dev.jdbg.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.channel.epoll.EpollEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.grpc.netty.shaded.io.netty.channel.unix.DomainSocketAddress;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * JDBG gRPC Server - maintains persistent JDI connections.
 */
public final class JdbgServer {
    
    private final Server server;
    private final SessionManager sessionManager;
    
    public JdbgServer(final String listenAddress) throws IOException {
        this.sessionManager = new SessionManager();
        
        final DebuggerServiceImpl debuggerService = new DebuggerServiceImpl(sessionManager);
        final CompletionServiceImpl completionService = new CompletionServiceImpl(sessionManager);
        
        if (listenAddress.startsWith("unix://")) {
            // Unix domain socket
            final String socketPath = listenAddress.substring(7);
            
            // Delete existing socket file
            Files.deleteIfExists(Path.of(socketPath));
            
            final EpollEventLoopGroup bossGroup = new EpollEventLoopGroup(1);
            final EpollEventLoopGroup workerGroup = new EpollEventLoopGroup();
            
            this.server = NettyServerBuilder
                .forAddress(new DomainSocketAddress(socketPath))
                .bossEventLoopGroup(bossGroup)
                .workerEventLoopGroup(workerGroup)
                .channelType(EpollServerDomainSocketChannel.class)
                .addService(debuggerService)
                .addService(completionService)
                .build();
        } else {
            // TCP socket
            String addr = listenAddress;
            if (addr.startsWith("tcp://")) {
                addr = addr.substring(6);
            }
            
            final String[] parts = addr.split(":");
            final String host = parts[0];
            final int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 5005;
            
            this.server = ServerBuilder
                .forPort(port)
                .addService(debuggerService)
                .addService(completionService)
                .build();
        }
    }
    
    public void start() throws IOException {
        server.start();
        System.out.println("JDBG server started on " + server.getPort());
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down JDBG server...");
            try {
                JdbgServer.this.stop();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
    }
    
    public void stop() throws InterruptedException {
        if (server != null) {
            sessionManager.closeAll();
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }
    
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
    
    public static void main(final String[] args) throws IOException, InterruptedException {
        String listenAddress = "tcp://127.0.0.1:5005";
        
        for (int i = 0; i < args.length; i++) {
            if ("--listen".equals(args[i]) && i + 1 < args.length) {
                listenAddress = args[++i];
            }
        }
        
        final JdbgServer server = new JdbgServer(listenAddress);
        server.start();
        server.blockUntilShutdown();
    }
}


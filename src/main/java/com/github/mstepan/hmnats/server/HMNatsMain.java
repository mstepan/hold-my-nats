package com.github.mstepan.hmnats.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HMNatsMain {

    private static final Logger LOG = LoggerFactory.getLogger(HMNatsMain.class);
    private static final int TCP_PORT = 4222;
    private static final long CLIENT_THREAD_JOIN_TIMEOUT_MILLIS = 1_000L;

    private record ClientConnection(Thread clientThread, Socket clientSocket) {}

    static void main() {
        new HMNatsMain().run();
    }

    void run() {
        LOG.info("hold-my-nats started");

        final Thread mainThread = Thread.currentThread();
        final List<ClientConnection> clientConnections = new ArrayList<>();

        Runtime.getRuntime()
                .addShutdownHook(
                        Thread.ofPlatform()
                                .name("hold-my-nats-shutdown")
                                .unstarted(
                                        () -> {
                                            LOG.info("shutdown requested");
                                            mainThread.interrupt();
                                        }));

        try (MessageRouter router = new MessageRouter()) {
            router.bootstrap();

            try (ServerSocketChannel channel = ServerSocketChannel.open()) {
                channel.configureBlocking(true);
                channel.bind(new InetSocketAddress(TCP_PORT));

                ServerRuntimeInfo.getInstance()
                        .updateListeningAddress(resolveAdvertisedHost(channel.socket()), TCP_PORT);

                LOG.info("TCP server listening on port {}", TCP_PORT);

                while (!mainThread.isInterrupted()) {
                    try {
                        final SocketChannel clientSocketChannel = channel.accept();
                        final Socket clientSocket = clientSocketChannel.socket();
                        LOG.info(
                                "accepted connection from {}:{}",
                                clientSocket.getInetAddress(),
                                clientSocket.getPort());

                        Thread clientThread =
                                Thread.ofVirtual()
                                        .name("client-thread")
                                        .start(new ClientInteractionHandler(clientSocket, router));

                        clientConnections.add(new ClientConnection(clientThread, clientSocket));
                        removeTerminatedConnections(clientConnections);
                    } catch (ClosedByInterruptException ex) {
                        LOG.debug("Server socket interrupted as part of shutdown");
                        Thread.currentThread().interrupt();
                    }
                }
            } catch (IOException ex) {
                LOG.error("Failed to start TCP server", ex);
            } finally {
                terminateClientConnections(clientConnections);
            }
        }

        LOG.info("hold-my-nats completed");
    }

    private static String resolveAdvertisedHost(ServerSocket serverSocket) {
        final InetAddress boundAddress = serverSocket.getInetAddress();

        if (boundAddress != null && !boundAddress.isAnyLocalAddress()) {
            return boundAddress.getHostAddress();
        }

        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException ex) {
            LOG.warn("Failed to resolve local host address, fallback to loopback", ex);
            return InetAddress.getLoopbackAddress().getHostAddress();
        }
    }

    private static void removeTerminatedConnections(List<ClientConnection> clientConnections) {
        clientConnections.removeIf(conn -> !conn.clientThread().isAlive());
    }

    private static void terminateClientConnections(List<ClientConnection> clientConnections) {
        LOG.info("Terminating {} active client handler threads", clientConnections.size());

        // Close sockets first to unblock handlers waiting on socket read.
        clientConnections.forEach(conn -> SocketUtils.closeQuietly(conn.clientSocket()));
        clientConnections.forEach(conn -> conn.clientThread().interrupt());

        for (ClientConnection conn : clientConnections) {
            Thread clientThread = conn.clientThread();
            try {
                clientThread.join(CLIENT_THREAD_JOIN_TIMEOUT_MILLIS);
            } catch (InterruptedException interruptedEx) {
                Thread.currentThread().interrupt();
                break;
            }

            if (clientThread.isAlive()) {
                LOG.warn("Client handler thread '{}' didn't terminate in time", clientThread);
            }
        }
    }
}

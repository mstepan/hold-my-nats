package com.github.mstepan.hmnats.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public final class HMNatsMain {

    private static final Logger LOG = LoggerFactory.getLogger(HMNatsMain.class);
    private static final int TCP_PORT = 4222;

    static void main() {
        new HMNatsMain().run();
    }

    void run() {
        LOG.info("hold-my-nats started");

        final Thread mainThread = Thread.currentThread();
        final MessageRouter router = new MessageRouter();
        final List<Thread> clientThreads = new ArrayList<>();
        router.bootstrap();

        Runtime.getRuntime()
                .addShutdownHook(
                        Thread.ofPlatform()
                                .name("hold-my-nats-shutdown")
                                .unstarted(
                                        () -> {
                                            LOG.info("shutdown requested");
                                            mainThread.interrupt();
                                        }));

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

                    clientThreads.add(clientThread);
                    removeTerminatedThreads(clientThreads);
                } catch (ClosedByInterruptException ex) {
                    LOG.debug("Server socket interrupted as part of shutdown");
                    Thread.currentThread().interrupt();
                }
            }
        } catch (IOException ex) {
            LOG.error("Failed to start TCP server", ex);
        } finally {
            terminateClientThreads(clientThreads);
            router.shutdown();
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

    private static void removeTerminatedThreads(List<Thread> clientThreads) {
        clientThreads.removeIf(thread -> !thread.isAlive());
    }

    private static void terminateClientThreads(List<Thread> clientThreads) {
        LOG.info("Terminating {} active client handler threads", clientThreads.size());

        clientThreads.forEach(Thread::interrupt);

        for (Thread clientThread : clientThreads) {
            try {
                clientThread.join(1_000L);
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

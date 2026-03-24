package com.github.mstepan.hmnats;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HMNatsMain {

    private static final Logger LOG = LoggerFactory.getLogger(HMNatsMain.class);
    private static final int TCP_PORT = 4222;
    private static final int SO_TIMEOUT_MILLIS = 1_000;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public static void main(String[] args) {
        new HMNatsMain().run();
    }

    @SuppressFBWarnings(
            value = "UNENCRYPTED_SERVER_SOCKET",
            justification =
                    "NATS protocol is plain TCP by default; TLS termination is expected to be optional"
                            + " and configured at deployment level")
    void run() {
        LOG.info("hold-my-nats started");

        try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
            serverSocket.setSoTimeout(SO_TIMEOUT_MILLIS);
            ServerRuntimeInfo.getInstance()
                    .updateListeningAddress(resolveAdvertisedHost(serverSocket), TCP_PORT);

            Runtime.getRuntime()
                    .addShutdownHook(
                            Thread.ofPlatform()
                                    .name("hold-my-nats-shutdown")
                                    .unstarted(
                                            () -> {
                                                LOG.info("shutdown requested");
                                                running.set(false);
                                                SocketUtils.closeQuietly(serverSocket);
                                            }));

            LOG.info("TCP server listening on port {}", TCP_PORT);

            while (running.get()) {
                try {
                    final Socket clientSocket = serverSocket.accept();
                    LOG.info(
                            "accepted connection from {}:{}",
                            clientSocket.getInetAddress(),
                            clientSocket.getPort());

                    Thread.ofVirtual()
                            .name("client-thread")
                            .start(new ClientInteractionTask(clientSocket));

                } catch (SocketException ex) {
                    if (running.get()) {
                        throw ex;
                    }
                    LOG.debug("Server socket closed as part of shutdown");
                } catch (SocketTimeoutException ignored) {
                    // Periodically check 'running' flag.
                }
            }
        } catch (IOException ex) {
            LOG.error("Failed to start TCP server", ex);
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
}

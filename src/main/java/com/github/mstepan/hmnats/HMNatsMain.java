package com.github.mstepan.hmnats;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HMNatsMain {

    private static final Logger LOG = LoggerFactory.getLogger(HMNatsMain.class);
    private static final int TCP_PORT = 4222;
    private static final int SO_TIMEOUT_MILLIS = 1_000;
    private volatile boolean isRunning = true;

    static void main() {
        new HMNatsMain().run();
    }

    void run() {
        LOG.info("hold-my-nats started");
        isRunning = true;

        try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
            serverSocket.setSoTimeout(SO_TIMEOUT_MILLIS);

            Runtime.getRuntime()
                    .addShutdownHook(
                            Thread.ofPlatform()
                                    .name("hold-my-nats-shutdown")
                                    .unstarted(
                                            () -> {
                                                LOG.info("shutdown requested");
                                                isRunning = false;
                                                SocketUtils.closeQuietly(serverSocket);
                                            }));

            LOG.info("TCP server listening on port {}", TCP_PORT);

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    LOG.info(
                            "accepted connection from {}:{}",
                            clientSocket.getInetAddress(),
                            clientSocket.getPort());

                    Thread.ofVirtual()
                            .name("client-thread")
                            .start(
                                    () -> {
                                        try {
                                            while (!Thread.currentThread().isInterrupted()) {}

                                        } finally {
                                            SocketUtils.closeQuietly(clientSocket);
                                        }
                                    });

                } catch (SocketTimeoutException ignored) {
                    // Periodically check isRunning flag.
                }
            }
        } catch (IOException ex) {
            LOG.error("Failed to start TCP server", ex);
        }

        LOG.info("hold-my-nats completed");
    }
}

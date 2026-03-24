package com.github.mstepan.hmnats;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SocketUtils {

    private static final Logger LOG = LoggerFactory.getLogger(SocketUtils.class);

    private SocketUtils() {
        throw new AssertionError("Can't instantiate utility-only class");
    }

    static void closeQuietly(ServerSocket serverSocket) {
        closeQuietly(serverSocket, "server socket");
    }

    static void closeQuietly(Socket clientSocket) {
        closeQuietly(clientSocket, "client socket");
    }

    private static void closeQuietly(Closeable resource, String resourceName) {
        if (resource == null) {
            return;
        }

        try {
            resource.close();
        } catch (IOException ex) {
            LOG.warn("Failed to close {}", resourceName, ex);
        }
    }
}

package com.github.mstepan.hmnats.server;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
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

    /** Write response to a socket output stream as a single atomic operation */
    static void writeAtomic(OutputStream out, byte[] payload, byte[] delimiter) throws IOException {
        synchronized (out) {
            out.write(payload);
            out.write(delimiter);
            out.flush();
        }
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

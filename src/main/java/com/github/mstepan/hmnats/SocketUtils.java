package com.github.mstepan.hmnats;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SocketUtils {

    private static final Logger LOG = LoggerFactory.getLogger(SocketUtils.class);

    private SocketUtils() {}

    static void closeQuietly(ServerSocket serverSocket) {
        try {
            serverSocket.close();
        } catch (IOException ex) {
            LOG.warn("Failed to close server socket", ex);
        }
    }

    static void closeQuietly(Socket clientSocket) {
        try {
            clientSocket.close();
        } catch (IOException ex) {
            LOG.warn("Failed to close client socket", ex);
        }
    }
}

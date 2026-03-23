package com.github.mstepan.hmnats;

import java.net.Socket;

final class ClientInteractionTask implements Runnable {

    private final Socket clientSocket;

    public ClientInteractionTask(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                //
                // TODO: read from socket
                //
            }

        } finally {
            SocketUtils.closeQuietly(clientSocket);
        }
    }
}

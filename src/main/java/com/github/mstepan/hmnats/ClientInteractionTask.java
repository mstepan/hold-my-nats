package com.github.mstepan.hmnats;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ClientInteractionTask implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ClientInteractionTask.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String DELIMITER = "\r\n";

    private final Socket clientSocket;
    private final String clientId;

    public ClientInteractionTask(Socket clientSocket) {
        this.clientSocket = clientSocket;
        this.clientId = randomUInt64String();
    }

    @Override
    public void run() {

        try (InputStream in = clientSocket.getInputStream();
                OutputStream out = clientSocket.getOutputStream()) {

            ServerRuntimeInfo info = ServerRuntimeInfo.getInstance();
            out.write((buildInfoResponse(info) + DELIMITER).getBytes(StandardCharsets.UTF_8));

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    TimeUnit.SECONDS.sleep(5L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

        } catch (IOException ioEx) {
            LOG.error("Error during client socket handling", ioEx);
        } finally {
            SocketUtils.closeQuietly(clientSocket);
        }
    }

    private String buildInfoResponse(ServerRuntimeInfo info) {
        return "INFO {\"server_id\":\""
                + info.serverId()
                + "\",\"version\":\""
                + info.version()
                + "\",\"proto\":"
                + info.proto()
                + ",\"java\":\""
                + info.javaInfo()
                + "\",\"host\":\""
                + info.host()
                + "\",\"port\":"
                + info.port()
                + ",\"max_payload\":"
                + info.maxPayload()
                + ",\"client_id\":"
                + clientId
                + "}";
    }

    private static String randomUInt64String() {
        return Long.toUnsignedString(SECURE_RANDOM.nextLong());
    }
}

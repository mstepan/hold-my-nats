package com.github.mstepan.hmnats.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ClientInteractionHandler implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ClientInteractionHandler.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Charset PROTOCOL_CHARSET = StandardCharsets.UTF_8;

    private static final String DELIMITER = "\r\n";

    private final Socket clientSocket;
    private final String clientId;

    public ClientInteractionHandler(Socket clientSocket) {
        this.clientSocket = Objects.requireNonNull(clientSocket, "clientSocket should not be null");
        this.clientId = randomUInt64String();
    }

    @Override
    public void run() {
        final ProtocolParser parser =
                new ProtocolParser(ServerRuntimeInfo.getInstance().maxPayload());

        try (InputStream in = clientSocket.getInputStream();
                OutputStream out = clientSocket.getOutputStream()) {

            ServerRuntimeInfo info = ServerRuntimeInfo.getInstance();
            out.write((buildInfoResponse(info) + DELIMITER).getBytes(PROTOCOL_CHARSET));

            while (!Thread.currentThread().isInterrupted()) {
                ProtocolCommand protocolCommand = parser.parseNext(in);
                if (protocolCommand == null) {
                    break;
                }

                protocolCommand.handle();
            }

        } catch (IOException ioEx) {
            LOG.error("Error during client socket handling", ioEx);
        } finally {
            SocketUtils.closeQuietly(clientSocket);
        }
    }

    /** Response format: INFO {"option_name":option_value,...}\r\n */
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

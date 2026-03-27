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
    private static final byte[] DELIMITER_BYTES = DELIMITER.getBytes(PROTOCOL_CHARSET);

    private final Socket clientSocket;
    private final String clientId;
    private final MessageRouter router;

    public ClientInteractionHandler(Socket clientSocket, MessageRouter router) {
        this.clientSocket = Objects.requireNonNull(clientSocket, "clientSocket should not be null");
        this.clientId = randomUInt64String();
        this.router = router;
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

                protocolCommand.logCommand();

                if (protocolCommand instanceof ProtocolCommand.PubCommand pubCommand) {
                    router.publishMessage(pubCommand.toMessage());
                } else if (protocolCommand instanceof ProtocolCommand.SubCommand subCommand) {
                    Subscriber subscriber = subCommand.toSubscriber();
                    router.registerSubscriber(subscriber);

                    Thread.ofVirtual()
                            .name("subscriber-response")
                            .start(() -> waitAndSendSubscriberPayload(subscriber, out));
                }
            }

        } catch (InterruptedException interEx) {
            Thread.currentThread().interrupt();
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

    private void waitAndSendSubscriberPayload(Subscriber subscriber, OutputStream out) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                LOG.info("Waiting for subscriber to receive a message");
                Message message = subscriber.get();

                // write response to a socket out stream as a single atomic operation
                synchronized (out) {
                    out.write(message.payload());
                    out.write(DELIMITER_BYTES);
                    out.flush();
                }
            }
        } catch (InterruptedException interEx) {
            Thread.currentThread().interrupt();
        } catch (IOException ioEx) {
            LOG.error("Failed to send payload to subscribed client", ioEx);
        }
    }
}

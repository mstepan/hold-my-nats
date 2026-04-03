package com.github.mstepan.hmnats.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
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

    // Stores mapping 'sid' -> 'stream'. This map will be used only from 'ClientInteractionHandler'
    // thread so don't need to be synchronized
    private final Map<String, StreamSubscription> subscriptions = new HashMap<>();

    public ClientInteractionHandler(Socket clientSocket, MessageRouter router) {
        this.clientSocket = Objects.requireNonNull(clientSocket, "clientSocket should not be null");
        this.clientId = randomUInt64String();
        this.router = router;
    }

    record StreamSubscription(Subscriber subscriber, Future<?> future) {}

    @Override
    public void run() {
        final ProtocolParser parser =
                new ProtocolParser(ServerRuntimeInfo.getInstance().maxPayload());

        try (InputStream in = clientSocket.getInputStream();
                OutputStream out = clientSocket.getOutputStream()) {

            ServerRuntimeInfo info = ServerRuntimeInfo.getInstance();
            out.write((buildInfoResponse(info) + DELIMITER).getBytes(PROTOCOL_CHARSET));

            try (ExecutorService childTaskExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        ProtocolCommand protocolCommand = parser.parseNext(in);
                        if (protocolCommand == null) {
                            break;
                        }

                        protocolCommand.logCommand();

                        switch (protocolCommand) {
                            case ProtocolCommand.PubCommand pubCommand ->
                                    router.publishMessage(pubCommand.toMessage());
                            case ProtocolCommand.SubCommand subCommand -> {
                                Subscriber newSubscriber = subCommand.toSubscriber();

                                StreamSubscription streamSub =
                                        subscriptions.get(newSubscriber.sid());

                                // If we have any previous streams subscriptions identified by a
                                // 'sid' we need to remove existing subscriptions first
                                if (streamSub != null) {
                                    terminateSubscription(streamSub);
                                }

                                createSubscription(childTaskExecutor, newSubscriber, out);
                            }
                            case ProtocolCommand.PingCommand _ ->
                                    SocketUtils.writeAtomic(
                                            out,
                                            "PONG".getBytes(PROTOCOL_CHARSET),
                                            DELIMITER_BYTES);
                            default ->
                                    LOG.warn(
                                            "Not implemented protocol command: {}",
                                            protocolCommand);
                        }
                    }
                } finally {
                    terminateAllSubscriptions();
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

    void createSubscription(
            ExecutorService childTaskExecutor, Subscriber newSubscriber, OutputStream out) {
        try {
            router.createSubscription(newSubscriber);

            Future<?> childFuture =
                    childTaskExecutor.submit(() -> handleSingleSubscription(newSubscriber, out));

            subscriptions.put(
                    newSubscriber.sid(), new StreamSubscription(newSubscriber, childFuture));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            LOG.error(
                    "Error creating subscription for subject '{}' and sid '{}'",
                    newSubscriber.subject(),
                    newSubscriber.sid(),
                    ex);
        }
    }

    private void terminateSubscription(StreamSubscription streamSub) {
        streamSub.future.cancel(true);
        router.terminateSubscription(streamSub.subscriber);
    }

    private void terminateAllSubscriptions() {
        subscriptions.values().forEach(this::terminateSubscription);
        subscriptions.clear();
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

    private void handleSingleSubscription(Subscriber subscriber, OutputStream out) {
        LOG.info("Subscriber started");
        try {
            while (!Thread.currentThread().isInterrupted()) {
                LOG.info("Waiting for subscriber to receive a message");
                Message message = subscriber.get();

                SocketUtils.writeAtomic(out, message.payload(), DELIMITER_BYTES);
            }
        } catch (InterruptedException interEx) {
            Thread.currentThread().interrupt();
        } catch (IOException ioEx) {
            LOG.error("Failed to send payload to subscribed client", ioEx);
        } finally {
            LOG.info("Subscriber terminated");
        }
    }
}

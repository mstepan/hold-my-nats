package com.github.mstepan.hmnats.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ProtocolParser {

    private static final Logger LOG = LoggerFactory.getLogger(ProtocolParser.class);

    private static final Charset PROTOCOL_CHARSET = StandardCharsets.UTF_8;
    private static final int MAX_ALLOWED_COMMAND_BYTES = 1024;

    private static final String DELIMITER = "\r\n";
    private static final byte[] DELIMITER_BYTES = DELIMITER.getBytes(PROTOCOL_CHARSET);
    private static final String CONNECT_COMMAND = "CONNECT";
    private static final String PUB_COMMAND = "PUB";
    private static final String SUB_COMMAND = "SUB";
    private static final String PING_COMMAND = "PING";
    private static final String PONG_COMMAND = "PONG";

    private final ByteBuffer commandBuffer;
    private final ByteBuffer payloadBuffer;

    ProtocolParser(int maxPayloadBytes) {
        this.commandBuffer = ByteBuffer.allocate(MAX_ALLOWED_COMMAND_BYTES);
        this.payloadBuffer = ByteBuffer.allocate(maxPayloadBytes);
    }

    ProtocolCommand parseNext(InputStream in) throws IOException {
        String command = readUntilDelimiter(in, this.commandBuffer);
        if (command == null) {
            return null;
        }

        return parseCommand(command, payloadBuffer, in);
    }

    private static String readUntilDelimiter(InputStream in, ByteBuffer buffer) throws IOException {
        buffer.clear();

        while (true) {
            int readValue = in.read();
            if (readValue == -1) {
                if (buffer.position() == 0) {
                    return null;
                }
                throw new IOException("Socket closed before delimiter was received");
            }

            if (!buffer.hasRemaining()) {
                throw new IllegalStateException(
                        "Command exceeds max allowed command size of "
                                + MAX_ALLOWED_COMMAND_BYTES
                                + " bytes");
            }

            buffer.put((byte) readValue);
            if (endsWithDelimiter(buffer, DELIMITER_BYTES)) {
                int payloadLength = buffer.position() - DELIMITER_BYTES.length;

                @SuppressWarnings("ByteBufferBackingArray")
                byte[] data = buffer.array();

                return new String(data, 0, payloadLength, PROTOCOL_CHARSET);
            }
        }
    }

    private static boolean endsWithDelimiter(ByteBuffer buffer, byte[] delimiterBytes) {
        int currentLength = buffer.position();
        if (currentLength < delimiterBytes.length) {
            return false;
        }
        int startOffset = currentLength - delimiterBytes.length;

        @SuppressWarnings("ByteBufferBackingArray")
        byte[] data = buffer.array();
        return Arrays.equals(
                data, startOffset, currentLength, delimiterBytes, 0, delimiterBytes.length);
    }

    private static ProtocolCommand parseCommand(
            String command, ByteBuffer payloadBuffer, InputStream in) throws IOException {

        String[] commandTokens = IOUtils.splitIntoTokens(command);

        if (commandTokens.length == 0) {
            throw new IllegalStateException(String.format("Invalid command format: '%s'", command));
        }

        // CONNECT {"option_name":option_value,...}\r\n
        if (CONNECT_COMMAND.equals(commandTokens[0])) {
            return new ProtocolCommand.ConnectCommand();
        }

        // PUB <subject> [reply-to] <#bytes>\r\n[payload]\r\n
        if (PUB_COMMAND.equals(commandTokens[0])) {
            if (commandTokens.length < 3) {
                throw new IllegalStateException(
                        String.format("Invalid PUB command format: '%s'", command));
            }

            String subject = commandTokens[1];
            int payloadLength = extractPubPayloadLength(command, commandTokens);
            LOG.debug("Received PUB command with payload length: {}", payloadLength);
            ByteBuffer payload = readPubPayload(in, payloadBuffer, payloadLength);

            byte[] payloadBytes = new byte[payload.remaining()];
            payload.duplicate().get(payloadBytes);

            return new ProtocolCommand.PubCommand(subject, payloadBytes);
        }

        // SUB <subject> [queue group] <sid>\r\n
        if (SUB_COMMAND.equals(commandTokens[0])) {
            if (commandTokens.length < 3) {
                throw new IllegalStateException(
                        String.format("Invalid SUB command format: '%s'", command));
            }
            return new ProtocolCommand.SubCommand(commandTokens[1], commandTokens[2]);
        }

        // PING\r\n
        if (PING_COMMAND.equals(commandTokens[0])) {
            return new ProtocolCommand.PingCommand();
        }

        // PONG\r\n
        if (PONG_COMMAND.equals(commandTokens[0])) {
            return new ProtocolCommand.PongCommand();
        }

        return new ProtocolCommand.UnknownCommand();
    }

    private static int extractPubPayloadLength(String originalCommand, String[] commandTokens)
            throws IOException {

        String payloadLengthToken = commandTokens[commandTokens.length - 1];
        try {
            return Integer.parseInt(payloadLengthToken);
        } catch (NumberFormatException ex) {
            throw new IOException(
                    "Invalid PUB payload length in command: '" + originalCommand + "'", ex);
        }
    }

    private static ByteBuffer readPubPayload(
            InputStream in, ByteBuffer payloadBuffer, int payloadLength) throws IOException {
        if (payloadLength < 0) {
            throw new IOException("PUB payload length is negative: " + payloadLength);
        }

        if (payloadLength > payloadBuffer.capacity()) {
            throw new IOException(
                    "PUB payload exceeds max allowed size: "
                            + payloadLength
                            + ", max="
                            + payloadBuffer.capacity());
        }

        payloadBuffer.clear();

        @SuppressWarnings("ByteBufferBackingArray")
        byte[] data = payloadBuffer.array();

        int offset = 0;
        while (offset < payloadLength) {
            int readCnt = in.read(data, offset, payloadLength - offset);
            if (readCnt == -1) {
                throw new IOException("Socket closed while reading PUB payload");
            }
            offset += readCnt;
        }

        byte[] actualDelimiter = new byte[DELIMITER_BYTES.length];
        int delimiterOffset = 0;
        while (delimiterOffset < actualDelimiter.length) {
            int readCnt =
                    in.read(
                            actualDelimiter,
                            delimiterOffset,
                            actualDelimiter.length - delimiterOffset);
            if (readCnt == -1) {
                throw new IOException("Socket closed while reading PUB payload delimiter");
            }
            delimiterOffset += readCnt;
        }

        if (!Arrays.equals(actualDelimiter, DELIMITER_BYTES)) {
            throw new IOException("Invalid PUB payload delimiter");
        }

        payloadBuffer.position(0);
        payloadBuffer.limit(payloadLength);

        return payloadBuffer.asReadOnlyBuffer().slice();
    }
}

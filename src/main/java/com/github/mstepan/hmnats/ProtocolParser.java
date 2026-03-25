package com.github.mstepan.hmnats;

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
    private static final byte[] CONNECT_COMMAND_BYTES = CONNECT_COMMAND.getBytes(PROTOCOL_CHARSET);
    private static final byte[] PUB_COMMAND_BYTES = PUB_COMMAND.getBytes(PROTOCOL_CHARSET);
    private static final byte[] SUB_COMMAND_BYTES = SUB_COMMAND.getBytes(PROTOCOL_CHARSET);
    private static final byte[] PING_COMMAND_BYTES = PING_COMMAND.getBytes(PROTOCOL_CHARSET);
    private static final byte[] PONG_COMMAND_BYTES = PONG_COMMAND.getBytes(PROTOCOL_CHARSET);

    private final ByteBuffer commandBuffer = ByteBuffer.allocate(MAX_ALLOWED_COMMAND_BYTES);
    private final ByteBuffer payloadBuffer;

    ProtocolParser(int maxPayloadBytes) {
        this.payloadBuffer = ByteBuffer.allocate(maxPayloadBytes);
    }

    ProtocolCommand parseNext(InputStream in) throws IOException {
        ByteBuffer commandBuffer = readUntilDelimiter(in, this.commandBuffer);
        if (commandBuffer == null) {
            return null;
        }

        return parseCommand(commandBuffer, payloadBuffer, in);
    }

    private static ByteBuffer readUntilDelimiter(InputStream in, ByteBuffer buffer)
            throws IOException {
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
                buffer.position(0);
                buffer.limit(payloadLength);
                return buffer.asReadOnlyBuffer().slice();
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
            ByteBuffer command, ByteBuffer payloadBuffer, InputStream in) throws IOException {

        // CONNECT {}\r\n
        if (startsWith(command, CONNECT_COMMAND_BYTES)) {
            return new ProtocolCommand.ConnectCommand();
        }

        // PUB channel1 11\r\nHello John!\r\n
        if (startsWith(command, PUB_COMMAND_BYTES)) {
            int payloadLength = extractPubPayloadLength(command);
            LOG.debug("Received PUB command with payload length: {}", payloadLength);
            ByteBuffer payload = readPubPayload(in, payloadBuffer, payloadLength);
            return new ProtocolCommand.PubCommand(payload);
        }

        // SUB channel1 1\r\n
        if (startsWith(command, SUB_COMMAND_BYTES)) {
            return new ProtocolCommand.SubCommand();
        }

        // PING\r\n
        if (startsWith(command, PING_COMMAND_BYTES)) {
            return new ProtocolCommand.PingCommand();
        }

        // PONG\r\n
        if (startsWith(command, PONG_COMMAND_BYTES)) {
            return new ProtocolCommand.PongCommand();
        }

        return new ProtocolCommand.UnknownCommand();
    }

    private static boolean startsWith(ByteBuffer data, byte[] prefix) {
        if (data.remaining() < prefix.length) {
            return false;
        }

        int startPos = data.position();
        for (int i = 0; i < prefix.length; ++i) {
            if (data.get(startPos + i) != prefix[i]) {
                return false;
            }
        }

        return true;
    }

    private static int extractPubPayloadLength(ByteBuffer command) throws IOException {
        ByteBuffer commandCopy = command.asReadOnlyBuffer();
        byte[] data = new byte[commandCopy.remaining()];
        commandCopy.get(data);

        int index = data.length - 1;
        while (index >= 0 && Character.isWhitespace(data[index])) {
            --index;
        }

        if (index < 0) {
            throw new IOException(
                    "Invalid PUB command format: '" + IOUtils.bytesToString(command) + "'");
        }

        int endExclusive = index + 1;
        while (index >= 0 && !Character.isWhitespace(data[index])) {
            --index;
        }
        int startInclusive = index + 1;

        int tokenCount = 1;
        while (index >= 0) {
            while (index >= 0 && Character.isWhitespace(data[index])) {
                --index;
            }
            if (index < 0) {
                break;
            }

            ++tokenCount;
            while (index >= 0 && !Character.isWhitespace(data[index])) {
                --index;
            }
        }

        if (tokenCount < 3) {
            throw new IOException(
                    "Invalid PUB command format: '" + IOUtils.bytesToString(command) + "'");
        }

        try {
            return Integer.parseInt(
                    new String(
                            data, startInclusive, endExclusive - startInclusive, PROTOCOL_CHARSET));
        } catch (NumberFormatException ex) {
            throw new IOException(
                    "Invalid PUB payload length in command: '"
                            + IOUtils.bytesToString(command)
                            + "'",
                    ex);
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

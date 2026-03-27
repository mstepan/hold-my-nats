package com.github.mstepan.hmnats.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProtocolParserTest {

    @Test
    void parseConnectCommand() throws IOException {
        ProtocolParser parser = new ProtocolParser(1024);
        ByteArrayInputStream in =
                new ByteArrayInputStream("CONNECT {}\r\n".getBytes(StandardCharsets.UTF_8));

        ProtocolCommand command = parser.parseNext(in);

        assertInstanceOf(ProtocolCommand.ConnectCommand.class, command);
    }

    @Test
    void parsePubCommand() throws IOException {
        ProtocolParser parser = new ProtocolParser(1024);
        ByteArrayInputStream in =
                new ByteArrayInputStream(
                        "PUB channel1 5\r\nhello\r\n".getBytes(StandardCharsets.UTF_8));

        ProtocolCommand command = parser.parseNext(in);

        ProtocolCommand.PubCommand pub =
                assertInstanceOf(ProtocolCommand.PubCommand.class, command);
        assertEquals("channel1", pub.subject());
        assertEquals("hello", IOUtils.bytesToString(pub.payload()));
    }

    @Test
    void parseSubCommand() throws IOException {
        ProtocolParser parser = new ProtocolParser(1024);
        ByteArrayInputStream in =
                new ByteArrayInputStream(
                        "SUB channel1 sid123\r\n".getBytes(StandardCharsets.UTF_8));

        ProtocolCommand command = parser.parseNext(in);

        ProtocolCommand.SubCommand sub =
                assertInstanceOf(ProtocolCommand.SubCommand.class, command);
        assertEquals("channel1", sub.subject());
        assertEquals("sid123", sub.sid());
    }

    @Test
    void parseSubCommandWithoutSidShouldFail() {
        ProtocolParser parser = new ProtocolParser(1024);
        ByteArrayInputStream in =
                new ByteArrayInputStream("SUB channel1\r\n".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalStateException.class, () -> parser.parseNext(in));
    }

    @Test
    void parsePingCommand() throws IOException {
        ProtocolParser parser = new ProtocolParser(1024);
        ByteArrayInputStream in =
                new ByteArrayInputStream("PING\r\n".getBytes(StandardCharsets.UTF_8));

        ProtocolCommand command = parser.parseNext(in);

        assertInstanceOf(ProtocolCommand.PingCommand.class, command);
    }

    @Test
    void parsePongCommand() throws IOException {
        ProtocolParser parser = new ProtocolParser(1024);
        ByteArrayInputStream in =
                new ByteArrayInputStream("PONG\r\n".getBytes(StandardCharsets.UTF_8));

        ProtocolCommand command = parser.parseNext(in);

        assertInstanceOf(ProtocolCommand.PongCommand.class, command);
    }

    @Test
    void parseUnknownCommand() throws IOException {
        ProtocolParser parser = new ProtocolParser(1024);
        ByteArrayInputStream in =
                new ByteArrayInputStream("SOMETHING\r\n".getBytes(StandardCharsets.UTF_8));

        ProtocolCommand command = parser.parseNext(in);

        assertInstanceOf(ProtocolCommand.UnknownCommand.class, command);
    }
}

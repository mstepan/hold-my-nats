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

        assertThrows(IOException.class, () -> parser.parseNext(in));
    }
}

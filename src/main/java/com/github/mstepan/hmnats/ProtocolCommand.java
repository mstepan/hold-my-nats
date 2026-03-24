package com.github.mstepan.hmnats;

import java.nio.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

sealed interface ProtocolCommand
        permits ProtocolCommand.ConnectCommand,
                ProtocolCommand.PubCommand,
                ProtocolCommand.SubCommand,
                ProtocolCommand.PingCommand,
                ProtocolCommand.PongCommand,
                ProtocolCommand.UnknownCommand {

    Logger LOG = LoggerFactory.getLogger(ProtocolCommand.class);

    record ConnectCommand() implements ProtocolCommand {}

    record PubCommand(ByteBuffer payload) implements ProtocolCommand {

        public PubCommand {
            payload = payload.asReadOnlyBuffer().slice();
        }

        @Override
        public ByteBuffer payload() {
            return payload.asReadOnlyBuffer().slice();
        }
    }

    record SubCommand() implements ProtocolCommand {}

    record PingCommand() implements ProtocolCommand {}

    record PongCommand() implements ProtocolCommand {}

    record UnknownCommand() implements ProtocolCommand {}

    static void handle(ProtocolCommand protocolCommand) {
        switch (protocolCommand) {
            case ProtocolCommand.ConnectCommand ignored -> LOG.info("Received CONNECT command");
            case ProtocolCommand.PubCommand pub -> {
                ByteBuffer payload = pub.payload();
                LOG.info("Received PUB command with payload length: {}", payload.remaining());
                LOG.info("Received PUB payload content: '{}'", IOUtils.bytesToString(payload));
            }
            case ProtocolCommand.SubCommand ignored -> LOG.info("Received SUB command");
            case ProtocolCommand.PingCommand ignored -> LOG.info("Received PING command");
            case ProtocolCommand.PongCommand ignored -> LOG.info("Received PONG command");
            case ProtocolCommand.UnknownCommand ignored -> LOG.info("Received unknown command");
        }
    }
}

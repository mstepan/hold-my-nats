package com.github.mstepan.hmnats.server;

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

    default void handle() {
        LOG.info("Received {}", representation());
    }

    String representation();

    record ConnectCommand() implements ProtocolCommand {

        @Override
        public String representation() {
            return "CONNECT";
        }
    }

    record PubCommand(String subject, ByteBuffer payload) implements ProtocolCommand {

        public PubCommand {
            payload = payload.asReadOnlyBuffer().slice();
        }

        @Override
        public ByteBuffer payload() {
            return payload.asReadOnlyBuffer().slice();
        }

        @Override
        public String subject() {
            return subject;
        }

        @Override
        public String representation() {
            return "PUB %s %s".formatted(subject, IOUtils.bytesToString(payload));
        }
    }

    record SubCommand(String subject, String sid) implements ProtocolCommand {

        @Override
        public String representation() {
            return "SUB %s %s".formatted(subject, sid);
        }
    }

    record PingCommand() implements ProtocolCommand {

        @Override
        public String representation() {
            return "PING";
        }
    }

    record PongCommand() implements ProtocolCommand {

        @Override
        public String representation() {
            return "PONG";
        }
    }

    record UnknownCommand() implements ProtocolCommand {

        @Override
        public String representation() {
            return "UNKNOWN";
        }
    }
}

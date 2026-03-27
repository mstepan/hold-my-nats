package com.github.mstepan.hmnats.server;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.concurrent.SynchronousQueue;
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

    default void logCommand() {
        LOG.info("Received {}", representation());
    }

    String representation();

    record ConnectCommand() implements ProtocolCommand {

        @Override
        public String representation() {
            return "CONNECT";
        }
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP")
    record PubCommand(String subject, byte[] payload) implements ProtocolCommand {

        public Message toMessage() {
            return new Message(subject, payload);
        }

        @Override
        public byte[] payload() {
            return payload;
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

        public Subscriber toSubscriber() {
            return new Subscriber(subject, new SynchronousQueue<>());
        }

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

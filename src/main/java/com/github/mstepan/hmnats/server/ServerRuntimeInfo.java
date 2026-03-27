package com.github.mstepan.hmnats.server;

import java.util.Objects;
import java.util.UUID;

public final class ServerRuntimeInfo {

    private static final String DEFAULT_VERSION = "1.2.0";
    private static final int DEFAULT_PROTO = 1;
    private static final int DEFAULT_MAX_PAYLOAD_BYTES = 1_048_576; // 1M in bytes
    private static final String DEFAULT_HOST = "0.0.0.0";
    private static final int DEFAULT_PORT = 4222;

    private static final ServerRuntimeInfo INSTANCE = new ServerRuntimeInfo();

    private record ListeningAddress(String host, int port) {

        private static final int MIN_PORT = 0;
        private static final int MAX_PORT = 65_535;

        private ListeningAddress {
            Objects.requireNonNull(host, "host should not be null");
            if (port < MIN_PORT || port > MAX_PORT) {
                throw new IllegalArgumentException("Invalid TCP port value: " + port);
            }
        }
    }

    private final String serverId;
    private final String version;
    private final int proto;
    private final String javaInfo;
    private final int maxPayload;
    private volatile ListeningAddress listeningAddress;

    private ServerRuntimeInfo() {
        serverId = UUID.randomUUID().toString();
        version = DEFAULT_VERSION;
        proto = DEFAULT_PROTO;
        javaInfo = System.getProperty("java.version", "unknown");
        maxPayload = DEFAULT_MAX_PAYLOAD_BYTES;
        listeningAddress = new ListeningAddress(DEFAULT_HOST, DEFAULT_PORT);
    }

    public static ServerRuntimeInfo getInstance() {
        return INSTANCE;
    }

    public void updateListeningAddress(String host, int port) {
        listeningAddress = new ListeningAddress(host, port);
    }

    public String serverId() {
        return serverId;
    }

    public String version() {
        return version;
    }

    public int proto() {
        return proto;
    }

    public String javaInfo() {
        return javaInfo;
    }

    public String host() {
        return listeningAddress.host();
    }

    public int port() {
        return listeningAddress.port();
    }

    public int maxPayload() {
        return maxPayload;
    }
}

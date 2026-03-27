package com.github.mstepan.hmnats.server;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

final class IOUtils {

    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    private IOUtils() {
        throw new AssertionError("Can't instantiate utility-only class");
    }

    static String bytesToString(ByteBuffer payload) {
        ByteBuffer payloadCopy = payload.asReadOnlyBuffer();
        byte[] data = new byte[payloadCopy.remaining()];
        payloadCopy.get(data);
        return new String(data, DEFAULT_CHARSET);
    }
}

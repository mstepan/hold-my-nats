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

    static String[] splitIntoTokens(String command) {
        assert command != null : "null 'command' parameter";

        int tokensCount = countTokens(command);

        String[] tokens = new String[tokensCount];
        if (tokensCount == 0) {
            return tokens;
        }

        int tokenIdx = 0;
        int tokenStart = -1;

        for (int idx = 0; idx < command.length(); ++idx) {
            if (isWhitespace(command.charAt(idx))) {
                if (tokenStart != -1) {
                    tokens[tokenIdx++] = command.substring(tokenStart, idx);
                    tokenStart = -1;
                }
            } else if (tokenStart == -1) {
                tokenStart = idx;
            }
        }

        if (tokenStart != -1) {
            tokens[tokenIdx] = command.substring(tokenStart);
        }

        return tokens;
    }

    private static int countTokens(String data) {
        assert data != null : "null 'data' parameter";

        int tokensCount = 0;
        char prev = ' ';

        for (int idx = 0; idx < data.length(); ++idx) {
            char cur = data.charAt(idx);

            if (isWhitespace(prev) && notWhitespace(cur)) {
                ++tokensCount;
            }

            prev = cur;
        }

        return tokensCount;
    }

    private static boolean isWhitespace(char value) {
        return value == ' ' || value == '\t';
    }

    private static boolean notWhitespace(char value) {
        return !isWhitespace(value);
    }
}

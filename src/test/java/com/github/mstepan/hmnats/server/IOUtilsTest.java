package com.github.mstepan.hmnats.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class IOUtilsTest {

    @Test
    void splitIntoTokensMultipleSpaces() {
        String[] tokens = IOUtils.splitIntoTokens("  SUB   channel1   sid123  ");

        assertArrayEquals(new String[] {"SUB", "channel1", "sid123"}, tokens);
    }

    @Test
    void splitIntoTokensSingleSpace() {
        String[] tokens = IOUtils.splitIntoTokens("SUB channel1 123");

        assertArrayEquals(new String[] {"SUB", "channel1", "123"}, tokens);
    }
}

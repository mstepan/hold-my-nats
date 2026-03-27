package com.github.mstepan.hmnats.server;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = "EI_EXPOSE_REP")
public record Message(String subject, byte[] payload) {}

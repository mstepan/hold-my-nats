package com.github.mstepan.hmnats.server;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.concurrent.SynchronousQueue;

@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification =
                "Subscriber intentionally exposes and stores the shared queue reference for hand-off"
                        + " semantics between router and client thread")
public record Subscriber(String subject, SynchronousQueue<Message> singleSlot) {

    Message get() throws InterruptedException {
        return singleSlot().take();
    }

    void put(Message message) throws InterruptedException {
        singleSlot().put(message);
    }
}

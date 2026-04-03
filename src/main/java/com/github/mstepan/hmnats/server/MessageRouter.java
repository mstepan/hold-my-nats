package com.github.mstepan.hmnats.server;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MessageRouter implements AutoCloseable {

    private static final int MAX_SUBSCRIBERS_PER_SUBJECT = 1024;
    private static final long PUBLISHER_THREAD_JOIN_TIMEOUT_MILLIS = 1_000L;

    private static final Logger LOG = LoggerFactory.getLogger(MessageRouter.class);

    private volatile Thread publisherThread;

    // ArrayBlockingQueue provides backpressure.
    // If the consumer cannot keep up and the queue fills up,
    // publishers will block on put() until space is available.
    private final BlockingQueue<Message> messageQueue = new ArrayBlockingQueue<>(1024);

    private final Map<String, Queue<Subscriber>> activeSubscribers = new ConcurrentHashMap<>();

    private MessageRouter() {}

    static MessageRouter newBootstrapped() {
        MessageRouter router = new MessageRouter();
        router.bootstrap();
        return router;
    }

    private void bootstrap() {
        // publisher thread is the core of message publishing and it's only ONE as of now, so better
        // to start as a PLATFORM thread
        // TODO: we should spin up more than ONE thread in future and do HASH routing
        publisherThread = Thread.ofPlatform().unstarted(new MessagePublishingLoop());
        publisherThread.start();

        LOG.info("Message router started");
    }

    private final class MessagePublishingLoop implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    final Message message = messageQueue.take();

                    Queue<Subscriber> allSubscribers = activeSubscribers.get(message.subject());

                    if (allSubscribers == null) {
                        LOG.debug("No subscribers for subject {}", message.subject());
                    } else {
                        for (Subscriber subscriber : allSubscribers) {
                            subscriber.put(message);
                        }
                    }
                } catch (InterruptedException interEx) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    void publishMessage(Message message) throws InterruptedException {
        messageQueue.put(message);
    }

    void createSubscription(Subscriber subscriber) {
        activeSubscribers.compute(
                subscriber.subject(),
                (keyNotUsed, subQueue) -> {
                    if (subQueue == null) {
                        // We should use ArrayBlockingQueue here to remove possibility of infinity
                        // queue
                        subQueue = new ArrayBlockingQueue<>(MAX_SUBSCRIBERS_PER_SUBJECT);
                    }

                    if (subQueue.size() == MAX_SUBSCRIBERS_PER_SUBJECT) {
                        throw new IllegalStateException("Subscription already full");
                    }

                    subQueue.add(subscriber);

                    return subQueue;
                });
    }

    void terminateSubscription(Subscriber subscriber) {
        activeSubscribers.computeIfPresent(
                subscriber.subject(),
                (keyNotUsed, subQueue) -> {
                    subQueue.remove(subscriber);
                    if (subQueue.isEmpty()) {
                        return null;
                    }
                    return subQueue;
                });
    }

    @Override
    public void close() {
        final Thread curPublisherThread = publisherThread;
        if (curPublisherThread == null) {
            return;
        }

        curPublisherThread.interrupt();

        try {
            curPublisherThread.join(PUBLISHER_THREAD_JOIN_TIMEOUT_MILLIS);
        } catch (InterruptedException interruptedEx) {
            Thread.currentThread().interrupt();
        }

        if (curPublisherThread.isAlive()) {
            LOG.warn(
                    "Message router thread wasn't terminate in {} ms",
                    PUBLISHER_THREAD_JOIN_TIMEOUT_MILLIS);
        }

        LOG.info("Message router terminated");
    }
}

package com.github.mstepan.hmnats.server;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MessageRouter {

    private static final int MAX_SUBSCRIBERS_PER_SUBJECT = 1024;

    private static final Logger LOG = LoggerFactory.getLogger(MessageRouter.class);

    private volatile Thread pubThread;

    // ArrayBlockingQueue provides backpressure.
    // If the consumer cannot keep up and the queue fills up,
    // publishers will block on put() until space is available.
    private final BlockingQueue<Message> newMessagesQueue = new ArrayBlockingQueue<>(1024);

    private final Map<String, Queue<Subscriber>> subscribers = new ConcurrentHashMap<>();

    void publishMessage(Message message) throws InterruptedException {
        newMessagesQueue.put(message);
    }

    void createSubscription(Subscriber subscriber) {
        subscribers.compute(
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
        subscribers.computeIfPresent(
                subscriber.subject(),
                (keyNotUsed, subQueue) -> {
                    subQueue.remove(subscriber);
                    if (subQueue.isEmpty()) {
                        return null;
                    }
                    return subQueue;
                });
    }

    public void bootstrap() {
        pubThread =
                new Thread(
                        () -> {
                            while (!Thread.currentThread().isInterrupted()) {
                                try {
                                    final Message message = newMessagesQueue.take();

                                    Queue<Subscriber> allSubscribers =
                                            subscribers.get(message.subject());

                                    if (allSubscribers == null) {
                                        LOG.debug(
                                                "No subscribers for subject {}", message.subject());
                                    } else {
                                        for (Subscriber subscriber : allSubscribers) {
                                            subscriber.put(message);
                                        }
                                    }
                                } catch (InterruptedException interEx) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        });
        pubThread.start();
    }

    public void shutdown() {
        pubThread.interrupt();
    }
}

package com.github.mstepan.hmnats.server;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MessageRouter {

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

    void registerSubscriber(Subscriber subscriber) {
        subscribers.compute(
                subscriber.subject(),
                (keyNotUsed, subQueue) -> {
                    if (subQueue == null) {
                        subQueue = new LinkedBlockingQueue<>();
                    }

                    subQueue.add(subscriber);

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

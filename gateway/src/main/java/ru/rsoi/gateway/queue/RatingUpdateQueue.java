package ru.rsoi.gateway.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class RatingUpdateQueue {

    private static final Logger log = LoggerFactory.getLogger(RatingUpdateQueue.class);

    private final BlockingQueue<RatingUpdateTask> queue = new LinkedBlockingQueue<>();
    private final Set<String> processedKeys = ConcurrentHashMap.newKeySet();

    public void enqueue(RatingUpdateTask task) {
        if (processedKeys.contains(task.idempotencyKey())) {
            log.debug("Task already processed, skipping: {}", task.idempotencyKey());
            return;
        }
        queue.offer(task);
        log.info("Enqueued rating update task: {} delta={} key={}",
                task.username(), task.delta(), task.idempotencyKey());
    }

    public RatingUpdateTask poll() throws InterruptedException {
        return queue.take();
    }

    public void markProcessed(String key) {
        processedKeys.add(key);
    }

    public boolean isProcessed(String key) {
        return processedKeys.contains(key);
    }

    public int size() {
        return queue.size();
    }
}
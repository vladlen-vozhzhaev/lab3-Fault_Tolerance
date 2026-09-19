package ru.rsoi.gateway.queue;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.rsoi.gateway.client.RatingClient;

@Component
public class RatingRetryWorker {

    private static final Logger log = LoggerFactory.getLogger(RatingRetryWorker.class);

    private final RatingUpdateQueue queue;
    private final RatingClient ratingClient;

    public RatingRetryWorker(RatingUpdateQueue queue, RatingClient ratingClient) {
        this.queue = queue;
        this.ratingClient = ratingClient;
    }

    @PostConstruct
    void start() {
        Thread t = new Thread(this::loop, "rating-retry-worker");
        t.setDaemon(true);
        t.start();
        log.info("RatingRetryWorker started");
    }

    private void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            RatingUpdateTask task;
            try {
                task = queue.poll();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            if (queue.isProcessed(task.idempotencyKey())) {
                continue;
            }

            try {
                ratingClient.changeRating(task.username(), task.delta());
                queue.markProcessed(task.idempotencyKey());
                log.info("Rating updated for {} delta={} key={}",
                        task.username(), task.delta(), task.idempotencyKey());
            } catch (Exception e) {
                log.warn("Failed to update rating ({}), retry in 2s: {}",
                        task.idempotencyKey(), e.getMessage());
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                // не помечаем processed — вернём в очередь
                queue.enqueue(task);
            }
        }
    }
}
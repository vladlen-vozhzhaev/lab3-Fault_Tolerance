package ru.rsoi.gateway.queue;

public record RatingUpdateTask(String username, int delta, String idempotencyKey) {}
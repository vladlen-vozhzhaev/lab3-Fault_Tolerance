package ru.rsoi.gateway.cb;

public class CircuitBreakerOpenException extends RuntimeException {
    public CircuitBreakerOpenException(String name) {
        super("Circuit breaker is OPEN: " + name);
    }
}
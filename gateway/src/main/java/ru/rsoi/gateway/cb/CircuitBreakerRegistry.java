package ru.rsoi.gateway.cb;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Component
public class CircuitBreakerRegistry {

    private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    public CircuitBreaker circuitBreaker(String name) {
        return breakers.computeIfAbsent(name, n ->
                new CircuitBreaker(n, CircuitBreakerConfig.defaults()));
    }

    public Map<String, CircuitBreaker> all() {
        return breakers;
    }
}
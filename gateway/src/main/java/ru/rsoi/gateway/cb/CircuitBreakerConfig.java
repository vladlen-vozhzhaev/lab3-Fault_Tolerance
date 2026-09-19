package ru.rsoi.gateway.cb;

public record CircuitBreakerConfig(
        int slidingWindowSize,
        int minimumNumberOfCalls,
        float failureRateThreshold,
        long waitDurationInOpenStateMs,
        int permittedNumberOfCallsInHalfOpenState
) {
    public static CircuitBreakerConfig defaults() {
        return new CircuitBreakerConfig(
                10,     // окно
                2,      // минимум вызовов
                50f,    // 50% ошибок
                3000,   // 3 сек в OPEN
                2       // 2 пробных запроса в HALF_OPEN
        );
    }
}
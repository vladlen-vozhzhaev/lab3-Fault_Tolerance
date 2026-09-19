package ru.rsoi.gateway.cb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    private final String name;
    private final CircuitBreakerConfig config;

    private volatile CircuitBreakerState state = CircuitBreakerState.CLOSED;

    /** Кольцевой буфер последних вызовов: true = успех, false = ошибка. */
    private final Deque<Boolean> window = new ArrayDeque<>();

    /** Счётчик ошибок в HALF_OPEN (для решения CLOSED vs OPEN). */
    private final AtomicInteger halfOpenFailures = new AtomicInteger(0);

    /** Сколько пробных вызовов уже пропущено в HALF_OPEN. */
    private final AtomicInteger halfOpenCalls = new AtomicInteger(0);

    /** Момент, когда CB перешёл в OPEN. */
    private final AtomicLong openedAt = new AtomicLong(0);

    public CircuitBreaker(String name, CircuitBreakerConfig config) {
        this.name = name;
        this.config = config;
    }

    public CircuitBreakerState getState() {
        return state;
    }

    public String getName() {
        return name;
    }

    /**
     * Если CB в OPEN — бросает CircuitBreakerOpenException, действие не вызывается.
     * Если CB в CLOSED или HALF_OPEN — вызывает действие и регистрирует результат.
     */
    public <T> T execute(Supplier<T> action) {
        CircuitBreakerState current = currentState();
        if (current == CircuitBreakerState.OPEN) {
            throw new CircuitBreakerOpenException(name);
        }

        // HALF_OPEN: ограничиваем число пробных запросов
        if (current == CircuitBreakerState.HALF_OPEN) {
            int calls = halfOpenCalls.incrementAndGet();
            if (calls > config.permittedNumberOfCallsInHalfOpenState()) {
                // больше пробных не пускаем — снова OPEN
                transitionToOpen();
                throw new CircuitBreakerOpenException(name);
            }
        }

        try {
            T result = action.get();
            onSuccess();
            return result;
        } catch (RuntimeException e) {
            onFailure();
            throw e;
        }
    }

    // ---------- внутренняя логика ----------

    /**
     * Возвращает актуальное состояние, проверяя истёк ли waitDurationInOpenState.
     * Если CB в OPEN и время вышло — переходит в HALF_OPEN.
     */
    private synchronized CircuitBreakerState currentState() {
        if (state == CircuitBreakerState.OPEN
                && System.currentTimeMillis() - openedAt.get() >= config.waitDurationInOpenStateMs()) {
            state = CircuitBreakerState.HALF_OPEN;
            halfOpenCalls.set(0);
            halfOpenFailures.set(0);
            log.info("CircuitBreaker [{}]: OPEN -> HALF_OPEN", name);
        }
        return state;
    }

    private synchronized void onSuccess() {
        switch (state) {
            case CLOSED -> recordWindow(true);
            case HALF_OPEN -> {
                // все пробные успешны → CLOSED
                log.info("CircuitBreaker [{}]: HALF_OPEN -> CLOSED (success)", name);
                state = CircuitBreakerState.CLOSED;
                window.clear();
                window.add(true);
                halfOpenCalls.set(0);
                halfOpenFailures.set(0);
            }
            case OPEN -> { /* уже обработано в execute */ }
        }
    }

    private synchronized void onFailure() {
        switch (state) {
            case CLOSED -> recordWindow(false);
            case HALF_OPEN -> {
                halfOpenFailures.incrementAndGet();
                log.info("CircuitBreaker [{}]: HALF_OPEN -> OPEN (failure)", name);
                transitionToOpen();
            }
            case OPEN -> { /* не должно случиться */ }
        }
    }

    /** Добавляет результат в окно; при переполнении вытесняет старейший. */
    private void recordWindow(boolean success) {
        window.addLast(success);
        while (window.size() > config.slidingWindowSize()) {
            window.removeFirst();
        }
        if (!success && window.size() >= config.minimumNumberOfCalls()) {
            long failures = window.stream().filter(b -> !b).count();
            float rate = (float) failures / window.size() * 100f;
            if (rate >= config.failureRateThreshold()) {
                log.info("CircuitBreaker [{}]: CLOSED -> OPEN (failure rate {}%)", name, rate);
                transitionToOpen();
            }
        }
    }

    private void transitionToOpen() {
        state = CircuitBreakerState.OPEN;
        openedAt.set(System.currentTimeMillis());
        window.clear();
        halfOpenCalls.set(0);
        halfOpenFailures.set(0);
    }

    // ---------- для тестов/health ----------

    public int windowSize() {
        return window.size();
    }

    public boolean isOpen() {
        return state == CircuitBreakerState.OPEN;
    }
}
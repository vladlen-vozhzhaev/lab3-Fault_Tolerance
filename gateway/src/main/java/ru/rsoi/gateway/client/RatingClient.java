package ru.rsoi.gateway.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.rsoi.gateway.exception.ServiceUnavailableException;

import java.util.Map;

@Component
public class RatingClient {

    private final RestClient client;
    private final String baseUrl;
    private final CircuitBreaker cb;

    public RatingClient(RestClient client,
                        CircuitBreakerRegistry registry,
                        @Value("${rating-url}") String baseUrl) {
        this.client = client;
        this.baseUrl = baseUrl;
        this.cb = registry.circuitBreaker("ratingClient");
    }

    /** GET /api/v1/rating */
    public Map<String, Object> getRating(String username) {
        return CircuitBreaker.decorateSupplier(cb, () ->
                client.get()
                        .uri(baseUrl + "/api/v1/rating")
                        .header("X-User-Name", username)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {})
        ).get();
    }

    /** POST /api/v1/rating?delta=N — используется при возврате книги. */
    public void changeRating(String username, int delta) {
        client.post()
                .uri(baseUrl + "/api/v1/rating?delta={d}", delta)
                .header("X-User-Name", username)
                .retrieve()
                .toBodilessEntity();
    }

    /** Проверка доступности (не бросает, если CB в OPEN). */
    public boolean isAvailable() {
        return cb.getState() != CircuitBreaker.State.OPEN;
    }
}
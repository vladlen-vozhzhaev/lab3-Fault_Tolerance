package ru.rsoi.gateway.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.rsoi.gateway.cb.CircuitBreaker;
import ru.rsoi.gateway.cb.CircuitBreakerRegistry;

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

    public Map<String, Object> getRating(String username) {
        return cb.execute(() ->
                client.get()
                        .uri(baseUrl + "/api/v1/rating")
                        .header("X-User-Name", username)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {})
        );
    }

    public void changeRating(String username, int delta) {
        // НЕ через CB: изменяющая операция, вызывается из очереди
        client.post()
                .uri(baseUrl + "/api/v1/rating?delta={d}", delta)
                .header("X-User-Name", username)
                .retrieve()
                .toBodilessEntity();
    }
}
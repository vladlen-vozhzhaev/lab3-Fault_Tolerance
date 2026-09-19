package ru.rsoi.gateway.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.rsoi.gateway.cb.CircuitBreaker;
import ru.rsoi.gateway.cb.CircuitBreakerRegistry;

import java.util.List;
import java.util.Map;

@Component
public class ReservationClient {

    private final RestClient client;
    private final String baseUrl;
    private final CircuitBreaker cb;

    public ReservationClient(RestClient client,
                             CircuitBreakerRegistry registry,
                             @Value("${reservation-url}") String baseUrl) {
        this.client = client;
        this.baseUrl = baseUrl;
        this.cb = registry.circuitBreaker("reservationClient");
    }

    public List<Map<String, Object>> list(String username) {
        return cb.execute(() ->
                client.get()
                        .uri(baseUrl + "/api/v1/reservations")
                        .header("X-User-Name", username)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
        );
    }

    public Long countActive(String username) {
        return cb.execute(() ->
                client.get()
                        .uri(baseUrl + "/api/v1/reservations/count-active")
                        .header("X-User-Name", username)
                        .retrieve()
                        .body(Long.class)
        );
    }

    public Map<String, Object> create(String username, Map<String, Object> body) {
        return client.post()
                .uri(baseUrl + "/api/v1/reservations")
                .header("X-User-Name", username)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    public void returnBook(String username, String uid, Map<String, Object> body) {
        client.post()
                .uri(baseUrl + "/api/v1/reservations/{u}/return", uid)
                .header("X-User-Name", username)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
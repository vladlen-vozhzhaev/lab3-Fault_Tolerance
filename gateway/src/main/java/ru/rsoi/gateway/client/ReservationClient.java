package ru.rsoi.gateway.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

    /** GET /api/v1/reservations */
    public List<Map<String, Object>> list(String username) {
        return CircuitBreaker.decorateSupplier(cb, () ->
                client.get()
                        .uri(baseUrl + "/api/v1/reservations")
                        .header("X-User-Name", username)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
        ).get();
    }

    /** GET /api/v1/reservations/count-active */
    public Long countActive(String username) {
        return CircuitBreaker.decorateSupplier(cb, () ->
                client.get()
                        .uri(baseUrl + "/api/v1/reservations/count-active")
                        .header("X-User-Name", username)
                        .retrieve()
                        .body(Long.class)
        ).get();
    }

    /** POST — создать бронь (не через CB). */
    public Map<String, Object> create(String username, Map<String, Object> body) {
        return client.post()
                .uri(baseUrl + "/api/v1/reservations")
                .header("X-User-Name", username)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    /** POST — вернуть книгу (не через CB). */
    public void returnBook(String username, String reservationUid, Map<String, Object> body) {
        client.post()
                .uri(baseUrl + "/api/v1/reservations/{u}/return", reservationUid)
                .header("X-User-Name", username)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
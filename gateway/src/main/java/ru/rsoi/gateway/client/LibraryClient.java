package ru.rsoi.gateway.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class LibraryClient {

    private final RestClient client;
    private final String baseUrl;
    private final CircuitBreaker cb;

    public LibraryClient(RestClient client,
                         CircuitBreakerRegistry registry,
                         @Value("${library-url}") String baseUrl) {
        this.client = client;
        this.baseUrl = baseUrl;
        this.cb = registry.circuitBreaker("libraryClient");
    }

    /** GET /api/v1/libraries?city&page&size */
    public Map<String, Object> getLibraries(String city, int page, int size) {
        return CircuitBreaker.decorateSupplier(cb, () ->
                client.get()
                        .uri(baseUrl + "/api/v1/libraries?city={c}&page={p}&size={s}", city, page, size)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {})
        ).get();
    }

    /** GET /api/v1/libraries/{uid}/books?page&size&showAll */
    public Map<String, Object> getBooks(String libraryUid, int page, int size, boolean showAll) {
        return CircuitBreaker.decorateSupplier(cb, () ->
                client.get()
                        .uri(baseUrl + "/api/v1/libraries/{u}/books?page={p}&size={s}&showAll={a}",
                                libraryUid, page, size, showAll)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {})
        ).get();
    }

    /** GET /api/v1/libraries/{uid} — используется при обогащении ответа. */
    public Map<String, Object> getLibrary(String libraryUid) {
        return CircuitBreaker.decorateSupplier(cb, () ->
                client.get()
                        .uri(baseUrl + "/api/v1/libraries/{u}", libraryUid)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {})
        ).get();
    }

    /** GET /api/v1/books/{uid} — используется при обогащении ответа. */
    public Map<String, Object> getBook(String bookUid) {
        return CircuitBreaker.decorateSupplier(cb, () ->
                client.get()
                        .uri(baseUrl + "/api/v1/books/{b}", bookUid)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {})
        ).get();
    }

    /** POST — резерв книги (не через CB, это изменяющая операция). */
    public void reserve(String libraryUid, String bookUid) {
        client.post()
                .uri(baseUrl + "/api/v1/libraries/{l}/books/{b}/reserve", libraryUid, bookUid)
                .retrieve()
                .toBodilessEntity();
    }

    /** POST — возврат книги (не через CB). */
    public void returnBook(String libraryUid, String bookUid) {
        client.post()
                .uri(baseUrl + "/api/v1/libraries/{l}/books/{b}/return", libraryUid, bookUid)
                .retrieve()
                .toBodilessEntity();
    }
}
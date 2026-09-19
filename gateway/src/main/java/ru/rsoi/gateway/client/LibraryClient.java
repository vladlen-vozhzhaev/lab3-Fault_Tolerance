package ru.rsoi.gateway.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.rsoi.gateway.cb.CircuitBreaker;
import ru.rsoi.gateway.cb.CircuitBreakerRegistry;

import java.util.Map;
import java.util.UUID;

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

    // --- через CB (чтение) ---

    public Map<String, Object> getLibraries(String city, int page, int size) {
        return cb.execute(() ->
                client.get()
                        .uri(baseUrl + "/api/v1/libraries?city={c}&page={p}&size={s}", city, page, size)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {})
        );
    }

    public Map<String, Object> getBooks(String libraryUid, int page, int size, boolean showAll) {
        return cb.execute(() ->
                client.get()
                        .uri(baseUrl + "/api/v1/libraries/{u}/books?page={p}&size={s}&showAll={a}",
                                libraryUid, page, size, showAll)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {})
        );
    }

    public Map<String, Object> getLibrary(String libraryUid) {
        return cb.execute(() ->
                client.get()
                        .uri(baseUrl + "/api/v1/libraries/{u}", libraryUid)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {})
        );
    }

    public Map<String, Object> getBook(String bookUid) {
        return cb.execute(() ->
                client.get()
                        .uri(baseUrl + "/api/v1/books/{b}", bookUid)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {})
        );
    }

    // --- UUID-перегрузки ---

    public Map<String, Object> getBook(UUID bookUid) { return getBook(bookUid.toString()); }
    public Map<String, Object> getLibrary(UUID libraryUid) { return getLibrary(libraryUid.toString()); }

    // --- без CB (изменяющие) ---

    public void reserve(String libraryUid, String bookUid) {
        client.post()
                .uri(baseUrl + "/api/v1/libraries/{l}/books/{b}/reserve", libraryUid, bookUid)
                .retrieve()
                .toBodilessEntity();
    }

    public void returnBook(String libraryUid, String bookUid) {
        client.post()
                .uri(baseUrl + "/api/v1/libraries/{l}/books/{b}/return", libraryUid, bookUid)
                .retrieve()
                .toBodilessEntity();
    }

    public void reserve(UUID libraryUid, UUID bookUid) { reserve(libraryUid.toString(), bookUid.toString()); }
    public void returnBook(UUID libraryUid, UUID bookUid) { returnBook(libraryUid.toString(), bookUid.toString()); }
}
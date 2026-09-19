package ru.rsoi.gateway.controller;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rsoi.gateway.client.LibraryClient;
import ru.rsoi.gateway.client.RatingClient;
import ru.rsoi.gateway.client.ReservationClient;
import ru.rsoi.gateway.dto.ReturnBookRequest;
import ru.rsoi.gateway.dto.TakeBookRequest;
import ru.rsoi.gateway.exception.ServiceUnavailableException;
import ru.rsoi.gateway.queue.RatingUpdateQueue;
import ru.rsoi.gateway.queue.RatingUpdateTask;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class GatewayController {

    private static final Logger log = LoggerFactory.getLogger(GatewayController.class);

    private static final String RATING_UNAVAILABLE = "Bonus Service unavailable";
    private static final String LIBRARY_UNAVAILABLE = "Library Service unavailable";
    private static final String RESERVATION_UNAVAILABLE = "Reservation Service unavailable";

    private final RatingClient ratingClient;
    private final LibraryClient libraryClient;
    private final ReservationClient reservationClient;
    private final RatingUpdateQueue ratingQueue;

    public GatewayController(RatingClient ratingClient,
                             LibraryClient libraryClient,
                             ReservationClient reservationClient,
                             RatingUpdateQueue ratingQueue) {
        this.ratingClient = ratingClient;
        this.libraryClient = libraryClient;
        this.reservationClient = reservationClient;
        this.ratingQueue = ratingQueue;
    }

    // =========================================================
    // GET /api/v1/libraries
    // =========================================================
    @GetMapping("/libraries")
    public ResponseEntity<Object> libraries(@RequestParam("city") String city,
                                            @RequestParam(value = "page", defaultValue = "1") int page,
                                            @RequestParam(value = "size", defaultValue = "10") int size) {
        try {
            return ResponseEntity.ok(libraryClient.getLibraries(city, page, size));
        } catch (CallNotPermittedException | ServiceUnavailableException e) {
            log.warn("Circuit breaker OPEN for libraryClient: {}", e.getMessage());
            throw new ServiceUnavailableException(LIBRARY_UNAVAILABLE);
        } catch (Exception e) {
            log.error("Error calling library service", e);
            throw new ServiceUnavailableException(LIBRARY_UNAVAILABLE);
        }
    }

    // =========================================================
    // GET /api/v1/libraries/{libraryUid}/books
    // =========================================================
    @GetMapping("/libraries/{libraryUid}/books")
    public ResponseEntity<Object> books(@PathVariable String libraryUid,
                                        @RequestParam(value = "page", defaultValue = "1") int page,
                                        @RequestParam(value = "size", defaultValue = "25") int size,
                                        @RequestParam(value = "showAll", defaultValue = "false") boolean showAll) {
        try {
            return ResponseEntity.ok(libraryClient.getBooks(libraryUid, page, size, showAll));
        } catch (CallNotPermittedException | ServiceUnavailableException e) {
            log.warn("Circuit breaker OPEN for libraryClient: {}", e.getMessage());
            throw new ServiceUnavailableException(LIBRARY_UNAVAILABLE);
        } catch (Exception e) {
            log.error("Error calling library service", e);
            throw new ServiceUnavailableException(LIBRARY_UNAVAILABLE);
        }
    }

    // =========================================================
    // GET /api/v1/rating
    // =========================================================
    @GetMapping("/rating")
    public ResponseEntity<Object> rating(@RequestHeader("X-User-Name") String username) {
        try {
            return ResponseEntity.ok(ratingClient.getRating(username));
        } catch (CallNotPermittedException | ServiceUnavailableException e) {
            log.warn("Circuit breaker OPEN for ratingClient: {}", e.getMessage());
            throw new ServiceUnavailableException(RATING_UNAVAILABLE);
        } catch (Exception e) {
            log.error("Error calling rating service", e);
            throw new ServiceUnavailableException(RATING_UNAVAILABLE);
        }
    }

    // =========================================================
    // GET /api/v1/reservations
    // =========================================================
    @GetMapping("/reservations")
    public ResponseEntity<List<Map<String, Object>>> listReservations(@RequestHeader("X-User-Name") String username) {
        List<Map<String, Object>> rows;
        try {
            rows = reservationClient.list(username);
        } catch (CallNotPermittedException | ServiceUnavailableException e) {
            log.warn("Circuit breaker OPEN for reservationClient: {}", e.getMessage());
            throw new ServiceUnavailableException(RESERVATION_UNAVAILABLE);
        } catch (Exception e) {
            log.error("Error calling reservation service", e);
            throw new ServiceUnavailableException(RESERVATION_UNAVAILABLE);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> r : rows) {
                Map<String, Object> book;
                Map<String, Object> library;
                try {
                    book = libraryClient.getBook((String) r.get("bookUid"));
                } catch (Exception e) {
                    // Library недоступен — некритичный источник для списка
                    book = Map.of("bookUid", r.get("bookUid"));
                }
                try {
                    library = libraryClient.getLibrary((String) r.get("libraryUid"));
                } catch (Exception e) {
                    library = Map.of("libraryUid", r.get("libraryUid"));
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("reservationUid", r.get("reservationUid"));
                item.put("status", r.get("status"));
                item.put("startDate", r.get("startDate"));
                item.put("tillDate", r.get("tillDate"));
                item.put("book", extractBook(book));
                item.put("library", extractLibrary(library));
                out.add(item);
            }
        }
        return ResponseEntity.ok(out);
    }

    // =========================================================
    // POST /api/v1/reservations — взять книгу
    // =========================================================
    @PostMapping("/reservations")
    public ResponseEntity<?> takeBook(@RequestHeader("X-User-Name") String username,
                                      @RequestBody TakeBookRequest req) {

        // 1) Rating — КРИТИЧНЫЙ источник для этой операции.
        Map<String, Object> rating;
        try {
            rating = ratingClient.getRating(username);
        } catch (CallNotPermittedException | ServiceUnavailableException e) {
            log.warn("Circuit breaker OPEN for ratingClient: {}", e.getMessage());
            throw new ServiceUnavailableException(RATING_UNAVAILABLE);
        } catch (Exception e) {
            log.error("Error calling rating service", e);
            throw new ServiceUnavailableException(RATING_UNAVAILABLE);
        }
        int stars = ((Number) rating.get("stars")).intValue();

        // 2) Reservation — количество активных.
        long rented;
        try {
            Long active = reservationClient.countActive(username);
            rented = active == null ? 0 : active;
        } catch (CallNotPermittedException | ServiceUnavailableException e) {
            log.warn("Circuit breaker OPEN for reservationClient: {}", e.getMessage());
            throw new ServiceUnavailableException(RESERVATION_UNAVAILABLE);
        } catch (Exception e) {
            log.error("Error calling reservation service", e);
            throw new ServiceUnavailableException(RESERVATION_UNAVAILABLE);
        }

        if (rented >= stars) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "User cannot take more books"));
        }

        // 3) Library — резерв.
        try {
            libraryClient.reserve(req.libraryUid().toString(), req.bookUid().toString());
        } catch (Exception e) {
            log.error("Reserve failed", e);
            throw new ServiceUnavailableException(LIBRARY_UNAVAILABLE);
        }

        // 4) Reservation — создать бронь.
        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("bookUid", req.bookUid());
        createBody.put("libraryUid", req.libraryUid());
        createBody.put("tillDate", req.tillDate());

        Map<String, Object> reservation;
        try {
            reservation = reservationClient.create(username, createBody);
        } catch (Exception e) {
            log.error("Create reservation failed, compensating library", e);
            // Компенсация: вернуть книгу в Library.
            try {
                libraryClient.returnBook(req.libraryUid().toString(), req.bookUid().toString());
            } catch (Exception comp) {
                log.error("Compensation failed", comp);
            }
            throw new ServiceUnavailableException(RESERVATION_UNAVAILABLE);
        }

        // 5) Обогащение (Library — некритичный источник).
        Map<String, Object> book;
        Map<String, Object> library;
        try {
            book = libraryClient.getBook(req.bookUid().toString());
            library = libraryClient.getLibrary(req.libraryUid().toString());
        } catch (Exception e) {
            log.warn("Enrichment failed, using stubs", e);
            book = Map.of("bookUid", req.bookUid());
            library = Map.of("libraryUid", req.libraryUid());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("reservationUid", reservation.get("reservationUid"));
        response.put("status", reservation.get("status"));
        response.put("startDate", reservation.get("startDate"));
        response.put("tillDate", reservation.get("tillDate"));
        response.put("book", extractBook(book));
        response.put("library", extractLibrary(library));
        response.put("rating", Map.of("stars", stars));
        return ResponseEntity.ok(response);
    }

    // =========================================================
    // POST /api/v1/reservations/{uid}/return — вернуть книгу
    // =========================================================
    @PostMapping("/reservations/{reservationUid}/return")
    public ResponseEntity<?> returnBook(@RequestHeader("X-User-Name") String username,
                                        @PathVariable String reservationUid,
                                        @RequestBody ReturnBookRequest req) {

        // 1) Найти бронь.
        List<Map<String, Object>> rows;
        try {
            rows = reservationClient.list(username);
        } catch (CallNotPermittedException | ServiceUnavailableException e) {
            throw new ServiceUnavailableException(RESERVATION_UNAVAILABLE);
        } catch (Exception e) {
            throw new ServiceUnavailableException(RESERVATION_UNAVAILABLE);
        }

        Map<String, Object> current = null;
        if (rows != null) {
            for (Map<String, Object> r : rows) {
                if (reservationUid.equals(r.get("reservationUid"))) { current = r; break; }
            }
        }
        if (current == null) {
            return ResponseEntity.notFound().build();
        }

        // 2) Получить исходное состояние книги (Library — некритичный для возврата).
        String originalCondition = null;
        try {
            Map<String, Object> book = libraryClient.getBook((String) current.get("bookUid"));
            originalCondition = book == null ? null : (String) book.get("condition");
        } catch (Exception e) {
            log.warn("Could not fetch original condition, skipping condition check", e);
        }

        // 3) Обновить статус в Reservation.
        Map<String, Object> returnBody = new LinkedHashMap<>();
        returnBody.put("condition", req.condition());
        returnBody.put("date", req.date());
        try {
            reservationClient.returnBook(username, reservationUid, returnBody);
        } catch (Exception e) {
            log.error("Reservation return failed", e);
            throw new ServiceUnavailableException(RESERVATION_UNAVAILABLE);
        }

        // 4) Вернуть книгу в Library.
        try {
            libraryClient.returnBook((String) current.get("libraryUid"), (String) current.get("bookUid"));
        } catch (Exception e) {
            log.warn("Library return failed, will not block operation", e);
            // компенсация не нужна — статус в Reservation уже обновлён
        }

        // 5) Пересчёт рейтинга — Rating НЕкритичен для возврата.
        LocalDate till = LocalDate.parse((String) current.get("tillDate"));
        boolean late = req.date() != null && req.date().isAfter(till);
        boolean badCondition = originalCondition != null && !originalCondition.equals(req.condition());
        int delta = (late || badCondition) ? -10 : 1;

        try {
            ratingClient.changeRating(username, delta);
        } catch (Exception e) {
            log.warn("Rating update failed, enqueueing for retry: {}", e.getMessage());
            ratingQueue.enqueue(new RatingUpdateTask(username, delta, reservationUid + "_return"));
        }

        return ResponseEntity.noContent().build();
    }

    // =========================================================
    // helpers
    // =========================================================
    private Map<String, Object> extractBook(Map<String, Object> book) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bookUid", book.get("bookUid"));
        if (book.containsKey("name")) out.put("name", book.get("name"));
        if (book.containsKey("author")) out.put("author", book.get("author"));
        if (book.containsKey("genre")) out.put("genre", book.get("genre"));
        return out;
    }

    private Map<String, Object> extractLibrary(Map<String, Object> library) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("libraryUid", library.get("libraryUid"));
        if (library.containsKey("name")) out.put("name", library.get("name"));
        if (library.containsKey("address")) out.put("address", library.get("address"));
        if (library.containsKey("city")) out.put("city", library.get("city"));
        return out;
    }
}
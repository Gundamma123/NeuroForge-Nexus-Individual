package com.neuroforge.nexus.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

//     @ExceptionHandler(DataIntegrityViolationException.class)
//     public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException ex) {
//     return ResponseEntity.status(HttpStatus.CONFLICT)
//             .body(buildBody(HttpStatus.CONFLICT,
//                     "This item is still linked to other data and can't be removed."));
// }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(buildBody(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(buildBody(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
    }

    // Fired when a non-Admin JWT calls an @PreAuthorize("hasRole('ADMIN')") endpoint,
    // e.g. POST /members from a Developer/Tester/PM/DevOps account.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(buildBody(HttpStatus.FORBIDDEN, "Access Denied. Only administrators are allowed to add members."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                fieldErrors.put(err.getField(), err.getDefaultMessage()));

        Map<String, Object> body = buildBody(HttpStatus.BAD_REQUEST, "Validation failed");
        body.put("errors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(buildBody(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    /**
     * Handles malformed or unreadable JSON request bodies — for example, a missing
     * body, invalid JSON syntax, or a field with an incompatible type.
     * Returns 400 with a safe generic message; the raw parse error is never
     * forwarded to the client.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("Unreadable HTTP message: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(buildBody(HttpStatus.BAD_REQUEST, "Request body is missing or malformed"));
    }

    /**
     * Handles database constraint violations thrown by Spring Data / JPA.
     * Classifies by the root cause message to return the most appropriate status:
     * <ul>
     *   <li>409 Conflict — unique constraint violated (duplicate record)</li>
     *   <li>400 Bad Request — null constraint or foreign-key constraint violated
     *       (client sent data referencing a non-existent record or omitted a
     *       required field that bypassed bean-validation)</li>
     *   <li>500 Internal Server Error — unexpected violation (logged fully)</li>
     * </ul>
     * The raw SQL, constraint name, and table details are never exposed to the client.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String rootMsg = ex.getMostSpecificCause().getMessage();

        if (rootMsg != null) {
            String lower = rootMsg.toLowerCase();

            if (lower.contains("duplicate entry") || lower.contains("unique constraint")) {
                // 409 — the record already exists (e.g. duplicate email on user update)
                log.warn("Unique constraint violation (duplicate record)");
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(buildBody(HttpStatus.CONFLICT,
                                "A record with those details already exists"));
            }

            if (lower.contains("cannot be null") || lower.contains("column cannot be null")) {
                // 400 — a required field was missing; bean-validation should have caught
                // this first, but JPA can also surface it for unmapped constraints
                log.warn("Not-null constraint violation");
                return ResponseEntity.badRequest()
                        .body(buildBody(HttpStatus.BAD_REQUEST,
                                "A required field is missing"));
            }

            if (lower.contains("foreign key constraint")) {
                // 400 — client referenced a non-existent related record
                log.warn("Foreign key constraint violation");
                return ResponseEntity.badRequest()
                        .body(buildBody(HttpStatus.BAD_REQUEST,
                                "Referenced record does not exist"));
            }
        }

        // Unexpected violation — log the full cause for diagnosis, return 500
        log.error("Unexpected data integrity violation", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildBody(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Something went wrong. Please try again."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildBody(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again."));
    }

    private Map<String, Object> buildBody(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("message", message);
        return body;
    }
}
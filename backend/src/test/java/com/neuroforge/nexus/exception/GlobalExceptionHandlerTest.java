package com.neuroforge.nexus.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for {@link GlobalExceptionHandler}.
 *
 * Each handler method is called directly — no Spring context is loaded,
 * keeping the tests fast and memory-light.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // ── HttpMessageNotReadableException → 400 ──────────────────────────────

    @Test
    @DisplayName("Malformed JSON body returns 400 with safe message")
    void malformedJsonReturns400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error: Unexpected character",
                new MockHttpInputMessage("{bad json".getBytes(StandardCharsets.UTF_8)));

        ResponseEntity<Map<String, Object>> response = handler.handleUnreadable(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().get("status"));
        assertEquals("Request body is missing or malformed", response.getBody().get("message"));
    }

    @Test
    @DisplayName("Malformed JSON response body never exposes raw parse error details")
    void malformedJsonResponseDoesNotLeakDetails() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "Sensitive internal parse detail: table=users column=password",
                new MockHttpInputMessage(new byte[0]));

        ResponseEntity<Map<String, Object>> response = handler.handleUnreadable(ex);
        String message = (String) response.getBody().get("message");

        assertFalse(message.contains("table"),    "Response must not expose table names");
        assertFalse(message.contains("column"),   "Response must not expose column names");
        assertFalse(message.contains("password"), "Response must not expose field names");
        assertFalse(message.contains("parse"),    "Response must not expose parse details");
    }

    // ── DataIntegrityViolationException — unique constraint → 409 ──────────

    @Test
    @DisplayName("Duplicate entry violation returns 409 Conflict")
    void duplicateEntryReturns409() {
        DataIntegrityViolationException ex = buildDive(
                "Duplicate entry 'test@example.com' for key 'users.UK_email'");

        ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrity(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().get("status"));
        assertEquals("A record with those details already exists",
                response.getBody().get("message"));
    }

    @Test
    @DisplayName("Unique constraint violation (non-MySQL dialect) returns 409 Conflict")
    void uniqueConstraintReturns409() {
        DataIntegrityViolationException ex = buildDive(
                "unique constraint violation on column email");

        ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrity(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().get("status"));
    }

    // ── DataIntegrityViolationException — not-null constraint → 400 ─────────

    @Test
    @DisplayName("Not-null constraint violation returns 400 Bad Request")
    void notNullViolationReturns400() {
        DataIntegrityViolationException ex = buildDive(
                "Column 'name' cannot be null");

        ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrity(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().get("status"));
        assertEquals("A required field is missing", response.getBody().get("message"));
    }

    // ── DataIntegrityViolationException — FK constraint → 400 ───────────────

    @Test
    @DisplayName("Foreign key constraint violation returns 400 Bad Request")
    void foreignKeyViolationReturns400() {
        DataIntegrityViolationException ex = buildDive(
                "Cannot add or update a child row: a foreign key constraint fails "
                + "(`neuroforge_nexus`.`tasks`, CONSTRAINT `fk_task_sprint` FOREIGN KEY "
                + "(`sprint_id`) REFERENCES `sprints` (`id`))");

        ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrity(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().get("status"));
        assertEquals("Referenced record does not exist", response.getBody().get("message"));
    }

    // ── DataIntegrityViolationException — safe message, no DB leakage ────────

    @Test
    @DisplayName("409 response body never exposes SQL, table, or constraint details")
    void duplicateResponseDoesNotLeakDbDetails() {
        DataIntegrityViolationException ex = buildDive(
                "Duplicate entry 'secret@corp.com' for key 'users.UK_users_email'");

        ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrity(ex);
        String message = (String) response.getBody().get("message");

        assertFalse(message.contains("secret@corp.com"), "Email must not appear in response");
        assertFalse(message.contains("UK_users_email"),  "Constraint name must not appear");
        assertFalse(message.contains("users"),           "Table name must not appear");
        assertFalse(message.contains("Duplicate entry"), "Raw DB message must not appear");
    }

    @Test
    @DisplayName("FK violation response body never exposes table or constraint details")
    void foreignKeyResponseDoesNotLeakDbDetails() {
        DataIntegrityViolationException ex = buildDive(
                "a foreign key constraint fails (`neuroforge_nexus`.`tasks`, "
                + "CONSTRAINT `fk_task_sprint`)");

        ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrity(ex);
        String message = (String) response.getBody().get("message");

        assertFalse(message.contains("neuroforge_nexus"), "Schema name must not appear");
        assertFalse(message.contains("fk_task_sprint"),   "Constraint name must not appear");
        assertFalse(message.contains("tasks"),            "Table name must not appear");
    }

    // ── DataIntegrityViolationException — unexpected violation → 500 ─────────

    @Test
    @DisplayName("Unrecognised integrity violation returns 500")
    void unknownIntegrityViolationReturns500() {
        DataIntegrityViolationException ex = buildDive(
                "Check constraint 'chk_positive_points' violated");

        ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrity(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().get("status"));
        // Safe generic message only
        assertEquals("Something went wrong. Please try again.",
                response.getBody().get("message"));
    }

    @Test
    @DisplayName("Null root cause message returns 500 (does not throw NPE)")
    void nullRootCauseMessageReturns500() {
        // Build a DIVE whose most specific cause has a null message
        DataIntegrityViolationException ex =
                new DataIntegrityViolationException("wrapper", new RuntimeException((String) null));

        ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrity(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    // ── Response structure ───────────────────────────────────────────────────

    @Test
    @DisplayName("All responses include timestamp and status fields")
    void responseBodyContainsRequiredFields() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "error", new MockHttpInputMessage(new byte[0]));

        Map<String, Object> body = handler.handleUnreadable(ex).getBody();

        assertNotNull(body);
        assertTrue(body.containsKey("timestamp"), "Response must contain 'timestamp'");
        assertTrue(body.containsKey("status"),    "Response must contain 'status'");
        assertTrue(body.containsKey("message"),   "Response must contain 'message'");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Builds a {@link DataIntegrityViolationException} whose
     * {@link DataIntegrityViolationException#getMostSpecificCause()} returns
     * a cause with the given message — matching how Spring wraps a real
     * {@code java.sql.SQLIntegrityConstraintViolationException}.
     */
    private static DataIntegrityViolationException buildDive(String causeMessage) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new java.sql.SQLIntegrityConstraintViolationException(causeMessage));
    }
}

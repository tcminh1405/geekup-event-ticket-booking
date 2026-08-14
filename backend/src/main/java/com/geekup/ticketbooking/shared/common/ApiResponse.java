package com.geekup.ticketbooking.shared.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Standard API response envelope for all endpoints.
 * <p>
 * Success:    { "success": true,  "data": <payload>,  "timestamp": "..." }
 * Error:      { "success": false, "error": { "code": "...", "message": "..." }, "timestamp": "..." }
 * Validation: { "success": false, "error": { "code": "VALIDATION_ERROR", "message": "...", "fields": [...] }, "timestamp": "..." }
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ErrorResponse error;
    private final String timestamp;

    private ApiResponse(boolean success, T data, ErrorResponse error) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.timestamp = DateTimeFormatter.ISO_INSTANT
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
    }

    // ─── Factory Methods ─────────────────────────────────────────────────────────

    /** Wrap a successful payload. */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** Wrap an error with a machine-readable code and human-readable message. */
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorResponse(code, message, null));
    }

    /** Wrap a validation error with field-level details. */
    public static <T> ApiResponse<T> validationError(List<FieldError> fields) {
        ErrorResponse err = new ErrorResponse("VALIDATION_ERROR", "Request validation failed.", fields);
        return new ApiResponse<>(false, null, err);
    }

    // ─── Nested Types ─────────────────────────────────────────────────────────────

    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorResponse {

        private final String code;
        private final String message;
        private final List<FieldError> fields;

        public ErrorResponse(String code, String message, List<FieldError> fields) {
            this.code = code;
            this.message = message;
            this.fields = fields;
        }
    }

    @Getter
    public static class FieldError {

        private final String field;
        private final String reason;

        public FieldError(String field, String reason) {
            this.field = field;
            this.reason = reason;
        }
    }
}

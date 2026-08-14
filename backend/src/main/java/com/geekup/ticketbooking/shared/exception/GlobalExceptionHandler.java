package com.geekup.ticketbooking.shared.exception;

import com.geekup.ticketbooking.shared.common.ApiResponse;
import com.geekup.ticketbooking.shared.common.ApiResponse.FieldError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralized exception-to-HTTP mapping.
 *
 * Exception hierarchy (flat, code-bearing):
 *   ApplicationException
 *   ├── ResourceNotFoundException  → 404  e.g. "CONCERT_NOT_FOUND", "BOOKING_NOT_FOUND"
 *   ├── ConflictException          → 409  e.g. "TICKET_SOLD_OUT", "VOUCHER_ALREADY_USED"
 *   ├── ValidationException        → 422  e.g. "INVALID_QUANTITY", "INVALID_STATE_TRANSITION"
 *   ├── ForbiddenException         → 403
 *   ├── ServiceBusyException       → 503
 *   ├── PaymentFailedException     → 402
 *   └── PaymentGatewayTimeoutException → 504
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Not found [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex) {
        log.warn("Conflict [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(ValidationException ex) {
        log.warn("Validation [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException ex) {
        log.warn("Forbidden [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(ServiceBusyException.class)
    public ResponseEntity<ApiResponse<Void>> handleServiceBusy(ServiceBusyException ex) {
        log.warn("Service busy [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "2")
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentFailed(PaymentFailedException ex) {
        log.warn("Payment failed [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(PaymentGatewayTimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentGatewayTimeout(PaymentGatewayTimeoutException ex) {
        log.warn("Payment gateway timeout [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    /**
     * Handles {@code @Valid} failures on request DTOs.
     * Returns HTTP 400 with a structured {@code fields} array.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                .collect(Collectors.toList());

        log.warn("Bean validation failed: {} field error(s)", fieldErrors.size());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.validationError(fieldErrors));
    }

    /**
     * Catch-all — never expose stack traces.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnhandled(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_SERVER_ERROR", "An unexpected error occurred. Please try again later."));
    }
}

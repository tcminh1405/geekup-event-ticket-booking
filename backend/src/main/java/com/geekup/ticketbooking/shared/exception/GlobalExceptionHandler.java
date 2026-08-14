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
 * Centralized exception-to-HTTP mapping for all controllers.
 * Every response is wrapped in {@link ApiResponse} with a UTC ISO-8601 timestamp.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ─── Specific Named Exceptions ────────────────────────────────────────────────

    @ExceptionHandler(TicketSoldOutException.class)
    public ResponseEntity<ApiResponse<Void>> handleTicketSoldOut(TicketSoldOutException ex) {
        log.warn("Ticket sold out: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(IdempotencyKeyMissingException.class)
    public ResponseEntity<ApiResponse<Void>> handleIdempotencyKeyMissing(IdempotencyKeyMissingException ex) {
        log.warn("Missing idempotency key: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(InvalidQuantityException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidQuantity(InvalidQuantityException ex) {
        log.warn("Invalid quantity: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(VoucherNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleVoucherNotFound(VoucherNotFoundException ex) {
        log.warn("Voucher not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(VoucherAlreadyUsedException.class)
    public ResponseEntity<ApiResponse<Void>> handleVoucherAlreadyUsed(VoucherAlreadyUsedException ex) {
        log.warn("Voucher already used: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(VoucherExhaustedException.class)
    public ResponseEntity<ApiResponse<Void>> handleVoucherExhausted(VoucherExhaustedException ex) {
        log.warn("Voucher exhausted: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(VoucherCampaignInactiveException.class)
    public ResponseEntity<ApiResponse<Void>> handleVoucherCampaignInactive(VoucherCampaignInactiveException ex) {
        log.warn("Voucher campaign inactive: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(VoucherMinimumNotMetException.class)
    public ResponseEntity<ApiResponse<Void>> handleVoucherMinimumNotMet(VoucherMinimumNotMetException ex) {
        log.warn("Voucher minimum not met: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(InvalidBookingStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidBookingState(InvalidBookingStateException ex) {
        log.warn("Invalid booking state: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidStateTransition(InvalidStateTransitionException ex) {
        log.warn("Invalid state transition: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleBookingNotFound(BookingNotFoundException ex) {
        log.warn("Booking not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(ConcertNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleConcertNotFound(ConcertNotFoundException ex) {
        log.warn("Concert not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentFailed(PaymentFailedException ex) {
        log.warn("Payment failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    // ─── Base Exception Handlers (catch-all for remaining subclasses) ─────────────

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException ex) {
        log.warn("Forbidden: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(ServiceBusyException.class)
    public ResponseEntity<ApiResponse<Void>> handleServiceBusy(ServiceBusyException ex) {
        log.warn("Service busy (lock not acquired): {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "2")
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(PaymentGatewayTimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentGatewayTimeout(PaymentGatewayTimeoutException ex) {
        log.warn("Payment gateway timeout: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found [{}]: {}", ex.getErrorCode(), ex.getMessage());
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
        log.warn("Validation error [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiResponse<Void>> handleApplication(ApplicationException ex) {
        log.error("Application error [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    // ─── Bean Validation ──────────────────────────────────────────────────────────

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

        log.warn("Validation failed: {} field error(s)", fieldErrors.size());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.validationError(fieldErrors));
    }

    // ─── Catch-all ────────────────────────────────────────────────────────────────

    /**
     * Handles any unhandled exception.
     * Returns HTTP 500 with a generic message — no stack trace exposed.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnhandled(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_SERVER_ERROR", "An unexpected error occurred. Please try again later."));
    }
}

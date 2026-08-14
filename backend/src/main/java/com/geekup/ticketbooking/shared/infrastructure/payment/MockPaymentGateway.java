package com.geekup.ticketbooking.shared.infrastructure.payment;

import com.geekup.ticketbooking.shared.exception.PaymentFailedException;
import com.geekup.ticketbooking.shared.exception.PaymentGatewayTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mock payment gateway that simulates SUCCESS, FAILED, and TIMEOUT scenarios.
 *
 * <p>Behavior is controlled via {@code payment.gateway.behavior} property:
 * <ul>
 *   <li>{@code SUCCESS} — returns immediately (payment succeeded)</li>
 *   <li>{@code FAILED}  — throws {@link PaymentFailedException}</li>
 *   <li>{@code TIMEOUT} — sleeps 11 seconds then throws {@link PaymentGatewayTimeoutException}</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class MockPaymentGateway {

    /** Simulated timeout duration in milliseconds. */
    private static final int TIMEOUT_SLEEP_MS = 11_000;

    public enum PaymentResult {
        SUCCESS, FAILED, TIMEOUT
    }

    private final String behavior;

    public MockPaymentGateway(
            @Value("${payment.gateway.behavior:SUCCESS}") String behavior) {
        this.behavior = behavior.toUpperCase();
    }

    /**
     * Process a payment for the given booking.
     *
     * @param bookingId     the booking ID being paid
     * @param paymentMethod the payment method string (e.g. "MOCK")
     * @return {@link PaymentResult#SUCCESS} when payment succeeds
     * @throws PaymentFailedException        when the gateway returns FAILED
     * @throws PaymentGatewayTimeoutException when the gateway times out
     */
    public PaymentResult process(Long bookingId, String paymentMethod) {
        log.info("[MockPaymentGateway] Processing payment: bookingId={}, method={}, behavior={}",
                bookingId, paymentMethod, behavior);

        return switch (behavior) {
            case "FAILED" -> {
                log.warn("[MockPaymentGateway] Simulating FAILED payment for bookingId={}", bookingId);
                throw new PaymentFailedException(
                        "Payment was declined by the gateway. Please try a different payment method.");
            }
            case "TIMEOUT" -> {
                log.warn("[MockPaymentGateway] Simulating TIMEOUT for bookingId={}", bookingId);
                try {
                    Thread.sleep(TIMEOUT_SLEEP_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new PaymentGatewayTimeoutException(
                        "Payment gateway did not respond in time. Your booking remains PENDING. Please retry.");
            }
            default -> {
                log.info("[MockPaymentGateway] Simulating SUCCESS for bookingId={}", bookingId);
                yield PaymentResult.SUCCESS;
            }
        };
    }
}

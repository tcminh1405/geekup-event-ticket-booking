package com.geekup.ticketbooking.booking.service;

import com.geekup.ticketbooking.booking.dto.*;
import com.geekup.ticketbooking.booking.entity.Booking;
import com.geekup.ticketbooking.booking.entity.BookingItem;
import com.geekup.ticketbooking.booking.repository.BookingRepository;
import com.geekup.ticketbooking.booking.state.BookingState;
import com.geekup.ticketbooking.concert.entity.Concert;
import com.geekup.ticketbooking.concert.entity.TicketCategory;
import com.geekup.ticketbooking.concert.repository.ConcertRepository;
import com.geekup.ticketbooking.concert.repository.TicketCategoryRepository;
import com.geekup.ticketbooking.shared.cache.InventoryCache;
import com.geekup.ticketbooking.shared.exception.ConflictException;
import com.geekup.ticketbooking.shared.exception.ForbiddenException;
import com.geekup.ticketbooking.shared.exception.PaymentFailedException;
import com.geekup.ticketbooking.shared.exception.PaymentGatewayTimeoutException;
import com.geekup.ticketbooking.shared.exception.ResourceNotFoundException;
import com.geekup.ticketbooking.shared.exception.ValidationException;
import com.geekup.ticketbooking.shared.infrastructure.payment.MockPaymentGateway;
import com.geekup.ticketbooking.voucher.dto.VoucherValidationResult;
import com.geekup.ticketbooking.voucher.entity.Voucher;
import com.geekup.ticketbooking.voucher.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core booking service handling reservation, payment, and state transitions.
 *
 * <p>Concurrency guarantees:
 * <ul>
 *   <li>Inventory decrement uses an atomic {@code UPDATE … WHERE available_quantity >= :qty}
 *       which prevents overselling at the database level without application-level locks.</li>
 *   <li>Voucher application acquires a Redisson distributed lock via {@link VoucherService}.</li>
 *   <li>Post-commit inventory cache update is done via {@link TransactionSynchronizationManager}
 *       to ensure Redis reflects the committed DB state.</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final int PAYMENT_DEADLINE_MINUTES = 15;

    private final BookingRepository        bookingRepository;
    private final ConcertRepository        concertRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final VoucherService           voucherService;
    private final MockPaymentGateway       paymentGateway;
    private final InventoryCache           inventoryCache;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Reserve tickets for a concert.
     *
     * <p>Steps:
     * <ol>
     *   <li>Validate each ticket category exists and belongs to a published concert.</li>
     *   <li>Atomically decrement {@code available_quantity} in DB for each category;
     *       throw {@code TICKET_SOLD_OUT} if any category has insufficient stock.</li>
     *   <li>Persist Booking (PENDING, paymentDeadline = now + 15 min) + BookingItems.</li>
     *   <li>If {@code voucherCode} is present, validate and apply via {@link VoucherService}.</li>
     *   <li>After commit: asynchronously update {@link InventoryCache} for each category.</li>
     * </ol>
     * </p>
     *
     * @param userId         the authenticated customer's ID
     * @param request        the reservation request DTO
     * @param idempotencyKey the idempotency key from the request header (may be null)
     * @return summary response with bookingId, state, totalAmount, paymentDeadline
     */
    @Transactional
    public BookingResponse reserve(Long userId, ReserveBookingRequest request, String idempotencyKey) {
        // Step 1 — Validate concert exists and is published
        Concert concert = concertRepository.findById(request.getConcertId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CONCERT_NOT_FOUND",
                        "Concert with id " + request.getConcertId() + " was not found."));

        if (!concert.isPublished()) {
            throw new ValidationException(
                    "CONCERT_NOT_PUBLISHED",
                    "Concert with id " + request.getConcertId() + " is not published.");
        }

        // Resolve each ticket category — validate it exists and belongs to this concert
        List<TicketCategory> categories = new ArrayList<>();
        for (BookingItemRequest itemReq : request.getItems()) {
            TicketCategory category = ticketCategoryRepository.findById(itemReq.getTicketCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "TICKET_CATEGORY_NOT_FOUND",
                            "Ticket category with id " + itemReq.getTicketCategoryId() + " was not found."));

            if (!category.getConcert().getId().equals(concert.getId())) {
                throw new ValidationException(
                        "INVALID_TICKET_CATEGORY",
                        "Ticket category " + itemReq.getTicketCategoryId()
                                + " does not belong to concert " + concert.getId() + ".");
            }
            categories.add(category);
        }

        // Step 2 — Atomically decrement available_quantity for each category
        for (int i = 0; i < request.getItems().size(); i++) {
            BookingItemRequest itemReq  = request.getItems().get(i);
            TicketCategory     category = categories.get(i);

            int rowsAffected = ticketCategoryRepository.decrementAvailableQuantity(
                    category.getId(), itemReq.getQuantity());

            if (rowsAffected == 0) {
                throw new ConflictException(
                        "TICKET_SOLD_OUT",
                        "Ticket category '" + category.getName() + "' has insufficient availability.");
            }
        }

        // Step 3 — Calculate amounts and build BookingItems
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<BookingItem> items = new ArrayList<>();

        for (int i = 0; i < request.getItems().size(); i++) {
            BookingItemRequest itemReq  = request.getItems().get(i);
            TicketCategory     category = categories.get(i);

            BigDecimal unitPrice = category.getPrice();
            BigDecimal subtotal  = unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            totalAmount          = totalAmount.add(subtotal);

            items.add(BookingItem.builder()
                    .ticketCategory(category)
                    .quantity(itemReq.getQuantity())
                    .unitPrice(unitPrice)
                    .subtotal(subtotal)
                    .build());
        }

        // Generate idempotency key if not provided
        String iKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey
                : UUID.randomUUID().toString();

        // Build and persist the Booking
        Booking booking = Booking.builder()
                .userId(userId)
                .concert(concert)
                .state(BookingState.PENDING)
                .totalAmount(totalAmount)
                .discountAmount(BigDecimal.ZERO)
                .idempotencyKey(iKey)
                .paymentDeadline(LocalDateTime.now().plusMinutes(PAYMENT_DEADLINE_MINUTES))
                .items(items)
                .build();

        // Set back-reference on items
        items.forEach(item -> item.setBooking(booking));

        Booking savedBooking = bookingRepository.save(booking);

        // Step 4 — Apply voucher if provided
        if (request.getVoucherCode() != null && !request.getVoucherCode().isBlank()) {
            Voucher voucher = voucherService.findVoucherByCode(request.getVoucherCode());
            VoucherValidationResult result =
                    voucherService.validateAndApplyVoucher(userId, voucher, totalAmount);

            savedBooking.setTotalAmount(result.getDiscountedAmount());
            savedBooking.setDiscountAmount(result.getDiscountAmount());
            savedBooking.setVoucher(voucher);
            savedBooking = bookingRepository.save(savedBooking);
        }

        final Booking finalBooking = savedBooking;

        // Step 5 — Post-commit: async update InventoryCache
        registerPostCommitInventoryCacheUpdate(request, categories);

        log.info("[BookingService] Booking reserved: bookingId={}, userId={}, state={}",
                finalBooking.getId(), userId, finalBooking.getState());

        return toBookingResponse(finalBooking);
    }

    /**
     * Process payment for a booking.
     *
     * <p>Steps:
     * <ol>
     *   <li>Load booking; validate 404/403; validate state is PENDING.</li>
     *   <li>Transition to AWAITING_PAYMENT, call {@link MockPaymentGateway}.</li>
     *   <li>SUCCESS  → CONFIRMED, record paymentTimestamp.</li>
     *   <li>FAILED   → CANCELLED, restore DB qty + InventoryCache + voucher; throw {@link PaymentFailedException}.</li>
     *   <li>Timeout  → leave PENDING; throw {@link PaymentGatewayTimeoutException}.</li>
     * </ol>
     * </p>
     *
     * @param userId    the authenticated customer's ID
     * @param bookingId the booking to pay for
     * @param request   the payment request DTO
     * @return detailed booking response on successful payment
     */
    @Transactional(noRollbackFor = {PaymentFailedException.class, PaymentGatewayTimeoutException.class})
    public BookingDetailResponse pay(Long userId, Long bookingId, PaymentRequest request) {
        // Step 1 — Load booking
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BOOKING_NOT_FOUND",
                        "Booking with id " + bookingId + " was not found."));

        // 403 check — user can only pay their own bookings
        if (!booking.getUserId().equals(userId)) {
            throw new ForbiddenException("You are not allowed to pay for this booking.");
        }

        // Validate state is PENDING
        if (booking.getState() != BookingState.PENDING) {
            throw new ConflictException(
                    "INVALID_BOOKING_STATE",
                    "Booking " + bookingId + " is in state " + booking.getState()
                            + " and cannot be paid. Only PENDING bookings can be paid.");
        }

        // Step 2 — claim the payment transition atomically. This prevents two
        // retries from charging the same booking concurrently.
        if (bookingRepository.transitionStateIfCurrent(bookingId, BookingState.PENDING,
                BookingState.AWAITING_PAYMENT) == 0) {
            throw new ConflictException("INVALID_BOOKING_STATE",
                    "Booking " + bookingId + " is already being paid or has been processed.");
        }
        booking.setState(BookingState.AWAITING_PAYMENT);

        // Call mock payment gateway — may throw PaymentFailedException or PaymentGatewayTimeoutException
        try {
            paymentGateway.process(bookingId, request.getPaymentMethod());

            // Step 3 — SUCCESS → CONFIRMED
            booking.setState(BookingState.CONFIRMED);
            booking.setPaymentTimestamp(LocalDateTime.now());
            Booking confirmed = bookingRepository.save(booking);

            log.info("[BookingService] Payment confirmed: bookingId={}, userId={}", bookingId, userId);
            return toBookingDetailResponse(confirmed);

        } catch (PaymentFailedException e) {
            // Step 4 — FAILED → CANCELLED; restore inventory + voucher
            log.warn("[BookingService] Payment failed for bookingId={}: {}", bookingId, e.getMessage());
            booking.setState(BookingState.CANCELLED);
            bookingRepository.save(booking);
            restoreInventory(booking);
            throw e;

        } catch (PaymentGatewayTimeoutException e) {
            // Step 5 — TIMEOUT → revert to PENDING; do not restore inventory
            log.warn("[BookingService] Payment gateway timeout for bookingId={}: {}", bookingId, e.getMessage());
            // The payment claim was a JPQL bulk update, so explicitly update
            // the database rather than relying on this entity's stale snapshot.
            bookingRepository.transitionStateIfCurrent(bookingId,
                    BookingState.AWAITING_PAYMENT, BookingState.PENDING);
            throw e;
        }
    }

    /**
     * List bookings for a user, ordered by creation date descending.
     *
     * @param userId   the authenticated customer's ID
     * @param pageable pagination parameters
     * @return paginated list of booking summaries
     */
    @Transactional(readOnly = true)
    public Page<BookingResponse> listBookings(Long userId, Pageable pageable) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toBookingResponse);
    }

    /**
     * Get the detail of a single booking.
     *
     * @param userId    the authenticated customer's ID
     * @param bookingId the booking ID
     * @return detailed booking response
     * @throws ResourceNotFoundException if booking not found
     * @throws ForbiddenException        if booking belongs to another user
     */
    @Transactional(readOnly = true)
    public BookingDetailResponse getBookingDetail(Long userId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BOOKING_NOT_FOUND",
                        "Booking with id " + bookingId + " was not found."));

        if (!booking.getUserId().equals(userId)) {
            throw new ForbiddenException("You are not allowed to view this booking.");
        }

        return toBookingDetailResponse(booking);
    }

    /**
     * Restore inventory (DB + cache) and voucher usage for a booking that was
     * cancelled or expired. Called from both the payment failure path and the
     * expiry scheduler.
     *
     * @param booking the booking whose items need to be restored
     */
    @Transactional
    public void restoreInventory(Booking booking) {
        for (BookingItem item : booking.getItems()) {
            Long categoryId = item.getTicketCategory().getId();
            int  qty        = item.getQuantity();

            // Restore DB quantity
            ticketCategoryRepository.incrementAvailableQuantity(categoryId, qty);

            registerPostCommitInventoryCacheIncrement(categoryId, qty);
        }

        // Restore voucher usage if one was applied
        if (booking.getVoucher() != null) {
            try {
                voucherService.restoreVoucherUsage(booking.getVoucher());
            } catch (Exception ex) {
                log.error("[BookingService] Failed to restore voucher usage for bookingId={}: {}",
                        booking.getId(), ex.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Registers a {@link TransactionSynchronization} that updates the inventory
     * cache for each booked category AFTER the current transaction commits.
     */
    private void registerPostCommitInventoryCacheUpdate(
            ReserveBookingRequest request, List<TicketCategory> categories) {

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (int i = 0; i < request.getItems().size(); i++) {
                    BookingItemRequest itemReq  = request.getItems().get(i);
                    TicketCategory     category = categories.get(i);
                    try {
                        inventoryCache.incrementInventory(category.getId(), -itemReq.getQuantity());
                    } catch (Exception ex) {
                        log.error("[BookingService] Post-commit cache update failed for categoryId={}: {}",
                                category.getId(), ex.getMessage());
                    }
                }
            }
        });
    }

    private void registerPostCommitInventoryCacheIncrement(Long categoryId, int qty) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                inventoryCache.incrementInventory(categoryId, qty);
            }
        });
    }

    private BookingResponse toBookingResponse(Booking booking) {
        return BookingResponse.builder()
                .bookingId(booking.getId())
                .concertId(booking.getConcert().getId())
                .concertName(booking.getConcert().getName())
                .state(booking.getState())
                .totalAmount(booking.getTotalAmount())
                .discountAmount(booking.getDiscountAmount())
                .paymentDeadline(booking.getPaymentDeadline())
                .paymentTimestamp(booking.getPaymentTimestamp())
                .createdAt(booking.getCreatedAt())
                .build();
    }

    private BookingDetailResponse toBookingDetailResponse(Booking booking) {
        List<BookingItemResponse> itemResponses = booking.getItems().stream()
                .map(item -> BookingItemResponse.builder()
                        .id(item.getId())
                        .ticketCategoryId(item.getTicketCategory().getId())
                        .ticketCategoryName(item.getTicketCategory().getName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .subtotal(item.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        String voucherCode = (booking.getVoucher() != null)
                ? booking.getVoucher().getCode()
                : null;

        return BookingDetailResponse.builder()
                .bookingId(booking.getId())
                .concertId(booking.getConcert().getId())
                .concertName(booking.getConcert().getName())
                .state(booking.getState())
                .items(itemResponses)
                .totalAmount(booking.getTotalAmount())
                .discountAmount(booking.getDiscountAmount())
                .voucherCode(voucherCode)
                .idempotencyKey(booking.getIdempotencyKey())
                .paymentDeadline(booking.getPaymentDeadline())
                .paymentTimestamp(booking.getPaymentTimestamp())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }
}

package com.geekup.ticketbooking.admin.service;

import com.geekup.ticketbooking.admin.dto.*;
import com.geekup.ticketbooking.booking.dto.BookingDetailResponse;
import com.geekup.ticketbooking.booking.dto.BookingItemResponse;
import com.geekup.ticketbooking.booking.dto.BookingResponse;
import com.geekup.ticketbooking.booking.entity.Booking;
import com.geekup.ticketbooking.booking.entity.BookingItem;
import com.geekup.ticketbooking.booking.repository.BookingRepository;
import com.geekup.ticketbooking.booking.service.BookingService;
import com.geekup.ticketbooking.booking.state.BookingState;
import com.geekup.ticketbooking.concert.dto.ConcertDetailResponse;
import com.geekup.ticketbooking.concert.dto.TicketCategoryResponse;
import com.geekup.ticketbooking.concert.entity.Concert;
import com.geekup.ticketbooking.concert.entity.TicketCategory;
import com.geekup.ticketbooking.concert.repository.ConcertRepository;
import com.geekup.ticketbooking.concert.repository.TicketCategoryRepository;
import com.geekup.ticketbooking.shared.cache.InventoryCache;
import com.geekup.ticketbooking.shared.exception.ResourceNotFoundException;
import com.geekup.ticketbooking.shared.exception.ValidationException;
import com.geekup.ticketbooking.voucher.entity.Voucher;
import com.geekup.ticketbooking.voucher.entity.VoucherCampaign;
import com.geekup.ticketbooking.voucher.repository.VoucherCampaignRepository;
import com.geekup.ticketbooking.voucher.repository.VoucherRepository;
import com.geekup.ticketbooking.voucher.service.VoucherService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin service handling operator-level operations for concerts, bookings, and voucher campaigns.
 *
 * <p>Requirements covered: 6.1–6.6, 7.1–7.7, 8.1–8.6</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final ConcertRepository        concertRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final BookingRepository        bookingRepository;
    private final BookingService           bookingService;
    private final VoucherCampaignRepository campaignRepository;
    private final VoucherRepository        voucherRepository;
    private final InventoryCache           inventoryCache;
    private final VoucherService           voucherService;

    // =========================================================================
    // Concert Admin
    // =========================================================================

    /**
     * Atomically persist a Concert and its TicketCategories.
     * Requirement 7.1
     */
    @Transactional
    public ConcertDetailResponse createConcert(CreateConcertRequest request) {
        Concert concert = Concert.builder()
                .name(request.getName())
                .venue(request.getVenue())
                .concertDate(request.getConcertDate())
                .published(false)
                .build();

        Concert saved = concertRepository.save(concert);

        List<TicketCategory> categories = request.getTicketCategories().stream()
                .map(r -> TicketCategory.builder()
                        .concert(saved)
                        .name(r.getName())
                        .price(r.getPrice())
                        .totalQuantity(r.getQuantity())
                        .availableQuantity(r.getQuantity())
                        .soldQuantity(0)
                        .build())
                .collect(Collectors.toList());

        List<TicketCategory> savedCats = ticketCategoryRepository.saveAll(categories);

        return toConcertDetailResponse(saved, savedCats);
    }

    /**
     * Mark concert as published and initialise inventory in Redis.
     * Requirements 7.3, 7.4, 7.5
     */
    @Transactional
    public ConcertDetailResponse publishConcert(Long concertId) {
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CONCERT_NOT_FOUND",
                        "Concert with id " + concertId + " was not found."));

        if (concert.isPublished()) {
            throw new ValidationException(
                    "CONCERT_ALREADY_PUBLISHED",
                    "Concert with id " + concertId + " is already published.");
        }

        List<TicketCategory> categories = ticketCategoryRepository.findAllByConcertId(concertId);

        if (categories.isEmpty()) {
            throw new ValidationException(
                    "NO_TICKET_CATEGORIES",
                    "Concert with id " + concertId + " has no ticket categories defined.");
        }

        concert.setPublished(true);
        Concert saved = concertRepository.save(concert);

        // Load inventory into Redis cache
        categories.forEach(cat ->
                inventoryCache.initInventory(cat.getId(), cat.getAvailableQuantity()));

        return toConcertDetailResponse(saved, categories);
    }

    /**
     * Return inventory stats (total, sold, available) per ticket category.
     * Requirement 7.6
     */
    @Transactional(readOnly = true)
    public InventoryStatsResponse getInventoryStats(Long concertId) {
        concertRepository.findById(concertId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CONCERT_NOT_FOUND",
                        "Concert with id " + concertId + " was not found."));

        List<TicketCategory> categories = ticketCategoryRepository.findAllByConcertId(concertId);

        List<InventoryStatsResponse.TicketCategoryInventory> stats = categories.stream()
                .map(cat -> {
                    // availableQuantity is the inventory source of truth. It
                    // already excludes PENDING reservations, unlike a query of
                    // only paid bookings.
                    int reservedOrSoldCount = cat.getTotalQuantity() - cat.getAvailableQuantity();
                    return InventoryStatsResponse.TicketCategoryInventory.builder()
                            .ticketCategoryId(cat.getId())
                            .name(cat.getName())
                            .totalQuantity(cat.getTotalQuantity())
                            .soldCount(reservedOrSoldCount)
                            .availableQuantity(cat.getAvailableQuantity())
                            .build();
                })
                .collect(Collectors.toList());

        return InventoryStatsResponse.builder()
                .concertId(concertId)
                .categories(stats)
                .build();
    }

    /**
     * Update the total quantity of a ticket category.
     * Validates new quantity >= soldCount; updates DB and Redis cache.
     * Requirement 7.7
     */
    @Transactional
    public TicketCategoryResponse updateTicketCategoryQuantity(Long categoryId, UpdateQuantityRequest request) {
        TicketCategory category = ticketCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TICKET_CATEGORY_NOT_FOUND",
                        "Ticket category with id " + categoryId + " was not found."));

        int reservedOrSoldCount = category.getTotalQuantity() - category.getAvailableQuantity();

        if (request.getNewQuantity() < reservedOrSoldCount) {
            throw new ValidationException(
                    "QUANTITY_BELOW_SOLD",
                    "New quantity cannot be less than tickets already reserved or sold ("
                            + reservedOrSoldCount + ").");
        }

        int newAvailable = request.getNewQuantity() - reservedOrSoldCount;
        category.setTotalQuantity(request.getNewQuantity());
        category.setAvailableQuantity(newAvailable);
        TicketCategory saved = ticketCategoryRepository.save(category);

        inventoryCache.updateInventory(categoryId, newAvailable);

        log.info("[AdminService] TicketCategory {} quantity updated to {}, available={}",
                categoryId, request.getNewQuantity(), newAvailable);

        return TicketCategoryResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .price(saved.getPrice())
                .totalQuantity(saved.getTotalQuantity())
                .remainingQuantity(newAvailable)
                .build();
    }

    // =========================================================================
    // Booking Admin
    // =========================================================================

    /**
     * List all bookings with optional filters.
     * Requirements 6.1, 6.2
     */
    @Transactional(readOnly = true)
    public Page<BookingResponse> listBookings(AdminBookingFilter filter, Pageable pageable) {
        Specification<Booking> spec = buildBookingSpec(filter);
        return bookingRepository.findAll(spec, pageable)
                .map(this::toBookingResponse);
    }

    /**
     * Manually transition a booking to a new state (operator action).
     * On CANCELLED: restore DB quantity, update Redis cache, restore voucher.
     * Requirements 6.3, 6.4, 6.5, 6.6
     */
    @Transactional
    public BookingDetailResponse transitionBookingState(Long bookingId, BookingState targetState) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BOOKING_NOT_FOUND",
                        "Booking with id " + bookingId + " was not found."));

        if (!booking.getState().canTransitionTo(targetState)) {
            throw new ValidationException(
                    "INVALID_STATE_TRANSITION",
                    "Cannot transition from " + booking.getState()
                            + " to " + targetState
                            + ". Valid transitions: " + booking.getState().validNextStates());
        }

        BookingState previousState = booking.getState();
        booking.setState(targetState);

        if (targetState == BookingState.CANCELLED) {
            // Reuse the booking flow so cache updates run after commit.
            bookingService.restoreInventory(booking);

            log.info("[AdminService] Booking {} cancelled from state {}; inventory restored.",
                    bookingId, previousState);
        }

        Booking saved = bookingRepository.save(booking);
        return toBookingDetailResponse(saved);
    }

    @Transactional
    public BookingDetailResponse updateBookingSuspicion(Long bookingId, boolean suspicious, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("BOOKING_NOT_FOUND",
                        "Booking with id " + bookingId + " was not found."));
        booking.setSuspicious(suspicious);
        booking.setSuspicionReason(suspicious ? reason : null);
        return toBookingDetailResponse(bookingRepository.save(booking));
    }

    // =========================================================================
    // Voucher Admin
    // =========================================================================

    /**
     * Create a new voucher campaign.
     * Requirement 8.1, 8.6
     */
    @Transactional
    public VoucherCampaignResponse createVoucherCampaign(CreateVoucherCampaignRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new ValidationException(
                    "INVALID_CAMPAIGN_DATES",
                    "Campaign end date must not be before the start date.");
        }

        BigDecimal minAmount = request.getMinBookingAmount() != null
                ? request.getMinBookingAmount()
                : BigDecimal.ZERO;

        VoucherCampaign campaign = VoucherCampaign.builder()
                .name(request.getName())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .minBookingAmount(minAmount)
                .maxUsageCount(request.getMaxUsageCount())
                .currentUsageCount(0)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();

        VoucherCampaign saved = campaignRepository.save(campaign);
        log.info("[AdminService] VoucherCampaign created: id={}, name='{}'", saved.getId(), saved.getName());
        return toVoucherCampaignResponse(saved);
    }

    /**
     * Generate a batch of unique voucher codes for a campaign.
     * Uses SecureRandom + base-36 alphanumeric (8 chars).
     * Ensures uniqueness both within the batch and against existing DB codes.
     * Requirements 8.2, 8.3, 8.4
     */
    @Transactional
    public Map<String, Object> generateVouchers(Long campaignId, GenerateVouchersRequest request) {
        VoucherCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CAMPAIGN_NOT_FOUND",
                        "Voucher campaign with id " + campaignId + " was not found."));

        int count = request.getCount();
        SecureRandom random = new SecureRandom();
        Set<String> batchCodes = new HashSet<>(count * 2);

        // Generate candidates, then check the whole batch against the DB once
        while (batchCodes.size() < count) {
            batchCodes.add(generateSecureCode(random));
        }

        // Detect DB collisions and regenerate until all codes are truly unique
        Set<String> existingInDb = new HashSet<>();
        List<Voucher> collisions = voucherRepository.findByCodeIn(batchCodes);
        collisions.forEach(v -> existingInDb.add(v.getCode()));

        if (!existingInDb.isEmpty()) {
            batchCodes.removeAll(existingInDb);
            while (batchCodes.size() < count) {
                String candidate = generateSecureCode(random);
                if (!existingInDb.contains(candidate)) {
                    batchCodes.add(candidate);
                }
            }
        }

        List<Voucher> vouchers = batchCodes.stream()
                .map(code -> Voucher.builder()
                        .campaign(campaign)
                        .code(code)
                        .used(false)
                        .build())
                .collect(Collectors.toList());

        voucherRepository.saveAll(vouchers);

        log.info("[AdminService] Generated {} vouchers for campaignId={}", count, campaignId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generated", count);
        return result;
    }

    /**
     * Get statistics for a voucher campaign.
     * Requirement 8.5
     */
    @Transactional(readOnly = true)
    public CampaignStatsResponse getCampaignStats(Long campaignId) {
        VoucherCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CAMPAIGN_NOT_FOUND",
                        "Voucher campaign with id " + campaignId + " was not found."));

        long totalIssued = voucherRepository.countByCampaignId(campaignId);
        long totalUsed   = voucherRepository.countByCampaignIdAndUsedTrue(campaignId);

        return CampaignStatsResponse.builder()
                .campaignId(campaign.getId())
                .campaignName(campaign.getName())
                .totalIssued(totalIssued)
                .totalUsed(totalUsed)
                .remaining(totalIssued - totalUsed)
                .build();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Generate a cryptographically secure 8-character uppercase alphanumeric code.
     * Uses base-36 (0–9, A–Z) character set via SecureRandom.
     */
    private String generateSecureCode(SecureRandom random) {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        int codeLength = 8;
        StringBuilder sb = new StringBuilder(codeLength);
        for (int i = 0; i < codeLength; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /** Build a JPA Specification from the optional filter parameters. */
    private Specification<Booking> buildBookingSpec(AdminBookingFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getState() != null) {
                predicates.add(cb.equal(root.get("state"), filter.getState()));
            }
            if (filter.getConcertId() != null) {
                predicates.add(cb.equal(root.get("concert").get("id"), filter.getConcertId()));
            }
            if (filter.getSuspicious() != null) {
                predicates.add(cb.equal(root.get("suspicious"), filter.getSuspicious()));
            }
            if (filter.getCreatedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getCreatedFrom()));
            }
            if (filter.getCreatedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.getCreatedTo()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ─── Mapping helpers ─────────────────────────────────────────────────────

    private ConcertDetailResponse toConcertDetailResponse(Concert concert, List<TicketCategory> categories) {
        List<TicketCategoryResponse> catResponses = categories.stream()
                .map(cat -> TicketCategoryResponse.builder()
                        .id(cat.getId())
                        .name(cat.getName())
                        .price(cat.getPrice())
                        .totalQuantity(cat.getTotalQuantity())
                        .remainingQuantity(cat.getAvailableQuantity())
                        .build())
                .collect(Collectors.toList());

        return ConcertDetailResponse.builder()
                .id(concert.getId())
                .name(concert.getName())
                .venue(concert.getVenue())
                .concertDate(concert.getConcertDate())
                .published(concert.isPublished())
                .ticketCategories(catResponses)
                .build();
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
                .suspicious(booking.isSuspicious())
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
                .suspicious(booking.isSuspicious())
                .suspicionReason(booking.getSuspicionReason())
                .paymentDeadline(booking.getPaymentDeadline())
                .paymentTimestamp(booking.getPaymentTimestamp())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }

    private VoucherCampaignResponse toVoucherCampaignResponse(VoucherCampaign campaign) {
        return VoucherCampaignResponse.builder()
                .id(campaign.getId())
                .name(campaign.getName())
                .discountType(campaign.getDiscountType())
                .discountValue(campaign.getDiscountValue())
                .minBookingAmount(campaign.getMinBookingAmount())
                .maxUsageCount(campaign.getMaxUsageCount())
                .startDate(campaign.getStartDate())
                .endDate(campaign.getEndDate())
                .createdAt(campaign.getCreatedAt())
                .build();
    }
}

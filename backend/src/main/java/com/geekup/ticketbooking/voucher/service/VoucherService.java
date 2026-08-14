package com.geekup.ticketbooking.voucher.service;

import com.geekup.ticketbooking.shared.cache.VoucherLockService;
import com.geekup.ticketbooking.shared.exception.ConflictException;
import com.geekup.ticketbooking.shared.exception.ResourceNotFoundException;
import com.geekup.ticketbooking.shared.exception.ValidationException;
import com.geekup.ticketbooking.voucher.dto.VoucherValidationResult;
import com.geekup.ticketbooking.voucher.entity.Voucher;
import com.geekup.ticketbooking.voucher.entity.VoucherCampaign;
import com.geekup.ticketbooking.voucher.repository.VoucherCampaignRepository;
import com.geekup.ticketbooking.voucher.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Handles voucher validation, discount calculation, usage tracking, and restoration.
 *
 * <p>Concurrency safety: {@link #validateAndApplyVoucher(Long, Voucher, BigDecimal)} acquires
 * a Redisson distributed lock scoped to {@code userId + voucherId} before any state mutation,
 * satisfying Requirement 4.5.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherService {

    private final VoucherRepository          voucherRepository;
    private final VoucherCampaignRepository  campaignRepository;
    private final VoucherLockService         voucherLockService;

    // -------------------------------------------------------------------------
    // Public API — used by BookingService
    // -------------------------------------------------------------------------

    /**
     * Look up a voucher by its code. Returns the Voucher entity if found;
     * throws {@link ResourceNotFoundException} with code {@code VOUCHER_NOT_FOUND}
     * if no voucher with that code exists (Requirement 4.8).
     *
     * @param code the voucher code supplied by the customer
     * @return the matching {@link Voucher}
     */
    public Voucher findVoucherByCode(String code) {
        return voucherRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "VOUCHER_NOT_FOUND",
                        "Voucher with code '" + code + "' was not found."));
    }

    /**
     * Validate the voucher against all business rules and, if valid, mark it as used,
     * increment the campaign usage counter, and return the discounted booking amount.
     *
     * <p>Steps (per design spec):
     * <ol>
     *   <li>Acquire Redisson lock via {@link VoucherLockService}</li>
     *   <li>Validate campaign active dates (Req 4.9)</li>
     *   <li>Validate voucher not already used by this user (Req 4.3)</li>
     *   <li>Validate campaign usage count &lt; maxUsageCount (Req 4.4)</li>
     *   <li>Validate minimum booking amount (Req 4.7, 4.10)</li>
     *   <li>Mark voucher as used</li>
     *   <li>Increment campaign currentUsageCount</li>
     *   <li>Calculate and return discounted amount (Req 4.2)</li>
     *   <li>Release lock (finally block)</li>
     * </ol>
     * </p>
     *
     * @param userId        the authenticated customer's ID
     * @param voucher       the {@link Voucher} entity to apply
     * @param bookingAmount the pre-discount booking total
     * @return the discounted booking amount wrapped in a {@link VoucherValidationResult}
     */
    @Transactional
    public VoucherValidationResult validateAndApplyVoucher(Long userId,
                                                           Voucher voucher,
                                                           BigDecimal bookingAmount) {
        RLock lock = null;
        try {
            // Step 1 — Acquire distributed lock (Req 4.5)
            lock = voucherLockService.acquireLock(userId, voucher.getId());

            // Re-fetch the voucher inside the lock to ensure we read the latest state
            voucher = voucherRepository.findById(voucher.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "VOUCHER_NOT_FOUND", "Voucher no longer exists."));

            VoucherCampaign campaign = voucher.getCampaign();

            // Step 2 — Validate campaign active dates (Req 4.9)
            LocalDate today = LocalDate.now();
            if (today.isBefore(campaign.getStartDate()) || today.isAfter(campaign.getEndDate())) {
                throw new ValidationException(
                        "VOUCHER_CAMPAIGN_INACTIVE",
                        "Voucher campaign '" + campaign.getName() + "' is not currently active.");
            }

            // Step 3 — Validate voucher not already used by this user (Req 4.3)
            if (voucher.isUsed() && userId.equals(voucher.getUsedByUserId())) {
                throw new ConflictException(
                        "VOUCHER_ALREADY_USED",
                        "You have already used voucher '" + voucher.getCode() + "'.");
            }

            // Step 4 — Validate campaign usage count < maxUsageCount (Req 4.4)
            if (campaign.getCurrentUsageCount() >= campaign.getMaxUsageCount()) {
                throw new ConflictException(
                        "VOUCHER_EXHAUSTED",
                        "Voucher campaign '" + campaign.getName() + "' has reached its maximum usage limit.");
            }

            // Step 5 — Validate minimum booking amount (Req 4.7, 4.10)
            BigDecimal minAmount = campaign.getMinBookingAmount();
            if (minAmount != null
                    && minAmount.compareTo(BigDecimal.ZERO) > 0
                    && bookingAmount.compareTo(minAmount) < 0) {
                throw new ValidationException(
                        "VOUCHER_MINIMUM_NOT_MET",
                        "Booking amount " + bookingAmount + " does not meet the minimum required amount of "
                                + minAmount + " for this voucher.");
            }

            // Step 6 — Mark voucher as used
            voucher.setUsed(true);
            voucher.setUsedByUserId(userId);
            voucher.setUsedAt(LocalDateTime.now());
            voucherRepository.save(voucher);

            // Step 7 — Increment campaign currentUsageCount
            campaign.setCurrentUsageCount(campaign.getCurrentUsageCount() + 1);
            campaignRepository.save(campaign);

            // Step 8 — Calculate discounted amount (Req 4.2)
            BigDecimal discountedAmount = calculateDiscountedAmount(bookingAmount, campaign);
            BigDecimal discountAmount   = bookingAmount.subtract(discountedAmount);

            log.info("[VoucherService] Voucher '{}' applied for userId={}: original={}, discounted={}",
                    voucher.getCode(), userId, bookingAmount, discountedAmount);

            return VoucherValidationResult.builder()
                    .voucherId(voucher.getId())
                    .discountedAmount(discountedAmount)
                    .discountAmount(discountAmount)
                    .build();

        } finally {
            // Step 9 — Release lock
            voucherLockService.releaseLock(lock);
        }
    }

    /**
     * Restore a voucher's usage after a booking is cancelled or expired (Requirement 4.6).
     *
     * <p>Steps:
     * <ol>
     *   <li>Mark the voucher as unused (used=false, usedByUserId=null, usedAt=null, usedInBookingId=null)</li>
     *   <li>Decrement campaign currentUsageCount by 1 (floor at 0)</li>
     *   <li>Save both entities</li>
     * </ol>
     * </p>
     *
     * @param voucher the {@link Voucher} to restore
     */
    @Transactional
    public void restoreVoucherUsage(Voucher voucher) {
        voucher.setUsed(false);
        voucher.setUsedByUserId(null);
        voucher.setUsedAt(null);
        voucher.setUsedInBooking(null);
        voucherRepository.save(voucher);

        VoucherCampaign campaign = voucher.getCampaign();
        int newCount = Math.max(0, campaign.getCurrentUsageCount() - 1);
        campaign.setCurrentUsageCount(newCount);
        campaignRepository.save(campaign);

        log.info("[VoucherService] Voucher '{}' usage restored. Campaign '{}' usage now: {}",
                voucher.getCode(), campaign.getName(), newCount);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Calculate the discounted amount based on the campaign's discount type.
     *
     * <ul>
     *   <li>PERCENTAGE: {@code floor(amount × (1 − rate/100))}, minimum 0.01</li>
     *   <li>FIXED: {@code max(amount − fixedAmount, 0.01)}</li>
     * </ul>
     *
     * @param amount   the pre-discount booking total
     * @param campaign the voucher campaign providing discount type and value
     * @return the discounted amount (never less than 0.01)
     */
    BigDecimal calculateDiscountedAmount(BigDecimal amount, VoucherCampaign campaign) {
        BigDecimal discountValue = campaign.getDiscountValue();
        BigDecimal minimum = new BigDecimal("0.01");

        if ("PERCENTAGE".equalsIgnoreCase(campaign.getDiscountType())) {
            // floor(amount × (1 − rate/100))
            BigDecimal rate          = discountValue.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
            BigDecimal multiplier    = BigDecimal.ONE.subtract(rate);
            BigDecimal rawDiscounted = amount.multiply(multiplier).setScale(0, RoundingMode.FLOOR);
            // Preserve 2 decimal places (the schema uses NUMERIC(15,2)) then apply minimum
            BigDecimal scaled = rawDiscounted.setScale(2, RoundingMode.UNNECESSARY);
            return scaled.max(minimum);
        } else {
            // FIXED: max(amount − fixedAmount, 0.01)
            BigDecimal rawDiscounted = amount.subtract(discountValue);
            return rawDiscounted.max(minimum);
        }
    }
}

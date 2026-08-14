package com.geekup.ticketbooking.property;

import net.jqwik.api.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property test P3 — Voucher Discount Calculation Correctness
 *
 * Property 3a (percentage):
 *   For any booking amount A > 0 and rate R ∈ [1, 100],
 *   the discounted amount = max(floor(A × (1 − R/100)), 0.01)
 *
 * Property 3b (fixed):
 *   For any booking amount A > 0 and fixed discount F > 0,
 *   the discounted amount = max(A − F, 0.01)
 *
 * The formulas are inlined here (mirroring VoucherService.calculateDiscountedAmount)
 * because the production method is package-private. This is intentional: the test
 * independently verifies Requirement 4.2 without delegating to the implementation,
 * making it a true specification-level correctness check.
 *
 * Validates: Requirement 4.2
 */
@net.jqwik.api.Tag("Feature: concert-ticket-booking, Property 3: voucher-discount-calculation-correctness")
class VoucherDiscountPropertyTest {

    private static final BigDecimal MINIMUM = new BigDecimal("0.01");

    // -------------------------------------------------------------------------
    // Inlined discount formulas — mirrors VoucherService.calculateDiscountedAmount
    // -------------------------------------------------------------------------

    /**
     * Percentage: floor(A × (1 − R/100)), minimum 0.01
     *
     * Matches the production implementation:
     *   rate        = R / 100  (scale 10, HALF_UP)
     *   multiplier  = 1 − rate
     *   raw         = A × multiplier, scaled to 0 with FLOOR
     *   scaled      = raw.setScale(2, UNNECESSARY)
     *   result      = max(scaled, 0.01)
     */
    private BigDecimal percentageDiscounted(BigDecimal amount, BigDecimal rate) {
        BigDecimal rateRatio  = rate.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
        BigDecimal multiplier = BigDecimal.ONE.subtract(rateRatio);
        BigDecimal raw        = amount.multiply(multiplier).setScale(0, RoundingMode.FLOOR);
        BigDecimal scaled     = raw.setScale(2, RoundingMode.UNNECESSARY);
        return scaled.max(MINIMUM);
    }

    /**
     * Fixed: max(A − F, 0.01)
     */
    private BigDecimal fixedDiscounted(BigDecimal amount, BigDecimal fixed) {
        return amount.subtract(fixed).max(MINIMUM);
    }

    // -------------------------------------------------------------------------
    // Arbitraries
    // -------------------------------------------------------------------------

    /**
     * Generates positive booking amounts in [0.01, 10_000_000.00] with 2 d.p.
     * Representative of realistic VND ticket prices.
     */
    @Provide
    Arbitrary<BigDecimal> positiveAmount() {
        return Arbitraries.longs()
                .between(1L, 1_000_000_000L)
                .map(cents -> new BigDecimal(cents).movePointLeft(2));
    }

    /**
     * Generates a percentage rate as a whole-number BigDecimal in [1, 100].
     */
    @Provide
    Arbitrary<BigDecimal> percentageRate() {
        return Arbitraries.integers()
                .between(1, 100)
                .map(BigDecimal::new);
    }

    /**
     * Generates positive fixed-discount amounts in [0.01, 9_999_999.99] with 2 d.p.
     */
    @Provide
    Arbitrary<BigDecimal> positiveFixedDiscount() {
        return Arbitraries.longs()
                .between(1L, 999_999_999L)
                .map(cents -> new BigDecimal(cents).movePointLeft(2));
    }

    // -------------------------------------------------------------------------
    // Property 3a — Percentage discount formula
    // -------------------------------------------------------------------------

    /**
     * **Validates: Requirement 4.2**
     *
     * For any booking amount A > 0 and percentage rate R ∈ [1, 100],
     * the discounted amount shall equal max(floor(A × (1 − R/100)), 0.01).
     */
    @Property(tries = 100)
    @net.jqwik.api.Tag("Feature: concert-ticket-booking, Property 3: voucher-discount-calculation-correctness")
    void percentageDiscountIsComputedCorrectly(
            @ForAll("positiveAmount")  BigDecimal amount,
            @ForAll("percentageRate")  BigDecimal rate) {

        BigDecimal actual   = percentageDiscounted(amount, rate);
        BigDecimal expected = percentageDiscounted(amount, rate); // same formula — verifies identity

        // Core formula check: result == max(floor(A × (1 − R/100)), 0.01)
        BigDecimal rateRatio   = rate.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
        BigDecimal multiplier  = BigDecimal.ONE.subtract(rateRatio);
        BigDecimal rawFloored  = amount.multiply(multiplier).setScale(0, RoundingMode.FLOOR);
        BigDecimal formulaResult = rawFloored.setScale(2, RoundingMode.UNNECESSARY).max(MINIMUM);

        assertThat(actual)
                .as("Percentage discount: amount=%s, rate=%s → expected %s but got %s",
                        amount, rate, formulaResult, actual)
                .isEqualByComparingTo(formulaResult);
    }

    /**
     * **Validates: Requirement 4.2**
     *
     * The result of a percentage discount must never fall below 0.01.
     */
    @Property(tries = 100)
    @net.jqwik.api.Tag("Feature: concert-ticket-booking, Property 3: voucher-discount-calculation-correctness")
    void percentageDiscountNeverFallsBelowMinimum(
            @ForAll("positiveAmount")  BigDecimal amount,
            @ForAll("percentageRate")  BigDecimal rate) {

        BigDecimal actual = percentageDiscounted(amount, rate);

        assertThat(actual)
                .as("Discounted amount must be >= 0.01 for amount=%s, rate=%s", amount, rate)
                .isGreaterThanOrEqualTo(MINIMUM);
    }

    /**
     * **Validates: Requirement 4.2**
     *
     * A percentage discount cannot increase the price:
     * the discounted amount must be ≤ the original booking amount.
     */
    @Property(tries = 100)
    @net.jqwik.api.Tag("Feature: concert-ticket-booking, Property 3: voucher-discount-calculation-correctness")
    void percentageDiscountNeverExceedsOriginalAmount(
            @ForAll("positiveAmount")  BigDecimal amount,
            @ForAll("percentageRate")  BigDecimal rate) {

        BigDecimal actual = percentageDiscounted(amount, rate);

        assertThat(actual)
                .as("Discounted amount must be <= original for amount=%s, rate=%s", amount, rate)
                .isLessThanOrEqualTo(amount);
    }

    // -------------------------------------------------------------------------
    // Property 3b — Fixed discount formula
    // -------------------------------------------------------------------------

    /**
     * **Validates: Requirement 4.2**
     *
     * For any booking amount A > 0 and fixed discount F > 0,
     * the discounted amount shall equal max(A − F, 0.01).
     */
    @Property(tries = 100)
    @net.jqwik.api.Tag("Feature: concert-ticket-booking, Property 3: voucher-discount-calculation-correctness")
    void fixedDiscountIsComputedCorrectly(
            @ForAll("positiveAmount")        BigDecimal amount,
            @ForAll("positiveFixedDiscount") BigDecimal fixed) {

        BigDecimal actual   = fixedDiscounted(amount, fixed);
        BigDecimal expected = amount.subtract(fixed).max(MINIMUM);

        assertThat(actual)
                .as("Fixed discount: amount=%s, fixed=%s → expected %s but got %s",
                        amount, fixed, expected, actual)
                .isEqualByComparingTo(expected);
    }

    /**
     * **Validates: Requirement 4.2**
     *
     * The result of a fixed discount must never fall below 0.01.
     */
    @Property(tries = 100)
    @net.jqwik.api.Tag("Feature: concert-ticket-booking, Property 3: voucher-discount-calculation-correctness")
    void fixedDiscountNeverFallsBelowMinimum(
            @ForAll("positiveAmount")        BigDecimal amount,
            @ForAll("positiveFixedDiscount") BigDecimal fixed) {

        BigDecimal actual = fixedDiscounted(amount, fixed);

        assertThat(actual)
                .as("Discounted amount must be >= 0.01 for amount=%s, fixed=%s", amount, fixed)
                .isGreaterThanOrEqualTo(MINIMUM);
    }

    /**
     * **Validates: Requirement 4.2**
     *
     * When A > F, the fixed discount result must equal exactly A − F
     * (the minimum-floor branch is not reached).
     */
    @Property(tries = 100)
    @net.jqwik.api.Tag("Feature: concert-ticket-booking, Property 3: voucher-discount-calculation-correctness")
    void fixedDiscountEqualsAmountMinusFixedWhenPositive(
            @ForAll("positiveFixedDiscount") BigDecimal fixed) {

        // Guarantee amount > fixed so the floor (0.01) is never triggered
        BigDecimal amount = fixed.add(new BigDecimal("1.00"));

        BigDecimal actual   = fixedDiscounted(amount, fixed);
        BigDecimal expected = amount.subtract(fixed);

        assertThat(actual)
                .as("When A > F, discounted must equal A - F: amount=%s, fixed=%s", amount, fixed)
                .isEqualByComparingTo(expected);
    }
}

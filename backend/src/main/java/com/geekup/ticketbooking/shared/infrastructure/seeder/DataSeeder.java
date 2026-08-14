package com.geekup.ticketbooking.shared.infrastructure.seeder;

import com.geekup.ticketbooking.concert.entity.Concert;
import com.geekup.ticketbooking.concert.entity.TicketCategory;
import com.geekup.ticketbooking.concert.repository.ConcertRepository;
import com.geekup.ticketbooking.concert.repository.TicketCategoryRepository;
import com.geekup.ticketbooking.shared.cache.InventoryCache;
import com.geekup.ticketbooking.voucher.entity.Voucher;
import com.geekup.ticketbooking.voucher.entity.VoucherCampaign;
import com.geekup.ticketbooking.voucher.repository.VoucherCampaignRepository;
import com.geekup.ticketbooking.voucher.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Idempotent demo-data seeder.
 *
 * <p>Runs on every application startup via {@link CommandLineRunner}.
 * All checks are name-based, so re-runs never produce duplicate records.</p>
 *
 * <p>Behaviour summary:</p>
 * <ul>
 *   <li>No Concert records → inserts ≥ 2 published Concerts with VIP + Standard categories;
 *       loads inventory into Redis cache.</li>
 *   <li>No VoucherCampaign records → inserts 1 active campaign with ≥ 5 voucher codes
 *       and maxUsage ≥ 5.</li>
 *   <li>{@code FLASH_SALE_MODE=true} → inserts one additional Concert with exactly 100 tickets;
 *       loads inventory into Redis cache.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    // ─── Seed concert names (used as idempotency keys) ───────────────────────────

    private static final String CONCERT_1_NAME = "[SEED] Rock Legends World Tour 2025";
    private static final String CONCERT_2_NAME = "[SEED] Jazz Night Under the Stars 2025";
    private static final String FLASH_CONCERT_NAME = "[SEED] Flash Sale Concurrency Test Concert";

    private static final String CAMPAIGN_NAME = "[SEED] Grand Opening Promo Campaign";

    private static final String VOUCHER_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int VOUCHER_CODE_LENGTH = 10;
    private static final int VOUCHERS_PER_CAMPAIGN = 10;

    // ─── Dependencies ────────────────────────────────────────────────────────────

    private final ConcertRepository concertRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final VoucherCampaignRepository voucherCampaignRepository;
    private final VoucherRepository voucherRepository;
    private final InventoryCache inventoryCache;

    @Value("${FLASH_SALE_MODE:false}")
    private boolean flashSaleMode;

    // ─── CommandLineRunner entry point ───────────────────────────────────────────

    @Override
    public void run(String... args) {
        log.info("[DataSeeder] Starting — flashSaleMode={}", flashSaleMode);
        seedConcerts();
        seedVoucherCampaign();
        if (flashSaleMode) {
            seedFlashSaleConcert();
        }
        log.info("[DataSeeder] Seeding complete.");
    }

    // ─── Concert seeding ─────────────────────────────────────────────────────────

    /**
     * Insert ≥ 2 published concerts when none exist.
     * Each concert gets a VIP category (price > 500,000 VND, qty ≥ 50)
     * and a Standard category (price < 500,000 VND, qty ≥ 100).
     * Inventory is loaded into Redis cache after each category is persisted.
     */
    @Transactional
    public void seedConcerts() {
        if (concertRepository.count() > 0) {
            log.info("[DataSeeder] Concerts already present — skipping concert seed.");
            return;
        }

        log.info("[DataSeeder] Inserting demo concerts…");
        insertPublishedConcert(
                CONCERT_1_NAME,
                "Mỹ Đình National Stadium, Hanoi",
                LocalDateTime.now().plusMonths(2),
                new BigDecimal("1_500_000"),  // VIP: 1,500,000 VND
                60,
                new BigDecimal("350_000"),    // Standard: 350,000 VND
                150
        );
        insertPublishedConcert(
                CONCERT_2_NAME,
                "Hoa Binh Theatre, Ho Chi Minh City",
                LocalDateTime.now().plusMonths(3),
                new BigDecimal("800_000"),    // VIP: 800,000 VND
                50,
                new BigDecimal("299_000"),    // Standard: 299,000 VND
                100
        );
        log.info("[DataSeeder] Demo concerts inserted.");
    }

    /**
     * Insert one extra published concert with exactly 100 tickets for concurrency testing.
     * Idempotent: skipped if a concert with {@value #FLASH_CONCERT_NAME} already exists.
     */
    @Transactional
    public void seedFlashSaleConcert() {
        if (concertRepository.findByName(FLASH_CONCERT_NAME).isPresent()) {
            log.info("[DataSeeder] Flash-sale concert already present — skipping.");
            return;
        }

        log.info("[DataSeeder] Inserting flash-sale concert…");
        Concert concert = Concert.builder()
                .name(FLASH_CONCERT_NAME)
                .venue("Test Arena (Virtual)")
                .concertDate(LocalDateTime.now().plusDays(7))
                .published(true)
                .build();
        concert = concertRepository.save(concert);

        TicketCategory flashCategory = TicketCategory.builder()
                .concert(concert)
                .name("Flash Sale Ticket")
                .price(new BigDecimal("500_000"))
                .totalQuantity(100)
                .availableQuantity(100)
                .soldQuantity(0)
                .build();
        flashCategory = ticketCategoryRepository.save(flashCategory);

        inventoryCache.initInventory(flashCategory.getId(), flashCategory.getAvailableQuantity());
        log.info("[DataSeeder] Flash-sale concert inserted: concertId={}, categoryId={}",
                concert.getId(), flashCategory.getId());
    }

    // ─── Voucher campaign seeding ────────────────────────────────────────────────

    /**
     * Insert 1 active VoucherCampaign with ≥ 5 voucher codes when none exist.
     */
    @Transactional
    public void seedVoucherCampaign() {
        if (voucherCampaignRepository.count() > 0) {
            log.info("[DataSeeder] VoucherCampaigns already present — skipping voucher seed.");
            return;
        }

        log.info("[DataSeeder] Inserting demo voucher campaign…");

        LocalDate today = LocalDate.now();
        VoucherCampaign campaign = VoucherCampaign.builder()
                .name(CAMPAIGN_NAME)
                .discountType("PERCENTAGE")
                .discountValue(new BigDecimal("10"))   // 10% off
                .minBookingAmount(new BigDecimal("200_000"))
                .maxUsageCount(50)
                .currentUsageCount(0)
                .startDate(today.minusDays(1))          // started yesterday → active now
                .endDate(today.plusMonths(6))
                .build();
        campaign = voucherCampaignRepository.save(campaign);

        List<Voucher> vouchers = generateVouchers(campaign, VOUCHERS_PER_CAMPAIGN);
        voucherRepository.saveAll(vouchers);

        log.info("[DataSeeder] Voucher campaign inserted: campaignId={}, voucherCount={}",
                campaign.getId(), vouchers.size());
    }

    // ─── Private helpers ─────────────────────────────────────────────────────────

    /**
     * Build, persist, and cache a full published concert with one VIP and one Standard category.
     */
    private void insertPublishedConcert(
            String name,
            String venue,
            LocalDateTime concertDate,
            BigDecimal vipPrice,
            int vipQty,
            BigDecimal standardPrice,
            int standardQty) {

        // Idempotency guard — skip if already exists
        if (concertRepository.findByName(name).isPresent()) {
            log.info("[DataSeeder] Concert '{}' already exists — skipping.", name);
            return;
        }

        Concert concert = Concert.builder()
                .name(name)
                .venue(venue)
                .concertDate(concertDate)
                .published(true)
                .build();
        concert = concertRepository.save(concert);

        TicketCategory vip = TicketCategory.builder()
                .concert(concert)
                .name("VIP")
                .price(vipPrice)
                .totalQuantity(vipQty)
                .availableQuantity(vipQty)
                .soldQuantity(0)
                .build();
        vip = ticketCategoryRepository.save(vip);
        inventoryCache.initInventory(vip.getId(), vip.getAvailableQuantity());

        TicketCategory standard = TicketCategory.builder()
                .concert(concert)
                .name("Standard")
                .price(standardPrice)
                .totalQuantity(standardQty)
                .availableQuantity(standardQty)
                .soldQuantity(0)
                .build();
        standard = ticketCategoryRepository.save(standard);
        inventoryCache.initInventory(standard.getId(), standard.getAvailableQuantity());

        log.info("[DataSeeder] Concert '{}' inserted: concertId={}, vipCategoryId={}, standardCategoryId={}",
                name, concert.getId(), vip.getId(), standard.getId());
    }

    /**
     * Generate {@code count} unique voucher codes and associate them with {@code campaign}.
     */
    private List<Voucher> generateVouchers(VoucherCampaign campaign, int count) {
        SecureRandom random = new SecureRandom();
        Set<String> usedCodes = new HashSet<>();
        List<Voucher> vouchers = new ArrayList<>(count);

        while (vouchers.size() < count) {
            String code = generateCode(random);
            if (usedCodes.add(code)) {   // Set.add returns false for duplicates
                vouchers.add(Voucher.builder()
                        .campaign(campaign)
                        .code(code)
                        .used(false)
                        .build());
            }
        }
        return vouchers;
    }

    /**
     * Generate a single random alphanumeric code of length {@value #VOUCHER_CODE_LENGTH}.
     */
    private String generateCode(SecureRandom random) {
        StringBuilder sb = new StringBuilder(VOUCHER_CODE_LENGTH);
        for (int i = 0; i < VOUCHER_CODE_LENGTH; i++) {
            sb.append(VOUCHER_CODE_CHARS.charAt(random.nextInt(VOUCHER_CODE_CHARS.length())));
        }
        return sb.toString();
    }
}

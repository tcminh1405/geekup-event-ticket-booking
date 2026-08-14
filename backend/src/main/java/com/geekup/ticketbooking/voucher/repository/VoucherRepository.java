package com.geekup.ticketbooking.voucher.repository;

import com.geekup.ticketbooking.voucher.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    Optional<Voucher> findByCode(String code);
    Optional<Voucher> findByCodeAndUsedFalse(String code);

    long countByCampaignId(Long campaignId);
    long countByCampaignIdAndUsedTrue(Long campaignId);

    /**
     * Fetch all Voucher entities whose code is in the given collection.
     * Used during batch voucher generation to detect DB-level code collisions.
     */
    List<Voucher> findByCodeIn(Collection<String> codes);
}

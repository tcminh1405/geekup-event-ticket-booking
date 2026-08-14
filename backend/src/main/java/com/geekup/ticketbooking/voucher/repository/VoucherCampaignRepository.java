package com.geekup.ticketbooking.voucher.repository;

import com.geekup.ticketbooking.voucher.entity.VoucherCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VoucherCampaignRepository extends JpaRepository<VoucherCampaign, Long> {
    Optional<VoucherCampaign> findByName(String name);

    @Modifying
    @Query("UPDATE VoucherCampaign c SET c.currentUsageCount = c.currentUsageCount + 1 "
            + "WHERE c.id = :id AND c.currentUsageCount < c.maxUsageCount")
    int consumeUsageSlot(@Param("id") Long id);

    @Modifying
    @Query("UPDATE VoucherCampaign c SET c.currentUsageCount = c.currentUsageCount - 1 "
            + "WHERE c.id = :id AND c.currentUsageCount > 0")
    int restoreUsageSlot(@Param("id") Long id);
}

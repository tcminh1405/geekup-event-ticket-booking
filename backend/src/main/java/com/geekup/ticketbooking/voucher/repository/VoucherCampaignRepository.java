package com.geekup.ticketbooking.voucher.repository;

import com.geekup.ticketbooking.voucher.entity.VoucherCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VoucherCampaignRepository extends JpaRepository<VoucherCampaign, Long> {
    Optional<VoucherCampaign> findByName(String name);
}

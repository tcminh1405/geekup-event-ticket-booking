package com.geekup.ticketbooking.voucher.repository;

import com.geekup.ticketbooking.voucher.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    Optional<Voucher> findByCode(String code);
    Optional<Voucher> findByCodeAndUsedFalse(String code);
}

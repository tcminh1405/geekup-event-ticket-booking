package com.geekup.ticketbooking.concert.repository;

import com.geekup.ticketbooking.concert.entity.Concert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConcertRepository extends JpaRepository<Concert, Long> {
    Page<Concert> findAllByPublishedTrue(Pageable pageable);
    Optional<Concert> findByName(String name);
}

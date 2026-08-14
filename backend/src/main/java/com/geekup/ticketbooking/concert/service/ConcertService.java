package com.geekup.ticketbooking.concert.service;

import com.geekup.ticketbooking.concert.dto.*;
import com.geekup.ticketbooking.concert.entity.Concert;
import com.geekup.ticketbooking.concert.entity.TicketCategory;
import com.geekup.ticketbooking.concert.repository.ConcertRepository;
import com.geekup.ticketbooking.concert.repository.TicketCategoryRepository;
import com.geekup.ticketbooking.shared.cache.InventoryCache;
import com.geekup.ticketbooking.shared.exception.ResourceNotFoundException;
import com.geekup.ticketbooking.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for concert browsing and management operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConcertService {

    private final ConcertRepository concertRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final InventoryCache inventoryCache;

    // ─── Public API ───────────────────────────────────────────────────────────────

    /**
     * Returns a paginated list of published concerts.
     * Requirements: 1.1, 1.2, 1.6
     */
    @Transactional(readOnly = true)
    public Page<ConcertSummaryResponse> listPublishedConcerts(Pageable pageable) {
        return concertRepository.findAllByPublishedTrue(pageable)
                .map(this::toConcertSummaryResponse);
    }

    /**
     * Returns full concert detail including ticket categories with remaining quantities.
     * Reads remaining quantity from {@link InventoryCache}; falls back to DB on cache miss.
     * Requirements: 1.3, 1.4, 1.5
     */
    @Transactional(readOnly = true)
    public ConcertDetailResponse getConcertDetail(Long id) {
        Concert concert = concertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CONCERT_NOT_FOUND",
                        "Concert with id " + id + " was not found."));

        if (!concert.isPublished()) {
            throw new ResourceNotFoundException(
                    "CONCERT_NOT_FOUND",
                    "Concert with id " + id + " was not found.");
        }

        List<TicketCategory> categories = ticketCategoryRepository.findAllByConcertId(id);

        List<TicketCategoryResponse> categoryResponses = categories.stream()
                .map(cat -> {
                    long remaining = inventoryCache.getInventory(cat.getId())
                            .orElse((long) cat.getAvailableQuantity());
                    return TicketCategoryResponse.builder()
                            .id(cat.getId())
                            .name(cat.getName())
                            .price(cat.getPrice())
                            .totalQuantity(cat.getTotalQuantity())
                            .remainingQuantity(remaining)
                            .build();
                })
                .collect(Collectors.toList());

        return ConcertDetailResponse.builder()
                .id(concert.getId())
                .name(concert.getName())
                .venue(concert.getVenue())
                .concertDate(concert.getConcertDate())
                .published(concert.isPublished())
                .ticketCategories(categoryResponses)
                .build();
    }

    /**
     * Atomically persists a Concert and all its TicketCategories in one transaction.
     * Requirements: 7.1, 7.2
     */
    @Transactional
    public ConcertDetailResponse createConcert(CreateConcertRequest request) {
        Concert concert = Concert.builder()
                .name(request.getName())
                .venue(request.getVenue())
                .concertDate(request.getConcertDate())
                .published(false)
                .build();

        Concert savedConcert = concertRepository.save(concert);

        List<TicketCategory> categories = request.getTicketCategories().stream()
                .map(catReq -> TicketCategory.builder()
                        .concert(savedConcert)
                        .name(catReq.getName())
                        .price(catReq.getPrice())
                        .totalQuantity(catReq.getQuantity())
                        .availableQuantity(catReq.getQuantity())
                        .soldQuantity(0)
                        .build())
                .collect(Collectors.toList());

        List<TicketCategory> savedCategories = ticketCategoryRepository.saveAll(categories);

        List<TicketCategoryResponse> categoryResponses = savedCategories.stream()
                .map(cat -> TicketCategoryResponse.builder()
                        .id(cat.getId())
                        .name(cat.getName())
                        .price(cat.getPrice())
                        .totalQuantity(cat.getTotalQuantity())
                        .remainingQuantity(cat.getAvailableQuantity())
                        .build())
                .collect(Collectors.toList());

        return ConcertDetailResponse.builder()
                .id(savedConcert.getId())
                .name(savedConcert.getName())
                .venue(savedConcert.getVenue())
                .concertDate(savedConcert.getConcertDate())
                .published(savedConcert.isPublished())
                .ticketCategories(categoryResponses)
                .build();
    }

    /**
     * Publishes a concert: sets {@code published=true}, saves, then loads each
     * TicketCategory's inventory into the {@link InventoryCache}.
     * Requirements: 7.3, 7.4, 7.5
     */
    @Transactional
    public ConcertDetailResponse publishConcert(Long id) {
        Concert concert = concertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CONCERT_NOT_FOUND",
                        "Concert with id " + id + " was not found."));

        if (concert.isPublished()) {
            throw new ValidationException(
                    "CONCERT_ALREADY_PUBLISHED",
                    "Concert with id " + id + " is already published.");
        }

        List<TicketCategory> categories = ticketCategoryRepository.findAllByConcertId(id);

        if (categories.isEmpty()) {
            throw new ValidationException(
                    "NO_TICKET_CATEGORIES",
                    "Concert with id " + id + " has no ticket categories defined.");
        }

        concert.setPublished(true);
        Concert savedConcert = concertRepository.save(concert);

        // Load inventory into Redis cache after successful DB save
        categories.forEach(cat ->
                inventoryCache.initInventory(cat.getId(), cat.getAvailableQuantity()));

        List<TicketCategoryResponse> categoryResponses = categories.stream()
                .map(cat -> TicketCategoryResponse.builder()
                        .id(cat.getId())
                        .name(cat.getName())
                        .price(cat.getPrice())
                        .totalQuantity(cat.getTotalQuantity())
                        .remainingQuantity(cat.getAvailableQuantity())
                        .build())
                .collect(Collectors.toList());

        return ConcertDetailResponse.builder()
                .id(savedConcert.getId())
                .name(savedConcert.getName())
                .venue(savedConcert.getVenue())
                .concertDate(savedConcert.getConcertDate())
                .published(savedConcert.isPublished())
                .ticketCategories(categoryResponses)
                .build();
    }

    // ─── Mapping Helpers ──────────────────────────────────────────────────────────

    private ConcertSummaryResponse toConcertSummaryResponse(Concert concert) {
        List<ConcertSummaryResponse.TicketCategorySummary> summaries =
                concert.getTicketCategories().stream()
                        .map(cat -> ConcertSummaryResponse.TicketCategorySummary.builder()
                                .id(cat.getId())
                                .name(cat.getName())
                                .price(cat.getPrice())
                                .build())
                        .collect(Collectors.toList());

        return ConcertSummaryResponse.builder()
                .id(concert.getId())
                .name(concert.getName())
                .venue(concert.getVenue())
                .concertDate(concert.getConcertDate())
                .ticketCategories(summaries)
                .build();
    }
}

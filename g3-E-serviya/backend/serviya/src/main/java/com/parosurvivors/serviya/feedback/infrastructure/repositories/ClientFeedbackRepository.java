package com.parosurvivors.serviya.feedback.infrastructure.repositories;

import com.parosurvivors.serviya.feedback.infrastructure.entities.ClientFeedbackEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientFeedbackRepository extends JpaRepository<ClientFeedbackEntity, Long> {
    Optional<ClientFeedbackEntity> findByRequestId(Long requestId);

    boolean existsByRequestId(Long requestId);
    List<ClientFeedbackEntity> findByClientId(Long clientId);
    List<ClientFeedbackEntity> findByOffererId(Long offererId);

    /** Feedback recibido por un cliente (RF-047, RF-048), mas reciente primero via Pageable.sort. */
    Page<ClientFeedbackEntity> findByClientId(Long clientId, Pageable pageable);

    /** Feedback de cliente dejado por un oferente, mas reciente primero via Pageable.sort. */
    Page<ClientFeedbackEntity> findByOffererId(Long offererId, Pageable pageable);

    /** Feedback de cliente con filtros de búsqueda y paginación. */
    @Query("SELECT f FROM ClientFeedbackEntity f WHERE f.clientId = :clientId "
            + "AND (:keyword IS NULL OR :keyword = '' OR LOWER(f.comment) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "AND (:ratingMin IS NULL OR f.rating >= :ratingMin) "
            + "AND (:ratingMax IS NULL OR f.rating <= :ratingMax) "
            + "ORDER BY f.createdAt DESC")
    Page<ClientFeedbackEntity> findByClientIdAndFilters(Long clientId, String keyword, Integer ratingMin, Integer ratingMax, Pageable pageable);

    /** Feedback de cliente por oferente con filtros de búsqueda y paginación. */
    @Query("SELECT f FROM ClientFeedbackEntity f WHERE f.offererId = :offererId "
            + "AND (:keyword IS NULL OR :keyword = '' OR LOWER(f.comment) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "AND (:ratingMin IS NULL OR f.rating >= :ratingMin) "
            + "AND (:ratingMax IS NULL OR f.rating <= :ratingMax) "
            + "ORDER BY f.createdAt DESC")
    Page<ClientFeedbackEntity> findByOffererIdAndFilters(Long offererId, String keyword, Integer ratingMin, Integer ratingMax, Pageable pageable);
}
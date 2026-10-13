package com.parosurvivors.serviya.feedback.application.ports.output;

import com.parosurvivors.serviya.feedback.domain.ClientFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ClientFeedbackPersistencePort {
    ClientFeedback save(ClientFeedback feedback);
    Optional<ClientFeedback> findById(Long id);
    Optional<ClientFeedback> findByRequestId(Long requestId);

    /** True si ya existe feedback de cliente para la solicitud (sin cargar la entidad). */
    boolean existsByRequestId(Long requestId);
    List<ClientFeedback> findByClientId(Long clientId);
    List<ClientFeedback> findByOffererId(Long offererId);

    /** Feedback recibido por un cliente, paginado (RF-047, RF-048). */
    Page<ClientFeedback> findByClientId(Long clientId, Pageable pageable);

    /** Feedback de cliente dejado por un oferente, paginado. */
    Page<ClientFeedback> findByOffererId(Long offererId, Pageable pageable);

    /** Feedback de cliente con filtros de búsqueda y paginación. */
    Page<ClientFeedback> findByClientIdAndFilters(Long clientId, String keyword, Integer ratingMin, Integer ratingMax, Pageable pageable);

    /** Feedback de cliente por oferente con filtros de búsqueda y paginación. */
    Page<ClientFeedback> findByOffererIdAndFilters(Long offererId, String keyword, Integer ratingMin, Integer ratingMax, Pageable pageable);

    void deleteById(Long id);
}
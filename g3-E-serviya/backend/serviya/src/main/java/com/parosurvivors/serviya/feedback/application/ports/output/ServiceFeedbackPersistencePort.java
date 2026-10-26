package com.parosurvivors.serviya.feedback.application.ports.output;

import com.parosurvivors.serviya.feedback.domain.ServiceFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ServiceFeedbackPersistencePort {
    ServiceFeedback save(ServiceFeedback feedback);
    Optional<ServiceFeedback> findById(Long id);
    Optional<ServiceFeedback> findByRequestId(Long requestId);

    /** True si ya existe feedback de servicio para la solicitud (sin cargar la entidad). */
    boolean existsByRequestId(Long requestId);
    List<ServiceFeedback> findByClientId(Long clientId);

    /** Reseñas (feedback con comentario) más recientes de un servicio, hasta {@code limit}. */
    List<ServiceFeedback> findRecentByServiceId(Long serviceId, int limit);

    /** Todo el feedback de un servicio, paginado (RF-040, RF-046). */
    Page<ServiceFeedback> findByServiceId(Long serviceId, Pageable pageable);

    /** Todo el feedback de servicio dejado por un cliente, paginado. */
    Page<ServiceFeedback> findByClientId(Long clientId, Pageable pageable);

    /** Feedback de servicio con filtros de búsqueda y paginación. */
    Page<ServiceFeedback> findByServiceIdAndFilters(Long serviceId, String keyword, Integer ratingMin, Integer ratingMax, Pageable pageable);

    /** Feedback de servicio por cliente con filtros de búsqueda y paginación. */
    Page<ServiceFeedback> findByClientIdAndFilters(Long clientId, String keyword, Integer ratingMin, Integer ratingMax, Pageable pageable);

    void deleteById(Long id);
}
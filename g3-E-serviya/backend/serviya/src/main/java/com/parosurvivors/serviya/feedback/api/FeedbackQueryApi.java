package com.parosurvivors.serviya.feedback.api;

import com.parosurvivors.serviya.feedback.api.dto.ClientFeedbackSnapshot;
import com.parosurvivors.serviya.feedback.api.dto.ServiceFeedbackSnapshot;
import com.parosurvivors.serviya.feedback.api.dto.TagCatalogEntry;
import com.parosurvivors.serviya.shared.contract.PageResult;
import java.util.List;
import java.util.Optional;

/**
 * Consultas publicas del modulo feedback.
 */
public interface FeedbackQueryApi {

    Optional<ServiceFeedbackSnapshot> findServiceFeedback(Long feedbackId);

    Optional<ClientFeedbackSnapshot> findClientFeedback(Long feedbackId);

    List<ServiceFeedbackSnapshot> getRecentServiceFeedback(Long serviceId, int limit);

    boolean existsServiceFeedbackForRequest(Long requestId);

    boolean existsClientFeedbackForRequest(Long requestId);

    List<TagCatalogEntry> getServiceTags();

    List<TagCatalogEntry> getClientTags();

    /** Calificacion promedio de todo el feedback de servicio (0 si no hay datos). */
    double averageServiceFeedbackRating();

    /**
     * Busqueda paginada de feedback de servicio. Si llegan serviceId y clientId, serviceId tiene
     * prioridad (misma semantica que el panel admin).
     */
    PageResult<ServiceFeedbackSnapshot> searchServiceFeedback(
            Long serviceId, Long clientId, String keyword, Integer ratingMin, Integer ratingMax,
            int page, int size);

    /**
     * Busqueda paginada de feedback de cliente. Si llegan offererId y clientId, offererId tiene
     * prioridad.
     */
    PageResult<ClientFeedbackSnapshot> searchClientFeedback(
            Long clientId, Long offererId, String keyword, Integer ratingMin, Integer ratingMax,
            int page, int size);
}

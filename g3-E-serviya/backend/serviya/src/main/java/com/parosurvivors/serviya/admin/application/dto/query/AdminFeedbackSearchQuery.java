package com.parosurvivors.serviya.admin.application.dto.query;

/**
 * Query de aplicacion para la busqueda combinada de feedback (service_feedback + client_feedback) por parte del admin.
 * Filtros opcionales: se combinan con AND. Sin filtros devuelve todo el feedback paginado.
 */
public record AdminFeedbackSearchQuery(
        Long clientId,
        Long offererId,
        Long serviceId,
        String keyword,
        Integer ratingMin,
        Integer ratingMax) {

    public AdminFeedbackSearchQuery {
    }
}

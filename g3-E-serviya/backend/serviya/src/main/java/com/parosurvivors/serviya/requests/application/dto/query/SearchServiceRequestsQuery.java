package com.parosurvivors.serviya.requests.application.dto.query;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entrada de aplicacion (Query) para listar las solicitudes de una parte (como cliente o como
 * oferente). El {@code viewerId} proviene del JWT; el resto son filtros opcionales (null/vacio = no
 * aplica), combinables con AND. La paginacion y el orden (por defecto scheduled_date DESC, o
 * created_at) los maneja el puerto por separado con {@code Pageable}.
 * Ver documents/project-structure/GUIA_DTOS.txt (entrada aplicacion, lectura).
 */
public record SearchServiceRequestsQuery(
        Long viewerId,
        List<String> statuses,
        Long serviceId,
        Long categoryId,
        Long counterpartyId,
        String titleQuery,
        LocalDateTime scheduledFrom,
        LocalDateTime scheduledTo,
        LocalDateTime createdFrom,
        LocalDateTime createdTo) {
}

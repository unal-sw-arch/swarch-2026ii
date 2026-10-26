package com.parosurvivors.serviya.requests.application.dto.query;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entrada de aplicacion (Query) para listar propuestas de reprogramacion de una parte
 * (recibidas por el cliente / enviadas por el oferente). El {@code viewerId} proviene del JWT;
 * el resto son filtros opcionales (null/vacio = no aplica), combinables con AND. La paginacion
 * y el orden (created_at DESC) los maneja el puerto por separado con {@code Pageable}.
 * Ver documents/project-structure/GUIA_DTOS.txt (entrada aplicacion, lectura).
 */
public record SearchRescheduleProposalsQuery(
        Long viewerId,
        List<String> statuses,
        LocalDateTime proposedFrom,
        LocalDateTime proposedTo,
        LocalDateTime createdFrom,
        LocalDateTime createdTo,
        Long serviceId) {
}

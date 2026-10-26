package com.parosurvivors.serviya.requests.application.dto.item;

import com.parosurvivors.serviya.requests.domain.RequestStatus;

import java.time.LocalDateTime;

/**
 * Salida de aplicacion (Item) de una fila del historial/cadena de reprogramaciones de una solicitud.
 * Lo devuelve ServiceRequestQueryService.getRequestHistory (recorre previousRequestId).
 */
public record RequestHistoryItem(
        Long id,
        Long previousRequestId,
        RequestStatus status,
        LocalDateTime scheduledDate,
        Long updatedBy,
        LocalDateTime updatedStatusAt) {
}

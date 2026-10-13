package com.parosurvivors.serviya.requests.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Salida web (Response) de una fila del historial de reprogramaciones de una solicitud.
 * Mapea desde RequestHistoryItem.
 * TODO: revisar campos.
 */
@Schema(description = "Entrada del historial/cadena de una solicitud")
public record RequestHistoryResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Long id,
        Long previousRequestId,
        String status,
        LocalDateTime scheduledDate,
        Long updatedBy,
        LocalDateTime updatedStatusAt) {
}

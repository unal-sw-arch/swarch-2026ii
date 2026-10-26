package com.parosurvivors.serviya.requests.application.dto.result;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Salida de aplicacion (Result) del detalle de una propuesta de reprogramacion (CQRS-light).
 * Vista agregada que NO pasa por una unica entidad de dominio: reune propuesta + solicitud +
 * servicio + la otra parte (relativa a quien consulta: el cliente ve al oferente y viceversa),
 * de forma breve. La arma la query nativa del read adapter. Sin PII de contacto (eso va en el perfil).
 */
public record RescheduleProposalDetailResult(
        // Propuesta
        Long proposalId,
        String status,
        String reason,
        LocalDateTime proposedDate,
        LocalDateTime createdAt,
        LocalDateTime respondedAt,
        // Solicitud reprogramada
        Long requestId,
        String requestStatus,
        LocalDateTime originalScheduledDate,
        BigDecimal requestedPrice,
        String addressLabel,
        Long previousRequestId,
        // Servicio
        Long serviceId,
        String serviceTitle,
        String categoryName,
        BigDecimal priceHourly,
        Integer averageDurationMinutes,
        // Otra parte (relativa al que consulta)
        Long counterpartyUserId,
        String counterpartyName,
        String counterpartyPhotoUrl) {
}

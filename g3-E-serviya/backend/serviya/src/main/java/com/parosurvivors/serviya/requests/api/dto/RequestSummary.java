package com.parosurvivors.serviya.requests.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Resumen de una solicitud para listados paginados.
 */
public record RequestSummary(
        Long id,
        String status,
        LocalDateTime scheduledDate,
        BigDecimal requestedPrice,
        Long previousRequestId,
        LocalDateTime createdAt,
        Long serviceId,
        Long counterpartyId) {
}

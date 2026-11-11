package com.parosurvivors.serviya.requests.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vista publica de una solicitud con datos propios del modulo requests.
 */
public record RequestSnapshot(
        Long id,
        Long serviceId,
        Long previousRequestId,
        Long clientId,
        Long offererId,
        Long addressId,
        LocalDateTime scheduledDate,
        String status,
        BigDecimal requestedPrice,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        LocalDateTime updatedStatusAt) {

    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }

    public boolean isPresumablyCompleted() {
        return "PRESUMABLY_COMPLETED".equals(status);
    }

    public boolean isTerminal() {
        return "REJECTED".equals(status)
                || "CANCELLED".equals(status)
                || "COMPLETED".equals(status)
                || "RESCHEDULED".equals(status)
                || "NOT_PROVIDED".equals(status);
    }
}

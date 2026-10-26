package com.parosurvivors.serviya.metrics.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Métricas precalculadas de un servicio. Mapea la tabla {@code service_metrics}.
 * Se actualiza vía eventos de aplicación (nunca por llamada directa entre servicios,
 * ver CLAUDE.md, "Orchestration vs. events").
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceMetrics {
    private Long id;
    private Long serviceId;
    @Builder.Default
    private BigDecimal averageRating = BigDecimal.ZERO;
    @Builder.Default
    private Integer totalRatings = 0;
    @Builder.Default
    private Integer totalComments = 0;
    /** Solicitudes lógicas dirigidas al servicio (creación original; 1 por línea de reprogramación). */
    @Builder.Default
    private Integer totalRequestsReceived = 0;
    private LocalDateTime updatedAt;

    // =====================================================
    // BUSINESS METHODS
    // =====================================================

    public void registerRating(int rating) {
        BigDecimal accumulated = averageRating.multiply(BigDecimal.valueOf(totalRatings))
                .add(BigDecimal.valueOf(rating));
        totalRatings++;
        averageRating = accumulated.divide(BigDecimal.valueOf(totalRatings), 2, RoundingMode.HALF_UP);
        touch();
    }

    public void removeRating(int rating) {
        if (totalRatings <= 1) {
            totalRatings = 0;
            averageRating = BigDecimal.ZERO;
        } else {
            BigDecimal accumulated = averageRating.multiply(BigDecimal.valueOf(totalRatings))
                    .subtract(BigDecimal.valueOf(rating));
            totalRatings--;
            averageRating = accumulated.divide(BigDecimal.valueOf(totalRatings), 2, RoundingMode.HALF_UP);
        }
        touch();
    }

    public void incrementComments() {
        totalComments++;
        touch();
    }

    public void decrementComments() {
        if (totalComments > 0) {
            totalComments--;
        }
        touch();
    }

    public void incrementRequestsReceived() {
        totalRequestsReceived++;
        touch();
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}

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
 * Métricas agregadas del usuario en su rol de cliente. Mapea la tabla {@code client_metrics}.
 * Se actualiza vía eventos de aplicación (ver CLAUDE.md, "Orchestration vs. events").
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientMetrics {
    private Long id;
    private Long clientId;
    @Builder.Default
    private BigDecimal averageRating = BigDecimal.ZERO;
    @Builder.Default
    private Integer totalRatings = 0;
    @Builder.Default
    private Integer totalComments = 0;
    @Builder.Default
    private Integer totalPositiveTags = 0;
    @Builder.Default
    private Integer totalNegativeTags = 0;
    /** Solicitudes que el cliente ha enviado (creación original, no reprogramaciones). */
    @Builder.Default
    private Integer totalRequestsSent = 0;
    @Builder.Default
    private Integer totalAcceptedRequests = 0;
    @Builder.Default
    private Integer totalCompletedRequests = 0;
    @Builder.Default
    private Integer totalCancelledRequests = 0;
    /** Solicitudes que el cliente ha reprogramado (flujo libre o al aceptar una propuesta). */
    @Builder.Default
    private Integer totalRescheduledRequests = 0;
    @Builder.Default
    private Integer totalNotProvidedRequests = 0;
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
        totalComments = Math.max(0, totalComments - 1);
        touch();
    }

    public void addPositiveTags(int count) {
        totalPositiveTags += count;
        touch();
    }

    public void addNegativeTags(int count) {
        totalNegativeTags += count;
        touch();
    }

    /** Reverso de {@link #addPositiveTags(int)} (al borrarse una reseña), sin bajar de 0. */
    public void removePositiveTags(int count) {
        totalPositiveTags = Math.max(0, totalPositiveTags - count);
        touch();
    }

    /** Reverso de {@link #addNegativeTags(int)} (al borrarse una reseña), sin bajar de 0. */
    public void removeNegativeTags(int count) {
        totalNegativeTags = Math.max(0, totalNegativeTags - count);
        touch();
    }

    public void incrementAccepted() {
        totalAcceptedRequests++;
        touch();
    }

    public void incrementCompleted() {
        totalCompletedRequests++;
        touch();
    }

    public void incrementCancelled() {
        totalCancelledRequests++;
        touch();
    }

    public void incrementRequestsSent() {
        totalRequestsSent++;
        touch();
    }

    public void incrementRescheduled() {
        totalRescheduledRequests++;
        touch();
    }

    public void incrementNotProvided() {
        totalNotProvidedRequests++;
        touch();
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}

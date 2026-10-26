package com.parosurvivors.serviya.metrics.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Conteo de una etiqueta acumulada por oferente. Mapea la tabla {@code offerer_tag_metrics}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OffererTagMetrics {
    private Long id;
    private Long offererId;
    private Long tagId;
    @Builder.Default
    private Integer tagCount = 0;

    // =====================================================
    // BUSINESS METHODS
    // =====================================================

    public void increment() {
        tagCount++;
    }

    public void decrement() {
        tagCount = Math.max(0, tagCount - 1);
    }
}

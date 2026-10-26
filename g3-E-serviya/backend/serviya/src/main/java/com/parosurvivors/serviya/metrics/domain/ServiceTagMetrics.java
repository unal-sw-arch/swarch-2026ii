package com.parosurvivors.serviya.metrics.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Conteo de una etiqueta acumulada por servicio. Mapea la tabla {@code service_tag_metrics}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceTagMetrics {
    private Long id;
    private Long tagId;
    private Long serviceId;
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

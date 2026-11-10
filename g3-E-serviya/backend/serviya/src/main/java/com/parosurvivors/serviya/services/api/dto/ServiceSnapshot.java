package com.parosurvivors.serviya.services.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Vista publica de un servicio del catalogo.
 *
 * <p>Incluye solo datos propios de services. El detalle compuesto (perfil del oferente, metricas,
 * feedback) se arma fuera del modulo con las APIs correspondientes.</p>
 */
public record ServiceSnapshot(
        Long id,
        Long offererId,
        String title,
        String description,
        List<String> photos,
        BigDecimal priceHourly,
        Long categoryId,
        Integer averageDurationMinutes,
        boolean active,
        BigDecimal operationRadiusKm,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt) {

    public ServiceSnapshot {
        photos = photos == null ? List.of() : List.copyOf(photos);
    }
}

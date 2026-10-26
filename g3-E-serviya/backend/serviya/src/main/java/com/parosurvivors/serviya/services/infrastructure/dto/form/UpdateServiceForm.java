package com.parosurvivors.serviya.services.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Entrada web (Form) para actualizar parcialmente un servicio. PATCH /api/v1/services/{id} (RF-013).
 * Dueno (OFFERER). Solo los campos no-nulos se actualizan. El serviceId va en el path.
 * TODO: revisar validaciones.
 */
@Schema(description = "Campos editables de un servicio (PATCH parcial)")
public record UpdateServiceForm(
        @Size(min = 3, max = 255) String title,
        @Size(min = 10, max = 5000) String description,
        List<String> photos,
        @DecimalMin("0.01") @DecimalMax("999999.99") BigDecimal priceHourly,
        @Positive Long categoryId,
        @Positive Integer averageDurationMinutes,
        @DecimalMin("0") @DecimalMax("500") BigDecimal operationRadiusKm) {
}

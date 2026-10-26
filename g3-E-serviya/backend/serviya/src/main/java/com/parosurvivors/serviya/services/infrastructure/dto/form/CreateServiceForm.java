package com.parosurvivors.serviya.services.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Entrada web (Form) para crear un servicio. POST /api/v1/services (RF-013). Solo OFFERER.
 * El offererId NO viaja aqui: se extrae del JWT en el controller.
 * TODO: revisar validaciones.
 */
@Schema(description = "Datos para crear un servicio")
public record CreateServiceForm(
        @NotBlank @Size(min = 3, max = 255) String title,
        @NotBlank @Size(min = 10, max = 5000) String description,
        List<String> photos,
        @NotNull @DecimalMin("0.01") @DecimalMax("999999.99") BigDecimal priceHourly,
        @NotNull @Positive Long categoryId,
        @Positive Integer averageDurationMinutes,
        @DecimalMin("0") @DecimalMax("500") BigDecimal operationRadiusKm,
        @NotNull @Positive Long offererId) {
}

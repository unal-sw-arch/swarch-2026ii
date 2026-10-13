package com.parosurvivors.serviya.services.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Sub-respuesta reutilizable para exponer informacion publica del oferente
 * dentro del detalle de un servicio.
 */
@Schema(description = "Informacion publica del oferente")
public record OffererProfileResponse(
        Long userId,
        String fullName,
        String profilePhotoUrl,
        String specialty,
        String whatsappNumber,
        String publicDescription,
        BigDecimal averageRating
        // Integer totalCompletedServices
) {}

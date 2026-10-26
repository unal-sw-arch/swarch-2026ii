package com.parosurvivors.serviya.profiles.domain;

import java.math.BigDecimal;

/**
 * Read model de dominio (proyeccion de lectura, sin sufijo tecnico): resumen del perfil de oferente
 * agregado a partir del perfil + metricas. Lo devuelve OffererProfileService.getProfileSummary.
 * No es un DTO: vive en domain/ (ver GUIA_DTOS.txt, "Caso especial").
 * TODO: revisar campos.
 */
public record OffererProfileSummary(
        Long userId,
        String fullName,
        String profilePhotoUrl,
        String specialty,
        BigDecimal averageRating) {
}

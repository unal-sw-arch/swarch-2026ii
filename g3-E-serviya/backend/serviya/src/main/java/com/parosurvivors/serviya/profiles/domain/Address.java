package com.parosurvivors.serviya.profiles.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Dirección de un usuario. Mapea la tabla {@code addresses}.
 * {@code addressLine} se guarda cifrado (AES-256-GCM) en la base; en el dominio se
 * maneja en claro (ver CLAUDE.md, "Security/PII").
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {
    private Long id;
    private Long userId;
    private String addressLine;
    private String city;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private LocalDateTime createdAt;

    // =====================================================
    // BUSINESS METHODS
    // =====================================================

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    /**
     * Distancia en kilómetros hasta otras coordenadas (fórmula de Haversine).
     * Útil para validar que una solicitud cae dentro del radio de operación del servicio
     * (ver ServiceRequestCommandServicePort#checkWithinRadius).
     */
    public double distanceKmTo(BigDecimal otherLatitude, BigDecimal otherLongitude) {
        if (!hasCoordinates() || otherLatitude == null || otherLongitude == null) {
            throw new IllegalStateException("Faltan coordenadas para calcular la distancia");
        }
        final double earthRadiusKm = 6371.0;
        double lat1 = Math.toRadians(latitude.doubleValue());
        double lat2 = Math.toRadians(otherLatitude.doubleValue());
        double deltaLat = Math.toRadians(otherLatitude.subtract(latitude).doubleValue());
        double deltaLon = Math.toRadians(otherLongitude.subtract(longitude).doubleValue());

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }
}

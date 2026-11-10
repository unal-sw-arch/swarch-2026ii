package com.parosurvivors.serviya.bff.application.dto;

import java.math.BigDecimal;

/**
 * Parametros HTTP de busqueda de servicios antes de resolver los filtros cross-module.
 */
public record ServiceSearchRequest(
        String name,
        Long categoryId,
        Long offererId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean available,
        Double minRating,
        Double maxRating,
        Double latitude,
        Double longitude,
        Double maxDistanceKm) {
}

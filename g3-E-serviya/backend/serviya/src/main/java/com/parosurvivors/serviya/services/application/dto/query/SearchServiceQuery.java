package com.parosurvivors.serviya.services.application.dto.query;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO de búsqueda para servicios. Es inmutable y dispone de un `@Builder` para construcción opcional.
 *
 * <p>Los ids resueltos por otros módulos ({@code metrics.api}, {@code profiles.api}) viajan en
 * listas separadas para que el repositorio de services no dependa de su dominio ni de su
 * infraestructura.</p>
 */
@Builder
public record SearchServiceQuery(
        // Full text / name search over title and description
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
        Double maxDistanceKm,
        // Ids resueltos por metrics.api para el filtro de rating; null/vacio = sin filtro.
        List<Long> serviceIds,
        // Ids resueltos por profiles.api para el nombre del oferente; null/vacio = sin filtro.
        List<Long> offererIdsByName,
        // Ids resueltos por profiles.api para la geolocalización; null/vacio = sin filtro.
        List<Long> offererIdsWithinDistance
) {

    public SearchServiceQuery withServiceIds(List<Long> ids) {
        return new SearchServiceQuery(
                name, categoryId, offererId, minPrice, maxPrice, available,
                minRating, maxRating, latitude, longitude, maxDistanceKm, ids,
                offererIdsByName, offererIdsWithinDistance);
    }

    public SearchServiceQuery withOffererIdsByName(List<Long> ids) {
        return new SearchServiceQuery(
                name, categoryId, offererId, minPrice, maxPrice, available,
                minRating, maxRating, latitude, longitude, maxDistanceKm, serviceIds,
                ids, offererIdsWithinDistance);
    }

    public SearchServiceQuery withOffererIdsWithinDistance(List<Long> ids) {
        return new SearchServiceQuery(
                name, categoryId, offererId, minPrice, maxPrice, available,
                minRating, maxRating, latitude, longitude, maxDistanceKm, serviceIds,
                offererIdsByName, ids);
    }
}
